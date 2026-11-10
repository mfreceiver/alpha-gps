package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * What glass surfaces blur. Wraps a Haze state so callers never touch Haze types directly.
 *
 * Rules (design v3 page 4):
 * - Sources and the glass that samples them must live in the **same Compose tree** (a separate
 *   dialog / popup window cannot sample them — use [GlassOverlayHost] instead of `Dialog`).
 * - The photo grid is never a source (performance); its screen passes
 *   `contentIsBackdropSource = false` to [GlassBackground].
 * - At most three blurred layers stacked on one screen.
 */
@Stable
class GlassBackdrop internal constructor() {
    internal val hazeState = HazeState()
}

/**
 * The backdrop glass surfaces sample: provided by the nearest [GlassBackground], or by a screen that
 * owns its own source (e.g. the photo viewer: `CompositionLocalProvider(LocalGlassBackdrop provides
 * backdrop)` + `Modifier.glassSource(backdrop)` on the photo). Null = solid glass.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

@Composable
fun rememberGlassBackdrop(): GlassBackdrop = remember { GlassBackdrop() }

/**
 * Marks this node as content that glass surfaces of [backdrop] may blur. Higher [zIndex] layers
 * are drawn on top of lower ones; a glass surface inside a source only samples sources with a lower
 * zIndex. [GlassBackground] uses 0 for the backdrop glow and 1 for the page content.
 */
fun Modifier.glassSource(backdrop: GlassBackdrop, zIndex: Float = 0f): Modifier =
    hazeSource(backdrop.hazeState, zIndex = zIndex)
