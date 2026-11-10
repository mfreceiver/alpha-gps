package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.AlphaColors
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.GlassControlColors
import com.sasch.cameragps.sharednew.ui.theme.GlassHighlight
import com.sasch.cameragps.sharednew.ui.theme.GlassTokens

/** Check mark (✓) drawn as a stroke — decorative, the owner carries the semantics. */
@Composable
fun CheckGlyph(color: Color, modifier: Modifier = Modifier, strokeWidth: Dp = 2.dp) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.75f)
            lineTo(w * 0.84f, h * 0.28f)
        }
        drawPath(path, color, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Mixed-state mark (–) of an indeterminate [GlassCheckbox]. */
@Composable
private fun DashGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawLine(
            color, Offset(size.width * 0.25f, size.height / 2f), Offset(size.width * 0.75f, size.height / 2f),
            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// N2 GlassRadio
// ---------------------------------------------------------------------------------------------

/** Pure (ring, dot) colours of a [GlassRadio]; dot is [Color.Transparent] when not selected. */
fun glassRadioColors(selected: Boolean, colors: AlphaColors): Pair<Color, Color> =
    if (selected) colors.accent to colors.accent else colors.textSecondary to Color.Transparent

/**
 * Radio mark (N2): 20 dp ring, accent inner dot when [selected]. With [onClick] null it is visual
 * only — make the whole row `Modifier.selectable(selected, role = Role.RadioButton)` inside a
 * `selectableGroup()` (preferred). Otherwise it is its own 48 dp target.
 */
@Composable
fun GlassRadio(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val (ring, dot) = glassRadioColors(selected, AlphaTheme.colors)
    val click = if (onClick != null) {
        Modifier.size(AlphaDimens.minTouchTarget).selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            interactionSource = null,
            indication = ripple(bounded = false, radius = 20.dp),
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Box(modifier.then(click).alpha(if (enabled) 1f else 0.38f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 2.dp.toPx()
            drawCircle(ring, radius = size.minDimension / 2f - stroke / 2f, style = Stroke(stroke))
            if (dot != Color.Transparent) drawCircle(dot, radius = 5.dp.toPx())
        }
    }
}

// ---------------------------------------------------------------------------------------------
// N3 GlassCheckbox / GlassTileAction
// ---------------------------------------------------------------------------------------------

/** Pure: the state after a tap — off → on, on → off, indeterminate ("some selected") → on. */
fun nextCheckState(state: ToggleableState): ToggleableState = when (state) {
    ToggleableState.Off -> ToggleableState.On
    ToggleableState.On -> ToggleableState.Off
    ToggleableState.Indeterminate -> ToggleableState.On
}

/** Pure colours of a [GlassCheckbox] box. [rim] null = no rim. */
@Immutable
data class GlassCheckboxColors(val fill: Color, val rim: Color?, val glyph: Color)

/**
 * Checked / indeterminate: solid accent fill + onPrimary glyph. Unchecked on a photo: Viewer glass
 * (dark translucent) with a white rim so it reads on any photo; unchecked on a theme surface: a
 * textSecondary ring (≥ 3:1).
 */
fun glassCheckboxColors(state: ToggleableState, colors: AlphaColors, onPhoto: Boolean): GlassCheckboxColors =
    when {
        state != ToggleableState.Off ->
            GlassCheckboxColors(GlassControlColors.accentFill(colors), GlassHighlight.copy(alpha = 0.55f), GlassControlColors.glyph(colors))
        onPhoto -> GlassCheckboxColors(GlassTokens.Viewer.fill, GlassHighlight.copy(alpha = 0.90f), Color.Transparent)
        else -> GlassCheckboxColors(Color.Transparent, colors.textSecondary, Color.Transparent)
    }

private val CheckboxShape = RoundedCornerShape(7.dp)

@Composable
private fun CheckboxBox(state: ToggleableState, onPhoto: Boolean) {
    val c = glassCheckboxColors(state, AlphaTheme.colors, onPhoto)
    Box(
        Modifier.size(22.dp)
            .background(c.fill, CheckboxShape)
            .then(if (c.rim != null) Modifier.border(if (state == ToggleableState.Off && !onPhoto) 2.dp else 1.dp, c.rim, CheckboxShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ToggleableState.On -> CheckGlyph(c.glyph, Modifier.size(16.dp))
            ToggleableState.Indeterminate -> DashGlyph(c.glyph, Modifier.size(16.dp))
            ToggleableState.Off -> Unit
        }
    }
}

/**
 * Checkbox (N3): 22 dp rounded square (radius 7). [onPhoto] = selection badge on a photo
 * thumbnail (Viewer glass + white rim when unchecked). With [onStateChange] null it is visual only
 * (put `Modifier.toggleable` / `triStateToggleable` on the cell); otherwise it is its own 48 dp
 * target and receives [nextCheckState].
 */
@Composable
fun GlassCheckbox(
    state: ToggleableState,
    onStateChange: ((ToggleableState) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onPhoto: Boolean = false,
) {
    val click = if (onStateChange != null) {
        Modifier.size(AlphaDimens.minTouchTarget).triStateToggleable(
            state = state,
            enabled = enabled,
            role = Role.Checkbox,
            interactionSource = null,
            indication = ripple(bounded = false, radius = 20.dp),
            onClick = { onStateChange(nextCheckState(state)) },
        )
    } else {
        Modifier
    }
    Box(modifier.then(click).alpha(if (enabled) 1f else 0.38f), contentAlignment = Alignment.Center) {
        CheckboxBox(state, onPhoto)
    }
}

/** Boolean convenience overload of [GlassCheckbox]. */
@Composable
fun GlassCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onPhoto: Boolean = false,
) = GlassCheckbox(
    state = ToggleableState(checked),
    onStateChange = onCheckedChange?.let { cb -> { s: ToggleableState -> cb(s == ToggleableState.On) } },
    modifier = modifier,
    enabled = enabled,
    onPhoto = onPhoto,
)

/**
 * Small action badge on a photo tile (N3, e.g. the magnifier "preview"): same 22 dp Viewer-glass
 * square as an unchecked [GlassCheckbox] with a 15 dp onPhoto icon, inside a 48 dp touch target.
 */
@Composable
fun GlassTileAction(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = glassCheckboxColors(ToggleableState.Off, AlphaTheme.colors, onPhoto = true)
    Box(
        modifier
            .size(AlphaDimens.minTouchTarget)
            .clickable(
                role = Role.Button,
                onClickLabel = null,
                interactionSource = null,
                indication = ripple(bounded = false, radius = 20.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(22.dp).background(c.fill, CheckboxShape).border(1.dp, c.rim ?: Color.Transparent, CheckboxShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(15.dp),
                colorFilter = ColorFilter.tint(AlphaTheme.colors.onPhoto),
            )
        }
    }
}

/** Round icon glyph helper: an [icon] tinted [tint] at [size] (decorative). */
@Composable
internal fun TintedIcon(icon: Painter, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}
