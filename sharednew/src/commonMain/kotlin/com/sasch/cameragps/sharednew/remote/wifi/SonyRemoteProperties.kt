package com.sasch.cameragps.sharednew.remote.wifi

import kotlin.math.abs

/**
 * Known Sony/PTP shooting properties exposed in the Wi-Fi live-view remote screen.
 * Ordered by priority: core exposure first, then focus/metering/flash/drive.
 */
enum class CameraShootingPropertyKey(val code: Int, val primaryBar: Boolean) {
    ShutterSpeed(0xd20d, primaryBar = true),
    Aperture(0x5007, primaryBar = true),
    Iso(0xd21e, primaryBar = true),
    ExposureBias(0x5010, primaryBar = true),
    WhiteBalance(0x5005, primaryBar = true),
    ExposureProgram(0x500e, primaryBar = false),
    FocusMode(0x500a, primaryBar = false),
    MeteringMode(0x500b, primaryBar = false),
    FlashMode(0x500c, primaryBar = false),
    DriveMode(0x5013, primaryBar = false),
    ;

    companion object {
        const val SONY_BATTERY_LEVEL = 0xd218
        const val SONY_MEDIA_SLOT1_REMAINING_SHOTS = 0xd249
        val allCodes: Set<Int> = entries.mapTo(mutableSetOf()) { it.code }
        val readCodes: Set<Int> = allCodes + setOf(SONY_BATTERY_LEVEL, SONY_MEDIA_SLOT1_REMAINING_SHOTS)
        fun byCode(code: Int): CameraShootingPropertyKey? = entries.firstOrNull { it.code == code }
    }
}

data class CameraPropertyOption(
    val rawValue: Long,
    val label: String,
)

data class CameraShootingProperty(
    val key: CameraShootingPropertyKey,
    val dataType: Int,
    val rawValue: Long?,
    val displayValue: String,
    val writable: Boolean,
    val options: List<CameraPropertyOption> = emptyList(),
)

enum class PropertyWriteStatus { Idle, Writing, Applied, Rejected, Unsupported, Failed }

data class CameraPropertiesSnapshot(
    val properties: List<CameraShootingProperty>,
    val batteryPercent: Int? = null,
    val remainingShots: Int? = null,
)

data class CameraPropertiesState(
    val properties: List<CameraShootingProperty> = emptyList(),
    val batteryPercent: Int? = null,
    val remainingShots: Int? = null,
    val canWrite: Boolean = false,
    val loading: Boolean = false,
    val writingKey: CameraShootingPropertyKey? = null,
    val writeStatus: PropertyWriteStatus = PropertyWriteStatus.Idle,
) {
    fun byKey(key: CameraShootingPropertyKey): CameraShootingProperty? =
        properties.firstOrNull { it.key == key }
}

internal object SonyRemotePropertyFormatter {
    private const val MAX_RANGE_STEPS = 64

    fun toSnapshot(
        raw: Map<Int, SonyCameraProperty>,
        supportsWriteOp: Boolean,
    ): CameraPropertiesSnapshot {
        val properties = toShootingProperties(raw, supportsWriteOp)
        val battery = raw[CameraShootingPropertyKey.SONY_BATTERY_LEVEL]
            ?.takeIf { it.enabled }?.value?.toInt()?.takeIf { it in 0..100 }
        val remaining = raw[CameraShootingPropertyKey.SONY_MEDIA_SLOT1_REMAINING_SHOTS]
            ?.takeIf { it.enabled }?.value?.takeIf { it in 0L..999_999L }?.toInt()
        return CameraPropertiesSnapshot(properties, battery, remaining)
    }

    fun toShootingProperties(
        raw: Map<Int, SonyCameraProperty>,
        supportsWriteOp: Boolean,
    ): List<CameraShootingProperty> = CameraShootingPropertyKey.entries.mapNotNull { key ->
        val prop = raw[key.code] ?: return@mapNotNull null
        if (!prop.enabled) return@mapNotNull null
        val currentRaw = prop.value
        val display = currentRaw?.let { formatValue(key, it) } ?: "—"
        val choiceRaws = when (prop.form) {
            2 -> prop.currentValues
            1 -> expandRange(prop.currentValues, prop.dataType)
            else -> emptyList()
        }
        val options = choiceRaws.distinct().map { CameraPropertyOption(it, formatValue(key, it)) }
        CameraShootingProperty(
            key = key,
            dataType = prop.dataType,
            rawValue = currentRaw,
            displayValue = display,
            writable = supportsWriteOp && prop.access == 1 && prop.enabledState == 1 && options.isNotEmpty(),
            options = options,
        )
    }

    private fun expandRange(range: List<Long>, dataType: Int): List<Long> {
        if (range.size != 3) return emptyList()
        val signed = dataType == 1 || dataType == 3 || dataType == 5 || dataType == 7
        val min = if (signed) signExtend(range[0], dataType) else range[0]
        val max = if (signed) signExtend(range[1], dataType) else range[1]
        val step = range[2]
        if (step <= 0 || min > max) return emptyList()
        val result = mutableListOf<Long>()
        var current = min
        while (current <= max && result.size < MAX_RANGE_STEPS) {
            result += current
            current += step
        }
        return result
    }

    private fun signExtend(value: Long, dataType: Int): Long = when (dataType) {
        1 -> value.toByte().toLong()
        3 -> value.toShort().toLong()
        5 -> value.toInt().toLong()
        else -> value
    }

    fun formatValue(key: CameraShootingPropertyKey, raw: Long): String = when (key) {
        CameraShootingPropertyKey.Aperture -> formatAperture(raw)
        CameraShootingPropertyKey.ShutterSpeed -> formatShutterSpeed(raw)
        CameraShootingPropertyKey.Iso -> formatIso(raw)
        CameraShootingPropertyKey.ExposureBias -> formatExposureBias(raw)
        CameraShootingPropertyKey.ExposureProgram -> formatExposureProgram(raw)
        CameraShootingPropertyKey.WhiteBalance -> formatWhiteBalance(raw)
        CameraShootingPropertyKey.FocusMode -> formatFocusMode(raw)
        CameraShootingPropertyKey.MeteringMode -> formatMeteringMode(raw)
        CameraShootingPropertyKey.FlashMode -> formatFlashMode(raw)
        CameraShootingPropertyKey.DriveMode -> formatDriveMode(raw)
    }

    private fun formatAperture(raw: Long): String {
        if (raw <= 0L || raw == 0xffffL) return "f/—"
        return "f/" + formatTenths(raw, scale = 100)
    }

    private fun formatShutterSpeed(raw: Long): String {
        if (raw == 0L) return "BULB"
        val num = ((raw ushr 16) and 0xffffL).toInt()
        val den = (raw and 0xffffL).toInt()
        if (num == 0 && den == 0) return "BULB"
        if (den == 0) return "0x${raw.toString(16)}"
        if (num == 1 && den > 1) return "1/$den"
        if (den == 10) return formatTenths(num.toLong(), scale = 10) + "\""
        if (den == 1) return "${num}\""
        if (num >= den) {
            val tenths = ((num.toLong() * 10L + den / 2) / den)
            return formatTenths(tenths, scale = 10) + "\""
        }
        return "$num/$den"
    }

    private fun formatIso(raw: Long): String {
        if (raw == 0x00ffffffL) return "AUTO"
        val flag = (raw ushr 24) and 0xffL
        val iso = raw and 0x00ffffffL
        return when (flag) {
            0x10L -> "ISO $iso (L)"
            0x20L -> "MFNR $iso"
            else -> "ISO $iso"
        }
    }

    private fun formatExposureBias(raw: Long): String {
        val milliEv = raw.toShort().toInt()
        if (milliEv == 0) return "0.0 EV"
        val sign = if (milliEv > 0) "+" else "-"
        val tenths = (abs(milliEv) + 50) / 100
        return "$sign${tenths / 10}.${tenths % 10} EV"
    }

    private fun formatExposureProgram(raw: Long): String = when (raw.toInt()) {
        0x00001 -> "M"
        0x00002, 0x10002 -> "P"
        0x00003, 0x20003 -> "A"
        0x00004, 0x30004 -> "S"
        0x48000, 0x08000 -> "AUTO"
        0x48001, 0x08001 -> "iAUTO+"
        0x78050 -> "MR 1"
        0x78051 -> "MR 2"
        0x78052 -> "MR 3"
        0x78053 -> "MR 4"
        0x80050, 0x98059 -> "Movie P"
        0x80051, 0x9805a -> "Movie A"
        0x80052, 0x9805b -> "Movie S"
        0x80053, 0x9805c -> "Movie M"
        else -> "0x${raw.toString(16)}"
    }

    private fun formatWhiteBalance(raw: Long): String = when (raw.toInt()) {
        0x0002 -> "AWB"
        0x0004 -> "Daylight"
        0x8011 -> "Shade"
        0x8010 -> "Cloudy"
        0x0006 -> "Incandescent"
        0x8001 -> "Fluor Warm"
        0x8002 -> "Fluor Cool"
        0x8003 -> "Fluor Day W"
        0x8004 -> "Fluor Day"
        0x0007 -> "Flash"
        0x8030 -> "Underwater"
        0x8012 -> "C.Temp"
        0x8020 -> "Custom 1"
        0x8021 -> "Custom 2"
        0x8022 -> "Custom 3"
        else -> "0x${raw.toString(16)}"
    }

    private fun formatFocusMode(raw: Long): String = when (raw.toInt()) {
        0x0001 -> "MF"
        0x0002 -> "AF-S"
        0x8004 -> "AF-C"
        0x8005 -> "AF-A"
        0x8006 -> "DMF"
        else -> "0x${raw.toString(16)}"
    }

    private fun formatMeteringMode(raw: Long): String = when (raw.toInt()) {
        0x8001 -> "Multi"
        0x8002 -> "Center"
        0x8003 -> "Avg"
        0x8004 -> "Spot Std"
        0x8005 -> "Spot L"
        0x8006 -> "Highlight"
        else -> "0x${raw.toString(16)}"
    }

    private fun formatFlashMode(raw: Long): String = when (raw.toInt()) {
        0x0000, 0x0002 -> "Off"
        0x0001 -> "Auto"
        0x0003 -> "Fill"
        0x8001 -> "Slow Sync"
        0x8003 -> "Rear Sync"
        0x8004 -> "Wireless"
        else -> "0x${raw.toString(16)}"
    }

    private fun formatDriveMode(raw: Long): String = when (raw.toInt()) {
        0x00001 -> "Single"
        0x00002, 0x10002 -> "Cont Lo"
        0x18015 -> "Cont Mid"
        0x18010 -> "Cont Hi"
        0x18012 -> "Cont Hi+"
        0x38004 -> "Self 10s"
        0x38003 -> "Self 5s"
        0x38005 -> "Self 2s"
        else -> "0x${raw.toString(16)}"
    }

    private fun formatTenths(raw: Long, scale: Int): String {
        val tenths = (raw * 10L + scale / 2) / scale
        val whole = tenths / 10
        val frac = tenths % 10
        return if (frac == 0L) "$whole" else "$whole.$frac"
    }
}
