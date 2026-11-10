package com.sasch.cameragps.sharednew.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Corner shapes of design v3 (page 4). */
object AlphaShapes {
    /** G1 card / settings group. */
    val card = RoundedCornerShape(28.dp)
    /** List row (device row, settings row). */
    val row = RoundedCornerShape(24.dp)
    /** Inset block inside a card (status cells, detail panes). */
    val inset = RoundedCornerShape(18.dp)
    /** G3 dialog. */
    val dialog = RoundedCornerShape(28.dp)
    /** Popover / menu. */
    val popover = RoundedCornerShape(24.dp)
    /** Bottom sheet (top corners only). */
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    /** Information tag: small square-ish corners — never a capsule (capsules are buttons). */
    val tag = RoundedCornerShape(4.dp)
    /** Operation buttons and floating bars. */
    val capsule = CircleShape
}

/** Spacing and size constants of design v3 (page 4). */
object AlphaDimens {
    val screenMargin = 16.dp
    val cardPadding = 20.dp
    val cardGap = 12.dp
    val rowMinHeight = 72.dp
    /** Minimum touch target of any tappable element. */
    val minTouchTarget = 48.dp
    /** Hard floor for dense icon buttons (toolbars inside a card); prefer [minTouchTarget]. */
    val minDenseTouchTarget = 40.dp
    val dialogWidth = 318.dp
    val dialogPadding = 24.dp
    val dialogButtonGap = 10.dp
    val buttonHeight = 48.dp
    val primaryButtonHeight = 56.dp
    val heroButtonHeight = 68.dp
    val iconSize = 24.dp
}
