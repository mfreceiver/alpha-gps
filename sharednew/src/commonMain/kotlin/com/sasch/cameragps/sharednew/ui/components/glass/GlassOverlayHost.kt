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

    /** Layer-by-layer focus returns of the modal overlays. */
    internal val focusMemory = OverlayFocusMemory()

    /** The modal overlays, bottom → top. */
    internal fun modalEntries(): List<GlassOverlayEntry> = entries.filter { it.modal }

    /** Whether [entry] (null = the page) has a modal overlay above it: no focus, no semantics. */
    internal fun isCovered(entry: GlassOverlayEntry?): Boolean =
        layerCovered(if (entry == null) -1 else entries.indexOf(entry), entries.map { it.modal })

    /** The top-most modal overlay below [entry], or null when the page is directly below. */
    private fun modalBelow(entry: GlassOverlayEntry): GlassOverlayEntry? {
        val index = entries.indexOf(entry)
        if (index <= 0) return null
        return entries.subList(0, index).lastOrNull { it.modal }
    }

    /** [entry]'s panel is shown with focus target [panel]: store where its close returns focus to. */
    internal fun onModalPanelShown(entry: GlassOverlayEntry, panel: FocusRequester) {
        entry.panelFocus = panel
        val below = modalBelow(entry)
        val group = below?.focusGroup ?: pageFocus
        val anchor = if (entry.returnFocus == null) {
            null
        } else {
            FocusAttempt(FocusReturn.Anchor) { entry.returnFocus?.let { r -> tryFocus { r.requestFocus() } } ?: false }
        }
        val restoreBelow = buildList {
            add(FocusAttempt(FocusReturn.Saved) { tryFocus { group.restoreFocusedChild() } })
            if (below != null) {
                add(FocusAttempt(FocusReturn.Container) { below.panelFocus?.let { p -> tryFocus { p.requestFocus() } } ?: false })
            }
        }
        focusMemory.onLayerShown(entry, anchor, { tryFocus { group.saveFocusedChild() } }, restoreBelow)
    }
}

internal class GlassOverlayEntry(
    content: @Composable () -> Unit,
    locals: CompositionLocalContext,
    val modal: Boolean,
    returnFocus: FocusRequester?,
) {
    var content by mutableStateOf(content)
    var locals by mutableStateOf(locals)

    /** Where focus returns first when this overlay closes (the anchor of a popover / menu). */
    var returnFocus: FocusRequester? = returnFocus

    /** Focus group of this layer (see [overlayLayerGate]). */
    val focusGroup = FocusRequester()

    /** Focus target of this overlay's panel container, once shown. */
    var panelFocus: FocusRequester? = null
}

/** Overlay host of the nearest [GlassBackground]; null when the screen has none (window fallback). */
val LocalGlassOverlayHost = staticCompositionLocalOf<GlassOverlayHost?> { null }

/** The overlay entry whose content is being composed; null outside the overlay layer. */
internal val LocalGlassOverlayEntry = staticCompositionLocalOf<GlassOverlayEntry?> { null }

/**
 * Shows [content] full-screen in [host]'s overlay layer for as long as this call stays in the
 * composition. CompositionLocals of the call site are carried over. A [modal] overlay hides the
 * page and every overlay below it from keyboard focus and accessibility services while shown; a
 * non-modal one (snackbar, tooltip) does not. [returnFocus] (the anchor) gets keyboard focus back
 * first when a modal overlay closes. Never opens a platform window.
 */
@Composable
internal fun GlassOverlay(
    host: GlassOverlayHost,
    modal: Boolean = true,
    returnFocus: FocusRequester? = null,
    content: @Composable () -> Unit,
) {
    val locals = currentCompositionLocalContext
    val entry = remember { GlassOverlayEntry(content, locals, modal, returnFocus) }
    SideEffect {
        entry.content = content
        entry.locals = locals
        entry.returnFocus = returnFocus
    }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose {
            host.entries.remove(entry)
            host.focusMemory.onLayerClosed(entry)
        }
    }
}

/**
 * Renders [host]'s overlays, bottom to top. Placed by [GlassBackground] above the content. Each entry
 * is a layer behind [overlayLayerGate]: covered by a modal overlay above it, it takes no focus and
 * is hidden from accessibility services — only the top-most modal layer is reachable.
 */
@Composable
internal fun GlassOverlayLayer(host: GlassOverlayHost, modifier: Modifier = Modifier) {
    if (host.entries.isEmpty()) return
    Box(modifier.fillMaxSize()) {
        host.entries.forEach { entry ->
            key(entry) {
                CompositionLocalProvider(entry.locals) {
                    // An overlay is top-level glass again even when shown from inside a card.
                    CompositionLocalProvider(LocalGlassDepth provides 0, LocalGlassOverlayEntry provides entry) {
                        Box(Modifier.fillMaxSize().overlayLayerGate(entry.focusGroup) { host.isCovered(entry) }) {
                            entry.content()
                        }
                    }
                }
            }
        }
    }
}
