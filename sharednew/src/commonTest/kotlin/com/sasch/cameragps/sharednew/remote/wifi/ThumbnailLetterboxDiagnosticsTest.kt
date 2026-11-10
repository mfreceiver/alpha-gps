package com.sasch.cameragps.sharednew.remote.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Diagnostics for the un-trimmed letterbox bars (the grid fits the whole thumbnail; a near-black
 * frame blends the bars in). These counts back the "AlphaThumb" log lines that a real-device run
 * can use to confirm the bars live in the camera's thumbnail pixels.
 */
class ThumbnailLetterboxDiagnosticsTest {
    private val black = 0xFF000000.toInt()
    private val nearBlack = 0xFF101410.toInt()
    private val bright = 0xFF80A0C0.toInt()

    @Test
    fun nearBlackMeansEveryChannelIsAtMost20() {
        assertTrue(isNearBlackPixel(black))
        assertTrue(isNearBlackPixel(0xFF141414.toInt()))
        assertFalse(isNearBlackPixel(0xFF203040.toInt()))
        assertFalse(isNearBlackPixel(0xFF0000FF.toInt()))
    }

    @Test
    fun countsNearBlackBarsOnEachEdgeIndependently() {
        val w = 10
        val h = 20
        // Horizontal bars: 5 fully black rows at the top and bottom, bright middle.
        val rows = IntArray(w * h) { bright }
        for (y in 0 until 5) for (x in 0 until w) rows[y * w + x] = black
        for (y in h - 5 until h) for (x in 0 until w) rows[y * w + x] = black
        assertEquals(
            LetterboxBarCounts(topRows = 5, bottomRows = 5, leftCols = 0, rightCols = 0),
            letterboxBarCounts(rows, w, h),
            "no column is fully black, so no column bars",
        )
        // Vertical bars: 3 fully black columns at the left and right, bright middle.
        val cols = IntArray(w * h) { bright }
        for (y in 0 until h) for (x in 0 until 3) {
            cols[y * w + x] = black
            cols[y * w + (w - 1 - x)] = black
        }
        assertEquals(
            LetterboxBarCounts(topRows = 0, bottomRows = 0, leftCols = 3, rightCols = 3),
            letterboxBarCounts(cols, w, h),
        )
    }

    @Test
    fun aBrightLineEndsTheRunButNoiseInsideABarDoesNot() {
        val w = 20
        val h = 4
        // 2 bar rows, but the second has one bright pixel of twenty (exactly 95 % — still a bar).
        val pixels = IntArray(w * h) { bright }
        for (x in 0 until w) {
            pixels[x] = black
            pixels[w + x] = if (x == 0) bright else black
        }
        val counts = letterboxBarCounts(pixels, w, h)
        assertEquals(2, counts.topRows, "the noisy row still counts as a bar")
        assertEquals(0, counts.bottomRows)
        assertEquals(0, counts.leftCols, "no column is fully black")
    }

    @Test
    fun aPlainPictureHasNoBarsAndAnAllBlackOneIsAllBars() {
        val w = 3
        val h = 3
        assertEquals(
            LetterboxBarCounts(0, 0, 0, 0),
            letterboxBarCounts(IntArray(w * h) { bright }, w, h),
        )
        assertEquals(
            LetterboxBarCounts(h, h, w, w),
            letterboxBarCounts(IntArray(w * h) { nearBlack }, w, h),
        )
    }
}
