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
import androidx.compose.ui.focus.FocusRequester

/**
 * Registry of in-tree overlays (dialogs, popovers, sheets). Overlays are composed by
 * [GlassOverlayLayer] above the page content of the same [GlassBackground], so their glass can blur
 * that content — a platform `Dialog` window cannot. Last shown = top-most.
 */
@Stable
class GlassOverlayHost internal constructor() {
    internal val entries = mutableStateListOf<GlassOverlayEntry>()

    /** True while at least one **modal** overlay (dialog, sheet, popover) is shown. */
    val hasOverlay: Boolean get() = entries.any { it.modal }

    /** Focus group of the page content below the overlays (see GlassOverlayFocus.kt). */
    internal val pageFocus = FocusRequester()

    /** Where keyboard focus returns when the last modal overlay closes. */
    internal val focusMemory = OverlayFocusMemory()
}

internal class GlassOverlayEntry(
    content: @Composable () -> Unit,
    locals: CompositionLocalContext,
    val modal: Boolean,
) {
    var content by mutableStateOf(content)
    var locals by mutableStateOf(locals)
}

/** Overlay host of the nearest [GlassBackground]; null when the screen has none (window fallback). */
val LocalGlassOverlayHost = staticCompositionLocalOf<GlassOverlayHost?> { null }

/**
 * Shows [content] full-screen in [host]'s overlay layer for as long as this call stays in the
 * composition. CompositionLocals of the call site are carried over. A [modal] overlay hides the
 * page from accessibility services while shown; a non-modal one (snackbar, tooltip) does not.
 * Never opens a platform window.
 */
@Composable
internal fun GlassOverlay(host: GlassOverlayHost, modal: Boolean = true, content: @Composable () -> Unit) {
    val locals = currentCompositionLocalContext
    val entry = remember { GlassOverlayEntry(content, locals, modal) }
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
                    // An overlay is top-level glass again even when shown from inside a card.
                    CompositionLocalProvider(LocalGlassDepth provides 0) {
                        entry.content()
                    }
                }
            }
        }
    }
}
