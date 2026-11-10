package com.sasch.cameragps.sharednew.bluetooth.transport

import com.diamondedge.logging.FixedLogLevel
import com.diamondedge.logging.KmLogging
import com.diamondedge.logging.PlatformLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BleOperationQueueTest {
    private class Transport : BlePeripheralTransport {
        override val events = emptyFlow<BleTransportEvent>()
        val writes = mutableListOf<String>()
        override fun isConnected(identifier: String) = true
        override fun hasCharacteristic(identifier: String, characteristicUuid: String) = true
        override fun supportsWriteWithResponse(identifier: String, characteristicUuid: String) =
            true
        override fun initiateWrite(identifier: String, characteristicUuid: String, value: ByteArray): Boolean {
            writes += characteristicUuid
            return true
        }
        override fun initiateRead(identifier: String, characteristicUuid: String) = true
        override fun initiateSubscribe(identifier: String, characteristicUuid: String, enable: Boolean) = true
        override fun initiateDiscoverServices(identifier: String) = true
    }

    @Test
    fun observerSeesFinalResultsOfStartedOperationsOnly() = runTest {
        val transport = Transport()
        val finished = mutableListOf<Pair<String, BleOperationResult>>()
        val queue = BleOperationQueue(
            transport, backgroundScope,
            shouldExecute = { _, op -> !(op is BleOperation.Write && op.characteristicUuid == "skipped") },
            onOperationFinished = { _, op, result ->
                finished += (op as BleOperation.Write).characteristicUuid to result
            },
        )
        launch { queue.execute("A", BleOperation.Write("skipped", byteArrayOf(1))) }
        launch { queue.execute("A", BleOperation.Write("ok", byteArrayOf(1))) }
        runCurrent()
        queue.onTransportEvent(BleTransportEvent.CharacteristicWritten("A", "ok", BleOperationStatus.Success))
        runCurrent()
        launch { queue.execute("A", BleOperation.Write("bad", byteArrayOf(1))) }
        runCurrent()
        queue.onTransportEvent(BleTransportEvent.CharacteristicWritten("A", "bad", BleOperationStatus.Failure))
        runCurrent()
        launch { queue.execute("A", BleOperation.Write("slow", byteArrayOf(1))) }
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(listOf("ok", "bad", "slow"), finished.map { it.first })
        assertTrue(finished[0].second is BleOperationResult.Success)
        assertTrue(finished[1].second is BleOperationResult.Failure)
        assertEquals(BleOperationResult.Timeout, finished[2].second)
    }

    @Test
    fun observerExceptionsNeverBreakTheLane() = runTest {
        val transport = Transport()
        val queue = BleOperationQueue(
            transport, backgroundScope,
            onOperationFinished = { _, _, _ -> error("observer bug") },
        )
        launch { queue.execute("A", BleOperation.Write("first", byteArrayOf(1))) }
        runCurrent()
        queue.onTransportEvent(BleTransportEvent.CharacteristicWritten("A", "first", BleOperationStatus.Success))
        runCurrent()
        launch { queue.execute("A", BleOperation.Write("second", byteArrayOf(1))) }
        runCurrent()
        assertEquals(listOf("first", "second"), transport.writes)
    }

    @BeforeTest fun silenceLogs() { KmLogging.setLoggers() }
    @AfterTest fun restoreLogs() { KmLogging.setLoggers(PlatformLogger(FixedLogLevel(true))) }

    @Test
    fun cancelledParkedWifiStartNeverExecutesButInflightWriteDrains() = runTest {
        val transport = Transport()
        val queue = BleOperationQueue(transport, backgroundScope)
        val inflight = launch { queue.execute("A", BleOperation.Write("first", byteArrayOf(1))) }
        runCurrent()
        val parked = launch { queue.execute("A", BleOperation.Write("wifi-start", byteArrayOf(1))) }
        runCurrent()
        parked.cancelAndJoin()
        inflight.cancelAndJoin()
        val next = launch { queue.execute("A", BleOperation.Write("next", byteArrayOf(1))) }
        runCurrent()
        assertEquals(listOf("first"), transport.writes)
        queue.onTransportEvent(BleTransportEvent.CharacteristicWritten("A", "first", BleOperationStatus.Success))
        runCurrent()
        assertEquals(listOf("first", "next"), transport.writes)
        queue.onTransportEvent(BleTransportEvent.CharacteristicWritten("A", "next", BleOperationStatus.Success))
        next.join()
    }
}
