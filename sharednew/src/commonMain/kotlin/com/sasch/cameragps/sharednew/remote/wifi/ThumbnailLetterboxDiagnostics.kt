package com.sasch.cameragps.sharednew.remote.wifi

/**
 * Diagnostics only (see FittedThumbnail): since the grid no longer trims the camera's baked-in
 * letterbox bars, these pure functions measure how many near-black rows/columns a decoded
 * thumbnail actually has at its edges, so a real-device log can confirm whether the black bars
 * live in the camera's thumbnail pixels. Nothing here changes what is displayed.
 */
internal fun isNearBlackPixel(argb: Int): Boolean {
    val r = argb shr 16 and 0xFF
    val g = argb shr 8 and 0xFF
    val b = argb and 0xFF
    return r <= 20 && g <= 20 && b <= 20
}

/** Near-black edge lines counted from each side (a line counts at [BLACK_LINE_FRACTION] coverage). */
internal class LetterboxBarCounts(
    val topRows: Int,
    val bottomRows: Int,
    val leftCols: Int,
    val rightCols: Int,
) {
    override fun equals(other: Any?): Boolean = other is LetterboxBarCounts &&
            other.topRows == topRows && other.bottomRows == bottomRows &&
            other.leftCols == leftCols && other.rightCols == rightCols

    override fun hashCode(): Int = listOf(topRows, bottomRows, leftCols, rightCols).hashCode()
    override fun toString(): String =
        "top=$topRows bottom=$bottomRows left=$leftCols right=$rightCols"
}

/** A line is "black" when at least this fraction of its pixels is near-black. */
internal const val BLACK_LINE_FRACTION = 0.95

/**
 * Counts near-black rows from the top/bottom and columns from the left/right of an ARGB pixel
 * array. A bright pixel inside a bar (noise, a scratch) does not end the run — the fraction
 * threshold does. Pure and thread-safe: callers hand over the pixels read on a worker thread.
 */
internal fun letterboxBarCounts(pixels: IntArray, width: Int, height: Int): LetterboxBarCounts {
    fun rowBlack(y: Int): Boolean {
        var black = 0
        for (x in 0 until width) if (isNearBlackPixel(pixels[y * width + x])) black++
        return black >= width * BLACK_LINE_FRACTION
    }

    fun colBlack(x: Int): Boolean {
        var black = 0
        for (y in 0 until height) if (isNearBlackPixel(pixels[y * width + x])) black++
        return black >= height * BLACK_LINE_FRACTION
    }

    fun run(limit: Int, line: (Int) -> Boolean): Int {
        var i = 0
        while (i < limit && line(i)) i++
        return i
    }

    return LetterboxBarCounts(
        topRows = run(height) { rowBlack(it) },
        bottomRows = run(height) { rowBlack(height - 1 - it) },
        leftCols = run(width) { colBlack(it) },
        rightCols = run(width) { colBlack(width - 1 - it) },
    )
}
