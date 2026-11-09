package com.sasch.cameragps.sharednew.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasch.cameragps.sharednew.bluetooth.session.CameraAutoCorrectionControls
import com.sasch.cameragps.sharednew.bluetooth.session.CameraAutoCorrectionSetting
import kotlinx.coroutines.Job
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
) : ViewModel() {

    val sessions = cameraSettings.sessions

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
    }
}
