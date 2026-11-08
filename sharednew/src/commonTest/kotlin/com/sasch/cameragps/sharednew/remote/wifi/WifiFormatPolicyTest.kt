package com.sasch.cameragps.sharednew.remote.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WifiFormatPolicyTest {
    private val jpegMime = "image/jpeg"
    private val rawMime = "image/x-sony-arw"
    private val heifMime = "image/heif"

    private fun file(handle: Long, mime: String, size: Long = 1000, downloadable: Boolean = true) =
        WifiCameraPhoto(handle, "IMG$handle", size, mime, "", downloadable, captureId = "c$handle")

    private fun capture(id: String, vararg files: WifiCameraPhoto) = WifiCameraCapture(id, files.toList())

    private val jpgRaw = capture("jpgRaw", file(1, jpegMime, 2000), file(2, rawMime, 30000))
    private val rawJpg = capture("rawJpg", file(3, rawMime), file(4, jpegMime, 500))
    private val rawOnly = capture("rawOnly", file(5, rawMime, 40000))
    private val jpgOnly = capture("jpgOnly", file(6, jpegMime, 1500))
    private val heifOnly = capture("heifOnly", file(7, heifMime, 900))
    private val heifRaw = capture("heifRaw", file(8, heifMime, 900), file(9, rawMime))
    private val multi = capture(
        "multi", file(10, rawMime), file(11, heifMime), file(12, jpegMime, 800)
    )

    // --- JPG target -----------------------------------------------------------------

    @Test
    fun jpegTargetPicksTheJpegFileAndNeverTheRaw() {
        assertEquals(WifiPhotoDownload(1), jpgRaw.jpegTarget(false)?.download)
        assertEquals(WifiPhotoDownload(4), rawJpg.jpegTarget(true)?.download)
        assertEquals(WifiPhotoDownload(12), multi.jpegTarget(true)?.download, "A real JPEG beats a HEIF conversion")
        assertEquals(2000L, jpgRaw.jpegTarget(false)?.knownSize)
        assertNull(rawOnly.jpegTarget(true))
        assertNull(rawOnly.jpegTarget(false))
    }

    @Test
    fun heifOnlyCapturesUseTheJpegTranscodeOnlyWhenThePlatformCanConvert() {
        val converted = heifOnly.jpegTarget(true)
        assertEquals(WifiPhotoDownload(7, WifiPhotoDownloadFormat.Jpeg), converted?.download)
        assertTrue(converted!!.converted)
        assertNull(converted.knownSize, "The transcoded size is unknown")
        assertNull(heifOnly.jpegTarget(false))
        assertNull(heifRaw.jpegTarget(false))
    }

    @Test
    fun jpegTargetPrefersADownloadableJpegButStillReportsAnOversizedOne() {
        val big = file(20, jpegMime, 600_000_000, downloadable = false)
        val small = file(21, jpegMime, 10)
        assertEquals(21L, capture("a", big, small).jpegTarget(false)?.file?.handle)
        val only = capture("b", big).jpegTarget(false)
        assertEquals(20L, only?.file?.handle)
        assertFalse(only!!.file.downloadable)
    }

    // --- grid badge -----------------------------------------------------------------

    @Test
    fun originalBadgeListsEveryFormat() {
        assertEquals(WifiFormatBadge.Label("JPEG + RAW"), jpgRaw.formatBadge(WifiFormatPolicy.Original, false))
        assertEquals(WifiFormatBadge.Label("RAW + JPEG"), rawJpg.formatBadge(WifiFormatPolicy.Original, true))
        assertEquals(WifiFormatBadge.Label("RAW"), rawOnly.formatBadge(WifiFormatPolicy.Original, true))
        assertEquals(WifiFormatBadge.Label("RAW + HEIF + JPEG"), multi.formatBadge(WifiFormatPolicy.Original, true))
    }

    @Test
    fun jpegOnlyBadgeIsHiddenExceptForRawOnlyPhotos() {
        val policy = WifiFormatPolicy.JpegOnly
        assertEquals(WifiFormatBadge.None, jpgRaw.formatBadge(policy, false), "No '+ RAW' in JPG mode")
        assertEquals(WifiFormatBadge.None, rawJpg.formatBadge(policy, false))
        assertEquals(WifiFormatBadge.None, jpgOnly.formatBadge(policy, false))
        assertEquals(WifiFormatBadge.None, multi.formatBadge(policy, false))
        assertEquals(WifiFormatBadge.RawOnly, rawOnly.formatBadge(policy, true))
        assertEquals(WifiFormatBadge.RawOnly, rawOnly.formatBadge(policy, false))
    }

    @Test
    fun jpegOnlyBadgeNeverMentionsRawEvenWithoutAUsableJpeg() {
        val policy = WifiFormatPolicy.JpegOnly
        // HEIF the platform cannot convert: name the HEIF, hide the RAW companion.
        assertEquals(WifiFormatBadge.Label("HEIF"), heifRaw.formatBadge(policy, false))
        assertEquals(WifiFormatBadge.Label("HEIF"), heifOnly.formatBadge(policy, false))
        assertEquals(WifiFormatBadge.None, heifRaw.formatBadge(policy, true))
    }

    // --- details page ---------------------------------------------------------------

    @Test
    fun originalDetailsKeepTheFormatChooser() {
        listOf(jpgRaw, rawOnly, heifOnly, multi).forEach {
            assertEquals(WifiDetailPlan.ChooseFormat, it.detailPlan(WifiFormatPolicy.Original, true))
        }
    }

    @Test
    fun jpegOnlyDetailsOfferOneJpegDownload() {
        val plan = jpgRaw.detailPlan(WifiFormatPolicy.JpegOnly, true)
        assertIs<WifiDetailPlan.DownloadJpeg>(plan)
        assertEquals(WifiPhotoDownload(1), plan.target.download)
        assertEquals(WifiPhotoDownload(12), (multi.detailPlan(WifiFormatPolicy.JpegOnly, true) as WifiDetailPlan.DownloadJpeg).target.download)
        assertEquals(
            WifiPhotoDownload(7, WifiPhotoDownloadFormat.Jpeg),
            (heifOnly.detailPlan(WifiFormatPolicy.JpegOnly, true) as WifiDetailPlan.DownloadJpeg).target.download
        )
    }

    @Test
    fun jpegOnlyDetailsOfRawOnlyPhotosSaySoAndOfferTheRawByHand() {
        val plan = rawOnly.detailPlan(WifiFormatPolicy.JpegOnly, true)
        assertIs<WifiDetailPlan.NoJpeg>(plan)
        assertEquals(5L, plan.fallback?.handle)
        // HEIF the platform cannot convert: no JPG either; the manual choice prefers a RAW.
        val heif = heifRaw.detailPlan(WifiFormatPolicy.JpegOnly, false)
        assertIs<WifiDetailPlan.NoJpeg>(heif)
        assertEquals(9L, heif.fallback?.handle)
        assertEquals(7L, (heifOnly.detailPlan(WifiFormatPolicy.JpegOnly, false) as WifiDetailPlan.NoJpeg).fallback?.handle)
    }

    @Test
    fun singlePhotoDefaultNeverAutoSelectsAJpegOnlyRawEither() {
        // Both modes go through the plan: JpegOnly never reaches the radio list, so the
        // default handle only matters for Original (first downloadable file).
        assertEquals(1L, defaultSelectedPhotoHandle(jpgRaw.files))
        assertEquals(3L, defaultSelectedPhotoHandle(rawJpg.files))
    }

    // --- batch plan -----------------------------------------------------------------

    @Test
    fun jpegOnlyBatchDownloadsOneJpegPerCaptureAndSkipsRawOnlyPhotos() {
        val plan = jpegOnlyBatchPlan(listOf(jpgRaw, rawOnly, rawJpg, jpgOnly), emptySet(), true)
        assertEquals(
            listOf(WifiPhotoDownload(1), WifiPhotoDownload(4), WifiPhotoDownload(6)),
            plan.downloads, "Selection order, JPGs only — never a RAW"
        )
        assertEquals(2000L + 500 + 1500, plan.totalBytes)
        assertEquals(mapOf(WifiSkipReason.NoJpeg to 1), plan.skipped)
        assertEquals(1, plan.skippedCount)
        assertEquals(WifiBatchSummary(3, mapOf(WifiSkipReason.NoJpeg to 1)), plan.toSummary())
    }

    @Test
    fun jpegOnlyBatchSkipsSavedAndOversizedAndDuplicates() {
        val big = capture("big", file(30, jpegMime, 600_000_000, downloadable = false), file(31, rawMime))
        val plan = jpegOnlyBatchPlan(
            listOf(jpgRaw, jpgRaw, jpgOnly, rawOnly, big, heifOnly),
            saved = setOf(WifiPhotoDownload(6)),
            canConvertHeif = false,
        )
        assertEquals(listOf(WifiPhotoDownload(1)), plan.downloads, "A repeated capture counts once")
        assertEquals(
            mapOf(WifiSkipReason.AlreadySaved to 1, WifiSkipReason.NoJpeg to 2, WifiSkipReason.TooLarge to 1),
            plan.skipped
        )
        assertEquals(4, plan.skippedCount)
    }

    @Test
    fun jpegOnlyBatchTranscodesHeifOnlyCapturesWhenSupported() {
        val plan = jpegOnlyBatchPlan(listOf(heifOnly, heifRaw), emptySet(), true)
        assertEquals(
            listOf(
                WifiPhotoDownload(7, WifiPhotoDownloadFormat.Jpeg),
                WifiPhotoDownload(8, WifiPhotoDownloadFormat.Jpeg)
            ), plan.downloads
        )
        // A saved original HEIF does not suppress the JPEG copy.
        assertEquals(
            2,
            jpegOnlyBatchPlan(listOf(heifOnly, heifRaw), setOf(WifiPhotoDownload(7)), true).downloads.size
        )
        assertTrue(jpegOnlyBatchPlan(listOf(heifOnly), emptySet(), false).downloads.isEmpty())
    }

    @Test
    fun jpegOnlyBatchOfOnlyRawPhotosIsEmpty() {
        val plan = jpegOnlyBatchPlan(listOf(rawOnly), emptySet(), true)
        assertTrue(plan.downloads.isEmpty())
        assertEquals(0L, plan.totalBytes)
        assertEquals(1, plan.skippedCount)
        assertTrue(jpegOnlyBatchPlan(emptyList(), emptySet(), true).downloads.isEmpty())
    }

    @Test
    fun jpegOnlyPlanAgreesWithWhatTheControllerWouldAccept() {
        // Every planned download must be a file the original-format batch logic would also
        // pick from the same selection, so the plan can never promise more than it can send.
        val captures = listOf(jpgRaw, rawJpg, jpgOnly, multi)
        val planned = jpegOnlyBatchPlan(captures, emptySet(), true).downloads.toSet()
        val allowed = photoBatchDownloads(captures, setOf(WifiPhotoBatchFormat.Jpeg), emptySet(), true).toSet()
        assertEquals(allowed, planned)
    }

    // --- batch result summary -------------------------------------------------------

    @Test
    fun summaryIsOnlyCompleteWhenEveryPlannedFileWasSaved() {
        val summary = WifiBatchSummary(3, mapOf(WifiSkipReason.NoJpeg to 2))
        val saved = WifiImageTransferState(WifiImageTransferStatus.Saved, completedFiles = 3, totalFiles = 3)
        assertTrue(summary.isComplete(saved))
        assertEquals(2, summary.skippedCount)
        // Still running: the status stays Downloading until the last file.
        assertFalse(summary.isComplete(saved.copy(status = WifiImageTransferStatus.Downloading, completedFiles = 2)))
        // Fail-stop: a failure part-way is a failure, not a summary.
        assertFalse(summary.isComplete(saved.copy(status = WifiImageTransferStatus.Failed, completedFiles = 1)))
        assertFalse(summary.isComplete(saved.copy(status = WifiImageTransferStatus.Cancelled, completedFiles = 1)))
        // A different transfer (e.g. a single download from the viewer) is not this batch.
        assertFalse(summary.isComplete(saved.copy(completedFiles = 1, totalFiles = 1)))
        assertFalse(WifiBatchSummary(0, mapOf(WifiSkipReason.NoJpeg to 1)).isComplete(WifiImageTransferState()))
    }

    @Test
    fun settledBatchSummaryOnlyShowsWhenTheTransferIsQuietAndSuccessful() {
        // Truth table over the transfer statuses; summarySettled=true throughout.
        for (status in WifiImageTransferStatus.entries) {
            val transfer = WifiImageTransferState(status)
            val expected = when (status) {
                WifiImageTransferStatus.Saved, WifiImageTransferStatus.Idle -> true
                else -> false
            }
            assertEquals(
                expected, shouldShowBatchSummary(true, transfer),
                "settled summary visibility for $status",
            )
        }
        // Without a settled summary the transfer status always shows.
        for (status in WifiImageTransferStatus.entries) {
            assertFalse(
                shouldShowBatchSummary(false, WifiImageTransferState(status)),
                "unsettled summary must never show, even for $status",
            )
        }
    }

    // --- sizes ----------------------------------------------------------------------

    @Test
    fun byteSizesAreFormattedCompactly() {
        assertEquals("0 KB", formatByteSize(0))
        assertEquals("1 KB", formatByteSize(1))
        assertEquals("512 KB", formatByteSize(512L * 1024))
        assertEquals("1.0 MB", formatByteSize(1024L * 1024))
        assertEquals("4.8 MB", formatByteSize((4.8 * 1024 * 1024).toLong() + 1024))
        assertEquals("48 MB", formatByteSize(48L * 1024 * 1024))
        assertEquals("1.2 GB", formatByteSize((1.25 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun policyFollowsTheSetting() {
        assertEquals(WifiFormatPolicy.JpegOnly, WifiFormatPolicy.of(true))
        assertEquals(WifiFormatPolicy.Original, WifiFormatPolicy.of(false))
    }
}
