package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sasch.cameragps.sharednew.remote.wifi.PhotoMetadataState
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraCapture
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraPhoto
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferState
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoDownload
import com.sasch.cameragps.sharednew.remote.wifi.batchFormatChoices
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import com.sasch.cameragps.sharednew.ui.theme.WindowClass
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "Ask each time" on the JVM (Robolectric + Compose): the viewer's Download button opens the in-page
 * format chooser only when the photo has more than one format, the pick downloads exactly that
 * choice's files, and Cancel downloads nothing; the batch chooser rows show what each format fetches
 * and skips and disable a format nobody in the selection has. Look and feel are device checks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WifiFormatChooserComposeTest {
    @get:Rule
    val compose = createComposeRule()

    private val jpg = WifiCameraPhoto(1, "A.JPG", 5_000_000, "image/jpeg", "", captureId = "a")
    private val raw = WifiCameraPhoto(2, "A.ARW", 25_000_000, "image/x-sony-arw", "", captureId = "a")
    private val pair = WifiCameraCapture("a", listOf(jpg, raw))
    private val jpgOnly = WifiCameraCapture("j", listOf(jpg.copy(handle = 3, captureId = "j")))

    private fun showViewer(capture: WifiCameraCapture, downloads: MutableList<List<WifiPhotoDownload>>) {
        val parts = ViewerParts(
            title = "A.JPG", capturedAt = null, position = "1 / 1", selectedCount = 0,
            info = viewerInfoStrip(capture, null),
            capture = capture, image = null as ImageBitmap?,
            metadata = MutableStateFlow<PhotoMetadataState>(PhotoMetadataState.Idle),
            download = viewerDownloadState(capture, null, emptySet(), WifiImageTransferState(), browserLoading = false),
            transfer = WifiImageTransferState(), selected = false, selectEnabled = true,
            onBack = {}, onToggleSelected = {}, onDownload = { downloads += it }, onCancel = {},
            onOpenDetails = null,
        )
        // Only the photo fills its area; the strip / filmstrip slots keep their own size so the
        // download row below stays on screen.
        val photo: @Composable (Modifier) -> Unit = { modifier -> Box(modifier.fillMaxSize()) }
        val slot: @Composable (Modifier) -> Unit = { modifier -> Box(modifier) }
        compose.setContent {
            CameraGpsTheme(darkTheme = true) {
                GlassBackground {
                    ViewerBody(WindowClass.Portrait, parts, ViewerDrawerState(initiallyOpen = false), FocusRequester(), photo, slot, slot)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aPhotoWithJpgAndRawAsksAndDownloadsThePickedFormatOnly() {
        val downloads = mutableListOf<List<WifiPhotoDownload>>()
        showViewer(pair, downloads)
        compose.onNodeWithText("Download").performClick()
        compose.waitForIdle()
        assertTrue("nothing downloaded before the pick", downloads.isEmpty())
        compose.onNodeWithText("Download format").assertExists()
        // The photo's formats, JPG preselected.
        compose.onNodeWithText("JPG").assertIsSelected()
        compose.onNodeWithText("JPG + RAW").assertExists()
        compose.onNodeWithText("RAW").performClick()
        compose.onNodeWithText("RAW").assertIsSelected()
        compose.onAllNodesWithText("Download").onLast().performClick()
        compose.waitForIdle()
        assertEquals(listOf(listOf(WifiPhotoDownload(2))), downloads)
        compose.onAllNodesWithText("Download format").assertCountEquals0()
    }

    @Test
    fun cancelDownloadsNothing() {
        val downloads = mutableListOf<List<WifiPhotoDownload>>()
        showViewer(pair, downloads)
        compose.onNodeWithText("Download").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertTrue(downloads.isEmpty())
        compose.onAllNodesWithText("Download format").assertCountEquals0()
    }

    @Test
    fun aPhotoWithOneFormatDownloadsWithoutAsking() {
        val downloads = mutableListOf<List<WifiPhotoDownload>>()
        showViewer(jpgOnly, downloads)
        compose.onNodeWithText("Download").performClick()
        compose.waitForIdle()
        assertEquals(listOf(listOf(WifiPhotoDownload(3))), downloads)
        compose.onAllNodesWithText("Download format").assertCountEquals0()
    }

    @Test
    fun batchRowsShowWhatEachFormatFetchesAndDisableAMissingOne() {
        val heifOnly = WifiCameraCapture("h", listOf(WifiCameraPhoto(5, "H.HIF", 1_000_000, "image/heif", "", captureId = "h")))
        var confirmed: WifiFormatPolicy? = null
        var open by mutableStateOf(true)
        compose.setContent {
            CameraGpsTheme {
                GlassBackground {
                    if (open) {
                        WifiFormatChooserDialog(
                            options = batchChooserOptions(batchFormatChoices(listOf(jpgOnly, heifOnly), emptySet())),
                            onConfirm = { confirmed = it; open = false },
                            onDismiss = { open = false },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("JPG").assertIsSelected().assertIsEnabled()
        compose.onNodeWithText("1 files · 4.8 MB · 1 will be skipped (1 without JPG)").assertExists()
        compose.onNodeWithText("RAW").assertIsNotEnabled()
        compose.onNodeWithText("JPG + RAW").performClick()
        compose.onNodeWithText("Download").performClick()
        compose.waitForIdle()
        assertEquals(WifiFormatPolicy.Original, confirmed)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals0() {
        assertEquals(0, fetchSemanticsNodes().size)
    }
}
