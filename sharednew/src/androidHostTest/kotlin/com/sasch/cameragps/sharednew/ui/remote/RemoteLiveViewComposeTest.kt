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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import com.sasch.cameragps.sharednew.remote.wifi.CameraPropertiesState
import com.sasch.cameragps.sharednew.remote.wifi.CameraPropertyOption
import com.sasch.cameragps.sharednew.remote.wifi.CameraShootingProperty
import com.sasch.cameragps.sharednew.remote.wifi.CameraShootingPropertyKey
import com.sasch.cameragps.sharednew.remote.wifi.PropertyProbeStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiAutomaticConnector
import com.sasch.cameragps.sharednew.remote.wifi.WifiCaptureStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiLiveViewFrame
import com.sasch.cameragps.sharednew.remote.wifi.WifiPreviewStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteController
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteFailure
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
                        onProbe = { state.value = state.value.copy(propertyProbe = PropertyProbeStatus.Reading) },
                        onShutter = { shots++ }, onRetryLiveView = { retries++ },
                    )
                }
            }
        }
    }

    @Test
    fun probeTapShowsProgressThenAllowsAnotherRead() {
        show()
        compose.onNodeWithContentDescription("More camera settings").performClick()
        val probe = compose.onNodeWithText("Read camera properties into session summary")
        probe.performTouchInput { click() }
        compose.onNodeWithText("Reading camera properties…").assertExists().assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(propertyProbe = PropertyProbeStatus.Recorded, probeAttempt = 1) }
        compose.onNodeWithText("Read 1 saved to summary").assertExists().assertIsEnabled()
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
            automaticConnector = object : WifiAutomaticConnector {
                override val phonePreconditions = null
                override suspend fun open(
                    identifier: String,
                    scope: CoroutineScope,
                    onPhase: (WifiRemotePhase) -> Unit,
                ) = error("Unexpected connection")
            },
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

    @Test
    fun bottomExposureBarOpensInPlaceHorizontalDialAndRecIsLeftOfAf() {
        var written: Pair<CameraShootingPropertyKey, Long>? = null
        var focuses = 0
        var movies = 0
        val richState = WifiRemoteState(
            phase = WifiRemotePhase.Ready,
            purpose = WifiSessionPurpose.RemoteControl,
            cameraName = "ILCE-1",
            canCaptureStill = true,
            canFocus = true,
            canRecordMovie = true,
            supportsLiveView = true,
            cameraProperties = CameraPropertiesState(
                canWrite = true,
                batteryPercent = 82,
                remainingShots = 1240,
                properties = listOf(
                    CameraShootingProperty(
                        key = CameraShootingPropertyKey.ExposureProgram,
                        dataType = 6,
                        rawValue = 131075L,
                        displayValue = "A",
                        writable = false,
                    ),
                    CameraShootingProperty(
                        key = CameraShootingPropertyKey.Aperture,
                        dataType = 4,
                        rawValue = 240L,
                        displayValue = "f/2.4",
                        writable = true,
                        options = listOf(
                            CameraPropertyOption(140L, "f/1.4"),
                            CameraPropertyOption(240L, "f/2.4"),
                            CameraPropertyOption(400L, "f/4"),
                        ),
                    ),
                ),
            ),
        )
        compose.setContent {
            CameraGpsTheme(darkTheme = false) {
                GlassBackground(contentIsBackdropSource = false) {
                    RemoteLiveView(
                        richState, frame, hapticsEnabled = false, onBack = {},
                        onShutter = { shots++ }, onRetryLiveView = { retries++ },
                        onFocus = { focuses++ },
                        onToggleMovie = { movies++ },
                        onSetProperty = { k, v -> written = k to v },
                    )
                }
            }
        }
        // Top bar Row 1 shows battery & remaining shots telemetry; Row 2 shows shooting mode value.
        compose.onNodeWithContentDescription("82%, 1240 left").assertExists()
        val telemetryBounds = compose.onNodeWithContentDescription("82%, 1240 left").fetchSemanticsNode().boundsInRoot
        val modeBounds = compose.onNodeWithText("A").fetchSemanticsNode().boundsInRoot
        assertEquals(true, telemetryBounds.top < modeBounds.top)

        // REC sits on the left of Shutter; AF sits on the right.
        val recBounds = compose.onNodeWithText("REC").fetchSemanticsNode().boundsInRoot
        val shutterBounds = compose.onNodeWithContentDescription("Shutter").fetchSemanticsNode().boundsInRoot
        val afBounds = compose.onNodeWithText("AF").fetchSemanticsNode().boundsInRoot
        assertEquals(true, recBounds.left < shutterBounds.left)
        assertEquals(true, shutterBounds.right < afBounds.right)

        compose.onNodeWithText("AF").performClick()
        compose.onNodeWithText("REC").performClick()
        assertEquals(1, focuses)
        assertEquals(1, movies)

        // Tapping Aperture replaces the bottom shutter row in-place with the centered ruler dial.
        compose.onNodeWithText("f/2.4").performClick()
        compose.onNodeWithText("Done").assertExists()
        compose.onNodeWithContentDescription("Shutter").assertDoesNotExist()
        compose.onNodeWithText("f/4").performClick()
        assertEquals(CameraShootingPropertyKey.Aperture to 400L, written)
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Shutter").assertExists()
    }

    @Test
    fun wifiRemoteFailureOffersBluetoothShutterFallback() {
        val id = "CAMERA"
        val registry = CameraSessionRegistry().apply {
            upsert(id) {
                it.copy(
                    wifiRemote = WifiRemoteState(
                        phase = WifiRemotePhase.Failed,
                        failure = WifiRemoteFailure.NetworkConnectionFailed,
                        purpose = WifiSessionPurpose.RemoteControl,
                    ),
                )
            }
        }
        val controller = WifiRemoteController(
            CoroutineScope(EmptyCoroutineContext), registry,
            connector = { _, _ -> error("Unexpected connection") },
            claimControls = {}, releaseControls = {},
            automaticConnector = object : WifiAutomaticConnector {
                override val phonePreconditions = null
                override suspend fun open(
                    identifier: String,
                    scope: CoroutineScope,
                    onPhase: (WifiRemotePhase) -> Unit,
                ) = error("Unexpected connection")
            },
        )
        var fellBackToBle = false
        compose.setContent {
            CameraGpsTheme(darkTheme = false) {
                WifiRemoteControlScreen(
                    id, controller, hapticsEnabled = false,
                    ensureNetworkPermission = { _, _ -> },
                    onWifiSettings = {},
                    onClose = {},
                    onFallbackToBle = { fellBackToBle = true },
                )
            }
        }
        compose.onNodeWithText("Use Bluetooth shutter instead").assertExists().performClick()
        assertEquals(true, fellBackToBle)
    }

}
