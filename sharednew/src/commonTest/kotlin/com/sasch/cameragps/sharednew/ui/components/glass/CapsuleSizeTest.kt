package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapsuleSizeTest {
    @Test
    fun sizesAreOrderedAndKeepTheExistingSpecs() {
        assertEquals(AlphaDimens.buttonHeight, CapsuleSize.Regular.height)
        assertEquals(AlphaDimens.primaryButtonHeight, CapsuleSize.Large.height)
        assertEquals(AlphaDimens.heroButtonHeight, CapsuleSize.Hero.height)
        // Device page photo-transfer entry: compact 52 dp primary.
        assertEquals(52.dp, CapsuleSize.Medium.height)
        val heights = CapsuleSize.entries.map { it.height }
        assertEquals(heights.sorted(), heights)
    }

    @Test
    fun smallChipIsFlat() {
        // The Small chip is a row-level action (Ⓘ dialog); its 48 dp touch target comes from the
        // minimum interactive size, not from its visual height.
        assertTrue(CapsuleSize.Small.height < AlphaDimens.minTouchTarget)
        assertFalse(CapsuleSize.Small.raised)
        assertFalse(CapsuleSize.Regular.raised)
        assertTrue(CapsuleSize.Medium.raised)
    }
}
