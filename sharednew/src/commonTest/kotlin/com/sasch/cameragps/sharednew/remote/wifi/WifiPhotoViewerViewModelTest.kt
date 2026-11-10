package com.sasch.cameragps.sharednew.remote.wifi

import androidx.compose.ui.graphics.ImageBitmap
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import com.sasch.cameragps.sharednew.ui.remote.ViewerInfoOptions
import com.sasch.cameragps.sharednew.ui.remote.WifiRemoteViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The viewer's ViewModel wiring (M7): shared selection and the metadata request / cancel rules. */
@OptIn(ExperimentalCoroutinesApi::class)
class WifiPhotoViewerViewModelTest : WifiLoggingTest() {
    private class Connection : WifiRemoteConnection {
        override val cameraName = "Test camera"
        override val canCapture = false
        override val canTransferImages = true
        override val hasLiveViewStream = false
        val photos = listOf(
            WifiCameraPhoto(42, "A.JPG", 5_000_000, "image/jpeg", "20260101T120000", captureId = "a", width = 6000, height = 4000),
            WifiCameraPhoto(43, "A.ARW", 25_000_000, "image/x-sony-arw", "20260101T120000", captureId = "a"),
            WifiCameraPhoto(44, "B.JPG", 4_000_000, "image/jpeg", "20260101T120100", captureId = "b"),
        )
        val heads = mutableListOf<Long>()
        override suspend fun openPhotoBrowser() = cameraPhotoPage(groupCameraPhotos(photos), 0)
        override suspend fun photoPage(offset: Int) = cameraPhotoPage(groupCameraPhotos(photos), offset)
        override suspend fun photoPreview(handle: Long): ImageBitmap? = null
        override suspend fun photoMetadataHead(handle: Long, length: Int): ByteArray {
            heads += handle
            return ExifTestData.jpeg(ExifTestData.sampleExif())
        }
        override val images: Flow<ImageBitmap> = emptyFlow()
        override val lost: Flow<Unit> = flow { awaitCancellation() }
        override suspend fun capture() = error("unused")
        override suspend fun close() {}
    }

    private fun TestScope.open(connection: Connection): Pair<WifiRemoteController, WifiRemoteViewModel> {
        val controller = WifiRemoteController(backgroundScope, CameraSessionRegistry(), { _, _ -> connection }, {}, {})
        val model = WifiRemoteViewModel("camera", controller)
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        return controller to model
    }

    @Test
    fun viewerCheckBoxSharesTheGridSelectionAndClosingKeepsIt() = runTest {
        val (controller, model) = open(Connection())
        model.previewPhoto("a")
        assertEquals(0, model.viewedPhotoIndex)
        model.setPhotoSelected("a", true)
        assertTrue(model.isPhotoSelected("a"))
        assertEquals(setOf("a"), model.selectedPhotoIds, "the grid sees the viewer's selection")
        model.setPhotoSelected("a", true)
        assertEquals(setOf("a"), model.selectedPhotoIds, "setting the same state is a no-op, not a toggle")
        model.viewPhotoAt(1)
        model.setPhotoSelected("b", true)
        model.setPhotoSelected("a", false)
        assertEquals(setOf("b"), model.selectedPhotoIds)
        model.closePhoto()
        assertNull(model.viewedPhotoIndex)
        assertEquals(setOf("b"), model.selectedPhotoIds, "returning to the grid keeps the selection")
        controller.closeAndJoin()
    }

    @Test
    fun allTogglesOffOrHistogramOnlyNeverReadTheOriginal() = runTest {
        val connection = Connection()
        val (controller, model) = open(connection)
        model.previewPhoto("a")
        model.showViewerMetadata(ViewerInfoOptions())
        val basic = assertIs<PhotoMetadataState.Ready>(controller.metadata.value).metadata
        assertEquals(42L, basic.handle)
        assertFalse(basic.extendedRead)
        model.showViewerMetadata(ViewerInfoOptions(showHistogram = true))
        assertIs<PhotoMetadataState.Ready>(controller.metadata.value)
        advanceTimeBy(1_000); runCurrent()
        assertTrue(connection.heads.isEmpty(), "basic data is free: no camera read")
        controller.closeAndJoin()
    }

    @Test
    fun parametersOrLensTriggerTheExtendedRead() = runTest {
        for (options in listOf(ViewerInfoOptions(showExifParams = true), ViewerInfoOptions(showLens = true))) {
            val connection = Connection()
            val (controller, model) = open(connection)
            model.previewPhoto("a")
            model.showViewerMetadata(options)
            assertIs<PhotoMetadataState.Loading>(controller.metadata.value)
            advanceTimeBy(300); runCurrent()
            val ready = assertIs<PhotoMetadataState.Ready>(controller.metadata.value).metadata
            assertTrue(ready.extendedRead)
            assertEquals(listOf(42L), connection.heads)
            controller.closeAndJoin()
        }
    }

    @Test
    fun aSwipeCancelsThePreviousReadAndClosingStopsIt() = runTest {
        val connection = Connection()
        val (controller, model) = open(connection)
        val options = ViewerInfoOptions(showExifParams = true)
        model.previewPhoto("a")
        model.showViewerMetadata(options)
        assertIs<PhotoMetadataState.Loading>(controller.metadata.value)
        // Swipe before the debounce ends: only the new photo is read.
        model.viewPhotoAt(1)
        model.showViewerMetadata(options)
        assertEquals(44L, assertIs<PhotoMetadataState.Loading>(controller.metadata.value).handle)
        advanceTimeBy(300); runCurrent()
        assertEquals(listOf(44L), connection.heads)
        // Back to the first photo, then close before the read starts.
        model.viewPhotoAt(0)
        model.showViewerMetadata(options)
        assertIs<PhotoMetadataState.Loading>(controller.metadata.value)
        model.closePhoto()
        model.stopViewerMetadata()
        assertEquals(PhotoMetadataState.Idle, controller.metadata.value)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(44L), connection.heads, "a closed viewer reads nothing")
        model.showViewerMetadata(options)
        assertEquals(PhotoMetadataState.Idle, controller.metadata.value, "no request without a viewed photo")
        controller.closeAndJoin()
    }
}
