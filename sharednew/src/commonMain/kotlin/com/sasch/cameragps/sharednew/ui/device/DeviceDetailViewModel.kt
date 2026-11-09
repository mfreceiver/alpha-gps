package com.sasch.cameragps.sharednew.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasch.cameragps.sharednew.bluetooth.session.CameraAutoCorrectionControls
import com.sasch.cameragps.sharednew.bluetooth.session.CameraAutoCorrectionSetting
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
     * [onReEnabled] fires when a device left disabled by an older version was
     * switched back on, so the host can restore presence observation.
     */
    fun load(address: String, deviceName: String? = null, onReEnabled: (() -> Unit)? = null) {
        viewModelScope.launch {
            if (stateStore.load(address, deviceName)) onReEnabled?.invoke()
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

    fun setAlwaysOnEnabled(enabled: Boolean, deviceAddress: String) {
        viewModelScope.launch {
            stateStore.setAlwaysOnEnabled(deviceAddress, enabled)
            if (enabled) {
                serviceActions.startAlwaysOn(deviceAddress)
            } else {
                requestShutdownWithDelay(deviceAddress)
            }
        }
    }

    private suspend fun requestShutdownWithDelay(deviceAddress: String) {
        stateStore.setButtonEnabled(false)
        serviceActions.requestShutdown(deviceAddress)
        delay(2.seconds)
        stateStore.setButtonEnabled(true)
    }
}
