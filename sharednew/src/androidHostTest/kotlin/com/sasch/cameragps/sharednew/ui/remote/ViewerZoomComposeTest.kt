package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.remote.wifi.PhotoMetadataState
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraCapture
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraPhoto
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferState
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import com.sasch.cameragps.sharednew.ui.theme.WindowClass
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ViewerZoomComposeTest {
    @get:Rule val compose = createComposeRule()

    private val capture = WifiCameraCapture("a", listOf(
        WifiCameraPhoto(42, "A.JPG", 5_000_000, "image/jpeg", "20260101T120000", captureId = "a"),
    ))

    @Test
    fun zoomedSwipeFromRealInfoStripDoesNotOpenButFitSwipeDoes() {
        val zoom = ViewerZoomState()
        val drawer = ViewerDrawerState(false)
        val info = viewerInfoStrip(capture, WifiFormatPolicy.Original)
        val parts = ViewerParts(
            title = "A.JPG", capturedAt = null, position = null, selectedCount = 0,
            info = info, capture = capture, image = null,
            metadata = MutableStateFlow<PhotoMetadataState>(PhotoMetadataState.Idle),
            download = null, transfer = WifiImageTransferState(), selected = false, selectEnabled = true,
            onBack = {}, onToggleSelected = {}, onDownload = {}, onCancel = {}, onOpenDetails = { drawer.show() },
        )
        val bitmap = ImageBitmap(400, 400)
        compose.setContent {
            val pager = rememberPagerState { 1 }
            CameraGpsTheme(darkTheme = true) {
                GlassBackground {
                    ViewerBody(
                        WindowClass.Portrait, parts, drawer, FocusRequester(),
                        photo = { modifier ->
                            ViewerPager(pager, listOf(capture), WifiRemoteState(), { bitmap }, { false }, true, zoom, modifier.testTag("photo"))
                        },
                        strip = { modifier -> ViewerInfoStripBar(info, parts.onOpenDetails, modifier.testTag("strip")) },
                        film = { modifier -> Box(modifier) },
                        zoomState = zoom,
                    )
                }
            }
        }
        compose.onNodeWithTag("photo").performTouchInput { doubleClick() }
        compose.runOnIdle { assertTrue(zoom.value.scale > 1f) }
        compose.onNodeWithTag("strip").performTouchInput { swipeUp(startY = centerY, endY = centerY - 150f) }
        compose.runOnIdle { assertFalse("zoomed strip swipe opened drawer", drawer.open) }
        compose.onNodeWithTag("photo").performTouchInput { doubleClick() }
        compose.runOnIdle { assertEquals(1f, zoom.value.scale) }
        compose.onNodeWithTag("strip").performTouchInput { swipeUp(startY = centerY, endY = centerY - 150f) }
        compose.runOnIdle { assertTrue("fit strip swipe did not open drawer", drawer.open) }
    }

    @Test
    fun previewAspectChangeAndViewportResizeReclampLivePagerOffset() {
        val zoom = ViewerZoomState()
        var bitmap by mutableStateOf(ImageBitmap(400, 400))
        var height by mutableStateOf(300.dp)
        var viewport = Size.Zero
        compose.setContent {
            val pager = rememberPagerState { 1 }
            CameraGpsTheme {
                ViewerPager(
                    pager, listOf(capture), WifiRemoteState(), { bitmap }, { false }, true, zoom,
                    Modifier.size(300.dp, height).onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) },
                )
            }
        }
        compose.runOnIdle {
            zoom.value = ViewerZoom(2.5f, Offset(viewport.width * .6f, viewport.height * .6f))
            bitmap = ImageBitmap(400, 100)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("wide preview cannot pan vertically", 0f, zoom.value.offset.y, .01f)
            bitmap = ImageBitmap(400, 400)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            zoom.value = ViewerZoom(2.5f, Offset(viewport.width * .6f, viewport.height * .6f))
            height = 100.dp
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val actual = zoom.value
            val expected = viewerZoomTransform(ViewerZoom(2.5f, Offset(viewport.width * .6f, viewport.width * .6f)), 1f, Offset.Zero, Offset.Zero, viewport, Size(400f, 400f))
            assertEquals(expected.scale, actual.scale, .01f)
            assertEquals(expected.offset.x, actual.offset.x, .01f)
            assertEquals(expected.offset.y, actual.offset.y, .01f)
            assertNotEquals("resize must actually clamp the previous offset", viewport.width * .6f, actual.offset.y, .01f)
        }
    }
}
