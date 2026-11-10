package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.AlphaColors
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.GlassControlColors
import com.sasch.cameragps.sharednew.ui.theme.GlassHighlight
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import com.sasch.cameragps.sharednew.ui.theme.GlassTokens

/**
 * Pure colours of a [GlassSwitch]: capsule [track], round [thumb], [rim] stroke of the track and the
 * alpha of the top [sheen] that makes the track read as glass.
 */
@Immutable
data class GlassSwitchColors(val track: Color, val thumb: Color, val rim: Color, val sheen: Float)

/** Track size of a [GlassSwitch] (N1: 52 × 32). */
val GlassSwitchTrackWidth: Dp = 52.dp
val GlassSwitchTrackHeight: Dp = 32.dp
private val SwitchPadding = 4.dp

/**
 * On: accent track ([GlassControlColors.accentFill]) with an onPrimary thumb (white on the light
 * theme's deep blue, navy on the dark theme's light blue — ≥ 6:1 both). Off: a pale textSecondary
 * 12 % track outlined in `textMuted` (≥ 3:1 on every glass level, so the control's boundary stays
 * visible) with a white thumb.
 */
fun glassSwitchColors(checked: Boolean, colors: AlphaColors): GlassSwitchColors =
    if (checked) GlassSwitchColors(
        track = GlassControlColors.accentFill(colors),
        thumb = GlassControlColors.glyph(colors),
        rim = GlassHighlight.copy(alpha = if (colors.isDark) 0.30f else 0.45f),
        sheen = if (colors.isDark) 0.14f else 0.24f,
    )
    else GlassSwitchColors(
        track = colors.textSecondary.copy(alpha = 0.12f),
        thumb = GlassHighlight,
        rim = colors.textMuted,
        sheen = if (colors.isDark) 0.06f else 0.30f,
    )

/**
 * Pure: x offset of the thumb inside the track's padding box — 0 when off, flush right when on.
 * [thumb] grows from 24 to 28 dp while pressed (N1), so the "on" offset shrinks accordingly.
 */
fun switchThumbOffset(checked: Boolean, thumb: Dp): Dp =
    if (checked) GlassSwitchTrackWidth - SwitchPadding * 2 - thumb else 0.dp

/**
 * Glass on/off switch (N1, 52 × 32 track, 24 dp white thumb growing to 28 dp while pressed). With
 * [onCheckedChange] null it is purely visual — put the toggle on the whole row
 * (`Modifier.toggleable(role = Role.Switch)`), as settings rows do. Otherwise it is its own 48 dp
 * switch target.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = glassSwitchColors(checked, AlphaTheme.colors)
    val thumbShadow = GlassTokens.of(GlassLevel.Float, AlphaTheme.colors.isDark).keyShadow.color
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val thumbSize by animateDpAsState(if (pressed && enabled) 28.dp else 24.dp, tween(120), label = "glassSwitchThumb")
    val thumbOffset by animateDpAsState(switchThumbOffset(checked, thumbSize), tween(160), label = "glassSwitch")
    val toggle = if (onCheckedChange != null) {
        Modifier.size(width = 64.dp, height = 48.dp).toggleable(
            value = checked,
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }
    Box(modifier.then(toggle).alpha(if (enabled) 1f else 0.38f), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(width = GlassSwitchTrackWidth, height = GlassSwitchTrackHeight)
                .clip(AlphaShapes.capsule)
                .background(c.track)
                .drawWithCache {
                    val sheen = Brush.verticalGradient(
                        0f to GlassHighlight.copy(alpha = c.sheen),
                        0.6f to Color.Transparent,
                    )
                    onDrawBehind { drawRect(sheen, topLeft = Offset.Zero, size = size) }
                }
                .border(1.dp, c.rim, AlphaShapes.capsule)
                .padding(SwitchPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.offset(x = thumbOffset).size(width = thumbSize, height = 24.dp)
                    .dropShadow(
                        AlphaShapes.capsule,
                        Shadow(radius = 4.dp, color = thumbShadow, offset = DpOffset(0.dp, 1.dp)),
                    )
                    .background(c.thumb, CircleShape),
            )
        }
    }
}
