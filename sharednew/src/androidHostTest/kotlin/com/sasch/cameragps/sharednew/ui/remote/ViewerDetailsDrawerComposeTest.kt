package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import com.sasch.cameragps.sharednew.remote.wifi.ExifTestData
import com.sasch.cameragps.sharednew.remote.wifi.PhotoMetadataState
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraCapture
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraPhoto
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferState
import com.sasch.cameragps.sharednew.remote.wifi.WifiLoggingTest
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteConnection
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteController
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy
import com.sasch.cameragps.sharednew.remote.wifi.WifiDownloadFormatSetting
import com.sasch.cameragps.sharednew.remote.wifi.cameraPhotoPage
import com.sasch.cameragps.sharednew.remote.wifi.groupCameraPhotos
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import com.sasch.cameragps.sharednew.ui.theme.WindowClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Collections

/**
 * The portrait details drawer in real Compose on the JVM (Robolectric):
 *  - opening / closing it does not recompose the photo, info strip or filmstrip slots of the
 *    layout below (composition counters inside the slots, [ViewerBody] driven directly);
 *  - the full [WifiPhotoViewer] reads the shown photo's EXIF header once after the debounce
 *    without the drawer, and opening / closing the drawer issues no further camera read and does
 *    not recompose the viewer's page scope, its layout, the real pager, its page or the filmstrip
 *    ([LocalViewerCompositionProbe]). Checked by mutation: reading the drawer flag in the page
 *    scope makes this test fail.
 * Frame timing and the look of the sheet are device checks (docs/todo.md).
 */
@RunWith(RobolectricTestRunner::class)
// A phone in portrait (class A): Robolectric's default 320 × 470 dp window would be class C.
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ViewerDetailsDrawerComposeTest : WifiLoggingTest() {
    @get:Rule
    val compose = createComposeRule()

    private val paneTitle = "Photo details"
    private fun pane() = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, paneTitle)

    private val photos = listOf(
        WifiCameraPhoto(42, "A.JPG", 5_000_000, "image/jpeg", "20260101T120000", captureId = "a", width = 6000, height = 4000),
        WifiCameraPhoto(43, "A.ARW", 25_000_000, "image/x-sony-arw", "20260101T120000", captureId = "a"),
        WifiCameraPhoto(44, "B.JPG", 4_000_000, "image/jpeg", "20260101T120100", captureId = "b"),
    )

    // ---- recomposition scope -----------------------------------------------------------------------

    @Test
    fun togglingTheDrawerDoesNotRecomposeThePhotoStripOrFilmstrip() {
        var photoCompositions = 0
        var stripCompositions = 0
        var filmCompositions = 0
        val drawer = ViewerDrawerState(initiallyOpen = false)
        val capture = WifiCameraCapture("a", photos.take(2))
        val parts = ViewerParts(
            title = "A.JPG", capturedAt = null, position = "1 / 2", selectedCount = 0,
            info = viewerInfoStrip(capture, WifiFormatPolicy.Original),
            capture = capture, image = null as ImageBitmap?,
            metadata = MutableStateFlow<PhotoMetadataState>(PhotoMetadataState.Idle),
            download = null, transfer = WifiImageTransferState(), selected = false, selectEnabled = true,
            onBack = {}, onToggleSelected = {}, onDownload = {}, onCancel = {},
            onOpenDetails = { drawer.show() },
        )
        val stripFocus = FocusRequester()
        val photo: @Composable (Modifier) -> Unit = { modifier ->
            SideEffect { photoCompositions++ }
            Box(modifier.fillMaxSize())
        }
        val strip: @Composable (Modifier) -> Unit = { modifier ->
            SideEffect { stripCompositions++ }
            Box(modifier)
        }
        val film: @Composable (Modifier) -> Unit = { modifier ->
            SideEffect { filmCompositions++ }
            Box(modifier)
        }
        compose.setContent {
            CameraGpsTheme(darkTheme = true) {
                GlassBackground {
                    ViewerBody(WindowClass.Portrait, parts, drawer, stripFocus, photo, strip, film)
                }
            }
        }
        compose.waitForIdle()
        val before = Triple(photoCompositions, stripCompositions, filmCompositions)
        assertTrue("slots composed once at least: $before", before.first > 0 && before.second > 0 && before.third > 0)
        compose.onAllNodes(pane()).assertCountEquals(0)

        compose.runOnIdle { drawer.show() }
        compose.waitForIdle()
        compose.onNode(pane()).assertExists()
        assertEquals("opening", before, Triple(photoCompositions, stripCompositions, filmCompositions))

        compose.runOnIdle { drawer.hide() }
        compose.waitForIdle()
        compose.onAllNodes(pane()).assertCountEquals(0)
        assertEquals("closing", before, Triple(photoCompositions, stripCompositions, filmCompositions))
    }

    // ---- extended read independent of the drawer -----------------------------------------------------

    private inner class Connection : WifiRemoteConnection {
        override val cameraName = "Test camera"
        override val canCapture = false
        override val canTransferImages = true
        override val hasLiveViewStream = false
        val heads: MutableList<Long> = Collections.synchronizedList(mutableListOf())
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    /** Main-thread composition counts of the real viewer's slots ([LocalViewerCompositionProbe]). */
    private val compositions = mutableMapOf<String, Int>()

    @Test
    fun theShownPhotoIsReadOnceWithoutTheDrawerAndTogglingItReadsNothingMore() {
        val connection = Connection()
        val controller = WifiRemoteController(scope, CameraSessionRegistry(), { _, _ -> connection }, {}, {})
        val model = WifiRemoteViewModel("camera", controller)
        controller.connect("camera", "127.0.0.1")
        compose.waitUntil(5_000) { controller.sessions.value["CAMERA"]?.wifiRemote?.phase == WifiRemotePhase.Ready }
        controller.browsePhotos("camera")
        compose.waitUntil(5_000) {
            controller.sessions.value["CAMERA"]?.wifiRemote?.photoBrowser?.photos?.isNotEmpty() == true
        }
        compose.runOnIdle { model.previewPhoto("a") }
        compose.setContent {
            val sessions by controller.sessions.collectAsState()
            val state = sessions["CAMERA"]?.wifiRemote ?: WifiRemoteState()
            CameraGpsTheme {
                GlassBackground(contentIsBackdropSource = false) {
                    CompositionLocalProvider(LocalViewerCompositionProbe provides { slot -> compositions.merge(slot, 1, Int::plus) }) {
                        // JPG + RAW: the strip reads "JPG + RAW · size" (ask each time would list each format).
                        if (model.viewedPhotoIndex != null) {
                            WifiPhotoViewer(state, model, onDownload = {}, downloadFormat = WifiDownloadFormatSetting.JpgRaw)
                        }
                    }
                }
            }
        }
        // Staying on the photo: one header read after the debounce, drawer closed.
        compose.waitUntil(5_000) { connection.heads.isNotEmpty() }
        compose.waitUntil(5_000) {
            (controller.metadata.value as? PhotoMetadataState.Ready)?.metadata?.extendedRead == true
        }
        compose.onAllNodes(pane()).assertCountEquals(0)
        assertEquals(listOf(42L), connection.heads.toList())
        compose.waitForIdle()
        val settled = compose.runOnIdle { compositions.toMap() }
        assertTrue("the real pager, its page and the filmstrip were counted: $settled",
            listOf("viewer", "layout", "pager", "page", "filmstrip").all { (settled[it] ?: 0) > 0 })

        // Open the drawer from the info strip: the details show the cached data, no new read.
        compose.onNodeWithText("JPG + RAW", substring = true).performClick()
        compose.waitForIdle()
        compose.onNode(pane()).assertExists()
        Thread.sleep(600)
        compose.waitForIdle()
        assertEquals("opening reads nothing more", listOf(42L), connection.heads.toList())
        assertEquals("opening recomposes neither the page scope, the layout, the pager nor the filmstrip", settled, compose.runOnIdle { compositions.toMap() })

        compose.onNodeWithContentDescription("Close photo details").performClick()
        compose.waitForIdle()
        Thread.sleep(600)
        compose.waitForIdle()
        compose.onAllNodes(pane()).assertCountEquals(0)
        assertEquals("closing reads nothing more", listOf(42L), connection.heads.toList())
        assertEquals("closing recomposes neither the page scope, the layout, the pager nor the filmstrip", settled, compose.runOnIdle { compositions.toMap() })
    }
}
