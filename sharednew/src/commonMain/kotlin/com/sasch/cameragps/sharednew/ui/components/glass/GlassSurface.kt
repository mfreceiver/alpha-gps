package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.GlassHighlight
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import com.sasch.cameragps.sharednew.ui.theme.GlassRenderMode
import com.sasch.cameragps.sharednew.ui.theme.GlassShadowToken
import com.sasch.cameragps.sharednew.ui.theme.GlassToken
import com.sasch.cameragps.sharednew.ui.theme.GlassTokens
import com.sasch.cameragps.sharednew.ui.theme.glassBlurActive
import com.sasch.cameragps.sharednew.ui.theme.tintOver
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * How one glass surface paints, resolved from the render mode, backdrop availability and nesting
 * depth (pure, unit-tested).
 *
 * @param blur whether the Haze backdrop blur runs.
 * @param fill the colour drawn over the blurred backdrop, or the whole fill when [blur] is false.
 * @param shadow whether the two-layer drop shadow is drawn.
 */
@Immutable
data class GlassPaint(val blur: Boolean, val fill: Color, val shadow: Boolean)

/**
 * Resolves a [GlassPaint]:
 * - top-level glass (depth 0) blurs when [glassBlurActive], otherwise paints the near-solid
 *   fallback;
 * - **nested** glass (depth ≥ 1, e.g. a text field inside a card) never blurs again: Haze would
 *   sample only the G0 backdrop (sources behind the page content), not the card it sits on, so it is
 *   drawn as a translucent layer over its parent instead — cheaper, and it composes correctly —
 *   and casts no shadow through the translucent parent;
 * - [tint] (a semantic [GlassTint] colour) is folded into the fill with [tintOver].
 */
fun resolveGlassPaint(
    token: GlassToken,
    mode: GlassRenderMode,
    hasBackdrop: Boolean,
    depth: Int,
    tint: Color = Color.Transparent,
    shadow: Boolean = true,
): GlassPaint {
    val nested = depth > 0
    val blur = !nested && glassBlurActive(mode, hasBackdrop)
    val base = if (blur || nested) token.fill else token.solidFill
    return GlassPaint(blur = blur, fill = tintOver(tint, base), shadow = shadow && !nested)
}

/**
 * Glass nesting depth of the current position: 0 on the page, +1 inside every [GlassSurface]. A
 * custom container built with [Modifier.glass] should provide `LocalGlassDepth.current + 1` to its
 * children so nested glass does not blur twice.
 */
val LocalGlassDepth = compositionLocalOf { 0 }

/**
 * Paints a glass background of [level] clipped to [shape] — the six parts of the glass look
 * (audit §2-9(c), token values in [GlassTokens], rationale in docs/glass-material-notes.md):
 * 1. two-layer drop shadow (ambient + key);
 * 2. backdrop blur + level fill (+ optional semantic [tint]) with a fine grain (`noiseFactor`),
 *    or the near-solid fallback when blur is off / no backdrop is reachable;
 * 3. diagonal sheen;
 * 4. inner highlight band along the top edge and inner shadow band along the bottom edge;
 * 5. bi-directional edge light: white at the top-start corner fading to a dark rim at bottom-end.
 * Nested glass (see [LocalGlassDepth]) skips the blur and the shadow.
 *
 * Use it for custom layouts (lazy-list rows); prefer [GlassSurface] / [GlassCard]. Content drawn
 * after this modifier is clipped to [shape].
 */
@Composable
fun Modifier.glass(
    level: GlassLevel,
    shape: Shape,
    shadow: Boolean = true,
    tint: Color = Color.Transparent,
): Modifier {
    val colors = AlphaTheme.colors
    val backdrop = LocalGlassBackdrop.current
    val token = GlassTokens.of(level, colors.isDark)
    val paint = resolveGlassPaint(token, AlphaTheme.glassRenderMode, backdrop != null, LocalGlassDepth.current, tint, shadow)
    val baseColor = colors.background
    val solid = tintOver(tint, token.solidFill)
    val style = remember(token, baseColor, paint.fill, solid) {
        HazeBlurStyle {
            blurRadius(token.blurRadius)
            noiseFactor(token.noise)
            backgroundColor(baseColor)
            // One SrcOver tint lets Haze fuse noise + tint into a single shader pass (API 33+).
            colorEffects(listOf(HazeColorEffect.tint(paint.fill)))
            fallbackColorEffect(HazeColorEffect.tint(solid))
        }
    }
    val input = remember(backdrop) { backdrop?.let { HazeInput.Sources(it.hazeState) } }
    val shadowModifier = if (paint.shadow) {
        Modifier
            .dropShadow(shape, token.ambientShadow.asShadow())
            .dropShadow(shape, token.keyShadow.asShadow())
    } else {
        Modifier
    }
    val fillModifier = if (paint.blur && input != null) {
        Modifier.hazeBlur(input, style)
    } else {
        Modifier.background(paint.fill)
    }
    return this
        .then(shadowModifier)
        .clip(shape)
        .then(fillModifier)
        .drawWithCache {
            val sheen = Brush.linearGradient(
                0f to GlassHighlight.copy(alpha = token.sheen),
                0.55f to Color.Transparent,
                start = Offset.Zero,
                end = Offset(size.width * 0.9f, size.height),
            )
            val band = token.bandHeight.toPx().coerceAtMost(size.height * 0.3f)
            val highlight = Brush.verticalGradient(
                0f to token.innerHighlight,
                1f to Color.Transparent,
                startY = 0f,
                endY = band,
            )
            val innerShadow = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to token.innerShadow,
                startY = size.height - band,
                endY = size.height,
            )
            onDrawBehind {
                drawRect(sheen)
                drawRect(highlight, size = Size(size.width, band))
                drawRect(innerShadow, topLeft = Offset(0f, size.height - band), size = Size(size.width, band))
            }
        }
        .border(width = token.rimWidth, brush = glassRimBrush(token), shape = shape)
}

/** Edge light: white [GlassToken.rimTop] at top-start → [GlassToken.rimBottom] → dark rim at bottom-end. */
fun glassRimBrush(token: GlassToken): Brush = Brush.linearGradient(
    0f to GlassHighlight.copy(alpha = token.rimTop),
    0.45f to GlassHighlight.copy(alpha = token.rimBottom),
    1f to token.rimShade,
    start = Offset.Zero,
    end = Offset.Infinite,
)

private fun GlassShadowToken.asShadow() = Shadow(radius = blur, color = color, offset = DpOffset(0.dp, offsetY))

/** Content colour on a glass level: `onPhoto` on the always-dark viewer glass, `textPrimary` elsewhere. */
@Composable
fun glassContentColor(level: GlassLevel): Color =
    if (level == GlassLevel.Viewer) AlphaTheme.colors.onPhoto else AlphaTheme.colors.textPrimary

/**
 * A glass surface (G1 card / G2 floating / G3 modal / GV viewer). When [onClick] is set the whole
 * surface is one touch target with ripple and the design's 0.97 press scale.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Card,
    shape: Shape = AlphaShapes.card,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    tint: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (onClick != null && pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "glassPress",
    )
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = ripple(),
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    CompositionLocalProvider(
        LocalContentColor provides glassContentColor(level),
        LocalGlassDepth provides LocalGlassDepth.current + 1,
    ) {
        Box(
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .glass(level, shape, tint = tint)
                .then(clickModifier)
                .padding(contentPadding),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

/** G1 card: radius 28, padding 20, children spaced 12 dp. Group with space — never with dividers. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(AlphaDimens.cardPadding),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(AlphaDimens.cardGap),
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        level = GlassLevel.Card,
        shape = AlphaShapes.card,
        onClick = onClick,
        onClickLabel = onClickLabel,
        contentPadding = contentPadding,
    ) {
        Column(verticalArrangement = verticalArrangement, content = content)
    }
}

/**
 * G2 floating bar / floating button container (bottom action bar, progress pill, viewer toolbar
 * with `level = GlassLevel.Viewer`). Capsule-shaped by default.
 */
@Composable
fun GlassFloatingBar(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Float,
    shape: Shape = AlphaShapes.capsule,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    GlassSurface(modifier = modifier, level = level, shape = shape, contentPadding = contentPadding) {
        Row(
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * Inset block inside a card (status cells, detail panes): flat onSurface 5 % (dark 7 %), radius 18.
 * Not glass — no blur, rim or shadow.
 */
@Composable
fun GlassInsetBlock(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = AlphaTheme.colors
    Box(
        modifier = modifier
            .clip(AlphaShapes.inset)
            .background(colors.onSurface.copy(alpha = insetFillAlpha(colors.isDark)))
            .padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** Alpha of the inset block fill (design: light 5 %, dark 7 %). */
fun insetFillAlpha(dark: Boolean): Float = if (dark) 0.07f else 0.05f
