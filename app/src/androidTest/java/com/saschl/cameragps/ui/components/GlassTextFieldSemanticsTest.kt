package com.saschl.cameragps.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTextField
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * P2-2 on a device: the editable node of a GlassTextField carries the label as its name (empty or
 * not), the error as an error, still accepts text, and the visible label / placeholder / supporting
 * Texts are not separate accessibility nodes (no double reading). Host tests only cover the pure
 * mapping (sharednew GlassTextFieldA11yTest); this needs an emulator / device run (docs/todo.md #23).
 */
class GlassTextFieldSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private var value by mutableStateOf("")

    private fun show(isError: Boolean = false, supportingText: String? = null) {
        compose.setContent {
            CameraGpsTheme {
                var text by remember { mutableStateOf(value) }
                GlassTextField(
                    value = text,
                    onValueChange = { text = it; value = it },
                    label = "Camera SSID",
                    placeholder = "DIRECT-xxxx",
                    supportingText = supportingText,
                    isError = isError,
                )
            }
        }
    }

    private val field = hasSetTextAction() and hasContentDescription("Camera SSID")

    @Test
    fun emptyAndFilledFieldIsNamedByItsLabelAndStillEditable() {
        show()
        compose.onNode(field).assertExists()
        compose.onNode(field).performTextInput("DIRECT-ab12")
        compose.waitForIdle()
        assertEquals("DIRECT-ab12", value)
        // Still named once it has a value (the placeholder is gone).
        compose.onNode(field and hasText("DIRECT-ab12")).assertExists()
        compose.onNode(field).performTextReplacement("x")
        compose.waitForIdle()
        assertEquals("x", value)
    }

    @Test
    fun errorIsOnTheEditableNode() {
        show(isError = true, supportingText = "Too short")
        compose.onNode(field and SemanticsMatcher.expectValue(SemanticsProperties.Error, "Too short")).assertExists()
        compose.onNode(field and SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)).assertExists()
    }

    @Test
    fun decorationTextsAreNotReadTwice() {
        show(supportingText = "2.4 GHz only")
        // Only the editable node mentions the label / placeholder / supporting text.
        compose.onAllNodesWithText("Camera SSID").assertCountEquals(0)
        compose.onAllNodesWithText("DIRECT-xxxx").assertCountEquals(0)
        compose.onAllNodesWithText("2.4 GHz only").assertCountEquals(0)
        compose.onNode(field and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "DIRECT-xxxx, 2.4 GHz only"))
            .assertExists()
    }
}
