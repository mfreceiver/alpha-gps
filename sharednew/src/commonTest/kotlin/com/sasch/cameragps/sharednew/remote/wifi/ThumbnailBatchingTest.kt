package com.sasch.cameragps.sharednew.remote.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThumbnailBatchingTest {
    @Test
    fun splitsAPageIntoBatchesOfTheConfiguredSizeInOrder() {
        val settled = mutableSetOf<Int>()
        val batches = mutableListOf<List<Int>>()
        while (true) {
            val batch = nextThumbnailBatch(60, settled, 0)
            if (batch.isEmpty()) break
            batches += batch
            settled += batch
        }
        assertEquals(5, batches.size)
        assertTrue(batches.all { it.size == THUMBNAIL_BATCH_SIZE })
        assertEquals((0 until 60).toList(), batches.flatten())
    }

    @Test
    fun lastBatchIsShorterAndEmptyPageHasNone() {
        assertEquals(listOf(10, 11, 12), nextThumbnailBatch(13, (0 until 10).toSet(), 0, 12))
        assertEquals(emptyList(), nextThumbnailBatch(0, emptySet(), 0))
        assertEquals(emptyList(), nextThumbnailBatch(5, (0 until 5).toSet(), 2))
    }

    @Test
    fun focusStartsTheBatchAtTheVisibleAreaThenWrapsAround() {
        assertEquals(listOf(40, 41, 42), nextThumbnailBatch(60, emptySet(), 40, 3))
        assertEquals(listOf(58, 59, 0, 1), nextThumbnailBatch(60, setOf(), 58, 4))
        assertEquals(listOf(41, 42, 43), nextThumbnailBatch(60, setOf(40), 40, 3))
    }

    @Test
    fun outOfRangeFocusIsClamped() {
        assertEquals(listOf(4, 0), nextThumbnailBatch(5, setOf(1, 2, 3), 99, 2))
        assertEquals(listOf(0, 1), nextThumbnailBatch(5, emptySet(), -3, 2))
    }

    @Test
    fun demandedBatchServesVisibleFirstThenMarginAndNeverAnythingElse() {
        assertEquals(
            listOf(3, 4, 5, 6, 7), demandedThumbnailBatch(listOf(5, 3, 4), listOf(6, 7, 2), setOf(2))
        )
        assertEquals(listOf(5, 6), demandedThumbnailBatch(listOf(5), listOf(6, 5, 6), emptySet()))
        assertEquals(emptyList(), demandedThumbnailBatch(emptyList(), emptyList(), emptySet()))
        assertEquals(emptyList(), demandedThumbnailBatch(listOf(1), listOf(2), setOf(1, 2)))
    }

    @Test
    fun demandedBatchIsCappedAndVisibleWinsTheCap() {
        val visible = (0 until 8).toList()
        val margin = (8 until 40).toList()
        assertEquals((0 until THUMBNAIL_BATCH_SIZE).toList(), demandedThumbnailBatch(visible, margin, emptySet()))
        assertEquals(listOf(0, 1), demandedThumbnailBatch(visible, margin, emptySet(), batchSize = 2))
    }

    @Test
    fun windowAddsRowsAfterThenBeforeAndStaysInsideThePage() {
        // 4 columns, rows 2..3 visible (indices 8..15) of a 40-capture page, 2 margin rows.
        val window = thumbnailWindow((8..15).toList(), columns = 4, count = 40)
        assertEquals((8..15).toList(), window.visible)
        assertEquals((16..23).toList() + (7 downTo 0).toList(), window.margin)
        val end = thumbnailWindow((36..39).toList(), columns = 4, count = 40)
        assertEquals((35 downTo 28).toList(), end.margin)
    }

    @Test
    fun windowIgnoresOutOfPageIndicesAndEmptyViewports() {
        assertEquals(ThumbnailWindow(emptyList(), emptyList()), thumbnailWindow(emptyList(), 4, 40))
        assertEquals(ThumbnailWindow(emptyList(), emptyList()), thumbnailWindow(listOf(-1, 99), 4, 40))
        assertEquals(ThumbnailWindow(emptyList(), emptyList()), thumbnailWindow(listOf(1), 0, 40))
        assertEquals(listOf(0, 1), thumbnailWindow(listOf(-1, 0, 1, 1), 4, 2).visible)
    }

    @Test
    fun evictionDropsTheOldestOutsideTheDemandOnlyBeyondTheLimit() {
        val cache = linkedMapOf<Long, Unit>().apply { (1L..5L).forEach { put(it, Unit) } }
        assertEquals(emptyList(), thumbnailsToEvict(cache, emptySet(), limit = 5), "within the limit")
        assertEquals(listOf(1L, 2L), thumbnailsToEvict(cache, emptySet(), limit = 3))
        assertEquals(listOf(2L, 4L), thumbnailsToEvict(cache, setOf(1L, 3L), limit = 3), "demand is kept")
        assertEquals(
            listOf(5L), thumbnailsToEvict(cache, setOf(1L, 2L, 3L, 4L), limit = 1),
            "never more than what is outside the demand"
        )
        assertEquals(300, THUMBNAIL_CACHE_LIMIT)
    }
}
