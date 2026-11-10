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
    fun onlyParametersOrLensNeedTheExtendedRead() {
        for (histogram in listOf(false, true)) for (params in listOf(false, true)) for (lens in listOf(false, true)) {
            assertEquals(
                params || lens,
                viewerNeedsExtendedMetadata(ViewerInfoOptions(histogram, params, lens)),
                "histogram=$histogram params=$params lens=$lens",
            )
        }
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
    fun stripShowsJpegResolutionAndTheDefaultFormatSizeOnce() {
        val jpegOnly = viewerInfoStrip(pair, WifiFormatPolicy.JpegOnly, PhotoMetadataState.Idle, ViewerInfoOptions())
        assertEquals("JPG + RAW", jpegOnly.formatText)
        assertEquals("7008×4672", jpegOnly.resolutionText, "the JPEG's size, never the RAW object info")
        assertEquals(6_600_000, jpegOnly.sizeBytes)
        assertFalse(jpegOnly.noJpeg)
        val original = viewerInfoStrip(pair, WifiFormatPolicy.Original, PhotoMetadataState.Idle, ViewerInfoOptions())
        assertEquals(54_600_000, original.sizeBytes, "Original downloads every file: the sum")
        assertEquals(ViewerExtrasState.None, original.extras)
    }

    @Test
    fun saveStatusDoesNotChangeTheShownSize() {
        // The plan of captureDetails drops saved files; the strip keeps the intended size.
        val strip = viewerInfoStrip(jpgOnly, WifiFormatPolicy.JpegOnly, PhotoMetadataState.Idle, ViewerInfoOptions())
        assertEquals(6_600_000, strip.sizeBytes)
    }

    @Test
    fun rawOnlyUnderJpegOnlyIsMarkedAndShowsTheRawSize() {
        val strip = viewerInfoStrip(rawOnly, WifiFormatPolicy.JpegOnly, PhotoMetadataState.Idle, ViewerInfoOptions())
        assertTrue(strip.noJpeg)
        assertEquals("RAW", strip.formatText)
        assertNull(strip.resolutionText, "RAW shows just RAW")
        assertEquals(48_000_000, strip.sizeBytes)
        assertFalse(viewerInfoStrip(rawOnly, WifiFormatPolicy.Original, PhotoMetadataState.Idle, ViewerInfoOptions()).noJpeg)
    }

    @Test
    fun resolutionFallsBackToTheMetadataOfThePreviewFile() {
        val bare = WifiCameraCapture("b", listOf(jpg.copy(width = null, height = null)))
        val ready = PhotoMetadataState.Ready(metadata(1))
        assertEquals("6000×4000", viewerInfoStrip(bare, WifiFormatPolicy.JpegOnly, ready, ViewerInfoOptions()).resolutionText)
        val stale = PhotoMetadataState.Ready(metadata(99))
        assertNull(viewerInfoStrip(bare, WifiFormatPolicy.JpegOnly, stale, ViewerInfoOptions()).resolutionText,
            "metadata of another photo (late swipe) is ignored")
    }

    @Test
    fun extrasFollowTheTogglesAndTheReadState() {
        val all = ViewerInfoOptions(showHistogram = true, showExifParams = true, showLens = true)
        val loading = viewerInfoStrip(jpgOnly, WifiFormatPolicy.JpegOnly, PhotoMetadataState.Loading(1, metadata(1)), all)
        assertEquals(ViewerExtrasState.Loading, loading.extras)
        assertNull(loading.exifParams)

        val ready = viewerInfoStrip(jpgOnly, WifiFormatPolicy.JpegOnly, PhotoMetadataState.Ready(metadata(1, sampleExif)), all)
        assertEquals(ViewerExtrasState.Ready, ready.extras)
        assertEquals("f/2.8  1/250s  ISO 400  35mm  +0.3 EV", ready.exifParams)
        assertEquals("FE 24-70mm F2.8 GM II", ready.lens)
        assertTrue(ready.showHistogram)

        val lensOnly = viewerInfoStrip(
            jpgOnly, WifiFormatPolicy.JpegOnly, PhotoMetadataState.Ready(metadata(1, sampleExif)),
            ViewerInfoOptions(showLens = true),
        )
        assertNull(lensOnly.exifParams)
        assertEquals("FE 24-70mm F2.8 GM II", lensOnly.lens)
        assertFalse(lensOnly.showHistogram)

        val off = viewerInfoStrip(jpgOnly, WifiFormatPolicy.JpegOnly, PhotoMetadataState.Ready(metadata(1, sampleExif)), ViewerInfoOptions())
        assertEquals(ViewerExtrasState.None, off.extras)
        assertNull(off.exifParams)
        assertNull(off.lens)
    }

    @Test
    fun aFailedReadKeepsTheBasicLineAndDropsTheExtras() {
        val all = ViewerInfoOptions(showHistogram = true, showExifParams = true, showLens = true)
        val failed = viewerInfoStrip(
            jpgOnly, WifiFormatPolicy.JpegOnly,
            PhotoMetadataState.Unavailable(1, PhotoMetadataFailureReason.Timeout, metadata(1)), all,
        )
        assertEquals(ViewerExtrasState.None, failed.extras)
        assertNull(failed.exifParams)
        assertEquals("JPG", failed.formatText)
        assertEquals(6_600_000, failed.sizeBytes)
        assertTrue(failed.showHistogram, "the histogram comes from the preview, not the read")
        // RAW has no readable EXIF: the basic Ready result shows no extras either.
        val rawReady = viewerInfoStrip(rawOnly, WifiFormatPolicy.Original, PhotoMetadataState.Ready(metadata(2, format = PhotoFormat.Raw)), all)
        assertEquals(ViewerExtrasState.None, rawReady.extras)
    }

    @Test
    fun exifParamsLeaveOutMissingValues() {
        assertNull(viewerExifParamsText(null))
        assertNull(viewerExifParamsText(metadata(1, PhotoExif())))
        assertEquals("2.5s  ISO 100", viewerExifParamsText(metadata(1, PhotoExif(iso = 100, exposureTimeSeconds = 2.5))))
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
}
