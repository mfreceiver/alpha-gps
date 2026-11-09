package com.sasch.cameragps.sharednew.remote.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WifiPhotoPagingTest {
    @Test
    fun pageCountRoundsUpAndEmptyHasNone() {
        assertEquals(0, photoPageCount(0, 60))
        assertEquals(1, photoPageCount(1, 60))
        assertEquals(1, photoPageCount(60, 60))
        assertEquals(2, photoPageCount(61, 60))
        assertEquals(17, photoPageCount(1000, 60))
    }

    @Test
    fun pageNumberAndOffsetRoundTrip() {
        assertEquals(1, photoPageNumber(0, 60))
        assertEquals(3, photoPageNumber(120, 60))
        assertEquals(120, photoPageOffset(3, 1000, 60))
        for (page in 1..17) assertEquals(page, photoPageNumber(photoPageOffset(page, 1000, 60), 60))
    }

    @Test
    fun jumpTargetIsClampedIntoTheCatalog() {
        assertEquals(0, photoPageOffset(0, 1000, 60))
        assertEquals(0, photoPageOffset(-4, 1000, 60))
        assertEquals(16 * 60, photoPageOffset(99, 1000, 60))
        assertEquals(0, photoPageOffset(5, 0, 60))
    }

    @Test
    fun pageInputAcceptsOnlyWholeNumbersInRange() {
        assertEquals(5, parsePageInput(" 5 ", 17))
        assertEquals(17, parsePageInput("17", 17))
        assertNull(parsePageInput("0", 17))
        assertNull(parsePageInput("18", 17))
        assertNull(parsePageInput("", 17))
        assertNull(parsePageInput("3.5", 17))
        assertNull(parsePageInput("abc", 17))
        assertNull(parsePageInput("1", 0))
    }
}
