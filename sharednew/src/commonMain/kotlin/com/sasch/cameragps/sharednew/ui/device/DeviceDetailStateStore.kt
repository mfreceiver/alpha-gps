package com.sasch.cameragps.sharednew.ui.device

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface DeviceDetailDataSource {
    suspend fun ensureDeviceExists(deviceId: String, deviceName: String? = null)
    suspend fun isDeviceEnabled(deviceId: String): Boolean
    suspend fun isAlwaysOnEnabled(deviceId: String): Boolean
    suspend fun setDeviceEnabled(deviceId: String, enabled: Boolean)
    suspend fun setAlwaysOnEnabled(deviceId: String, enabled: Boolean)
    suspend fun getDeviceName(deviceId: String): String?

    /** Persists a name a person chose, so it is never replaced by a hardware name. */
    suspend fun setDeviceName(deviceId: String, name: String)
}

interface DeviceDetailServiceActions {
    fun startAlwaysOn(deviceAddress: String)
    fun requestShutdown(deviceAddress: String)

    /** Explicit user Connect: ask the platform to connect this camera now. */
    fun connect(deviceAddress: String) = Unit

    /** Explicit user Disconnect: really close this one camera's link (not a pause). */
    fun disconnect(deviceAddress: String) = Unit

    /** Start/stop the platform's "camera appeared" observation that auto-connects the camera. */
    fun setPresenceObservation(deviceAddress: String, enabled: Boolean) = Unit
}

data class DeviceDetailToggleState(
    val buttonEnabled: Boolean = true,
    val deviceName: String = "",
    val isDeviceEnabled: Boolean = true,
    val isAlwaysOnEnabled: Boolean = false,
)

class DeviceDetailStateStore(
    private val dataSource: DeviceDetailDataSource,
) {
    private val _uiState = MutableStateFlow(DeviceDetailToggleState())
    val uiState: StateFlow<DeviceDetailToggleState> = _uiState.asStateFlow()

    /**
     * Loads the row and publishes its real flags: "disabled" is the user's manual disconnect and
     * must survive leaving and re-opening the screen. [reEnableDisabled] exists only for iOS,
     * which has no connect button yet and so must not leave a device disabled for good.
     *
     * @return true when a previously disabled device was re-enabled ([reEnableDisabled]).
     */
    suspend fun load(deviceId: String, deviceName: String? = null, reEnableDisabled: Boolean = false): Boolean {
        val normalized = deviceId.uppercase()
        dataSource.ensureDeviceExists(normalized, deviceName)
        var enabled = dataSource.isDeviceEnabled(normalized)
        var reEnabled = false
        if (!enabled && reEnableDisabled) {
            dataSource.setDeviceEnabled(normalized, true)
            enabled = true
            reEnabled = true
        }
        val alwaysOn = dataSource.isAlwaysOnEnabled(normalized)
        _uiState.update {
            it.copy(
                deviceName = dataSource.getDeviceName(normalized) ?: deviceName.orEmpty(),
                isAlwaysOnEnabled = alwaysOn,
                isDeviceEnabled = enabled,
            )
        }
        return reEnabled
    }

    suspend fun setDeviceEnabled(deviceId: String, enabled: Boolean, deviceName: String? = null) {
        val normalized = deviceId.uppercase()
        dataSource.ensureDeviceExists(normalized, deviceName)
        dataSource.setDeviceEnabled(normalized, enabled)
        _uiState.update { it.copy(isDeviceEnabled = enabled) }
    }

    suspend fun setAlwaysOnEnabled(deviceId: String, enabled: Boolean, deviceName: String? = null) {
        val normalized = deviceId.uppercase()
        dataSource.ensureDeviceExists(normalized, deviceName)
        dataSource.setAlwaysOnEnabled(normalized, enabled)
        _uiState.update { it.copy(isAlwaysOnEnabled = enabled) }
    }

    suspend fun setDeviceName(deviceId: String, name: String) {
        val normalized = deviceId.uppercase()
        val trimmed = name.trim()
        // An empty rename is a no-op rather than a way to erase a camera's name.
        if (trimmed.isEmpty()) return
        dataSource.ensureDeviceExists(normalized, trimmed)
        dataSource.setDeviceName(normalized, trimmed)
        _uiState.update { it.copy(deviceName = trimmed) }
    }

    fun setButtonEnabled(enabled: Boolean) {
        _uiState.update { it.copy(buttonEnabled = enabled) }
    }
}
