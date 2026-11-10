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
        assertFalse(options.showFormat || options.showSize || options.showProtection)
        assertEquals(listOf(4, 3, 2), WifiGridSize.entries.map { it.columns })
        assertEquals(WifiGridSize.Large, options.withSize(WifiGridSize.Large).gridSize)
        assertEquals("Large", options.withSize(WifiGridSize.Large).size, "the persisted value is the name")
        assertEquals(WifiGridSize.Small, WifiGridOptions(size = "bogus").gridSize, "unknown values fall back")
    }

    private fun photo(handle: Long, capture: String, mime: String, size: Long = 0, protected: Boolean? = null) =
        WifiCameraPhoto(handle, "F$handle", size, mime, "20240501T100000", captureId = capture, isProtected = protected)

    @Test
    fun protectionIsKnownOnlyWhenTheCatalogReportsIt() {
        val captures = groupCameraPhotos(
            listOf(
                photo(1, "a", "image/jpeg", protected = true),
                photo(2, "a", "image/x-sony-arw", protected = false),
                photo(3, "b", "image/jpeg", protected = false),
                photo(4, "c", "image/jpeg"),
            )
        ).associateBy { it.id }
        assertEquals(true, captures.getValue("a").isProtected, "any protected file protects the capture")
        assertEquals(false, captures.getValue("b").isProtected)
        assertNull(captures.getValue("c").isProtected, "unknown is never guessed")
        assertTrue(protectionKnown(captures.values.toList()))
        assertFalse(protectionKnown(listOf(captures.getValue("c"))), "v4 catalogs grey the switch out")
    }

    @Test
    fun sizeTagsAreMegabytesWithOneDecimal() {
        val mb = 1024L * 1024L
        assertEquals("0.0 MB", formatSizeTag(0))
        assertEquals("0.1 MB", formatSizeTag(mb / 10))
        assertEquals("1.0 MB", formatSizeTag(mb))
        assertEquals("24.5 MB", formatSizeTag(mb * 49 / 2))
        assertEquals("1.1 MB", formatSizeTag(mb + 52_429), "1.05 MB rounds half up")
        assertEquals("1.0 MB", formatSizeTag(mb + 52_428), "just below 1.05 MB rounds down")
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
