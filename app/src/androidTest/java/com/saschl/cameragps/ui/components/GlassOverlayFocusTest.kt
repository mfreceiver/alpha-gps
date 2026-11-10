package com.saschl.cameragps.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.components.glass.GlassAnchorState
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBottomSheet
import com.sasch.cameragps.sharednew.ui.components.glass.GlassMenu
import com.sasch.cameragps.sharednew.ui.components.glass.GlassMenuItem
import com.sasch.cameragps.sharednew.ui.components.glass.glassAnchor
import com.sasch.cameragps.sharednew.ui.components.glass.rememberGlassAnchor
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * P2-3 on a device: an in-page modal overlay takes keyboard focus when it opens, the layers below
 * (page, lower overlays) can neither take it back nor be reached by accessibility services while it
 * is up, and focus returns layer by layer when overlays close — what stacked platform
 * Popup(focusable = true) / Dialog windows did. Without a GlassBackground, CapsuleDialog keeps the
 * window fallback of 0.2.1 (panel not focusable). Pure decisions: sharednew OverlayFocusMemoryTest.
 * Needs an emulator / device run (docs/todo.md #23 / #27).
 */
class GlassOverlayFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private var open by mutableStateOf(false)
    private var dialogOpen by mutableStateOf(false)
    private var sheetOpen by mutableStateOf(false)
    private var innerMenuOpen by mutableStateOf(false)
    private val anchorFocus = FocusRequester()
    private val innerAnchorFocus = FocusRequester()
    private lateinit var focusManager: FocusManager
    private var pageFocusedWhileOpen = false
    private var lowerLayerFocusedWhileCovered = false

    private fun pane(title: String) = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)

    private fun tabAround() = repeat(4) {
        compose.runOnIdle { focusManager.moveFocus(FocusDirection.Next) }
        compose.runOnIdle { focusManager.moveFocus(FocusDirection.Down) }
    }

    /** Page with a focusable anchor, a second focusable box and whatever overlays [overlays] adds. */
    private fun show(overlays: @Composable (anchor: GlassAnchorState) -> Unit) {
        compose.setContent {
            focusManager = LocalFocusManager.current
            CameraGpsTheme {
                GlassBackground {
                    Column {
                        val anchor = rememberGlassAnchor()
                        Box(
                            Modifier.size(48.dp).glassAnchor(anchor).focusRequester(anchorFocus).focusable().testTag("anchor"),
                        )
                        Box(
                            Modifier.size(48.dp)
                                .onFocusChanged { if (it.isFocused && (open || dialogOpen || sheetOpen)) pageFocusedWhileOpen = true }
                                .focusable()
                                .testTag("other"),
                        )
                        overlays(anchor)
                    }
                }
            }
        }
    }

    private fun showMenu() = show { anchor ->
        GlassMenu(
            expanded = open,
            onDismissRequest = { open = false },
            anchor = anchor,
            items = listOf(GlassMenuItem("Rename", onClick = {})),
            paneTitle = "Options",
        )
    }

    @Test
    fun menuTakesFocusKeepsItAndReturnsItToTheAnchor() {
        showMenu()
        compose.runOnIdle { anchorFocus.requestFocus() }
        compose.onNodeWithTag("anchor").assertIsFocused()

        compose.runOnIdle { open = true }
        compose.onNode(pane("Options")).assertIsFocused()
        // Tab / D-pad from the panel never lands on the page below.
        tabAround()
        compose.runOnIdle { assertFalse(pageFocusedWhileOpen) }

        compose.onNodeWithText("Rename").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("anchor").assertIsFocused()
    }

    @Test
    fun menuOpenedWithoutKeyboardFocusLeavesNoFocusBehind() {
        showMenu()
        compose.runOnIdle { open = true }
        compose.onNode(pane("Options")).assertIsFocused()
        compose.runOnIdle { open = false }
        compose.waitForIdle()
        compose.onNodeWithTag("anchor").assertIsNotFocused()
        compose.onNodeWithTag("other").assertIsNotFocused()
    }

    /** Dialog on the page with a menu inside it (two stacked modal layers). */
    private fun showDialogWithInnerMenu() = show {
        if (dialogOpen) {
            CapsuleDialog(
                onDismissRequest = { dialogOpen = false },
                confirm = null,
                dismiss = DialogAction("Cancel", { dialogOpen = false }),
                title = "Outer",
            ) {
                val inner = rememberGlassAnchor()
                Box(
                    Modifier.size(48.dp)
                        .glassAnchor(inner)
                        .focusRequester(innerAnchorFocus)
                        .onFocusChanged { if (it.isFocused && innerMenuOpen) lowerLayerFocusedWhileCovered = true }
                        .focusable()
                        .testTag("innerAnchor"),
                )
                GlassMenu(
                    expanded = innerMenuOpen,
                    onDismissRequest = { innerMenuOpen = false },
                    anchor = inner,
                    items = listOf(GlassMenuItem("Inner item", onClick = {})),
                    paneTitle = "Inner menu",
                )
            }
        }
    }

    @Test
    fun onlyTheTopMostModalLayerIsReachableAndFocusReturnsLayerByLayer() {
        showDialogWithInnerMenu()
        compose.runOnIdle { anchorFocus.requestFocus() }
        compose.runOnIdle { dialogOpen = true }
        compose.onNode(pane("Outer")).assertIsFocused()
        compose.runOnIdle { innerAnchorFocus.requestFocus() }
        compose.onNodeWithTag("innerAnchor").assertIsFocused()

        compose.runOnIdle { innerMenuOpen = true }
        compose.onNode(pane("Inner menu")).assertIsFocused()
        // The covered dialog is out of the accessibility tree and out of keyboard reach.
        compose.onNode(pane("Outer")).assertDoesNotExist()
        compose.onNodeWithTag("innerAnchor").assertDoesNotExist()
        tabAround()
        compose.runOnIdle {
            assertFalse(lowerLayerFocusedWhileCovered)
            assertFalse(pageFocusedWhileOpen)
        }

        // Closing the menu returns into the dialog (its anchor), not to the page.
        compose.runOnIdle { innerMenuOpen = false }
        compose.waitForIdle()
        compose.onNodeWithTag("innerAnchor").assertIsFocused()
        compose.onNode(pane("Outer")).assertExists()

        // Closing the dialog returns to the page anchor focused before it opened.
        compose.runOnIdle { dialogOpen = false }
        compose.waitForIdle()
        compose.onNodeWithTag("anchor").assertIsFocused()
    }

    @Test
    fun menuItemOpeningADialogReturnsToTheMenuAnchorWhenTheDialogCloses() {
        show { anchor ->
            GlassMenu(
                expanded = open,
                onDismissRequest = { open = false },
                anchor = anchor,
                items = listOf(GlassMenuItem("Delete", onClick = { dialogOpen = true })),
                paneTitle = "Options",
            )
            if (dialogOpen) {
                CapsuleDialog(
                    onDismissRequest = { dialogOpen = false },
                    title = "Confirm",
                    message = null,
                    confirm = null,
                    dismiss = DialogAction("Cancel", { dialogOpen = false }),
                )
            }
        }
        compose.runOnIdle { anchorFocus.requestFocus() }
        compose.runOnIdle { open = true }
        compose.onNodeWithText("Delete").performClick() // menu closes, dialog opens in the same frame
        compose.waitForIdle()
        compose.onNode(pane("Confirm")).assertIsFocused()
        compose.runOnIdle { dialogOpen = false }
        compose.waitForIdle()
        compose.onNodeWithTag("anchor").assertIsFocused()
    }

    @Test
    fun closingTheLowerOverlayFirstKeepsTheUpperOneAndReturnsToThePageLater() {
        show {
            if (dialogOpen) {
                CapsuleDialog(
                    onDismissRequest = { dialogOpen = false },
                    title = "Lower",
                    message = null,
                    confirm = null,
                    dismiss = DialogAction("Cancel", { dialogOpen = false }),
                )
            }
            GlassBottomSheet(visible = sheetOpen, onDismissRequest = { sheetOpen = false }, title = "Upper") {
                Box(Modifier.size(48.dp).focusable().testTag("sheetItem"))
            }
        }
        compose.runOnIdle { anchorFocus.requestFocus() }
        compose.runOnIdle { dialogOpen = true }
        compose.onNode(pane("Lower")).assertIsFocused()
        compose.runOnIdle { sheetOpen = true }
        compose.onNode(pane("Upper")).assertIsFocused()
        compose.onNode(pane("Lower")).assertDoesNotExist()

        compose.runOnIdle { dialogOpen = false } // out of order: the covered layer goes first
        compose.waitForIdle()
        compose.onNode(pane("Upper")).assertIsFocused()

        compose.runOnIdle { sheetOpen = false }
        compose.waitForIdle()
        compose.onNodeWithTag("anchor").assertIsFocused()
    }

    @Test
    fun dialogWithoutGlassBackgroundKeepsTheWindowFallbackFocus() {
        compose.setContent {
            CameraGpsTheme {
                CapsuleDialog(
                    onDismissRequest = {},
                    title = "Fallback",
                    message = null,
                    confirm = null,
                    dismiss = DialogAction("Cancel", {}),
                )
            }
        }
        // 0.2.1 behaviour: the platform Dialog window handles focus; the panel is not made focusable.
        compose.onNode(pane("Fallback")).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
    }
}
