package com.sasch.cameragps.sharednew.ui.components.glass

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where keyboard focus goes back to when in-page modal overlays close (P2-3). The Compose wiring
 * (page focus group blocked while modal, panel takes focus, anchor gets it back) is exercised on a
 * device by `GlassOverlayFocusTest` in app/src/androidTest; this pins the decisions.
 */
class OverlayFocusMemoryTest {
    private class Recorder {
        val calls = mutableListOf<String>()
        fun saver(result: Boolean): () -> Boolean = { calls += "save"; result }
        fun anchor(result: Boolean): () -> Boolean = { calls += "anchor"; result }
        fun page(result: Boolean): () -> Boolean = { calls += "page"; result }
    }

    @Test
    fun popoverOpenedFromAFocusedAnchorReturnsFocusToTheAnchor() {
        val m = OverlayFocusMemory(); val r = Recorder()
        m.noteAnchor(r.anchor(true))
        m.onModalPanelShown(r.saver(true))
        assertTrue(m.sessionOpen)
        assertEquals(FocusReturn.Anchor, m.onAllModalsClosed(r.page(true)))
        assertEquals(listOf("save", "anchor"), r.calls)
        assertFalse(m.sessionOpen)
    }

    @Test
    fun detachedAnchorFallsBackToThePageFocusedChild() {
        val m = OverlayFocusMemory(); val r = Recorder()
        m.noteAnchor(r.anchor(false))
        m.onModalPanelShown(r.saver(true))
        assertEquals(FocusReturn.Page, m.onAllModalsClosed(r.page(true)))
        assertEquals(listOf("save", "anchor", "page"), r.calls)
    }

    @Test
    fun dialogWithoutAnchorRestoresThePageFocusedChild() {
        val m = OverlayFocusMemory(); val r = Recorder()
        m.onModalPanelShown(r.saver(true))
        assertEquals(FocusReturn.Page, m.onAllModalsClosed(r.page(true)))
        assertEquals(listOf("save", "page"), r.calls)
    }

    @Test
    fun nothingFocusedBeforeMeansNoFocusIsForcedAfterwards() {
        // Touch use: no keyboard focus on the page when the menu opened → do not put focus anywhere
        // on close (no stray focus ring), not even on the anchor.
        val m = OverlayFocusMemory(); val r = Recorder()
        m.noteAnchor(r.anchor(true))
        m.onModalPanelShown(r.saver(false))
        assertEquals(FocusReturn.None, m.onAllModalsClosed(r.page(true)))
        assertEquals(listOf("save"), r.calls)
    }

    @Test
    fun chainedOverlaysSaveOnceAndReturnToTheFirstAnchor() {
        // Menu item opens a dialog in the same frame: the modal session never ends in between.
        val m = OverlayFocusMemory(); val r = Recorder()
        m.noteAnchor(r.anchor(true))
        m.onModalPanelShown(r.saver(true))
        m.onModalPanelShown(r.saver(false)) // dialog panel: page focus is not saved again
        assertEquals(FocusReturn.Anchor, m.onAllModalsClosed(r.page(true)))
        assertEquals(listOf("save", "anchor"), r.calls)
    }

    @Test
    fun aClosedSessionForgetsItsAnchor() {
        val m = OverlayFocusMemory(); val r = Recorder()
        m.noteAnchor(r.anchor(true))
        m.onModalPanelShown(r.saver(true))
        m.onAllModalsClosed(r.page(true))
        r.calls.clear()
        // Next overlay is a dialog without anchor: must not jump to the old menu button.
        m.onModalPanelShown(r.saver(true))
        assertEquals(FocusReturn.Page, m.onAllModalsClosed(r.page(true)))
        assertEquals(listOf("save", "page"), r.calls)
    }

    @Test
    fun closingWithoutASessionDoesNothing() {
        val m = OverlayFocusMemory(); val r = Recorder()
        assertEquals(FocusReturn.None, m.onAllModalsClosed(r.page(true)))
        m.noteAnchor(r.anchor(true))
        assertEquals(FocusReturn.None, m.onAllModalsClosed(r.page(true)))
        assertTrue(r.calls.isEmpty())
    }

    @Test
    fun pageTakesFocusOnlyWithoutAModalOverlay() {
        assertTrue(pageAcceptsFocus(hasModalOverlay = false))
        assertFalse(pageAcceptsFocus(hasModalOverlay = true))
    }

    @Test
    fun focusAttemptSwallowsOnlyDetachedTargetFailures() {
        assertTrue(tryFocus { true })
        assertFalse(tryFocus { false })
        assertFalse(tryFocus { throw IllegalStateException("FocusRequester is not initialized") })
        assertFailsWith<CancellationException> { tryFocus { throw CancellationException("gone") } }
        assertFailsWith<IllegalArgumentException> { tryFocus { throw IllegalArgumentException("bug") } }
    }
}
