package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.focus.restoreFocusedChild
import androidx.compose.ui.focus.saveFocusedChild
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.TraversableNode.Companion.TraverseDescendantsAction
import androidx.compose.ui.node.traverseDescendants
import androidx.compose.ui.platform.InspectorInfo
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
 *  2. when a modal panel shows, the focus path of the uncovered layer below it is saved — down to
 *     the focused element through every focus level the overlay system owns (panel, dialog / sheet
 *     content, ScrollbarLazyColumn: [overlayFocusLevel], [OverlayFocusNode.captureFocusPath]) — and
 *     focus moves onto the panel container ([rememberModalPanelFocus]); only a still registered
 *     overlay entry may do this ([GlassOverlayHost.showModalPanel]);
 *  3. closing a layer returns focus to what it covered, layer by layer ([OverlayFocusMemory]): its
 *     anchor (popover / menu), else the saved path of the layer below, else that layer's panel
 *     container — only if that layer had focus before (touch use stays focus-free). An element
 *     below a focus group the overlay system does not own (a caller's own scroll container) is
 *     restored only up to the nearest owned level; then the return reports Container, not Saved.
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
     * The caller only reports layers that are still shown (see [GlassOverlayHost.showModalPanel]).
     * Returns true: the layer is stored (now or already).
     */
    fun onLayerShown(key: Any, anchor: FocusAttempt?, saveBelow: () -> Boolean, restoreBelow: List<FocusAttempt>): Boolean {
        if (frames.any { it.key === key }) return true
        val replaced = pending
        frames += if (replaced != null) {
            pending = null
            Frame(key, replaced.hadFocus, anchor, replaced.returnTo)
        } else {
            Frame(key, saveBelow(), anchor, restoreBelow)
        }
        return true
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
     * its panel yet (it takes the return over in [onLayerShown]). A stored layer that is not live
     * any more but was never closed (an orphan) is closed here first, so nothing outlives its layer.
     */
    fun settle(liveModalKeys: List<Any>): FocusReturn {
        frames.filter { frame -> liveModalKeys.none { it === frame.key } }.forEach { onLayerClosed(it.key) }
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
internal fun Modifier.overlayLayerGate(group: OverlayFocusHandle, covered: () -> Boolean): Modifier = this
    .then(OverlayFocusElement(group))
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
 * Runs a focus call. Second line of defence only: every overlay focus call goes through an
 * [OverlayFocusHandle] whose node is attached (checked before the call, see [OverlayFocusNode]);
 * this catches what Compose may still report for a focus target below it that detaches during the
 * same frame (its own TODO b/278765590), matched by message — re-check [DETACHED_FOCUS_TARGET] on
 * every Compose upgrade (docs/todo.md #27). Cancellation and every other failure (including other
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

// ---- focus handles and saved focus paths -------------------------------------------------------

/** A saved focus path of one layer; [restore] puts focus back on its end (false: that is gone). */
internal fun interface OverlayFocusPath {
    fun restore(): Boolean
}

/** What an [OverlayFocusHandle] is bound to while its modifier node is attached. */
internal interface OverlayFocusBinding {
    /** Moves focus onto the focus target this binding sits on (or the first one inside). */
    fun moveFocusHere(): Boolean

    /** Saves the focused path below (null: nothing below has focus); see [OverlayFocusNode]. */
    fun captureFocusPath(): OverlayFocusPath?
}

/**
 * A focus point of the overlay system (layer gate, panel container, popover anchor). Bound only
 * while its [OverlayFocusNode] is attached, so a call on a detached / not yet attached target is a
 * plain `false` / `null` instead of a Compose exception.
 */
internal class OverlayFocusHandle {
    private var binding: OverlayFocusBinding? = null

    val isAttached: Boolean get() = binding != null

    fun bind(binding: OverlayFocusBinding) {
        this.binding = binding
    }

    fun unbind(binding: OverlayFocusBinding) {
        if (this.binding === binding) this.binding = null
    }

    fun moveFocusHere(): Boolean = binding?.moveFocusHere() ?: false

    fun captureFocusPath(): OverlayFocusPath? = binding?.captureFocusPath()
}

/** Traverse key shared by every [OverlayFocusNode]: the focus levels the overlay system owns. */
private object OverlayFocusLevelKey

/**
 * One focus level the overlay system owns (layer gate, panel, anchor, a component's own scroll
 * container). Sits right before a focus target in the modifier chain: requests / saves / restores
 * go to that target (public [FocusRequesterModifierNode] API), its focus state arrives through
 * [onFocusEvent].
 *
 * Why levels: Compose's `saveFocusedChild` stores only the *direct* focused child of one focus
 * target, and without the experimental `ComposeUiFlags.isFocusRestorationEnabled` nothing else saves
 * one; `restoreFocusedChild` then walks down only as far as every level saved one — and a focusable
 * container on the way (our panel) takes focus itself when the walk breaks below it. So
 * [captureFocusPath] saves the focused child at *every owned level* on the focused path (found with
 * the public [traverseDescendants]) and [OverlayFocusPath.restore] starts at the deepest one. A
 * focus group we do not own (a caller's own LazyColumn / verticalScroll) between the deepest owned
 * level and the focused leaf still breaks the walk: the restore then fails rather than landing on
 * the wrong element, and the caller falls back explicitly (docs/todo.md #27).
 */
internal class OverlayFocusNode(handle: OverlayFocusHandle?) :
    Modifier.Node(),
    FocusRequesterModifierNode,
    FocusEventModifierNode,
    TraversableNode,
    OverlayFocusBinding {
    override val traverseKey: Any get() = OverlayFocusLevelKey

    var handle: OverlayFocusHandle? = handle
        set(value) {
            if (field === value) return
            field?.unbind(this)
            field = value
            if (isAttached) value?.bind(this)
        }

    private var focusState: FocusState? = null

    private val hasFocus: Boolean get() = isAttached && focusState?.hasFocus == true
    private val isFocused: Boolean get() = isAttached && focusState?.isFocused == true

    override fun onFocusEvent(focusState: FocusState) {
        this.focusState = focusState
    }

    override fun onAttach() {
        handle?.bind(this)
    }

    override fun onDetach() {
        handle?.unbind(this)
        focusState = null
    }

    override fun moveFocusHere(): Boolean = isAttached && tryFocus { requestFocus() }

    private fun restoreHere(): Boolean = isAttached && tryFocus { restoreFocusedChild() }

    override fun captureFocusPath(): OverlayFocusPath? {
        if (!hasFocus) return null
        // Owned levels on the focused path, outer → inner (pre-order: nested levels follow their parent).
        val levels = mutableListOf(this)
        traverseDescendants(OverlayFocusLevelKey) { level ->
            if (level is OverlayFocusNode && level.hasFocus) {
                levels += level
                TraverseDescendantsAction.ContinueTraversal
            } else {
                TraverseDescendantsAction.SkipSubtreeAndContinueTraversal // nothing focused below
            }
        }
        val deepest = levels.last()
        if (deepest.isFocused) {
            // The focused element is an owned level itself (a panel container, an anchor).
            return OverlayFocusPath { deepest.moveFocusHere() }
        }
        levels.forEach { level -> if (!level.isFocused) tryFocus { level.saveFocusedChild() } }
        // Innermost first; an outer level only restores through the children saved above, so it
        // never lands on an element that was not on the path (it may stop on a focusable level).
        return OverlayFocusPath { levels.asReversed().any { it.restoreHere() } }
    }
}

private class OverlayFocusElement(val handle: OverlayFocusHandle?) : ModifierNodeElement<OverlayFocusNode>() {
    override fun create() = OverlayFocusNode(handle)

    override fun update(node: OverlayFocusNode) {
        node.handle = handle
    }

    override fun hashCode(): Int = handle.hashCode()

    override fun equals(other: Any?): Boolean = other is OverlayFocusElement && other.handle === handle

    override fun InspectorInfo.inspectableProperties() {
        name = "overlayFocus"
    }
}

/**
 * Marks a component's own focus group (e.g. the scroll container of a panel) as a focus level of
 * the overlay system, so a focus return reaches the element inside it (see [OverlayFocusNode]).
 * Put it right before the modifier that creates the focus group (`verticalScroll`, `LazyColumn`).
 */
internal fun Modifier.overlayFocusLevel(): Modifier = this.then(OverlayFocusElement(null))

/** Makes the element after it the focus return target of [handle] (popover anchors). */
internal fun Modifier.overlayFocusTarget(handle: OverlayFocusHandle): Modifier = this.then(OverlayFocusElement(handle))

/**
 * Focus entry of a modal panel shown through a [GlassOverlayHost]: once shown — and only while its
 * overlay entry is still live — stores where focus was (see [OverlayFocusMemory]) and moves focus
 * onto the panel container (put [modalPanelFocus] on it); D-pad / Tab then reach its items. The
 * panel's stored return is dropped with the panel. Null outside an overlay entry (the
 * CapsuleDialog platform-Dialog fallback): the panel then neither becomes focusable nor requests
 * focus.
 */
@Composable
internal fun rememberModalPanelFocus(): OverlayFocusHandle? {
    val host = LocalGlassOverlayHost.current
    val entry = LocalGlassOverlayEntry.current
    if (host == null || entry == null) return null
    val panel = remember(entry) { OverlayFocusHandle() }
    LaunchedEffect(host, entry, panel) { host.showModalPanel(entry, panel) }
    DisposableEffect(host, entry, panel) {
        onDispose { host.onModalPanelGone(entry, panel) }
    }
    return panel
}

/** Makes the panel container the focus target of [panel] (focus lands on it, then its items); no-op for null. */
internal fun Modifier.modalPanelFocus(panel: OverlayFocusHandle?): Modifier =
    if (panel == null) this else this.then(OverlayFocusElement(panel)).focusable()
