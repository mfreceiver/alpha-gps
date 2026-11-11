package com.sasch.cameragps.sharednew.remote.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    // --- planner ---------------------------------------------------------------------

    private val jpegOnly = WifiFormatPolicy.JpegOnly
    private val original = WifiFormatPolicy.Original

    private fun handles(plan: WifiDownloadPlan) = plan.files.map { it.handle }

    @Test
    fun jpegOnlyDownloadsOnlyTheExistingJpegBytesInSelectionOrder() {
        val plan = planDownloads(listOf(jpgRaw, rawOnly, rawJpg, jpgOnly), jpegOnly, emptySet())
        assertEquals(listOf(1L, 4L, 6L), handles(plan), "Selection order, JPEG files only — never a RAW")
        assertTrue(plan.downloads.all { it.format == WifiPhotoDownloadFormat.Original }, "never a transcode")
        assertEquals(2000L + 500 + 1500, plan.totalBytes)
        assertEquals(mapOf(WifiSkipReason.NoJpeg to 1), plan.skipped)
        assertEquals(WifiBatchSummary(3, mapOf(WifiSkipReason.NoJpeg to 1)), plan.toSummary())
    }

    @Test
    fun jpegOnlySkipsHeifOnlyAndHeifRawCapturesAsNoJpegWithoutTranscodeOrFallback() {
        val plan = planDownloads(listOf(heifOnly, heifRaw, rawOnly), jpegOnly, emptySet())
        assertTrue(plan.files.isEmpty())
        assertEquals(mapOf(WifiSkipReason.NoJpeg to 3), plan.skipped)
        assertEquals(WifiSkipReason.NoJpeg, plan.mainSkipReason)
        // A real JPEG next to a HEIF still downloads the JPEG file itself.
        assertEquals(listOf(12L), handles(planDownloads(listOf(multi), jpegOnly, emptySet())))
    }

    @Test
    fun jpegOnlyPrefersADownloadableJpegAndCountsSavedOversizedAndDuplicates() {
        val big = file(20, jpegMime, 600_000_000, downloadable = false)
        val small = file(21, jpegMime, 10)
        assertEquals(listOf(21L), handles(planDownloads(listOf(capture("a", big, small)), jpegOnly, emptySet())))
        val tooBig = planDownloads(listOf(capture("b", big, file(22, rawMime))), jpegOnly, emptySet())
        assertTrue(tooBig.files.isEmpty())
        assertEquals(mapOf(WifiSkipReason.TooLarge to 1), tooBig.skipped)
        val plan = planDownloads(
            listOf(jpgRaw, jpgRaw, jpgOnly, rawOnly, heifOnly),
            jpegOnly, saved = setOf(WifiPhotoDownload(6)),
        )
        assertEquals(listOf(1L), handles(plan), "A repeated capture counts once")
        assertEquals(mapOf(WifiSkipReason.AlreadySaved to 1, WifiSkipReason.NoJpeg to 2), plan.skipped.filterValues { it > 0 })
        assertEquals(3, plan.skippedCount)
    }

    @Test
    fun originalDownloadsEveryOriginalFileOfEveryCapture() {
        val plan = planDownloads(listOf(jpgRaw, heifRaw, rawOnly), original, emptySet())
        assertEquals(listOf(1L, 2L, 8L, 9L, 5L), handles(plan), "JPG+RAW and HEIF+RAW both come down, no picker")
        assertTrue(plan.downloads.all { it.format == WifiPhotoDownloadFormat.Original })
        assertEquals(2000L + 30000 + 900 + 1000 + 40000, plan.totalBytes)
        assertEquals(0, plan.skippedCount)
    }

    @Test
    fun originalSkipsSavedAndOversizedFilesOneByOne() {
        val big = file(30, jpegMime, 600_000_000, downloadable = false)
        val plan = planDownloads(
            listOf(jpgRaw, capture("big", big, file(31, rawMime)), jpgRaw),
            original, saved = setOf(WifiPhotoDownload(2)),
        )
        assertEquals(listOf(1L, 31L), handles(plan), "the saved RAW and the oversized JPG are skipped, the rest stays")
        assertEquals(mapOf(WifiSkipReason.AlreadySaved to 2, WifiSkipReason.TooLarge to 1), plan.skipped)
    }

    @Test
    fun anEmptyPlanSaysWhyAndTheSamePlanServesASingleCapture() {
        assertTrue(planDownloads(emptyList(), original, emptySet()).files.isEmpty())
        assertNull(planDownloads(emptyList(), original, emptySet()).mainSkipReason)
        val saved = setOf(WifiPhotoDownload(6))
        val single = planDownloads(listOf(jpgOnly), jpegOnly, saved)
        assertTrue(single.files.isEmpty())
        assertEquals(WifiSkipReason.AlreadySaved, single.mainSkipReason)
        assertEquals(listOf(1L, 2L), handles(planDownloads(listOf(jpgRaw), original, saved)))
    }

    // --- grid badge -----------------------------------------------------------------

    @Test
    fun badgeReadsOnlyTheFileFormatsAndNeverThePolicyOrPlatform() {
        assertEquals("J&R", jpgRaw.formatBadge())
        assertEquals("J&R", rawJpg.formatBadge(), "Order of the files does not matter")
        assertEquals("JPG", jpgOnly.formatBadge())
        assertEquals("RAW", rawOnly.formatBadge())
        assertEquals("HEIF", heifOnly.formatBadge(), "A HEIF is shown as HEIF, never disguised as JPG")
        assertEquals("H&R", heifRaw.formatBadge())
        assertEquals("J&H&R", multi.formatBadge())
    }

    @Test
    fun badgeIsAbsentWhenNoFileHasAKnownFormat() {
        assertNull(capture("x", file(30, "application/octet-stream")).formatBadge())
        assertNull(WifiCameraCapture("empty", emptyList()).formatBadge())
        assertEquals("JPG", capture("y", file(31, jpegMime), file(32, "application/octet-stream")).formatBadge())
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
        // Nothing submitted (everything skipped) is a local result: settled without any transfer.
        val none = WifiBatchSummary(0, mapOf(WifiSkipReason.NoJpeg to 1))
        assertTrue(none.isComplete(WifiImageTransferState()))
        assertTrue(none.isComplete(saved.copy(status = WifiImageTransferStatus.Failed)))
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
        assertEquals("1.3 GB", formatByteSize((1.25 * 1024 * 1024 * 1024).toLong()), "half up, not truncated")
        assertEquals("25 MB", formatByteSize(245L * 1024 * 1024 / 10))
        assertEquals("6.6 MB", formatByteSize(66L * 1024 * 1024 / 10))
    }

    @Test
    fun sizeNumbersRoundOnceFromTheRawBytes() {
        val mb = 1024L * 1024L
        // 25,648,168 B = 24.4599 MiB: the old tenths-then-integer path gave 24.5 -> "25".
        assertEquals("24 MB", formatByteSize(25_648_168))
        assertEquals("24", formatSizeNumber(25_648_168))
        assertEquals("25", formatSizeNumber(mb * 49 / 2), "24.5 MiB exactly rounds half up")
        assertEquals("24", formatSizeNumber(mb * 49 / 2 - 1), "one byte below 24.5 MiB")
        // 9.95 MiB is the first value that would round to "10.0": it shows the integer "10" by rule.
        assertEquals("10", formatSizeNumber(mb * 199 / 20 + 1), "9.95 MiB (first byte at/after it) carries into 10")
        assertEquals("9.9", formatSizeNumber(mb * 199 / 20 - 1), "truncated 9.95 MiB is a hair below it: stays one decimal")
        assertEquals("10", formatSizeNumber(10 * mb))
        assertEquals("6.6", formatSizeNumber(mb * 66 / 10))
        assertEquals("1.3", formatSizeNumber((1.25 * mb).toLong()), "1.25 half up")
        assertEquals("1.3 GB", formatByteSize((1.25 * 1024 * mb).toLong()), "1.25 GiB half up")
        assertEquals("10 GB", formatByteSize(mb * 1024 * 996 / 100), "9.96 GiB carries into 10")
        assertEquals("24 GB", formatByteSize(1024 * mb * 2446 / 100), "24.46 GiB rounds once")
        assertNull(formatSizeNumber(0))
        assertNull(formatSizeNumber(-5))
        assertEquals("0 KB", formatByteSize(0))
        assertEquals("0 KB", formatByteSize(-1))
    }

    @Test
    fun policyFollowsTheSetting() {
        assertNull(WifiDownloadFormatSetting.Ask.policy, "ask each time: no fixed plan")
        assertEquals(WifiFormatPolicy.JpegOnly, WifiDownloadFormatSetting.Jpg.policy)
        assertEquals(WifiFormatPolicy.RawOnly, WifiDownloadFormatSetting.Raw.policy)
        assertEquals(WifiFormatPolicy.Original, WifiDownloadFormatSetting.JpgRaw.policy)
    }

    @Test
    fun storedSettingValuesRoundTripAndOldValuesKeepTheirMeaning() {
        for (setting in WifiDownloadFormatSetting.entries) {
            assertEquals(setting, WifiDownloadFormatSetting.fromStorage(setting.storageValue))
        }
        // Values written by 0.3.0 and earlier: "original" = every format, "jpeg" = JPG only.
        assertEquals(WifiDownloadFormatSetting.JpgRaw, WifiDownloadFormatSetting.fromStorage("original"))
        assertEquals(WifiDownloadFormatSetting.Jpg, WifiDownloadFormatSetting.fromStorage("jpeg"))
        // Missing / unknown: the new default, ask each time.
        assertEquals(WifiDownloadFormatSetting.Ask, WifiDownloadFormatSetting.Default)
        assertEquals(WifiDownloadFormatSetting.Ask, WifiDownloadFormatSetting.fromStorage(null))
        assertEquals(WifiDownloadFormatSetting.Ask, WifiDownloadFormatSetting.fromStorage("heif"))
        assertEquals(WifiDownloadFormatSetting.Ask, WifiDownloadFormatSetting.fromStorage("JPEG"), "case-sensitive")
        assertEquals(WifiDownloadFormatSetting.Ask, WifiDownloadFormatSetting.fromStorage(""))
        assertEquals(4, WifiDownloadFormatSetting.entries.map { it.storageValue }.toSet().size, "distinct values")
    }

    @Test
    fun captureSizeIsWhatTheDefaultFormatWouldDownload() {
        // JPG only: just the JPEG of a JPG+RAW capture; Original: every file.
        assertEquals(CaptureDownloadSize(2000, null), captureDownloadSize(jpgRaw, jpegOnly))
        assertEquals(CaptureDownloadSize(32000, null), captureDownloadSize(jpgRaw, original))
        assertEquals(CaptureDownloadSize(1500, null), captureDownloadSize(jpgOnly, original))
    }

    @Test
    fun anEmptyPlanCarriesItsAccurateSkipReason() {
        // NoJpeg (JPG-only policy, no JPEG file): the confirmed exception — show the originals'
        // size so the user sees what the RAW weighs.
        assertEquals(CaptureDownloadSize(40000, WifiSkipReason.NoJpeg), captureDownloadSize(rawOnly, jpegOnly))
        assertEquals(CaptureDownloadSize(900, WifiSkipReason.NoJpeg), captureDownloadSize(heifOnly, jpegOnly))
        assertEquals(CaptureDownloadSize(40000, null), captureDownloadSize(rawOnly, original))
        // TooLarge: nothing will be fetched — no size to show, the reason picks the text.
        val bigRaw = file(40, rawMime, 600_000_000, downloadable = false)
        assertEquals(
            CaptureDownloadSize(0, WifiSkipReason.TooLarge),
            captureDownloadSize(capture("big", bigRaw), original),
        )
        val bigJpg = file(41, jpegMime, 600_000_000, downloadable = false)
        assertEquals(
            CaptureDownloadSize(0, WifiSkipReason.TooLarge),
            captureDownloadSize(capture("bigJpg", bigJpg), jpegOnly),
        )
        // AlreadySaved: every file of the capture is saved — the tag shows the saved state instead.
        assertEquals(
            CaptureDownloadSize(0, WifiSkipReason.AlreadySaved),
            captureDownloadSize(jpgOnly, jpegOnly, saved = setOf(WifiPhotoDownload(6))),
        )
        assertEquals(
            CaptureDownloadSize(0, WifiSkipReason.AlreadySaved),
            captureDownloadSize(jpgRaw, original, saved = setOf(WifiPhotoDownload(1), WifiPhotoDownload(2))),
        )
    }

    @Test
    fun tileSizeBytesAgreeWithTheBatchButtonBytes() {
        // The floating "Download N JPG (x MB)" and the tile tags come from the same planner —
        // including the saved-aware one, so the *byte* sums agree when files were downloaded earlier.
        // (The visible numbers are rounded one by one: they are not guaranteed to add up to the
        // rounded total; only the unrounded byte counts are asserted here.)
        val big = file(50, jpegMime, 600_000_000, downloadable = false)
        val selection = listOf(
            jpgRaw, rawJpg, jpgOnly, rawOnly, heifRaw, capture("big", big, file(51, rawMime, 5)),
        )
        val savedCases = listOf(
            "none saved" to emptySet<WifiPhotoDownload>(),
            "some saved" to setOf(WifiPhotoDownload(6), WifiPhotoDownload(1)), // jpgOnly, one file of jpgRaw
            "raw jpeg saved" to setOf(WifiPhotoDownload(4)),                   // JPG of rawJpg (Original mode too)
        )
        for (policy in WifiFormatPolicy.entries) {
            for ((name, saved) in savedCases) {
                // What the tags count: a tile contributes its bytes only when something will be
                // fetched (reason == null); saved/too-large tiles show a state instead of a size.
                val tags = selection.sumOf { captureDownloadSize(it, policy, saved).takeIf { it.reason == null }?.bytes ?: 0L }
                assertEquals(
                    planDownloads(selection, policy, saved).totalBytes, tags,
                    "policy $policy, $name",
                )
            }
        }
    }

    // --- skip-reason priority and the saved RAW-only exception -----------------------

    @Test
    fun anEmptyPlanPrefersTooLargeOverAlreadySavedWhateverTheFileOrder() {
        val savedJpeg = file(60, jpegMime, 2000)
        val bigRaw = file(61, rawMime, 40_000_000, downloadable = false)
        val saved = setOf(WifiPhotoDownload(60))
        for (files in listOf(listOf(savedJpeg, bigRaw), listOf(bigRaw, savedJpeg))) {
            val c = capture("mixed", *files.toTypedArray())
            val plan = planDownloads(listOf(c), original, saved)
            assertTrue(plan.files.isEmpty())
            assertEquals(mapOf(WifiSkipReason.AlreadySaved to 1, WifiSkipReason.TooLarge to 1), plan.skipped)
            assertEquals(WifiSkipReason.TooLarge, plan.mainSkipReason, "order ${files.map { it.handle }}")
            assertEquals(
                CaptureDownloadSize(0, WifiSkipReason.TooLarge), captureDownloadSize(c, original, saved),
                "order ${files.map { it.handle }}",
            )
        }
    }

    @Test
    fun savedIsOnlyShownWhenEveryCandidateFileIsSaved() {
        val a = file(62, jpegMime, 100)
        val b = file(63, rawMime, 200)
        val both = setOf(WifiPhotoDownload(62), WifiPhotoDownload(63))
        assertEquals(WifiSkipReason.AlreadySaved, planDownloads(listOf(capture("x", a, b)), original, both).mainSkipReason)
        // One file saved, the other still downloadable: the plan is not empty, nothing is "saved".
        val half = planDownloads(listOf(capture("x", a, b)), original, setOf(WifiPhotoDownload(62)))
        assertEquals(listOf(63L), handles(half))
        assertEquals(
            CaptureDownloadSize(200, null),
            captureDownloadSize(capture("x", a, b), original, setOf(WifiPhotoDownload(62))),
        )
        // Only an oversized file: too large, whatever else is absent.
        val big = file(64, rawMime, 600_000_000, downloadable = false)
        assertEquals(WifiSkipReason.TooLarge, planDownloads(listOf(capture("y", big)), original, emptySet()).mainSkipReason)
    }

    @Test
    fun aRawOnlyCaptureTheUserSavedByHandShowsSavedUnderJpegOnlyAndNoSizeWarning() {
        val saved = setOf(WifiPhotoDownload(5)) // rawOnly's RAW, downloaded from the viewer
        // Unsaved: the confirmed NoJpeg exception (size + warning) stays.
        assertEquals(CaptureDownloadSize(40000, WifiSkipReason.NoJpeg), captureDownloadSize(rawOnly, jpegOnly))
        // Saved: same wording as the viewer's already-saved button, no size, no warning.
        assertEquals(
            CaptureDownloadSize(0, WifiSkipReason.AlreadySaved),
            captureDownloadSize(rawOnly, jpegOnly, saved),
        )
        // HEIF+RAW: one file saved is not "saved" (the other would still download manually).
        assertEquals(
            CaptureDownloadSize(900 + 1000, WifiSkipReason.NoJpeg),
            captureDownloadSize(heifRaw, jpegOnly, setOf(WifiPhotoDownload(9))),
        )
        assertEquals(
            CaptureDownloadSize(0, WifiSkipReason.AlreadySaved),
            captureDownloadSize(heifRaw, jpegOnly, setOf(WifiPhotoDownload(8), WifiPhotoDownload(9))),
        )
    }

    // --- RAW only -----------------------------------------------------------------------

    private val rawOnlyPolicy = WifiFormatPolicy.RawOnly

    @Test
    fun rawOnlyDownloadsOnlyTheRawAndSkipsPhotosWithoutOne() {
        val plan = planDownloads(listOf(jpgRaw, jpgOnly, rawJpg, heifOnly, rawOnly, multi), rawOnlyPolicy, emptySet())
        assertEquals(listOf(2L, 3L, 5L, 10L), handles(plan), "selection order, RAW files only — never a JPG/HEIF")
        assertTrue(plan.files.all { it.mimeType == rawMime })
        assertEquals(mapOf(WifiSkipReason.NoRaw to 2), plan.skipped, "JPG-only and HEIF-only are skipped, not replaced")
        assertEquals(WifiBatchSummary(4, mapOf(WifiSkipReason.NoRaw to 2)), plan.toSummary())
        assertEquals(30000L + 1000 + 40000 + 1000, plan.totalBytes)
    }

    @Test
    fun rawOnlyOverPhotosWithoutRawSkipsEverything() {
        val plan = planDownloads(listOf(jpgOnly, heifOnly), rawOnlyPolicy, emptySet())
        assertTrue(plan.files.isEmpty())
        assertEquals(WifiSkipReason.NoRaw, plan.mainSkipReason)
        assertEquals(WifiBatchSummary(0, mapOf(WifiSkipReason.NoRaw to 2)), plan.toSummary())
        assertTrue(WifiSkipReason.NoRaw.isMissingFormat)
        assertTrue(WifiSkipReason.NoJpeg.isMissingFormat)
        assertFalse(WifiSkipReason.TooLarge.isMissingFormat)
        assertFalse(WifiSkipReason.AlreadySaved.isMissingFormat)
    }

    @Test
    fun rawOnlySkipsSavedAndOversizedRawsWithTheirOwnReasons() {
        val bigRaw = file(70, rawMime, 600_000_000, downloadable = false)
        val big = capture("big", file(71, jpegMime, 100), bigRaw)
        val plan = planDownloads(listOf(jpgRaw, big, rawOnly), rawOnlyPolicy, setOf(WifiPhotoDownload(2)))
        assertEquals(listOf(5L), handles(plan))
        assertEquals(mapOf(WifiSkipReason.AlreadySaved to 1, WifiSkipReason.TooLarge to 1), plan.skipped)
    }

    @Test
    fun rawOnlyCaptureSizeIsTheRawOrTheMissingRawException() {
        assertEquals(CaptureDownloadSize(30000, null), captureDownloadSize(jpgRaw, rawOnlyPolicy))
        assertEquals(CaptureDownloadSize(40000, null), captureDownloadSize(rawOnly, rawOnlyPolicy))
        // No RAW: like JPG-only's NoJpeg — the originals' size with the reason (tile warns, never fetches).
        assertEquals(CaptureDownloadSize(1500, WifiSkipReason.NoRaw), captureDownloadSize(jpgOnly, rawOnlyPolicy))
        assertEquals(CaptureDownloadSize(900, WifiSkipReason.NoRaw), captureDownloadSize(heifOnly, rawOnlyPolicy))
        // The JPG of a JPG-only capture already saved: shown as saved.
        assertEquals(
            CaptureDownloadSize(0, WifiSkipReason.AlreadySaved),
            captureDownloadSize(jpgOnly, rawOnlyPolicy, setOf(WifiPhotoDownload(6))),
        )
    }

    @Test
    fun aMixedSkipPrefersTooLargeThenNoJpegThenNoRaw() {
        assertEquals(
            WifiSkipReason.NoRaw,
            WifiDownloadPlan(emptyList(), mapOf(WifiSkipReason.NoRaw to 1, WifiSkipReason.AlreadySaved to 1)).mainSkipReason,
        )
        assertEquals(
            WifiSkipReason.TooLarge,
            WifiDownloadPlan(emptyList(), mapOf(WifiSkipReason.NoRaw to 1, WifiSkipReason.TooLarge to 1)).mainSkipReason,
        )
    }

    // --- ask each time ----------------------------------------------------------------

    @Test
    fun batchChoicesAreJpgRawAndBothEachWithItsOwnPlan() {
        val selection = listOf(jpgRaw, jpgOnly, rawOnly)
        val choices = batchFormatChoices(selection, emptySet())
        assertEquals(listOf(WifiFormatPolicy.JpegOnly, WifiFormatPolicy.RawOnly, WifiFormatPolicy.Original), choices.map { it.policy })
        for (choice in choices) assertEquals(planDownloads(selection, choice.policy, emptySet()), choice.plan)
        val (jpg, raw, both) = choices
        assertEquals(listOf(1L, 6L), handles(jpg.plan)); assertEquals(mapOf(WifiSkipReason.NoJpeg to 1), jpg.plan.skipped)
        assertEquals(listOf(2L, 5L), handles(raw.plan)); assertEquals(mapOf(WifiSkipReason.NoRaw to 1), raw.plan.skipped)
        assertEquals(listOf(1L, 2L, 6L, 5L), handles(both.plan)); assertEquals(0, both.plan.skippedCount)
        assertTrue(choices.all { it.available })
    }

    @Test
    fun aBatchChoiceThatFetchesNothingIsListedButUnavailable() {
        val choices = batchFormatChoices(listOf(jpgOnly, heifOnly), emptySet())
        assertEquals(3, choices.size)
        assertEquals(listOf(true, false, true), choices.map { it.available }, "no RAW anywhere")
        assertEquals(WifiSkipReason.NoRaw, choices[1].plan.mainSkipReason)
    }

    @Test
    fun singleChoicesListOnlyTheFormatsThePhotoHas() {
        assertEquals(
            listOf(WifiFormatPolicy.JpegOnly, WifiFormatPolicy.RawOnly, WifiFormatPolicy.Original),
            singleFormatChoices(jpgRaw, emptySet()).map { it.policy },
        )
        // One format only: one choice — the viewer downloads directly without asking.
        assertEquals(listOf(WifiFormatPolicy.JpegOnly), singleFormatChoices(jpgOnly, emptySet()).map { it.policy })
        assertEquals(listOf(WifiFormatPolicy.RawOnly), singleFormatChoices(rawOnly, emptySet()).map { it.policy })
        assertEquals(listOf(WifiFormatPolicy.Original), singleFormatChoices(heifOnly, emptySet()).map { it.policy })
        // HEIF + RAW: RAW alone or both (HEIF is not offered on its own, as with the fixed settings).
        assertEquals(
            listOf(WifiFormatPolicy.RawOnly, WifiFormatPolicy.Original),
            singleFormatChoices(heifRaw, emptySet()).map { it.policy },
        )
        val sizes = singleFormatChoices(jpgRaw, emptySet()).map { it.plan.totalBytes }
        assertEquals(listOf(2000L, 30000L, 32000L), sizes)
    }

    @Test
    fun singleChoicesLeaveOutSavedOrOversizedFiles() {
        // JPG already saved: "JPG + RAW" would fetch only the RAW — same as "RAW", one choice, no question.
        assertEquals(listOf(WifiFormatPolicy.RawOnly), singleFormatChoices(jpgRaw, setOf(WifiPhotoDownload(1))).map { it.policy })
        assertTrue(singleFormatChoices(jpgRaw, setOf(WifiPhotoDownload(1), WifiPhotoDownload(2))).isEmpty())
        val bigRaw = capture("bigRaw", file(80, jpegMime, 100), file(81, rawMime, 600_000_000, downloadable = false))
        assertEquals(listOf(WifiFormatPolicy.JpegOnly), singleFormatChoices(bigRaw, emptySet()).map { it.policy })
    }
}
