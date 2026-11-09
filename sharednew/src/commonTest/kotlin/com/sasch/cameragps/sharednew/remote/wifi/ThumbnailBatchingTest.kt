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
}
