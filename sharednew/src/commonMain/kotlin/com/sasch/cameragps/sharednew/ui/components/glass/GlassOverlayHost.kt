package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Registry of in-tree overlays (dialogs, popovers, sheets). Overlays are composed by
 * [GlassOverlayLayer] above the page content of the same [GlassBackground], so their glass can blur
 * that content — a platform `Dialog` window cannot. Last shown = top-most.
 */
@Stable
class GlassOverlayHost internal constructor() {
    internal val entries = mutableStateListOf<GlassOverlayEntry>()

    /** True while at least one overlay is shown. */
    val hasOverlay: Boolean get() = entries.isNotEmpty()
}

internal class GlassOverlayEntry(
    content: @Composable () -> Unit,
    locals: CompositionLocalContext,
) {
    var content by mutableStateOf(content)
    var locals by mutableStateOf(locals)
}

/** Overlay host of the nearest [GlassBackground]; null when the screen has none (window fallback). */
val LocalGlassOverlayHost = staticCompositionLocalOf<GlassOverlayHost?> { null }

/**
 * Shows [content] full-screen in [host]'s overlay layer for as long as this call stays in the
 * composition. CompositionLocals of the call site are carried over.
 */
@Composable
internal fun GlassOverlay(host: GlassOverlayHost, content: @Composable () -> Unit) {
    val locals = currentCompositionLocalContext
    val entry = remember { GlassOverlayEntry(content, locals) }
    SideEffect {
        entry.content = content
        entry.locals = locals
    }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/** Renders [host]'s overlays, bottom to top. Placed by [GlassBackground] above the content. */
@Composable
internal fun GlassOverlayLayer(host: GlassOverlayHost, modifier: Modifier = Modifier) {
    if (host.entries.isEmpty()) return
    Box(modifier.fillMaxSize()) {
        host.entries.forEach { entry ->
            key(entry) {
                CompositionLocalProvider(entry.locals) {
                    entry.content()
                }
            }
        }
    }
}
