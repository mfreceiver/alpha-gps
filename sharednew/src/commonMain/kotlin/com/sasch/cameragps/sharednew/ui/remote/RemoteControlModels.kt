package com.sasch.cameragps.sharednew.ui.remote

import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.remote_ble_dialog_known
import cameragps.sharednew.generated.resources.remote_ble_dialog_unverified
import cameragps.sharednew.generated.resources.remote_ble_failed_disconnected
import cameragps.sharednew.generated.resources.remote_ble_failed_not_connected
import cameragps.sharednew.generated.resources.remote_ble_failed_rejected
import cameragps.sharednew.generated.resources.remote_ble_failed_timeout
import cameragps.sharednew.generated.resources.remote_ble_failed_unavailable
import cameragps.sharednew.generated.resources.remote_ble_failed_wifi
import cameragps.sharednew.generated.resources.remote_ble_status_failed
import com.sasch.cameragps.sharednew.bluetooth.coordinator.BleLocationCompatibility
import com.sasch.cameragps.sharednew.bluetooth.coordinator.BleShutterFailureReason
import com.sasch.cameragps.sharednew.bluetooth.coordinator.BleShutterPhase
import com.sasch.cameragps.sharednew.bluetooth.coordinator.BleShutterState
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.WifiSessionPurpose
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.StringResource

/** In-page route of the device page's remote-shooting flow (no system navigation involved). */
enum class RemoteRoute { None, Chooser, Ble, Wifi }

fun remoteRouteOf(name: String?): RemoteRoute = RemoteRoute.entries.firstOrNull { it.name == name } ?: RemoteRoute.None

/** What the R1 page needs from the BLE remote-control owner (the orchestrator on Android). */
interface BleShutterControls {
    val states: StateFlow<Map<String, BleShutterState>>
    fun acquire(identifier: String): Boolean
    fun trigger(identifier: String): Boolean
    fun releaseAsync(identifier: String)
    fun cancelActiveShutter(identifier: String) {}
}

/** The one status line of the R1 page. [RequestSent] is the settled result of a press (never "photo taken"). */
enum class BleShutterStatusLine { Idle, Preparing, Ready, RequestSent, Pressing, Releasing, Uncertain, Failed }

fun bleShutterStatusLine(state: BleShutterState, requestSent: Boolean): BleShutterStatusLine = when (state.phase) {
    BleShutterPhase.Idle -> BleShutterStatusLine.Idle
    BleShutterPhase.Preparing -> BleShutterStatusLine.Preparing
    BleShutterPhase.Ready -> if (requestSent) BleShutterStatusLine.RequestSent else BleShutterStatusLine.Ready
    BleShutterPhase.Pressing -> BleShutterStatusLine.Pressing
    BleShutterPhase.Releasing -> BleShutterStatusLine.Releasing
    BleShutterPhase.Uncertain -> BleShutterStatusLine.Uncertain
    BleShutterPhase.Failed -> BleShutterStatusLine.Failed
}

/** Pressing and releasing are one in-flight press: the button shows the suppressed state. */
fun bleShutterPressing(state: BleShutterState): Boolean =
    state.phase == BleShutterPhase.Pressing || state.phase == BleShutterPhase.Releasing

fun bleShutterFailureText(reason: BleShutterFailureReason?): StringResource = when (reason) {
    BleShutterFailureReason.NotConnected -> Res.string.remote_ble_failed_not_connected
    BleShutterFailureReason.RemoteControlUnavailable -> Res.string.remote_ble_failed_unavailable
    BleShutterFailureReason.WifiOccupied -> Res.string.remote_ble_failed_wifi
    BleShutterFailureReason.CommandRejected -> Res.string.remote_ble_failed_rejected
    BleShutterFailureReason.Disconnected -> Res.string.remote_ble_failed_disconnected
    BleShutterFailureReason.Timeout -> Res.string.remote_ble_failed_timeout
    null -> Res.string.remote_ble_status_failed
}

fun bleCompatibilityMessage(compatibility: BleLocationCompatibility): StringResource = when (compatibility) {
    BleLocationCompatibility.KnownMutuallyExclusive -> Res.string.remote_ble_dialog_known
    BleLocationCompatibility.Unverified -> Res.string.remote_ble_dialog_unverified
}

/** What entering the R2 page should do given the current Wi-Fi session (see [wifiRemoteEntryAction]). */
enum class WifiRemoteEntryAction { Wait, Start, ConfirmSwitch, Reuse, BlockedOtherCamera, BlockedDownload }

fun wifiRemoteEntryAction(state: WifiRemoteState, owner: String?, identifier: String): WifiRemoteEntryAction {
    if (owner != null && !owner.equals(identifier, ignoreCase = true)) return WifiRemoteEntryAction.BlockedOtherCamera
    return when (state.phase) {
        WifiRemotePhase.Closing -> WifiRemoteEntryAction.Wait
        WifiRemotePhase.Idle, WifiRemotePhase.Failed -> WifiRemoteEntryAction.Start
        else -> when {
            state.purpose == WifiSessionPurpose.RemoteControl -> WifiRemoteEntryAction.Reuse
            state.imageTransfer.busy -> WifiRemoteEntryAction.BlockedDownload
            else -> WifiRemoteEntryAction.ConfirmSwitch
        }
    }
}

/** True when a recovery result should announce "location linking restored". */
fun shouldAnnounceGpsRecovery(status: com.sasch.cameragps.sharednew.bluetooth.coordinator.GpsRecoveryStatus): Boolean =
    status == com.sasch.cameragps.sharednew.bluetooth.coordinator.GpsRecoveryStatus.Restored ||
        status == com.sasch.cameragps.sharednew.bluetooth.coordinator.GpsRecoveryStatus.FirstWriteReceived
