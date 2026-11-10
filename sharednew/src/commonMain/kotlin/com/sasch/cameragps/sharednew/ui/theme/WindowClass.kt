package com.sasch.cameragps.sharednew.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.isSpecified

/**
 * Window classes of design v3 (page 21). Decided by the **window** size (multi-window and foldables
 * included), never by device type.
 */
enum class WindowClass {
    /** A — tall portrait: width < 600 dp and height ≥ 480 dp. Single column, bottom action bar. */
    Portrait,
    /** B — near-square / large: width ≥ 600 dp and height ≥ 480 dp. Two panes or a wider column. */
    NearSquare,
    /** C — landscape / short: height < 480 dp. Side-by-side, compact chrome. */
    Landscape,
}

const val WINDOW_WIDTH_BREAKPOINT_DP = 600f
const val WINDOW_HEIGHT_BREAKPOINT_DP = 480f

/** Pure classification; height is checked first so a short wide window is always [WindowClass.Landscape]. */
fun windowClassOf(widthDp: Float, heightDp: Float): WindowClass = when {
    heightDp < WINDOW_HEIGHT_BREAKPOINT_DP -> WindowClass.Landscape
    widthDp < WINDOW_WIDTH_BREAKPOINT_DP -> WindowClass.Portrait
    else -> WindowClass.NearSquare
}

/** Class of the current window; [WindowClass.Portrait] until the window size is known. */
@Composable
fun currentWindowClass(): WindowClass {
    val size = LocalWindowInfo.current.containerDpSize
    if (!size.isSpecified || size.width.value <= 0f || size.height.value <= 0f) return WindowClass.Portrait
    return windowClassOf(size.width.value, size.height.value)
}
