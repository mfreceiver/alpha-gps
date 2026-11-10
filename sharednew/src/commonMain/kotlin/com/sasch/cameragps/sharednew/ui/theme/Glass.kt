package com.sasch.cameragps.sharednew.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glass levels of design v3 (page 4). Pick by role, not by look:
 * - [Card] G1: cards, list rows, settings groups.
 * - [Float] G2: floating bars / pills over scrolling content (bottom action bar, progress pill).
 * - [Modal] G3: dialogs, popovers, bottom sheets, the connection overlay.
 * - [Viewer] GV: chrome over a full-screen photo; always dark, independent of the theme.
 *
 * At most three blurred layers may be stacked on one screen.
 */
enum class GlassLevel { Card, Float, Modal, Viewer }

/** How glass renders. [Solid] = no backdrop blur, near-opaque fill (fallback / tests / perf). */
enum class GlassRenderMode { Blur, Solid }

@Immutable
data class GlassShadowToken(val offsetY: Dp, val blur: Dp, val color: Color)

/**
 * Visual tokens of one glass level in one theme (design `lib3.py` GLASS / FALLBACK tables).
 *
 * @param fill tint drawn over the blurred backdrop (alpha included).
 * @param solidFill near-opaque fill used when blur is not active.
 * @param rimTop / [rimBottom] alpha of the 1 dp white rim gradient (top → bottom).
 * @param sheen alpha of the white highlight that fades out diagonally at 55 %.
 */
@Immutable
data class GlassToken(
    val blurRadius: Dp,
    val fill: Color,
    val solidFill: Color,
    val rimTop: Float,
    val rimBottom: Float,
    val sheen: Float,
    val shadow: GlassShadowToken,
)

object GlassTokens {
    private val Navy = Color(0xFF0A1F44)

    val LightCard = GlassToken(
        blurRadius = 20.dp,
        fill = Color.White.copy(alpha = 0.58f),
        solidFill = Color(0xFFF5F8FD).copy(alpha = 0.94f),
        rimTop = 0.95f, rimBottom = 0.35f, sheen = 0.30f,
        shadow = GlassShadowToken(4.dp, 16.dp, Color(0xFF0B57D0).copy(alpha = 0.10f)),
    )
    val LightFloat = GlassToken(
        blurRadius = 28.dp,
        fill = Color(0xFFF9FAFE).copy(alpha = 0.78f),
        solidFill = Color(0xFFF9FAFE).copy(alpha = 0.96f),
        rimTop = 0.95f, rimBottom = 0.40f, sheen = 0.22f,
        shadow = GlassShadowToken(6.dp, 20.dp, Navy.copy(alpha = 0.18f)),
    )
    val LightModal = GlassToken(
        blurRadius = 32.dp,
        fill = Color.White.copy(alpha = 0.86f),
        solidFill = Color.White.copy(alpha = 0.98f),
        rimTop = 1.0f, rimBottom = 0.45f, sheen = 0.18f,
        shadow = GlassShadowToken(12.dp, 32.dp, Navy.copy(alpha = 0.24f)),
    )
    val DarkCard = GlassToken(
        blurRadius = 20.dp,
        fill = Color.White.copy(alpha = 0.07f),
        solidFill = Color(0xFF1A1F25).copy(alpha = 0.94f),
        rimTop = 0.22f, rimBottom = 0.06f, sheen = 0.05f,
        shadow = GlassShadowToken(4.dp, 16.dp, Color.Black.copy(alpha = 0.30f)),
    )
    val DarkFloat = GlassToken(
        blurRadius = 28.dp,
        fill = Color(0xFF20252B).copy(alpha = 0.74f),
        solidFill = Color(0xFF20252B).copy(alpha = 0.96f),
        rimTop = 0.26f, rimBottom = 0.08f, sheen = 0.06f,
        shadow = GlassShadowToken(6.dp, 20.dp, Color.Black.copy(alpha = 0.42f)),
    )
    val DarkModal = GlassToken(
        blurRadius = 32.dp,
        fill = Color(0xFF272C33).copy(alpha = 0.86f),
        solidFill = Color(0xFF272C33).copy(alpha = 0.98f),
        rimTop = 0.30f, rimBottom = 0.10f, sheen = 0.07f,
        shadow = GlassShadowToken(12.dp, 32.dp, Color.Black.copy(alpha = 0.55f)),
    )
    val Viewer = GlassToken(
        blurRadius = 24.dp,
        fill = Color(0xFF101418).copy(alpha = 0.66f),
        solidFill = Color(0xFF101418).copy(alpha = 0.94f),
        rimTop = 0.26f, rimBottom = 0.08f, sheen = 0.05f,
        shadow = GlassShadowToken(4.dp, 16.dp, Color.Black.copy(alpha = 0.40f)),
    )

    /** Token for [level] in the light or dark theme ([GlassLevel.Viewer] ignores [dark]). */
    fun of(level: GlassLevel, dark: Boolean): GlassToken = when (level) {
        GlassLevel.Card -> if (dark) DarkCard else LightCard
        GlassLevel.Float -> if (dark) DarkFloat else LightFloat
        GlassLevel.Modal -> if (dark) DarkModal else LightModal
        GlassLevel.Viewer -> Viewer
    }
}

/**
 * Whether a glass surface actually blurs: only in [GlassRenderMode.Blur] and only when a backdrop
 * source is reachable in the same Compose tree. Otherwise it falls back to [GlassToken.solidFill].
 */
fun glassBlurActive(mode: GlassRenderMode, hasBackdrop: Boolean): Boolean =
    mode == GlassRenderMode.Blur && hasBackdrop

/** The fill a surface paints for its current render state (pure, for tests and custom drawing). */
fun GlassToken.resolvedFill(blurActive: Boolean): Color = if (blurActive) fill else solidFill
