package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraPhoto
import com.sasch.cameragps.sharednew.remote.wifi.WifiDownloadFormatSetting
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferState
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoBrowserState
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoDownload
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteController
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WifiPhotoBrowserComposeTest {
    @get:Rule
    val compose = createComposeRule()

    private val cameraId = "AA:BB:CC:DD:EE:FF"
    private val registry = CameraSessionRegistry()
    private val controller = WifiRemoteController(
        CoroutineScope(Dispatchers.Main), registry, { _, _ -> error("No camera connection expected") }, {}, {},
    )
    private val viewModel = WifiRemoteViewModel(cameraId, controller)
    private val raw = WifiCameraPhoto(1, "A.ARW", 25_000_000, "image/x-sony-arw", "", captureId = "a")
    private val liveRegion = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    private fun showBrowser(
        browser: WifiPhotoBrowserState,
        options: WifiGridOptions = WifiGridOptions(),
        format: WifiDownloadFormatSetting = WifiDownloadFormatSetting.Default,
    ) {
        registry.upsert(cameraId) {
            it.copy(
                wifiRemote = WifiRemoteState(
                    phase = WifiRemotePhase.Ready,
                    photoBrowser = browser.copy(open = true, dateKey = "2026-11-27"),
                ),
            )
        }
        var gridOptions by mutableStateOf(options)
        compose.setContent {
            val sessions by controller.sessions.collectAsState()
            CameraGpsTheme {
                GlassBackground(contentIsBackdropSource = false) {
                    WifiPhotoBrowser(
                        state = sessions.getValue(cameraId).wifiRemote,
                        viewModel = viewModel,
                        onDownload = { error("No camera download expected") },
                        downloadFormat = format,
                        gridOptions = gridOptions,
                        onGridOptionsChange = { gridOptions = it },
                    )
                }
            }
        }
    }

    @Test
    fun showAllTurnsOffOnlyUnsavedAndRestoresTheSavedCapture() {
        showBrowser(
            WifiPhotoBrowserState(photos = listOf(raw), savedDownloads = setOf(WifiPhotoDownload(raw.handle))),
            options = WifiGridOptions(onlyUnsaved = true),
        )

        compose.onNodeWithContentDescription(raw.filename.substringBeforeLast('.')).assertDoesNotExist()
        compose.onNodeWithText("Show all photos").performClick()
        compose.onNodeWithContentDescription(raw.filename.substringBeforeLast('.')).assertExists()
        compose.onNodeWithText("Show all photos").assertDoesNotExist()
    }

    @Test
    fun resultAndSkipHintLeaveTheLastRowReachableAndDismissPreservesSelection() {
        val photos = (1L..36).map { handle ->
            raw.copy(handle = handle, filename = "PHOTO_$handle.ARW", captureId = "capture-$handle")
        }
        showBrowser(WifiPhotoBrowserState(photos = photos), format = WifiDownloadFormatSetting.Jpg)
        compose.runOnIdle { viewModel.togglePhotoSelection("capture-1") }
        compose.onNodeWithText("Nothing to download").performClick()

        val summary = compose.onNodeWithText("Downloaded 0, skipped 1 (1 without JPG)")
        summary.assert(liveRegion)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(photos.lastIndex)
        val lastTile = compose.onNodeWithContentDescription(photos.last().filename.substringBeforeLast('.'))
        assertTrue(lastTile.fetchSemanticsNode().boundsInRoot.bottom < summary.fetchSemanticsNode().boundsInRoot.top)

        compose.onNodeWithContentDescription("Dismiss").performClick()
        summary.assertDoesNotExist()
        assertEquals(setOf("capture-1"), viewModel.selectedPhotoIds)
        compose.onNodeWithText("Nothing to download").assertExists()
    }

    @Test
    fun blockedBackShowsAnInPageAnnouncementWithoutMovingTheGrid() {
        showBrowser(WifiPhotoBrowserState(photos = listOf(raw)))
        val tile = compose.onNodeWithContentDescription(raw.filename.substringBeforeLast('.'))
        val before = tile.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            registry.updateIfPresent(cameraId) {
                it.copy(wifiRemote = it.wifiRemote.copy(
                    imageTransfer = WifiImageTransferState(WifiImageTransferStatus.Downloading, filename = raw.filename),
                ))
            }
            viewModel.navigateBack { error("Busy browser closed") }
        }
        compose.onNodeWithText("Wait for the current operation", substring = true).assert(liveRegion)
        assertEquals(before, tile.fetchSemanticsNode().boundsInRoot)
    }
}
