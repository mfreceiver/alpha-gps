package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sasch.cameragps.sharednew.ui.theme.AlphaColors
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.BackdropOrb
import com.sasch.cameragps.sharednew.ui.theme.tintOver

/**
 * Screen root for the v3 look: draws the G0 backdrop (base colour + coloured light orbs), makes it —
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
            // While a modal overlay is up, the page below is hidden from accessibility services and
            // refuses keyboard / D-pad focus — the same layer gate every covered overlay gets
            // (GlassOverlayFocus.kt); the top-most modal panel takes focus.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(sourceModifier)
                    .overlayLayerGate(overlayHost.pageFocus) { overlayHost.isCovered(null) },
                content = content,
            )
            // A modal layer closed: focus back to what it covered, layer by layer (anchor / saved child).
            // Keyed on the modal overlays; settles against the ones live when it runs.
            val modalEntries = overlayHost.modalEntries()
            LaunchedEffect(modalEntries) { overlayHost.focusMemory.settle(overlayHost.modalEntries()) }
            GlassOverlayLayer(overlayHost)
        }
    }
}

/**
 * Draws the G0 backdrop: base colour plus the theme's low-contrast coloured light orbs
 * ([AlphaColors.orbs], D1). The orbs give the backdrop blur something to blur — without them a
 * glass card over a flat colour reads as a plain translucent fill.
 */
fun DrawScope.drawGlassBackdrop(colors: AlphaColors) {
    drawRect(colors.background)
    colors.orbs.forEach { drawOrb(it) }
}

/** Centre and radius in px of [orb] on a canvas of [width] × [height] (pure, unit-tested). */
fun orbGeometry(orb: BackdropOrb, width: Float, height: Float): Pair<Offset, Float> =
    Offset(width * orb.x, height * orb.y) to orb.radius * minOf(width, height)

/** Radial stop of an orb's soft body … */
private const val ORB_BODY_STOP = 0.45f

/** … and its relative opacity there: a lit orb rather than a linear smear. */
private const val ORB_BODY_ALPHA = 0.55f

/**
 * Pure radial profile of an orb: relative opacity at [distance] (fraction of the radius) — 1 at the
 * centre, [ORB_BODY_ALPHA] at [ORB_BODY_STOP], 0 at and beyond the edge. Mirrors the gradient stops
 * drawn by [drawGlassBackdrop].
 */
fun orbIntensity(distance: Float): Float = when {
    distance.isNaN() || distance >= 1f -> 0f
    distance <= 0f -> 1f
    distance < ORB_BODY_STOP -> 1f + (ORB_BODY_ALPHA - 1f) * distance / ORB_BODY_STOP
    else -> ORB_BODY_ALPHA * (1f - (distance - ORB_BODY_STOP) / (1f - ORB_BODY_STOP))
}

/**
 * Pure colour of the G0 backdrop at ([x], [y]) px on a [width] × [height] canvas — the background
 * with every orb composited in drawing order. Used by the contrast tests to sample the real
 * backdrop instead of a guessed worst case.
 */
fun backdropColorAt(colors: AlphaColors, x: Float, y: Float, width: Float, height: Float): Color =
    colors.orbs.fold(colors.background) { acc, orb ->
        val (center, radius) = orbGeometry(orb, width, height)
        if (radius <= 0f) return@fold acc
        val d = (Offset(x, y) - center).getDistance() / radius
        tintOver(orb.color.copy(alpha = orb.alpha * orbIntensity(d)), acc)
    }

private fun DrawScope.drawOrb(orb: BackdropOrb) {
    val (center, radius) = orbGeometry(orb, size.width, size.height)
    if (radius <= 0f || orb.alpha <= 0f) return
    val brush = Brush.radialGradient(
        0f to orb.color,
        ORB_BODY_STOP to orb.color.copy(alpha = ORB_BODY_ALPHA),
        1f to orb.color.copy(alpha = 0f),
        center = center,
        radius = radius,
    )
    drawCircle(brush = brush, radius = radius, center = center, alpha = orb.alpha)
}
