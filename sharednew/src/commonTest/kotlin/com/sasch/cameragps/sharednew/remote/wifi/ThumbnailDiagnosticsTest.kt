package com.sasch.cameragps.sharednew.remote.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThumbnailDiagnosticsTest {
    @Test
    fun summaryAggregatesAveragesMaximaAndTotals() {
        val line = thumbnailPageSummary(
            60, 3, listOf(
                ThumbnailSample(100, 1_000, 640 * 424 * 4L),
                ThumbnailSample(900, 2_000, 640 * 424 * 4L),
                ThumbnailSample(200, 3_000, null),
            )
        )
        assertEquals(
            "page=60 photos=3 fetched=3 reqTotalMs=1200 reqAvgMs=400 reqMaxMs=900 " +
                    "bytesTotal=6000 bitmapEstBytes=2170880",
            line
        )
    }

    @Test
    fun summaryOfEmptyPageHasNoDivisionAndNoBitmaps() {
        assertEquals(
            "page=0 photos=0 fetched=0 reqTotalMs=0 reqAvgMs=0 reqMaxMs=0 " +
                    "bytesTotal=0 bitmapEstBytes=0",
            thumbnailPageSummary(0, 0, emptyList())
        )
    }

    @Test
    fun cancelledLineReportsProgress() {
        assertEquals("page=90 cancelled after 7/30 thumbnails", thumbnailCancelledLine(90, 7, 30))
    }

    @Test
    fun pageStatsReportsSummaryOnceAndOnlyAfterAFetch() {
        val stats = ThumbnailPageStats(0, 2)
        assertNull(stats.takeSummary(), "empty page must not report a summary")
        stats.record(ThumbnailSample(10, 100, 400))
        stats.record(ThumbnailSample(30, 200, 400))
        assertEquals(2, stats.completed)
        val summary = stats.takeSummary()
        assertTrue(summary != null && "reqTotalMs=40" in summary && "reqMaxMs=30" in summary)
        assertNull(stats.takeSummary(), "summary must not repeat")
        assertNull(stats.takeCancelled(), "cancellation must not follow the summary")
    }

    @Test
    fun pageStatsReportsCancellationOnce() {
        val stats = ThumbnailPageStats(120, 5)
        stats.record(ThumbnailSample(5, 50, 200))
        assertEquals("page=120 cancelled after 1/5 thumbnails", stats.takeCancelled())
        assertNull(stats.takeCancelled(), "cancellation must not repeat")
        assertNull(stats.takeSummary(), "summary must not follow cancellation")
    }

    @Test
    fun batchLineReportsFetchedOfRequestedAndTimings() {
        assertEquals(
            "page=60 batch=2 n=11/12 reqMs=640 wallMs=700 bytes=120000",
            thumbnailBatchLine(60, 2, 11, 12, 640, 700, 120_000)
        )
    }
}
