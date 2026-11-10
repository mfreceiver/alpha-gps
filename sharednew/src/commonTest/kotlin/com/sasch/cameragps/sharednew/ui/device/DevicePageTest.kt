package com.sasch.cameragps.sharednew.ui.device

import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.session.CameraAutoCorrectionControls
import com.sasch.cameragps.sharednew.bluetooth.session.CameraAutoCorrectionSetting
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.status.CameraBattery
import com.sasch.cameragps.sharednew.bluetooth.status.CameraLiveStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Device page (M2): connection slot, photo-transfer entry, battery and age display. */
@OptIn(ExperimentalCoroutinesApi::class)
class DevicePageTest {
    private val connected = DeviceConnectionStatus.Connected
    private val connecting = DeviceConnectionStatus.Connecting
    private val disconnected = DeviceConnectionStatus.Disconnected

    @Test
    fun connectionSlotOffersOneControlPerState() {
        assertEquals(ConnectionAction.Disconnect, connectionAction(connected, PendingConnection.None))
        assertEquals(ConnectionAction.CancelConnecting, connectionAction(connecting, PendingConnection.None))
        assertEquals(ConnectionAction.CancelConnecting, connectionAction(disconnected, PendingConnection.Connecting))
        assertEquals(ConnectionAction.Connect, connectionAction(disconnected, PendingConnection.None))
        for (status in DeviceConnectionStatus.entries) {
            assertEquals(ConnectionAction.None, connectionAction(status, PendingConnection.Disconnecting), "$status")
        }
    }

    @Test
    fun headlineFollowsTheLinkAndTheUsersPendingRequest() {
        assertEquals(ConnectionHeadline.Connected, connectionHeadline(connected, PendingConnection.None))
        assertEquals(ConnectionHeadline.Connecting, connectionHeadline(connecting, PendingConnection.None))
        assertEquals(ConnectionHeadline.Connecting, connectionHeadline(disconnected, PendingConnection.Connecting))
        assertEquals(ConnectionHeadline.Disconnecting, connectionHeadline(connected, PendingConnection.Disconnecting))
        assertEquals(ConnectionHeadline.Disconnected, connectionHeadline(disconnected, PendingConnection.None))
    }

    @Test
    fun photoTransferOpensOnlyOnAReadyLinkAndConnectsFirstOtherwise() {
        assertEquals(PhotoTransferAction.Open, photoTransferAction(connected, PendingConnection.None))
        assertEquals(PhotoTransferAction.Wait, photoTransferAction(connecting, PendingConnection.None))
        assertEquals(PhotoTransferAction.Wait, photoTransferAction(disconnected, PendingConnection.Connecting))
        assertEquals(PhotoTransferAction.ConnectFirst, photoTransferAction(disconnected, PendingConnection.None))
        assertEquals(PhotoTransferAction.Ignore, photoTransferAction(connected, PendingConnection.Disconnecting))
    }

    @Test
    fun batteryIsUnknownWithoutACacheAndShowsItsAgeOnlyPastThirtyMinutes() {
        val now = 100.hours.inWholeMilliseconds
        fun cached(age: Long) = CameraBattery(80, obtainedAtMillis = now - age, cameraId = "AA:BB")

        assertEquals(BatteryDisplay.Unknown, batteryDisplay(null, now))
        assertEquals(BatteryDisplay.Value(80, null), batteryDisplay(cached(0), now))
        assertEquals(BatteryDisplay.Value(80, null), batteryDisplay(cached(30.minutes.inWholeMilliseconds), now))
        assertEquals(BatteryDisplay.Value(80, 31), batteryDisplay(cached(31.minutes.inWholeMilliseconds), now))
        assertEquals(BatteryDisplay.Value(80, 180), batteryDisplay(cached(3.hours.inWholeMilliseconds), now))
        assertEquals(BatteryDisplay.Unknown, batteryDisplay(cached(25.hours.inWholeMilliseconds), now))
    }

    @Test
    fun relativeAgeIsCoarse() {
        val now = 10 * 24.hours.inWholeMilliseconds
        assertEquals(RelativeAge.Never, relativeAge(null, now))
        assertEquals(RelativeAge.JustNow, relativeAge(now - 59.seconds.inWholeMilliseconds, now))
        assertEquals(RelativeAge.JustNow, relativeAge(now + 5_000, now)) // clock skew is not "in the future"
        assertEquals(RelativeAge.Minutes(5), relativeAge(now - 5.minutes.inWholeMilliseconds, now))
        assertEquals(RelativeAge.Hours(2), relativeAge(now - 2.hours.inWholeMilliseconds, now))
        assertEquals(RelativeAge.Days(3), relativeAge(now - 3 * 24.hours.inWholeMilliseconds, now))
    }

    @Test
    fun locationWritingIsActiveOnlyWhileTransmitting() {
        assertFalse(locationWritingActive(null))
        assertFalse(locationWritingActive(session(BleSessionPhase.SyncingTime)))
        assertTrue(locationWritingActive(session(BleSessionPhase.Transmitting)))
    }

    @Test
    fun photoTransferOnAReadyLinkOpensAtOnceWithoutTouchingBluetooth() = withMain {
        val calls = mutableListOf<String>()
        val model = model(calls, Controls(mapOf(ID to session(BleSessionPhase.Transmitting))))

        model.requestPhotoTransfer("aa:bb")
        assertEquals(PhotoTransferEntry.Ready, model.photoTransfer.value)
        assertEquals(emptyList(), calls)

        model.photoTransferOpened()
        assertEquals(PhotoTransferEntry.Idle, model.photoTransfer.value)
    }

    @Test
    fun photoTransferConnectsBluetoothFirstAndOpensOnceTheLinkIsReady() = withMain {
        val calls = mutableListOf<String>()
        val controls = Controls(emptyMap())
        val model = model(calls, controls)

        model.requestPhotoTransfer("aa:bb")
        // Same path as the Connect button (re-enables a manually disconnected camera).
        assertEquals(listOf("enabled=true", "observe AA:BB true", "connect AA:BB"), calls)
        assertEquals(PhotoTransferEntry.ConnectingBluetooth, model.photoTransfer.value)

        model.requestPhotoTransfer("aa:bb") // repeated tap while waiting
        assertEquals(3, calls.size)

        controls.set(session(BleSessionPhase.Connecting))
        controls.set(session(BleSessionPhase.SyncingTime))
        assertEquals(PhotoTransferEntry.ConnectingBluetooth, model.photoTransfer.value)
        controls.set(session(BleSessionPhase.Transmitting))
        assertEquals(PhotoTransferEntry.Ready, model.photoTransfer.value)
    }

    @Test
    fun aFailedAttemptShowsFailedAndTheNextTapRetries() = withMain {
        val calls = mutableListOf<String>()
        // A previous attempt left the session in Error: that must not fail the new one at once.
        val controls = Controls(mapOf(ID to session(BleSessionPhase.Error)))
        val model = model(calls, controls)

        model.requestPhotoTransfer("AA:BB")
        assertEquals(PhotoTransferEntry.ConnectingBluetooth, model.photoTransfer.value)

        controls.set(session(BleSessionPhase.Connecting))
        controls.set(session(BleSessionPhase.Error))
        assertEquals(PhotoTransferEntry.Failed, model.photoTransfer.value)

        calls.clear()
        model.requestPhotoTransfer("AA:BB")
        assertEquals(PhotoTransferEntry.ConnectingBluetooth, model.photoTransfer.value)
        assertEquals(listOf("enabled=true", "observe AA:BB true", "connect AA:BB"), calls)
    }

    @Test
    fun aCameraThatNeverAnswersFailsAfterTheTimeout() = withMain {
        val model = model(mutableListOf(), Controls(emptyMap()))

        model.requestPhotoTransfer("AA:BB")
        advanceTimeBy(29.seconds)
        assertEquals(PhotoTransferEntry.ConnectingBluetooth, model.photoTransfer.value)
        advanceTimeBy(2.seconds)
        assertEquals(PhotoTransferEntry.Failed, model.photoTransfer.value)
    }

    @Test
    fun leavingThePageDropsAPendingEntrySoWiFiNeverOpensLater() = withMain {
        val calls = mutableListOf<String>()
        val controls = Controls(emptyMap())
        val model = model(calls, controls)

        model.requestPhotoTransfer("AA:BB")
        model.abandonPhotoTransfer()
        controls.set(session(BleSessionPhase.Transmitting))

        assertEquals(PhotoTransferEntry.Idle, model.photoTransfer.value)
        // Bluetooth itself is left alone: no disconnect.
        assertFalse(calls.any { it.startsWith("disconnect") })
    }

    @Test
    fun cancelWhileConnectingIsAPersistedManualDisconnect() = withMain {
        val calls = mutableListOf<String>()
        val source = Flags(enabled = false, log = calls)
        val controls = Controls(emptyMap())
        val model = DeviceDetailViewModel(source, Actions(calls), controls)

        model.requestPhotoTransfer("AA:BB")
        assertEquals(PendingConnection.Connecting, model.pending.value)
        controls.set(session(BleSessionPhase.Connecting)) // the link is being set up (autoConnect)
        assertEquals(ConnectionAction.CancelConnecting, connectionAction(deviceConnectionStatus(controls.sessions.value[ID]), model.pending.value))
        calls.clear()

        model.cancelConnecting("AA:BB")

        assertEquals(listOf("enabled=false", "observe AA:BB false", "disconnect AA:BB"), calls)
        assertFalse(source.enabled)
        assertEquals(PendingConnection.Disconnecting, model.pending.value)
        assertEquals(PhotoTransferEntry.Idle, model.photoTransfer.value)
        assertFalse(calls.any { it.startsWith("connect") })

        controls.set(session(BleSessionPhase.Disconnected))
        assertEquals(PendingConnection.None, model.pending.value)
    }

    @Test
    fun liveStatusAndSignalMonitoringUseTheNormalisedAddress() = withMain {
        val observed = mutableListOf<String>()
        val released = mutableListOf<String>()
        val status = CameraLiveStatus(address = ID, phase = BleSessionPhase.Transmitting)
        val live = object : DeviceLiveStatusSource {
            override fun observe(address: String): Flow<CameraLiveStatus?> {
                observed += address
                return flowOf(status)
            }

            override fun acquireSignalMonitoring(address: String): () -> Unit = { released += address }
        }
        val model = DeviceDetailViewModel(Flags(enabled = true), Actions(mutableListOf()), Controls(emptyMap()), live)

        assertEquals(status, model.liveStatus("aa:bb").first())
        val release = model.acquireSignalMonitoring("aa:bb")
        assertEquals(emptyList(), released)
        release()
        assertEquals(listOf(ID), observed)
        assertEquals(listOf(ID), released)
    }

    private fun session(phase: BleSessionPhase) = CameraSession(ID, phase = phase)

    private fun model(calls: MutableList<String>, controls: Controls) =
        DeviceDetailViewModel(Flags(enabled = true, log = calls), Actions(calls), controls)

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

    private class Controls(initial: Map<String, CameraSession>) : CameraAutoCorrectionControls {
        private val state = MutableStateFlow(initial)
        override val sessions: StateFlow<Map<String, CameraSession>> = state
        fun set(session: CameraSession) { state.value = state.value + (session.identifier to session) }
        override fun refreshAutoCorrectionSettings(identifier: String) = Unit
        override fun setAutoCorrectionSetting(identifier: String, setting: CameraAutoCorrectionSetting, enabled: Boolean) = Unit
    }

    private class Actions(private val log: MutableList<String>) : DeviceDetailServiceActions {
        override fun startAlwaysOn(deviceAddress: String) { log += "startAlwaysOn" }
        override fun requestShutdown(deviceAddress: String) { log += "shutdown" }
        override fun connect(deviceAddress: String) { log += "connect $deviceAddress" }
        override fun disconnect(deviceAddress: String) { log += "disconnect $deviceAddress" }
        override fun setPresenceObservation(deviceAddress: String, enabled: Boolean) { log += "observe $deviceAddress $enabled" }
    }

    private class Flags(
        var enabled: Boolean,
        private val log: MutableList<String> = mutableListOf(),
    ) : DeviceDetailDataSource {
        override suspend fun ensureDeviceExists(deviceId: String, deviceName: String?) = Unit
        override suspend fun isDeviceEnabled(deviceId: String) = enabled
        override suspend fun isAlwaysOnEnabled(deviceId: String) = false
        override suspend fun setDeviceEnabled(deviceId: String, enabled: Boolean) { this.enabled = enabled; log += "enabled=$enabled" }
        override suspend fun setAlwaysOnEnabled(deviceId: String, enabled: Boolean) = Unit
        override suspend fun getDeviceName(deviceId: String): String? = null
        override suspend fun setDeviceName(deviceId: String, name: String) = Unit
    }

    private companion object {
        const val ID = "AA:BB"
    }
}
