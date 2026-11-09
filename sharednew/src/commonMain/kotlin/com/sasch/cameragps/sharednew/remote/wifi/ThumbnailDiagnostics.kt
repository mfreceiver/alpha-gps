package com.sasch.cameragps.sharednew.remote.wifi

/**
 * Reports the raw thumbnail bytes received from the camera (GET_THUMB or the content
 * catalog), measured from request start until the last byte arrived, before decoding.
 * Used by [WifiRemoteController] for the "AlphaThumb" diagnostics logs.
 */
internal fun interface ThumbnailStatsSink {
    fun onThumbnailBytes(handle: Long, bytes: Int, requestMs: Long)
}

/** One measured thumbnail fetch; [bitmapBytes] is null when decoding produced no bitmap. */
internal data class ThumbnailSample(
    val requestMs: Long,
    val bytes: Long,
    val bitmapBytes: Long? = null,
)

/** Pure aggregation of one browser page's thumbnail fetches into a single log line. */
internal fun thumbnailPageSummary(page: Int, expected: Int, samples: List<ThumbnailSample>): String {
    val requestTotalMs = samples.sumOf { it.requestMs }
    val bytesTotal = samples.sumOf { it.bytes }
    val bitmapEstBytes = samples.mapNotNull { it.bitmapBytes }.sum()
    val avgMs = if (samples.isEmpty()) 0L else requestTotalMs / samples.size
    val maxMs = samples.maxOfOrNull { it.requestMs } ?: 0L
    return "page=$page photos=$expected fetched=${samples.size} " +
            "reqTotalMs=$requestTotalMs reqAvgMs=$avgMs reqMaxMs=$maxMs " +
            "bytesTotal=$bytesTotal bitmapEstBytes=$bitmapEstBytes"
}

/**
 * One loader batch: [fetched] of [requested] thumbnails arrived, [requestMs] is the summed camera
 * request time, [wallMs] the batch wall-clock time (includes pauses while a transfer ran).
 */
internal fun thumbnailBatchLine(
    page: Int, batch: Int, fetched: Int, requested: Int, requestMs: Long, wallMs: Long, bytes: Long,
): String = "page=$page batch=$batch n=$fetched/$requested reqMs=$requestMs wallMs=$wallMs bytes=$bytes"

internal fun thumbnailCancelledLine(page: Int, completed: Int, expected: Int): String =
    "page=$page cancelled after $completed/$expected thumbnails"

/**
 * Collects the thumbnails of one photo-browser page and yields each diagnostics line
 * at most once: the summary when the page finishes, or the cancellation line when the
 * page is switched away from or the coroutine is cancelled.
 */
internal class ThumbnailPageStats(private val page: Int, private val expected: Int) {
    private val samples = mutableListOf<ThumbnailSample>()
    private var reported = false

    val completed: Int get() = samples.size

    fun record(sample: ThumbnailSample) {
        samples += sample
    }

    /** Page summary, returned at most once, and only once a thumbnail arrived. */
    fun takeSummary(): String? {
        if (reported || samples.isEmpty()) return null
        reported = true
        return thumbnailPageSummary(page, expected, samples)
    }

    /** Cancellation line, at most once, and never after the summary was taken. */
    fun takeCancelled(): String? {
        if (reported) return null
        reported = true
        return thumbnailCancelledLine(page, samples.size, expected)
    }
}
