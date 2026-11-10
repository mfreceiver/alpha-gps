package com.sasch.cameragps.sharednew.ui.components.glass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Overlay entry lifecycle against the panel focus effect (P2-2), with controlled focus targets
 * instead of Compose: the order of register / panel effect / close is driven by hand, so the late
 * effects that real frames can produce are reproduced deterministically. Real Compose wiring:
 * androidHostTest `OverlayFocusComposeTest`.
 */
class OverlayHostLifecycleTest {
    /** A focus target: records calls; [hasFocus] decides whether a path is saved below it. */
    private class FakeTarget(
        val name: String,
        val log: MutableList<String>,
        var hasFocus: Boolean = true,
        var restores: Boolean = true,
    ) : OverlayFocusBinding {
        override fun moveFocusHere(): Boolean {
            log += "focus $name"
            return true
        }

        override fun captureFocusPath(): OverlayFocusPath? {
            log += "save $name"
            if (!hasFocus) return null
            return OverlayFocusPath {
                log += "restore $name"
                restores
            }
        }
    }

    private val log = mutableListOf<String>()
    private val host = GlassOverlayHost()
    private val page = FakeTarget("page", log).also { host.pageFocus.bind(it) }

    private fun entry(): GlassOverlayEntry = GlassOverlayEntry({}, null, modal = true, returnFocus = null)

    private fun panel(name: String, hasFocus: Boolean = true, restores: Boolean = true): OverlayFocusHandle =
        OverlayFocusHandle().also { it.bind(FakeTarget(name, log, hasFocus, restores)) }

    private fun settle(): FocusReturn = host.focusMemory.settle(host.modalEntries())

    private fun assertNothingStored() {
        assertEquals(0, host.focusMemory.depth)
        assertFalse(host.focusMemory.hasPendingReturn)
    }

    @Test
    fun aPanelShownAndClosedNormallyReturnsFocusToThePage() {
        val dialog = entry()
        host.register(dialog)
        assertTrue(host.showModalPanel(dialog, panel("dialog")))
        host.unregister(dialog)
        assertEquals(FocusReturn.Saved, settle())
        assertEquals(listOf("save page", "focus dialog", "restore page"), log)
        assertNothingStored()
    }

    @Test
    fun overlayClosedBeforeItsPanelEffectRanIsNeverShown() {
        // Frame 0 registers the entry; the overlay is closed before the frame that composes its
        // panel applies, so the panel effect runs after the entry left the host.
        val dialog = entry()
        host.register(dialog)
        host.unregister(dialog)
        assertFalse(host.showModalPanel(dialog, panel("dialog")))
        assertEquals(emptyList(), log, "no saved path, no focus request on the closed panel")
        assertNull(dialog.panelFocus)
        assertNothingStored()
        assertEquals(FocusReturn.None, settle())
        assertEquals(emptyList(), log)
    }

    @Test
    fun aShowCallbackLaterThanTheCloseNeitherRevivesTheLayerNorTakesItsReturn() {
        val dialog = entry()
        host.register(dialog)
        val dialogPanel = panel("dialog")
        assertTrue(host.showModalPanel(dialog, dialogPanel))
        host.unregister(dialog)
        // The panel effect runs again (restarted / delayed) after the close.
        assertFalse(host.showModalPanel(dialog, dialogPanel))
        assertTrue(host.focusMemory.hasPendingReturn, "the closed layer's return is still due")
        assertEquals(FocusReturn.Saved, settle())
        assertEquals(listOf("save page", "focus dialog", "restore page"), log, "no second focus request")
        assertNothingStored()
    }

    @Test
    fun aPanelLeavingTheCompositionTakesItsStoredReturnWithIt() {
        val dialog = entry()
        host.register(dialog)
        val first = panel("dialog")
        host.showModalPanel(dialog, first)
        host.onModalPanelGone(dialog, first)
        assertEquals(0, host.focusMemory.depth)
        assertNull(dialog.panelFocus)
        // The entry is still up without a panel: the return waits for its next panel.
        assertEquals(FocusReturn.Deferred, settle())
        val second = panel("dialog again")
        assertTrue(host.showModalPanel(dialog, second))
        assertFalse(host.focusMemory.hasPendingReturn, "the new panel took the return over")
        host.unregister(dialog)
        host.onModalPanelGone(dialog, second) // the panel's own dispose after the entry's: no-op
        assertEquals(FocusReturn.Saved, settle())
        assertEquals("restore page", log.last())
        assertNothingStored()
    }

    @Test
    fun dialogOverDialogRestoresTheLowerPanelsSavedPathFirst() {
        val lower = entry()
        host.register(lower)
        host.showModalPanel(lower, panel("lower"))
        val upper = entry()
        host.register(upper)
        host.showModalPanel(upper, panel("upper"))
        host.unregister(upper)
        assertEquals(FocusReturn.Saved, settle())
        assertEquals(listOf("save page", "focus lower", "save lower", "focus upper", "restore lower"), log)
    }

    @Test
    fun aLowerPathThatCannotBeRestoredFallsBackToItsPanelAndSaysSo() {
        // The saved path ends below a focus group the overlay system does not own: the restore
        // fails instead of passing the panel container off as the saved child.
        val lower = entry()
        host.register(lower)
        host.showModalPanel(lower, panel("lower", restores = false))
        val upper = entry()
        host.register(upper)
        host.showModalPanel(upper, panel("upper"))
        host.unregister(upper)
        assertEquals(FocusReturn.Container, settle())
        assertEquals(listOf("restore lower", "focus lower"), log.takeLast(2))
    }

    @Test
    fun touchUseSavesNothingAndForcesNoFocus() {
        page.hasFocus = false
        val dialog = entry()
        host.register(dialog)
        host.showModalPanel(dialog, panel("dialog"))
        host.unregister(dialog)
        assertEquals(FocusReturn.None, settle())
        assertEquals(listOf("save page", "focus dialog"), log)
    }

    @Test
    fun aDetachedHandleIsAPlainFalseNotACrash() {
        val handle = OverlayFocusHandle()
        assertFalse(handle.isAttached)
        assertFalse(handle.moveFocusHere())
        assertNull(handle.captureFocusPath())
        val target = FakeTarget("t", log)
        handle.bind(target)
        assertTrue(handle.moveFocusHere())
        handle.unbind(FakeTarget("other", log)) // a stale node detaching does not unbind the new one
        assertTrue(handle.isAttached)
        handle.unbind(target)
        assertFalse(handle.moveFocusHere())
        assertEquals(listOf("focus t"), log)
    }
}
