package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import com.sasch.cameragps.sharednew.ui.theme.GlassTintRole
import com.sasch.cameragps.sharednew.ui.theme.GlassTints

/** Pure: the effective selected index — clamped into `0 until count`; -1 when there are no options. */
fun segmentedSelection(count: Int, requested: Int): Int = if (count <= 0) -1 else requested.coerceIn(0, count - 1)

/**
 * Pure: (start offset, width) of the selection slider at [position] (a segment index, fractional
 * while animating) of [count] equal segments inside a container of [totalWidth] with [padding] on
 * each side. Out-of-range positions give (padding, 0) — no slider.
 */
fun segmentSliderBounds(position: Float, count: Int, totalWidth: Float, padding: Float): Pair<Float, Float> {
    if (count <= 0 || position.isNaN() || position < 0f || position > count - 1f) return padding to 0f
    val inner = (totalWidth - padding * 2f).coerceAtLeast(0f)
    val w = inner / count
    return (padding + w * position) to w
}

private val SegmentPadding = 3.dp

/**
 * Segmented control (N7): capsule container (textPrimary 6 %), an accent-tinted glass slider under
 * the selected segment, a Label per segment, ≥ 40 dp high (48 dp by default). Each segment is a
 * radio button in a selectable group for TalkBack. Replaces `SingleChoiceSegmentedButtonRow` / tab
 * rows with few options.
 */
@Composable
fun GlassSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
) {
    val colors = AlphaTheme.colors
    val selected = segmentedSelection(options.size, selectedIndex)
    val tint = GlassTints.of(GlassTintRole.Accent, colors)
    val position by animateFloatAsState(selected.toFloat().coerceAtLeast(0f), tween(180), label = "segmented")
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height.coerceAtLeast(40.dp))
            .clip(AlphaShapes.capsule)
            .background(colors.textPrimary.copy(alpha = 0.06f))
            .alpha(if (enabled) 1f else 0.38f),
    ) {
        val count = options.size
        if (count == 0) return@BoxWithConstraints
        val (start, width) = segmentSliderBounds(position, count, maxWidth.value, SegmentPadding.value)
        if (selected >= 0 && width > 0f) {
            Box(
                Modifier
                    .offset(x = start.dp)
                    .padding(vertical = SegmentPadding)
                    .width(width.dp)
                    .fillMaxHeight()
                    .glass(GlassLevel.Float, AlphaShapes.capsule, shadow = false, tint = tint.tint),
            )
        }
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(SegmentPadding).selectableGroup()) {
            options.forEachIndexed { i, label ->
                val isSelected = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(AlphaShapes.capsule)
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { if (!isSelected) onSelect(i) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = AlphaType.Label,
                        color = if (isSelected) tint.content else colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}
