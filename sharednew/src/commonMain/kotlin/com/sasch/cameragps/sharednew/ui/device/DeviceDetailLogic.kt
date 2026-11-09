package com.sasch.cameragps.sharednew.ui.device

import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.session.CameraAutoCorrectionSetting
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession

/** Coarse connection state shown under the camera name on the detail screen. */
enum class DeviceConnectionStatus { Disconnected, Connecting, Connected }

/**
 * "Connected" only once location transmission is possible (the same bar the
 * camera settings use); every intermediate handshake phase reads as connecting.
 */
fun deviceConnectionStatus(session: CameraSession?): DeviceConnectionStatus = when (session?.phase) {
    null, BleSessionPhase.Disconnected, BleSessionPhase.Error -> DeviceConnectionStatus.Disconnected
    BleSessionPhase.Transmitting -> DeviceConnectionStatus.Connected
    else -> DeviceConnectionStatus.Connecting
}

/** What the user has just asked for and the camera/session has not confirmed yet. */
enum class PendingConnection { None, Connecting, Disconnecting }

/** The single connect/disconnect button: its label and whether it can be pressed. */
enum class DeviceConnectionButton(val enabled: Boolean) {
    Connect(true),
    Connecting(false),
    Disconnect(true),
    Disconnecting(false),
}

/**
 * Facts come from the session registry ([status]); only the not-yet-confirmed request comes from
 * the user's intent ([pending]). A disconnect in flight disables both choices until the session is
 * really gone, a connect in flight or a handshake under way reads "connecting" and cannot be
 * pressed again, and a failed or paused session offers "connect" again.
 */
fun deviceConnectionButton(status: DeviceConnectionStatus, pending: PendingConnection): DeviceConnectionButton = when {
    pending == PendingConnection.Disconnecting -> DeviceConnectionButton.Disconnecting
    status == DeviceConnectionStatus.Connected -> DeviceConnectionButton.Disconnect
    status == DeviceConnectionStatus.Connecting -> DeviceConnectionButton.Connecting
    pending == PendingConnection.Connecting -> DeviceConnectionButton.Connecting
    else -> DeviceConnectionButton.Connect
}

/**
 * Camera auto time correction / auto area adjustment are always on in this build
 * (there is no switch any more). Returns the settings that should be switched on
 * right now: the camera is ready, the setting is supported, known to be off, and
 * neither mid-write nor in a failed state (a failure is not retried in a loop;
 * the next refresh clears it).
 */
fun autoCorrectionSettingsToEnable(session: CameraSession?): List<CameraAutoCorrectionSetting> {
    if (session?.phase != BleSessionPhase.Transmitting) return emptyList()
    return CameraAutoCorrectionSetting.entries.filter { setting ->
        val state = session.autoCorrectionSetting(setting)
        state.supported == true && state.enabled == false && !state.pending && !state.failed
    }
}

/**
 * Single saved camera => go straight to its detail screen. [dismissed] is set once
 * the user has navigated back to the list, so Back does not bounce them forward again.
 */
fun autoOpenDeviceAddress(addresses: List<String>, dismissed: Boolean): String? =
    if (!dismissed && addresses.size == 1) addresses.single() else null
