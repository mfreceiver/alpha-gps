package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import com.sasch.cameragps.sharednew.remote.wifi.WifiCaptureStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiLiveViewFrame
import com.sasch.cameragps.sharednew.remote.wifi.WifiPreviewStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteController
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.WifiSessionPurpose
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemoteLiveViewComposeTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = mutableStateOf(
        WifiRemoteState(
            phase = WifiRemotePhase.Ready,
            purpose = WifiSessionPurpose.RemoteControl,
            cameraName = "Camera",
            canCaptureStill = true,
            supportsLiveView = true,
        ),
    )
    private val frame = mutableStateOf<WifiLiveViewFrame?>(null)
    private var shots = 0
    private var retries = 0

    private fun show(fontScale: Float = 1f) = compose.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            CameraGpsTheme(darkTheme = false) {
                GlassBackground(contentIsBackdropSource = false) {
                    RemoteLiveView(
                        state.value, frame, hapticsEnabled = false, onBack = {},
                        onShutter = { shots++ }, onRetryLiveView = { retries++ },
                    )
                }
            }
        }
    }

    @Test
    fun portraitShutterStaysInPlaceAcrossCaptureStates() = checkShutterSlot(fontScale = 1f)

    @Test
    @Config(qualifiers = "w812dp-h380dp")
    fun landscapeShutterStaysInPlaceWithLargeText() = checkShutterSlot(fontScale = 2f)

    private fun checkShutterSlot(fontScale: Float) {
        show(fontScale)
        val shutter = compose.onNodeWithContentDescription("Shutter")
        val initialBounds = shutter.fetchSemanticsNode().boundsInRoot
        shutter.assertIsEnabled().performClick()
        assertEquals(1, shots)

        for ((status, message) in listOf(
            WifiCaptureStatus.Shooting to "Shooting…",
            WifiCaptureStatus.Captured to "The camera confirmed the shot.",
            WifiCaptureStatus.Uncertain to "Shot unconfirmed. Check the camera.",
            WifiCaptureStatus.Rejected to "The camera could not shoot.",
        )) {
            compose.runOnIdle { state.value = state.value.copy(capture = status) }
            compose.onNodeWithText(message).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
            )
            if (status == WifiCaptureStatus.Shooting) shutter.assertIsNotEnabled() else shutter.assertIsEnabled()
            assertEquals(status.name, initialBounds, shutter.fetchSemanticsNode().boundsInRoot)
        }
        compose.runOnIdle { state.value = state.value.copy(canCaptureStill = false) }
        shutter.assertIsNotEnabled()
        compose.onNodeWithText("Wi-Fi shutter unavailable.").assertHasNoClickAction()
        assertEquals(initialBounds, shutter.fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun pausedFrameKeepsASeparateRetryActionAndAUsableShutter() {
        show()
        compose.onNodeWithText("Starting live view…").assertHasNoClickAction()
        compose.runOnIdle {
            frame.value = WifiLiveViewFrame(ImageBitmap(32, 32))
            state.value = state.value.copy(liveViewPaused = true, preview = WifiPreviewStatus.Streaming)
        }
        compose.onNodeWithContentDescription("Live view").assertExists()
        compose.onNodeWithText("Live view paused").assertHasNoClickAction()
        compose.onNodeWithText("Reconnect live view").performClick()
        compose.onNodeWithContentDescription("Shutter").assertIsEnabled().performClick()
        assertEquals(1, retries)
        assertEquals(1, shots)
    }
    @Test
    fun switchConfirmationCoversTheConnectionPageAndCancelsWithoutConnecting() {
        val id = "CAMERA"
        val registry = CameraSessionRegistry().apply {
            upsert(id) { it.copy(wifiRemote = WifiRemoteState(phase = WifiRemotePhase.Ready)) }
        }
        val controller = WifiRemoteController(
            CoroutineScope(EmptyCoroutineContext), registry,
            connector = { _, _ -> error("Unexpected connection") },
            claimControls = {}, releaseControls = {},
        )
        var closed = false
        compose.setContent {
            CameraGpsTheme(darkTheme = false) {
                WifiRemoteControlScreen(
                    id, controller, hapticsEnabled = false,
                    ensureNetworkPermission = { _, _ -> error("Unexpected permission request") },
                    onWifiSettings = {},
                    onClose = { closed = true },
                )
            }
        }

        compose.onNodeWithText("Switch to remote shooting?").assertExists()
        compose.onNodeWithText("Wi-Fi live view", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Wi-Fi live view").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(true, closed)
            assertEquals(WifiSessionPurpose.Browse, registry.get(id)?.wifiRemote?.purpose)
        }
    }

}
