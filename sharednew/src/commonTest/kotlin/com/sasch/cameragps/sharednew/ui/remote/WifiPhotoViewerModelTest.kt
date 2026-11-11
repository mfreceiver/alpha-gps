package com.sasch.cameragps.sharednew.ui.remote

import com.sasch.cameragps.sharednew.remote.wifi.PhotoExif
import com.sasch.cameragps.sharednew.remote.wifi.PhotoFormat
import com.sasch.cameragps.sharednew.remote.wifi.PhotoMetadata
import com.sasch.cameragps.sharednew.remote.wifi.PhotoMetadataFailureReason
import com.sasch.cameragps.sharednew.remote.wifi.PhotoMetadataState
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraCapture
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraPhoto
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferState
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoDownload
import com.sasch.cameragps.sharednew.remote.wifi.WifiSkipReason
import com.sasch.cameragps.sharednew.remote.wifi.captureDownloadSize
import com.sasch.cameragps.sharednew.remote.wifi.formatByteSize
import com.sasch.cameragps.sharednew.remote.wifi.singleFormatChoices
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WifiPhotoViewerModelTest {
    private val jpg = WifiCameraPhoto(1, "DSC1.JPG", 6_600_000, "image/jpeg", "", captureId = "c", width = 7008, height = 4672)
    private val raw = WifiCameraPhoto(2, "DSC1.ARW", 48_000_000, "image/x-sony-arw", "", captureId = "c", width = 160, height = 120)
    private val heif = WifiCameraPhoto(3, "DSC2.HIF", 5_000_000, "image/heif", "", captureId = "h")
    private val pair = WifiCameraCapture("c", listOf(raw, jpg))
    private val rawOnly = WifiCameraCapture("r", listOf(raw.copy(captureId = "r")))
    private val jpgOnly = WifiCameraCapture("j", listOf(jpg))

    private fun metadata(handle: Long, exif: PhotoExif? = null, format: PhotoFormat = PhotoFormat.Jpeg) = PhotoMetadata(
        handle = handle, filename = "DSC1.JPG", format = format, sizeBytes = 1, capturedAt = "",
        width = 6000, height = 4000, exif = exif, extendedRead = exif != null,
    )

    private val sampleExif = PhotoExif(
        apertureF = 2.8, exposureTimeSeconds = 1.0 / 250, iso = 400, focalLengthMm = 35.0,
        exposureBiasEv = 0.3, lensModel = "FE 24-70mm F2.8 GM II",
    )

    // ---- extended read trigger --------------------------------------------------------------------

    @Test
    fun theExtendedReadIsAskedForTheSettledPhotoWhateverTheDetailsShow() {
        // No drawer argument at all: the read does not depend on the details being visible.
        assertTrue(viewerWantsExtendedMetadata(transferBusy = false, swiping = false), "settled on a photo: read (after the debounce)")
        assertFalse(viewerWantsExtendedMetadata(transferBusy = false, swiping = true), "being swiped away: no read")
        assertFalse(viewerWantsExtendedMetadata(transferBusy = true, swiping = false), "downloading: the camera is busy")
        assertFalse(viewerWantsExtendedMetadata(transferBusy = true, swiping = true))
    }

    // ---- details drawer gesture ---------------------------------------------------------------------

    @Test
    fun onlyTheLowerPartOfThePhotoOpensTheDrawer() {
        val height = 1000f
        assertFalse(inDetailsSwipeZone(0f, height), "the top of the photo belongs to the pager")
        assertFalse(inDetailsSwipeZone(height * (1f - DETAILS_SWIPE_ZONE_FRACTION) - 1f, height))
        assertTrue(inDetailsSwipeZone(height * (1f - DETAILS_SWIPE_ZONE_FRACTION), height))
        assertTrue(inDetailsSwipeZone(height - 1f, height), "the info strip lies inside the zone")
        assertFalse(inDetailsSwipeZone(height + 1f, height))
        assertFalse(inDetailsSwipeZone(0f, 0f), "no layout yet: no zone")
    }

    @Test
    fun onlyASteepUpwardSwipeOpensAndASteepDownwardDragCloses() {
        val d = 40f
        assertTrue(detailsSwipeOpens(dx = 0f, dy = -d, distance = d))
        assertTrue(detailsSwipeOpens(dx = 20f, dy = -60f, distance = d), "a slightly slanted swipe still opens")
        assertFalse(detailsSwipeOpens(dx = 0f, dy = -(d - 1f), distance = d), "too short")
        assertFalse(detailsSwipeOpens(dx = 0f, dy = d, distance = d), "downwards")
        assertFalse(detailsSwipeOpens(dx = 80f, dy = -60f, distance = d), "a page swipe (mostly horizontal) never opens")
        assertFalse(detailsSwipeOpens(dx = -50f, dy = -60f, distance = d), "below the steepness")
        assertTrue(detailsSwipeCloses(dx = 0f, dy = d, distance = d))
        assertFalse(detailsSwipeCloses(dx = 0f, dy = -d, distance = d), "upwards keeps it open")
        assertFalse(detailsSwipeCloses(dx = 90f, dy = 50f, distance = d), "sideways keeps it open")
    }

    @Test
    fun onlyThePortraitDrawerHidesTheViewerBelowFromAccessibility() {
        // Portrait: the drawer is modal — the photo, download, selection and filmstrip below
        // become unreachable for accessibility services while it is up.
        assertTrue(viewerHidesBackgroundSemantics(portrait = true, drawerOpen = true, hasDetails = true))
        assertFalse(viewerHidesBackgroundSemantics(portrait = true, drawerOpen = false, hasDetails = true))
        assertFalse(viewerHidesBackgroundSemantics(portrait = true, drawerOpen = true, hasDetails = false))
        // Wide layouts: the details panel sits next to the photo, not over it — never hidden.
        assertFalse(viewerHidesBackgroundSemantics(portrait = false, drawerOpen = true, hasDetails = true))
    }

    // ---- info strip --------------------------------------------------------------------------------

    @Test
    fun formatLineSpellsOutTheGroup() {
        assertEquals("JPG + RAW", viewerFormatText(pair))
        assertEquals("RAW", viewerFormatText(rawOnly))
        assertEquals("JPG", viewerFormatText(jpgOnly))
        assertEquals("HEIF + RAW", viewerFormatText(WifiCameraCapture("x", listOf(raw, heif))))
    }

    @Test
    fun stripShowsFormatAndTheDefaultFormatDownloadSizeOnce() {
        // JPG default: only the JPG is downloaded, so the strip names only it — "JPG · 6.6 MB".
        assertEquals(ViewerInfoStrip("JPG", 6_600_000, missingFormat = false), viewerInfoStrip(pair, WifiFormatPolicy.JpegOnly))
        assertEquals(ViewerInfoStrip("JPG + RAW", 54_600_000, missingFormat = false), viewerInfoStrip(pair, WifiFormatPolicy.Original),
            "Original downloads every file: all formats and the sum")
    }

    @Test
    fun stripFormatLabelNamesTheFilesThatWouldBeDownloaded() {
        assertEquals("JPG", stripFormatLabel(pair, WifiFormatPolicy.JpegOnly))
        assertEquals("JPG + RAW", stripFormatLabel(pair, WifiFormatPolicy.Original))
        assertEquals("JPG", stripFormatLabel(jpgOnly, WifiFormatPolicy.JpegOnly))
        assertEquals("JPG", stripFormatLabel(jpgOnly, WifiFormatPolicy.Original))
        assertEquals("RAW", stripFormatLabel(rawOnly, WifiFormatPolicy.Original))
        // No JPG to download: the strip names what the capture has (shown as "Only RAW").
        assertEquals("RAW", stripFormatLabel(rawOnly, WifiFormatPolicy.JpegOnly))
        assertEquals("HEIF + RAW", stripFormatLabel(WifiCameraCapture("x", listOf(raw, heif)), WifiFormatPolicy.JpegOnly))
        // The label is about the format choice, not what is left: saved / too-large files keep their names.
        val tooLarge = WifiCameraCapture("c", listOf(raw, jpg.copy(downloadable = false)))
        assertEquals("JPG", stripFormatLabel(tooLarge, WifiFormatPolicy.JpegOnly))
        assertEquals("JPG", viewerInfoStrip(pair, WifiFormatPolicy.JpegOnly, setOf(WifiPhotoDownload(1))).formatText)
        assertNull(stripFormatLabel(WifiCameraCapture("e", emptyList()), WifiFormatPolicy.Original))
    }

    @Test
    fun downloadTargetsFollowThePlanOfThePolicy() {
        assertEquals(listOf(jpg), viewerDownloadTargets(pair, WifiFormatPolicy.JpegOnly))
        assertEquals(setOf(jpg, raw), viewerDownloadTargets(pair, WifiFormatPolicy.Original).toSet())
        assertTrue(viewerDownloadTargets(rawOnly, WifiFormatPolicy.JpegOnly).isEmpty(), "no JPG: nothing planned")
    }

    @Test
    fun stripSizeIsTheSameNumberAsTheGridTagAndTheBatchButton() {
        for (policy in WifiFormatPolicy.entries) for (capture in listOf(pair, rawOnly, jpgOnly)) {
            assertEquals(captureDownloadSize(capture, policy).bytes, viewerInfoStrip(capture, policy).sizeBytes, "$policy ${capture.id}")
        }
        // Saved-aware: the strip shows what remains to download, like the grid tag and the button.
        assertEquals(
            captureDownloadSize(pair, WifiFormatPolicy.Original, setOf(WifiPhotoDownload(1))).bytes,
            viewerInfoStrip(pair, WifiFormatPolicy.Original, setOf(WifiPhotoDownload(1))).sizeBytes,
        )
    }

    @Test
    fun aFullySavedCaptureHidesItsStripSizeInsteadOfThePhotoTotal() {
        val strip = viewerInfoStrip(pair, WifiFormatPolicy.Original, setOf(WifiPhotoDownload(1), WifiPhotoDownload(2)))
        assertFalse(strip.missingFormat)
        assertEquals(0L, strip.sizeBytes, "everything saved: no size, the download area says Saved")
    }

    @Test
    fun rawOnlyUnderJpegOnlyIsMarkedAndShowsTheRawSize() {
        val strip = viewerInfoStrip(rawOnly, WifiFormatPolicy.JpegOnly)
        assertTrue(strip.missingFormat, "shown as \"Only RAW\", never downloaded silently")
        assertEquals("RAW", strip.formatText)
        assertEquals(48_000_000, strip.sizeBytes)
        assertFalse(viewerInfoStrip(rawOnly, WifiFormatPolicy.Original).missingFormat)
    }

    @Test
    fun rawOnlyStripNamesTheRawAndItsSize() {
        assertEquals(ViewerInfoStrip("RAW", 48_000_000, missingFormat = false), viewerInfoStrip(pair, WifiFormatPolicy.RawOnly))
        assertEquals(listOf(raw), viewerDownloadTargets(pair, WifiFormatPolicy.RawOnly))
        assertEquals("RAW", stripFormatLabel(pair, WifiFormatPolicy.RawOnly))
        assertEquals("RAW", stripFormatLabel(WifiCameraCapture("x", listOf(raw, heif)), WifiFormatPolicy.RawOnly))
    }

    @Test
    fun jpgOnlyCaptureUnderRawOnlyIsMarkedOnlyJpgAndNeverFetchedSilently() {
        val strip = viewerInfoStrip(jpgOnly, WifiFormatPolicy.RawOnly)
        assertTrue(strip.missingFormat, "shown as \"Only JPG\"")
        assertEquals("JPG", strip.formatText)
        assertEquals(6_600_000, strip.sizeBytes)
        assertTrue(viewerDownloadTargets(jpgOnly, WifiFormatPolicy.RawOnly).isEmpty())
        val state = viewerDownloadState(jpgOnly, WifiFormatPolicy.RawOnly, emptySet(), idle, false)
        assertEquals(ViewerDownloadPhase.NoRaw, state.phase)
        assertEquals("JPG", state.manualFormat, "manual \"Download JPG\" button")
        assertEquals(listOf(WifiPhotoDownload(1)), state.downloads)
        assertTrue(state.enabled)
        assertFalse(state.asks)
        val saved = viewerDownloadState(jpgOnly, WifiFormatPolicy.RawOnly, setOf(WifiPhotoDownload(1)), idle, false)
        assertEquals(ViewerDownloadPhase.Saved, saved.phase)
    }

    @Test
    fun rawOnlyReadyDownloadsTheRaw() {
        val state = viewerDownloadState(pair, WifiFormatPolicy.RawOnly, emptySet(), idle, false)
        assertEquals(ViewerDownloadPhase.Ready, state.phase)
        assertEquals(listOf(WifiPhotoDownload(2)), state.downloads)
        assertFalse(state.asks)
    }

    @Test
    fun askStripListsEveryFormatWithItsSizeAndNoTotal() {
        val strip = viewerInfoStrip(pair, null)
        assertEquals("JPG ${formatByteSize(6_600_000)} / RAW ${formatByteSize(48_000_000)}", strip.formatText)
        assertEquals(0L, strip.sizeBytes, "no default assumed: no single size")
        assertFalse(strip.missingFormat)
        assertEquals("JPG ${formatByteSize(6_600_000)}", viewerInfoStrip(jpgOnly, null).formatText)
        assertEquals(
            "HEIF ${formatByteSize(5_000_000)} / RAW ${formatByteSize(48_000_000)}",
            stripFormatLabel(WifiCameraCapture("x", listOf(raw, heif)), null),
        )
        assertEquals(setOf(jpg, raw), viewerDownloadTargets(pair, null).toSet())
        assertNull(stripFormatLabel(WifiCameraCapture("e", emptyList()), null))
    }

    @Test
    fun askWithSeveralFormatsOpensTheChooser() {
        val state = viewerDownloadState(pair, null, emptySet(), idle, false)
        assertEquals(ViewerDownloadPhase.Ready, state.phase)
        assertTrue(state.asks)
        assertTrue(state.enabled)
        assertEquals(listOf(WifiFormatPolicy.JpegOnly, WifiFormatPolicy.RawOnly, WifiFormatPolicy.Original), state.choices.map { it.policy })
        assertEquals(singleFormatChoices(pair, emptySet()), state.choices)
        // The chooser rows: format names with what each fetches.
        assertEquals(listOf("JPG", "RAW", "JPG + RAW"), state.choices.map { it.formatText() })
        assertEquals(listOf(listOf(WifiPhotoDownload(1)), listOf(WifiPhotoDownload(2))), state.choices.take(2).map { it.plan.downloads })
    }

    @Test
    fun askWithOneFormatDownloadsDirectly() {
        for (capture in listOf(jpgOnly, rawOnly)) {
            val state = viewerDownloadState(capture, null, emptySet(), idle, false)
            assertEquals(ViewerDownloadPhase.Ready, state.phase, capture.id)
            assertFalse(state.asks, "one format: no question")
            assertEquals(capture.files.map { WifiPhotoDownload(it.handle) }, state.downloads)
        }
        // JPG already saved: only the RAW is left — downloaded without asking.
        val rest = viewerDownloadState(pair, null, setOf(WifiPhotoDownload(1)), idle, false)
        assertFalse(rest.asks)
        assertEquals(listOf(WifiPhotoDownload(2)), rest.downloads)
        // Everything saved / too large: the usual states.
        assertEquals(
            ViewerDownloadPhase.Saved,
            viewerDownloadState(pair, null, setOf(WifiPhotoDownload(1), WifiPhotoDownload(2)), idle, false).phase,
        )
        val huge = WifiCameraCapture("x", listOf(jpg.copy(downloadable = false)))
        assertEquals(ViewerDownloadPhase.TooLarge, viewerDownloadState(huge, null, emptySet(), idle, false).phase)
        // Busy transfers win over asking.
        val batch = WifiImageTransferState(WifiImageTransferStatus.Downloading, filename = "OTHER.JPG", totalFiles = 5)
        assertEquals(ViewerDownloadPhase.OtherTransfer, viewerDownloadState(pair, null, emptySet(), batch, false).phase)
    }

    @Test
    fun missingFormatSizesUseTheSkipReasonOfThePolicy() {
        assertEquals(WifiSkipReason.NoRaw, captureDownloadSize(jpgOnly, WifiFormatPolicy.RawOnly).reason)
        assertEquals(WifiSkipReason.NoJpeg, captureDownloadSize(rawOnly, WifiFormatPolicy.JpegOnly).reason)
    }

    // ---- details -----------------------------------------------------------------------------------

    @Test
    fun detailsListEveryFileWithItsOwnSizeJpgFirst() {
        val details = viewerDetails(pair, PhotoMetadataState.Idle)
        // Format and size only: the file name is in the top bar.
        assertEquals(
            listOf(ViewerFileEntry("JPG", 6_600_000), ViewerFileEntry("RAW", 48_000_000)),
            details.files,
        )
        assertEquals("7008×4672", details.resolutionText, "the JPEG's size, never the RAW object info")
        assertTrue(details.exif.isEmpty())
        assertFalse(details.loading)
    }

    @Test
    fun unreadableValuesAreLeftOut() {
        val details = viewerDetails(rawOnly, PhotoMetadataState.Ready(metadata(2, format = PhotoFormat.Raw)))
        assertNull(details.resolutionText, "RAW-only: no trusted pixel size")
        assertNull(details.capturedAt, "no capture time in the catalog")
        assertTrue(details.exif.isEmpty(), "RAW has no readable EXIF")
        assertEquals(listOf(ViewerFileEntry("RAW", 48_000_000)), details.files)
    }

    @Test
    fun resolutionFallsBackToTheMetadataOfThePreviewFile() {
        val bare = WifiCameraCapture("b", listOf(jpg.copy(width = null, height = null)))
        assertEquals("6000×4000", viewerDetails(bare, PhotoMetadataState.Ready(metadata(1))).resolutionText)
        assertNull(viewerDetails(bare, PhotoMetadataState.Ready(metadata(99))).resolutionText,
            "metadata of another photo (late swipe) is ignored")
    }

    @Test
    fun shootingParametersFollowTheReadState() {
        val loading = viewerDetails(jpgOnly, PhotoMetadataState.Loading(1, metadata(1)))
        assertTrue(loading.loading)
        assertTrue(loading.exif.isEmpty())
        assertFalse(viewerDetails(jpgOnly, PhotoMetadataState.Loading(99, metadata(99))).loading, "another photo's read")

        val ready = viewerDetails(jpgOnly, PhotoMetadataState.Ready(metadata(1, sampleExif)))
        assertFalse(ready.loading)
        assertEquals(
            listOf(
                // Display order: lens, focal length, then the exposure grid.
                ViewerExifField.Lens to "FE 24-70mm F2.8 GM II",
                ViewerExifField.FocalLength to "35mm",
                ViewerExifField.Aperture to "f/2.8",
                ViewerExifField.Shutter to "1/250s",
                ViewerExifField.Iso to "ISO 400",
                ViewerExifField.ExposureBias to "+0.3 EV",
            ),
            ready.exif,
        )

        val failed = viewerDetails(jpgOnly, PhotoMetadataState.Unavailable(1, PhotoMetadataFailureReason.Timeout, metadata(1)))
        assertFalse(failed.loading)
        assertTrue(failed.exif.isEmpty(), "a failed read hides the rows instead of leaving them empty")
        assertEquals(listOf(ViewerFileEntry("JPG", 6_600_000)), failed.files, "the basic data stays")
    }

    @Test
    fun placeholdersOnlyForFieldsTheReadMayStillFill() {
        val idleDetails = viewerDetails(jpgOnly, PhotoMetadataState.Idle)
        assertTrue(viewerPendingExifFields(idleDetails).isEmpty(), "no read pending: no placeholder")
        val loading = viewerDetails(jpgOnly, PhotoMetadataState.Loading(1, metadata(1)))
        val pending = viewerPendingExifFields(loading)
        assertTrue(ViewerExifField.Aperture in pending && ViewerExifField.Lens in pending && ViewerExifField.Camera in pending)
        assertFalse(ViewerExifField.FocalLength35mm in pending, "an optional row gets no placeholder (it may never appear)")
        val ready = viewerDetails(jpgOnly, PhotoMetadataState.Ready(metadata(1, sampleExif)))
        assertTrue(viewerPendingExifFields(ready).isEmpty())
    }

    @Test
    fun exifRowsLeaveOutMissingValues() {
        assertTrue(viewerExifRows(null).isEmpty())
        assertTrue(viewerExifRows(metadata(1, PhotoExif())).isEmpty())
        assertEquals(
            listOf(ViewerExifField.Camera to "ILCE-7M4", ViewerExifField.Shutter to "2.5s", ViewerExifField.Iso to "ISO 100"),
            viewerExifRows(metadata(1, PhotoExif(iso = 100, exposureTimeSeconds = 2.5, lensModel = " ", cameraModel = "ILCE-7M4"))),
        )
    }

    @Test
    fun theEquivalentFocalLengthIsItsOwnRowNextToTheActualOne() {
        // Both present: two rows, "focal length 50mm" and "focal length (35 mm equiv.) 75mm".
        assertEquals(
            listOf(
                ViewerExifField.FocalLength to "50mm",
                ViewerExifField.FocalLength35mm to "75mm",
            ),
            viewerExifRows(metadata(1, PhotoExif(focalLengthMm = 50.0, focalLength35mm = 75))).map { it },
        )
        // Only the equivalent (no actual focal length): still shown.
        assertEquals(
            listOf(ViewerExifField.FocalLength35mm to "40mm"),
            viewerExifRows(metadata(1, PhotoExif(focalLength35mm = 40))),
        )
        // Same as the actual focal length (full frame): no second row.
        assertEquals(
            listOf(ViewerExifField.FocalLength to "50mm"),
            viewerExifRows(metadata(1, PhotoExif(focalLengthMm = 50.0, focalLength35mm = 50))),
        )
        // Neither: no focal row at all.
        assertTrue(viewerExifRows(metadata(1, PhotoExif())).none { it.first == ViewerExifField.FocalLength35mm })
    }

    // ---- download button ---------------------------------------------------------------------------

    private val idle = WifiImageTransferState()

    @Test
    fun readyDownloadsTheDefaultFormatPlan() {
        val jpegOnly = viewerDownloadState(pair, WifiFormatPolicy.JpegOnly, emptySet(), idle, browserLoading = false)
        assertEquals(ViewerDownloadPhase.Ready, jpegOnly.phase)
        assertEquals(listOf(WifiPhotoDownload(1)), jpegOnly.downloads)
        assertTrue(jpegOnly.enabled)
        val original = viewerDownloadState(pair, WifiFormatPolicy.Original, emptySet(), idle, browserLoading = false)
        assertEquals(setOf(WifiPhotoDownload(1), WifiPhotoDownload(2)), original.downloads.toSet())
    }

    @Test
    fun savedFilesGreyTheButton() {
        val saved = viewerDownloadState(pair, WifiFormatPolicy.JpegOnly, setOf(WifiPhotoDownload(1)), idle, false)
        assertEquals(ViewerDownloadPhase.Saved, saved.phase)
        assertFalse(saved.enabled)
        // Original with only the JPEG saved still has the RAW to fetch.
        val partly = viewerDownloadState(pair, WifiFormatPolicy.Original, setOf(WifiPhotoDownload(1)), idle, false)
        assertEquals(ViewerDownloadPhase.Ready, partly.phase)
        assertEquals(listOf(WifiPhotoDownload(2)), partly.downloads)
    }

    @Test
    fun rawOnlyUnderJpegOnlyOffersAManualRawDownloadNeverASilentOne() {
        val state = viewerDownloadState(rawOnly, WifiFormatPolicy.JpegOnly, emptySet(), idle, false)
        assertEquals(ViewerDownloadPhase.NoJpeg, state.phase)
        assertEquals("RAW", state.manualFormat)
        assertEquals(listOf(WifiPhotoDownload(2)), state.downloads)
        assertTrue(state.enabled)
        val savedRaw = viewerDownloadState(rawOnly, WifiFormatPolicy.JpegOnly, setOf(WifiPhotoDownload(2)), idle, false)
        assertEquals(ViewerDownloadPhase.Saved, savedRaw.phase)
    }

    @Test
    fun tooLargeIsDisabledWithItsReason() {
        val huge = WifiCameraCapture("x", listOf(jpg.copy(downloadable = false)))
        val state = viewerDownloadState(huge, WifiFormatPolicy.JpegOnly, emptySet(), idle, false)
        assertEquals(ViewerDownloadPhase.TooLarge, state.phase)
        assertFalse(state.enabled)
    }

    @Test
    fun runningTransfersAndLoadingDisableTheButton() {
        val ours = WifiImageTransferState(WifiImageTransferStatus.Downloading, filename = "DSC1.ARW", bytesReceived = 1, totalBytes = 4)
        assertEquals(ViewerDownloadPhase.Downloading, viewerDownloadState(pair, WifiFormatPolicy.Original, emptySet(), ours, false).phase)
        val unnamed = WifiImageTransferState(WifiImageTransferStatus.Converting)
        assertEquals(ViewerDownloadPhase.Downloading, viewerDownloadState(pair, WifiFormatPolicy.Original, emptySet(), unnamed, false).phase)
        val batch = WifiImageTransferState(WifiImageTransferStatus.Downloading, filename = "OTHER.JPG", totalFiles = 5)
        val busy = viewerDownloadState(pair, WifiFormatPolicy.Original, emptySet(), batch, false)
        assertEquals(ViewerDownloadPhase.OtherTransfer, busy.phase)
        assertFalse(busy.enabled)
        val loading = viewerDownloadState(pair, WifiFormatPolicy.Original, emptySet(), idle, browserLoading = true)
        assertEquals(ViewerDownloadPhase.Waiting, loading.phase)
        assertFalse(loading.enabled)
        // A finished (failed) transfer gives the button back.
        val failed = WifiImageTransferState(WifiImageTransferStatus.Failed, filename = "DSC1.JPG")
        assertEquals(ViewerDownloadPhase.Ready, viewerDownloadState(pair, WifiFormatPolicy.JpegOnly, emptySet(), failed, false).phase)
    }

    @Test
    fun transferFractionIsClampedAndUnknownWithoutASize() {
        assertNull(viewerTransferFraction(WifiImageTransferState(WifiImageTransferStatus.Downloading)))
        assertEquals(0.25f, viewerTransferFraction(WifiImageTransferState(WifiImageTransferStatus.Downloading, bytesReceived = 1, totalBytes = 4)))
        assertEquals(1f, viewerTransferFraction(WifiImageTransferState(WifiImageTransferStatus.Downloading, bytesReceived = 9, totalBytes = 4)))
    }

    // ---- top bar / filmstrip -----------------------------------------------------------------------

    @Test
    fun positionTotalIsTheDayForGroupedBrowsing() {
        assertEquals(12, viewerPositionTotal(grouped = true, captureCount = 12, totalObjects = 30))
        assertEquals(30, viewerPositionTotal(grouped = false, captureCount = 12, totalObjects = 30))
    }

    @Test
    fun captureTimeIsShownAsLocalDateAndTime() {
        assertEquals("2026-11-09 14:32:08", viewerCaptureTimeText("20261109T143208"))
        assertEquals("2026-11-09 14:32:08", viewerCaptureTimeText("20261109T143208.5Z"))
        assertEquals("2026-11-09 14:32:08", viewerCaptureTimeText("2026-11-09T14:32:08"))
        assertEquals("2026-11-09 14:32", viewerCaptureTimeText("2026-11-09T14:32+08:00"))
        assertEquals("odd", viewerCaptureTimeText("odd"))
        assertNull(viewerCaptureTimeText(" "))
    }

    @Test
    fun filmstripAsksForTheShownVisibleAndAMarginOnly() {
        val (now, margin) = filmstripThumbnailDemand(current = 50, visible = 47..55, count = 300)
        assertEquals(50, now.first(), "the shown photo comes first")
        assertEquals((47..55).toSet(), now.toSet())
        assertEquals(((43..46) + (56..59)).toSet(), margin.toSet())
        assertTrue(now.size + margin.size < 300, "never the whole day")
        val (edgeNow, edgeMargin) = filmstripThumbnailDemand(current = 0, visible = IntRange.EMPTY, count = 3)
        assertEquals(listOf(0), edgeNow)
        assertEquals(listOf(1, 2), edgeMargin)
        assertEquals(emptyList<Int>() to emptyList(), filmstripThumbnailDemand(0, IntRange.EMPTY, 0))
    }

    // ---- histogram ---------------------------------------------------------------------------------

    @Test
    fun lumaUsesRec601Weights() {
        assertEquals(0, lumaOf(0xFF000000.toInt()))
        assertEquals(255, lumaOf(0xFFFFFFFF.toInt()))
        assertEquals(77, lumaOf(0xFFFF0000.toInt()))
        assertEquals(149, lumaOf(0xFF00FF00.toInt()))
        assertEquals(29, lumaOf(0xFF0000FF.toInt()))
        assertEquals(lumaOf(0xFF808080.toInt()), lumaOf(0x00808080), "alpha is ignored")
    }

    @Test
    fun histogramCountsEveryPixelIntoItsBin() {
        val pixels = IntArray(100) { if (it < 75) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val bins = luminanceHistogram(pixels, binCount = 4)
        assertContentEquals(intArrayOf(75, 0, 0, 25), bins)
        assertEquals(64, luminanceHistogram(pixels).size)
        val grey = luminanceHistogram(IntArray(10) { 0xFF808080.toInt() }, binCount = 4)
        assertContentEquals(intArrayOf(0, 0, 10, 0), grey)
    }

    @Test
    fun accumulateHonoursStepAndRange() {
        val pixels = IntArray(10) { 0xFFFFFFFF.toInt() }
        val bins = IntArray(2)
        accumulateLuminance(pixels, bins, from = 1, count = 8, step = 3)
        assertContentEquals(intArrayOf(0, 3), bins) // indices 1, 4, 7
    }

    @Test
    fun sampleStepBoundsTheWorkOfLargePreviews() {
        assertEquals(1, histogramSampleStep(100, 100))
        assertEquals(1, histogramSampleStep(0, 100))
        val step = histogramSampleStep(6000, 4000)
        val samples = ((6000 + step - 1) / step).toLong() * ((4000 + step - 1) / step)
        assertTrue(samples <= HISTOGRAM_MAX_SAMPLES, "samples=$samples step=$step")
        assertTrue(step > 1)
        assertEquals(2, histogramSampleStep(4, 4, maxSamples = 4))
    }

    @Test
    fun normalizedHistogramScalesToTheTallestBin() {
        assertContentEquals(floatArrayOf(0f, 0f), normalizedHistogram(intArrayOf(0, 0)))
        val scaled = normalizedHistogram(intArrayOf(0, 25, 100))
        assertEquals(0f, scaled[0])
        assertEquals(0.5f, scaled[1])
        assertEquals(1f, scaled[2])
    }

    @Test
    fun actionSlotIsTheTouchTargetUnlessALabelLineNeedsMore() {
        assertEquals(48.dp, viewerActionSlotHeight(20.dp))
        assertEquals(48.dp, viewerActionSlotHeight(40.dp))
        assertEquals(48.dp, viewerActionSlotHeight(0.dp))
        assertEquals(52.dp, viewerActionSlotHeight(44.dp))
    }

    @Test
    fun aTransferResultIsAnnouncedOnceAndANewTransferClearsIt() {
        listOf(WifiImageTransferStatus.Failed, WifiImageTransferStatus.Cancelled, WifiImageTransferStatus.ConversionFailed, WifiImageTransferStatus.PermissionDenied).forEach { result ->
            assertEquals(ViewerNoticeAction.Show, viewerNoticeAction(WifiImageTransferStatus.Downloading, result), "$result")
            assertEquals(ViewerNoticeAction.Show, viewerNoticeAction(WifiImageTransferStatus.Converting, result), "$result")
            // Recomposition / a viewer opened on an old result: nothing.
            assertEquals(ViewerNoticeAction.None, viewerNoticeAction(result, result), "$result")
            assertEquals(ViewerNoticeAction.Clear, viewerNoticeAction(result, WifiImageTransferStatus.Downloading), "$result")
        }
        assertEquals(ViewerNoticeAction.Clear, viewerNoticeAction(WifiImageTransferStatus.Idle, WifiImageTransferStatus.Downloading))
        assertEquals(ViewerNoticeAction.None, viewerNoticeAction(WifiImageTransferStatus.Downloading, WifiImageTransferStatus.Downloading))
        // A save shows as the greyed "Saved" button, not a message.
        assertEquals(ViewerNoticeAction.None, viewerNoticeAction(WifiImageTransferStatus.Downloading, WifiImageTransferStatus.Saved))
        assertEquals(ViewerNoticeAction.None, viewerNoticeAction(WifiImageTransferStatus.Failed, WifiImageTransferStatus.Idle))
    }
}
