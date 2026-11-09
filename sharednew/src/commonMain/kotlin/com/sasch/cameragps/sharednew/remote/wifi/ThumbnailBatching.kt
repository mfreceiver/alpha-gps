package com.sasch.cameragps.sharednew.remote.wifi

/** Thumbnails requested per loader batch (≈0.5–0.8 s on a real camera at 42–66 ms each). */
internal const val THUMBNAIL_BATCH_SIZE = 12

/**
 * Picks the next batch of capture indices still to fetch: those not yet [settled] (fetched or given
 * up on), starting at the grid's [focus] position and running forward, then wrapping around to the
 * ones above it, so what the user is looking at (and about to scroll to) arrives first.
 */
internal fun nextThumbnailBatch(
    count: Int,
    settled: Set<Int>,
    focus: Int,
    batchSize: Int = THUMBNAIL_BATCH_SIZE,
): List<Int> {
    if (count <= 0 || batchSize <= 0) return emptyList()
    val start = focus.coerceIn(0, count - 1)
    return (0 until count)
        .filter { it !in settled }
        .sortedBy { if (it >= start) it - start else it + count - start }
        .take(batchSize)
}
