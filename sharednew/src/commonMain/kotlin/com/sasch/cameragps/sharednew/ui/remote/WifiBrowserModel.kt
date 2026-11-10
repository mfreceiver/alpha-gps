package com.sasch.cameragps.sharednew.ui.remote

import com.sasch.cameragps.sharednew.remote.wifi.UNKNOWN_DATE_KEY
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteFailure
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import androidx.compose.ui.graphics.Color
import kotlinx.datetime.LocalDate
import kotlin.math.abs

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
 * Grid view preferences (persisted by the app). Default: small tiles and no tags — the format and
 * size tags are each opt-in. (The former protection switch is gone; its stored key is ignored.)
 */
data class WifiGridOptions(
    val size: String = WifiGridSize.Small.name,
    val showFormat: Boolean = false,
    val showSize: Boolean = false,
) {
    internal val gridSize: WifiGridSize
        get() = WifiGridSize.entries.firstOrNull { it.name == size } ?: WifiGridSize.Small

    internal fun withSize(size: WifiGridSize) = copy(size = size.name)
}

// ---- Capture tags ----------------------------------------------------------------------------

/**
 * The grid's size tag: megabytes (binary MB) with one decimal, rounded half up, **without** the unit
 * ("6.6"); anything below 0.1 MB reads "<0.1". Null for an unknown (zero) size — no tag then.
 * The number is [captureDownloadSize] — what a download under the current default format fetches.
 */
internal fun formatSizeTag(bytes: Long): String? {
    if (bytes <= 0) return null
    val mb = 1024L * 1024L
    if (bytes * 10 < mb) return "<0.1"
    val tenths = (bytes * 10 + mb / 2) / mb
    return "${tenths / 10}.${tenths % 10}"
}

// ---- Grid tile layout --------------------------------------------------------------------------

/**
 * What sits in the corners of a grid tile. Every element is anchored to the tile's square *frame*,
 * never to the fitted photo inside it, so portrait and landscape shots line up the same way.
 */
internal enum class TileElement { Preview, SizeTag, FormatTag, Checkbox }

internal enum class TileCorner { TopStart, TopEnd, BottomStart, BottomEnd }

/** Preview (magnifier) top-left, size top-right, format bottom-left, check box bottom-right. */
internal fun tileCorner(element: TileElement): TileCorner = when (element) {
    TileElement.Preview -> TileCorner.TopStart
    TileElement.SizeTag -> TileCorner.TopEnd
    TileElement.FormatTag -> TileCorner.BottomStart
    TileElement.Checkbox -> TileCorner.BottomEnd
}

/** Touch target of the preview button and the check box (the grid gesture uses the same size). */
internal const val TILE_ACTION_DP = 40

/** Visible size of the preview button and the check box inside their touch targets. */
internal const val TILE_BADGE_DP = 22

/** Gap between every corner element's visible part and the frame edges (the same on all four). */
internal const val TILE_CORNER_INSET_DP = 6

/**
 * Fill of a tile's square frame, visible around a photo fitted inside it: a pale glass-like veil
 * (white based, never black) so the uncovered parts read as glass rather than as dark borders.
 */
internal fun tileFrameColor(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f)

// ---- Thumbnail letterbox -----------------------------------------------------------------------

/** Black bars thinner than this are not treated as letterboxing. */
internal const val LETTERBOX_MIN_BAR_PX = 2

/** A pixel this dark (every channel ≤ 20 of 255) counts as letterbox black. */
internal fun isLetterboxBlack(argb: Int): Boolean =
    (argb shr 16 and 0xff) <= 20 && (argb shr 8 and 0xff) <= 20 && (argb and 0xff) <= 20

/**
 * Camera thumbnails (PTP GetThumb, 160×120) carry baked-in black bars when the photo is not 4:3;
 * fitted into the frame those would read as black borders. Returns the picture's span along one
 * axis of [length] lines, given which lines are entirely black: the bars are only trimmed when both
 * sides have one (≥ [LETTERBOX_MIN_BAR_PX], within 2 px of each other — the camera centres the
 * picture) and at least half of the image remains; otherwise the whole span is kept.
 */
internal fun letterboxSpan(length: Int, isBlackLine: (Int) -> Boolean): IntRange {
    var start = 0
    while (start < length && isBlackLine(start)) start++
    var end = length
    while (end > start && isBlackLine(end - 1)) end--
    val leading = start
    val trailing = length - end
    val symmetric = leading >= LETTERBOX_MIN_BAR_PX && trailing >= LETTERBOX_MIN_BAR_PX &&
        abs(leading - trailing) <= 2
    return if (symmetric && (end - start) * 2 >= length) start until end else 0 until length
}

// ---- Day list --------------------------------------------------------------------------------

/** Parses a "yyyy-MM-dd" day key; null for the unknown group or anything malformed. */
internal fun dateKeyToLocalDate(key: String): LocalDate? {
    if (key == UNKNOWN_DATE_KEY) return null
    return runCatching { LocalDate.parse(key) }.getOrNull()
}

/**
 * ISO weekday 1 (Monday) … 7 (Sunday) of a day key, null when unknown. The day row's subtitle is only
 * this weekday — no relative words ("today" / "yesterday"); the unknown group has no subtitle.
 */
internal fun dateKeyWeekday(key: String): Int? = dateKeyToLocalDate(key)?.dayOfWeek?.ordinal?.plus(1)
