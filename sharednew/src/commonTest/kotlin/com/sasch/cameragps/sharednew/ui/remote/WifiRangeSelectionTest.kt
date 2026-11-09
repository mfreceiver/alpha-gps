package com.sasch.cameragps.sharednew.ui.remote

import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraCapture
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraPhoto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WifiRangeSelectionTest {
    private fun capture(index: Int) = WifiCameraCapture(
        "c$index", listOf(WifiCameraPhoto(index.toLong(), "$index.JPG", 1, "image/jpeg", "", captureId = "c$index"))
    )

    private val page = (0 until 8).map(::capture)

    @Test
    fun rangeIsClosedOrderIndependentAndNeverTouchesTilesOutsideIt() {
        val initial = mapOf("c7" to page[7])
        assertEquals(setOf("c7", "c2", "c3", "c4"), rangeSelectionResult(initial, page, 2, 4, true).keys)
        assertEquals(setOf("c7", "c2", "c3", "c4"), rangeSelectionResult(initial, page, 4, 2, true).keys)
        val all = page.associateBy { it.id }
        assertEquals(all.keys - setOf("c3", "c4"), rangeSelectionResult(all, page, 4, 3, false).keys)
    }

    @Test
    fun resultKeepsTheInitialOrderAndClampsOutOfRangeEnds() {
        val result = rangeSelectionResult(mapOf("c6" to page[6], "c1" to page[1]), page, 3, 99, true)
        assertEquals(listOf("c6", "c1", "c3", "c4", "c5", "c7"), result.keys.toList())
        assertEquals(emptyMap(), rangeSelectionResult(emptyMap(), emptyList(), 0, 0, true))
    }

    @Test
    fun hitTestFindsTheTileAndSeparatesTheActionCornersFromTheBody() {
        val tiles = listOf(TileBounds(0, 0f, 0f, 100f, 100f), TileBounds(1, 104f, 0f, 100f, 100f))
        assertEquals(TileHit(0, TileZone.Body), hitTestTile(tiles, 50f, 50f, 40f))
        assertEquals(TileHit(0, TileZone.Preview), hitTestTile(tiles, 10f, 10f, 40f))
        assertEquals(TileHit(1, TileZone.Checkbox), hitTestTile(tiles, 200f, 95f, 40f))
        assertEquals(TileHit(1, TileZone.Body), hitTestTile(tiles, 200f, 5f, 40f))
        assertNull(hitTestTile(tiles, 102f, 50f, 40f), "the gap between tiles hits nothing")
        assertNull(hitTestTile(tiles, 50f, 150f, 40f))
        assertEquals(TileHit(1, TileZone.Body), hitTestTile(tiles, 110f, 90f, 0f), "zero action size = body only")
    }

    @Test
    fun autoScrollIsStillInTheMiddleAndRampsTowardsTheEdges() {
        assertEquals(0f, autoScrollStep(500f, 1000f, 100f, 20f))
        assertEquals(-20f, autoScrollStep(0f, 1000f, 100f, 20f))
        assertEquals(-10f, autoScrollStep(50f, 1000f, 100f, 20f))
        assertEquals(20f, autoScrollStep(1000f, 1000f, 100f, 20f))
        assertEquals(0f, autoScrollStep(0f, 0f, 100f, 20f), "no viewport, no scrolling")
        assertEquals(-20f, autoScrollStep(-50f, 1000f, 100f, 20f), "a finger above the grid is capped")
    }
}
