package com.sasch.cameragps.sharednew.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.location.PhoneLocationFix
import com.sasch.cameragps.sharednew.bluetooth.session.CameraAutoCorrectionControls
import com.sasch.cameragps.sharednew.bluetooth.session.CameraAutoCorrectionSetting
import com.sasch.cameragps.sharednew.bluetooth.status.CameraLiveStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class DeviceDetailViewModel(
    dataSource: DeviceDetailDataSource,
    private val serviceActions: DeviceDetailServiceActions,
    private val cameraSettings: CameraAutoCorrectionControls,
    private val liveStatusSource: DeviceLiveStatusSource = DeviceLiveStatusSource.None,
) : ViewModel() {

    val sessions = cameraSettings.sessions

    /** Live status (signal, GPS writes, time correction, cached battery); null until the camera was seen. */
    fun liveStatus(device: String): Flow<CameraLiveStatus?> = liveStatusSource.observe(device.uppercase())

    /** Signal polling while the page is visible; call the returned function when it is not. */
    fun acquireSignalMonitoring(device: String): () -> Unit =
        liveStatusSource.acquireSignalMonitoring(device.uppercase())

    /** Phone-level accuracy/time of the latest location fix (for the "location signal" cell). */
    val phoneLocation: Flow<PhoneLocationFix?> = liveStatusSource.observePhoneLocation()

    fun refreshCameraSettings(device: String) = cameraSettings.refreshAutoCorrectionSettings(device)

    fun setAutoCorrectionSetting(
        device: String,
        setting: CameraAutoCorrectionSetting,
        enabled: Boolean
    ) =
        cameraSettings.setAutoCorrectionSetting(device, setting, enabled)

    private val stateStore = DeviceDetailStateStore(dataSource)
    val uiState: StateFlow<DeviceDetailToggleState> = stateStore.uiState

    /**
     * [onReEnabled] (iOS only) fires when a disabled device was switched back on; Android reads the
     * flags as they are, so a manual disconnect survives re-opening the screen.
     */
    fun load(address: String, deviceName: String? = null, onReEnabled: (() -> Unit)? = null) {
        viewModelScope.launch {
            if (stateStore.load(address, deviceName, reEnableDisabled = onReEnabled != null)) onReEnabled?.invoke()
        }
    }

    private val _pending = MutableStateFlow(PendingConnection.None)

    /** The user's connect/disconnect request that the session has not confirmed yet. */
    val pending: StateFlow<PendingConnection> = _pending.asStateFlow()
    private var pendingJob: Job? = null

    /**
     * Manual disconnect: persist "disabled" first (so no automatic entry point reconnects), stop
     * the presence observation, then ask the service to really close this one link.
     */
    fun disconnect(device: String) {
        val id = device.uppercase()
        if (_pending.value != PendingConnection.None) return
        _pending.value = PendingConnection.Disconnecting
        pendingJob?.cancel()
        pendingJob = viewModelScope.launch {
            stateStore.setDeviceEnabled(id, false)
            serviceActions.setPresenceObservation(id, false)
            serviceActions.disconnect(id)
            // Settled once the session is really gone; the timeout keeps a lost event from
            // freezing the button.
            withTimeoutOrNull(DISCONNECT_SETTLE) {
                sessions.first { map ->
                    map[id]?.let { deviceConnectionStatus(it) == DeviceConnectionStatus.Disconnected } ?: true
                }
            }
            _pending.value = PendingConnection.None
        }
    }

    /** Manual connect: re-enable, restore the presence observation, then connect explicitly. */
    fun connect(device: String) {
        val id = device.uppercase()
        if (_pending.value != PendingConnection.None) return
        _pending.value = PendingConnection.Connecting
        pendingJob?.cancel()
        pendingJob = viewModelScope.launch {
            stateStore.setDeviceEnabled(id, true)
            serviceActions.setPresenceObservation(id, true)
            serviceActions.connect(id)
            // The session registry takes over as soon as the attempt starts (or fails).
            withTimeoutOrNull(CONNECT_SETTLE) {
                sessions.first { map ->
                    map[id]?.let { deviceConnectionStatus(it) != DeviceConnectionStatus.Disconnected } ?: false
                }
            }
            _pending.value = PendingConnection.None
        }
    }

    /**
     * "Cancel" while connecting: stop waiting and stop trying. It is the same persisted intent as a
     * manual disconnect (no automatic reconnect until the user taps Connect), so it reuses that
     * path, which also closes a GATT that is still waiting in autoConnect.
     */
    fun cancelConnecting(device: String) {
        abandonPhotoTransfer()
        if (_pending.value == PendingConnection.Disconnecting) return
        pendingJob?.cancel()
        _pending.value = PendingConnection.None
        disconnect(device)
    }

    private val _photoTransfer = MutableStateFlow(PhotoTransferEntry.Idle)

    /** Entry state of the photo-transfer button (see [requestPhotoTransfer]). */
    val photoTransfer: StateFlow<PhotoTransferEntry> = _photoTransfer.asStateFlow()
    private var photoTransferJob: Job? = null

    /**
     * Tap on "Photo transfer". Bluetooth ready → [PhotoTransferEntry.Ready] at once. Otherwise
     * connect Bluetooth first (exactly like the Connect button: re-enables the camera) and wait
     * until the link is ready for Wi-Fi ([BleSessionPhase.Transmitting]); a failed attempt or the
     * timeout ends in [PhotoTransferEntry.Failed]. The page then starts the Wi-Fi flow, which owns
     * everything after this point. A new tap after a failure is the retry.
     */
    fun requestPhotoTransfer(device: String) {
        val id = device.uppercase()
        if (_photoTransfer.value == PhotoTransferEntry.ConnectingBluetooth) return
        val action = photoTransferAction(deviceConnectionStatus(sessions.value[id]), _pending.value)
        when (action) {
            PhotoTransferAction.Ignore -> return
            PhotoTransferAction.Open -> {
                _photoTransfer.value = PhotoTransferEntry.Ready
                return
            }
            PhotoTransferAction.ConnectFirst -> connect(id)
            PhotoTransferAction.Wait -> Unit
        }
        _photoTransfer.value = PhotoTransferEntry.ConnectingBluetooth
        photoTransferJob?.cancel()
        photoTransferJob = viewModelScope.launch {
            // An Error/Disconnected left over from an earlier attempt must not fail this one:
            // only count it after this attempt was seen running.
            var attemptSeen = action == PhotoTransferAction.Wait
            val settled = withTimeoutOrNull(PHOTO_TRANSFER_CONNECT_TIMEOUT) {
                sessions.first { map ->
                    val phase = map[id]?.phase
                    if (phase != null && phase != BleSessionPhase.Disconnected && phase != BleSessionPhase.Error) {
                        attemptSeen = true
                    }
                    phase == BleSessionPhase.Transmitting || (attemptSeen &&
                        (phase == null || phase == BleSessionPhase.Disconnected || phase == BleSessionPhase.Error))
                }
            }
            _photoTransfer.value = if (settled?.get(id)?.phase == BleSessionPhase.Transmitting) {
                PhotoTransferEntry.Ready
            } else {
                PhotoTransferEntry.Failed
            }
        }
    }

    /** The page opened the Wi-Fi flow for a [PhotoTransferEntry.Ready]. */
    fun photoTransferOpened() {
        if (_photoTransfer.value == PhotoTransferEntry.Ready) _photoTransfer.value = PhotoTransferEntry.Idle
    }

    /**
     * Leaving the page (or cancelling) drops a pending entry so the Wi-Fi flow never opens later on
     * its own. The Bluetooth connection itself is left alone.
     */
    fun abandonPhotoTransfer() {
        photoTransferJob?.cancel()
        photoTransferJob = null
        _photoTransfer.value = PhotoTransferEntry.Idle
    }

    /** Auto time/area are fixed on: switch on whatever is supported but currently off. */
    fun enforceAutoCorrection(device: String) {
        val session = sessions.value[device.uppercase()]
        for (setting in autoCorrectionSettingsToEnable(session)) {
            cameraSettings.setAutoCorrectionSetting(device, setting, true)
        }
    }

    fun setDeviceEnabled(
        isEnabled: Boolean,
        device: String,
        onCompleted: ((Boolean) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            stateStore.setDeviceEnabled(device, isEnabled)
            // Only notify once the write is committed: platform reactions
            // (reconnect, service shutdown) read the flag back from the database
            onCompleted?.invoke(isEnabled)
        }
    }

    /** In-app rename. iOS accessories rename through the system sheet instead. */
    fun renameDevice(device: String, name: String) {
        viewModelScope.launch {
            stateStore.setDeviceName(device, name)
        }
    }

    /**
     * "Keep active" only changes the running keep-alive policy (the service lingers for this camera
     * after it goes out of range); it never connects or disconnects anything, and leaving the app
     * still disconnects as before.
     */
    fun setAlwaysOnEnabled(enabled: Boolean, deviceAddress: String) {
        viewModelScope.launch {
            stateStore.setAlwaysOnEnabled(deviceAddress, enabled)
        }
    }

    private companion object {
        val DISCONNECT_SETTLE = 6.seconds
        val CONNECT_SETTLE = 8.seconds
        val PHOTO_TRANSFER_CONNECT_TIMEOUT = 30.seconds
    }
}
