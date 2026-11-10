package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.AlphaColors
import com.sasch.cameragps.sharednew.ui.theme.AlphaTextStyles
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme

/**
 * Semantic tone of a status (dot, icon, short status word). [Accent] is the secondary teal accent
 * for informational data (not a state).
 */
enum class StatusTone { Neutral, Active, Success, Warning, Error, Accent }

/** Pure tone → colour (text-safe: warning uses the darker [AlphaColors.warningText]). */
fun statusToneColor(tone: StatusTone, colors: AlphaColors, forText: Boolean = true): Color = when (tone) {
    StatusTone.Neutral -> colors.onSurfaceVariant
    StatusTone.Active -> colors.primary
    StatusTone.Success -> colors.success
    StatusTone.Warning -> if (forText) colors.warningText else colors.warning
    StatusTone.Error -> colors.error
    StatusTone.Accent -> colors.accentTeal
}

/**
 * Tint of a [StatusItem] icon. Icons are small glyphs on glass, so they take the text-safe colour
 * (light-theme amber is too pale for a 20 dp glyph); [StatusTone.Neutral] stays grey.
 */
fun statusIconColor(tone: StatusTone, colors: AlphaColors): Color = statusToneColor(tone, colors, forText = true)

/**
 * Status dot: 9 dp, with a 22 % halo for [StatusTone.Success] only (design `lib3.dot`). Decorative:
 * the adjacent text carries the state.
 */
@Composable
fun StatusDot(tone: StatusTone, modifier: Modifier = Modifier) {
    val color = statusToneColor(tone, AlphaTheme.colors, forText = false)
    val halo = tone == StatusTone.Success
    Canvas(modifier.size(16.dp)) {
        val r = 4.5.dp.toPx()
        if (halo) drawCircle(color.copy(alpha = 0.22f), radius = r + 3.5.dp.toPx())
        drawCircle(color, radius = r)
    }
}

/**
 * Headline status line: dot + title (+ optional supporting line), e.g. "● GPS 已定位 / 31.23° N …".
 * Read as one node by TalkBack.
 */
@Composable
fun StatusLine(
    title: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(tone)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = AlphaTheme.colors.onSurface,
            )
            if (supportingText != null) {
                Text(supportingText, style = MaterialTheme.typography.bodySmall, color = AlphaTheme.colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * One cell of a [StatusRow]: optional icon, the changing [value], and a **fixed** item name
 * [label] (已写入 / 失败 / 最近 …). [tone] colours the value (and icon); [iconTone] colours only
 * the icon ("tinted metric" — the value stays in the normal text colour), null = follow [tone].
 */
@Immutable
data class StatusItem(
    val label: String,
    val value: String,
    val icon: Painter? = null,
    val tone: StatusTone = StatusTone.Neutral,
    val iconTone: StatusTone? = null,
)

/**
 * Status row: equal-width inset cells (normally three) — icon / value / fixed item name. Each cell
 * is announced as "label: value". No dividers between cells. Value and label wrap to at most two
 * lines (narrow screens, large font scale) before they ellipsize.
 */
@Composable
fun StatusRow(items: List<StatusItem>, modifier: Modifier = Modifier) {
    val colors = AlphaTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            val valueColor = if (item.tone == StatusTone.Neutral) colors.onSurface else statusToneColor(item.tone, colors)
            GlassInsetBlock(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 72.dp)
                    .clearAndSetSemantics { contentDescription = "${item.label}: ${item.value}" },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (item.icon != null) {
                        Image(
                            item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            colorFilter = ColorFilter.tint(statusIconColor(item.iconTone ?: item.tone, colors)),
                        )
                    }
                    Text(
                        item.value,
                        style = AlphaTextStyles.statusValue,
                        color = valueColor,
                        textAlign = TextAlign.Center,
                        // Two lines so a large font scale wraps instead of cutting the value.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.label,
                        style = AlphaTextStyles.statusLabel,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
