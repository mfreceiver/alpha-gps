package com.sasch.cameragps.sharednew.remote.wifi

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoMetadataTypesTest {
    @Test
    fun mimeTypesMapToFormats() {
        assertEquals(PhotoFormat.Jpeg, photoFormatOf("image/jpeg"))
        assertEquals(PhotoFormat.Raw, photoFormatOf("image/x-sony-arw"))
        assertEquals(PhotoFormat.Heif, photoFormatOf("image/heif"))
        assertEquals(PhotoFormat.Unknown, photoFormatOf("video/mp4"))
    }

    @Test
    fun basicMetadataComesFromTheCatalogEntry() {
        val meta = WifiCameraPhoto(
            7, "DSC1.JPG", 123, "image/jpeg", "20260102T030405",
            width = 6000, height = 4000,
        ).toBasicMetadata()
        assertEquals("6000×4000", meta.resolutionText)
        assertTrue(meta.supportsExtended)
        assertFalse(WifiCameraPhoto(7, "A.ARW", 123, "image/x-sony-arw", "").toBasicMetadata().supportsExtended)
        assertEquals(null, WifiCameraPhoto(7, "A.JPG", 123, "image/jpeg", "").toBasicMetadata().resolutionText)
    }

    @Test
    fun ptpResponsesMapToFailureReasons() {
        fun reason(code: Int) = photoMetadataFailureReason(PtpOperationRejected(code))
        assertEquals(PhotoMetadataFailureReason.CameraBusy, reason(0x2019))
        assertEquals(PhotoMetadataFailureReason.CatalogChanged, reason(0x2009))
        assertEquals(PhotoMetadataFailureReason.Unsupported, reason(0x2005))
        assertEquals(PhotoMetadataFailureReason.Protocol, reason(0x2002))
        assertEquals(PhotoMetadataFailureReason.Protocol, photoMetadataFailureReason(RuntimeException()))
    }

    @Test
    fun timeoutsMapToTimeout() = runTest {
        val timeout = try {
            withTimeout(1) { kotlinx.coroutines.awaitCancellation() }
        } catch (e: TimeoutCancellationException) {
            e
        }
        assertEquals(PhotoMetadataFailureReason.Timeout, photoMetadataFailureReason(timeout))
    }

    @Test
    fun refusedImageOperationKeepsItsHistoricalMessage() {
        assertEquals("Camera refused image operation", PtpOperationRejected(0x2019).message)
    }
}
