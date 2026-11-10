package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.sasch.cameragps.sharednew.ui.theme.AlphaColors
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme

/**
 * Screen root for the v3 look: draws the G0 backdrop (base colour + three soft glows), makes it —
 * and, unless [contentIsBackdropSource] is false, the page content — the blur source of every glass
 * surface below, and hosts in-tree overlays ([CapsuleDialog] and other G3 layers) above the content
 * so they can blur it.
 *
 * Put it at the root of a screen (inside [com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme]);
 * screens that use a Scaffold should give the Scaffold a transparent container colour.
 *
 * @param contentIsBackdropSource false for screens whose content must not be sampled (the photo
 *   grid, design page 4) — dialogs then blur only the glow layer.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    contentIsBackdropSource: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberGlassBackdrop()
    val overlayHost = remember { GlassOverlayHost() }
    val colors = AlphaTheme.colors
    CompositionLocalProvider(
        LocalGlassBackdrop provides backdrop,
        LocalGlassOverlayHost provides overlayHost,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().glassSource(backdrop, zIndex = 0f)) {
                drawGlassBackdrop(colors)
            }
            val sourceModifier =
                if (contentIsBackdropSource) Modifier.glassSource(backdrop, zIndex = 1f) else Modifier
            // While a modal overlay is up, the page below is hidden from accessibility services.
            val a11yModifier = if (overlayHost.hasOverlay) Modifier.clearAndSetSemantics { } else Modifier
            Box(
                modifier = Modifier.fillMaxSize().then(sourceModifier).then(a11yModifier),
                content = content,
            )
            GlassOverlayLayer(overlayHost)
        }
    }
}

/** Draws the G0 backdrop (design `lib3.backdrop`): base colour plus three radial glows. */
fun DrawScope.drawGlassBackdrop(colors: AlphaColors) {
    drawRect(colors.background)
    val w = size.width
    val h = size.height
    glow(colors.glowBlue, Offset(w * 0.12f, h * 0.10f), w * 0.78f, colors.glowAlphas[0])
    glow(colors.glowViolet, Offset(w * 1.02f, h * 0.46f), w * 0.70f, colors.glowAlphas[1])
    glow(colors.glowTeal, Offset(w * 0.20f, h * 0.95f), w * 0.75f, colors.glowAlphas[2])
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float, alpha: Float) {
    if (radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius),
        radius = radius,
        center = center,
        alpha = alpha,
    )
}
