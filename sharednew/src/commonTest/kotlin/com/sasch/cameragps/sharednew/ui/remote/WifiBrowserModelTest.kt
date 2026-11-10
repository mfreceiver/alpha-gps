package com.sasch.cameragps.sharednew.ui.remote

import com.sasch.cameragps.sharednew.remote.wifi.UNKNOWN_DATE_KEY
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraPhoto
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoBrowserState
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteFailure
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.formatByteSize
import com.sasch.cameragps.sharednew.remote.wifi.groupCameraPhotos
import com.sasch.cameragps.sharednew.ui.theme.DarkAlphaColors
import com.sasch.cameragps.sharednew.ui.theme.LightAlphaColors
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
        assertEquals("25", formatSizeTag(mb * 49 / 2), "24.5 MB drops the decimal, rounded half up")
        assertEquals("24", formatSizeTag(25_648_168), "24.4599 MB: one rounding from the raw bytes, not 24.5 -> 25")
        assertEquals("1.1", formatSizeTag(mb + 52_429), "1.05 MB rounds half up")
        assertEquals("1.0", formatSizeTag(mb + 52_428), "just below 1.05 MB rounds down")
    }

    @Test
    fun sizeTagsAndStripSizesShareOneNumberFormat() {
        // P3-1: the tag (no unit) and the strip/button text ("MB") are the same number.
        val mb = 1024L * 1024L
        for (bytes in listOf(6.6.toLong() * mb, 245L * mb / 10, 105L * mb / 100)) {
            assertEquals(formatSizeTag(bytes), formatByteSize(bytes).removeSuffix(" MB"), "bytes=$bytes")
        }
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
    fun theTileFrameIsNearBlackInBothThemes() {
        // The camera's thumbnails bake their letterbox bars into the pixels; the near-black frame
        // (one token, both themes) makes those bars blend in instead of being trimmed.
        listOf(false, true).forEach { dark ->
            val fill = if (dark) DarkAlphaColors else LightAlphaColors
            assertEquals(1f, fill.thumbnailFrame.alpha, "opaque, dark=$dark")
            assertTrue(
                fill.thumbnailFrame.red <= 0x1A / 255f && fill.thumbnailFrame.green <= 0x1A / 255f &&
                        fill.thumbnailFrame.blue <= 0x1A / 255f,
                "near-black #0E0E10-ish, dark=$dark",
            )
        }
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
