package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.ui.Modifier
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Where keyboard focus goes back to when in-page modal overlays close, layer by layer (P2-3), and
 * which layers may take focus at all. The Compose wiring (layer gates, panel takes focus, returns
 * performed after the overlays settled) is exercised on a device by `GlassOverlayFocusTest` in
 * app/src/androidTest; this pins the decisions.
 */
class OverlayFocusMemoryTest {
    /** Records every focus call by name. */
    private class Recorder {
        val calls = mutableListOf<String>()
        fun step(name: String, result: Boolean): () -> Boolean = { calls += name; result }
        fun anchor(name: String, result: Boolean) = FocusAttempt(FocusReturn.Anchor, step(name, result))
        fun saved(name: String, result: Boolean) = FocusAttempt(FocusReturn.Saved, step(name, result))
        fun container(name: String, result: Boolean) = FocusAttempt(FocusReturn.Container, step(name, result))
    }

    private val m = OverlayFocusMemory()
    private val r = Recorder()

    /** A layer opened over the page: saves the page's focused child ([pageHadFocus]). */
    private fun showOverPage(key: String, pageHadFocus: Boolean = true, anchor: FocusAttempt? = null) =
        m.onLayerShown(key, anchor, r.step("save page", pageHadFocus), listOf(r.saved("page", true)))

    /** A layer opened over overlay [below]: saves [below]'s focused child, falls back to its panel container. */
    private fun showOver(
        below: String,
        key: String,
        anchor: FocusAttempt? = null,
        belowHadFocus: Boolean = true,
        savedRestores: Boolean = true,
    ) = m.onLayerShown(
        key,
        anchor,
        r.step("save $below", belowHadFocus),
        listOf(r.saved(below, savedRestores), r.container("$below panel", true)),
    )

    private fun assertEmpty() {
        assertEquals(0, m.depth)
        assertFalse(m.hasPendingReturn)
    }

    // ---- one layer ---------------------------------------------------------------------------

    @Test
    fun popoverOpenedFromAFocusedAnchorReturnsFocusToTheAnchor() {
        showOverPage("menu", anchor = r.anchor("anchor", true))
        assertEquals(1, m.depth)
        m.onLayerClosed("menu")
        assertEquals(FocusReturn.Anchor, m.settle(emptyList()))
        assertEquals(listOf("save page", "anchor"), r.calls)
        assertEmpty()
    }

    @Test
    fun detachedAnchorFallsBackToThePageFocusedChild() {
        showOverPage("menu", anchor = r.anchor("anchor", false))
        m.onLayerClosed("menu")
        assertEquals(FocusReturn.Saved, m.settle(emptyList()))
        assertEquals(listOf("save page", "anchor", "page"), r.calls)
    }

    @Test
    fun dialogWithoutAnchorRestoresThePageFocusedChild() {
        showOverPage("dialog")
        m.onLayerClosed("dialog")
        assertEquals(FocusReturn.Saved, m.settle(emptyList()))
        assertEquals(listOf("save page", "page"), r.calls)
    }

    @Test
    fun nothingFocusedBeforeMeansNoFocusIsForcedAfterwards() {
        // Touch use: no keyboard focus on the page when the menu opened → do not put focus anywhere
        // on close (no stray focus ring), not even on the anchor.
        showOverPage("menu", pageHadFocus = false, anchor = r.anchor("anchor", true))
        m.onLayerClosed("menu")
        assertEquals(FocusReturn.None, m.settle(emptyList()))
        assertEquals(listOf("save page"), r.calls)
        assertEmpty()
    }

    @Test
    fun aSettledReturnIsForgotten() {
        showOverPage("menu", anchor = r.anchor("anchor", true))
        m.onLayerClosed("menu")
        m.settle(emptyList())
        r.calls.clear()
        // Next overlay is a dialog without anchor: must not jump to the old menu button.
        showOverPage("dialog")
        m.onLayerClosed("dialog")
        assertEquals(FocusReturn.Saved, m.settle(emptyList()))
        assertEquals(listOf("save page", "page"), r.calls)
        assertEquals(FocusReturn.None, m.settle(emptyList()))
    }

    @Test
    fun nothingHappensWithoutAClosedLayer() {
        assertEquals(FocusReturn.None, m.settle(emptyList()))
        m.onLayerClosed("never shown")
        assertEquals(FocusReturn.None, m.settle(emptyList()))
        showOverPage("dialog")
        assertEquals(FocusReturn.None, m.settle(listOf("dialog"))) // still shown: nothing due
        assertEquals(listOf("save page"), r.calls)
    }

    @Test
    fun aPanelShownTwiceIsStoredOnce() {
        showOverPage("dialog")
        showOverPage("dialog")
        assertEquals(1, m.depth)
        assertEquals(listOf("save page"), r.calls)
    }

    // ---- stacked layers -----------------------------------------------------------------------

    @Test
    fun closingTheUpperLayerReturnsToTheLowerLayerNotThePage() {
        showOverPage("dialog")
        showOver("dialog", "menu", anchor = r.anchor("dialog button", true))
        assertEquals(2, m.depth)
        m.onLayerClosed("menu")
        assertEquals(FocusReturn.Anchor, m.settle(listOf("dialog")))
        assertEquals(listOf("save page", "save dialog", "dialog button"), r.calls)
        assertEquals(1, m.depth)
        r.calls.clear()
        m.onLayerClosed("dialog")
        assertEquals(FocusReturn.Saved, m.settle(emptyList()))
        assertEquals(listOf("page"), r.calls)
        assertEmpty()
    }

    @Test
    fun dialogOverDialogReturnsToTheLowerDialogsSavedChild() {
        showOverPage("first")
        showOver("first", "second")
        m.onLayerClosed("second")
        assertEquals(FocusReturn.Saved, m.settle(listOf("first")))
        assertEquals(listOf("save page", "save first", "first"), r.calls)
    }

    @Test
    fun lowerLayerWithoutItsSavedChildGetsFocusOnItsPanel() {
        showOverPage("first")
        showOver("first", "second", savedRestores = false)
        m.onLayerClosed("second")
        assertEquals(FocusReturn.Container, m.settle(listOf("first")))
        assertEquals(listOf("save page", "save first", "first", "first panel"), r.calls)
    }

    @Test
    fun touchUseOnTheLowerLayerIsNotOverriddenByItsAutoFocusedPanel() {
        // Page had no focus; the dialog panel took focus by itself, so the menu on it saved a child.
        showOverPage("dialog", pageHadFocus = false)
        showOver("dialog", "menu", anchor = r.anchor("dialog button", true))
        m.onLayerClosed("menu")
        assertEquals(FocusReturn.Anchor, m.settle(listOf("dialog"))) // back into the still open dialog
        m.onLayerClosed("dialog")
        assertEquals(FocusReturn.None, m.settle(emptyList())) // page stays focus-free
        assertEmpty()
    }

    // ---- replacement (menu item opens a dialog) ------------------------------------------------

    @Test
    fun dialogReplacingAMenuTakesOverTheReturnToTheAnchor() {
        showOverPage("menu", anchor = r.anchor("anchor", true))
        m.onLayerClosed("menu")
        // The dialog entry is live but its panel has not shown yet: the return waits for it.
        assertEquals(FocusReturn.Deferred, m.settle(listOf("dialog")))
        assertTrue(m.hasPendingReturn)
        assertEquals(listOf("save page"), r.calls)
        // The page lost its focus to the menu; the dialog must not save that empty state.
        showOverPage("dialog", pageHadFocus = false)
        assertFalse(m.hasPendingReturn)
        m.onLayerClosed("dialog")
        assertEquals(FocusReturn.Anchor, m.settle(emptyList()))
        assertEquals(listOf("save page", "anchor"), r.calls)
        assertEmpty()
    }

    @Test
    fun replacementWorksWhenThePanelShowsBeforeTheSettle() {
        showOverPage("menu", anchor = r.anchor("anchor", true))
        m.onLayerClosed("menu")
        showOverPage("dialog", pageHadFocus = false)
        assertEquals(FocusReturn.None, m.settle(listOf("dialog")))
        m.onLayerClosed("dialog")
        assertEquals(FocusReturn.Anchor, m.settle(emptyList()))
        assertEquals(listOf("save page", "anchor"), r.calls)
    }

    @Test
    fun replacementAfterTouchUseStaysFocusFree() {
        showOverPage("menu", pageHadFocus = false, anchor = r.anchor("anchor", true))
        m.onLayerClosed("menu")
        showOverPage("dialog", pageHadFocus = false)
        m.onLayerClosed("dialog")
        assertEquals(FocusReturn.None, m.settle(emptyList()))
        assertEquals(listOf("save page"), r.calls)
    }

    @Test
    fun replacementOnALowerLayerReturnsIntoThatLayer() {
        showOverPage("dialog")
        showOver("dialog", "menu", anchor = r.anchor("dialog button", true))
        m.onLayerClosed("menu")
        assertEquals(FocusReturn.Deferred, m.settle(listOf("dialog", "confirm")))
        showOver("dialog", "confirm", belowHadFocus = false)
        m.onLayerClosed("confirm")
        assertEquals(FocusReturn.Anchor, m.settle(listOf("dialog")))
        assertEquals(listOf("save page", "save dialog", "dialog button"), r.calls)
    }

    @Test
    fun aLiveLayerBelowTheTopThatNeverShowedAPanelDoesNotBlockTheReturn() {
        showOverPage("dialog")
        showOver("dialog", "menu", anchor = r.anchor("dialog button", true))
        m.onLayerClosed("menu")
        assertEquals(FocusReturn.Anchor, m.settle(listOf("panel-less", "dialog")))
    }

    // ---- out-of-order close --------------------------------------------------------------------

    @Test
    fun closingTheLowerLayerFirstHandsItsReturnToTheUpperOne() {
        showOverPage("dialog")
        showOver("dialog", "menu", anchor = r.anchor("dialog button", false))
        m.onLayerClosed("dialog") // lower layer first: focus stays in the menu, nothing moves
        assertEquals(FocusReturn.None, m.settle(listOf("menu")))
        assertEquals(1, m.depth)
        assertEquals(listOf("save page", "save dialog"), r.calls)
        r.calls.clear()
        m.onLayerClosed("menu") // its anchor was in the dialog (gone) → what the dialog covered
        assertEquals(FocusReturn.Saved, m.settle(emptyList()))
        assertEquals(listOf("dialog button", "page"), r.calls)
        assertEmpty()
    }

    @Test
    fun closingAMiddleLayerKeepsTheRestOfTheStack() {
        showOverPage("a")
        showOver("a", "b")
        showOver("b", "c")
        m.onLayerClosed("b")
        assertEquals(2, m.depth)
        m.onLayerClosed("c") // c now covers what b covered: a's saved child
        assertEquals(FocusReturn.Saved, m.settle(listOf("a")))
        assertEquals(listOf("save page", "save a", "save b", "a"), r.calls)
        m.onLayerClosed("a")
        m.settle(emptyList())
        assertEmpty()
    }

    @Test
    fun layersClosedTogetherReturnOnceToWhatTheBottomOneCovered() {
        for (order in listOf(listOf("menu", "dialog"), listOf("dialog", "menu"))) {
            val memory = OverlayFocusMemory()
            val rec = Recorder()
            memory.onLayerShown("dialog", null, rec.step("save page", true), listOf(rec.saved("page", true)))
            memory.onLayerShown(
                "menu",
                rec.anchor("dialog button", false),
                rec.step("save dialog", true),
                listOf(rec.saved("dialog", false), rec.container("dialog panel", false)),
            )
            order.forEach { memory.onLayerClosed(it) }
            assertEquals(FocusReturn.Saved, memory.settle(emptyList()), "close order $order")
            assertEquals(0, memory.depth)
            assertFalse(memory.hasPendingReturn)
            assertEquals("page", rec.calls.last(), "close order $order")
            assertEquals(1, rec.calls.count { it == "page" }, "close order $order")
        }
    }

    // ---- which layers may take focus ------------------------------------------------------------

    @Test
    fun onlyTheTopMostModalLayerIsReachable() {
        val page = -1
        // page | dialog | menu (both modal)
        val stack = listOf(true, true)
        assertTrue(layerCovered(page, stack))
        assertTrue(layerCovered(0, stack), "the lower dialog is covered by the menu")
        assertFalse(layerCovered(1, stack))
        // page | dialog
        assertTrue(layerCovered(page, listOf(true)))
        assertFalse(layerCovered(0, listOf(true)))
    }

    @Test
    fun nonModalOverlaysNeitherCoverNorStayReachableBelowAModal() {
        val page = -1
        assertFalse(layerCovered(page, emptyList()))
        assertFalse(layerCovered(page, listOf(false)), "a snackbar does not cover the page")
        // page | snackbar | dialog | tooltip
        val stack = listOf(false, true, false)
        assertTrue(layerCovered(page, stack))
        assertTrue(layerCovered(0, stack), "snackbar below the dialog")
        assertFalse(layerCovered(1, stack), "the dialog is the top-most modal layer")
        assertFalse(layerCovered(2, stack), "tooltip above it")
    }

    // ---- focus requests --------------------------------------------------------------------------

    @Test
    fun focusAttemptSwallowsOnlyDetachedTargetFailures() {
        assertTrue(tryFocus { true })
        assertFalse(tryFocus { false })
        assertFalse(tryFocus { throw IllegalStateException("visitChildren called on an unattached node") })
        assertFalse(tryFocus { throw IllegalStateException("visitAncestors called on an unattached node") })
        assertFalse(tryFocus { throw IllegalStateException("\n   FocusRequester is not initialized. Here are some possible fixes:") })
        assertFailsWith<CancellationException> { tryFocus { throw CancellationException("gone") } }
        assertFailsWith<IllegalArgumentException> { tryFocus { throw IllegalArgumentException("bug") } }
    }

    @Test
    fun focusAttemptRethrowsUnknownIllegalStates() {
        val misuse = IllegalStateException("Please check whether the focusRequester is FocusRequester.Cancel or FocusRequester.Default")
        assertSame(misuse, assertFailsWith<IllegalStateException> { tryFocus { throw misuse } })
        assertFailsWith<IllegalStateException> { tryFocus { throw IllegalStateException() } }
        assertFailsWith<IllegalStateException> { tryFocus { error("some other bug") } }
    }

    @Test
    fun panelWithoutAnOverlayEntryGetsNoFocusHandling() {
        // CapsuleDialog's platform-Dialog fallback has no overlay entry → no requester: the panel
        // must stay exactly as in 0.2.1 (not focusable, no focus request).
        assertSame(Modifier, Modifier.modalPanelFocus(null))
    }
}
