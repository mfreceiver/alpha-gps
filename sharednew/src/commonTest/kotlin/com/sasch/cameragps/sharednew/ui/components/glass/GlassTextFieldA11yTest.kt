package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Accessible name / description / error of a [GlassTextField] (P2-2): the visible label is a
 * separate Text, so the editable node itself must carry it, or TalkBack announces a nameless
 * "edit box". The Compose wiring (single node, still settable, decorations not read twice) is
 * covered on device by `GlassTextFieldSemanticsTest` in app/src/androidTest.
 */
class GlassTextFieldA11yTest {
    private val fallback = "Invalid input"

    private fun a11y(
        label: String? = "Camera SSID",
        placeholder: String? = "DIRECT-xxxx",
        supportingText: String? = null,
        isError: Boolean = false,
        valueEmpty: Boolean = true,
    ) = glassTextFieldA11y(label, placeholder, supportingText, isError, valueEmpty, fallback)

    private fun semanticsOf(a: GlassTextFieldA11y) = SemanticsConfiguration().apply { applyGlassTextFieldA11y(a) }

    @Test
    fun emptyAndFilledFieldsAreNamedByTheLabel() {
        listOf(true, false).forEach { empty ->
            val cfg = semanticsOf(a11y(valueEmpty = empty))
            assertEquals(listOf("Camera SSID"), cfg.getOrNull(SemanticsProperties.ContentDescription), "valueEmpty=$empty")
        }
    }

    @Test
    fun placeholderIsTheHintOnlyWhileEmpty() {
        assertEquals("DIRECT-xxxx", semanticsOf(a11y(valueEmpty = true)).getOrNull(SemanticsProperties.StateDescription))
        assertNull(semanticsOf(a11y(valueEmpty = false)).getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun withoutLabelThePlaceholderNamesTheField() {
        listOf(true, false).forEach { empty ->
            val cfg = semanticsOf(a11y(label = null, valueEmpty = empty))
            assertEquals(listOf("DIRECT-xxxx"), cfg.getOrNull(SemanticsProperties.ContentDescription))
        }
        // Neither: no invented name (the caller must label it), but nothing crashes.
        assertNull(semanticsOf(a11y(label = null, placeholder = null)).getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun supportingTextIsADescriptionUnlessItIsTheError() {
        val ok = semanticsOf(a11y(supportingText = "2.4 GHz only", valueEmpty = false))
        assertEquals("2.4 GHz only", ok.getOrNull(SemanticsProperties.StateDescription))
        assertNull(ok.getOrNull(SemanticsProperties.Error))
        // Empty + supporting: both, hint first.
        assertEquals(
            "DIRECT-xxxx, 2.4 GHz only",
            semanticsOf(a11y(supportingText = "2.4 GHz only")).getOrNull(SemanticsProperties.StateDescription),
        )
    }

    @Test
    fun errorCarriesTheSupportingTextOrAFallback() {
        val withText = semanticsOf(a11y(supportingText = "Password too short", isError = true, valueEmpty = false))
        assertEquals("Password too short", withText.getOrNull(SemanticsProperties.Error))
        // The error text is not repeated as a state description.
        assertNull(withText.getOrNull(SemanticsProperties.StateDescription))
        val bare = semanticsOf(a11y(supportingText = null, isError = true, valueEmpty = false))
        assertEquals(fallback, bare.getOrNull(SemanticsProperties.Error))
        // The name survives the error state.
        assertEquals(listOf("Camera SSID"), bare.getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun theNameBlockDoesNotReplaceTheEditableNode() {
        // Applied with a plain (non-clearing, non-merging) Modifier.semantics on the BasicTextField,
        // so BasicTextField keeps its own EditableText / SetText / SetSelection; the block itself
        // must not define (and thereby shadow) any of them.
        val cfg = semanticsOf(a11y(supportingText = "x", isError = true))
        assertFalse(cfg.isClearingSemantics)
        assertFalse(cfg.isMergingSemanticsOfDescendants)
        assertNull(cfg.getOrNull(SemanticsActions.SetText))
        assertNull(cfg.getOrNull(SemanticsActions.SetSelection))
        assertNull(cfg.getOrNull(SemanticsProperties.EditableText))
    }
}
