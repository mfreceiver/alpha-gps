package com.sasch.cameragps.sharednew.ui.theme

import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.dialogButtonRoles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GlassTokensTest {
    @Test
    fun levelsMapToThemeTokens() {
        assertSame(GlassTokens.LightCard, GlassTokens.of(GlassLevel.Card, dark = false))
        assertSame(GlassTokens.DarkCard, GlassTokens.of(GlassLevel.Card, dark = true))
        assertSame(GlassTokens.LightFloat, GlassTokens.of(GlassLevel.Float, dark = false))
        assertSame(GlassTokens.DarkModal, GlassTokens.of(GlassLevel.Modal, dark = true))
        // The photo viewer chrome is always dark.
        assertSame(GlassTokens.Viewer, GlassTokens.of(GlassLevel.Viewer, dark = false))
        assertSame(GlassTokens.Viewer, GlassTokens.of(GlassLevel.Viewer, dark = true))
    }

    @Test
    fun blurRadiiFollowDesign() {
        assertEquals(20.dp, GlassTokens.LightCard.blurRadius)
        assertEquals(28.dp, GlassTokens.DarkFloat.blurRadius)
        assertEquals(32.dp, GlassTokens.LightModal.blurRadius)
    }

    @Test
    fun higherLevelsAreMoreOpaqueAndSolidFallbackIsNearOpaque() {
        listOf(false, true).forEach { dark ->
            val card = GlassTokens.of(GlassLevel.Card, dark)
            val float = GlassTokens.of(GlassLevel.Float, dark)
            val modal = GlassTokens.of(GlassLevel.Modal, dark)
            assertTrue(card.fill.alpha < float.fill.alpha)
            assertTrue(float.fill.alpha <= modal.fill.alpha)
            GlassLevel.entries.forEach { level ->
                val token = GlassTokens.of(level, dark)
                assertTrue(token.solidFill.alpha >= 0.94f, "$level solid fill too transparent")
                assertTrue(token.rimTop >= token.rimBottom, "$level rim must fade downwards")
            }
        }
    }

    @Test
    fun blurOnlyWithModeAndBackdrop() {
        assertTrue(glassBlurActive(GlassRenderMode.Blur, hasBackdrop = true))
        assertFalse(glassBlurActive(GlassRenderMode.Blur, hasBackdrop = false))
        assertFalse(glassBlurActive(GlassRenderMode.Solid, hasBackdrop = true))
        assertFalse(glassBlurActive(GlassRenderMode.Solid, hasBackdrop = false))
    }

    @Test
    fun resolvedFillFallsBackToSolid() {
        val token = GlassTokens.LightModal
        assertEquals(token.fill, token.resolvedFill(blurActive = true))
        assertEquals(token.solidFill, token.resolvedFill(blurActive = false))
    }

    @Test
    fun dialogButtonsCancelLeftConfirmRight() {
        assertEquals(
            listOf(CapsuleRole.Secondary, CapsuleRole.Primary),
            dialogButtonRoles(hasDismiss = true, hasConfirm = true, destructive = false),
        )
        assertEquals(
            listOf(CapsuleRole.Secondary, CapsuleRole.Danger),
            dialogButtonRoles(hasDismiss = true, hasConfirm = true, destructive = true),
        )
        assertEquals(listOf(CapsuleRole.Primary), dialogButtonRoles(false, true, false))
        assertEquals(listOf(CapsuleRole.Danger), dialogButtonRoles(false, true, true))
        assertEquals(listOf(CapsuleRole.Secondary), dialogButtonRoles(true, false, true))
        assertEquals(emptyList(), dialogButtonRoles(false, false, false))
    }

    @Test
    fun touchTargetsMeetGuidelines() {
        assertTrue(AlphaDimens.minTouchTarget >= 48.dp)
        assertTrue(AlphaDimens.minDenseTouchTarget >= 40.dp)
        assertTrue(AlphaDimens.buttonHeight >= 48.dp)
        assertEquals(318.dp, AlphaDimens.dialogWidth)
    }
}
