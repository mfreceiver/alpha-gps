package com.sasch.cameragps.sharednew.ui.remote

import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WifiFormatChooserTest {
    private fun option(policy: WifiFormatPolicy, enabled: Boolean = true) =
        FormatChooserOption(policy, policy.choiceLabel(), "", enabled)

    @Test
    fun labelsAreTheFormatNames() {
        assertEquals(listOf("JPG", "RAW", "JPG + RAW"), listOf(WifiFormatPolicy.JpegOnly, WifiFormatPolicy.RawOnly, WifiFormatPolicy.Original).map { it.choiceLabel() })
    }

    @Test
    fun theFirstSelectableRowIsPreselectedAndAPickWinsWhileSelectable() {
        val all = listOf(option(WifiFormatPolicy.JpegOnly), option(WifiFormatPolicy.RawOnly), option(WifiFormatPolicy.Original))
        assertEquals(WifiFormatPolicy.JpegOnly, effectiveChooserSelection(all, null))
        assertEquals(WifiFormatPolicy.RawOnly, effectiveChooserSelection(all, WifiFormatPolicy.RawOnly))
        // RAW unavailable for the selection: never preselected, a stale pick falls back.
        val noRaw = listOf(option(WifiFormatPolicy.JpegOnly), option(WifiFormatPolicy.RawOnly, enabled = false), option(WifiFormatPolicy.Original))
        assertEquals(WifiFormatPolicy.JpegOnly, effectiveChooserSelection(noRaw, WifiFormatPolicy.RawOnly))
        val noJpg = listOf(option(WifiFormatPolicy.JpegOnly, enabled = false), option(WifiFormatPolicy.RawOnly), option(WifiFormatPolicy.Original))
        assertEquals(WifiFormatPolicy.RawOnly, effectiveChooserSelection(noJpg, null))
        // Nothing selectable: no selection, the Download button is disabled.
        assertNull(effectiveChooserSelection(all.map { it.copy(enabled = false) }, WifiFormatPolicy.Original))
        assertNull(effectiveChooserSelection(emptyList(), null))
    }
}
