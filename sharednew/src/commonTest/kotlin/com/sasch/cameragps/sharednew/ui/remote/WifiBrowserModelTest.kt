package com.sasch.cameragps.sharednew.ui.remote

import com.sasch.cameragps.sharednew.remote.wifi.UNKNOWN_DATE_KEY
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraPhoto
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoBrowserState
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteFailure
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.groupCameraPhotos
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WifiBrowserModelTest {
    private fun frame(
        phase: WifiRemotePhase,
        failure: WifiRemoteFailure? = null,
        transfer: Boolean = true,
        browser: WifiPhotoBrowserState = WifiPhotoBrowserState(),
        awaiting: Boolean = false,
        shown: Boolean = false,
    ) = wifiConnectionFrame(
        WifiRemoteState(phase = phase, failure = failure, canTransferImages = transfer, photoBrowser = browser),
        awaitingAutoConnect = awaiting,
        catalogShown = shown,
    )

    private fun progress(step: WifiConnectStep) = WifiConnectionFrame.Progress(step)

    @Test
    fun phasesMapToTheOverlayStepsInOrder() {
        assertEquals(progress(WifiConnectStep.PrepareCamera), frame(WifiRemotePhase.PreparingCamera))
        assertEquals(progress(WifiConnectStep.ApproveNetwork), frame(WifiRemotePhase.AwaitingNetworkApproval))
        assertEquals(progress(WifiConnectStep.JoinCamera), frame(WifiRemotePhase.JoiningNetwork))
        assertEquals(progress(WifiConnectStep.JoinCamera), frame(WifiRemotePhase.OpeningSession))
        assertEquals(progress(WifiConnectStep.ReadCatalog), frame(WifiRemotePhase.Ready))
        assertEquals(WifiConnectionFrame.Closing, frame(WifiRemotePhase.Closing))
    }

    @Test
    fun idleIsTheFirstStepOnlyWhileTheAutomaticConnectionIsAboutToStart() {
        assertEquals(progress(WifiConnectStep.PrepareCamera), frame(WifiRemotePhase.Idle, awaiting = true))
        assertNull(frame(WifiRemotePhase.Idle))
    }

    @Test
    fun theManualPathKeepsItsOwnControls() = assertNull(frame(WifiRemotePhase.AwaitingManualNetwork))

    @Test
    fun aFailureCarriesItsReasonAndOnlyThePermissionFailureAsksForAction() {
        val failed = frame(WifiRemotePhase.Failed, WifiRemoteFailure.NetworkLost)
        assertEquals(WifiConnectionFrame.Failed(WifiRemoteFailure.NetworkLost), failed)
        assertFalse(failed!!.needsPermissionAction())
        val permission = frame(WifiRemotePhase.Failed, WifiRemoteFailure.NetworkPermissionDenied)!!
        assertTrue(permission.needsPermissionAction())
        assertFalse(WifiConnectionFrame.Closing.needsPermissionAction())
    }

    @Test
    fun theFirstCatalogReadIsTheLastStepAndTheDayListEndsTheOverlay() {
        val reading = WifiPhotoBrowserState(open = true, grouped = true, loading = true)
        assertEquals(progress(WifiConnectStep.ReadCatalog), frame(WifiRemotePhase.Ready, browser = reading))
        val listed = reading.copy(loading = false)
        assertNull(frame(WifiRemotePhase.Ready, browser = listed))
        // A failed read is shown by the day list itself ("read again"), not by the overlay.
        assertNull(frame(WifiRemotePhase.Ready, browser = reading.copy(failed = true)))
        // A refresh after the list was shown keeps the list on screen.
        assertNull(frame(WifiRemotePhase.Ready, browser = reading, shown = true))
        // Loading a day is not a catalog read.
        assertNull(frame(WifiRemotePhase.Ready, browser = reading.copy(dateKey = "2024-05-01")))
    }

    @Test
    fun aCameraWithoutPhotoTransferEndsLikeAFailure() =
        assertEquals(WifiConnectionFrame.Unsupported, frame(WifiRemotePhase.Ready, transfer = false))

    @Test
    fun gridOptionsDefaultToSmallTilesWithEveryTagOff() {
        val options = WifiGridOptions()
        assertEquals(WifiGridSize.Small, options.gridSize)
        assertFalse(options.showFormat || options.showSize)
        assertEquals(listOf(4, 3, 2), WifiGridSize.entries.map { it.columns })
        assertEquals(WifiGridSize.Large, options.withSize(WifiGridSize.Large).gridSize)
        assertEquals("Large", options.withSize(WifiGridSize.Large).size, "the persisted value is the name")
        assertEquals(WifiGridSize.Small, WifiGridOptions(size = "bogus").gridSize, "unknown values fall back")
    }

    @Test
    fun sizeTagsAreUnitlessMegabytesWithOneDecimal() {
        val mb = 1024L * 1024L
        assertNull(formatSizeTag(0), "unknown size: no tag")
        assertEquals("<0.1", formatSizeTag(1))
        assertEquals("<0.1", formatSizeTag(mb / 10 - 1))
        assertEquals("0.1", formatSizeTag(mb / 10 + 1))
        assertEquals("1.0", formatSizeTag(mb))
        assertEquals("6.6", formatSizeTag(mb * 66 / 10))
        assertEquals("24.5", formatSizeTag(mb * 49 / 2))
        assertEquals("1.1", formatSizeTag(mb + 52_429), "1.05 MB rounds half up")
        assertEquals("1.0", formatSizeTag(mb + 52_428), "just below 1.05 MB rounds down")
    }

    @Test
    fun cornerElementsAreAnchoredToDistinctFrameCorners() {
        assertEquals(TileCorner.TopStart, tileCorner(TileElement.Preview))
        assertEquals(TileCorner.TopEnd, tileCorner(TileElement.SizeTag))
        assertEquals(TileCorner.BottomStart, tileCorner(TileElement.FormatTag))
        assertEquals(TileCorner.BottomEnd, tileCorner(TileElement.Checkbox))
        assertEquals(TileCorner.entries.toSet(), TileElement.entries.map(::tileCorner).toSet(), "one per corner")
        assertTrue(TILE_ACTION_DP >= 40, "touch targets stay at least 40 dp")
        assertTrue(TILE_CORNER_INSET_DP + TILE_BADGE_DP <= TILE_ACTION_DP, "the visible badge sits inside its target")
    }

    @Test
    fun theGridGestureZonesMatchTheCornerAnchors() {
        // The long-press range gesture must treat the preview and check box corners as theirs.
        val tile = TileBounds(captureIndex = 0, left = 0f, top = 0f, width = 100f, height = 100f)
        val action = TILE_ACTION_DP.toFloat()
        assertEquals(TileZone.Preview, hitTestTile(listOf(tile), 5f, 5f, action)?.zone)
        assertEquals(TileZone.Checkbox, hitTestTile(listOf(tile), 95f, 95f, action)?.zone)
        // Size (top-right) and format (bottom-left) tags are not buttons: their corners are the body.
        assertEquals(TileZone.Body, hitTestTile(listOf(tile), 95f, 5f, action)?.zone)
        assertEquals(TileZone.Body, hitTestTile(listOf(tile), 5f, 95f, action)?.zone)
    }

    @Test
    fun theTileFrameIsPaleGlassNeverBlack() {
        listOf(false, true).forEach { dark ->
            val fill = tileFrameColor(dark)
            assertEquals(1f, fill.red); assertEquals(1f, fill.green); assertEquals(1f, fill.blue)
            assertTrue(fill.alpha in 0.05f..0.7f, "translucent veil, dark=$dark")
        }
    }

    @Test
    fun letterboxBarsAreTrimmedOnlyWhenSymmetric() {
        // 120 rows, 7 black on each side (a 3:2 picture in a 4:3 thumbnail).
        fun bars(top: Int, bottom: Int, length: Int = 120): (Int) -> Boolean = { it < top || it >= length - bottom }
        assertEquals(7 until 113, letterboxSpan(120, bars(7, 7)))
        assertEquals(7 until 112, letterboxSpan(120, bars(7, 8)), "a pixel of rounding is fine")
        assertEquals(0 until 120, letterboxSpan(120, bars(0, 0)), "no bars")
        assertEquals(0 until 120, letterboxSpan(120, bars(30, 0)), "a dark sky on one side is the picture")
        assertEquals(0 until 120, letterboxSpan(120, bars(1, 1)), "hairlines are not letterboxing")
        assertEquals(0 until 120, letterboxSpan(120, bars(40, 40)), "too little would remain")
        assertEquals(0 until 120, letterboxSpan(120) { true }, "an all-black frame is kept")
        assertTrue(isLetterboxBlack(0xFF000000.toInt()))
        assertTrue(isLetterboxBlack(0xFF101410.toInt()))
        assertFalse(isLetterboxBlack(0xFF203040.toInt()))
    }

    @Test
    fun daySubtitlesAreOnlyTheWeekdayAndUnknownDaysHaveNone() {
        // No relative words any more: today's and yesterday's rows show their weekday like every day.
        assertEquals(4, dateKeyWeekday("2024-05-02")) // Thursday, whatever "today" is
        assertEquals(LocalDate(2024, 5, 2), dateKeyToLocalDate("2024-05-02"))
        assertNull(dateKeyToLocalDate(UNKNOWN_DATE_KEY), "the unknown-date group keeps no subtitle")
        assertNull(dateKeyWeekday("garbage"))
    }

    @Test
    fun weekdaysAreIsoNumbered() {
        assertEquals(1, dateKeyWeekday("2024-04-29")) // Monday
        assertEquals(7, dateKeyWeekday("2024-05-05")) // Sunday
        assertNull(dateKeyWeekday(UNKNOWN_DATE_KEY))
    }
}
