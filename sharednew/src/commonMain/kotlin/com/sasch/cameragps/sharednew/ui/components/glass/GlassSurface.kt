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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
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
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import com.sasch.cameragps.sharednew.ui.theme.GlassTokens
import com.sasch.cameragps.sharednew.ui.theme.glassBlurActive
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Paints a glass background of [level] clipped to [shape]: drop shadow, backdrop blur + tint (or
 * the near-solid fallback when blur is off / no backdrop is reachable), diagonal sheen and the 1 dp
 * white rim. Use it for custom layouts (lazy-list rows); prefer [GlassSurface] / [GlassCard].
 *
 * Content drawn after this modifier is clipped to [shape].
 */
@Composable
fun Modifier.glass(
    level: GlassLevel,
    shape: Shape,
    shadow: Boolean = true,
): Modifier {
    val colors = AlphaTheme.colors
    val backdrop = LocalGlassBackdrop.current
    val token = GlassTokens.of(level, colors.isDark)
    val blur = glassBlurActive(AlphaTheme.glassRenderMode, backdrop != null)
    val baseColor = colors.background
    val style = remember(token, baseColor) {
        HazeBlurStyle {
            blurRadius(token.blurRadius)
            noiseFactor(0f)
            backgroundColor(baseColor)
            colorEffects(listOf(HazeColorEffect.tint(token.fill)))
            fallbackColorEffect(HazeColorEffect.tint(token.solidFill))
        }
    }
    val input = remember(backdrop) { backdrop?.let { HazeInput.Sources(it.hazeState) } }
    val shadowModifier = if (shadow) {
        Modifier.dropShadow(
            shape,
            Shadow(
                radius = token.shadow.blur,
                color = token.shadow.color,
                offset = DpOffset(0.dp, token.shadow.offsetY),
            ),
        )
    } else {
        Modifier
    }
    val fillModifier = if (blur && input != null) {
        Modifier.hazeBlur(input, style)
    } else {
        Modifier.background(token.solidFill)
    }
    return this
        .then(shadowModifier)
        .clip(shape)
        .then(fillModifier)
        .drawWithCache {
            val sheen = Brush.linearGradient(
                0f to Color.White.copy(alpha = token.sheen),
                0.55f to Color.Transparent,
                start = Offset.Zero,
                end = Offset(size.width * 0.9f, size.height),
            )
            onDrawBehind { drawRect(sheen) }
        }
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = token.rimTop), Color.White.copy(alpha = token.rimBottom)),
            ),
            shape = shape,
        )
}

/** Content colour on a glass level: white on the always-dark viewer glass, onSurface elsewhere. */
@Composable
fun glassContentColor(level: GlassLevel): Color =
    if (level == GlassLevel.Viewer) Color.White else AlphaTheme.colors.onSurface

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
    CompositionLocalProvider(LocalContentColor provides glassContentColor(level)) {
        Box(
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .glass(level, shape)
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
