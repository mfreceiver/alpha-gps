package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.AlphaColors
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTextStyles
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme

/** Where an [InfoTag] sits: on a theme surface, or over a photo (always black 60 % + white). */
enum class InfoTagStyle { OnSurface, OnPhoto }

/** Pure (background, content) colours of an [InfoTag]. */
fun infoTagColors(style: InfoTagStyle, colors: AlphaColors, content: Color = Color.Unspecified): Pair<Color, Color> =
    when (style) {
        InfoTagStyle.OnPhoto -> Color.Black.copy(alpha = 0.60f) to Color.White
        InfoTagStyle.OnSurface ->
            colors.onSurface.copy(alpha = if (colors.isDark) 0.14f else 0.08f) to
                // Dark: onSurface instead of onSurfaceVariant keeps AA (4.4 → 5.8) over the brightest glow.
                (if (content.isSpecified) content else if (colors.isDark) colors.onSurface else colors.onSurfaceVariant)
    }

/**
 * Information tag (RAW / JPEG / 已下载 / ★ 4 …): flat fill, 4 dp corners, no stroke, no shadow,
 * **not clickable** — it must never look like a capsule button. Its text is read as part of the
 * owning row / cell (merge the owner's semantics); [icon] is decorative.
 *
 * @param contentColor text / icon colour on [InfoTagStyle.OnSurface] (e.g. success green); ignored
 *   on photos.
 */
@Composable
fun InfoTag(
    text: String,
    modifier: Modifier = Modifier,
    style: InfoTagStyle = InfoTagStyle.OnSurface,
    contentColor: Color = Color.Unspecified,
    icon: Painter? = null,
) {
    val (background, content) = infoTagColors(style, AlphaTheme.colors, contentColor)
    Row(
        modifier = modifier
            .heightIn(min = 17.dp)
            .background(background, AlphaShapes.tag)
            .padding(PaddingValues(horizontal = 5.dp, vertical = 1.5.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(12.dp), colorFilter = ColorFilter.tint(content))
        }
        Text(text = text, style = AlphaTextStyles.tag, color = content, maxLines = 1)
    }
}
