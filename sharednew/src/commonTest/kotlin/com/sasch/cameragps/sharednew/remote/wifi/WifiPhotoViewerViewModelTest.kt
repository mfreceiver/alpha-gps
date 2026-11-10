package com.sasch.cameragps.sharednew.remote.wifi

import androidx.compose.ui.graphics.ImageBitmap
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import com.sasch.cameragps.sharednew.ui.remote.WifiRemoteViewModel
import kotlinx.coroutines.CompletableDeferred
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

    private fun TestScope.open(connection: WifiRemoteConnection): Pair<WifiRemoteController, WifiRemoteViewModel> {
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
    fun aClosedDetailsDrawerNeverReadsTheOriginal() = runTest {
        val connection = Connection()
        val (controller, model) = open(connection)
        model.previewPhoto("a")
        model.showViewerMetadata(extended = false)
        val basic = assertIs<PhotoMetadataState.Ready>(controller.metadata.value).metadata
        assertEquals(42L, basic.handle)
        assertFalse(basic.extendedRead)
        advanceTimeBy(1_000); runCurrent()
        assertTrue(connection.heads.isEmpty(), "basic data is free: no camera read")
        controller.closeAndJoin()
    }

    @Test
    fun openingTheDetailsTriggersTheExtendedRead() = runTest {
        val connection = Connection()
        val (controller, model) = open(connection)
        model.previewPhoto("a")
        model.showViewerMetadata(extended = true)
        assertIs<PhotoMetadataState.Loading>(controller.metadata.value)
        advanceTimeBy(300); runCurrent()
        val ready = assertIs<PhotoMetadataState.Ready>(controller.metadata.value).metadata
        assertTrue(ready.extendedRead)
        assertEquals(listOf(42L), connection.heads)
        controller.closeAndJoin()
    }

    @Test
    fun closingTheDrawerBeforeTheDebounceCancelsTheRead() = runTest {
        val connection = Connection()
        val (controller, model) = open(connection)
        model.previewPhoto("a")
        model.showViewerMetadata(extended = true)
        assertIs<PhotoMetadataState.Loading>(controller.metadata.value)
        // The drawer closes within the 250 ms debounce: the viewer asks for the basic data again.
        model.showViewerMetadata(extended = false)
        assertFalse(assertIs<PhotoMetadataState.Ready>(controller.metadata.value).metadata.extendedRead)
        advanceTimeBy(1_000); runCurrent()
        assertTrue(connection.heads.isEmpty(), "the pending extended read was cancelled")
        controller.closeAndJoin()
    }

    @Test
    fun aSwipeCancelsThePreviousReadAndClosingStopsIt() = runTest {
        val connection = Connection()
        val (controller, model) = open(connection)
        val extended = true // the details are shown
        model.previewPhoto("a")
        model.showViewerMetadata(extended)
        assertIs<PhotoMetadataState.Loading>(controller.metadata.value)
        // Swipe before the debounce ends: only the new photo is read.
        model.viewPhotoAt(1)
        model.showViewerMetadata(extended)
        assertEquals(44L, assertIs<PhotoMetadataState.Loading>(controller.metadata.value).handle)
        advanceTimeBy(300); runCurrent()
        assertEquals(listOf(44L), connection.heads)
        // Back to the first photo, then close before the read starts.
        model.viewPhotoAt(0)
        model.showViewerMetadata(extended)
        assertIs<PhotoMetadataState.Loading>(controller.metadata.value)
        model.closePhoto()
        model.stopViewerMetadata()
        assertEquals(PhotoMetadataState.Idle, controller.metadata.value)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(44L), connection.heads, "a closed viewer reads nothing")
        model.showViewerMetadata(extended)
        assertEquals(PhotoMetadataState.Idle, controller.metadata.value, "no request without a viewed photo")
        controller.closeAndJoin()
    }

    /** Same as [Connection] but the head read blocks on a per-handle gate until the test releases it. */
    private class GatedConnection : WifiRemoteConnection {
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
        val headGates = mutableMapOf<Long, CompletableDeferred<ByteArray>>()
        val downloadGate = CompletableDeferred<Unit>()
        override suspend fun openPhotoBrowser() = cameraPhotoPage(groupCameraPhotos(photos), 0)
        override suspend fun photoPage(offset: Int) = cameraPhotoPage(groupCameraPhotos(photos), offset)
        override suspend fun photoPreview(handle: Long): ImageBitmap? = null
        override suspend fun photoMetadataHead(handle: Long, length: Int): ByteArray {
            heads += handle
            return headGates.getOrPut(handle) { CompletableDeferred() }.await()
        }
        override suspend fun downloadPhoto(
            handle: Long,
            format: WifiPhotoDownloadFormat,
            onProgress: (WifiImageTransferState) -> Unit,
        ) = downloadGate.await().let { WifiImageTransferState(WifiImageTransferStatus.Saved) }
        override val images: Flow<ImageBitmap> = emptyFlow()
        override val lost: Flow<Unit> = flow { awaitCancellation() }
        override suspend fun capture() = error("unused")
        override suspend fun close() {}
    }

    @Test
    fun aLateHeadResultNeverPollutesTheNewlyShownPhoto() = runTest {
        val connection = GatedConnection()
        val (controller, model) = open(connection)
        val extended = true // the details are shown
        model.previewPhoto("a")
        model.showViewerMetadata(extended)
        advanceTimeBy(300); runCurrent()
        assertEquals(listOf(42L), connection.heads, "the first head read was issued and is in flight")

        // Swipe while the head of the first photo is still in flight.
        model.viewPhotoAt(1)
        model.showViewerMetadata(extended)
        assertEquals(44L, assertIs<PhotoMetadataState.Loading>(controller.metadata.value).handle)
        advanceTimeBy(300); runCurrent()
        assertEquals(listOf(42L, 44L), connection.heads)

        // The stale read for 42 completes now: its coroutine was cancelled by the second request,
        // so it must not overwrite the state of photo 44.
        connection.headGates.getValue(42L).complete(ExifTestData.jpeg(ExifTestData.sampleExif()))
        runCurrent()
        assertIs<PhotoMetadataState.Loading>(controller.metadata.value)
        assertEquals(44L, assertIs<PhotoMetadataState.Loading>(controller.metadata.value).handle)

        connection.headGates.getValue(44L).complete(ExifTestData.jpeg(ExifTestData.sampleExif()))
        advanceTimeBy(1_000); runCurrent()
        val ready = assertIs<PhotoMetadataState.Ready>(controller.metadata.value).metadata
        assertEquals(44L, ready.handle)
        assertTrue(ready.extendedRead, "the current photo's own read completes normally")
        controller.closeAndJoin()
    }

    @Test
    fun aLateHeadResultAfterClosingTheViewerStaysIdle() = runTest {
        val connection = GatedConnection()
        val (controller, model) = open(connection)
        val extended = true // the details are shown
        model.previewPhoto("a")
        model.showViewerMetadata(extended)
        advanceTimeBy(300); runCurrent()
        assertEquals(listOf(42L), connection.heads)

        model.closePhoto()
        model.stopViewerMetadata()
        assertEquals(PhotoMetadataState.Idle, controller.metadata.value)

        // The in-flight read resolves only now: it must not resurrect any state.
        connection.headGates.getValue(42L).complete(ExifTestData.jpeg(ExifTestData.sampleExif()))
        advanceTimeBy(1_000); runCurrent()
        assertEquals(PhotoMetadataState.Idle, controller.metadata.value)
        assertEquals(listOf(42L), connection.heads, "no new head request after the viewer closed")
        controller.closeAndJoin()
    }

    @Test
    fun aDownloadInterruptsTheReadAndTheViewerReRequestsOnceTheCameraIsIdleAgain() = runTest {
        val connection = GatedConnection()
        val (controller, model) = open(connection)
        val extended = true // the details are shown
        model.previewPhoto("a")
        model.showViewerMetadata(extended)
        advanceTimeBy(300); runCurrent()
        assertEquals(listOf(42L), connection.heads)

        // A download takes the camera's command queue: the pending read falls back to basic data.
        controller.downloadPhoto("camera", 42L)
        runCurrent()
        val interrupted = assertIs<PhotoMetadataState.Ready>(controller.metadata.value).metadata
        assertFalse(interrupted.extendedRead, "interrupt() falls back to the free basic data")

        // The download finishes; the viewer (its LaunchedEffect on transfer.busy) asks again and
        // the current photo's read restarts from scratch.
        connection.downloadGate.complete(Unit)
        runCurrent()
        model.showViewerMetadata(extended)
        assertIs<PhotoMetadataState.Loading>(controller.metadata.value)
        advanceTimeBy(300); runCurrent()
        assertEquals(listOf(42L, 42L), connection.heads, "the read is issued a second time")
        connection.headGates.getValue(42L).complete(ExifTestData.jpeg(ExifTestData.sampleExif()))
        advanceTimeBy(1_000); runCurrent()
        assertTrue(assertIs<PhotoMetadataState.Ready>(controller.metadata.value).metadata.extendedRead)
        controller.closeAndJoin()
    }
}
