package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.sasch.cameragps.sharednew.remote.wifi.PhotoMetadataState
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraCapture
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraPhoto
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferState
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoDownload
import com.sasch.cameragps.sharednew.remote.wifi.WifiTransferFailureReason
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import com.sasch.cameragps.sharednew.ui.theme.WindowClass
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The viewer's bottom action row (download slot + check box) keeps the same bounds in every
 * download phase and transfer result, in portrait and in the wide side panel, at normal and large
 * font scale — so starting, finishing, failing or cancelling a download never moves the photo or the
 * filmstrip. A finished transfer's result is an overlay snackbar, not a row of its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ViewerDownloadSlotComposeTest {
    @get:Rule
    val compose = createComposeRule()

    private val jpg = WifiCameraPhoto(1, "A.JPG", 5_000_000, "image/jpeg", "", captureId = "a")
    private val raw = WifiCameraPhoto(2, "A.ARW", 25_000_000, "image/x-sony-arw", "", captureId = "a")
    private val pair = WifiCameraCapture("a", listOf(jpg, raw))
    private val rawOnly = WifiCameraCapture("a", listOf(raw))
    private val jpgOnly = WifiCameraCapture("a", listOf(jpg))
    private val huge = WifiCameraCapture("a", listOf(jpg.copy(size = 600L * 1024 * 1024)))

    private class Case(
        val name: String,
        val capture: WifiCameraCapture,
        val policy: WifiFormatPolicy?,
        val transfer: WifiImageTransferState = WifiImageTransferState(),
        val saved: Set<WifiPhotoDownload> = emptySet(),
        val browserLoading: Boolean = false,
    )

    private val downloading = WifiImageTransferState(
        WifiImageTransferStatus.Downloading, filename = "A.ARW",
        bytesReceived = 12_345_678, totalBytes = 25_000_000, completedFiles = 1, totalFiles = 2,
    )

    private val cases = listOf(
        Case("ready", pair, WifiFormatPolicy.JpegOnly),
        Case("ask", pair, null),
        Case("downloading", pair, WifiFormatPolicy.Original, downloading),
        Case("converting", pair, WifiFormatPolicy.JpegOnly,
            WifiImageTransferState(WifiImageTransferStatus.Converting, filename = "A.ARW")),
        Case("saved", pair, WifiFormatPolicy.Original, saved = setOf(WifiPhotoDownload(1), WifiPhotoDownload(2))),
        Case("noJpeg", rawOnly, WifiFormatPolicy.JpegOnly),
        Case("noRaw", jpgOnly, WifiFormatPolicy.RawOnly),
        Case("otherTransfer", pair, WifiFormatPolicy.JpegOnly, downloading.copy(filename = "Z.JPG")),
        Case("tooLarge", huge, WifiFormatPolicy.Original),
        Case("waiting", pair, WifiFormatPolicy.JpegOnly, browserLoading = true),
        Case("failed", pair, WifiFormatPolicy.JpegOnly,
            WifiImageTransferState(WifiImageTransferStatus.Failed, failureReason = WifiTransferFailureReason.Timeout)),
        Case("failedGeneric", pair, WifiFormatPolicy.JpegOnly, WifiImageTransferState(WifiImageTransferStatus.Failed)),
        Case("conversionFailed", pair, WifiFormatPolicy.JpegOnly,
            WifiImageTransferState(WifiImageTransferStatus.ConversionFailed)),
        Case("cancelled", pair, WifiFormatPolicy.JpegOnly, WifiImageTransferState(WifiImageTransferStatus.Cancelled)),
        Case("permissionDenied", pair, WifiFormatPolicy.JpegOnly,
            WifiImageTransferState(WifiImageTransferStatus.PermissionDenied)),
    )

    private fun parts(case: Case) = ViewerParts(
        title = "A.JPG", capturedAt = null, position = "1 / 1", selectedCount = 0,
        info = viewerInfoStrip(case.capture, case.policy),
        capture = case.capture, image = null as ImageBitmap?,
        metadata = MutableStateFlow<PhotoMetadataState>(PhotoMetadataState.Idle),
        download = viewerDownloadState(case.capture, case.policy, case.saved, case.transfer, case.browserLoading),
        transfer = case.transfer, selected = false, selectEnabled = true,
        onBack = {}, onToggleSelected = {}, onDownload = {}, onCancel = {},
        onOpenDetails = null,
    )

    private var current by mutableStateOf(cases.first())

    private fun show(windowClass: WindowClass, fontScale: Float) {
        val photo: @Composable (Modifier) -> Unit = { modifier -> Box(modifier.fillMaxSize()) }
        val slot: @Composable (Modifier) -> Unit = { modifier -> Box(modifier) }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                CameraGpsTheme(darkTheme = true) {
                    GlassBackground {
                        ViewerBody(windowClass, parts(current), ViewerDrawerState(initiallyOpen = false), FocusRequester(), photo, slot, slot)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun rowBounds(): Rect = compose.onNodeWithTag(VIEWER_ACTION_ROW_TAG).fetchSemanticsNode().boundsInRoot

    private fun assertRowNeverMoves(windowClass: WindowClass, fontScale: Float) {
        current = cases.first()
        show(windowClass, fontScale)
        val expected = rowBounds()
        cases.forEach { case ->
            current = case
            compose.waitForIdle()
            assertEquals("${case.name} ($windowClass, font ×$fontScale)", expected, rowBounds())
        }
    }

    @Test
    fun portraitRowKeepsItsBoundsInEveryPhase() = assertRowNeverMoves(WindowClass.Portrait, 1f)

    @Test
    fun portraitRowKeepsItsBoundsAtLargeFontScale() = assertRowNeverMoves(WindowClass.Portrait, 2f)

    @Test
    fun widePanelRowKeepsItsBoundsInEveryPhase() = assertRowNeverMoves(WindowClass.NearSquare, 1f)

    @Test
    fun widePanelRowKeepsItsBoundsAtLargeFontScale() = assertRowNeverMoves(WindowClass.Landscape, 1.5f)

    @Test
    fun rowIsTheTouchTargetHeight() {
        show(WindowClass.Portrait, 1f)
        val height = with(compose.density) { rowBounds().height.toDp() }
        assertEquals(48f, height.value, 0.5f)
    }

    @Test
    fun aFailureAfterADownloadIsAnOverlayMessage() {
        current = cases.first { it.name == "downloading" }
        show(WindowClass.Portrait, 1f)
        val before = rowBounds()
        compose.onNodeWithText("Downloading 49%", substring = true).assertExists()
        current = cases.first { it.name == "failedGeneric" }
        compose.waitForIdle()
        compose.onNodeWithText("Download failed. Any incomplete file was removed. You can retry the download.").assertExists()
        assertEquals(before, rowBounds())
    }
}
