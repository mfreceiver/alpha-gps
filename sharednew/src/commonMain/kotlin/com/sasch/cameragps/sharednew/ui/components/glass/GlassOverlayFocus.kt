package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.coroutines.cancellation.CancellationException

/*
 * Keyboard / D-pad focus of in-page modal overlays (P2-3). A platform Popup(focusable = true) used
 * to give this for free; the in-page overlays (GlassPopover / GlassMenu / CapsuleDialog /
 * GlassBottomSheet) rebuild it without opening a window. The page and every overlay entry are
 * *layers* (bottom → top); modal layers stack:
 *  1. only the top-most modal layer (and non-modal overlays above it) is reachable: every layer with
 *     a modal overlay above it — the page or a lower overlay — refuses keyboard focus and is hidden
 *     from accessibility services, through one shared gate ([overlayLayerGate], [layerCovered]);
 *  2. when a modal panel shows, the focused child of the uncovered layer below it is saved and focus
 *     moves onto the panel container ([rememberModalPanelFocus]);
 *  3. closing a layer returns focus to what it covered, layer by layer ([OverlayFocusMemory]): its
 *     anchor (popover / menu), else the saved child of the layer below, else that layer's panel
 *     container — only if that layer had focus before (touch use stays focus-free).
 * Only the hosted path (inside a [GlassBackground]) takes part; the platform-Dialog fallback of
 * CapsuleDialog keeps window focus handling.
 */

/** Which step of a focus return succeeded (or why none ran). */
internal enum class FocusReturn {
    /** Nothing to return, or the layer below had no focus before (touch use). */
    None,

    /** A newer layer that has not shown its panel yet sits on top; it takes the return over. */
    Deferred,

    /** The anchor that opened the closed popover / menu. */
    Anchor,

    /** The child of the uncovered layer that was focused before the closed layer opened. */
    Saved,

    /** The panel container of the uncovered overlay (its saved child is gone). */
    Container,
}

/** One step of a focus return; [run] reports whether focus moved. */
internal class FocusAttempt(val kind: FocusReturn, val run: () -> Boolean)

/**
 * Pure bookkeeping of focus returns over a stack of modal layers. Keys identify layers (overlay
 * entries). Each shown layer stores where focus was before it opened; closing the top layer makes
 * that return due ([settle] performs it once the overlays settled), closing a lower layer first
 * hands its return over to the layer above it (that one now covers what the closed one covered).
 */
internal class OverlayFocusMemory {
    private class Frame(
        val key: Any,
        /** The uncovered layer had a focused child when this layer opened. */
        val hadFocus: Boolean,
        val anchor: FocusAttempt?,
        /** Fallbacks after [anchor], in order. */
        val fallback: List<FocusAttempt>,
    ) {
        val returnTo: List<FocusAttempt> get() = listOfNotNull(anchor) + fallback
    }

    private val frames = mutableListOf<Frame>()
    private var pending: Frame? = null

    /** Number of shown layers with a stored return (bottom → top). */
    val depth: Int get() = frames.size

    /** A closed top layer whose focus return [settle] has not performed yet. */
    val hasPendingReturn: Boolean get() = pending != null

    /**
     * Layer [key] shows its panel, before focus moves into it. [saveBelow] records the focused child
     * of the uncovered layer below (true if there was one); [anchor] and then [restoreBelow] are
     * tried when this layer closes. A layer that replaces one closed in the same transaction (menu
     * item → dialog) takes over that layer's return instead — the page lost its focus to the menu.
     */
    fun onLayerShown(key: Any, anchor: FocusAttempt?, saveBelow: () -> Boolean, restoreBelow: List<FocusAttempt>) {
        if (frames.any { it.key === key }) return
        val replaced = pending
        frames += if (replaced != null) {
            pending = null
            Frame(key, replaced.hadFocus, anchor, replaced.returnTo)
        } else {
            Frame(key, saveBelow(), anchor, restoreBelow)
        }
    }

    /** Layer [key] left the overlay host. Pure bookkeeping: no focus moves here (see [settle]). */
    fun onLayerClosed(key: Any) {
        val index = frames.indexOfFirst { it.key === key }
        if (index < 0) return
        val closed = frames.removeAt(index)
        if (index == frames.size) {
            pending = closed // it was the top layer: its return is due
        } else {
            val above = frames[index]
            frames[index] = Frame(above.key, closed.hadFocus, above.anchor, closed.returnTo)
        }
    }

    /**
     * Performs a due focus return once the overlays settled; [liveModalKeys] are the modal layers
     * still shown, bottom → top. Deferred while a live layer above the top stored one has not shown
     * its panel yet (it takes the return over in [onLayerShown]).
     */
    fun settle(liveModalKeys: List<Any>): FocusReturn {
        val due = pending ?: return FocusReturn.None
        val top = frames.lastOrNull()
        val topIndex = if (top == null) -1 else liveModalKeys.indexOfFirst { it === top.key }
        val newerLayerComing = liveModalKeys.withIndex().any { (i, key) -> i > topIndex && frames.none { it.key === key } }
        if (newerLayerComing) return FocusReturn.Deferred
        pending = null
        if (!due.hadFocus) return FocusReturn.None
        return due.returnTo.firstOrNull { it.run() }?.kind ?: FocusReturn.None
    }
}

/**
 * Whether layer [index] (−1 = the page, else the overlay entry index, bottom → top) has a modal
 * overlay above it — then it takes no keyboard focus and is hidden from accessibility services.
 */
internal fun layerCovered(index: Int, modal: List<Boolean>): Boolean {
    for (i in index + 1 until modal.size) if (modal[i]) return true
    return false
}

/**
 * The gate of one layer (the page or an overlay entry): a focus group behind [group] that refuses
 * entry and hides its semantics while [covered]. Same mechanism for the page and lower overlays.
 */
internal fun Modifier.overlayLayerGate(group: FocusRequester, covered: () -> Boolean): Modifier = this
    .focusRequester(group)
    .focusProperties { onEnter = { if (covered()) cancelFocusChange() } }
    .focusGroup()
    .then(if (covered()) Modifier.clearAndSetSemantics { } else Modifier)

/** Messages of the [IllegalStateException]s Compose throws for a focus target that is not attached (yet / any more). */
private val DETACHED_FOCUS_TARGET = listOf(
    "called on an unattached node", // visitChildren / visitAncestors on a detached node
    "FocusRequester is not initialized", // older Compose threw instead of warning
)

/** Whether [e] is the expected "focus target not attached" failure around overlay transitions. */
internal fun isDetachedFocusTarget(e: IllegalStateException): Boolean {
    val message = e.message ?: return false
    return DETACHED_FOCUS_TARGET.any { it in message }
}

/**
 * Runs a focus request. A target that is not attached (yet / any more) — expected around overlay
 * transitions — is reported as false. Cancellation and every other failure (including other
 * [IllegalStateException]s, e.g. misuse of FocusRequester.Default) propagate.
 */
internal fun tryFocus(request: () -> Boolean): Boolean =
    try {
        request()
    } catch (cancelled: CancellationException) {
        throw cancelled // on the JVM a CancellationException *is* an IllegalStateException
    } catch (e: IllegalStateException) {
        if (isDetachedFocusTarget(e)) false else throw e
    }

/**
 * Focus entry of a modal panel shown through a [GlassOverlayHost]: once shown, stores where focus was
 * (see [OverlayFocusMemory]) and moves focus onto the panel container (put [modalPanelFocus] on it);
 * D-pad / Tab then reach its items. Null outside an overlay entry (the CapsuleDialog platform-Dialog
 * fallback): the panel then neither becomes focusable nor requests focus.
 */
@Composable
internal fun rememberModalPanelFocus(): FocusRequester? {
    val host = LocalGlassOverlayHost.current
    val entry = LocalGlassOverlayEntry.current
    if (host == null || entry == null) return null
    val requester = remember(entry) { FocusRequester() }
    LaunchedEffect(entry, requester) {
        host.onModalPanelShown(entry, requester)
        tryFocus { requester.requestFocus() }
    }
    return requester
}

/** Makes the panel container the focus target of [requester] (focus lands on it, then its items); no-op for null. */
internal fun Modifier.modalPanelFocus(requester: FocusRequester?): Modifier =
    if (requester == null) this else focusRequester(requester).focusable()
