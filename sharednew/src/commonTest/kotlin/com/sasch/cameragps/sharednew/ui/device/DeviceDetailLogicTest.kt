package com.sasch.cameragps.sharednew.ui.device

import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.session.CameraAutoCorrectionControls
import com.sasch.cameragps.sharednew.bluetooth.session.CameraAutoCorrectionSetting
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSettingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceDetailLogicTest {
    private fun session(
        phase: BleSessionPhase = BleSessionPhase.Transmitting,
        time: CameraSettingState = CameraSettingState(),
        area: CameraSettingState = CameraSettingState(),
    ) = CameraSession("AA:BB", phase = phase, autoTimeCorrection = time, autoAreaAdjustment = area)

    private val offSupported = CameraSettingState(supported = true, enabled = false)
    private val onSupported = CameraSettingState(supported = true, enabled = true)

    @Test
    fun connectionStatusFollowsTheBleSessionPhase() {
        assertEquals(DeviceConnectionStatus.Disconnected, deviceConnectionStatus(null))
        assertEquals(DeviceConnectionStatus.Disconnected, deviceConnectionStatus(session(BleSessionPhase.Disconnected)))
        assertEquals(DeviceConnectionStatus.Disconnected, deviceConnectionStatus(session(BleSessionPhase.Error)))
        assertEquals(DeviceConnectionStatus.Connected, deviceConnectionStatus(session(BleSessionPhase.Transmitting)))
        for (phase in listOf(
            BleSessionPhase.Connecting, BleSessionPhase.Connected, BleSessionPhase.DiscoveringServices,
            BleSessionPhase.ReadingConfig, BleSessionPhase.EnablingGps, BleSessionPhase.LockingGps,
            BleSessionPhase.SyncingTime, BleSessionPhase.RemoteStatusProbing,
        )) {
            assertEquals(DeviceConnectionStatus.Connecting, deviceConnectionStatus(session(phase)), "$phase")
        }
    }

    @Test
    fun autoCorrectionTurnsOnlySupportedSettingsThatAreKnownOff() {
        val toEnable = autoCorrectionSettingsToEnable(session(time = offSupported, area = onSupported))
        assertEquals(listOf(CameraAutoCorrectionSetting.Time), toEnable)
        assertEquals(
            CameraAutoCorrectionSetting.entries.toList(),
            autoCorrectionSettingsToEnable(session(time = offSupported, area = offSupported)),
        )
    }

    @Test
    fun autoCorrectionLeavesUnknownUnsupportedPendingAndFailedAlone() {
        val blocked = listOf(
            CameraSettingState(),
            CameraSettingState(supported = false, enabled = false),
            CameraSettingState(supported = true, enabled = null),
            offSupported.copy(pending = true),
            offSupported.copy(failed = true),
        )
        for (state in blocked) {
            assertTrue(autoCorrectionSettingsToEnable(session(time = state, area = state)).isEmpty(), "$state")
        }
        assertTrue(autoCorrectionSettingsToEnable(session(BleSessionPhase.Connecting, offSupported, offSupported)).isEmpty())
        assertTrue(autoCorrectionSettingsToEnable(null).isEmpty())
    }

    @Test
    fun singleSavedCameraOpensDirectlyUntilTheUserBacksOut() {
        assertEquals("AA:BB", autoOpenDeviceAddress(listOf("AA:BB"), dismissed = false))
        assertNull(autoOpenDeviceAddress(listOf("AA:BB"), dismissed = true))
        assertNull(autoOpenDeviceAddress(emptyList(), dismissed = false))
        assertNull(autoOpenDeviceAddress(listOf("AA:BB", "CC:DD"), dismissed = false))
    }

    @Test
    fun viewModelSwitchesOnWhatTheCameraReportsOff() {
        val controls = FakeControls(mapOf("AA:BB" to session(time = offSupported, area = onSupported)))
        val model = DeviceDetailViewModel(NoopSource, NoopActions, controls)

        model.enforceAutoCorrection("aa:bb")

        assertEquals(listOf(Triple("aa:bb", CameraAutoCorrectionSetting.Time, true)), controls.writes)
    }

    @Test
    fun viewModelDoesNothingWhileTheCameraIsNotReady() {
        val controls = FakeControls(mapOf("AA:BB" to session(BleSessionPhase.Connecting, offSupported, offSupported)))
        DeviceDetailViewModel(NoopSource, NoopActions, controls).enforceAutoCorrection("AA:BB")
        assertTrue(controls.writes.isEmpty())
    }

    @Test
    fun loadReEnablesALegacyDisabledDeviceAndClearsAlwaysOn() = runTest {
        val source = FlagSource(enabled = false, alwaysOn = true)
        val store = DeviceDetailStateStore(source)

        val reEnabled = store.load("aa:bb")

        assertTrue(reEnabled)
        assertTrue(source.enabled)
        assertFalse(source.alwaysOn)
        assertTrue(store.uiState.value.isDeviceEnabled)
        assertFalse(store.uiState.value.isAlwaysOnEnabled)
    }

    @Test
    fun loadLeavesAlreadyNormalFlagsUntouched() = runTest {
        val source = FlagSource(enabled = true, alwaysOn = false)
        val reEnabled = DeviceDetailStateStore(source).load("AA:BB")
        assertFalse(reEnabled)
        assertEquals(0, source.writes)
    }

    private class FakeControls(initial: Map<String, CameraSession>) : CameraAutoCorrectionControls {
        override val sessions: StateFlow<Map<String, CameraSession>> = MutableStateFlow(initial)
        val writes = mutableListOf<Triple<String, CameraAutoCorrectionSetting, Boolean>>()
        override fun refreshAutoCorrectionSettings(identifier: String) = Unit
        override fun setAutoCorrectionSetting(identifier: String, setting: CameraAutoCorrectionSetting, enabled: Boolean) {
            writes += Triple(identifier, setting, enabled)
        }
    }

    private object NoopActions : DeviceDetailServiceActions {
        override fun startAlwaysOn(deviceAddress: String) = Unit
        override fun requestShutdown(deviceAddress: String) = Unit
    }

    private object NoopSource : DeviceDetailDataSource {
        override suspend fun ensureDeviceExists(deviceId: String, deviceName: String?) = Unit
        override suspend fun isDeviceEnabled(deviceId: String) = true
        override suspend fun isAlwaysOnEnabled(deviceId: String) = false
        override suspend fun setDeviceEnabled(deviceId: String, enabled: Boolean) = Unit
        override suspend fun setAlwaysOnEnabled(deviceId: String, enabled: Boolean) = Unit
        override suspend fun getDeviceName(deviceId: String): String? = null
        override suspend fun setDeviceName(deviceId: String, name: String) = Unit
    }

    private class FlagSource(var enabled: Boolean, var alwaysOn: Boolean) : DeviceDetailDataSource {
        var writes = 0
        override suspend fun ensureDeviceExists(deviceId: String, deviceName: String?) = Unit
        override suspend fun isDeviceEnabled(deviceId: String) = enabled
        override suspend fun isAlwaysOnEnabled(deviceId: String) = alwaysOn
        override suspend fun setDeviceEnabled(deviceId: String, enabled: Boolean) { this.enabled = enabled; writes++ }
        override suspend fun setAlwaysOnEnabled(deviceId: String, enabled: Boolean) { alwaysOn = enabled; writes++ }
        override suspend fun getDeviceName(deviceId: String): String? = null
        override suspend fun setDeviceName(deviceId: String, name: String) = Unit
    }
}
