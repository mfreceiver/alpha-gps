package com.sasch.cameragps.sharednew.remote.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WifiSingleDownloadTest {
    private fun photo(handle: Long, mime: String, downloadable: Boolean = true) =
        WifiCameraPhoto(handle, "P$handle", 100, mime, "", downloadable, "c")

    private val jpg = photo(1, "image/jpeg")
    private val raw = photo(2, "image/x-sony-arw")
    private val heif = photo(3, "image/heif")

    private fun options(
        vararg files: WifiCameraPhoto,
        convert: Boolean = false,
        saved: Set<WifiPhotoDownload> = emptySet(),
    ) = WifiCameraCapture("c", files.toList()).downloadOptions(convert, saved)

    @Test
    fun aLoneFileDownloadsDirectly() {
        val action = singleDownloadAction(options(jpg))
        assertEquals(WifiSingleDownloadAction.Direct(options(jpg).single()), action)
    }

    @Test
    fun aJpgRawPairAsksWhichOne() {
        val action = singleDownloadAction(options(jpg, raw))
        assertIs<WifiSingleDownloadAction.Choose>(action)
        assertEquals(listOf(1L, 2L), action.options.map { it.file.handle })
    }

    @Test
    fun heifOffersAJpegCopyOnlyWhenThePlatformCanConvert() {
        assertEquals(1, options(heif, convert = false).size)
        val withCopy = options(heif, convert = true)
        assertEquals(listOf(WifiPhotoDownloadFormat.Original, WifiPhotoDownloadFormat.Jpeg), withCopy.map { it.format })
        assertTrue(withCopy[1].converted)
        assertIs<WifiSingleDownloadAction.Choose>(singleDownloadAction(withCopy))
    }

    @Test
    fun savedAndOversizeChoicesDoNotCountAsChoices() {
        val savedRaw = options(jpg, raw, saved = setOf(WifiPhotoDownload(2)))
        // Only the JPG is left, so there is nothing to choose between.
        assertEquals(WifiSingleDownloadAction.Direct(savedRaw[0]), singleDownloadAction(savedRaw))

        val tooBig = options(jpg, photo(2, "image/x-sony-arw", downloadable = false))
        assertEquals(WifiSingleDownloadAction.Direct(tooBig[0]), singleDownloadAction(tooBig))
    }

    @Test
    fun everythingSavedMeansUnavailable() {
        val all = options(jpg, raw, saved = setOf(WifiPhotoDownload(1), WifiPhotoDownload(2)))
        assertEquals(WifiSingleDownloadAction.Unavailable, singleDownloadAction(all))
        assertEquals(WifiSingleDownloadAction.Unavailable, singleDownloadAction(emptyList()))
    }

    @Test
    fun anUnsavedOversizeFileIsNotDownloadableInsteadOfSaved() {
        val tooBigOnly = options(photo(1, "image/jpeg", downloadable = false))
        assertEquals(WifiSingleDownloadAction.NotDownloadable, singleDownloadAction(tooBigOnly))
    }

    @Test
    fun savedPlusOversizeIsStillReportedAsNotDownloadable() {
        val mixed = options(
            photo(1, "image/jpeg"), photo(2, "image/x-sony-arw", downloadable = false),
            saved = setOf(WifiPhotoDownload(1)),
        )
        assertEquals(WifiSingleDownloadAction.NotDownloadable, singleDownloadAction(mixed))
    }

    @Test
    fun optionsKnowTheirDownloadRequest() {
        val copy = options(heif, convert = true)[1]
        assertEquals(WifiPhotoDownload(3, WifiPhotoDownloadFormat.Jpeg), copy.download)
    }
}
