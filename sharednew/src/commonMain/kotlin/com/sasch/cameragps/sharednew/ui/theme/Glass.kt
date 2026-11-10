package com.sasch.cameragps.sharednew.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glass levels of design v3 (page 4). Pick by role, not by look:
 * - [Card] G1: cards, list rows, settings groups.
 * - [Float] G2: floating bars / pills over scrolling content (bottom action bar, progress pill),
 *   inset glass inside a card (text field, icon tile, segmented control).
 * - [Modal] G3: dialogs, popovers, bottom sheets, snackbars, the connection overlay.
 * - [Viewer] GV: chrome over a full-screen photo; always dark, independent of the theme.
 *
 * At most three blurred layers may be stacked on one screen. Glass nested inside glass does not
 * blur again — it is drawn as a translucent layer over its parent (see `Modifier.glass`).
 */
enum class GlassLevel { Card, Float, Modal, Viewer }

/** How glass renders. [Solid] = no backdrop blur, near-opaque fill (fallback / tests / perf). */
enum class GlassRenderMode { Blur, Solid }

/** Colour of every glass highlight (edge light, sheen, inner highlight); alpha comes from the token. */
val GlassHighlight: Color = Color.White

/** One drop shadow layer; [color] includes its alpha. */
@Immutable
data class GlassShadowToken(val offsetY: Dp, val blur: Dp, val color: Color)

/**
 * Visual tokens of one glass level in one theme. The "liquid glass" look is the sum of six parts
 * (audit §2-9(c) 1–6); every value and the reason for it is documented in
 * `docs/glass-material-notes.md` for on-device tuning.
 *
 * @param fill tint drawn over the blurred backdrop (alpha included).
 * @param solidFill near-opaque fill used when blur is not active.
 * @param rimTop / [rimBottom] alpha of the white edge light at the top-start corner → middle.
 * @param rimShade dark edge at the bottom-end corner (alpha included) — the "bi-directional" rim.
 * @param rimWidth edge-light stroke width.
 * @param sheen alpha of the white highlight that fades out diagonally at 55 %.
 * @param innerHighlight bright band along the top inner edge (alpha included).
 * @param innerShadow dark band along the bottom inner edge (alpha included).
 * @param bandHeight height over which the inner bands fade out.
 * @param noise Haze `noiseFactor` (grain on the blurred backdrop; 0 = off).
 * @param ambientShadow large, soft, low-alpha shadow (elevation).
 * @param keyShadow small, close, slightly darker shadow (contact).
 */
@Immutable
data class GlassToken(
    val blurRadius: Dp,
    val fill: Color,
    val solidFill: Color,
    val rimTop: Float,
    val rimBottom: Float,
    val rimShade: Color,
    val rimWidth: Dp,
    val sheen: Float,
    val innerHighlight: Color,
    val innerShadow: Color,
    val bandHeight: Dp,
    val noise: Float,
    val ambientShadow: GlassShadowToken,
    val keyShadow: GlassShadowToken,
)

object GlassTokens {
    private val Navy = Color(0xFF0A1F44)
    private val Blue = Color(0xFF0B57D0)
    private val White = Color.White
    private val Black = Color.Black

    val LightCard = GlassToken(
        blurRadius = 20.dp,
        fill = Color(0xFFFBFDFF).copy(alpha = 0.60f),
        solidFill = Color(0xFFF5F8FD).copy(alpha = 0.94f),
        rimTop = 0.95f, rimBottom = 0.30f, rimShade = Navy.copy(alpha = 0.08f), rimWidth = 1.5.dp,
        sheen = 0.26f,
        innerHighlight = White.copy(alpha = 0.70f), innerShadow = Navy.copy(alpha = 0.05f), bandHeight = 6.dp,
        noise = 0.04f,
        ambientShadow = GlassShadowToken(8.dp, 24.dp, Blue.copy(alpha = 0.09f)),
        keyShadow = GlassShadowToken(1.dp, 4.dp, Navy.copy(alpha = 0.08f)),
    )
    val LightFloat = GlassToken(
        blurRadius = 28.dp,
        fill = Color(0xFFF9FAFE).copy(alpha = 0.74f),
        solidFill = Color(0xFFF9FAFE).copy(alpha = 0.96f),
        rimTop = 0.95f, rimBottom = 0.36f, rimShade = Navy.copy(alpha = 0.10f), rimWidth = 1.5.dp,
        sheen = 0.20f,
        innerHighlight = White.copy(alpha = 0.80f), innerShadow = Navy.copy(alpha = 0.06f), bandHeight = 6.dp,
        noise = 0.05f,
        ambientShadow = GlassShadowToken(10.dp, 28.dp, Navy.copy(alpha = 0.14f)),
        keyShadow = GlassShadowToken(2.dp, 6.dp, Navy.copy(alpha = 0.10f)),
    )
    val LightModal = GlassToken(
        blurRadius = 32.dp,
        fill = White.copy(alpha = 0.84f),
        solidFill = White.copy(alpha = 0.98f),
        rimTop = 1.0f, rimBottom = 0.42f, rimShade = Navy.copy(alpha = 0.10f), rimWidth = 1.5.dp,
        sheen = 0.16f,
        innerHighlight = White.copy(alpha = 0.85f), innerShadow = Navy.copy(alpha = 0.05f), bandHeight = 8.dp,
        noise = 0.05f,
        ambientShadow = GlassShadowToken(16.dp, 40.dp, Navy.copy(alpha = 0.20f)),
        keyShadow = GlassShadowToken(3.dp, 8.dp, Navy.copy(alpha = 0.12f)),
    )
    val DarkCard = GlassToken(
        blurRadius = 20.dp,
        fill = Color(0xFFDDE6F5).copy(alpha = 0.08f),
        solidFill = Color(0xFF1A1F25).copy(alpha = 0.94f),
        rimTop = 0.34f, rimBottom = 0.08f, rimShade = Black.copy(alpha = 0.30f), rimWidth = 1.5.dp,
        sheen = 0.06f,
        innerHighlight = White.copy(alpha = 0.14f), innerShadow = Black.copy(alpha = 0.22f), bandHeight = 6.dp,
        noise = 0.05f,
        ambientShadow = GlassShadowToken(8.dp, 24.dp, Black.copy(alpha = 0.34f)),
        keyShadow = GlassShadowToken(1.dp, 4.dp, Black.copy(alpha = 0.30f)),
    )
    val DarkFloat = GlassToken(
        blurRadius = 28.dp,
        fill = Color(0xFF20252B).copy(alpha = 0.70f),
        solidFill = Color(0xFF20252B).copy(alpha = 0.96f),
        rimTop = 0.38f, rimBottom = 0.10f, rimShade = Black.copy(alpha = 0.35f), rimWidth = 1.5.dp,
        sheen = 0.07f,
        innerHighlight = White.copy(alpha = 0.16f), innerShadow = Black.copy(alpha = 0.26f), bandHeight = 6.dp,
        noise = 0.05f,
        ambientShadow = GlassShadowToken(10.dp, 28.dp, Black.copy(alpha = 0.42f)),
        keyShadow = GlassShadowToken(2.dp, 6.dp, Black.copy(alpha = 0.30f)),
    )
    val DarkModal = GlassToken(
        blurRadius = 32.dp,
        fill = Color(0xFF272C33).copy(alpha = 0.84f),
        solidFill = Color(0xFF272C33).copy(alpha = 0.98f),
        rimTop = 0.40f, rimBottom = 0.12f, rimShade = Black.copy(alpha = 0.40f), rimWidth = 1.5.dp,
        sheen = 0.08f,
        innerHighlight = White.copy(alpha = 0.18f), innerShadow = Black.copy(alpha = 0.28f), bandHeight = 8.dp,
        noise = 0.05f,
        ambientShadow = GlassShadowToken(16.dp, 40.dp, Black.copy(alpha = 0.55f)),
        keyShadow = GlassShadowToken(3.dp, 8.dp, Black.copy(alpha = 0.30f)),
    )
    val Viewer = GlassToken(
        blurRadius = 24.dp,
        // .70 (was .66): onPhotoSecondary (white 85 %) needs ≥ 4.5:1 over a white photo — .66 gave 4.27.
        fill = Color(0xFF101418).copy(alpha = 0.70f),
        solidFill = Color(0xFF101418).copy(alpha = 0.94f),
        rimTop = 0.34f, rimBottom = 0.10f, rimShade = Black.copy(alpha = 0.30f), rimWidth = 1.5.dp,
        sheen = 0.06f,
        innerHighlight = White.copy(alpha = 0.16f), innerShadow = Black.copy(alpha = 0.24f), bandHeight = 6.dp,
        noise = 0.03f,
        ambientShadow = GlassShadowToken(6.dp, 20.dp, Black.copy(alpha = 0.40f)),
        keyShadow = GlassShadowToken(1.dp, 4.dp, Black.copy(alpha = 0.30f)),
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
 * Semantic tint laid over a glass fill (glass buttons, the switch "on" track, the segmented
 * slider, the checkbox) together with the content colour that stays readable on it.
 */
enum class GlassTintRole { Neutral, AccentSoft, Accent, Danger }

/** A tint colour (alpha included, [Color.Transparent] = none) and its text / icon colour. */
@Immutable
data class GlassTint(val tint: Color, val content: Color)

object GlassTints {
    private val DeepBlue = Color(0xFF0B57D0)

    /**
     * Light: accent at 30 % turns the glass a clear blue while the navy label keeps ≥ 5.8:1 (the
     * accent itself would only reach 2.3:1 on its own tint). Dark: the light accent (#A8C7FA) cannot
     * darken a surface, so the dark glass takes the deep brand blue at 46 % under a near-white label.
     */
    fun of(role: GlassTintRole, colors: AlphaColors): GlassTint = when (role) {
        // Dark: a 30 % veil of the page colour, so the near-white label keeps ≥ 4.5:1 even when the
        // floating glass sits on a white photo (untinted it fell to 3.8:1).
        GlassTintRole.Neutral ->
            GlassTint(if (colors.isDark) colors.background.copy(alpha = 0.30f) else Color.Transparent, colors.textPrimary)
        // Tonal glass: a faint blue cast; the label keeps the high-contrast text colour (the accent
        // itself drops to 3.3:1 on light glass over a dark photo).
        GlassTintRole.AccentSoft ->
            // Dark: the blue cast sits on the same page-colour veil as Neutral (blue alone: 4.2:1 on a white photo).
            if (colors.isDark) GlassTint(tintOver(DeepBlue.copy(alpha = 0.24f), colors.background.copy(alpha = 0.30f)), colors.textPrimary)
            else GlassTint(colors.primary.copy(alpha = 0.14f), colors.onPrimaryContainer)
        GlassTintRole.Accent ->
            if (colors.isDark) GlassTint(DeepBlue.copy(alpha = 0.46f), colors.textPrimary)
            else GlassTint(colors.primary.copy(alpha = 0.30f), colors.onPrimaryContainer)
        GlassTintRole.Danger ->
            if (colors.isDark) GlassTint(Color(0xFF93000A).copy(alpha = 0.46f), colors.onErrorContainer)
            else GlassTint(colors.error.copy(alpha = 0.18f), colors.onErrorContainer)
    }
}

/**
 * Solid accent fills of selection controls and the glyph drawn on them (switch "on" thumb, check
 * mark). Accent + onPrimary in both themes: a white glyph on the dark accent (#A8C7FA) is 1.7:1,
 * and any mid blue that keeps white ≥ 3:1 is < 3:1 against the blue backdrop orb of the dark page
 * (window L ∈ [0.29, 0.30] — too fragile), so the dark theme uses a navy glyph on the light accent
 * (7.5:1; track ≥ 5:1 against the brightest dark Card glass).
 */
object GlassControlColors {
    /** "On" switch track, checked checkbox. */
    fun accentFill(colors: AlphaColors): Color = colors.accent

    /** Glyph / thumb colour drawn on [accentFill]. */
    fun glyph(colors: AlphaColors): Color = colors.onPrimary
}

/**
 * Whether a glass surface actually blurs: only in [GlassRenderMode.Blur] and only when a backdrop
 * source is reachable in the same Compose tree. Otherwise it falls back to [GlassToken.solidFill].
 */
fun glassBlurActive(mode: GlassRenderMode, hasBackdrop: Boolean): Boolean =
    mode == GlassRenderMode.Blur && hasBackdrop

/** The fill a surface paints for its current render state (pure, for tests and custom drawing). */
fun GlassToken.resolvedFill(blurActive: Boolean): Color = if (blurActive) fill else solidFill

/**
 * Source-over of two **translucent** colours into one translucent colour (non-premultiplied):
 * drawing the result once looks the same as drawing [bottom] then [top]. Used to fold a semantic
 * [GlassTint] into the level fill so Haze can keep its single-pass noise + tint shader.
 */
fun tintOver(top: Color, bottom: Color): Color {
    val a = top.alpha + bottom.alpha * (1f - top.alpha)
    if (a <= 0f) return Color.Transparent
    fun ch(t: Float, b: Float) = ((t * top.alpha + b * bottom.alpha * (1f - top.alpha)) / a).coerceIn(0f, 1f)
    return Color(ch(top.red, bottom.red), ch(top.green, bottom.green), ch(top.blue, bottom.blue), a.coerceIn(0f, 1f))
}
