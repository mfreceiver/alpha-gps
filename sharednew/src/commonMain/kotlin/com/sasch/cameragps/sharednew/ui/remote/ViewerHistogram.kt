package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.ceil
import kotlin.math.sqrt

/** Number of luminance bins the viewer draws. */
internal const val HISTOGRAM_BINS = 64

/** Upper bound of pixels sampled per histogram, whatever the preview's size. */
internal const val HISTOGRAM_MAX_SAMPLES = 40_000

/**
 * Sampling step (in both directions) that keeps a [width]×[height] image at or under [maxSamples]
 * sampled pixels. 1 for small images.
 */
internal fun histogramSampleStep(width: Int, height: Int, maxSamples: Int = HISTOGRAM_MAX_SAMPLES): Int {
    require(maxSamples > 0)
    if (width <= 0 || height <= 0) return 1
    val pixels = width.toLong() * height
    if (pixels <= maxSamples) return 1
    return ceil(sqrt(pixels.toDouble() / maxSamples)).toInt().coerceAtLeast(1)
}

/** Rec. 601 luma (0..255) of an ARGB pixel, in integer arithmetic. Alpha is ignored. */
internal fun lumaOf(argb: Int): Int {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (77 * r + 150 * g + 29 * b + 128) shr 8
}

/** Adds the luma of [count] pixels of [argb] starting at [from], every [step]th, to [bins]. */
internal fun accumulateLuminance(argb: IntArray, bins: IntArray, from: Int = 0, count: Int = argb.size, step: Int = 1) {
    require(bins.isNotEmpty() && step > 0)
    var i = from
    val end = minOf(argb.size, from + count)
    while (i < end) {
        val luma = lumaOf(argb[i]).coerceIn(0, 255)
        bins[luma * bins.size / 256]++
        i += step
    }
}

/** Luminance histogram of ARGB pixels ([HISTOGRAM_BINS] bins by default). */
internal fun luminanceHistogram(argb: IntArray, binCount: Int = HISTOGRAM_BINS): IntArray =
    IntArray(binCount).also { accumulateLuminance(argb, it) }

/**
 * Bin heights scaled to 0..1 for drawing. Uses a square-root scale so a few huge bins (sky, black
 * borders) do not flatten everything else; an empty histogram stays all zero.
 */
internal fun normalizedHistogram(bins: IntArray): FloatArray {
    val max = bins.maxOrNull() ?: 0
    if (max <= 0) return FloatArray(bins.size)
    val top = sqrt(max.toDouble())
    return FloatArray(bins.size) { (sqrt(bins[it].toDouble()) / top).toFloat() }
}

/**
 * Down-sampled luminance histogram of a decoded preview: every [histogramSampleStep]th row is read
 * (one row at a time, so no full-size pixel copy) and every step-th pixel of it counted. Blocking —
 * call it off the main thread. Null when the pixels cannot be read.
 */
internal fun ImageBitmap.luminanceHistogram(binCount: Int = HISTOGRAM_BINS): FloatArray? {
    if (width <= 0 || height <= 0) return null
    val step = histogramSampleStep(width, height)
    val bins = IntArray(binCount)
    val row = IntArray(width)
    return try {
        var y = 0
        while (y < height) {
            readPixels(row, startX = 0, startY = y, width = width, height = 1)
            accumulateLuminance(row, bins, step = step)
            y += step
        }
        normalizedHistogram(bins)
    } catch (_: Exception) {
        null
    }
}
