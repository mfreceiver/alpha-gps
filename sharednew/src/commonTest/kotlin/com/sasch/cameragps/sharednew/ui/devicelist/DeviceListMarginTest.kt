package com.sasch.cameragps.sharednew.ui.devicelist

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Device list cards must span the same width as "Add camera" (both at the screen margin): the
 * list adds the margin symmetrically to its content padding instead of a one-sided scrollbar gutter.
 */
class DeviceListMarginTest {
    @Test
    fun marginIsAddedSymmetricallyToBothSides() {
        LayoutDirection.entries.forEach { dir ->
            val p = withScreenMargin(PaddingValues(top = 8.dp, bottom = 16.dp), dir)
            assertEquals(AlphaDimens.screenMargin, p.calculateStartPadding(dir))
            assertEquals(AlphaDimens.screenMargin, p.calculateEndPadding(dir))
            assertEquals(8.dp, p.calculateTopPadding())
            assertEquals(16.dp, p.calculateBottomPadding())
        }
    }

    @Test
    fun callerHorizontalPaddingIsKeptOnTopOfTheMargin() {
        val p = withScreenMargin(PaddingValues(start = 4.dp, end = 2.dp), LayoutDirection.Ltr)
        assertEquals(AlphaDimens.screenMargin + 4.dp, p.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(AlphaDimens.screenMargin + 2.dp, p.calculateEndPadding(LayoutDirection.Ltr))
    }
}
