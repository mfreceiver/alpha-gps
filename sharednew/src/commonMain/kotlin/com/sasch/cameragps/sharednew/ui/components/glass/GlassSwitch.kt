package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.AlphaColors
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme

/** Pure colours of a [GlassSwitch]: capsule track, round thumb, white rim alpha of the track. */
@Immutable
data class GlassSwitchColors(val track: Color, val thumb: Color, val rimAlpha: Float)

/**
 * On: primary track with an onPrimary thumb (≥ 3:1 both themes). Off: a pale glass track (onSurface
 * veil + white rim) with an onSurfaceVariant thumb, so the off state never looks like a hole.
 */
fun glassSwitchColors(checked: Boolean, colors: AlphaColors): GlassSwitchColors =
    if (checked) GlassSwitchColors(colors.primary, colors.onPrimary, if (colors.isDark) 0.30f else 0.45f)
    else GlassSwitchColors(
        track = colors.onSurface.copy(alpha = if (colors.isDark) 0.16f else 0.10f),
        thumb = colors.onSurfaceVariant,
        rimAlpha = if (colors.isDark) 0.18f else 0.70f,
    )

/**
 * Glass-style on/off switch (design v3 has no Material switch). With [onCheckedChange] null it is
 * purely visual — put the toggle on the whole row (`Modifier.toggleable(role = Role.Switch)`), as
 * settings rows do. Otherwise it is its own 48 dp switch target.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = glassSwitchColors(checked, AlphaTheme.colors)
    val thumbOffset by animateDpAsState(if (checked) 18.dp else 0.dp, tween(140), label = "glassSwitch")
    val toggle = if (onCheckedChange != null) {
        Modifier.size(48.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Box(modifier.then(toggle).alpha(if (enabled) 1f else 0.38f), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(width = 44.dp, height = 26.dp)
                .background(c.track, AlphaShapes.capsule)
                .border(1.dp, Color.White.copy(alpha = c.rimAlpha), AlphaShapes.capsule)
                .padding(3.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.offset(x = thumbOffset).size(20.dp)
                    .dropShadow(CircleShape, Shadow(radius = 3.dp, color = Color.Black.copy(alpha = 0.22f), offset = DpOffset(0.dp, 1.dp)))
                    .background(c.thumb, CircleShape)
            )
        }
    }
}
