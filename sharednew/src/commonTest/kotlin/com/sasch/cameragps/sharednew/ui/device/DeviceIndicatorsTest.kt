package com.sasch.cameragps.sharednew.ui.device

import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSettingState
import com.sasch.cameragps.sharednew.bluetooth.status.SignalLevel
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import kotlin.test.Test
import kotlin.test.assertEquals

/** Device status card, coloured metrics and the Ⓘ dialog rows (batch A 0.2.1, batch Q). */
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

    private fun live(time: CameraSettingState, area: CameraSettingState) = CameraSession(
        "AA:BB",
        phase = BleSessionPhase.Transmitting,
        autoTimeCorrection = time,
        autoAreaAdjustment = area,
    )

    @Test
    fun autoTimeAndAreaReadAsOneRowNamingWhatIsOff() {
        val unsupported = CameraSettingState(supported = false, enabled = false)
        val unknown = CameraSettingState()
        assertEquals(AutoCorrectionSummary.Enabled, autoCorrectionSummary(live(onSupported, onSupported)))
        // The two settings are independent on the camera: the row names the one that is off.
        assertEquals(AutoCorrectionSummary.TimeNotEnabled, autoCorrectionSummary(live(offSupported, onSupported)))
        assertEquals(AutoCorrectionSummary.AreaNotEnabled, autoCorrectionSummary(live(onSupported, offSupported)))
        assertEquals(AutoCorrectionSummary.NeitherEnabled, autoCorrectionSummary(live(offSupported, offSupported)))
        assertEquals(AutoCorrectionSummary.NeitherEnabled, autoCorrectionSummary(live(offSupported, unsupported)))
        assertEquals(AutoCorrectionSummary.AreaNotEnabled, autoCorrectionSummary(live(onSupported, unsupported)))
        assertEquals(AutoCorrectionSummary.Unsupported, autoCorrectionSummary(live(unsupported, unsupported)))
        // One side still unknown: report the known "off", otherwise unknown (never a false "enabled").
        assertEquals(AutoCorrectionSummary.TimeNotEnabled, autoCorrectionSummary(live(offSupported, unknown)))
        assertEquals(AutoCorrectionSummary.Unknown, autoCorrectionSummary(live(onSupported, unknown)))
        // Not connected: nothing is trusted.
        assertEquals(AutoCorrectionSummary.Unknown, autoCorrectionSummary(null))
        assertEquals(AutoCorrectionSummary.Unknown, autoCorrectionSummary(session(BleSessionPhase.Disconnected)))
    }

    @Test
    fun autoRowToneIsGreenOnlyWhenBothAreOn() {
        assertEquals(StatusTone.Success, autoCorrectionTone(AutoCorrectionSummary.Enabled))
        listOf(
            AutoCorrectionSummary.TimeNotEnabled,
            AutoCorrectionSummary.AreaNotEnabled,
            AutoCorrectionSummary.NeitherEnabled,
        ).forEach { assertEquals(StatusTone.Warning, autoCorrectionTone(it), "$it") }
        assertEquals(StatusTone.Neutral, autoCorrectionTone(AutoCorrectionSummary.Unsupported))
        assertEquals(StatusTone.Neutral, autoCorrectionTone(AutoCorrectionSummary.Unknown))
    }

    @Test
    fun signalRingArcFollowsTheLevel() {
        assertEquals(1f, signalArcFraction(SignalLevel.Excellent))
        assertEquals(0.75f, signalArcFraction(SignalLevel.Good))
        assertEquals(0.5f, signalArcFraction(SignalLevel.Fair))
        assertEquals(0.25f, signalArcFraction(SignalLevel.Poor))
        // Unknown: only the track.
        assertEquals(0f, signalArcFraction(SignalLevel.Unknown))
    }

    @Test
    fun locationSignalIsColouredStrongMediumWeak() {
        assertEquals(StatusTone.Success, locationSignalTone(LocationSignalLevel.Strong))
        assertEquals(StatusTone.Warning, locationSignalTone(LocationSignalLevel.Medium))
        assertEquals(StatusTone.Error, locationSignalTone(LocationSignalLevel.Weak))
        assertEquals(StatusTone.Neutral, locationSignalTone(LocationSignalLevel.Unknown))
    }
}
