package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.remote.wifi.formatByteSize
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The portrait info strip is a details button, not an expander: its contents stay vertically centered. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ViewerInfoStripComposeTest {
    @get:Rule val compose = createComposeRule()

    private val info = ViewerInfoStrip("JPG", 5_000_000, missingFormat = false)
    private var opened = 0

    private fun show(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                CameraGpsTheme(darkTheme = true) {
                    GlassBackground {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ViewerInfoStripBar(
                                info, onOpenDetails = { opened++ },
                                modifier = Modifier.width(320.dp).testTag("strip"),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertCenteredAndClickable() {
        val strip = compose.onNodeWithTag("strip").fetchSemanticsNode().boundsInRoot
        val format = compose.onNodeWithText("JPG", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val size = compose.onNodeWithText(formatByteSize(info.sizeBytes), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val indicator = compose.onNodeWithTag("viewer-details-indicator", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        assertTrue("strip meets the 48 dp touch target", strip.height >= with(compose.density) { 48.dp.toPx() } - .01f)
        assertEquals("format text is centered", strip.center.y, format.center.y, .5f)
        assertEquals("size text is centered", strip.center.y, size.center.y, .5f)
        assertEquals("details indicator is centered", strip.center.y, indicator.center.y, .5f)

        compose.onNodeWithTag("strip").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun detailsContentsAreCenteredAtDefaultFontScale() {
        show(1f)
        assertCenteredAndClickable()
    }

    @Test
    fun detailsContentsAreCenteredAtLargeFontScale() {
        show(2f)
        assertCenteredAndClickable()
    }
}
