package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.a1_address_hidden
import cameragps.sharednew.generated.resources.a1_address_hide
import cameragps.sharednew.generated.resources.a1_address_show
import com.sasch.cameragps.sharednew.ui.theme.AlphaColors
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import org.jetbrains.compose.resources.stringResource

// ---------------------------------------------------------------------------------------------
// N10 StatusBadge
// ---------------------------------------------------------------------------------------------

/**
 * Read-only status badge (N10, e.g. "✓ 已配对" / "● 未连接"): ✓ for [StatusTone.Success] or a dot,
 * then a Label word in the tone's text colour, on a G2 capsule. Not clickable; one TalkBack node.
 * Use [CapsuleButton] for anything actionable.
 */
@Composable
fun StatusBadge(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    showCheck: Boolean = tone == StatusTone.Success,
) {
    val colors = AlphaTheme.colors
    GlassSurface(
        modifier = modifier.heightIn(min = 32.dp).semantics(mergeDescendants = true) { },
        level = GlassLevel.Float,
        shape = AlphaShapes.capsule,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showCheck) {
                CheckGlyph(statusIconColor(tone, colors), Modifier.size(14.dp))
            } else {
                StatusDot(tone, Modifier.size(12.dp))
            }
            Text(text, style = AlphaType.Label, color = statusToneColor(tone, colors, forText = true), maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// N15 GlassWarningCard
// ---------------------------------------------------------------------------------------------

/** Pure (edge stroke, faint tint) of a [GlassWarningCard] for [tone]. */
fun warningCardColors(tone: StatusTone, colors: AlphaColors): Pair<Color, Color> {
    val edge = statusIconColor(tone, colors)
    return edge to edge.copy(alpha = if (colors.isDark) 0.10f else 0.07f)
}

/**
 * Warning / notice card (N15): a G1 card with a 4 dp tone-coloured leading edge and a faint tone
 * tint, an optional icon, a Heading [title], Caption-sized [message] (Body for readability) and an
 * optional Tonal capsule action. Replaces ad-hoc `errorContainer` cards (container colours are not
 * text backgrounds).
 */
@Composable
fun GlassWarningCard(
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Warning,
    icon: Painter? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = AlphaTheme.colors
    val (edge, tint) = warningCardColors(tone, colors)
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            // Drawn after the glass so the edge sits on top of the frosted fill.
            .drawWithContent {
                drawContent()
                val w = 4.dp.toPx()
                drawRoundRect(
                    edge,
                    // Only along the straight part of the 28 dp-radius side, never outside the shape.
                    topLeft = Offset(0f, 28.dp.toPx()),
                    size = Size(w, (size.height - 56.dp.toPx()).coerceAtLeast(w)),
                    cornerRadius = CornerRadius(w / 2f),
                )
            },
        level = GlassLevel.Card,
        shape = AlphaShapes.card,
        tint = tint,
        contentPadding = PaddingValues(start = 22.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) TintedIcon(icon, edge, 22.dp)
                Text(title, style = AlphaType.Heading, color = colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
            }
            if (message != null) {
                Text(message, style = AlphaType.Body, color = colors.textSecondary)
            }
            if (actionLabel != null && onAction != null) {
                CapsuleButton(actionLabel, onAction, role = CapsuleRole.Tonal, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// N16 GlassIconTile
// ---------------------------------------------------------------------------------------------

/**
 * Round glass icon tile (N16) at the start of a list row / card header: G2 circle, [icon] tinted by
 * [tone] (non-text colour). Decorative unless [contentDescription] is given.
 */
@Composable
fun GlassIconTile(
    icon: Painter,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Active,
    size: Dp = 44.dp,
    contentDescription: String? = null,
) {
    GlassSurface(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription }
                else Modifier.clearAndSetSemantics { },
            ),
        level = GlassLevel.Float,
        shape = CircleShape,
        contentAlignment = Alignment.Center,
    ) {
        TintedIcon(icon, statusIconColor(tone, AlphaTheme.colors), size * 0.5f)
    }
}

// ---------------------------------------------------------------------------------------------
// N17 RevealValueRow
// ---------------------------------------------------------------------------------------------

/**
 * Pure mask: every letter / digit except the last [visibleSuffix] ones becomes [maskChar];
 * separators (`:`, `-`, spaces …) stay so the shape remains recognisable
 * (`AA:BB:CC:DD:EE:FF`, 2 → `••:••:••:••:••:FF`).
 */
fun maskValue(value: String, visibleSuffix: Int = 0, maskChar: Char = '•'): String {
    val total = value.count { it.isLetterOrDigit() }
    val keepFrom = total - visibleSuffix.coerceAtLeast(0)
    var seen = 0
    return buildString(value.length) {
        value.forEach { ch ->
            if (ch.isLetterOrDigit()) {
                append(if (seen >= keepFrom) ch else maskChar)
                seen++
            } else {
                append(ch)
            }
        }
    }
}

/**
 * A label + sensitive value (N17, e.g. Bluetooth address): masked by default, a Secondary capsule
 * toggles it ("Show" / "Hide"). While masked, TalkBack reads the label and "Hidden" instead of the
 * dots. State is hoisted ([revealed] / [onRevealedChange]) so a re-opened dialog can start hidden.
 */
@Composable
fun RevealValueRow(
    label: String,
    value: String,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    masked: String = maskValue(value),
) {
    val colors = AlphaTheme.colors
    val hidden = stringResource(Res.string.a1_address_hidden)
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.weight(1f).semantics(mergeDescendants = true) {
                if (!revealed) stateDescription = hidden
            },
        ) {
            Text(label, style = AlphaType.Caption, color = colors.textSecondary)
            Text(
                if (revealed) value else masked,
                style = AlphaType.Body,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (revealed) Modifier else Modifier.clearAndSetSemantics { },
            )
        }
        CapsuleButton(
            text = stringResource(if (revealed) Res.string.a1_address_hide else Res.string.a1_address_show),
            onClick = { onRevealedChange(!revealed) },
            role = CapsuleRole.Secondary,
            size = CapsuleSize.Small,
        )
    }
}
