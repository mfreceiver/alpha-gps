package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlin.coroutines.cancellation.CancellationException

/*
 * Keyboard / D-pad focus of in-page modal overlays (P2-3). A platform Popup(focusable = true) used
 * to give this for free; the in-page overlays (GlassPopover / GlassMenu / CapsuleDialog /
 * GlassBottomSheet) rebuild it without opening a window:
 *  1. on show, the page's focused child is saved and focus moves into the panel container;
 *  2. while any modal overlay is up the page focus group refuses focus ([pageAcceptsFocus]);
 *  3. when the last modal overlay closes, focus goes back to the anchor that opened it (popover /
 *     menu) or else to the saved page child — only if the page had focus before (touch use stays
 *     focus-free).
 * TalkBack is handled separately: GlassBackground already hides the page semantics while modal.
 */

/** Where focus went when the last modal overlay closed. */
internal enum class FocusReturn { None, Anchor, Page }

/** Pure bookkeeping of one "modal session" (from the first modal panel shown until none is left). */
internal class OverlayFocusMemory {
    var sessionOpen: Boolean = false
        private set
    private var pageHadFocus = false
    private var anchor: (() -> Boolean)? = null

    /** A popover / menu is shown from an anchor; [restore] moves focus back to it (false if it cannot). */
    fun noteAnchor(restore: () -> Boolean) {
        anchor = restore
    }

    /** A modal panel appeared. The first one of a session saves the page's focused child. */
    fun onModalPanelShown(savePageFocus: () -> Boolean) {
        if (sessionOpen) return
        sessionOpen = true
        pageHadFocus = savePageFocus()
    }

    /** The last modal overlay is gone: anchor first, then the saved page child; nothing if the page had no focus. */
    fun onAllModalsClosed(restorePageFocus: () -> Boolean): FocusReturn {
        val returnTo = anchor
        anchor = null
        if (!sessionOpen) return FocusReturn.None
        sessionOpen = false
        val hadFocus = pageHadFocus
        pageHadFocus = false
        return when {
            !hadFocus -> FocusReturn.None
            returnTo?.invoke() == true -> FocusReturn.Anchor
            restorePageFocus() -> FocusReturn.Page
            else -> FocusReturn.None
        }
    }
}

/** The page under a [GlassBackground] may take keyboard focus only while no modal overlay is up. */
internal fun pageAcceptsFocus(hasModalOverlay: Boolean): Boolean = !hasModalOverlay

/**
 * Runs a focus request. Compose throws [IllegalStateException] when the target is not attached (yet
 * / any more) — expected around overlay transitions, reported as false. Cancellation and every other
 * failure propagate.
 */
internal fun tryFocus(request: () -> Boolean): Boolean =
    try {
        request()
    } catch (cancelled: CancellationException) {
        throw cancelled // on the JVM a CancellationException *is* an IllegalStateException
    } catch (detached: IllegalStateException) {
        false
    }

/**
 * Focus entry of a modal panel: once shown, saves the page focus (first panel of a session) and moves
 * focus onto the panel container (put [modalPanelFocus] on it); D-pad / Tab then reach its items.
 */
@Composable
internal fun rememberModalPanelFocus(host: GlassOverlayHost?): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) {
        host?.focusMemory?.onModalPanelShown { tryFocus { host.pageFocus.saveFocusedChild() } }
        tryFocus { requester.requestFocus() }
    }
    return requester
}

/** Makes the panel container the focus target of [requester] (focus lands on it, then its items). */
internal fun Modifier.modalPanelFocus(requester: FocusRequester): Modifier = focusRequester(requester).focusable()
