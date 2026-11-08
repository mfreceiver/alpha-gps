package com.sasch.cameragps.sharednew.remote.wifi

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import com.sasch.cameragps.sharednew.ui.remote.WifiRemoteViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class WifiRemoteControllerTest : WifiLoggingTest() {
    private class PreviewBitmap(override val width: Int = 1600, override val height: Int = 800) :
        ImageBitmap {
        override val colorSpace = ColorSpaces.Srgb
        override val hasAlpha = false
        override val config = ImageBitmapConfig.Argb8888
        override fun prepareToDraw() = Unit
        override fun readPixels(
            buffer: IntArray, startX: Int, startY: Int, width: Int,
            height: Int, bufferOffset: Int, stride: Int
        ) = Unit
    }

    private class Connection : WifiRemoteConnection {
        override val cameraName = "Test camera"
        override val canCapture = true
        override val canTransferImages = true
        override var canConvertHeif = true
        var browsing = false
        var downloads = 0
        var photos = listOf(WifiCameraPhoto(42L, "DSC.JPG", 100L, "image/jpeg", ""))
        val thumbnailRequests = mutableListOf<Long>()
        var thumbnailImage: ImageBitmap? = null
        val screenPreviewRequests = mutableListOf<Long>()
        var screenPreviewGate: CompletableDeferred<Unit>? = null
        var screenPreviewImage: ImageBitmap? = PreviewBitmap()
        var ignorePreviewCancellation = false
        var failScreenPreview = false
        val unavailablePreviews = mutableSetOf<Long>()
        val failingPreviews = mutableSetOf<Long>()
        val pageRequests = mutableListOf<Int>()
        var pageGate: CompletableDeferred<Unit>? = null
        var failPage = false
        val downloadedHandles = mutableListOf<Long>()
        val downloadedFormats = mutableListOf<WifiPhotoDownloadFormat>()
        var failBrowserExit = false
        val downloadResult = CompletableDeferred<WifiImageTransferState>()
        var downloadResults: Channel<WifiImageTransferState>? = null
        var afterDownload: (() -> Unit)? = null
        override suspend fun openPhotoBrowser(): CameraPhotoPage {
            browsing = true
            cleanup += "browse"
            return cameraPhotoPage(groupCameraPhotos(photos), 0)
        }

        override suspend fun photoPage(offset: Int): CameraPhotoPage {
            pageRequests += offset
            pageGate?.await()
            check(!failPage)
            return cameraPhotoPage(groupCameraPhotos(photos), offset)
        }

        override suspend fun photoThumbnail(handle: Long): ImageBitmap? {
            thumbnailRequests += handle
            return thumbnailImage
        }

        override suspend fun photoPreview(handle: Long): ImageBitmap? {
            screenPreviewRequests += handle
            val gate = screenPreviewGate
            if (ignorePreviewCancellation) withContext(NonCancellable) { gate?.await() }
            else gate?.await()
            check(!failScreenPreview && handle !in failingPreviews)
            return screenPreviewImage.takeUnless { handle in unavailablePreviews }
        }

        override suspend fun downloadPhoto(
            handle: Long,
            format: WifiPhotoDownloadFormat,
            onProgress: (WifiImageTransferState) -> Unit
        ): WifiImageTransferState {
            downloads++
            downloadedHandles += handle
            downloadedFormats += format
            return try {
                val filename = photos.first { it.handle == handle }.filename
                onProgress(
                    WifiImageTransferState(
                        if (format == WifiPhotoDownloadFormat.Jpeg) WifiImageTransferStatus.Converting
                        else WifiImageTransferStatus.Downloading,
                        filename, bytesReceived = 20, totalBytes = 100
                    )
                )
                (downloadResults?.receive() ?: downloadResult.await()).copy(filename = filename)
                    .also { afterDownload?.invoke() }
            } finally {
                cleanup += "download"
            }
        }

        override suspend fun closePhotoBrowser() {
            if (!browsing) return
            check(!failBrowserExit)
            cleanup += "browse-close"
            browsing = false
        }
        val cleanup = mutableListOf<String>()
        val losses = Channel<Unit>(Channel.CONFLATED)
        var result = CompletableDeferred<WifiCaptureStatus>()
        var captures = 0
        var shutdownEnabled = false
        var previewStarts = 0
        override var hasLiveViewStream: Boolean = true
        override val images: Flow<ImageBitmap> = flow {
            previewStarts++
            try { awaitCancellation() } finally { cleanup += "preview" }
        }
        override val lost = losses.receiveAsFlow()
        override suspend fun capture(): WifiCaptureStatus {
            captures++
            return try { result.await() } finally { cleanup += "capture" }
        }
        override suspend fun close() { cleanup += "connection" }
        override suspend fun turnOffWifi(): WifiShutdownStatus {
            if (!shutdownEnabled) return WifiShutdownStatus.NotRequested
            cleanup += "wifi-off"
            return WifiShutdownStatus.Requested
        }
    }

    private val pageSize = SonyImageTransfer.PAGE_SIZE

    private fun pagedConnection() = Connection().apply {
        photos = (0 until SonyImageTransfer.PAGE_SIZE + 2).flatMap { index ->
            listOf(
                WifiCameraPhoto(
                    index * 2L,
                    "$index.JPG",
                    100,
                    "image/jpeg",
                    "",
                    captureId = "photo:$index"
                ),
                WifiCameraPhoto(
                    index * 2L + 1,
                    "$index.ARW",
                    100,
                    "image/x-sony-arw",
                    "",
                    captureId = "photo:$index"
                ),
            )
        }
    }

    @Test
    fun blankAddressUsesManualConnectorWithoutBluetoothSetup() = runTest {
        val registry = CameraSessionRegistry()
        val connection = Connection()
        var receivedHost: String? = null
        val connector = object : WifiRemoteConnector {
            override val supportsAddressDiscovery = true
            override suspend fun open(host: String, scope: CoroutineScope): WifiRemoteConnection {
                receivedHost = host
                return connection
            }
        }
        val controller = WifiRemoteController(
            backgroundScope, registry, connector, {}, {},
            automaticConnector = { _, _, _ -> error("Unexpected Bluetooth setup") },
        )
        assertTrue(controller.supportsAddressDiscovery)
        controller.connect("camera", "  ")
        runCurrent()
        assertEquals("", receivedHost)
        assertEquals(WifiRemotePhase.Ready, registry.get("camera")?.wifiRemote?.phase)
        controller.closeAndJoin()
    }

    @Test
    fun viewerLoadsPairedJpegScreenPreviewOnDemandWithoutReplacingGridThumbnails() = runTest {
        val connection = pagedConnection()
        connection.thumbnailImage = PreviewBitmap(160, 80)
        val controller = WifiRemoteController(
            backgroundScope,
            CameraSessionRegistry(),
            { _, _ -> connection },
            {},
            {})
        val model = WifiRemoteViewModel("camera", controller)
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        assertTrue(connection.screenPreviewRequests.isEmpty())
        val thumbnails = controller.thumbnails.value
        assertEquals(160, thumbnails.getValue((pageSize - 2) * 2L + 1).width)
        connection.screenPreviewGate = CompletableDeferred()
        model.openPhoto("photo:${pageSize - 2}")
        runCurrent()
        assertEquals(listOf((pageSize - 2) * 2L), connection.screenPreviewRequests)
        assertTrue(controller.photoPreview.value!!.loading)
        assertNull(controller.photoPreview.value!!.image)
        connection.screenPreviewGate!!.complete(Unit)
        runCurrent()
        assertEquals(1600, controller.photoPreview.value!!.image!!.width)
        assertFalse(controller.photoPreview.value!!.loading)
        assertEquals(thumbnails, controller.thumbnails.value)
        model.viewPhotoAt(pageSize - 2)
        runCurrent()
        assertEquals(listOf((pageSize - 2) * 2L), connection.screenPreviewRequests)
        model.closePhoto()
        assertNull(controller.photoPreview.value)
        controller.closeAndJoin()
    }

    @Test
    fun swipingDiscardsLatePreviewAndFailureKeepsBrowserUsable() = runTest {
        val connection = pagedConnection()
        val registry = CameraSessionRegistry()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        val model = WifiRemoteViewModel("camera", controller)
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        connection.unavailablePreviews += 0L
        val delayed = CompletableDeferred<Unit>()
        connection.screenPreviewGate = delayed
        connection.ignorePreviewCancellation = true
        model.openPhoto("photo:0")
        runCurrent()
        connection.screenPreviewGate = null
        model.viewPhotoAt(1)
        runCurrent()
        assertEquals(2L, controller.photoPreview.value!!.handle)
        delayed.complete(Unit)
        runCurrent()
        assertEquals(2L, controller.photoPreview.value!!.handle)
        connection.failScreenPreview = true
        model.viewPhotoAt(2)
        runCurrent()
        assertNull(controller.photoPreview.value!!.image)
        assertFalse(controller.photoPreview.value!!.loading)
        assertFalse(registry.get("camera")!!.wifiRemote.photoBrowser.failed)
        model.viewPhotoAt(2)
        runCurrent()
        assertEquals(listOf(0L, 2L, 4L, 5L), connection.screenPreviewRequests)
        controller.closeAndJoin()
        assertNull(controller.photoPreview.value)
    }

    @Test
    fun downloadCancelsPendingPreviewAndPageChangeClearsIt() = runTest {
        val connection = pagedConnection()
        val controller = WifiRemoteController(
            backgroundScope,
            CameraSessionRegistry(),
            { _, _ -> connection },
            {},
            {})
        val model = WifiRemoteViewModel("camera", controller)
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        connection.screenPreviewGate = CompletableDeferred()
        model.openPhoto("photo:0")
        runCurrent()
        controller.downloadPhoto("camera", 1L)
        runCurrent()
        assertNull(controller.photoPreview.value)
        model.viewPhotoAt(1)
        runCurrent()
        assertEquals(listOf(0L), connection.screenPreviewRequests)
        connection.downloadResult.complete(WifiImageTransferState(WifiImageTransferStatus.Saved))
        connection.screenPreviewGate = null
        runCurrent()
        model.viewPhotoAt(1)
        runCurrent()
        assertEquals(2L, controller.photoPreview.value!!.handle)
        model.viewPhotoAt(pageSize)
        runCurrent()
        assertNull(controller.photoPreview.value)
        model.viewPhotoAt(pageSize)
        runCurrent()
        assertEquals(pageSize * 2L, controller.photoPreview.value!!.handle)
        controller.closeAndJoin()
    }

    @Test
    fun photoViewerCrossesPageBoundariesInBothDirectionsAndBackReturnsToGrid() = runTest {
        val registry = CameraSessionRegistry()
        val connection = pagedConnection()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        val model = WifiRemoteViewModel("camera", controller)
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        model.openPhoto("photo:${pageSize - 2}")
        assertEquals(pageSize - 2, model.viewedPhotoIndex)
        model.viewPhotoAt(pageSize - 1)
        assertEquals(pageSize - 1, model.viewedPhotoIndex)
        assertTrue(connection.pageRequests.isEmpty())
        connection.pageGate = CompletableDeferred()
        model.viewPhotoAt(pageSize)
        runCurrent()
        assertEquals(pageSize, model.viewedPhotoIndex)
        assertEquals(listOf(pageSize), connection.pageRequests)
        assertEquals(0, registry.get("camera")!!.wifiRemote.photoBrowser.offset)
        assertEquals(
            pageSize, groupCameraPhotos(registry.get("camera")!!.wifiRemote.photoBrowser.photos).size,
            "Keep the current page until its replacement arrives"
        )
        model.viewPhotoAt(pageSize)
        assertEquals(listOf(pageSize), connection.pageRequests)
        connection.pageGate!!.complete(Unit)
        runCurrent()
        assertEquals(pageSize, registry.get("camera")!!.wifiRemote.photoBrowser.offset)
        assertEquals(
            listOf("photo:$pageSize", "photo:${pageSize + 1}"),
            groupCameraPhotos(registry.get("camera")!!.wifiRemote.photoBrowser.photos).map { it.id })
        model.viewPhotoAt(pageSize + 1)
        model.viewPhotoAt(pageSize + 2)
        assertEquals(pageSize + 1, model.viewedPhotoIndex, "Stop at the end of the catalog")
        assertEquals(listOf(pageSize), connection.pageRequests)
        model.viewPhotoAt(pageSize)
        model.viewPhotoAt(pageSize - 1)
        runCurrent()
        assertEquals(listOf(pageSize, 0), connection.pageRequests)
        assertEquals(
            (pageSize - 1) * 2L + 1, connection.thumbnailRequests.takeLast(pageSize).first(),
            "Load the displayed photo first when entering the previous page at its end"
        )
        assertEquals(
            pageSize - 1,
            model.viewedPhotoIndex,
            "Returning to the previous page selects its last capture"
        )
        assertEquals(0, registry.get("camera")!!.wifiRemote.photoBrowser.offset)
        model.navigateBack { error("Viewer must return to grid") }
        assertNull(model.viewedPhotoIndex)
        assertTrue(registry.get("camera")!!.wifiRemote.photoBrowser.open)
        assertEquals(1, connection.previewStarts)
        model.navigateBack { error("Grid must return to live view") }
        runCurrent()
        assertFalse(registry.get("camera")!!.wifiRemote.photoBrowser.open)
        assertEquals(2, connection.previewStarts)
        controller.closeAndJoin()
    }

    @Test
    fun failedViewerPageRetainsCurrentPhotosAndCanBeRetried() = runTest {
        val registry = CameraSessionRegistry()
        val connection = pagedConnection()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        val model = WifiRemoteViewModel("camera", controller)
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        val originalPage = registry.get("camera")!!.wifiRemote.photoBrowser.photos
        model.openPhoto("photo:${pageSize - 1}")
        connection.failPage = true
        model.viewPhotoAt(pageSize)
        runCurrent()
        val failed = registry.get("camera")!!.wifiRemote.photoBrowser
        assertTrue(failed.failed)
        assertFalse(failed.loading)
        assertEquals(originalPage, failed.photos)
        assertEquals(0, failed.offset)
        assertEquals(pageSize, model.viewedPhotoIndex)
        connection.failPage = false
        model.viewPhotoAt(pageSize)
        runCurrent()
        assertEquals(listOf(pageSize, pageSize), connection.pageRequests)
        assertEquals(pageSize, registry.get("camera")!!.wifiRemote.photoBrowser.offset)
        assertFalse(registry.get("camera")!!.wifiRemote.photoBrowser.failed)
        controller.closeAndJoin()
        model.reconcilePhotoSelection()
        assertNull(model.viewedPhotoIndex)
    }

    @Test
    fun viewerDoesNotTurnPagesDuringDownloadAndSelectionTapsDoNotOpenIt() = runTest {
        val registry = CameraSessionRegistry()
        val connection = pagedConnection()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        val model = WifiRemoteViewModel("camera", controller)
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        model.togglePhotoSelection("photo:0")
        model.openPhoto("photo:0")
        assertNull(model.viewedPhotoIndex)
        model.clearPhotoSelection()
        model.openPhoto("photo:0")
        model.viewPhotoAt(-1)
        assertEquals(0, model.viewedPhotoIndex)
        controller.downloadPhoto("camera", 0)
        runCurrent()
        model.viewPhotoAt(pageSize - 1)
        assertEquals(
            pageSize - 1,
            model.viewedPhotoIndex,
            "Viewing loaded photos remains available during download"
        )
        model.viewPhotoAt(pageSize)
        assertEquals(pageSize - 1, model.viewedPhotoIndex)
        assertTrue(connection.pageRequests.isEmpty())
        controller.cancelPhotoOperation("camera")
        runCurrent()
        connection.pageGate = CompletableDeferred()
        model.viewPhotoAt(pageSize)
        runCurrent()
        model.navigateBack { error("Viewer must return to grid while a page loads") }
        assertNull(model.viewedPhotoIndex)
        connection.pageGate!!.complete(Unit)
        runCurrent()
        model.reconcilePhotoSelection()
        assertNull(model.viewedPhotoIndex, "Completing a page load must not reopen the viewer")
        assertEquals(pageSize, registry.get("camera")!!.wifiRemote.photoBrowser.offset)
        controller.closeAndJoin()
    }

    private class ManualSetup(private val connection: Connection) : WifiManualSetupConnector {
        val credentials = CameraWifiCredentials("DIRECT-camera", "secret-pass", null)
        var preparations = 0
        var opens = 0
        var prepareGate: CompletableDeferred<Unit>? = null
        var openGate: CompletableDeferred<Unit>? = null
        var missingCredentials = false
        var openFailure: WifiRemoteFailure? = null
        override suspend fun prepare(identifier: String): CameraWifiCredentials? {
            preparations++
            prepareGate?.await()
            return credentials.takeUnless { missingCredentials }
        }

        override suspend fun open(
            credentials: CameraWifiCredentials,
            scope: kotlinx.coroutines.CoroutineScope
        ): WifiRemoteConnection {
            assertTrue(credentials === this.credentials)
            opens++
            openGate?.await()
            openFailure?.let { throw WifiRemoteConnectException(it) }
            return connection
        }
    }

    @Test
    fun manualSetupSurvivesWifiSettingsAndWaitsForExplicitConfirmation() = runTest {
        val registry = CameraSessionRegistry()
        val connection = Connection()
        val setup = ManualSetup(connection)
        val controls = mutableListOf<String>()
        val controller = WifiRemoteController(
            backgroundScope,
            registry,
            { _, _ -> error("Must detect the camera network") },
            { controls += "claim" },
            { controls += "release" },
            manualSetupConnector = setup
        )
        assertFalse(controller.supportsAutomaticConnection)
        assertTrue(controller.supportsManualSetup)
        controller.continueManualConnection("camera")
        controller.prepareManualConnection("camera")
        runCurrent()
        val waiting = registry.get("camera")!!.wifiRemote
        assertEquals(WifiRemotePhase.AwaitingManualNetwork, waiting.phase)
        assertEquals("DIRECT-camera", waiting.manualNetwork?.ssid)
        assertEquals("secret-pass", waiting.manualNetwork?.password)
        assertFalse(waiting.toString().contains("secret-pass"))
        assertFalse(waiting.toString().contains("DIRECT-camera"))
        controller.prepareManualConnection("camera")
        controller.continueManualConnection("other-camera")
        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(1, setup.preparations)
        assertEquals(0, setup.opens)
        assertEquals(listOf("claim"), controls)
        assertEquals("CAMERA", controller.owner.value)

        controller.continueManualConnection("camera")
        assertEquals(WifiRemotePhase.OpeningSession, registry.get("camera")!!.wifiRemote.phase)
        controller.continueManualConnection("camera")
        runCurrent()
        assertEquals(1, setup.opens)
        assertEquals(WifiRemotePhase.Ready, registry.get("camera")!!.wifiRemote.phase)
        assertNull(registry.get("camera")!!.wifiRemote.manualNetwork)
        controller.closeAndJoin()
        assertNull(controller.owner.value)
        assertEquals(listOf("claim", "release"), controls)
        assertTrue("connection" in connection.cleanup)
    }

    @Test
    fun closingManualSetupClearsCredentialsAndReleasesControlsWithoutOpeningSockets() = runTest {
        val registry = CameraSessionRegistry()
        val setup = ManualSetup(Connection())
        var releases = 0
        val controller = WifiRemoteController(
            backgroundScope,
            registry,
            { _, _ -> error("Not manual IP entry") },
            {},
            { releases++ },
            manualSetupConnector = setup
        )
        controller.prepareManualConnection("camera")
        runCurrent()
        controller.closeAndJoin("camera")
        assertEquals(0, setup.opens)
        assertEquals(1, releases)
        assertNull(controller.owner.value)
        assertNull(registry.get("camera")?.wifiRemote?.manualNetwork)
        controller.continueManualConnection("camera")
        runCurrent()
        assertEquals(0, setup.opens)
    }

    @Test
    fun failedManualJoinClearsCredentialsAndAllowsAnotherPreparation() = runTest {
        val registry = CameraSessionRegistry()
        val setup =
            ManualSetup(Connection()).apply { openFailure = WifiRemoteFailure.JoinCameraWifi }
        val controller = WifiRemoteController(
            backgroundScope,
            registry,
            { _, _ -> error("Not manual IP entry") },
            {},
            {},
            manualSetupConnector = setup
        )
        controller.prepareManualConnection("camera")
        runCurrent()
        controller.continueManualConnection("camera")
        runCurrent()
        assertEquals(WifiRemoteFailure.JoinCameraWifi, registry.get("camera")!!.wifiRemote.failure)
        assertNull(registry.get("camera")!!.wifiRemote.manualNetwork)
        assertNull(controller.owner.value)
        controller.prepareManualConnection("camera")
        runCurrent()
        assertEquals(2, setup.preparations)
        assertEquals(
            WifiRemotePhase.AwaitingManualNetwork,
            registry.get("camera")!!.wifiRemote.phase
        )
        controller.closeAndJoin()
    }

    @Test
    fun unsuccessfulBluetoothPreparationDoesNotOfferManualJoin() = runTest {
        val registry = CameraSessionRegistry()
        val setup = ManualSetup(Connection()).apply { missingCredentials = true }
        var releases = 0
        val controller = WifiRemoteController(
            backgroundScope,
            registry,
            { _, _ -> error("Not manual IP entry") },
            {},
            { releases++ },
            manualSetupConnector = setup
        )
        controller.prepareManualConnection("camera")
        runCurrent()
        assertEquals(
            WifiRemoteFailure.CameraSetupFailed,
            registry.get("camera")!!.wifiRemote.failure
        )
        assertNull(registry.get("camera")!!.wifiRemote.manualNetwork)
        assertEquals(0, setup.opens)
        assertEquals(1, releases)
        assertNull(controller.owner.value)
    }

    @Test
    fun preparationAndSessionOpeningWaitUntilExplicitlyCancelled() = runTest {
        val registry = CameraSessionRegistry()
        val setup = ManualSetup(Connection()).apply { prepareGate = CompletableDeferred() }
        val controller = WifiRemoteController(
            backgroundScope,
            registry,
            { _, _ -> error("Not manual IP entry") },
            {},
            {},
            manualSetupConnector = setup
        )
        controller.prepareManualConnection("camera")
        runCurrent()
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals("CAMERA", controller.owner.value)
        controller.closeAndJoin()
        assertNull(controller.owner.value)
        setup.prepareGate = null
        setup.openGate = CompletableDeferred()
        controller.prepareManualConnection("camera")
        runCurrent()
        controller.continueManualConnection("camera")
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals("CAMERA", controller.owner.value)
        controller.closeAndJoin()
        assertEquals(1, setup.opens)
        assertNull(controller.owner.value)
        assertNull(registry.get("camera")?.wifiRemote?.manualNetwork)
    }

    @Test
    fun sessionStaysOpenUntilExplicitlyDisconnected() = runTest {
        val connection = Connection().apply { shutdownEnabled = true }
        val registry = CameraSessionRegistry()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals("CAMERA", controller.owner.value)
        assertEquals(WifiRemotePhase.Ready, registry.get("camera")!!.wifiRemote.phase)
        assertEquals(1, connection.previewStarts)
        assertTrue(connection.cleanup.isEmpty())
        controller.closeAndJoin()
        assertNull(controller.owner.value)
        assertEquals(listOf("preview", "wifi-off", "connection"), connection.cleanup)
    }

    @Test
    fun backFromGalleryResumesPreviewBeforeLeavingTheRemoteScreen() = runTest {
        val registry = CameraSessionRegistry()
        val connection = Connection()
        val controller = WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        val model = WifiRemoteViewModel("camera", controller)
        var exits = 0
        val close = { exits++; controller.disconnect("camera") }
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()

        model.navigateBack(close)
        runCurrent()

        assertEquals(0, exits)
        assertEquals("CAMERA", controller.owner.value)
        assertFalse(registry.get("camera")!!.wifiRemote.photoBrowser.open)
        assertEquals(2, connection.previewStarts)
        assertFalse("connection" in connection.cleanup)

        model.navigateBack(close)
        runCurrent()
        assertEquals(1, exits)
        assertTrue("connection" in connection.cleanup)
    }

    @Test
    fun backDuringDownloadDoesNotDisconnectOrLeaveTheGallery() = runTest {
        val registry = CameraSessionRegistry()
        val connection = Connection()
        val controller = WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        val model = WifiRemoteViewModel("camera", controller)
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        controller.downloadPhoto("camera", 42L)
        runCurrent()

        model.navigateBack { error("Busy gallery must not exit remote shooting") }
        runCurrent()

        assertTrue(registry.get("camera")!!.wifiRemote.photoBrowser.open)
        assertTrue(registry.get("camera")!!.wifiRemote.imageTransfer.busy)
        assertFalse("connection" in connection.cleanup)
        controller.cancelPhotoOperation("camera")
        runCurrent()
        model.navigateBack { error("Gallery must return to live view") }
        runCurrent()
        assertFalse(registry.get("camera")!!.wifiRemote.photoBrowser.open)
        assertEquals(2, connection.previewStarts)
        controller.closeAndJoin()
    }

    @Test
    fun disconnectFinishesCaptureAndPreviewCleanupBeforeClosingConnection() = runTest {
        val registry = CameraSessionRegistry()
        val connection = Connection()
        val controller = WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, { connection.cleanup += "ble" })
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        assertEquals(WifiRemotePhase.Ready, registry.get("camera")?.wifiRemote?.phase)
        controller.capture("camera")
        controller.capture("camera")
        runCurrent()
        assertEquals(1, connection.captures)
        controller.disconnect("camera")
        runCurrent()
        assertEquals(setOf("preview", "capture"), connection.cleanup.take(2).toSet())
        assertEquals(listOf("connection", "ble"), connection.cleanup.takeLast(2))
        assertNull(controller.owner.value)
        assertNull(registry.get("camera"))
    }

    @Test
    fun networkFailureSurvivesCleanupAndSuccessfulRetryClearsGuidance() = runTest {
        val registry = CameraSessionRegistry()
        val connection = Connection()
        var fail = true
        var releases = 0
        val controller = WifiRemoteController(backgroundScope, registry, { _, _ ->
            if (fail) throw WifiRemoteConnectException(WifiRemoteFailure.NetworkConnectionFailed)
            connection
        }, {}, { releases++ })
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        assertEquals(
            WifiRemoteFailure.NetworkConnectionFailed,
            registry.get("camera")!!.wifiRemote.failure
        )
        assertEquals(1, releases)
        assertNull(controller.owner.value)

        fail = false
        controller.connect("camera", "127.0.0.1")
        assertNull(registry.get("camera")!!.wifiRemote.failure)
        runCurrent()
        assertEquals(WifiRemotePhase.Ready, registry.get("camera")!!.wifiRemote.phase)
        controller.closeAndJoin()
    }

    @Test
    fun failedOpenCanRetryAndNeverCreatesDuplicateSessions() = runTest {
        val registry = CameraSessionRegistry()
        var opens = 0
        val controller = WifiRemoteController(backgroundScope, registry, { _, _ ->
            opens++
            throw WifiRemoteConnectException(WifiRemoteFailure.JoinCameraWifi)
        }, {}, {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        assertEquals(WifiRemoteFailure.JoinCameraWifi, registry.get("camera")?.wifiRemote?.failure)
        assertNull(controller.owner.value)
        controller.connect("camera", "127.0.0.1")
        controller.connect("other", "127.0.0.1")
        runCurrent()
        assertEquals(2, opens)
        assertNull(registry.get("other"))
    }

    @Test
    fun captureConfirmationDisappearsAfterThreeSeconds() = runTest {
        val registry = CameraSessionRegistry()
        val connection = Connection()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.capture("camera")
        connection.result.complete(WifiCaptureStatus.Captured)
        runCurrent()
        advanceTimeBy(2.seconds)
        assertEquals(WifiCaptureStatus.Captured, registry.get("camera")!!.wifiRemote.capture)
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(WifiCaptureStatus.Idle, registry.get("camera")!!.wifiRemote.capture)
        assertEquals(WifiRemotePhase.Ready, registry.get("camera")!!.wifiRemote.phase)
        controller.closeAndJoin()
    }

    @Test
    fun oldConfirmationTimerCannotClearANewerShotOrItsResult() = runTest {
        for (pending in listOf(false, true)) {
            val registry = CameraSessionRegistry()
            val connection = Connection()
            val controller =
                WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
            controller.connect("camera", "127.0.0.1")
            runCurrent()
            controller.capture("camera")
            connection.result.complete(WifiCaptureStatus.Captured)
            runCurrent()
            advanceTimeBy(2.seconds)
            connection.result = CompletableDeferred()
            controller.capture("camera")
            if (!pending) connection.result.complete(WifiCaptureStatus.Captured)
            runCurrent()
            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(
                if (pending) WifiCaptureStatus.Shooting else WifiCaptureStatus.Captured,
                registry.get("camera")!!.wifiRemote.capture,
            )
            if (pending) {
                connection.result.complete(WifiCaptureStatus.Captured)
                runCurrent()
                advanceTimeBy(2.seconds)
                assertEquals(
                    WifiCaptureStatus.Captured,
                    registry.get("camera")!!.wifiRemote.capture
                )
            }
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(WifiCaptureStatus.Idle, registry.get("camera")!!.wifiRemote.capture)
            controller.closeAndJoin()
        }
    }

    @Test
    fun reportsConfirmedCaptureAndClosesOnNetworkLossWithoutRetry() = runTest {
        val registry = CameraSessionRegistry()
        val connection = Connection()
        val controller = WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.capture("camera")
        runCurrent()
        connection.result.complete(WifiCaptureStatus.Captured)
        runCurrent()
        assertEquals(WifiCaptureStatus.Captured, registry.get("camera")?.wifiRemote?.capture)
        connection.losses.send(Unit)
        runCurrent()
        assertEquals(WifiRemoteFailure.NetworkLost, registry.get("camera")?.wifiRemote?.failure)
        assertEquals(1, connection.captures)
        assertNull(controller.owner.value)
    }

    @Test
    fun cancellationWhileOpeningReleasesBleOwnership() = runTest {
        var released = false
        val controller = WifiRemoteController(backgroundScope, CameraSessionRegistry(), { _, _ -> awaitCancellation() },
            {}, { released = true })
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.closeAndJoin()
        assertTrue(released)
        assertNull(controller.owner.value)
    }

    @Test
    fun cancellationBeforeDispatchDoesNotLeaveBusyOwner() = runTest {
        val controller = WifiRemoteController(backgroundScope, CameraSessionRegistry(), { _, _ -> awaitCancellation() }, {}, {})
        controller.connect("camera", "127.0.0.1")
        controller.disconnect()
        runCurrent()
        assertNull(controller.owner.value)
    }

    @Test
    fun automaticApprovalUsesSessionStateAndExcludesAnotherAttempt() = runTest {
        val registry = CameraSessionRegistry()
        var attempts = 0
        var released = false
        val controller = WifiRemoteController(backgroundScope, registry, { _, _ -> error("Not manual") }, {},
            { released = true }, WifiAutomaticConnector { id, _, onPhase ->
                assertEquals("CAMERA", id)
                attempts++
                onPhase(WifiRemotePhase.AwaitingNetworkApproval)
                awaitCancellation()
            })
        controller.connectAutomatically("camera")
        runCurrent()
        assertEquals(WifiRemotePhase.AwaitingNetworkApproval, registry.get("camera")?.wifiRemote?.phase)
        controller.connectAutomatically("other")
        controller.connect("other", "127.0.0.1")
        controller.capture("camera")
        assertEquals(1, attempts)
        controller.closeAndJoin()
        assertTrue(released)
        assertNull(controller.owner.value)
    }

    @Test
    fun disconnectRequestsWifiOffAfterPreviewCleanupAndBeforeClosingSockets() = runTest {
        val registry = CameraSessionRegistry()
        val connection = Connection().apply { shutdownEnabled = true }
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.closeAndJoin()
        assertEquals(listOf("preview", "wifi-off", "connection"), connection.cleanup)
        assertEquals(WifiShutdownStatus.Requested, registry.get("camera")?.wifiRemote?.wifiShutdown)
    }

    @Test
    fun connectionLossDoesNotAttemptWifiShutdown() = runTest {
        val connection = Connection().apply { shutdownEnabled = true }
        val controller = WifiRemoteController(
            backgroundScope,
            CameraSessionRegistry(),
            { _, _ -> connection },
            {},
            {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        connection.losses.send(Unit)
        runCurrent()
        assertFalse("wifi-off" in connection.cleanup)
    }

    @Test
    fun browserPausesPreviewBlocksCaptureAndPreventsDuplicateDownloads() = runTest {
        val registry = CameraSessionRegistry()
        val connection = Connection()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        controller.capture("camera")
        runCurrent()
        assertEquals(listOf("preview", "browse"), connection.cleanup)
        assertEquals(0, connection.captures)
        controller.downloadPhoto("camera", 42L)
        controller.downloadPhoto("camera", 42L)
        runCurrent()
        assertEquals(1, connection.downloads)
        connection.downloadResult.complete(
            WifiImageTransferState(
                WifiImageTransferStatus.Saved,
                "DSC.JPG"
            )
        )
        runCurrent()
        controller.downloadPhoto("camera", 42L)
        runCurrent()
        assertEquals(1, connection.downloads)
        assertEquals(setOf(WifiPhotoDownload(42)), registry.get("camera")?.wifiRemote?.photoBrowser?.savedDownloads)
        controller.leavePhotoBrowser("camera")
        runCurrent()
        assertFalse(registry.get("camera")!!.wifiRemote.photoBrowser.open)
        controller.closeAndJoin()
        assertEquals(
            listOf("browse-close", "preview", "connection"),
            connection.cleanup.takeLast(3)
        )
    }

    @Test
    fun navigatingPhotosClearsSavedMessagesButRetainsDownloadedFiles() = runTest {
        val registry = CameraSessionRegistry()
        val connection = pagedConnection().apply {
            downloadResult.complete(WifiImageTransferState(WifiImageTransferStatus.Saved))
        }
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        val model = WifiRemoteViewModel("camera", controller)
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        model.openPhoto("photo:0")
        controller.downloadPhoto("camera", 0L)
        runCurrent()
        assertEquals("0.JPG", registry.get("camera")!!.wifiRemote.imageTransfer.filename)
        model.viewPhotoAt(0)
        controller.clearPhotoTransferResult("other-camera")
        assertEquals(
            WifiImageTransferStatus.Saved,
            registry.get("camera")!!.wifiRemote.imageTransfer.status
        )
        model.viewPhotoAt(1)
        assertEquals(WifiImageTransferState(), registry.get("camera")!!.wifiRemote.imageTransfer)
        controller.downloadPhoto("camera", 2L)
        runCurrent()
        assertEquals(
            WifiImageTransferStatus.Saved,
            registry.get("camera")!!.wifiRemote.imageTransfer.status
        )
        model.closePhoto()
        assertEquals(WifiImageTransferState(), registry.get("camera")!!.wifiRemote.imageTransfer)
        controller.downloadPhoto("camera", 4L)
        runCurrent()
        model.openPhoto("photo:3")
        assertEquals(WifiImageTransferState(), registry.get("camera")!!.wifiRemote.imageTransfer)
        assertEquals(
            setOf(WifiPhotoDownload(0L), WifiPhotoDownload(2L), WifiPhotoDownload(4L)),
            registry.get("camera")!!.wifiRemote.photoBrowser.savedDownloads,
        )
        controller.downloadPhoto("camera", 0L)
        runCurrent()
        assertEquals(3, connection.downloads)
        controller.closeAndJoin()
    }

    @Test
    fun clearingResultsPreservesDownloadsAndConversionsInProgress() = runTest {
        for (format in WifiPhotoDownloadFormat.entries) {
            val registry = CameraSessionRegistry()
            val connection = Connection().apply {
                photos = listOf(
                    WifiCameraPhoto(
                        42L,
                        "PHOTO.HIF",
                        100L,
                        "image/heif",
                        "",
                        captureId = "photo"
                    )
                )
            }
            val controller =
                WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
            val model = WifiRemoteViewModel("camera", controller)
            controller.connect("camera", "127.0.0.1")
            runCurrent()
            controller.browsePhotos("camera")
            runCurrent()
            controller.downloadPhoto("camera", 42L, format)
            runCurrent()
            val progress = registry.get("camera")!!.wifiRemote.imageTransfer
            assertTrue(progress.busy)
            controller.clearPhotoTransferResult("camera")
            model.openPhoto("photo")
            model.closePhoto()
            assertEquals(progress, registry.get("camera")!!.wifiRemote.imageTransfer)
            connection.downloadResult.complete(WifiImageTransferState(WifiImageTransferStatus.Saved))
            runCurrent()
            controller.clearPhotoTransferResult("camera")
            assertEquals(
                WifiImageTransferState(),
                registry.get("camera")!!.wifiRemote.imageTransfer
            )
            assertEquals(
                setOf(WifiPhotoDownload(42L, format)),
                registry.get("camera")!!.wifiRemote.photoBrowser.savedDownloads
            )
            controller.closeAndJoin()
        }
    }

    @Test
    fun pairedCapturesPreferCompanionPreviewsAndDownloadEachSelectedFormatIndependently() =
        runTest {
        val registry = CameraSessionRegistry()
        val connection = Connection().apply {
            photos = listOf(
                WifiCameraPhoto(42, "ONE.HIF", 100, "image/heif", "", captureId = "one"),
                WifiCameraPhoto(43, "ONE.ARW", 100, "image/x-sony-arw", "", captureId = "one"),
                WifiCameraPhoto(44, "TWO.JPG", 100, "image/jpeg", "", captureId = "two"),
                WifiCameraPhoto(45, "TWO.ARW", 100, "image/x-sony-arw", "", captureId = "two"),
                WifiCameraPhoto(46, "THREE.HIF", 100, "image/heif", "", captureId = "three"),
            )
        }
        val controller = WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        assertEquals(listOf(43L, 45L, 46L), connection.thumbnailRequests)
            controller.showPhotoPreview("camera", 42L)
            runCurrent()
            controller.showPhotoPreview("camera", 44L)
            runCurrent()
            assertEquals(listOf(42L, 44L), connection.screenPreviewRequests)
        connection.downloadResult.complete(WifiImageTransferState(WifiImageTransferStatus.Saved))
        controller.downloadPhoto("camera", 42)
        runCurrent()
        assertEquals(setOf(WifiPhotoDownload(42)), registry.get("camera")!!.wifiRemote.photoBrowser.savedDownloads)
        controller.downloadPhoto("camera", 43)
        runCurrent()
        assertEquals(listOf(42L, 43L), connection.downloadedHandles)
        assertEquals(setOf(WifiPhotoDownload(42), WifiPhotoDownload(43)), registry.get("camera")!!.wifiRemote.photoBrowser.savedDownloads)
        controller.closeAndJoin()
    }

    @Test
    fun heifPairsFallBackToRawWhenCompanionPreviewIsMissingOrThrows() = runTest {
        for (throws in listOf(false, true)) {
            val connection = Connection().apply {
                photos = listOf(
                    WifiCameraPhoto(42, "ONE.HIF", 100, "image/heif", "", captureId = "one"),
                    WifiCameraPhoto(43, "ONE.ARW", 100, "image/x-sony-arw", "", captureId = "one"),
                )
                thumbnailImage = PreviewBitmap(160, 80)
                if (throws) failingPreviews += 42L else unavailablePreviews += 42L
            }
            val controller = WifiRemoteController(
                backgroundScope,
                CameraSessionRegistry(),
                { _, _ -> connection },
                {},
                {})
            val model = WifiRemoteViewModel("camera", controller)
            controller.connect("camera", "127.0.0.1")
            runCurrent()
            controller.browsePhotos("camera")
            runCurrent()
            assertEquals(listOf(43L), connection.thumbnailRequests)
            val thumbnails = controller.thumbnails.value
            assertEquals(160, thumbnails.getValue(43L).width)
            model.openPhoto("one")
            runCurrent()
            assertEquals(listOf(42L, 43L), connection.screenPreviewRequests)
            assertEquals(42L, controller.photoPreview.value!!.handle)
            assertEquals(connection.screenPreviewImage, controller.photoPreview.value!!.image)
            assertFalse(controller.photoPreview.value!!.loading)
            assertEquals(thumbnails, controller.thumbnails.value)
            controller.closeAndJoin()
        }
    }

    @Test
    fun unavailableHeifAndRawPreviewsKeepTheRawGalleryThumbnail() = runTest {
        val connection = Connection().apply {
            photos = listOf(
                WifiCameraPhoto(42, "ONE.HIF", 100, "image/heif", "", captureId = "one"),
                WifiCameraPhoto(43, "ONE.ARW", 100, "image/x-sony-arw", "", captureId = "one"),
            )
            thumbnailImage = PreviewBitmap(160, 80)
            unavailablePreviews += listOf(42L, 43L)
        }
        val registry = CameraSessionRegistry()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        controller.showPhotoPreview("camera", 42L)
        runCurrent()
        assertEquals(listOf(42L, 43L), connection.screenPreviewRequests)
        assertNull(controller.photoPreview.value!!.image)
        assertFalse(controller.photoPreview.value!!.loading)
        val capture =
            groupCameraPhotos(registry.get("camera")!!.wifiRemote.photoBrowser.photos).single()
        assertEquals(
            connection.thumbnailImage,
            controller.thumbnails.value[capture.thumbnail.handle]
        )
        assertFalse(registry.get("camera")!!.wifiRemote.photoBrowser.failed)
        controller.closeAndJoin()
    }

    @Test
    fun jpegCopiesAndOriginalsHaveIndependentSavedStateAndRejectUnsupportedConversions() = runTest {
        val registry = CameraSessionRegistry()
        val connection = Connection().apply {
            photos = listOf(
                WifiCameraPhoto(42, "ONE.HIF", 100, "image/heif", ""),
                WifiCameraPhoto(43, "TWO.JPG", 100, "image/jpeg", ""),
            )
            downloadResult.complete(WifiImageTransferState(WifiImageTransferStatus.Saved))
        }
        val controller = WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        controller.downloadPhoto("camera", 43, WifiPhotoDownloadFormat.Jpeg)
        assertEquals(0, connection.downloads)
        controller.downloadPhoto("camera", 42, WifiPhotoDownloadFormat.Jpeg)
        runCurrent()
        controller.downloadPhoto("camera", 42, WifiPhotoDownloadFormat.Jpeg)
        runCurrent()
        assertEquals(1, connection.downloads)
        controller.downloadPhoto("camera", 42)
        runCurrent()
        assertEquals(listOf(WifiPhotoDownloadFormat.Jpeg, WifiPhotoDownloadFormat.Original), connection.downloadedFormats)
        assertEquals(setOf(WifiPhotoDownload(42), WifiPhotoDownload(42, WifiPhotoDownloadFormat.Jpeg)),
            registry.get("camera")!!.wifiRemote.photoBrowser.savedDownloads)
        controller.closeAndJoin()

        connection.canConvertHeif = false
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        controller.downloadPhoto("camera", 42, WifiPhotoDownloadFormat.Jpeg)
        runCurrent()
        assertEquals(2, connection.downloads)
        controller.closeAndJoin()
    }

    private fun batchConnection() = Connection().apply {
        photos = listOf(
            WifiCameraPhoto(42, "ONE.HIF", 100, "image/heif", "", captureId = "one"),
            WifiCameraPhoto(43, "ONE.ARW", 100, "image/x-sony-arw", "", captureId = "one"),
            WifiCameraPhoto(44, "TWO.JPG", 100, "image/jpeg", "", captureId = "two"),
            WifiCameraPhoto(
                45,
                "LARGE.ARW",
                600_000_000,
                "image/x-sony-arw",
                "",
                downloadable = false
            ),
        )
        downloadResults = Channel(Channel.UNLIMITED)
    }

    @Test
    fun batchDownloadsRunSequentiallyAndTrackProgressAcrossConversions() = runTest {
        val registry = CameraSessionRegistry()
        val connection = batchConnection()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        val batch = listOf(
            WifiPhotoDownload(43),
            WifiPhotoDownload(42, WifiPhotoDownloadFormat.Jpeg),
            WifiPhotoDownload(44)
        )
        controller.downloadPhotos(
            "camera", batch + listOf(
                batch.first(), WifiPhotoDownload(999),
                WifiPhotoDownload(45), WifiPhotoDownload(44, WifiPhotoDownloadFormat.Jpeg)
            )
        )
        runCurrent()
        assertEquals(listOf(43L), connection.downloadedHandles)
        assertEquals(3, registry.get("camera")!!.wifiRemote.imageTransfer.totalFiles)
        controller.downloadPhotos("camera", batch)
        controller.leavePhotoBrowser("camera")
        runCurrent()
        assertEquals(1, connection.downloads)
        assertTrue(connection.browsing)

        connection.downloadResults!!.send(WifiImageTransferState(WifiImageTransferStatus.Saved))
        runCurrent()
        val converting = registry.get("camera")!!.wifiRemote.imageTransfer
        assertEquals(1, converting.completedFiles)
        assertEquals(3, converting.totalFiles)
        assertEquals(WifiImageTransferStatus.Converting, converting.status)
        assertTrue(converting.busy)
        assertEquals(listOf(43L, 42L), connection.downloadedHandles)
        connection.downloadResults!!.send(WifiImageTransferState(WifiImageTransferStatus.Saved))
        runCurrent()
        assertEquals(2, registry.get("camera")!!.wifiRemote.imageTransfer.completedFiles)
        connection.downloadResults!!.send(WifiImageTransferState(WifiImageTransferStatus.Saved))
        runCurrent()
        val finished = registry.get("camera")!!.wifiRemote
        assertEquals(WifiImageTransferStatus.Saved, finished.imageTransfer.status)
        assertEquals(3, finished.imageTransfer.completedFiles)
        assertEquals(batch.toSet(), finished.photoBrowser.savedDownloads)
        controller.downloadPhotos("camera", batch)
        runCurrent()
        assertEquals(3, connection.downloads)
        controller.closeAndJoin()
    }

    @Test
    fun cancellingBatchKeepsCompletedFilesAndRetrySkipsThem() = runTest {
        val registry = CameraSessionRegistry()
        val connection = batchConnection()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        val batch = listOf(WifiPhotoDownload(42), WifiPhotoDownload(43), WifiPhotoDownload(44))
        controller.downloadPhotos("camera", batch)
        runCurrent()
        connection.downloadResults!!.send(WifiImageTransferState(WifiImageTransferStatus.Saved))
        runCurrent()
        controller.cancelPhotoOperation("camera")
        runCurrent()
        val cancelled = registry.get("camera")!!.wifiRemote
        assertEquals(WifiImageTransferStatus.Cancelled, cancelled.imageTransfer.status)
        assertEquals(1, cancelled.imageTransfer.completedFiles)
        assertEquals(3, cancelled.imageTransfer.totalFiles)
        assertEquals(setOf(batch.first()), cancelled.photoBrowser.savedDownloads)
        assertEquals(listOf(42L, 43L), connection.downloadedHandles)
        assertEquals("CAMERA", controller.owner.value)

        controller.downloadPhotos("camera", batch)
        runCurrent()
        assertEquals(2, registry.get("camera")!!.wifiRemote.imageTransfer.totalFiles)
        repeat(2) {
            connection.downloadResults!!.send(WifiImageTransferState(WifiImageTransferStatus.Saved))
            runCurrent()
        }
        assertEquals(listOf(42L, 43L, 43L, 44L), connection.downloadedHandles)
        assertEquals(batch.toSet(), registry.get("camera")!!.wifiRemote.photoBrowser.savedDownloads)
        controller.closeAndJoin()
    }

    @Test
    fun cancellationAfterSavingAFileDoesNotStartTheNextDownload() = runTest {
        val registry = CameraSessionRegistry()
        val connection = batchConnection()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        connection.afterDownload = { controller.cancelPhotoOperation("camera") }
        controller.downloadPhotos("camera", listOf(WifiPhotoDownload(42), WifiPhotoDownload(43)))
        runCurrent()
        connection.downloadResults!!.send(WifiImageTransferState(WifiImageTransferStatus.Saved))
        runCurrent()
        val cancelled = registry.get("camera")!!.wifiRemote
        assertEquals(listOf(42L), connection.downloadedHandles)
        assertEquals(setOf(WifiPhotoDownload(42)), cancelled.photoBrowser.savedDownloads)
        assertEquals(1, cancelled.imageTransfer.completedFiles)
        assertEquals(WifiImageTransferStatus.Cancelled, cancelled.imageTransfer.status)
        controller.closeAndJoin()
    }

    @Test
    fun failedConversionStopsTheBatchWithoutLosingCompletedFiles() = runTest {
        val registry = CameraSessionRegistry()
        val connection = batchConnection()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        controller.downloadPhotos(
            "camera", listOf(
                WifiPhotoDownload(43),
                WifiPhotoDownload(42, WifiPhotoDownloadFormat.Jpeg), WifiPhotoDownload(44)
            )
        )
        runCurrent()
        connection.downloadResults!!.send(WifiImageTransferState(WifiImageTransferStatus.Saved))
        runCurrent()
        connection.downloadResults!!.send(WifiImageTransferState(WifiImageTransferStatus.ConversionFailed))
        runCurrent()
        val failed = registry.get("camera")!!.wifiRemote
        assertEquals(WifiImageTransferStatus.ConversionFailed, failed.imageTransfer.status)
        assertEquals(1, failed.imageTransfer.completedFiles)
        assertEquals(3, failed.imageTransfer.totalFiles)
        assertEquals(setOf(WifiPhotoDownload(43)), failed.photoBrowser.savedDownloads)
        assertEquals(listOf(43L, 42L), connection.downloadedHandles)
        assertFalse(failed.imageTransfer.busy)
        controller.closeAndJoin()
    }

    @Test
    fun selectionGroupsPairsAndBackClearsSelectionBeforeLeavingGallery() = runTest {
        val connection = batchConnection()
        val registry = CameraSessionRegistry()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        val model = WifiRemoteViewModel("camera", controller)
        model.togglePhotoSelection("one")
        assertTrue(model.selectedPhotoIds.isEmpty())
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        model.togglePhotoSelection("one")
        model.togglePhotoSelection("two")
        model.togglePhotoSelection("missing")
        assertEquals(setOf("one", "two"), model.selectedPhotoIds)
        model.reconcilePhotoSelection()
        assertEquals(
            setOf("one", "two"),
            model.selectedPhotoIds,
            "Recreating the screen preserves selection"
        )
        model.togglePhotoSelection("one")
        assertEquals(setOf("two"), model.selectedPhotoIds)
        // Selections survive paging or partial page changes within one catalog generation.
        val current = registry.get("camera")!!.wifiRemote
        registry.updateWifiRemote(
            "camera", current.copy(
                photoBrowser = current.photoBrowser.copy(
                    photos = current.photoBrowser.photos.filterNot { it.captureId == "two" })
            )
        )
        model.reconcilePhotoSelection()
        assertEquals(setOf("two"), model.selectedPhotoIds, "Paging away must keep selection")
        registry.updateWifiRemote("camera", current)
        // A catalog refresh bumps the browser generation and invalidates the selection.
        controller.browsePhotos("camera")
        runCurrent()
        model.reconcilePhotoSelection()
        assertTrue(model.selectedPhotoIds.isEmpty(), "Refreshed catalog invalidates selection")
        model.selectAllPhotos()
        assertEquals(setOf("one", "two", "file:45"), model.selectedPhotoIds)
        controller.downloadPhoto("camera", 42)
        runCurrent()
        model.togglePhotoSelection("one")
        assertEquals(3, model.selectedPhotoIds.size, "Selection stays stable while downloading")
        controller.cancelPhotoOperation("camera")
        runCurrent()
        model.navigateBack { error("Selection must not exit remote shooting") }
        assertTrue(model.selectedPhotoIds.isEmpty())
        assertTrue(registry.get("camera")!!.wifiRemote.photoBrowser.open)
        model.togglePhotoSelection("one")
        model.togglePhotoSelection("one")
        assertTrue(model.selectedPhotoIds.isEmpty())
        model.navigateBack { error("Gallery must return to live view") }
        runCurrent()
        assertFalse(registry.get("camera")!!.wifiRemote.photoBrowser.open)
        controller.closeAndJoin()
    }

    @Test
    fun crossPageSelectionDownloadsOneUnifiedBatch() = runTest {
        val registry = CameraSessionRegistry()
        val connection = pagedConnection().apply {
            downloadResults = Channel(Channel.UNLIMITED)
            // Unified batch (6 Saved), partial-failure retry run (Saved, Failed), and
            // the one remaining retry item.
            repeat(6) { downloadResults?.trySend(WifiImageTransferState(WifiImageTransferStatus.Saved)) }
            downloadResults?.trySend(WifiImageTransferState(WifiImageTransferStatus.Saved))
            downloadResults?.trySend(WifiImageTransferState(WifiImageTransferStatus.Failed))
            downloadResults?.trySend(WifiImageTransferState(WifiImageTransferStatus.Saved))
        }
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        val model = WifiRemoteViewModel("camera", controller)
        val page2 = pageSize * 2L // first JPG handle on the second page
        // The download request must be derived from the selection, never hand-picked.
        fun selectedBatch() = model.selectedPhotos.values
            .flatMap { capture -> capture.files.map { WifiPhotoDownload(it.handle) } }
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        // Select on page 1…
        model.togglePhotoSelection("photo:0")
        assertEquals(setOf("photo:0"), model.selectedPhotoIds)
        // …page to page 2, the selection survives…
        controller.browsePhotos("camera", SonyImageTransfer.PAGE_SIZE)
        runCurrent()
        model.reconcilePhotoSelection()
        assertEquals(setOf("photo:0"), model.selectedPhotoIds, "Paging must keep selections")
        // …select on page 2, union with the other page…
        model.togglePhotoSelection("photo:$pageSize")
        model.selectAllPhotos()
        assertEquals(
            setOf("photo:0", "photo:$pageSize", "photo:${pageSize + 1}"),
            model.selectedPhotoIds,
            "Select-all unions the current page with selections on other pages"
        )
        // …deselecting on the current page keeps other pages selected.
        model.togglePhotoSelection("photo:$pageSize")
        assertFalse("photo:$pageSize" in model.selectedPhotoIds)
        assertTrue("photo:0" in model.selectedPhotoIds)
        model.togglePhotoSelection("photo:$pageSize")
        // Return to page 1 and download the mixed JPG+ARW batch from both pages at once.
        controller.browsePhotos("camera", 0)
        runCurrent()
        model.reconcilePhotoSelection()
        assertTrue("photo:0" in model.selectedPhotoIds && "photo:$pageSize" in model.selectedPhotoIds)
        val batch = selectedBatch()
        // Insertion order: photo:0, then photo:pageSize + 1 (added by select-all), then photo:pageSize
        // (toggled off/on re-appends at the end).
        assertEquals(
            listOf(0L, 1L, page2 + 2, page2 + 3, page2, page2 + 1),
            batch.map { it.handle },
            "The download batch mirrors the selection's insertion order across pages"
        )
        controller.downloadPhotos("camera", batch)
        runCurrent()
        assertEquals(
            listOf(0L, 1L, page2 + 2, page2 + 3, page2, page2 + 1),
            connection.downloadedHandles,
            "Batch downloads files from every selected page, not just the visible one"
        )
        assertEquals(
            setOf(
                WifiPhotoDownload(0L), WifiPhotoDownload(1L),
                WifiPhotoDownload(page2), WifiPhotoDownload(page2 + 1),
                WifiPhotoDownload(page2 + 2), WifiPhotoDownload(page2 + 3)
            ),
            registry.get("camera")!!.wifiRemote.photoBrowser.savedDownloads
        )
        // A refresh starts a new generation: saved markers and selections are invalidated…
        controller.browsePhotos("camera")
        runCurrent()
        model.reconcilePhotoSelection()
        assertTrue(model.selectedPhotoIds.isEmpty(), "Refreshed catalog invalidates selection")
        assertTrue(registry.get("camera")!!.wifiRemote.photoBrowser.savedDownloads.isEmpty())
        controller.clearPhotoTransferResult("camera")
        assertEquals(
            WifiImageTransferStatus.Idle,
            registry.get("camera")!!.wifiRemote.imageTransfer.status
        )
        // …and stale page-2 handles that fell out of the refreshed catalog cannot be
        // submitted: the request is dropped instead of downloading.
        controller.downloadPhotos("camera", listOf(WifiPhotoDownload(page2 + 2), WifiPhotoDownload(page2 + 3)))
        runCurrent()
        assertEquals(
            listOf(0L, 1L, page2 + 2, page2 + 3, page2, page2 + 1),
            connection.downloadedHandles,
            "Stale handles from an older catalog generation must not download"
        )
        assertEquals(
            WifiImageTransferStatus.Idle,
            registry.get("camera")!!.wifiRemote.imageTransfer.status,
            "A dropped stale request must not even start a transfer"
        )
        // A partial failure stops the batch and marks only the finished files as saved…
        controller.browsePhotos("camera", SonyImageTransfer.PAGE_SIZE)
        runCurrent()
        model.reconcilePhotoSelection()
        model.clearPhotoSelection()
        model.togglePhotoSelection("photo:$pageSize")
        val retryBatch = selectedBatch()
        assertEquals(listOf(page2, page2 + 1), retryBatch.map { it.handle })
        controller.downloadPhotos("camera", retryBatch)
        runCurrent()
        assertEquals(
            listOf(0L, 1L, page2 + 2, page2 + 3, page2, page2 + 1, page2, page2 + 1),
            connection.downloadedHandles,
            "A failing download is attempted but not recorded as saved"
        )
        assertEquals(
            WifiImageTransferStatus.Failed,
            registry.get("camera")!!.wifiRemote.imageTransfer.status
        )
        assertEquals(
            setOf(WifiPhotoDownload(page2)),
            registry.get("camera")!!.wifiRemote.photoBrowser.savedDownloads,
            "Only the item that finished before the failure is marked saved"
        )
        // …and a retry only submits what is still unsaved.
        controller.downloadPhotos("camera", retryBatch)
        runCurrent()
        assertEquals(
            listOf(0L, 1L, page2 + 2, page2 + 3, page2, page2 + 1, page2, page2 + 1, page2 + 1),
            connection.downloadedHandles,
            "Retrying the batch re-downloads only the failed item"
        )
        assertEquals(
            setOf(WifiPhotoDownload(page2), WifiPhotoDownload(page2 + 1)),
            registry.get("camera")!!.wifiRemote.photoBrowser.savedDownloads
        )
        controller.closeAndJoin()
    }

    @Test
    fun disconnectAbortsDownloadBeforeLeavingBrowseModeAndTurningOffWifi() = runTest {
        val connection = Connection().apply { shutdownEnabled = true }
        val controller = WifiRemoteController(
            backgroundScope,
            CameraSessionRegistry(),
            { _, _ -> connection },
            {},
            {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        controller.downloadPhoto("camera", 42L)
        runCurrent()
        controller.closeAndJoin()
        assertEquals(
            listOf("download", "browse-close", "wifi-off", "connection"),
            connection.cleanup.takeLast(4)
        )
        assertTrue(controller.thumbnails.value.isEmpty())
    }

    @Test
    fun cancelledDownloadKeepsConnectionAndFailedModeExitDoesNotRestartPreview() = runTest {
        val registry = CameraSessionRegistry()
        val connection = Connection()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        controller.downloadPhoto("camera", 42L)
        runCurrent()
        controller.cancelPhotoOperation("camera")
        runCurrent()
        assertEquals(
            WifiImageTransferStatus.Cancelled,
            registry.get("camera")?.wifiRemote?.imageTransfer?.status
        )
        assertEquals(WifiRemotePhase.Ready, registry.get("camera")?.wifiRemote?.phase)
        connection.failBrowserExit = true
        controller.leavePhotoBrowser("camera")
        runCurrent()
        assertTrue(registry.get("camera")!!.wifiRemote.photoBrowser.open)
        assertTrue(registry.get("camera")!!.wifiRemote.photoBrowser.failed)
        controller.capture("camera")
        assertEquals(0, connection.captures)
        connection.failBrowserExit = false
        controller.closeAndJoin()
        assertEquals(1, connection.cleanup.count { it == "preview" })
    }

    @Test
    fun browseOnlyAutoOpensBrowserOnceAndBackDisconnects() = runTest {
        val registry = CameraSessionRegistry()
        val connection = pagedConnection()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        val model = WifiRemoteViewModel("camera", controller).apply { browseOnly = true }
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        assertEquals(WifiRemotePhase.Ready, registry.get("camera")?.wifiRemote?.phase)
        // The screen effect auto-enters the browser exactly once per connection.
        model.maybeAutoEnterBrowser()
        runCurrent()
        assertTrue(registry.get("camera")!!.wifiRemote.photoBrowser.open)
        // Leaving the browser deliberately must not re-open it — that was the
        // browse-only trap door with no way out.
        controller.leavePhotoBrowser("camera")
        runCurrent()
        model.reconcilePhotoSelection()
        model.maybeAutoEnterBrowser()
        runCurrent()
        assertFalse(registry.get("camera")!!.wifiRemote.photoBrowser.open)
        // Back navigation with the browser open disconnects the session instead of
        // stranding the user on a live-view-less Ready screen.
        controller.browsePhotos("camera")
        runCurrent()
        model.maybeAutoEnterBrowser()
        try {
            model.navigateBack { error("Browse-only back must disconnect, not exit the screen") }
            // Back itself must have started teardown immediately — a Closing phase
            // here proves it, without any closeAndJoin()/disconnect() call that
            // would disconnect on its own and mask a broken Back path.
            assertEquals(
                WifiRemotePhase.Closing,
                registry.get("camera")?.wifiRemote?.phase,
                "Back in browse-only must trigger disconnect right away"
            )
            // Drive only the scheduler: the runSession finally block must finish the
            // cleanup (prepareToDisconnect → close → Idle) and the registry entry
            // must vanish without any further disconnect call.
            runCurrent()
            assertNull(
                registry.get("camera"),
                "Back in browse-only disconnects and fully cleans up the session"
            )
        } finally {
            controller.closeAndJoin()
        }
    }

    @Test
    fun browseOnlyReconnectAfterCleanupAutoEntersBrowserAgain() = runTest {
        val registry = CameraSessionRegistry()
        val connection = pagedConnection()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        val model = WifiRemoteViewModel("camera", controller).apply { browseOnly = true }
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        model.maybeAutoEnterBrowser()
        runCurrent()
        assertTrue(registry.get("camera")!!.wifiRemote.photoBrowser.open)
        controller.disconnect("camera")
        controller.closeAndJoin()
        // After cleanup the registry entry is gone; the Compose effect only ever
        // observes this null state (same effect keys on reconnect — no transition
        // state in between). maybeAutoEnterBrowser called at null must reset the
        // once-flag, or the next connection never auto-enters.
        assertNull(registry.get("camera"))
        model.maybeAutoEnterBrowser()
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        assertEquals(WifiRemotePhase.Ready, registry.get("camera")?.wifiRemote?.phase)
        model.maybeAutoEnterBrowser()
        runCurrent()
        assertTrue(
            registry.get("camera")!!.wifiRemote.photoBrowser.open,
            "A fresh connection after null-cleanup must auto-enter the browser again"
        )
        // And Back on the reconnected session still disconnects.
        try {
            model.navigateBack { error("Browse-only back must disconnect, not exit the screen") }
            assertEquals(WifiRemotePhase.Closing, registry.get("camera")?.wifiRemote?.phase)
            runCurrent()
            assertNull(registry.get("camera"))
        } finally {
            controller.closeAndJoin()
        }
    }

    @Test
    fun nonBrowseOnlyBackLeavesBrowserWithoutDisconnecting() = runTest {
        val registry = CameraSessionRegistry()
        val connection = pagedConnection()
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        val model = WifiRemoteViewModel("camera", controller).apply { browseOnly = false }
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        controller.browsePhotos("camera")
        runCurrent()
        assertTrue(registry.get("camera")!!.wifiRemote.photoBrowser.open)
        // Outside browse-only, Back leaves the photo browser and keeps the
        // remote-shooting session alive; the screen is not exited either.
        model.navigateBack { error("Back must leave the browser, not exit the screen") }
        runCurrent()
        assertFalse(registry.get("camera")!!.wifiRemote.photoBrowser.open)
        assertEquals(WifiRemotePhase.Ready, registry.get("camera")?.wifiRemote?.phase)
        controller.closeAndJoin()
    }

    @Test
    fun connectionWithoutLiveViewStreamReportsPreviewUnavailable() = runTest {
        val registry = CameraSessionRegistry()
        val connection = Connection().apply { hasLiveViewStream = false }
        val controller =
            WifiRemoteController(backgroundScope, registry, { _, _ -> connection }, {}, {})
        controller.connect("camera", "127.0.0.1")
        runCurrent()
        assertEquals(WifiRemotePhase.Ready, registry.get("camera")?.wifiRemote?.phase)
        // A connection that has no live-view stream (browse-only) must not collect
        // the immediately-complete images flow and must not report a preview
        // failure — the stream was never supposed to run.
        assertEquals(0, connection.previewStarts)
        assertEquals(WifiPreviewStatus.Unavailable, registry.get("camera")!!.wifiRemote.preview)
        assertFalse(registry.get("camera")!!.wifiRemote.hasLiveView)
        controller.closeAndJoin()
    }

}
