package com.sasch.cameragps.sharednew.bluetooth.status

import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import com.sasch.cameragps.sharednew.bluetooth.transport.BleOperationStatus
import com.sasch.cameragps.sharednew.bluetooth.transport.BlePeripheralTransport
import com.sasch.cameragps.sharednew.bluetooth.transport.BleTransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RssiMonitorTest {
    private class Transport : BlePeripheralTransport {
        override val events = emptyFlow<BleTransportEvent>()
        var requests = 0
        var accept = true
        override fun isConnected(identifier: String) = true
        override fun hasCharacteristic(identifier: String, characteristicUuid: String) = true
        override fun supportsWriteWithResponse(identifier: String, characteristicUuid: String) = true
        override fun initiateWrite(identifier: String, characteristicUuid: String, value: ByteArray) = true
        override fun initiateRead(identifier: String, characteristicUuid: String) = true
        override fun initiateSubscribe(identifier: String, characteristicUuid: String, enable: Boolean) = true
        override fun initiateDiscoverServices(identifier: String) = true
        override fun initiateReadRssi(identifier: String): Boolean {
            requests++
            return accept
        }
    }

    private class Fixture(scope: CoroutineScope, maxFailures: Int = 3) {
        val registry = CameraSessionRegistry()
        val transport = Transport()
        val reports = mutableListOf<Int?>()
        val monitor = RssiMonitor(
            transport, registry, scope, report = { _, rssi -> reports += rssi },
            intervalMs = 10_000, readTimeoutMs = 5_000, maxFailures = maxFailures,
        )

        fun ready() = registry.upsert("A") { it.copy(phase = BleSessionPhase.Transmitting) }
        fun answer(rssi: Int, status: BleOperationStatus = BleOperationStatus.Success) =
            monitor.onEvent(BleTransportEvent.RssiRead("A", rssi, status))
    }

    @Test
    fun noLeaseMeansNoTraffic() = runTest {
        val f = Fixture(backgroundScope)
        f.ready()
        advanceTimeBy(60_000)
        assertEquals(0, f.transport.requests)
    }

    @Test
    fun readsImmediatelyThenEveryTenSecondsWhileLeased() = runTest {
        val f = Fixture(backgroundScope)
        f.ready()
        val lease = f.monitor.acquire("a")
        runCurrent()
        assertEquals(1, f.transport.requests)
        f.answer(-58)
        runCurrent()
        assertEquals(listOf<Int?>(-58), f.reports)
        advanceTimeBy(9_999)
        assertEquals(1, f.transport.requests)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, f.transport.requests)
        f.answer(-72)
        runCurrent()
        assertEquals(listOf<Int?>(-58, -72), f.reports)
        lease.release()
        advanceTimeBy(60_000)
        assertEquals(2, f.transport.requests)
    }

    @Test
    fun doesNotPollUntilTheSessionIsTransmitting() = runTest {
        val f = Fixture(backgroundScope)
        f.registry.upsert("A") { it.copy(phase = BleSessionPhase.SyncingTime) }
        f.monitor.acquire("A")
        advanceTimeBy(30_000)
        assertEquals(0, f.transport.requests)
        f.ready()
        runCurrent()
        assertEquals(1, f.transport.requests)
    }

    @Test
    fun leasesAreReferenceCountedAndReleaseIsIdempotent() = runTest {
        val f = Fixture(backgroundScope)
        f.ready()
        val first = f.monitor.acquire("A")
        val second = f.monitor.acquire("A")
        assertEquals(2, f.monitor.leaseCount("A"))
        first.release()
        first.release()
        assertEquals(1, f.monitor.leaseCount("A"))
        runCurrent()
        f.answer(-60)
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(2, f.transport.requests) // still polling for the second consumer
        second.release()
        assertEquals(0, f.monitor.leaseCount("A"))
        advanceTimeBy(60_000)
        assertEquals(2, f.transport.requests)
    }

    @Test
    fun failuresAreSwallowedAndReportedAsUnknownOnlyAfterRepeatedFailures() = runTest {
        val f = Fixture(backgroundScope, maxFailures = 2)
        f.ready()
        f.monitor.acquire("A")
        runCurrent()
        f.answer(0, BleOperationStatus.Failure) // error status → no value
        runCurrent()
        assertTrue(f.reports.isEmpty())
        advanceTimeBy(10_001) // second read gets no answer at all → timeout after 5 s
        runCurrent()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(listOf<Int?>(null), f.reports)
        // A later good reading recovers.
        advanceTimeBy(10_001)
        runCurrent()
        f.answer(-65)
        runCurrent()
        assertEquals(listOf(null, -65), f.reports)
    }

    @Test
    fun refusedRequestsKeepTheLoopAliveWithoutBlocking() = runTest {
        val f = Fixture(backgroundScope)
        f.ready()
        f.transport.accept = false
        f.monitor.acquire("A")
        runCurrent()
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(2, f.transport.requests)
        f.transport.accept = true
        advanceTimeBy(10_001)
        runCurrent()
        f.answer(-90)
        runCurrent()
        assertEquals(-90, f.reports.last())
    }

    @Test
    fun unsolicitedAnswersAreIgnoredAndShutdownStopsEverything() = runTest {
        val f = Fixture(backgroundScope)
        f.answer(-50) // no lease at all
        assertTrue(f.reports.isEmpty())
        f.ready()
        f.monitor.acquire("A")
        runCurrent()
        f.monitor.shutdown()
        advanceTimeBy(60_000)
        assertEquals(1, f.transport.requests)
    }

    @Test
    fun intervalBelowTheFloorIsRejected() = runTest {
        val f = Fixture(backgroundScope)
        assertFailsWith<IllegalArgumentException> {
            RssiMonitor(f.transport, f.registry, backgroundScope, { _, _ -> }, intervalMs = 500)
        }
    }
}
