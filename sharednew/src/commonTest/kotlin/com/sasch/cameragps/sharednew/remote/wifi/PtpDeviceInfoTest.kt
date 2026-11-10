package com.sasch.cameragps.sharednew.remote.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PtpDeviceInfoTest {
    @Test
    fun readsSonyOperationCodesWithoutParsingPrivateFields() {
        val bytes = byteArrayOf(
            100, 0, 6, 0, 0, 0, 1, 0, // PTP and vendor versions
            1, 0, 0, // empty extension description
            0, 0, // functional mode
            3, 0, 0, 0, // operation count
            1, 16, 1, 0x92.toByte(), 0x3a, 0x92.toByte(),
            0xff.toByte(), 0xff.toByte(), // ignored private trailing fields
        )
        val info = PtpDeviceInfoParser.parse(bytes)
        assertEquals(3, info.supportedOperations.size)
        assertTrue(info.supports(SonyPtpOperation.GET_DEVICE_INFO))
        assertTrue(info.supports(SonyPtpOperation.SDIO_CONNECT))
        assertTrue(info.supports(SonyPtpOperation.SDIO_GET_DEVICE_DESCRIPTION_FILE))
        assertFalse(info.supports(SonyPtpOperation.SDIO_CONTROL_DEVICE))
    }

    @Test
    fun rejectsTruncatedAndOversizedOperationLists() {
        assertFailsWith<IllegalArgumentException> { PtpDeviceInfoParser.parse(ByteArray(8)) }
        val prefix = byteArrayOf(100, 0, 6, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0)
        assertFailsWith<IllegalArgumentException> {
            PtpDeviceInfoParser.parse(prefix + byteArrayOf(0xff.toByte(), 0xff.toByte(), 0, 0))
        }
    }

    private fun u16(vararg values: Int) = values.flatMap { listOf(it.toByte(), (it shr 8).toByte()) }.toByteArray()
    private fun u32(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte(), 0, 0)

    @Test
    fun readsDevicePropertiesSupportedAfterTheEventsList() {
        val bytes = byteArrayOf(100, 0, 6, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0) +
                u32(1) + u16(0x1001) + // operations
                u32(2) + u16(0x4002, 0x4003) + // events
                u32(3) + u16(0x5001, 0x5003, 0xd218) + // device properties
                byteArrayOf(1, 0, 0) // trailing private strings are never read
        val info = PtpDeviceInfoParser.parse(bytes)
        assertEquals(setOf(0x5001, 0x5003, 0xd218), info.supportedProperties)
        assertTrue(info.supportsProperty(0x5001))
        assertFalse(info.supportsProperty(0x5002))
    }

    @Test
    fun truncatedOrImplausiblePropertyListsKeepTheOperationsAndYieldNoProperties() {
        val head = byteArrayOf(100, 0, 6, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0) + u32(1) + u16(0x1001)
        for (tail in listOf(
            byteArrayOf(),
            u32(1) + u16(0x4002) + u32(2) + u16(0x5001), // property list cut short
            u32(0xffff) + u16(0x4002), // event count larger than the data
        )) {
            val info = PtpDeviceInfoParser.parse(head + tail)
            assertTrue(info.supports(0x1001))
            assertTrue(info.supportedProperties.isEmpty())
        }
    }
}
