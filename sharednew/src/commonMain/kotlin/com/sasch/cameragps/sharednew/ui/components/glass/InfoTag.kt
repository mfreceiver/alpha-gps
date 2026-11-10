package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.AlphaColors
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassHighlight
import com.sasch.cameragps.sharednew.ui.theme.GlassTokens

/** Where an [InfoTag] sits: on a theme surface / glass, or over a photo. */
enum class InfoTagStyle { OnSurface, OnPhoto }

/** Pure colours of an [InfoTag] (N13 GlassTag): [background] (may be transparent), 1 dp [stroke], [content]. */
@Immutable
data class InfoTagColors(val background: Color, val stroke: Color, val content: Color)

/**
 * - [InfoTagStyle.OnSurface]: no fill — Tag text inside a 1 dp outline (textSecondary 32 % light /
 *   highlight 24 % dark), so it reads as a label on glass and never as a button. Text: textSecondary
 *   (light) / textPrimary (dark keeps AA over the brightest orb), or [content] (e.g. success).
 * - [InfoTagStyle.OnPhoto]: Viewer glass fill (dark translucent, unblurred: a photo grid is not a
 *   blur source) + white 22 % rim + onPhoto text — ≥ 5:1 even on a white photo.
 */
fun infoTagColors(style: InfoTagStyle, colors: AlphaColors, content: Color = Color.Unspecified): InfoTagColors =
    when (style) {
        InfoTagStyle.OnPhoto -> InfoTagColors(GlassTokens.Viewer.fill, GlassHighlight.copy(alpha = 0.22f), colors.onPhoto)
        InfoTagStyle.OnSurface -> InfoTagColors(
            background = Color.Transparent,
            stroke = if (colors.isDark) GlassHighlight.copy(alpha = 0.24f) else colors.textSecondary.copy(alpha = 0.32f),
            content = if (content.isSpecified) content else if (colors.isDark) colors.textPrimary else colors.textSecondary,
        )
    }

/**
 * Information tag / GlassTag (N13: RAW / JPEG / 已下载 / ★ 4 …): Tag text with a 1 dp outline, 4 dp
 * corners, no shadow, **not clickable** — it must never look like a capsule button. Its text is read
 * as part of the owning row / cell (merge the owner's semantics); [icon] is decorative.
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
    val c = infoTagColors(style, AlphaTheme.colors, contentColor)
    Row(
        modifier = modifier
            .heightIn(min = 17.dp)
            .background(c.background, AlphaShapes.tag)
            .border(1.dp, c.stroke, AlphaShapes.tag)
            .padding(PaddingValues(horizontal = 5.dp, vertical = 1.5.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(12.dp), colorFilter = ColorFilter.tint(c.content))
        }
        Text(text = text, style = AlphaType.Tag, color = c.content, maxLines = 1)
    }
}
