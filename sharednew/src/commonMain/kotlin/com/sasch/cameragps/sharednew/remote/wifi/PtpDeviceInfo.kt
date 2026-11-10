package com.sasch.cameragps.sharednew.remote.wifi

/**
 * The public capability lists in the standard PTP DeviceInfo dataset: OperationsSupported and
 * DevicePropertiesSupported (standard DevicePropCodes such as BatteryLevel 0x5001).
 */
internal data class PtpDeviceInfo(
    val supportedOperations: Set<Int>,
    val supportedProperties: Set<Int> = emptySet(),
) {
    fun supports(code: Int): Boolean = code in supportedOperations
    fun supportsProperty(code: Int): Boolean = code in supportedProperties
}

internal object SonyPtpOperation {
    const val GET_DEVICE_INFO = 0x1001
    const val OPEN_SESSION = 0x1002
    const val CLOSE_SESSION = 0x1003
    const val SDIO_CONNECT = 0x9201
    const val SDIO_GET_EXT_DEVICE_INFO = 0x9202
    const val SDIO_CONTROL_DEVICE = 0x9207
    const val SDIO_OPEN_SESSION = 0x9210
    const val SDIO_GET_VENDOR_CODE_VERSION = 0x9216
    const val SDIO_GET_DEVICE_DESCRIPTION_FILE = 0x923a
    const val GET_DEVICE_PROP_VALUE = 0x1015
    const val SDIO_GET_ALL_EXT_DEVICE_PROP_INFO = 0x9209
}

/** Standard PTP DevicePropCode for the battery percentage (UINT8, 0..100, read-only). */
internal const val PTP_DEVICE_PROP_BATTERY_LEVEL = 0x5001

/**
 * Parses OperationsSupported, then (tolerantly) EventsSupported and DevicePropertiesSupported.
 * The trailing strings (manufacturer, model, version, serial number) are never read; a truncated
 * or implausible property list yields an empty property set instead of failing the parse.
 */
internal object PtpDeviceInfoParser {
    fun parse(data: ByteArray): PtpDeviceInfo {
        var offset = 0

        fun take(size: Int): Int {
            require(size >= 0 && size <= data.size - offset) { "Malformed PTP DeviceInfo" }
            val start = offset
            offset += size
            return start
        }

        fun uint8(): Int = data[take(1)].toInt() and 0xff
        fun uint32(): Long {
            val start = take(4)
            return (0..3).fold(0L) { value, i ->
                value or ((data[start + i].toLong() and 0xff) shl (8 * i))
            }
        }

        fun parseCount(elementBytes: Int): Int {
            val count = uint32()
            require(count <= 4096 && count <= (data.size - offset) / elementBytes) { "Malformed PTP array count" }
            return count.toInt()
        }

        take(8) // PTP version, vendor extension ID and version
        take(uint8() * 2) // PTP string: vendor extension description
        take(2) // functional mode
        val count = uint32()
        require(count <= 4096 && count <= (data.size - offset) / 2) { "Malformed PTP operation count" }
        val operations = buildSet {
            repeat(count.toInt()) {
                val start = take(2)
                add((data[start].toInt() and 0xff) or ((data[start + 1].toInt() and 0xff) shl 8))
            }
        }
        val properties = try {
            take(parseCount(2) * 2) // EventsSupported
            val propertyCount = parseCount(2)
            buildSet {
                repeat(propertyCount) {
                    val start = take(2)
                    add((data[start].toInt() and 0xff) or ((data[start + 1].toInt() and 0xff) shl 8))
                }
            }
        } catch (_: IllegalArgumentException) {
            emptySet()
        }
        return PtpDeviceInfo(operations, properties)
    }
}
