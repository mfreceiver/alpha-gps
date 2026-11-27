package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteFailure
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
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
class WifiConnectionOverlayComposeTest {
    @get:Rule val compose = createComposeRule()

    private var frame: WifiConnectionFrame by mutableStateOf(WifiConnectionFrame.Progress(WifiConnectStep.JoinCamera))
    private var cancelled = 0
    private var dismissed = 0
    private var appSettingsOpened = 0
    private var wifiSettingsOpened = 0

    private fun show(totalSteps: Int = 3) {
        compose.setContent {
            CameraGpsTheme {
                GlassBackground {
                    WifiConnectionOverlay(
                        frame = frame,
                        networkPermissionMessage = "Nearby devices permission is needed.",
                        onCancel = { cancelled++ },
                        onDismiss = { dismissed++ },
                        onAppSettings = { appSettingsOpened++ },
                        onWifiSettings = { wifiSettingsOpened++ },
                        totalSteps = totalSteps,
                    )
                }
            }
        }
    }

    @Test
    fun permissionCardConsumesBlankTapsAndOnlyBackgroundDismisses() {
        frame = WifiConnectionFrame.Failed(WifiRemoteFailure.NetworkPermissionDenied)
        show()
        compose.onNodeWithText("Grant permission").performClick()
        compose.onRoot().performTouchInput { click(center - Offset(0f, height * 0.12f)) }
        compose.runOnIdle {
            assertEquals(1, appSettingsOpened)
            assertEquals(0, dismissed)
        }
        compose.onNode(SemanticsMatcher("Return action") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == "Return"
        }).assertExists()
        compose.onRoot().performTouchInput { click(Offset(4f, 4f)) }
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test
    fun wifiDisabledOpensSettingsWithoutDismissing() {
        frame = WifiConnectionFrame.Failed(WifiRemoteFailure.WifiDisabled)
        show()
        compose.onNodeWithText("Open Wi-Fi settings").performClick()
        compose.runOnIdle {
            assertEquals(1, wifiSettingsOpened)
            assertEquals(0, dismissed)
        }
    }

    @Test
    fun progressCanBeCancelledAndReadyRemovesCancelSemantics() {
        show()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(1, cancelled)
            frame = WifiConnectionFrame.Ready
        }
        compose.onNodeWithText("Opening live view…").assertExists()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w600dp-h240dp")
    fun shortLandscapeCanScrollToSettingsAction() {
        frame = WifiConnectionFrame.Failed(WifiRemoteFailure.NetworkPermissionDenied)
        show()
        compose.onNodeWithText("Grant permission").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, appSettingsOpened)
            assertEquals(0, dismissed)
        }
    }

    @Test
    fun remoteOverlayUsesCameraConnectionCopyInsteadOfPhotoCopy() {
        frame = WifiConnectionFrame.Progress(WifiConnectStep.ReadCatalog)
        show()
        compose.onNodeWithText("Connecting to the camera").assertExists()
        compose.onNodeWithText("Reading the photo list").assertDoesNotExist()
        compose.runOnIdle { frame = WifiConnectionFrame.Unsupported }
        compose.onNodeWithText("Photo transfer unavailable").assertDoesNotExist()
        compose.onNodeWithText("Wi-Fi live view").assertExists()
    }

    @Test
    fun photoTransferKeepsCatalogAndPhotoDayMessages() {
        frame = WifiConnectionFrame.Progress(WifiConnectStep.ReadCatalog)
        show(totalSteps = 4)
        compose.onNodeWithText("Reading the photo list").assertExists()
        compose.runOnIdle { frame = WifiConnectionFrame.Ready }
        compose.onNodeWithText("Opening the photo days…").assertExists()
    }
}
