package com.sasch.cameragps.sharednew.ui.remote

import android.content.ClipDescription
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.sasch.cameragps.sharednew.remote.wifi.CameraWifiCredentials
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteFailure
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import org.junit.Assert.assertEquals
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
class WifiConnectFallbackContentComposeTest {
    @get:Rule val compose = createComposeRule()

    private var state by mutableStateOf(WifiRemoteState())
    private var blocked by mutableStateOf(false)
    private var manual by mutableStateOf(false)
    private var host by mutableStateOf("")
    private var manualConnects = 0
    private var settingsOpened = 0
    private var continued = 0

    private fun show(supportsAutomaticConnection: Boolean = true) {
        compose.setContent {
            CameraGpsTheme {
                GlassBackground {
                    WifiConnectFallbackContent(
                        state = state,
                        supportsAutomaticConnection = supportsAutomaticConnection,
                        supportsManualSetup = false,
                        supportsAddressDiscovery = false,
                        host = host,
                        onHostChange = { host = it },
                        manual = manual,
                        onToggleManual = { manual = !manual },
                        onAutoConnect = {},
                        onManualConnect = { manualConnects++ },
                        onWifiSettings = {},
                        onContinueManualJoin = { continued++ },
                        onDisconnect = {},
                        networkPermissionMessage = "Nearby devices permission is needed.",
                        notice = if (blocked) "Another camera is connected." else null,
                        connectionBlocked = blocked,
                        onAppSettings = { settingsOpened++ },
                    )
                }
            }
        }
    }

    @Test
    fun manualConnectionFollowsInputAndBlock() {
        show()
        compose.onNodeWithText("Manual connection").performClick()
        compose.onNodeWithText("Connect").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Camera IP address").performScrollTo().performTextInput("192.168.1.1")
        compose.onNodeWithText("Connect").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("192.168.1.1", host)
            assertEquals(1, manualConnects)
            blocked = true
        }
        compose.onNodeWithText("Another camera is connected.").assertExists()
        compose.onNodeWithText("Connect automatically").assertIsNotEnabled()
        compose.onNodeWithText("Connect").assertIsNotEnabled()
    }

    @Test
    fun progressIsLiveAndPermissionFailureOffersSettings() {
        state = WifiRemoteState(phase = WifiRemotePhase.OpeningSession)
        show()
        compose.onNodeWithText("Connecting to camera…").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
        compose.runOnIdle {
            state = WifiRemoteState(phase = WifiRemotePhase.Failed, failure = WifiRemoteFailure.NetworkPermissionDenied)
        }
        compose.onNodeWithText("Nearby devices permission is needed.").assertExists()
        compose.onNodeWithText("Grant permission").performClick()
        compose.runOnIdle { assertEquals(1, settingsOpened) }
    }

    @Test
    fun manualJoinHasReadOnlyCopyableCredentialsAndCopyFeedback() {
        state = WifiRemoteState(
            phase = WifiRemotePhase.AwaitingManualNetwork,
            manualNetwork = CameraWifiCredentials("Test camera", "test-password", null),
        )
        show(supportsAutomaticConnection = false)
        compose.onNodeWithContentDescription("Wi-Fi password").assert(
            SemanticsMatcher.keyNotDefined(SemanticsActions.SetText),
        )
        compose.onNodeWithContentDescription("Copy Wi-Fi password").performClick()
        compose.onNodeWithText("Wi-Fi password copied").assertExists()
        compose.onNodeWithText("I’ve joined — connect").performClick()
        compose.runOnIdle { assertEquals(1, continued) }
        val clip = wifiCredentialClip("test-password", sensitive = true).clipData
        assertEquals("test-password", clip.getItemAt(0).text)
        assertTrue(clip.description.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
    }
}
