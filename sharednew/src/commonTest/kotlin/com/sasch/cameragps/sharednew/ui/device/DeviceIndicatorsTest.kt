package com.sasch.cameragps.sharednew.ui.device

import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSettingState
import com.sasch.cameragps.sharednew.bluetooth.status.SignalLevel
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Batch A (0.2.1): device status card, coloured metrics and the Ⓘ dialog rows. */
class DeviceIndicatorsTest {
    private val connected = DeviceConnectionStatus.Connected
    private val connecting = DeviceConnectionStatus.Connecting
    private val disconnected = DeviceConnectionStatus.Disconnected

    private val onSupported = CameraSettingState(supported = true, enabled = true)
    private val offSupported = CameraSettingState(supported = true, enabled = false)

    private fun session(phase: BleSessionPhase, time: CameraSettingState = onSupported) =
        CameraSession("AA:BB", phase = phase, autoTimeCorrection = time, autoAreaAdjustment = time)

    @Test
    fun connectedCardShowsAStatusIconNotADisconnectAction() {
        assertEquals(
            ConnectionCardTrailing.ConnectedIndicator,
            connectionCardTrailing(connected, PendingConnection.None),
        )
        // The two necessary actions stay in the same slot.
        assertEquals(
            ConnectionCardTrailing.CancelConnecting,
            connectionCardTrailing(connecting, PendingConnection.None),
        )
        assertEquals(
            ConnectionCardTrailing.CancelConnecting,
            connectionCardTrailing(disconnected, PendingConnection.Connecting),
        )
        assertEquals(ConnectionCardTrailing.Connect, connectionCardTrailing(disconnected, PendingConnection.None))
        assertEquals(ConnectionCardTrailing.Busy, connectionCardTrailing(connected, PendingConnection.Disconnecting))
    }

    @Test
    fun everyCardStateMapsToExactlyOneTrailingSlot() {
        val all = DeviceConnectionStatus.entries.flatMap { s ->
            PendingConnection.entries.map { p -> connectionCardTrailing(s, p) }
        }.toSet()
        assertEquals(ConnectionCardTrailing.entries.toSet(), all)
    }

    @Test
    fun manualDisconnectIsOfferedOnlyWhileConnected() {
        // The Ⓘ dialog's "Disconnect" chip follows connectionAction == Disconnect.
        DeviceConnectionStatus.entries.forEach { s ->
            PendingConnection.entries.forEach { p ->
                val offered = connectionAction(s, p) == ConnectionAction.Disconnect
                assertEquals(s == connected && p != PendingConnection.Disconnecting, offered, "$s/$p")
            }
        }
    }

    @Test
    fun connectionToneFollowsHeadline() {
        assertEquals(StatusTone.Success, connectionTone(ConnectionHeadline.Connected))
        assertEquals(StatusTone.Active, connectionTone(ConnectionHeadline.Connecting))
        assertEquals(StatusTone.Active, connectionTone(ConnectionHeadline.Disconnecting))
        assertEquals(StatusTone.Neutral, connectionTone(ConnectionHeadline.Disconnected))
    }

    @Test
    fun signalIconIsColouredByLevel() {
        assertEquals(StatusTone.Success, signalTone(SignalLevel.Excellent))
        assertEquals(StatusTone.Success, signalTone(SignalLevel.Good))
        assertEquals(StatusTone.Warning, signalTone(SignalLevel.Fair))
        assertEquals(StatusTone.Error, signalTone(SignalLevel.Poor))
        assertEquals(StatusTone.Neutral, signalTone(SignalLevel.Unknown))
    }

    @Test
    fun writtenIsAccentBlueAndLastWriteIsTeal() {
        assertEquals(StatusTone.Active, WrittenMetricTone)
        assertEquals(StatusTone.Accent, LastWriteMetricTone)
    }

    @Test
    fun autoSettingsReadUnknownUnlessTheCameraIsConnected() {
        assertEquals(SettingDisplay.Unknown, infoSettingDisplay(null, onSupported))
        // A stale value from a disconnected / still-connecting session is not trusted.
        assertEquals(
            SettingDisplay.Unknown,
            infoSettingDisplay(session(BleSessionPhase.Disconnected), onSupported),
        )
        val connecting = session(BleSessionPhase.entries.first {
            deviceConnectionStatus(session(it)) == DeviceConnectionStatus.Connecting
        })
        assertEquals(SettingDisplay.Unknown, infoSettingDisplay(connecting, onSupported))

        val live = session(BleSessionPhase.Transmitting)
        assertEquals(SettingDisplay.Enabled, infoSettingDisplay(live, onSupported))
        assertEquals(SettingDisplay.Off, infoSettingDisplay(live, offSupported))
        assertEquals(SettingDisplay.Unknown, infoSettingDisplay(live, CameraSettingState()))
        assertEquals(
            SettingDisplay.Unsupported,
            infoSettingDisplay(live, CameraSettingState(supported = false, enabled = false)),
        )
    }

    @Test
    fun onlyEnabledGetsTheGreenCheck() {
        assertEquals(StatusTone.Success, settingTone(SettingDisplay.Enabled))
        assertTrue(showsCheck(settingTone(SettingDisplay.Enabled)))
        listOf(SettingDisplay.Off, SettingDisplay.Unsupported, SettingDisplay.Unknown).forEach {
            assertEquals(StatusTone.Neutral, settingTone(it))
            assertFalse(showsCheck(settingTone(it)), "$it")
        }
        // Same check style as "Bluetooth connected" / "Location writing · Active".
        assertTrue(showsCheck(connectionTone(ConnectionHeadline.Connected)))
        assertFalse(showsCheck(connectionTone(ConnectionHeadline.Connecting)))
    }

    @Test
    fun addressIsHiddenByDefaultAndTogglesOnTap() {
        val initial = AddressVisibility.entries.first()
        assertEquals(AddressVisibility.Hidden, initial)
        assertEquals(AddressVisibility.Shown, initial.toggled())
        assertEquals(AddressVisibility.Hidden, initial.toggled().toggled())
    }

    @Test
    fun hiddenAddressRevealsNothing() {
        val address = "a0:b1:c2:d3:e4:f5"
        val hidden = addressText(address, AddressVisibility.Hidden)
        assertEquals(HIDDEN_ADDRESS_TEXT, hidden)
        assertEquals(hidden, addressText("00:11:22:33:44:55", AddressVisibility.Hidden))
        assertFalse(hidden.any { it.isLetterOrDigit() })
        assertEquals("A0:B1:C2:D3:E4:F5", addressText(address, AddressVisibility.Shown))
    }
}
