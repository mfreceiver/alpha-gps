package com.saschl.cameragps.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.components.glass.GlassMenu
import com.sasch.cameragps.sharednew.ui.components.glass.GlassMenuItem
import com.sasch.cameragps.sharednew.ui.components.glass.glassAnchor
import com.sasch.cameragps.sharednew.ui.components.glass.rememberGlassAnchor
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * P2-3 on a device: an in-page GlassMenu takes keyboard focus when it opens, the page below cannot
 * take it back while the menu is up, and focus returns to the anchor when it closes — what the
 * platform Popup(focusable = true) did. Pure decisions: sharednew OverlayFocusMemoryTest. Needs an
 * emulator / device run (docs/todo.md #23).
 */
class GlassOverlayFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private var open by mutableStateOf(false)
    private val anchorFocus = FocusRequester()
    private lateinit var focusManager: FocusManager
    private var pageFocusedWhileOpen = false

    private fun show() {
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
                                .onFocusChanged { if (it.isFocused && open) pageFocusedWhileOpen = true }
                                .focusable()
                                .testTag("other"),
                        )
                        GlassMenu(
                            expanded = open,
                            onDismissRequest = { open = false },
                            anchor = anchor,
                            items = listOf(GlassMenuItem("Rename", onClick = {})),
                            paneTitle = "Options",
                        )
                    }
                }
            }
        }
    }

    private val pane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Options")

    @Test
    fun menuTakesFocusKeepsItAndReturnsItToTheAnchor() {
        show()
        compose.runOnIdle { anchorFocus.requestFocus() }
        compose.onNodeWithTag("anchor").assertIsFocused()

        compose.runOnIdle { open = true }
        compose.onNode(pane).assertIsFocused()
        // Tab / D-pad from the panel never lands on the page below.
        repeat(4) {
            compose.runOnIdle { focusManager.moveFocus(FocusDirection.Next) }
            compose.runOnIdle { focusManager.moveFocus(FocusDirection.Down) }
        }
        compose.runOnIdle { assertFalse(pageFocusedWhileOpen) }

        compose.onNodeWithText("Rename").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("anchor").assertIsFocused()
    }

    @Test
    fun menuOpenedWithoutKeyboardFocusLeavesNoFocusBehind() {
        show()
        compose.runOnIdle { open = true }
        compose.onNode(pane).assertIsFocused()
        compose.runOnIdle { open = false }
        compose.waitForIdle()
        compose.onNodeWithTag("anchor").assertIsNotFocused()
        compose.onNodeWithTag("other").assertIsNotFocused()
    }
}
