package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.components.ScrollbarLazyColumn
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real Compose focus behaviour of the in-page overlays on the JVM (Robolectric, keyboard input
 * mode): which element gets focus back when a modal overlay closes (P2-1), and that an overlay
 * closed before its panel effect ran leaves nothing behind (P2-2). Pure decisions:
 * OverlayFocusMemoryTest / OverlayHostLifecycleTest (commonTest). Device-only checks (TalkBack,
 * hardware keys, IME): docs/todo.md #27.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayFocusComposeTest {
    @get:Rule
    val compose = createComposeRule()

    private var first by mutableStateOf(false)
    private var second by mutableStateOf(false)
    private var menuOpen by mutableStateOf(false)
    private var sheetOpen by mutableStateOf(false)
    private lateinit var inputModes: InputModeManager
    private lateinit var host: GlassOverlayHost
    private var watching = false

    private fun pane(title: String) = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)

    /** Keyboard / D-pad use: buttons take focus (in touch mode they do not). */
    private fun keyboardMode() = compose.runOnIdle { inputModes.requestInputMode(InputMode.Keyboard) }

    private fun show(page: @Composable () -> Unit = {}, firstContent: @Composable () -> Unit = {}) {
        compose.setContent {
            inputModes = LocalInputModeManager.current
            CameraGpsTheme {
                GlassBackground {
                    host = LocalGlassOverlayHost.current!!
                    Column {
                        Box(Modifier.size(48.dp).focusable().testTag("page"))
                        page()
                    }
                    if (first) {
                        CapsuleDialog(
                            onDismissRequest = { first = false },
                            confirm = DialogAction("Open second", { second = true }),
                            dismiss = DialogAction("Cancel", { first = false }),
                            title = "First",
                        ) { firstContent() }
                    }
                    if (second) {
                        CapsuleDialog(
                            onDismissRequest = { second = false },
                            confirm = null,
                            dismiss = DialogAction("Close", { second = false }),
                            title = "Second",
                        ) {}
                    }
                }
            }
        }
        keyboardMode()
    }

    /** Opens the second dialog (no anchor) over the first, closes it again. */
    private fun openAndCloseSecond() {
        compose.runOnIdle { second = true }
        compose.onNode(pane("Second")).assertIsFocused()
        compose.onNodeWithText("Open second").assertDoesNotExist() // covered: out of the a11y tree
        forgetReturn()
        compose.runOnIdle { second = false }
        compose.waitForIdle()
    }

    /**
     * Kind of the focus return the closes since [forgetReturn] ended with, read from the real
     * [OverlayFocusMemory] (what the focus ends up on cannot tell Saved from Container).
     */
    private fun lastReturn(): FocusReturn? = compose.runOnIdle { host.focusMemory.lastReturn }

    private fun forgetReturn() = compose.runOnIdle { host.focusMemory.lastReturn = null }

    private fun assertNothingStored() = compose.runOnIdle {
        assertEquals(0, host.focusMemory.depth)
        assertFalse(host.focusMemory.hasPendingReturn)
    }

    @Test
    fun dialogOverDialogReturnsToTheLowerDialogsButton() {
        show()
        compose.onNodeWithTag("page").requestFocus()
        compose.runOnIdle { first = true }
        compose.onNode(pane("First")).assertIsFocused()
        compose.onNodeWithText("Open second").requestFocus()
        compose.onNodeWithText("Open second").assertIsFocused()

        openAndCloseSecond()
        compose.onNodeWithText("Open second").assertIsFocused()
        compose.onNode(pane("First")).assertIsNotFocused()
        assertEquals(FocusReturn.Saved, lastReturn()) // the real saved path, not the panel container

        // And the page button once the first dialog closes too.
        forgetReturn()
        compose.runOnIdle { first = false }
        compose.waitForIdle()
        compose.onNodeWithTag("page").assertIsFocused()
        assertEquals(FocusReturn.Saved, lastReturn())
        assertNothingStored()
    }

    @Test
    fun theSiblingButtonIsNotConfusedWithTheSavedOne() {
        show()
        compose.onNodeWithTag("page").requestFocus()
        compose.runOnIdle { first = true }
        compose.onNodeWithText("Cancel").requestFocus()
        openAndCloseSecond()
        compose.onNodeWithText("Cancel").assertIsFocused()
        compose.onNodeWithText("Open second").assertIsNotFocused()
        assertEquals(FocusReturn.Saved, lastReturn())
    }

    @Test
    fun dialogOverDialogReturnsToTheItemInsideTheLowerDialogsScrollingContent() {
        show(firstContent = {
            Box(Modifier.size(48.dp).focusable().testTag("item1"))
            Box(Modifier.size(48.dp).focusable().testTag("item2"))
        })
        compose.onNodeWithTag("page").requestFocus()
        compose.runOnIdle { first = true }
        compose.onNodeWithTag("item2").requestFocus()
        compose.onNodeWithTag("item2").assertIsFocused()
        openAndCloseSecond()
        compose.onNodeWithTag("item2").assertIsFocused()
        assertEquals(FocusReturn.Saved, lastReturn())
    }

    @Test
    fun anItemBelowAFocusGroupTheOverlayDoesNotOwnFallsBackToTheLowerPanelNotAWrongItem() {
        // Known limit (docs/todo.md #27): the caller's own scroll container inside the dialog is a
        // focus group the overlay system cannot save into; the return lands on the dialog panel.
        show(firstContent = {
            Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                Box(Modifier.size(48.dp).focusable().testTag("nested1"))
                Box(Modifier.size(48.dp).focusable().testTag("nested2"))
            }
        })
        compose.onNodeWithTag("page").requestFocus()
        compose.runOnIdle { first = true }
        compose.onNodeWithTag("nested2").requestFocus()
        openAndCloseSecond()
        compose.onNode(pane("First")).assertIsFocused()
        compose.onNodeWithTag("nested1").assertIsNotFocused()
        compose.onNodeWithTag("nested2").assertIsNotFocused()
        // The real restore closure failed and the panel container took over: reported as such.
        assertEquals(FocusReturn.Container, lastReturn())
    }

    @Test
    fun dialogFromARowOfAPageListReturnsToThatRow() {
        show(page = {
            ScrollbarLazyColumn(Modifier.heightIn(max = 300.dp)) {
                items(3) { i -> Box(Modifier.size(48.dp).focusable().testTag("row$i")) }
            }
        })
        compose.onNodeWithTag("row2").requestFocus()
        compose.runOnIdle { first = true }
        compose.onNode(pane("First")).assertIsFocused()
        forgetReturn()
        compose.runOnIdle { first = false }
        compose.waitForIdle()
        compose.onNodeWithTag("row2").assertIsFocused()
        assertEquals(FocusReturn.Saved, lastReturn())
        assertNothingStored()
    }

    @Test
    fun menuReturnsFocusToItsAnchor() {
        show(page = {
            val anchor = rememberGlassAnchor()
            CapsuleButton(text = "More", onClick = { menuOpen = true }, modifier = Modifier.glassAnchor(anchor))
            GlassMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                anchor = anchor,
                items = listOf(GlassMenuItem("Rename", onClick = {})),
                paneTitle = "Options",
            )
        })
        compose.onNodeWithText("More").requestFocus()
        compose.runOnIdle { menuOpen = true }
        compose.onNode(pane("Options")).assertIsFocused()
        forgetReturn()
        compose.onNodeWithText("Rename").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("More").assertIsFocused()
        assertEquals(FocusReturn.Anchor, lastReturn())
        assertNothingStored()
    }

    @Test
    fun touchUseForcesNoFocusOnTheOpeningButton() {
        // Touch use: nothing on the page had focus, so the close puts focus nowhere (no focus ring
        // on the button that opened the dialog). The always-focusable "page" box is not asserted:
        // the platform may hand initial focus to it once the focused panel detaches.
        show(page = { CapsuleButton(text = "Open", onClick = { first = true }) })
        compose.runOnIdle { inputModes.requestInputMode(InputMode.Touch) }
        compose.onNodeWithText("Open").performClick()
        compose.onNode(pane("First")).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        compose.onNode(pane("First")).assertDoesNotExist()
        compose.onNodeWithText("Open").assertIsNotFocused()
        // lastReturn() is not asserted: the always-focusable "page" box may hold the initial focus
        // here, which makes the return Saved onto that box (the None path: OverlayHostLifecycleTest).
        assertNothingStored()
    }

    @Test
    fun dialogClosedBeforeItsPanelEffectRanLeavesNoFrameAndTakesNoFocus() {
        var focusLost = false
        show(page = {
            Box(
                Modifier.size(48.dp)
                    .onFocusChanged { if (watching && !it.isFocused) focusLost = true }
                    .focusable()
                    .testTag("watched"),
            )
            // Closes the dialog in the same apply that registers its overlay entry: the next
            // frame composes the entry's panel (the host still lists it) while removing the dialog,
            // so the panel effect runs only after the entry left the host.
            if (first) {
                DisposableEffect(Unit) {
                    first = false
                    onDispose {}
                }
            }
        })
        compose.onNodeWithTag("watched").requestFocus()
        compose.runOnIdle {
            watching = true
            first = true
        }
        compose.waitForIdle()
        compose.onNode(pane("First")).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, host.entries.size)
            // The closed panel never requested focus: the page element kept it the whole time.
            assertFalse(focusLost)
        }
        compose.onNodeWithTag("watched").assertIsFocused()
        assertNothingStored()
        assertNull("no layer was ever stored, so no focus return ran", lastReturn())
    }

    // A caller-owned scroll area inside a *sheet / popover*: these overlays put their own focus
    // level on the scroll column, so the leaf inside is restored (Saved), like in a dialog.

    @Test
    fun dialogOverBottomSheetReturnsToTheLeafInTheSheetsScrollArea() {
        show(page = {
            GlassBottomSheet(visible = sheetOpen, onDismissRequest = { sheetOpen = false }, title = "Sheet") {
                Box(Modifier.size(48.dp).focusable().testTag("sheet1"))
                Box(Modifier.size(48.dp).focusable().testTag("sheet2"))
            }
        })
        compose.onNodeWithTag("page").requestFocus()
        compose.runOnIdle { sheetOpen = true }
        compose.onNode(pane("Sheet")).assertIsFocused()
        compose.onNodeWithTag("sheet2").requestFocus()
        compose.onNodeWithTag("sheet2").assertIsFocused()

        openAndCloseSecond()
        compose.onNodeWithTag("sheet2").assertIsFocused()
        compose.onNodeWithTag("sheet1").assertIsNotFocused()
        assertEquals(FocusReturn.Saved, lastReturn())

        // Closing the sheet itself hands focus back to the page element that had it.
        forgetReturn()
        compose.runOnIdle { sheetOpen = false }
        compose.waitForIdle()
        compose.onNodeWithTag("page").assertIsFocused()
        assertEquals(FocusReturn.Saved, lastReturn())
        assertNothingStored()
    }

    @Test
    fun dialogOverPopoverReturnsToTheLeafInThePopoversContent() {
        show(page = {
            val anchor = rememberGlassAnchor()
            CapsuleButton(text = "More", onClick = { menuOpen = true }, modifier = Modifier.glassAnchor(anchor))
            GlassPopover(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                anchor = anchor,
                paneTitle = "Popover",
            ) {
                Box(Modifier.size(48.dp).focusable().testTag("pop1"))
                Box(Modifier.size(48.dp).focusable().testTag("pop2"))
            }
        })
        compose.onNodeWithText("More").requestFocus()
        compose.runOnIdle { menuOpen = true }
        compose.onNode(pane("Popover")).assertIsFocused()
        compose.onNodeWithTag("pop2").requestFocus()
        compose.onNodeWithTag("pop2").assertIsFocused()

        openAndCloseSecond()
        compose.onNodeWithTag("pop2").assertIsFocused()
        compose.onNodeWithTag("pop1").assertIsNotFocused()
        assertEquals(FocusReturn.Saved, lastReturn())

        // Closing the popover returns to the button that opened it.
        forgetReturn()
        compose.runOnIdle { menuOpen = false }
        compose.waitForIdle()
        compose.onNodeWithText("More").assertIsFocused()
        assertEquals(FocusReturn.Anchor, lastReturn())
        assertNothingStored()
    }
}
