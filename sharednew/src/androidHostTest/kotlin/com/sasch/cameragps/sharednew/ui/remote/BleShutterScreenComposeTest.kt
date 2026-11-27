package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sasch.cameragps.sharednew.bluetooth.coordinator.BleLocationCompatibility
import com.sasch.cameragps.sharednew.bluetooth.coordinator.BleShutterFailureReason
import com.sasch.cameragps.sharednew.bluetooth.coordinator.BleShutterPhase
import com.sasch.cameragps.sharednew.bluetooth.coordinator.BleShutterState
import com.sasch.cameragps.sharednew.bluetooth.coordinator.GpsRecoveryBaseline
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferState
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.WifiSessionPurpose
import com.sasch.cameragps.sharednew.ui.help.GuideTopic
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class FakeBleControls : BleShutterControls {
    val flow = MutableStateFlow<Map<String, BleShutterState>>(emptyMap())
    override val states: StateFlow<Map<String, BleShutterState>> = flow
    val acquired = mutableListOf<String>()
    val triggered = mutableListOf<String>()
    val released = mutableListOf<String>()
    override fun acquire(identifier: String): Boolean { acquired += identifier; return true }
    override fun trigger(identifier: String): Boolean { triggered += identifier; return true }
    override fun releaseAsync(identifier: String) { released += identifier }
    fun set(state: BleShutterState) { flow.value = mapOf("AA:BB" to state) }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BleShutterScreenComposeTest {
    @get:Rule
    val compose = createComposeRule()

    private val controls = FakeBleControls()
    private var closed = 0
    private var baselines = mutableListOf<GpsRecoveryBaseline>()
    private var guides = mutableListOf<GuideTopic>()

    private fun show(name: String? = "ILCE-7M4", onSwitchToWifi: (() -> Unit)? = null) {
        compose.setContent {
            CameraGpsTheme(darkTheme = true) {
                BleShutterScreen(
                    identifier = "aa:bb",
                    deviceName = name,
                    isCustomName = false,
                    controls = controls,
                    live = null,
                    hapticsEnabled = false,
                    onClose = { closed++ },
                    onLeft = { baselines += it },
                    onOpenGuide = { guides += it },
                    onSwitchToWifi = onSwitchToWifi,
                )
            }
        }
    }

    @Test
    fun unverifiedModelShowsWarningAndCancelAcquiresNothing() {
        show()
        compose.onNodeWithText("Pause location linking?").assertExists()
        compose.onNodeWithText("may be mutually exclusive", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, closed)
        assertTrue(controls.acquired.isEmpty())
        assertTrue(controls.released.isEmpty())
    }

    @Test
    fun knownModelShowsPauseMessage() {
        show("ILCE-6400")
        compose.onNodeWithText("will be paused", substring = true).assertExists()
    }

    @Test
    fun confirmAcquiresAndShutterFollowsState() {
        show()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(listOf("AA:BB"), controls.acquired)
        compose.onNodeWithContentDescription("Shutter").assertIsNotEnabled()
        controls.set(BleShutterState(phase = BleShutterPhase.Ready))
        compose.waitForIdle()
        compose.onNodeWithText("Ready").assertExists()
        compose.onNodeWithContentDescription("Shutter").assertIsEnabled().performClick()
        assertEquals(listOf("AA:BB"), controls.triggered)
    }

    @Test
    fun pressingAndReleasingDisableTheButtonThenRequestSentShows() {
        show()
        compose.onNodeWithText("Continue").performClick()
        controls.set(BleShutterState(phase = BleShutterPhase.Pressing))
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Shutter").assertIsNotEnabled()
        controls.set(BleShutterState(phase = BleShutterPhase.Releasing))
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Shutter").assertIsNotEnabled()
        controls.set(BleShutterState(phase = BleShutterPhase.Ready))
        compose.waitForIdle()
        compose.onNodeWithText("Request sent").assertExists()
    }

    @Test
    fun uncertainSaysCheckCamera() {
        show()
        compose.onNodeWithText("Continue").performClick()
        controls.set(BleShutterState(phase = BleShutterPhase.Uncertain, releaseUncertain = true))
        compose.waitForIdle()
        compose.onNodeWithText("Please check the camera").assertExists()
    }

    @Test
    fun failureOffersRetryAndGuide() {
        show()
        compose.onNodeWithText("Continue").performClick()
        controls.set(BleShutterState(phase = BleShutterPhase.Failed, failureReason = BleShutterFailureReason.Timeout))
        compose.waitForIdle()
        compose.onNodeWithText("The camera did not respond in time.").assertExists()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(2, controls.acquired.size)
        compose.onNodeWithText("Fix steps").performClick()
        assertEquals(listOf(GuideTopic.Remote), guides)
    }

    @Test
    fun cameraNameAndShutterStayInPlaceWhenRecoveryActionsAppear() {
        show()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("ILCE-7M4").assertExists()
        val shutter = compose.onNodeWithContentDescription("Shutter")
        val initial = shutter.fetchSemanticsNode().boundsInRoot
        controls.set(BleShutterState(phase = BleShutterPhase.Failed, failureReason = BleShutterFailureReason.Disconnected))
        compose.onNodeWithText("Bluetooth disconnected.").assertExists()
        assertEquals(initial, shutter.fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun switchingToWifiReleasesAndRecordsGpsBaselineBeforeNavigation() {
        var switches = 0
        show(onSwitchToWifi = {
            assertEquals(listOf("AA:BB"), controls.released)
            assertEquals(1, baselines.size)
            switches++
        })
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Switch to Wi-Fi live view").performClick()
        assertEquals(1, switches)
        assertEquals(0, closed)
    }

    @Test
    fun leavingReleasesAndHandsOverRecoveryBaseline() {
        var showScreen by mutableStateOf(true)
        compose.setContent {
            CameraGpsTheme(darkTheme = true) {
                if (showScreen) {
                    BleShutterScreen(
                        identifier = "aa:bb", deviceName = "ILCE-7M4", isCustomName = false, controls = controls,
                        live = null, hapticsEnabled = false, onClose = {}, onLeft = { baselines += it },
                    )
                }
            }
        }
        compose.onNodeWithText("Continue").performClick()
        showScreen = false
        compose.waitForIdle()
        assertEquals(listOf("AA:BB"), controls.released)
        assertEquals(1, baselines.size)
        assertTrue(baselines.single().active)
    }

    @Test
    fun statusAndEntryLogic() {
        assertEquals(BleShutterStatusLine.RequestSent, bleShutterStatusLine(BleShutterState(phase = BleShutterPhase.Ready), requestSent = true))
        assertEquals(BleShutterStatusLine.Ready, bleShutterStatusLine(BleShutterState(phase = BleShutterPhase.Ready), requestSent = false))
        assertEquals(BleShutterStatusLine.Uncertain, bleShutterStatusLine(BleShutterState(phase = BleShutterPhase.Uncertain), requestSent = true))
        assertTrue(bleShutterPressing(BleShutterState(phase = BleShutterPhase.Releasing)))

        val browse = WifiRemoteState(phase = WifiRemotePhase.OpeningSession, purpose = WifiSessionPurpose.Browse)
        assertEquals(WifiRemoteEntryAction.Start, wifiRemoteEntryAction(WifiRemoteState(), null, "A"))
        assertEquals(WifiRemoteEntryAction.ConfirmSwitch, wifiRemoteEntryAction(browse, "A", "a"))
        assertEquals(WifiRemoteEntryAction.BlockedOtherCamera, wifiRemoteEntryAction(browse, "B", "A"))
        assertEquals(
            WifiRemoteEntryAction.Reuse,
            wifiRemoteEntryAction(WifiRemoteState(phase = WifiRemotePhase.OpeningSession, purpose = WifiSessionPurpose.RemoteControl), "A", "A"),
        )
        assertEquals(WifiRemoteEntryAction.Wait, wifiRemoteEntryAction(WifiRemoteState(phase = WifiRemotePhase.Closing), "A", "A"))
        assertNull(remoteRouteOf("x").let { if (it == RemoteRoute.None) null else it })
    }
}
