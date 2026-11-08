package com.sasch.cameragps.sharednew.remote.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WifiPhotoBatchTest {
    private val captures = groupCameraPhotos(
        listOf(
            WifiCameraPhoto(1, "ONE.ARW", 100, "image/x-sony-arw", "", captureId = "one"),
            WifiCameraPhoto(2, "ONE.HIF", 100, "image/heif", "", captureId = "one"),
            WifiCameraPhoto(3, "TWO.ARW", 100, "image/x-sony-arw", "", captureId = "two"),
            WifiCameraPhoto(4, "TWO.JPG", 100, "image/jpeg", "", captureId = "two"),
            WifiCameraPhoto(5, "LARGE.JPG", 600_000_000, "image/jpeg", "", downloadable = false),
        )
    )

    @Test
    fun selectedFormatsResolveToFilesWithoutDuplicatingPairedPhotos() {
        assertEquals(
            listOf(WifiPhotoDownload(1), WifiPhotoDownload(3)),
            photoBatchDownloads(captures, setOf(WifiPhotoBatchFormat.Raw), emptySet(), true)
        )
        assertEquals(
            listOf(WifiPhotoDownload(2, WifiPhotoDownloadFormat.Jpeg), WifiPhotoDownload(4)),
            photoBatchDownloads(
                captures,
                setOf(WifiPhotoBatchFormat.Jpeg, WifiPhotoBatchFormat.JpegCopy),
                emptySet(),
                true
            )
        )
        assertEquals(
            listOf(WifiPhotoDownload(1), WifiPhotoDownload(2)),
            photoBatchDownloads(
                captures.take(1),
                setOf(WifiPhotoBatchFormat.Raw, WifiPhotoBatchFormat.Heif),
                emptySet(),
                true
            )
        )
    }

    @Test
    fun savedOriginalDoesNotSuppressJpegCopyAndUnsupportedConversionIsExcluded() {
        val saved = setOf(WifiPhotoDownload(2))
        assertEquals(
            listOf(WifiPhotoDownload(2, WifiPhotoDownloadFormat.Jpeg)),
            photoBatchDownloads(
                captures,
                setOf(WifiPhotoBatchFormat.Heif, WifiPhotoBatchFormat.JpegCopy),
                saved,
                true
            )
        )
        assertTrue(
            photoBatchDownloads(
                captures,
                setOf(WifiPhotoBatchFormat.JpegCopy),
                emptySet(),
                false
            ).isEmpty()
        )
    }

    @Test
    fun emptySelectionAndSavedOrOversizedFilesDoNotDownload() {
        assertTrue(
            photoBatchDownloads(
                emptyList(),
                WifiPhotoBatchFormat.entries.toSet(),
                emptySet(),
                true
            ).isEmpty()
        )
        assertTrue(photoBatchDownloads(captures, emptySet(), emptySet(), true).isEmpty())
        assertTrue(
            photoBatchDownloads(
                captures,
                setOf(WifiPhotoBatchFormat.Jpeg),
                setOf(WifiPhotoDownload(4)),
                true
            ).isEmpty()
        )
    }

    @Test
    fun originalDefaultChecksEveryAvailableOriginalFormat() {
        assertEquals(
            setOf(WifiPhotoBatchFormat.Jpeg, WifiPhotoBatchFormat.Raw, WifiPhotoBatchFormat.Heif),
            defaultBatchFormats(
                setOf(WifiPhotoBatchFormat.Jpeg, WifiPhotoBatchFormat.Raw, WifiPhotoBatchFormat.Heif),
                defaultJpegOnly = false
            )
        )
        // The HEIF→JPEG conversion is opt-in even when it is the only JPEG-ish option.
        assertEquals(
            setOf(WifiPhotoBatchFormat.Heif),
            defaultBatchFormats(setOf(WifiPhotoBatchFormat.Heif, WifiPhotoBatchFormat.JpegCopy), defaultJpegOnly = false)
        )
    }

    @Test
    fun jpegOnlyDefaultChecksJpegAndNeverFallsBackToRaw() {
        assertEquals(
            setOf(WifiPhotoBatchFormat.Jpeg),
            defaultBatchFormats(setOf(WifiPhotoBatchFormat.Jpeg, WifiPhotoBatchFormat.Raw), defaultJpegOnly = true)
        )
        // No downloadable JPEG → stay empty so the user chooses explicitly.
        assertEquals(
            emptySet(),
            defaultBatchFormats(setOf(WifiPhotoBatchFormat.Raw, WifiPhotoBatchFormat.Heif), defaultJpegOnly = true)
        )
        assertEquals(emptySet(), defaultBatchFormats(emptySet(), defaultJpegOnly = true))
    }

    @Test
    fun singlePhotoDefaultPreselectsJpegOrFirstDownloadable() {
        val pair = captures[1].files // ARW + JPG
        assertEquals(4L, defaultSelectedPhotoHandle(pair, defaultJpegOnly = true))
        // Original default: a concrete preselection, not a disabled download button.
        assertEquals(3L, defaultSelectedPhotoHandle(pair, defaultJpegOnly = false))
        // Single file keeps working either way, downloadable or not.
        assertEquals(1L, defaultSelectedPhotoHandle(listOf(captures[0].files[0]), defaultJpegOnly = false))
        assertEquals(5L, defaultSelectedPhotoHandle(listOf(captures[2].files[0]), defaultJpegOnly = true))
        // JPEG-only with only non-JPEG files: no silent RAW preselection when the choice
        // is ambiguous; a lone file stays preselected because there is nothing to swap.
        assertEquals(
            1L,
            defaultSelectedPhotoHandle(listOf(captures[0].files[0]), defaultJpegOnly = true)
        )
        assertEquals(
            null,
            defaultSelectedPhotoHandle(captures[0].files, defaultJpegOnly = true),
            "A JPEG-less pair must not silently preselect the RAW"
        )
        assertEquals(null, defaultSelectedPhotoHandle(emptyList(), defaultJpegOnly = false))
    }
}
