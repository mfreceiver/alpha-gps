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

    /** True while at least one **modal** overlay (dialog, sheet, popover) is shown. */
    val hasOverlay: Boolean get() = entries.any { it.modal }

    /** Focus group of the page content below the overlays (see GlassOverlayFocus.kt). */
    internal val pageFocus = OverlayFocusHandle()

    /** Layer-by-layer focus returns of the modal overlays. */
    internal val focusMemory = OverlayFocusMemory()

    /** The modal overlays, bottom → top. */
    internal fun modalEntries(): List<GlassOverlayEntry> = entries.filter { it.modal }

    /** Whether [entry] (null = the page) has a modal overlay above it: no focus, no semantics. */
    internal fun isCovered(entry: GlassOverlayEntry?): Boolean =
        layerCovered(if (entry == null) -1 else entries.indexOf(entry), entries.map { it.modal })

    /** [entry] enters the host (its call site entered the composition). */
    internal fun register(entry: GlassOverlayEntry) {
        entry.live = true
        entries.add(entry)
    }

    /**
     * [entry] leaves the host for good. From here on a late panel effect of it — composed in the
     * same frame the overlay was closed, run after this — is refused ([showModalPanel]).
     */
    internal fun unregister(entry: GlassOverlayEntry) {
        entry.live = false
        entries.remove(entry)
        focusMemory.onLayerClosed(entry)
    }

    /** Registered and not left yet. */
    internal fun isLive(entry: GlassOverlayEntry): Boolean = entry.live && entries.any { it === entry }

    /** The top-most modal overlay below [entry], or null when the page is directly below. */
    private fun modalBelow(entry: GlassOverlayEntry): GlassOverlayEntry? {
        val index = entries.indexOf(entry)
        if (index <= 0) return null
        return entries.subList(0, index).lastOrNull { it.modal }
    }

    /**
     * [entry]'s panel (focus target [panel]) is shown: store where its close returns focus to and
     * move focus onto the panel. Refused — no stored return, no focus request — once [entry] left
     * the host: a panel effect that runs after the overlay closed must not bring the layer back.
     * Returns whether focus moved onto the panel.
     */
    internal fun showModalPanel(entry: GlassOverlayEntry, panel: OverlayFocusHandle): Boolean {
        if (!onModalPanelShown(entry, panel)) return false
        return panel.moveFocusHere()
    }

    /** Bookkeeping half of [showModalPanel]; false when [entry] is not live any more. */
    internal fun onModalPanelShown(entry: GlassOverlayEntry, panel: OverlayFocusHandle): Boolean {
        if (!isLive(entry)) return false
        entry.panelFocus = panel
        val below = modalBelow(entry)
        // The saved path starts at the panel of the overlay below (not its gate, whose restore
        // would stop on the focusable panel container and pass for the saved child).
        val belowRoot = if (below == null) pageFocus else below.panelFocus ?: below.focusGroup
        val anchor = entry.returnFocus?.let { anchor -> FocusAttempt(FocusReturn.Anchor) { anchor.moveFocusHere() } }
        var saved: OverlayFocusPath? = null
        val restoreBelow = buildList {
            add(FocusAttempt(FocusReturn.Saved) { saved?.restore() ?: false })
            if (below != null) {
                add(FocusAttempt(FocusReturn.Container) { below.panelFocus?.moveFocusHere() ?: false })
            }
        }
        return focusMemory.onLayerShown(entry, anchor, { belowRoot.captureFocusPath().also { saved = it } != null }, restoreBelow)
    }

    /**
     * [entry]'s panel left the composition (with its overlay, or on its own): its stored return
     * goes with it — same as closing the layer; a no-op when the entry already closed it.
     */
    internal fun onModalPanelGone(entry: GlassOverlayEntry, panel: OverlayFocusHandle) {
        if (entry.panelFocus === panel) entry.panelFocus = null
        focusMemory.onLayerClosed(entry)
    }
}

internal class GlassOverlayEntry(
    content: @Composable () -> Unit,
    locals: CompositionLocalContext?,
    val modal: Boolean,
    returnFocus: OverlayFocusHandle?,
) {
    var content by mutableStateOf(content)
    var locals by mutableStateOf(locals)

    /** Where focus returns first when this overlay closes (the anchor of a popover / menu). */
    var returnFocus: OverlayFocusHandle? = returnFocus

    /** Focus group of this layer (see [overlayLayerGate]). */
    val focusGroup = OverlayFocusHandle()

    /** Focus target of this overlay's panel container, once shown. */
    var panelFocus: OverlayFocusHandle? = null

    /** Between [GlassOverlayHost.register] and [GlassOverlayHost.unregister]. */
    var live: Boolean = false
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
    returnFocus: OverlayFocusHandle? = null,
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
        host.register(entry)
        onDispose { host.unregister(entry) }
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
                val layer: @Composable () -> Unit = {
                    // An overlay is top-level glass again even when shown from inside a card.
                    CompositionLocalProvider(LocalGlassDepth provides 0, LocalGlassOverlayEntry provides entry) {
                        Box(Modifier.fillMaxSize().overlayLayerGate(entry.focusGroup) { host.isCovered(entry) }) {
                            entry.content()
                        }
                    }
                }
                val locals = entry.locals
                if (locals != null) CompositionLocalProvider(locals, layer) else layer()
            }
        }
    }
}
