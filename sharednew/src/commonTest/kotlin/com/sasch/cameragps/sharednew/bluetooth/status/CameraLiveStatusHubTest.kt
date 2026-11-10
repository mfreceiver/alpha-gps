package com.sasch.cameragps.sharednew.bluetooth.status

import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CameraLiveStatusHubTest {
    private class Fixture(scope: CoroutineScope, persistence: CameraStatusPersistence = CameraStatusPersistence.None) {
        val registry = CameraSessionRegistry()
        var now = 1_000L
        val hub = CameraLiveStatusHub(registry, scope, clock = { now }, persistence = persistence)
    }

    private class MemoryPersistence(val stored: MutableMap<String, Long> = mutableMapOf()) : CameraStatusPersistence {
        val saves = mutableListOf<Pair<String, Long>>()
        override fun loadLastWriteAt(address: String) = stored[address]
        override fun saveLastWriteAt(address: String, epochMillis: Long) {
            stored[address] = epochMillis
            saves += address to epochMillis
        }
    }

    @Test
    fun queuedAndWrittenAreSeparateCountersAndFailuresAreTracked() = runTest {
        val f = Fixture(backgroundScope)
        f.hub.onSessionStarted("a")
        repeat(3) { f.hub.onGpsQueued("a") }
        f.now = 2_000
        f.hub.onGpsWriteResult("A", success = true)
        f.now = 3_000
        f.hub.onGpsWriteResult("A", success = false)
        runCurrent()
        val gps = f.hub.statusOf("a")!!.gps
        assertEquals(3, gps.queuedCount)
        assertEquals(1, gps.writtenCount)
        assertEquals(1, gps.failedCount)
        assertEquals(2_000L, gps.lastWriteAtMillis)
        assertEquals(3_000L, gps.lastFailureAtMillis)
        assertEquals(2_000L, f.hub.statusOf("a")!!.lastWriteAtMillis)
    }

    @Test
    fun statusJoinsRegistryPhaseAndMtuAndSurvivesDisconnect() = runTest {
        val f = Fixture(backgroundScope)
        f.registry.upsert("A") { it.copy(phase = BleSessionPhase.Transmitting, mtu = 247) }
        f.hub.onGpsWriteResult("A", true)
        runCurrent()
        val live = f.hub.observe("a").first()!!
        assertEquals(BleSessionPhase.Transmitting, live.phase)
        assertEquals(247, live.mtu)
        assertTrue(live.connected)

        f.hub.onSessionEnded("A")
        f.registry.remove("A")
        runCurrent()
        val after = f.hub.statusOf("A")!!
        assertEquals(BleSessionPhase.Disconnected, after.phase)
        assertNull(after.mtu)
        assertEquals(1, after.gps.writtenCount)
        assertEquals(1_000L, after.lastWriteAtMillis)
    }

    @Test
    fun newSessionResetsCountersSignalAndTimeSyncButKeepsTimestampsAndBattery() = runTest {
        val f = Fixture(backgroundScope)
        f.hub.onSessionStarted("A")
        f.hub.onGpsQueued("A")
        f.hub.onGpsWriteResult("A", true)
        f.hub.onRssi("A", -55)
        f.hub.onTimeSync("A", TimeSyncOutcome.Applied)
        f.hub.onBattery("A", 64)
        runCurrent()
        assertEquals(SignalLevel.Excellent, f.hub.statusOf("A")!!.signalLevel)

        f.now = 9_000
        f.hub.onSessionStarted("A")
        runCurrent()
        val s = f.hub.statusOf("A")!!
        assertEquals(0, s.gps.queuedCount)
        assertEquals(0, s.gps.writtenCount)
        assertEquals(1_000L, s.gps.lastWriteAtMillis)
        assertNull(s.rssiDbm)
        assertEquals(SignalLevel.Unknown, s.signalLevel)
        assertEquals(TimeSyncOutcome.NotRun, s.timeSync.outcome)
        assertEquals(64, s.battery?.percent)
    }

    @Test
    fun timeSyncRecordsOutcomeTimestampAndRejectReason() = runTest {
        val f = Fixture(backgroundScope)
        f.now = 5_000
        f.hub.onTimeSync("A", TimeSyncOutcome.Rejected, 3)
        runCurrent()
        assertEquals(TimeSyncStatus(TimeSyncOutcome.Rejected, 5_000, 3), f.hub.statusOf("A")!!.timeSync)
    }

    @Test
    fun interruptedTimeSyncBecomesUnconfirmedWhenTheLinkEnds() = runTest {
        val f = Fixture(backgroundScope)
        f.hub.onTimeSync("A", TimeSyncOutcome.InProgress)
        f.hub.onSessionEnded("A")
        runCurrent()
        assertEquals(TimeSyncOutcome.Unconfirmed, f.hub.statusOf("A")!!.timeSync.outcome)
    }

    @Test
    fun batteryKeepsObtainedAtAndCameraId() = runTest {
        val f = Fixture(backgroundScope)
        f.now = 42_000
        f.hub.onBattery("aa:bb", 150) // out-of-range input is clamped, never stored as >100
        runCurrent()
        assertEquals(CameraBattery(100, 42_000, "AA:BB"), f.hub.statusOf("AA:BB")!!.battery)
    }

    @Test
    fun persistsLastWriteAtMostOncePerIntervalAndOnDisconnect() = runTest {
        val persistence = MemoryPersistence()
        val f = Fixture(backgroundScope, persistence)
        f.hub.onGpsWriteResult("A", true) // 1_000: first write is saved
        f.now = 10_000
        f.hub.onGpsWriteResult("A", true) // throttled
        f.now = 20_000
        f.hub.onGpsWriteResult("A", true) // throttled
        assertEquals(listOf("A" to 1_000L), persistence.saves)
        f.hub.onSessionEnded("A") // flush the newest
        assertEquals("A" to 20_000L, persistence.saves.last())
        f.now = 20_000 + CameraLiveStatusHub.PERSIST_INTERVAL_MS
        f.hub.onGpsWriteResult("A", true)
        assertEquals("A" to f.now, persistence.saves.last())
    }

    @Test
    fun lastWriteIsSeededFromPersistenceAndFailuresDoNotOverwriteIt() = runTest {
        val persistence = MemoryPersistence(mutableMapOf("A" to 777L))
        val f = Fixture(backgroundScope, persistence)
        f.hub.onSessionStarted("A")
        f.hub.onGpsWriteResult("A", false)
        runCurrent()
        val s = f.hub.statusOf("A")!!
        assertEquals(777L, s.lastWriteAtMillis)
        assertEquals(1_000L, s.gps.lastFailureAtMillis)
        assertTrue(persistence.saves.isEmpty())
    }

    @Test
    fun forgetDropsTheDeviceAndUnknownDevicesAreNull() = runTest {
        val f = Fixture(backgroundScope)
        f.hub.onGpsQueued("A")
        runCurrent()
        f.hub.forget("a")
        runCurrent()
        assertNull(f.hub.statusOf("A"))
        assertEquals(emptyMap(), f.hub.statuses.value)
    }

    @Test
    fun registryOnlyDevicesAppearEvenWithoutTelemetry() = runTest {
        val f = Fixture(backgroundScope)
        f.registry.upsert("B") { it.copy(phase = BleSessionPhase.Connecting) }
        runCurrent()
        val s = f.hub.statusOf("B")!!
        assertEquals(BleSessionPhase.Connecting, s.phase)
        assertEquals(TimeSyncOutcome.NotRun, s.timeSync.outcome)
        assertEquals(0, s.gps.queuedCount)
    }
}
