package com.sasch.cameragps.sharednew.remote.wifi

import androidx.compose.ui.graphics.ImageBitmap
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WifiRemoteControllerMetadataTest : WifiLoggingTest() {
    private class Connection : WifiRemoteConnection {
        override val cameraName = "Test camera"
        override val canCapture = false
        override val canTransferImages = true
        override val hasLiveViewStream = false
        var photos = listOf(
            WifiCameraPhoto(42, "ONE.JPG", 5_000_000, "image/jpeg", "20260101T120000", width = 6000, height = 4000),
            WifiCameraPhoto(43, "TWO.ARW", 25_000_000, "image/x-sony-arw", ""),
        )
        val heads = mutableListOf<Pair<Long, Int>>()
        var headGate: CompletableDeferred<Unit>? = null
        var battery: Int? = null
        var previewGate: CompletableDeferred<Unit>? = null
        override suspend fun openPhotoBrowser() = cameraPhotoPage(groupCameraPhotos(photos), 0)
        override suspend fun photoPage(offset: Int) = cameraPhotoPage(groupCameraPhotos(photos), offset)
        override suspend fun photoPreview(handle: Long): ImageBitmap? {
            previewGate?.await()
            return null
        }
        override suspend fun photoMetadataHead(handle: Long, length: Int): ByteArray {
            heads += handle to length
            headGate?.await()
            return ExifTestData.jpeg(ExifTestData.sampleExif())
        }
        override suspend fun readBatteryPercent() = battery
        override val images: Flow<ImageBitmap> = emptyFlow()
        override val lost: Flow<Unit> = flow { awaitCancellation() }
        override suspend fun capture() = error("unused")
        override suspend fun close() {}
    }

    private fun kotlinx.coroutines.test.TestScope.open(
        connection: Connection,
        batteries: MutableList<Pair<String, Int>> = mutableListOf(),
    ): Pair<WifiRemoteController, CameraSessionRegistry> {
        val registry = CameraSessionRegistry()
        val controller = WifiRemoteController(
            backgroundScope, registry, { _, _ -> connection }, {}, {},
            onBatteryRead = { id, percent -> batteries += id to percent },
        )
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        return controller to registry
    }

    @Test
    fun basicMetadataIsImmediateAndExtendedReadsAfterTheDebounce() = runTest {
        val connection = Connection()
        val (controller, _) = open(connection)
        controller.loadMetadata("camera", 42, extended = false)
        val basic = assertIs<PhotoMetadataState.Ready>(controller.metadata.value).metadata
        assertEquals("6000×4000", basic.resolutionText)
        assertTrue(connection.heads.isEmpty())
        controller.loadMetadata("camera", 42, extended = true)
        assertIs<PhotoMetadataState.Loading>(controller.metadata.value)
        advanceTimeBy(300); runCurrent()
        val ready = assertIs<PhotoMetadataState.Ready>(controller.metadata.value).metadata
        assertEquals("f/2.8", ready.exif!!.apertureText)
        assertEquals(listOf(42L to 65_536), connection.heads)
        controller.closeAndJoin()
    }

    @Test
    fun rawIsBasicOnlyAndUnknownHandlesAreCatalogChanged() = runTest {
        val connection = Connection()
        val (controller, _) = open(connection)
        controller.loadMetadata("camera", 43, extended = true)
        assertEquals(PhotoFormat.Raw, assertIs<PhotoMetadataState.Ready>(controller.metadata.value).metadata.format)
        controller.loadMetadata("camera", 999, extended = true)
        assertEquals(
            PhotoMetadataFailureReason.CatalogChanged,
            assertIs<PhotoMetadataState.Unavailable>(controller.metadata.value).reason
        )
        advanceTimeBy(1_000); runCurrent()
        assertTrue(connection.heads.isEmpty())
        controller.closeAndJoin()
    }

    @Test
    fun cancelMetadataAndRequestsFromOtherOwnersAreHonouredCorrectly() = runTest {
        val connection = Connection()
        val (controller, _) = open(connection)
        controller.loadMetadata("other", 42, extended = true)
        assertEquals(PhotoMetadataState.Idle, controller.metadata.value)
        controller.loadMetadata("camera", 42, extended = true)
        controller.cancelMetadata("other")
        assertIs<PhotoMetadataState.Loading>(controller.metadata.value)
        controller.cancelMetadata("camera")
        assertEquals(PhotoMetadataState.Idle, controller.metadata.value)
        advanceTimeBy(1_000); runCurrent()
        assertTrue(connection.heads.isEmpty())
        controller.closeAndJoin()
    }

    @Test
    fun theExifReadWaitsForAPreviewLoadInsteadOfCompetingWithIt() = runTest {
        val connection = Connection().apply { previewGate = CompletableDeferred() }
        val (controller, _) = open(connection)
        controller.showPhotoPreview("camera", 42L)
        runCurrent()
        controller.loadMetadata("camera", 42, extended = true)
        advanceTimeBy(5_000); runCurrent()
        assertTrue(connection.heads.isEmpty(), "metadata must not interleave with a running preview load")
        connection.previewGate!!.complete(Unit)
        advanceTimeBy(300); runCurrent()
        assertEquals(1, connection.heads.size)
        assertIs<PhotoMetadataState.Ready>(controller.metadata.value)
        controller.closeAndJoin()
    }

    @Test
    fun aDownloadOrBrowseInterruptsAPendingReadWithoutBreakingTheViewer() = runTest {
        val connection = Connection().apply { headGate = CompletableDeferred() }
        val (controller, _) = open(connection)
        controller.loadMetadata("camera", 42, extended = true)
        advanceTimeBy(300); runCurrent()
        assertEquals(1, connection.heads.size)
        controller.browsePhotos("camera")
        runCurrent()
        val state = controller.metadata.value
        assertTrue(state is PhotoMetadataState.Ready && !state.metadata.extendedRead, "$state")
        controller.closeAndJoin()
    }

    @Test
    fun endingTheSessionClearsMetadata() = runTest {
        val connection = Connection()
        val (controller, _) = open(connection)
        controller.loadMetadata("camera", 42, extended = true)
        advanceTimeBy(300); runCurrent()
        controller.closeAndJoin()
        assertEquals(PhotoMetadataState.Idle, controller.metadata.value)
    }

    @Test
    fun theBatteryLevelIsReportedOncePerSession() = runTest {
        val batteries = mutableListOf<Pair<String, Int>>()
        val connection = Connection().apply { battery = 76 }
        val (controller, _) = open(connection, batteries)
        assertEquals(listOf("CAMERA" to 76), batteries.map { it.first.uppercase() to it.second })
        controller.closeAndJoin()
    }

    @Test
    fun anUnknownBatteryReportsNothing() = runTest {
        val batteries = mutableListOf<Pair<String, Int>>()
        val (controller, _) = open(Connection(), batteries)
        assertTrue(batteries.isEmpty())
        controller.closeAndJoin()
    }
}
