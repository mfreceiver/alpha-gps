package com.sasch.cameragps.sharednew.remote.wifi

import com.diamondedge.logging.logging
import kotlinx.coroutines.CancellationException

/**
 * One-shot, best-effort battery percentage read over the PTP/IP session.
 *
 * Sources, in order (each only when the camera advertises it):
 * 1. Standard PTP DeviceProp `BatteryLevel` 0x5001 (UINT8, 0..100) when listed in the DeviceInfo
 *    DevicePropertiesSupported → `GetDevicePropValue` (0x1015).
 * 2. Sony extension property 0xD218 (INT8 percent, -1 = unknown; seen on USB-connected Alpha bodies in
 *    libgphoto2's `PTP_DPC_SONY_BatteryLevel`) when listed by SDIO_GetExtDeviceInfo → read via
 *    SDIO_GetAllExtDevicePropInfo (0x9209) like the other Sony properties.
 *
 * Neither source is verified on a Wi-Fi (PTP/IP) session of real hardware yet; any refusal,
 * malformed data or out-of-range value yields `null` ("unknown") and never an exception, so a
 * camera that does not support it costs at most one rejected command.
 */
internal class SonyBatteryReader(
    private val commands: PtpIpCommandQueue,
    private val capabilities: SonyPtpInitializationResult.Ready,
) {
    private val log = logging("SonyBattery")

    /** Whether the camera advertises any battery source (no traffic). */
    val available: Boolean
        get() = capabilities.deviceInfo.supportsProperty(PTP_DEVICE_PROP_BATTERY_LEVEL) &&
                capabilities.deviceInfo.supports(SonyPtpOperation.GET_DEVICE_PROP_VALUE) ||
                capabilities.extendedInfo.supportsProperty(SONY_BATTERY_LEVEL)

    suspend fun read(): Int? {
        try {
            val info = capabilities.deviceInfo
            if (info.supportsProperty(PTP_DEVICE_PROP_BATTERY_LEVEL) &&
                info.supports(SonyPtpOperation.GET_DEVICE_PROP_VALUE)
            ) {
                standard()?.let { return it }
            }
            if (capabilities.extendedInfo.supportsProperty(SONY_BATTERY_LEVEL)) {
                sony()?.let { return it }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.i { "Battery level unavailable (${failure::class.simpleName})" }
        }
        return null
    }

    private suspend fun standard(): Int? {
        val result = commands.executeDataIn(
            SonyPtpOperation.GET_DEVICE_PROP_VALUE,
            listOf(PTP_DEVICE_PROP_BATTERY_LEVEL.toLong()),
            maxDataBytes = 16,
        ).ensureResponded()
        if (result.response.code != PTP_OK) return null
        return percentFromUint8(result.data)
    }

    private suspend fun sony(): Int? {
        val flags = if ((capabilities.vendorCodeVersion ?: 0) >= 310) listOf(1L) else emptyList()
        val result = commands.executeDataIn(
            SonyPtpOperation.SDIO_GET_ALL_EXT_DEVICE_PROP_INFO,
            listOf(0L) + flags,
            maxDataBytes = 1024 * 1024,
        ).ensureResponded()
        if (result.response.code != PTP_OK) return null
        val data = result.data ?: return null
        val property = SonyCameraProperties.parse(
            data, capabilities.extendedInfo.controlCodes, setOf(SONY_BATTERY_LEVEL),
        )[SONY_BATTERY_LEVEL] ?: return null
        return sonyPercent(property.value)
    }

    companion object {
        /** Sony extension DevicePropCode "BatteryLevel" (INT8 percent). */
        const val SONY_BATTERY_LEVEL = 0xd218
        private const val PTP_OK = 0x2001

        /** UINT8 0..100; anything else (including the 0xFF "unknown") is not a percentage. */
        internal fun percentFromUint8(data: ByteArray?): Int? {
            if (data == null || data.size != 1) return null
            return (data[0].toInt() and 0xff).takeIf { it in 0..100 }
        }

        /** [value] is the property's raw little-endian unsigned byte; INT8 -1 arrives as 255. */
        internal fun sonyPercent(value: Long?): Int? = value?.toInt()?.takeIf { it in 0..100 }
    }
}
