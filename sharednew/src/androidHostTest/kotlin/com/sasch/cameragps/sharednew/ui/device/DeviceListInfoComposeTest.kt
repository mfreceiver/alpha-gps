package com.sasch.cameragps.sharednew.ui.device

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.BluetoothDeviceInfo
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSettingState
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.bluetooth.status.TimeSyncOutcome
import com.sasch.cameragps.sharednew.bluetooth.status.TimeSyncStatus
import com.sasch.cameragps.sharednew.ui.devicelist.DeviceListItem
import com.sasch.cameragps.sharednew.ui.devicelist.SharedDeviceList
import kotlin.time.Clock
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Batch Q on the JVM (Robolectric + Compose): a device-list row opens its page from anywhere on the
 * card while its ⓘ opens the camera-info dialog without also opening the page; the dialog's name
 * row offers Connect / Cancel / Disconnect by connection state and no longer shows an address.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceListInfoComposeTest {
    @get:Rule
    val compose = createComposeRule()

    private val camera = BluetoothDeviceInfo(
        identifier = "AA:BB:CC:DD:EE:FF",
        name = "ILCE-7M4",
        isConnected = false,
        isSaved = true,
    )

    private fun showList(onOpenDetails: () -> Unit, onOpenInfo: () -> Unit) {
        compose.setContent {
            CameraGpsTheme {
                GlassBackground {
                    SharedDeviceList(
                        devices = listOf(camera),
                        items = emptyMap(),
                        onConnect = {},
                        onOpenInfo = { onOpenInfo() },
                        onOpenDetails = { onOpenDetails() },
                        onOpenTroubleshooting = {},
                    )
                }
            }
        }
    }

    @Test
    fun tappingTheCardOpensTheDevicePage() {
        var details = 0
        var info = 0
        showList(onOpenDetails = { details++ }, onOpenInfo = { info++ })
        compose.onNodeWithText("ILCE-7M4")
            .assert(SemanticsMatcher("open-page click label") {
                it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Open camera page"
            })
            .performClick()
        compose.waitForIdle()
        assertEquals(1, details)
        assertEquals(0, info)
    }

    @Test
    fun infoButtonOpensTheDialogOnly() {
        var details = 0
        var info = 0
        showList(onOpenDetails = { details++ }, onOpenInfo = { info++ })
        compose.onNodeWithContentDescription("Camera info: ILCE-7M4").performClick()
        compose.waitForIdle()
        assertEquals(1, info)
        assertEquals(0, details)
    }

    private var session by mutableStateOf<CameraSession?>(null)
    private var pending by mutableStateOf(PendingConnection.None)

    private fun showDialog(
        onConnect: () -> Unit = {},
        onCancel: () -> Unit = {},
        onDisconnect: () -> Unit = {},
        timeSync: TimeSyncStatus? = null,
        nowMillis: Long = 0L,
        onOpenBackgroundConditions: (() -> Unit)? = null,
        onKeepActiveChange: (Boolean) -> Unit = {},
    ) {
        compose.setContent {
            CameraGpsTheme {
                GlassBackground {
                    DeviceInfoDialog(
                        deviceName = "ILCE-7M4",
                        session = session,
                        pending = pending,
                        onConnect = onConnect,
                        onCancelConnecting = onCancel,
                        onDisconnect = onDisconnect,
                        keepActive = false,
                        onKeepActiveChange = onKeepActiveChange,
                        onClose = {},
                        onRename = {},
                        onRemove = {},
                        timeSync = timeSync,
                        nowMillis = nowMillis,
                        onOpenBackgroundConditions = onOpenBackgroundConditions,
                    )
                }
            }
        }
    }

    private fun phase(p: BleSessionPhase) = CameraSession(
        "AA:BB:CC:DD:EE:FF",
        phase = p,
        autoTimeCorrection = CameraSettingState(supported = true, enabled = true),
        autoAreaAdjustment = CameraSettingState(supported = true, enabled = false),
    )

    @Test
    fun nameRowActionFollowsTheConnectionState() {
        var connects = 0
        var cancels = 0
        var disconnects = 0
        showDialog(onConnect = { connects++ }, onCancel = { cancels++ }, onDisconnect = { disconnects++ })

        // Not connected (works for a camera that is out of range, from the list).
        compose.onNodeWithText("Connect").performClick()
        compose.onAllNodesWithText("Disconnect").assertCountEquals(0)

        session = phase(BleSessionPhase.Connecting)
        compose.onNodeWithText("Cancel").performClick()
        compose.onAllNodesWithText("Connect").assertCountEquals(0)

        session = phase(BleSessionPhase.Transmitting)
        // Disconnect only asks for the confirmation route; it does not disconnect by itself.
        compose.onNodeWithText("Disconnect").performClick()
        compose.onAllNodesWithText("Cancel").assertCountEquals(0)

        // A disconnect in flight: no action at all.
        pending = PendingConnection.Disconnecting
        compose.waitForIdle()
        listOf("Connect", "Cancel", "Disconnect").forEach { compose.onAllNodesWithText(it).assertCountEquals(0) }

        assertEquals(1, connects)
        assertEquals(1, cancels)
        assertEquals(1, disconnects)
    }

    @Test
    fun autoRowNamesTheSettingThatIsOff() {
        session = phase(BleSessionPhase.Transmitting)
        showDialog()
        compose.onNodeWithText("Auto time & area", substring = true).assertExists()
        compose.onNodeWithText("Area adjustment off", substring = true).assertExists()
        compose.onAllNodesWithText("AA:BB", substring = true).assertCountEquals(0)
    }

    private val syncedAt = 1_000_000L

    @Test
    fun timeSyncRowShowsTheAppliedOutcomeWithItsAge() {
        session = phase(BleSessionPhase.Transmitting)
        showDialog(
            timeSync = TimeSyncStatus(TimeSyncOutcome.Applied, atMillis = syncedAt),
            nowMillis = syncedAt + 5 * 60_000L,
        )
        compose.onNodeWithText("Time sync").assertExists()
        compose.onNodeWithText("Applied · 5 min ago").assertExists()
    }

    @Test
    fun timeSyncRowSaysNotConfirmedWhenTheCameraSentNoResult() {
        session = phase(BleSessionPhase.Transmitting)
        showDialog(
            timeSync = TimeSyncStatus(TimeSyncOutcome.Unconfirmed, atMillis = syncedAt),
            nowMillis = syncedAt + 2 * 60 * 60_000L,
        )
        compose.onNodeWithText("Sent, not confirmed · 2 h ago").assertExists()
    }

    @Test
    fun timeSyncRowReportsARejectionAndNeverNotRun() {
        session = phase(BleSessionPhase.Transmitting)
        showDialog(
            timeSync = TimeSyncStatus(TimeSyncOutcome.Rejected, atMillis = syncedAt, rejectReason = 3),
            nowMillis = syncedAt,
        )
        compose.onNodeWithText("The camera rejected the time correction").assertExists()
    }

    @Test
    fun timeSyncRowIsHiddenWithoutAResult() {
        session = phase(BleSessionPhase.Transmitting)
        showDialog(timeSync = TimeSyncStatus(TimeSyncOutcome.NotRun), nowMillis = syncedAt)
        compose.onAllNodesWithText("Time sync").assertCountEquals(0)
    }

    @Test
    fun backgroundConditionsLinkIsBelowTheKeepActiveToggleAndTapsAreIndependent() {
        var opened = 0
        var toggled = 0
        showDialog(
            onOpenBackgroundConditions = { opened++ },
            onKeepActiveChange = { toggled++ },
        )

        val toggle = compose.onNodeWithText("Keep active")
        val link = compose.onNodeWithText("Background & running conditions")
        assertTrue(toggle.fetchSemanticsNode().boundsInRoot.bottom <= link.fetchSemanticsNode().boundsInRoot.top)
        toggle.performTouchInput { click() }
        assertEquals(1, toggled)
        assertEquals(0, opened)

        link.performTouchInput { click() }
        assertEquals(1, opened)
        assertEquals(1, toggled)
    }

    @Test
    fun connectedRowShowsWriteAgeAndOnlyAFreshBatteryTag() {
        compose.setContent {
            CameraGpsTheme {
                GlassBackground {
                    SharedDeviceList(
                        devices = listOf(camera.copy(isConnected = true)),
                        items = mapOf(
                            camera.identifier to DeviceListItem(
                                identifier = camera.identifier,
                                customName = null,
                                isAlwaysOnEnabled = false,
                                isTransmissionActive = true,
                                isRemoteFeatureActive = false,
                                isShutterActive = false,
                                lastWriteAtMillis = Clock.System.now().toEpochMilliseconds() - 3 * 60_000L,
                                freshBatteryPercent = 87,
                            ),
                        ),
                        onConnect = {},
                        onOpenInfo = {},
                        onOpenDetails = {},
                        onOpenTroubleshooting = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Sending location · 3 min ago").assertExists()
        compose.onNodeWithText("87%").assertExists()
    }
}
