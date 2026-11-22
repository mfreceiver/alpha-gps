package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme

/** Default diameter of [GlassShutterButton] (double ring). */
val ShutterButtonSize = 72.dp

private val RingStroke = 3.dp
private val RingGap = 5.dp

/** A press is accepted only when the button is enabled and no earlier press is still in flight. */
internal fun shutterButtonClickable(enabled: Boolean, pressing: Boolean): Boolean = enabled && !pressing

/**
 * Round double-ring shutter button of the remote-shooting pages: an outer ring and an inner disc.
 *
 * - [enabled] false dims both rings and ignores taps (control channel not ready).
 * - [pressing] true is the in-flight (press suppressed) state: the disc shrinks and further taps are
 *   ignored, so a double tap cannot fire twice. Semantics read as disabled in both cases.
 * - [onPhoto] true recolours the rings with the `onPhoto*` roles for use over a live-view image;
 *   false uses the accent / muted text roles on glass pages.
 * - [contentDescription] is required (the button has no text); [diameter] is clamped to ≥ 48 dp.
 */
@Composable
fun GlassShutterButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressing: Boolean = false,
    onPhoto: Boolean = false,
    diameter: Dp = ShutterButtonSize,
) {
    val colors = AlphaTheme.colors
    val clickable = shutterButtonClickable(enabled, pressing)
    val ring: Color
    val disc: Color
    if (onPhoto) {
        ring = if (enabled) colors.onPhoto else colors.onPhotoMuted
        disc = when {
            !enabled -> colors.onPhotoMuted
            pressing -> colors.onPhotoSecondary
            else -> colors.onPhoto
        }
    } else {
        ring = if (enabled) colors.accent else colors.textMuted
        disc = when {
            !enabled -> colors.textMuted
            pressing -> colors.accent.copy(alpha = 0.7f)
            else -> colors.accent
        }
    }
    val discScale by animateFloatAsState(if (pressing) 0.78f else 1f, tween(120), label = "shutterPress")
    val target = diameter.coerceAtLeast(AlphaDimens.minTouchTarget)
    Box(
        modifier = modifier
            .size(target)
            .clip(CircleShape)
            .drawBehind {
                val stroke = RingStroke.toPx()
                val outerRadius = this.size.minDimension / 2f - stroke / 2f
                drawCircle(color = ring, radius = outerRadius, center = Offset(this.size.width / 2f, this.size.height / 2f), style = Stroke(width = stroke))
                val discRadius = (this.size.minDimension / 2f - stroke - RingGap.toPx()).coerceAtLeast(0f) * discScale
                drawCircle(color = disc, radius = discRadius, center = Offset(this.size.width / 2f, this.size.height / 2f))
            }
            .clickable(
                interactionSource = null,
                indication = ripple(bounded = true),
                enabled = clickable,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
    )
}
