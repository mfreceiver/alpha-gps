package com.sasch.cameragps.sharednew.remote.wifi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SonyBatteryReaderTest : WifiLoggingTest() {
    private fun number(value: Long, size: Int) = ByteArray(size) { (value ushr (8 * it)).toByte() }

    private class Transport(
        val data: (Int) -> ByteArray?,
        val responseCode: Int = 0x2001,
    ) : PtpIpCommandTransport {
        val replies = Channel<PtpIpPacket>(Channel.UNLIMITED)
        val operations = mutableListOf<Pair<Int, List<Long>>>()
        override suspend fun receive() = replies.receive()
        override suspend fun send(packet: PtpIpPacket) {
            if (packet.type != 6) return
            fun read(offset: Int, size: Int) = (0 until size).fold(0L) { n, i ->
                n or ((packet.body[offset + i].toLong() and 255) shl (8 * i))
            }
            val id = read(6, 4).toInt()
            val code = read(4, 2).toInt()
            operations += code to (10 until packet.body.size step 4).map { read(it, 4) }
            val payload = data(code)
            if (payload != null && responseCode == 0x2001) {
                replies.send(PtpIpOperations.startData(id, payload.size))
                replies.send(PtpIpOperations.endData(id, payload))
            }
            replies.send(
                PtpIpPacket(
                    7,
                    byteArrayOf(responseCode.toByte(), (responseCode shr 8).toByte()) +
                            byteArrayOf(id.toByte(), (id shr 8).toByte(), 0, 0)
                )
            )
        }
    }

    private fun ready(deviceProps: Set<Int>, extProps: Set<Int>, ops: Set<Int> = setOf(0x1015)) =
        SonyPtpInitializationResult.Ready(
            "4.00", 310,
            SonyExtendedDeviceInfo(300, extProps, emptySet()),
            PtpDeviceInfo(ops, deviceProps), null,
        )

    /** One 0x9209 property record: code, type, access, enabled, factory value, current value, form 0. */
    private fun sonyProperty(code: Int, type: Int, current: Int) =
        number(1, 8) + number(code.toLong(), 2) + number(type.toLong(), 2) +
                byteArrayOf(0, 1) + byteArrayOf(0) + byteArrayOf(current.toByte()) + byteArrayOf(0)

    @Test
    fun readsStandardBatteryLevelWhenAdvertised() = runTest {
        val transport = Transport({ if (it == 0x1015) byteArrayOf(87) else null })
        val queue = PtpIpCommandQueue(transport, backgroundScope)
        val reader = SonyBatteryReader(queue, ready(setOf(0x5001), emptySet()))
        assertTrue(reader.available)
        assertEquals(87, reader.read())
        assertEquals(listOf(0x1015 to listOf(0x5001L)), transport.operations)
        queue.close()
    }

    @Test
    fun fallsBackToTheSonyExtensionPropertyWhenStandardOneIsMissing() = runTest {
        val transport = Transport({ if (it == 0x9209) sonyProperty(0xd218, 1, 64) else null })
        val queue = PtpIpCommandQueue(transport, backgroundScope)
        val reader = SonyBatteryReader(queue, ready(emptySet(), setOf(0xd218)))
        assertTrue(reader.available)
        assertEquals(64, reader.read())
        assertEquals(0x9209, transport.operations.single().first)
        queue.close()
    }

    @Test
    fun noAdvertisedSourceMeansNoTrafficAndUnknown() = runTest {
        val transport = Transport({ null })
        val queue = PtpIpCommandQueue(transport, backgroundScope)
        val reader = SonyBatteryReader(queue, ready(setOf(0x5003), setOf(0xd295)))
        assertFalse(reader.available)
        assertNull(reader.read())
        assertTrue(transport.operations.isEmpty())
        queue.close()
    }

    @Test
    fun outOfRangeAndRefusedAnswersAreUnknownNotErrors() = runTest {
        for (bad in listOf(byteArrayOf(101), byteArrayOf(-1), byteArrayOf(), byteArrayOf(1, 2))) {
            val queue = PtpIpCommandQueue(Transport({ bad }), backgroundScope)
            assertNull(SonyBatteryReader(queue, ready(setOf(0x5001), emptySet())).read())
            queue.close()
        }
        val refused = PtpIpCommandQueue(Transport({ byteArrayOf(50) }, responseCode = 0x2019), backgroundScope)
        assertNull(SonyBatteryReader(refused, ready(setOf(0x5001), emptySet())).read())
        refused.close()
    }

    @Test
    fun sonyUnknownMarkerMinusOneIsNotAPercentage() {
        assertNull(SonyBatteryReader.sonyPercent(255)) // INT8 -1 arrives as 0xFF
        assertNull(SonyBatteryReader.sonyPercent(null))
        assertEquals(0, SonyBatteryReader.sonyPercent(0))
        assertEquals(100, SonyBatteryReader.sonyPercent(100))
        assertNull(SonyBatteryReader.sonyPercent(101))
    }

    @Test
    fun timeoutIsSwallowedAsUnknown() = runTest {
        val transport = object : PtpIpCommandTransport {
            override suspend fun receive(): PtpIpPacket = kotlinx.coroutines.awaitCancellation()
            override suspend fun send(packet: PtpIpPacket) = Unit
        }
        val queue = PtpIpCommandQueue(transport, backgroundScope)
        val result = async { SonyBatteryReader(queue, ready(setOf(0x5001), emptySet())).read() }
        advanceTimeBy(20_000)
        assertNull(result.await())
        queue.close()
    }
}
