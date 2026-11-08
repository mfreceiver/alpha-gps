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
                setOf(WifiPhotoBatchFormat.Jpeg, WifiPhotoBatchFormat.Raw, WifiPhotoBatchFormat.Heif)
            )
        )
        // The HEIF→JPEG conversion is opt-in even when it is the only JPEG-ish option.
        assertEquals(
            setOf(WifiPhotoBatchFormat.Heif),
            defaultBatchFormats(setOf(WifiPhotoBatchFormat.Heif, WifiPhotoBatchFormat.JpegCopy))
        )
        assertEquals(emptySet(), defaultBatchFormats(emptySet()))
    }

    @Test
    fun singlePhotoDefaultPreselectsFirstDownloadableFile() {
        // Original: a concrete preselection (the first downloadable file), not a disabled button.
        assertEquals(3L, defaultSelectedPhotoHandle(captures[1].files)) // ARW + JPG
        assertEquals(1L, defaultSelectedPhotoHandle(captures[0].files)) // ARW + HIF
        // A lone file stays preselected even when it is not downloadable, so the page can
        // explain why (too large).
        assertEquals(5L, defaultSelectedPhotoHandle(listOf(captures[2].files[0])))
        assertEquals(null, defaultSelectedPhotoHandle(emptyList()))
    }
}
