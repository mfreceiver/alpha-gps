package com.sasch.cameragps.sharednew.ui.device

import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.session.CameraAutoCorrectionControls
import com.sasch.cameragps.sharednew.bluetooth.session.CameraAutoCorrectionSetting
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSettingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    fun loadReadsTheRealFlagsSoAManualDisconnectSurvivesReopening() = runTest {
        val source = FlagSource(enabled = false, alwaysOn = true)
        val store = DeviceDetailStateStore(source)

        val reEnabled = store.load("aa:bb")

        assertFalse(reEnabled)
        assertFalse(source.enabled, "a disconnected camera is not silently re-enabled")
        assertTrue(source.alwaysOn)
        assertEquals(0, source.writes)
        assertFalse(store.uiState.value.isDeviceEnabled)
        assertTrue(store.uiState.value.isAlwaysOnEnabled)
    }

    @Test
    fun loadLeavesNormalFlagsUntouched() = runTest {
        val source = FlagSource(enabled = true, alwaysOn = false)
        val reEnabled = DeviceDetailStateStore(source).load("AA:BB")
        assertFalse(reEnabled)
        assertEquals(0, source.writes)
        assertTrue(source.enabled)
    }

    @Test
    fun iosStillReEnablesADisabledDeviceOnRequest() = runTest {
        val source = FlagSource(enabled = false, alwaysOn = true)
        val store = DeviceDetailStateStore(source)

        assertTrue(store.load("aa:bb", reEnableDisabled = true))

        assertTrue(source.enabled)
        assertTrue(store.uiState.value.isDeviceEnabled)
    }

    @Test
    fun connectionButtonFollowsFactsFromTheSessionAndPendingFromTheUser() {
        val d = DeviceConnectionStatus.Disconnected
        val c = DeviceConnectionStatus.Connecting
        val t = DeviceConnectionStatus.Connected
        val none = PendingConnection.None
        assertEquals(DeviceConnectionButton.Connect, deviceConnectionButton(d, none))
        assertEquals(DeviceConnectionButton.Connecting, deviceConnectionButton(d, PendingConnection.Connecting))
        assertEquals(DeviceConnectionButton.Connecting, deviceConnectionButton(c, none))
        assertEquals(DeviceConnectionButton.Disconnect, deviceConnectionButton(t, none))
        assertEquals(DeviceConnectionButton.Disconnecting, deviceConnectionButton(t, PendingConnection.Disconnecting))
        assertEquals(DeviceConnectionButton.Disconnecting, deviceConnectionButton(c, PendingConnection.Disconnecting))
        assertEquals(DeviceConnectionButton.Disconnecting, deviceConnectionButton(d, PendingConnection.Disconnecting))
        assertTrue(DeviceConnectionButton.Connect.enabled && DeviceConnectionButton.Disconnect.enabled)
        assertFalse(DeviceConnectionButton.Connecting.enabled || DeviceConnectionButton.Disconnecting.enabled)
    }

    @Test
    fun settingDisplayNeverClaimsMoreThanTheCameraReported() {
        val s = session()
        assertEquals(SettingDisplay.Unknown, settingDisplay(null, onSupported))
        assertEquals(SettingDisplay.Unknown, settingDisplay(s, null))
        assertEquals(SettingDisplay.Unknown, settingDisplay(s, CameraSettingState()))
        assertEquals(SettingDisplay.Enabled, settingDisplay(s, onSupported))
        assertEquals(SettingDisplay.Off, settingDisplay(s, offSupported))
        assertEquals(SettingDisplay.Unsupported, settingDisplay(s, CameraSettingState(supported = false, enabled = false)))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun withMain(block: suspend TestScope.() -> Unit) {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) { block() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun disconnectPersistsDisabledBeforeAnyServiceActionAndSettlesWhenTheSessionIsGone() = withMain {
        val calls = mutableListOf<String>()
        val source = FlagSource(enabled = true, alwaysOn = false, log = calls)
        val actions = RecordingActions(calls)
        val controls = FakeControls(mapOf("AA:BB" to session()))
        val model = DeviceDetailViewModel(source, actions, controls)

        model.disconnect("aa:bb")
        assertEquals(PendingConnection.Disconnecting, model.pending.value)
        assertEquals(listOf("enabled=false", "observe AA:BB false", "disconnect AA:BB"), calls)
        assertFalse(source.enabled)

        model.disconnect("aa:bb") // a repeated tap while pending is ignored
        assertEquals(3, calls.size)

        controls.set("AA:BB", session(BleSessionPhase.Disconnected))
        assertEquals(PendingConnection.None, model.pending.value)
    }

    @Test
    fun connectReEnablesAndRestoresObservationBeforeTheExplicitConnect() = withMain {
        val calls = mutableListOf<String>()
        val source = FlagSource(enabled = false, alwaysOn = false, log = calls)
        val controls = FakeControls(emptyMap())
        val model = DeviceDetailViewModel(source, RecordingActions(calls), controls)

        model.connect("aa:bb")
        assertEquals(PendingConnection.Connecting, model.pending.value)
        assertEquals(listOf("enabled=true", "observe AA:BB true", "connect AA:BB"), calls)

        controls.set("AA:BB", session(BleSessionPhase.Connecting))
        assertEquals(PendingConnection.None, model.pending.value)
    }

    @Test
    fun keepActiveOnlyPersistsTheFlagAndNeverTouchesTheConnection() = withMain {
        val calls = mutableListOf<String>()
        val source = FlagSource(enabled = true, alwaysOn = false, log = calls)
        val model = DeviceDetailViewModel(source, RecordingActions(calls), FakeControls(emptyMap()))

        model.setAlwaysOnEnabled(true, "AA:BB")

        assertTrue(source.alwaysOn)
        assertEquals(listOf("alwaysOn=true"), calls)
    }

    private class FakeControls(initial: Map<String, CameraSession>) : CameraAutoCorrectionControls {
        private val state = MutableStateFlow(initial)
        override val sessions: StateFlow<Map<String, CameraSession>> = state
        fun set(id: String, session: CameraSession) { state.value = state.value + (id to session) }
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

    private class RecordingActions(private val log: MutableList<String>) : DeviceDetailServiceActions {
        override fun startAlwaysOn(deviceAddress: String) { log += "startAlwaysOn" }
        override fun requestShutdown(deviceAddress: String) { log += "shutdown" }
        override fun connect(deviceAddress: String) { log += "connect $deviceAddress" }
        override fun disconnect(deviceAddress: String) { log += "disconnect $deviceAddress" }
        override fun setPresenceObservation(deviceAddress: String, enabled: Boolean) { log += "observe $deviceAddress $enabled" }
    }

    private class FlagSource(
        var enabled: Boolean,
        var alwaysOn: Boolean,
        private val log: MutableList<String> = mutableListOf(),
    ) : DeviceDetailDataSource {
        var writes = 0
        override suspend fun ensureDeviceExists(deviceId: String, deviceName: String?) = Unit
        override suspend fun isDeviceEnabled(deviceId: String) = enabled
        override suspend fun isAlwaysOnEnabled(deviceId: String) = alwaysOn
        override suspend fun setDeviceEnabled(deviceId: String, enabled: Boolean) { this.enabled = enabled; writes++; log += "enabled=$enabled" }
        override suspend fun setAlwaysOnEnabled(deviceId: String, enabled: Boolean) { alwaysOn = enabled; writes++; log += "alwaysOn=$enabled" }
        override suspend fun getDeviceName(deviceId: String): String? = null
        override suspend fun setDeviceName(deviceId: String, name: String) = Unit
    }
}
