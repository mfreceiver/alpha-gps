package com.sasch.cameragps.sharednew.remote.wifi

/** Key of the group collecting captures whose camera-recorded date is missing or malformed. */
internal const val UNKNOWN_DATE_KEY = "unknown"

/** One calendar day of the card catalog; [count] is in captures (JPG+RAW pair = 1). */
data class DateGroup(val key: String, val count: Int) {
    val isUnknown: Boolean get() = key == UNKNOWN_DATE_KEY
}

// Full date-time stamps, strictly: the legacy PTP form `YYYYMMDDThhmmss` and the v4 catalog
// ISO form `YYYY-MM-DDThh:mm[:ss]` (LocalDateTime serializes without seconds when they are
// zero). Both tolerate a fractional-second part and a trailing zone marker, which is never
// converted — only its numeric plausibility is checked.
private val legacyDateTime =
    Regex("""(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(?:\.\d+)?(Z|[+-]\d{2}:?\d{2})?""")
private val isoDateTime =
    Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?(Z|[+-]\d{2}:\d{2})?""")

private fun isLeap(year: Int) = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

private fun daysIn(year: Int, month: Int) = when (month) {
    2 -> if (isLeap(year)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

/**
 * The camera-recorded local calendar day as `YYYY-MM-DD`, or null when the value is absent or
 * not a strictly valid date-time. Both the legacy PTP form `YYYYMMDDThhmmss` and the v4 catalog
 * ISO form `YYYY-MM-DDThh:mm[:ss]` (seconds optional, as LocalDateTime emits them) are
 * understood; a fractional-second part and a trailing `Z`/numeric zone marker are tolerated.
 * No time-zone conversion is ever applied. Anything else — impossible clock values included —
 * is not a camera date and lands in the unknown group.
 */
internal fun cameraDateKey(capturedAt: String): String? {
    fun dateKey(year: Int, month: Int, day: Int): String? {
        if (year !in 1900..9999 || month !in 1..12 || day !in 1..daysIn(year, month)) return null
        return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-" +
                day.toString().padStart(2, '0')
    }

    fun validTime(hour: Int, minute: Int, second: Int?): Boolean =
        hour < 24 && minute < 60 && (second == null || second < 60)

    fun plausibleOffset(offset: String?): Boolean {
        if (offset == null || offset == "Z") return true
        val digits = offset.filter { it.isDigit() }
        return digits.take(2).toInt() < 24 && digits.substring(2).toInt() < 60
    }

    legacyDateTime.matchEntire(capturedAt)?.let { m ->
        return if (
            validTime(m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt()) &&
            plausibleOffset(m.groups[7]?.value)
        ) {
            dateKey(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        } else {
            null
        }
    }
    isoDateTime.matchEntire(capturedAt)?.let { m ->
        return if (
            validTime(m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groups[6]?.value?.toInt()) &&
            plausibleOffset(m.groups[7]?.value)
        ) {
            dateKey(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        } else {
            null
        }
    }
    return null
}

/**
 * Date of the whole capture: the preview file's recorded date wins, so a JPG+RAW pair with
 * conflicting stamps is never split; only when it is unusable the other files are consulted.
 */
internal val WifiCameraCapture.dateKey: String
    get() = previewCandidates.firstNotNullOfOrNull { cameraDateKey(it.capturedAt) } ?: UNKNOWN_DATE_KEY

/**
 * Full, already enumerated card catalog indexed by camera-local day. Both the legacy PTP object list
 * and the v4 content catalog are enumerated completely before this is built, so grouping by date
 * costs no further camera traffic. Pure: shared by every protocol path.
 */
internal class CatalogSnapshot private constructor(
    val captures: List<WifiCameraCapture>,
    private val byDate: Map<String, List<WifiCameraCapture>>
) {
    /** Newest day first, the unknown-date group always last. */
    val dates: List<DateGroup> = byDate.map { (key, list) -> DateGroup(key, list.size) }
        .sortedWith(compareBy<DateGroup> { it.isUnknown }.thenByDescending { it.key })

    fun capturesOn(dateKey: String): List<WifiCameraCapture>? = byDate[dateKey]

    /** A slice of one day; the page's photos are what the session must treat as the displayed page. */
    fun page(dateKey: String, offset: Int): CameraPhotoPage =
        cameraPhotoPage(requireNotNull(byDate[dateKey]) { "Unknown catalog date" }, offset)

    companion object {
        val EMPTY = CatalogSnapshot(emptyList(), emptyMap())

        /** [captures] keep their given (newest-first) order inside each day. */
        fun of(captures: List<WifiCameraCapture>): CatalogSnapshot =
            CatalogSnapshot(captures, captures.groupBy { it.dateKey })
    }
}
