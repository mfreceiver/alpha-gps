package com.sasch.cameragps.sharednew.ui.remote

import com.sasch.cameragps.sharednew.remote.wifi.UNKNOWN_DATE_KEY
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraCapture
import com.sasch.cameragps.sharednew.remote.wifi.WifiDownloadPlan
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteFailure
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import kotlinx.datetime.LocalDate

// ---- Connection overlay ----------------------------------------------------------------------

/**
 * The segments of the connection overlay's step ring, in order. Bluetooth is not one of them: the
 * device page connects it before the Wi-Fi screen is entered.
 */
internal enum class WifiConnectStep { PrepareCamera, ApproveNetwork, JoinCamera, ReadCatalog }

/** What the full-screen connection overlay shows; null = no overlay (the day list / grid is up). */
internal sealed interface WifiConnectionFrame {
    /** Connecting: [step] is the running segment, every earlier one is done. */
    data class Progress(val step: WifiConnectStep) : WifiConnectionFrame

    /** All done; shown briefly (green ring) before the day list takes over. */
    data object Ready : WifiConnectionFrame

    /** The attempt ended: red icon, no help / retry (entering again is the retry). */
    data class Failed(val failure: WifiRemoteFailure?) : WifiConnectionFrame

    /** Connected, but the camera offers no photo transfer. Ends like a failure. */
    data object Unsupported : WifiConnectionFrame

    /** The session is being ended (cancel / leaving). */
    data object Closing : WifiConnectionFrame
}

/**
 * Maps the controller state to the overlay frame. Pure (unit-tested): the existing connection flow
 * and its phases are untouched, this only decides what to draw.
 *
 * - [awaitingAutoConnect]: the screen will start the automatic connection itself (Idle right after
 *   entry is the first step, not an empty page).
 * - [catalogShown]: the day list has already been shown on this connection; a later catalog read
 *   (refresh) keeps the list on screen instead of covering it with the overlay again.
 *
 * [WifiRemotePhase.AwaitingManualNetwork] belongs to the manual (non-automatic) path, which keeps
 * its own controls, so it maps to null as well.
 */
internal fun wifiConnectionFrame(
    state: WifiRemoteState,
    awaitingAutoConnect: Boolean,
    catalogShown: Boolean,
): WifiConnectionFrame? = when (state.phase) {
    WifiRemotePhase.Idle -> if (awaitingAutoConnect) WifiConnectionFrame.Progress(WifiConnectStep.PrepareCamera) else null
    WifiRemotePhase.PreparingCamera -> WifiConnectionFrame.Progress(WifiConnectStep.PrepareCamera)
    WifiRemotePhase.AwaitingNetworkApproval -> WifiConnectionFrame.Progress(WifiConnectStep.ApproveNetwork)
    WifiRemotePhase.AwaitingManualNetwork -> null
    WifiRemotePhase.JoiningNetwork, WifiRemotePhase.OpeningSession ->
        WifiConnectionFrame.Progress(WifiConnectStep.JoinCamera)
    WifiRemotePhase.Closing -> WifiConnectionFrame.Closing
    WifiRemotePhase.Failed -> WifiConnectionFrame.Failed(state.failure)
    WifiRemotePhase.Ready -> {
        val browser = state.photoBrowser
        when {
            !state.canTransferImages -> WifiConnectionFrame.Unsupported
            catalogShown -> null
            // Ready but the day list is not opened yet (auto-enter is one frame away) or the
            // first catalog read is still running: that is the overlay's last step.
            !browser.open -> WifiConnectionFrame.Progress(WifiConnectStep.ReadCatalog)
            browser.grouped && browser.dateKey == null && browser.loading && !browser.failed ->
                WifiConnectionFrame.Progress(WifiConnectStep.ReadCatalog)
            else -> null
        }
    }
}

/** A failure frame closes itself after this long — except the permission one, which waits. */
internal const val CONNECTION_FAILURE_AUTO_DISMISS_MS = 5_000L

/** How long the green "ready" frame stays before the day list fades in. */
internal const val CONNECTION_READY_HOLD_MS = 400L

/** The failure that is fixed in the app settings: it offers "grant" and never auto-dismisses. */
internal fun WifiConnectionFrame.needsPermissionAction(): Boolean =
    this is WifiConnectionFrame.Failed && failure == WifiRemoteFailure.NetworkPermissionDenied

// ---- Grid view options ---------------------------------------------------------------------

/** Tile size of the day grid: columns on a phone, minimum tile width, corner radius. */
internal enum class WifiGridSize(val columns: Int, val minTileDp: Int, val cornerDp: Int) {
    Small(4, 80, 8),
    Medium(3, 104, 10),
    Large(2, 150, 14),
}

/**
 * Grid view preferences (persisted by the app). Default: small tiles and no tags — the format, size
 * and protection tags are each opt-in.
 */
data class WifiGridOptions(
    val size: String = WifiGridSize.Small.name,
    val showFormat: Boolean = false,
    val showSize: Boolean = false,
    val showProtection: Boolean = false,
) {
    internal val gridSize: WifiGridSize
        get() = WifiGridSize.entries.firstOrNull { it.name == size } ?: WifiGridSize.Small

    internal fun withSize(size: WifiGridSize) = copy(size = size.name)
}

// ---- Capture tags ----------------------------------------------------------------------------

/**
 * Protection of a whole capture: true if any file is protected, false only when every file says it
 * is not, null when unknown (the v4 catalog does not report it) — then no tag is shown, never a guess.
 */
internal val WifiCameraCapture.isProtected: Boolean?
    get() = when {
        files.any { it.isProtected == true } -> true
        files.isNotEmpty() && files.all { it.isProtected == false } -> false
        else -> null
    }

/** Whether any capture of [captures] reports a protection flag at all (else the switch is greyed). */
internal fun protectionKnown(captures: List<WifiCameraCapture>): Boolean =
    captures.any { it.isProtected != null }

/** "12.3 MB": the size tag, always megabytes with one decimal (binary MB), rounded half up. */
internal fun formatSizeTag(bytes: Long): String {
    val mb = 1024L * 1024L
    val tenths = (bytes.coerceAtLeast(0) * 10 + mb / 2) / mb
    return "${tenths / 10}.${tenths % 10} MB"
}

/** What the viewer (and its info line) needs about one capture; see the view model's day API. */
internal data class WifiCaptureDetails(
    /** "JPG", "RAW", "J&R", … (null when no known format). */
    val format: String?,
    /** Sum of all original files of the capture. */
    val totalBytes: Long,
    /** See [isProtected]: null = unknown. */
    val isProtected: Boolean?,
    /** What a single download of this capture fetches under the current policy. */
    val plan: WifiDownloadPlan,
)

// ---- Day list --------------------------------------------------------------------------------

internal enum class DayRelation { Today, Yesterday, Other }

/** Parses a "yyyy-MM-dd" day key; null for the unknown group or anything malformed. */
internal fun dateKeyToLocalDate(key: String): LocalDate? {
    if (key == UNKNOWN_DATE_KEY) return null
    return runCatching { LocalDate.parse(key) }.getOrNull()
}

/** Today / yesterday relative to [today], else Other (also for unknown dates). */
internal fun dayRelation(key: String, today: LocalDate): DayRelation {
    val date = dateKeyToLocalDate(key) ?: return DayRelation.Other
    val days = today.toEpochDays() - date.toEpochDays()
    return when (days) {
        0L -> DayRelation.Today
        1L -> DayRelation.Yesterday
        else -> DayRelation.Other
    }
}

/** ISO weekday 1 (Monday) … 7 (Sunday) of a day key, null when unknown. */
internal fun dateKeyWeekday(key: String): Int? = dateKeyToLocalDate(key)?.dayOfWeek?.ordinal?.plus(1)
