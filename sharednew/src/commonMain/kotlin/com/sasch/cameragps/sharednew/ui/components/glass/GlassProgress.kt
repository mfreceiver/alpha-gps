package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.AlphaColors
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme

/** Diameter of a [GlassProgressCircle] (N8): inline in a capsule, standalone, page-level. */
enum class GlassProgressSize(val diameter: Dp) { Small(16.dp), Medium(24.dp), Large(36.dp) }

/** Pure: determinate progress → sweep angle in degrees (clamped to 0..360; NaN → 0). */
fun progressSweep(progress: Float): Float = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f) * 360f

/** Pure (track, indicator) colours: accent on a theme surface, onPhoto over a photo / Viewer glass. */
fun glassProgressColors(colors: AlphaColors, onPhoto: Boolean): Pair<Color, Color> =
    if (onPhoto) colors.onPhoto.copy(alpha = 0.24f) to colors.onPhoto
    else colors.textPrimary.copy(alpha = 0.12f) to colors.accent

/**
 * Circular progress (N8): 2.5 dp stroke, textPrimary 12 % track, accent arc (onPhoto with
 * [onPhoto]). [progress] null = indeterminate spinner. Size it with `Modifier.size(...)` or
 * [GlassProgressSize]. Announced as a progress bar.
 *
 * @param color overrides the indicator colour (e.g. a capsule's label colour).
 */
@Composable
fun GlassProgressCircle(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = Color.Unspecified,
    onPhoto: Boolean = false,
    size: GlassProgressSize = GlassProgressSize.Medium,
    strokeWidth: Dp = 2.5.dp,
) {
    val (track, accent) = glassProgressColors(AlphaTheme.colors, onPhoto)
    val indicator = if (color.isSpecified) color else accent
    val trackColor = if (color.isSpecified) color.copy(alpha = color.alpha * 0.22f) else track
    val rotation = if (progress == null) {
        val transition = rememberInfiniteTransition(label = "glassProgress")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "glassProgressAngle",
        )
        angle
    } else {
        0f
    }
    Canvas(
        modifier
            .size(size.diameter)
            .progressSemantics(progress),
    ) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
        val start = if (progress == null) rotation - 90f else -90f
        val sweep = if (progress == null) 100f else progressSweep(progress)
        drawArc(
            indicator, start, sweep, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/**
 * Linear progress (N8): 4 dp capsule, same colours as [GlassProgressCircle]. [progress] null =
 * indeterminate (a 30 % segment sweeping across).
 */
@Composable
fun GlassProgressLinear(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onPhoto: Boolean = false,
) {
    val (track, accent) = glassProgressColors(AlphaTheme.colors, onPhoto)
    val phase = if (progress == null) {
        val transition = rememberInfiniteTransition(label = "glassLinear")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
            label = "glassLinearPhase",
        )
        p
    } else {
        0f
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .progressSemantics(progress),
    ) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(track, cornerRadius = r)
        if (progress == null) {
            val w = size.width * 0.3f
            val x = (size.width + w) * phase - w
            val left = x.coerceAtLeast(0f)
            val right = (x + w).coerceAtMost(size.width)
            if (right > left) drawRoundRect(accent, topLeft = Offset(left, 0f), size = Size(right - left, size.height), cornerRadius = r)
        } else {
            val w = size.width * progressSweep(progress) / 360f
            if (w > 0f) drawRoundRect(accent, size = Size(w, size.height), cornerRadius = r)
        }
    }
}

private fun Modifier.progressSemantics(progress: Float?): Modifier = semantics {
    progressBarRangeInfo = if (progress == null) {
        ProgressBarRangeInfo.Indeterminate
    } else {
        ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
    }
}
