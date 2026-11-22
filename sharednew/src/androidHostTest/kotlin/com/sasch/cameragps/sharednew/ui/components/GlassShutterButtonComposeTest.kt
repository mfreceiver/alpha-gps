package com.sasch.cameragps.sharednew.ui.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.components.glass.GlassShutterButton
import com.sasch.cameragps.sharednew.ui.components.glass.shutterButtonClickable
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassShutterButtonComposeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(enabled: Boolean = true, pressing: Boolean = false, onPhoto: Boolean = false, onClick: () -> Unit) {
        compose.setContent {
            CameraGpsTheme(darkTheme = true) {
                GlassBackground {
                    GlassShutterButton(
                        onClick = onClick,
                        contentDescription = "Shutter",
                        enabled = enabled,
                        pressing = pressing,
                        onPhoto = onPhoto,
                    )
                }
            }
        }
    }

    @Test
    fun enabledButtonClicksAndIsLabelled() {
        var clicks = 0
        show { clicks++ }
        compose.onNodeWithContentDescription("Shutter").assertIsEnabled().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun disabledButtonIgnoresTaps() {
        var clicks = 0
        show(enabled = false) { clicks++ }
        compose.onNodeWithContentDescription("Shutter").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun pressingSuppressesFurtherTaps() {
        var clicks = 0
        show(pressing = true) { clicks++ }
        compose.onNodeWithContentDescription("Shutter").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun onPhotoVariantStillClicks() {
        var clicks = 0
        show(onPhoto = true) { clicks++ }
        compose.onNodeWithContentDescription("Shutter").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun clickableRule() {
        assertTrue(shutterButtonClickable(enabled = true, pressing = false))
        assertFalse(shutterButtonClickable(enabled = true, pressing = true))
        assertFalse(shutterButtonClickable(enabled = false, pressing = false))
    }
}
