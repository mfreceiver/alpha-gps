package com.sasch.cameragps.sharednew.remote.wifi

/** Thumbnails requested per loader batch (≈0.5–0.8 s on a real camera at 42–66 ms each). */
internal const val THUMBNAIL_BATCH_SIZE = 12

/**
 * What the grid shows right now, by stable capture key: [visible] are on screen, [margin] are the
 * neighbouring rows worth having ready. [pageRevision] ties it to the page it was measured on.
 */
internal data class ThumbnailDemand(
    val pageRevision: Long, val visible: List<String>, val margin: List<String>
)

/**
 * Next batch of a demand-driven loader: unsettled visible captures first (in grid order), then
 * unsettled margin ones, at most [batchSize]. Nothing outside the demand is ever picked, so an idle
 * or far-away page costs no camera traffic.
 */
internal fun demandedThumbnailBatch(
    visible: List<Int>, margin: List<Int>, settled: Set<Int>, batchSize: Int = THUMBNAIL_BATCH_SIZE,
): List<Int> = (visible.sorted() + margin).distinct().filter { it !in settled }.take(batchSize)

/** Visible capture indices plus the neighbouring-row margin, clamped to the page. */
internal data class ThumbnailWindow(val visible: List<Int>, val margin: List<Int>)

/**
 * Turns the visible capture indices of a grid with [columns] columns into a [ThumbnailWindow] whose
 * margin is [marginRows] rows after (nearest first) and then before the visible block.
 */
internal fun thumbnailWindow(
    visible: List<Int>, columns: Int, count: Int, marginRows: Int = 2
): ThumbnailWindow {
    val shown = visible.filter { it in 0 until count }.distinct().sorted()
    if (shown.isEmpty() || columns <= 0) return ThumbnailWindow(emptyList(), emptyList())
    val extra = marginRows.coerceAtLeast(0) * columns
    val after = (shown.last() + 1..minOf(shown.last() + extra, count - 1)).toList()
    val before = (maxOf(shown.first() - extra, 0) until shown.first()).toList().asReversed()
    return ThumbnailWindow(shown, after + before)
}

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
