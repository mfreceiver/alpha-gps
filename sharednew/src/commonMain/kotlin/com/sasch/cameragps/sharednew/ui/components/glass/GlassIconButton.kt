package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
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
import com.sasch.cameragps.sharednew.ui.theme.AlphaColors
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel

/**
 * Emphasis of an icon-only button (design page 7):
 * - [Standard]: top-bar / toolbar icons (back, close, refresh, view options, Ⓘ, select all).
 * - [Primary]: an icon that is the active / selected choice.
 * - [Subtle]: low-key 20 dp icon at 62 % (断开蓝牙) — still a 48 dp touch target.
 * - [Danger]: destructive icon in a toolbar (delete selection).
 */
enum class IconEmphasis { Standard, Primary, Subtle, Danger }

/** Pure tint resolution; [onViewer] = white chrome over a photo. */
fun iconButtonTint(emphasis: IconEmphasis, colors: AlphaColors, enabled: Boolean, onViewer: Boolean = false): Color {
    val base = when (emphasis) {
        IconEmphasis.Standard -> if (onViewer) Color.White else colors.onSurfaceVariant
        IconEmphasis.Primary -> if (onViewer) Color.White else colors.primary
        IconEmphasis.Subtle -> (if (onViewer) Color.White else colors.onSurfaceVariant).copy(alpha = 0.62f)
        IconEmphasis.Danger -> colors.error
    }
    return if (enabled) base else base.copy(alpha = base.alpha * 0.38f)
}

/**
 * Icon-only button. [contentDescription] is mandatory for anything actionable (pass null only when a
 * visible label right next to it already describes the same action); a long-press tooltip shows it.
 *
 * @param touchTarget clickable size, ≥ 48 dp by default; clamped to at least 40 dp for dense rows.
 * @param container optional round glass backdrop (G2 floating button over content / GV over a photo).
 * @param tint overrides the [emphasis] colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val button: @Composable () -> Unit = {
        Box(
            modifier = modifier
                .size(target)
                .then(if (container != null) Modifier.glass(container, CircleShape) else Modifier.clip(CircleShape))
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = null,
                    interactionSource = null,
                    indication = ripple(bounded = true),
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
    if (contentDescription.isNullOrEmpty()) {
        button()
    } else {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(contentDescription) } },
            state = rememberTooltipState(),
        ) {
            button()
        }
    }
}
