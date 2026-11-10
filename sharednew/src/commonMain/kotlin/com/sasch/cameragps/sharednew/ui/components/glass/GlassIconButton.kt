package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.sasch.cameragps.sharednew.ui.theme.AlphaColors
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel

/**
 * Emphasis of an icon-only button (design page 7):
 * - [Standard]: top-bar / toolbar icons (back, close, refresh, view options, Ⓘ, select all).
 * - [Primary]: an icon that is the active / selected choice.
 * - [Subtle]: low-key 20 dp icon in `textMuted` (断开蓝牙; ≥ 3:1, unlike the old 62 % alpha which
 *   fell to 2.4:1 on dark glass) — still a 48 dp touch target.
 * - [Danger]: destructive icon in a toolbar (delete selection).
 */
enum class IconEmphasis { Standard, Primary, Subtle, Danger }

/** Pure tint resolution; [onViewer] = onPhoto chrome over a photo. */
fun iconButtonTint(emphasis: IconEmphasis, colors: AlphaColors, enabled: Boolean, onViewer: Boolean = false): Color {
    val base = when (emphasis) {
        IconEmphasis.Standard -> if (onViewer) colors.onPhoto else colors.textSecondary
        IconEmphasis.Primary -> if (onViewer) colors.onPhoto else colors.accent
        IconEmphasis.Subtle -> if (onViewer) colors.onPhotoMuted else colors.textMuted
        IconEmphasis.Danger -> colors.danger
    }
    return if (enabled) base else base.copy(alpha = base.alpha * 0.38f)
}

/** How long the long-press tooltip of a [GlassIconButton] stays up. */
const val ICON_TOOLTIP_MILLIS = 1500L

/**
 * Icon-only button. [contentDescription] is mandatory for anything actionable (pass null only when a
 * visible label right next to it already describes the same action); a long press shows it as an
 * **in-page** tooltip (through the [GlassBackground] overlay host — no platform popup window).
 *
 * @param touchTarget clickable size, ≥ 48 dp by default; clamped to at least 40 dp for dense rows.
 * @param container optional round glass backdrop (G2 floating button over content / GV over a photo).
 * @param tint overrides the [emphasis] colour.
 */
@Composable
fun GlassIconButton(
    icon: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasis: IconEmphasis = IconEmphasis.Standard,
    touchTarget: Dp = AlphaDimens.minTouchTarget,
    container: GlassLevel? = null,
    tint: Color = Color.Unspecified,
) {
    val colors = AlphaTheme.colors
    val resolvedTint = if (tint.isSpecified) tint else iconButtonTint(emphasis, colors, enabled, container == GlassLevel.Viewer)
    val iconSize = if (emphasis == IconEmphasis.Subtle) 20.dp else AlphaDimens.iconSize
    val target = touchTarget.coerceAtLeast(AlphaDimens.minDenseTouchTarget)
    val anchor = rememberGlassAnchor()
    var tooltip by remember { mutableStateOf(false) }
    val label = contentDescription?.takeIf { it.isNotEmpty() }
    if (tooltip && label != null) {
        GlassTooltip(label, anchor)
        LaunchedEffect(Unit) {
            delay(ICON_TOOLTIP_MILLIS)
            tooltip = false
        }
    }
    // The caller's modifier sits on the outermost node so parent-data modifiers (`align`,
    // `weight`, …) reach the caller's Box / Row.
    Box(
        modifier = modifier
            .size(target)
            .glassAnchor(anchor)
            .then(if (container != null) Modifier.glass(container, CircleShape) else Modifier.clip(CircleShape))
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = null,
                indication = ripple(bounded = true),
                onLongClick = if (label != null) ({ tooltip = true }) else null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            colorFilter = ColorFilter.tint(resolvedTint),
        )
    }
}
