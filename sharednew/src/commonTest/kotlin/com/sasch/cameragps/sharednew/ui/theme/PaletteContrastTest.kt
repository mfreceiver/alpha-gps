package com.sasch.cameragps.sharednew.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.sasch.cameragps.sharednew.bluetooth.status.SignalLevel
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.IconEmphasis
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTagStyle
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import com.sasch.cameragps.sharednew.ui.components.glass.capsuleColors
import com.sasch.cameragps.sharednew.ui.components.glass.iconButtonTint
import com.sasch.cameragps.sharednew.ui.components.glass.infoTagColors
import com.sasch.cameragps.sharednew.ui.components.glass.statusIconColor
import com.sasch.cameragps.sharednew.ui.components.glass.statusToneColor
import com.sasch.cameragps.sharednew.ui.device.LastWriteMetricTone
import com.sasch.cameragps.sharednew.ui.device.WrittenMetricTone
import com.sasch.cameragps.sharednew.ui.device.signalTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrast cases of design v3 (content3a.py "对比度"): worst backdrop = the brightest glow blob,
 * glass composited on top, text on the glass. All must pass WCAG AA (4.5 text, 3.0 non-text).
 */
class PaletteContrastTest {
    private val light = LightAlphaColors
    private val dark = DarkAlphaColors

    private val lightWorstBackdrop = Contrast.over(Color(0xFFA8C7FA).copy(alpha = 0.60f), light.background)
    private val darkWorstBackdrop = Contrast.over(Color(0xFF0B57D0).copy(alpha = 0.42f), dark.background)

    private fun glass(level: GlassLevel, dark: Boolean, backdrop: Color) =
        Contrast.over(GlassTokens.of(level, dark).fill, backdrop)

    private fun assertAa(fg: Color, bg: Color, min: Float = Contrast.AA_TEXT, what: String) {
        val ratio = Contrast.ratio(Contrast.over(fg, bg), bg)
        assertTrue(ratio >= min, "$what: contrast $ratio < $min")
    }

    @Test
    fun contrastMathMatchesWcagReference() {
        assertEquals(21.0, Contrast.ratio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, Contrast.ratio(Color.White, Color.White), 0.0001)
        // Design table: white on #0B57D0 = 6.4.
        assertEquals(6.4, Contrast.ratio(Color.White, Color(0xFF0B57D0)), 0.05)
        // Design table: #062E6F on #A8C7FA = 7.5.
        assertEquals(7.5, Contrast.ratio(Color(0xFF062E6F), Color(0xFFA8C7FA)), 0.05)
    }

    @Test
    fun overCompositesWithAlpha() {
        val half = Contrast.over(Color.Black.copy(alpha = 0.5f), Color.White)
        assertEquals(0.5f, half.red, 1.01f / 255f)
        assertEquals(1f, half.alpha)
    }

    @Test
    fun fixedBluePrimaryWithoutPurpleBaseline() {
        assertEquals(Color(0xFF0B57D0), light.toColorScheme().primary)
        assertEquals(Color(0xFFA8C7FA), dark.toColorScheme().primary)
        assertEquals(light.error, light.toColorScheme().error)
        assertEquals(dark.surface, dark.toColorScheme().surface)
        // The old M3 baseline purple must be gone.
        listOf(light, dark).forEach { c ->
            val scheme = c.toColorScheme()
            listOf(scheme.primary, scheme.secondary, scheme.primaryContainer).forEach {
                assertTrue(it != Color(0xFF6650A4) && it != Color(0xFFD0BCFF), "baseline purple in $it")
            }
        }
    }

    @Test
    fun lightCardGlassText() {
        val bg = glass(GlassLevel.Card, false, lightWorstBackdrop)
        assertAa(light.onSurface, bg, what = "light G1 onSurface")
        assertAa(light.onSurfaceVariant, bg, what = "light G1 onSurfaceVariant")
        assertAa(light.primary, bg, what = "light G1 primary")
        assertAa(light.success, bg, what = "light G1 success")
        assertAa(light.error, bg, what = "light G1 error")
        assertAa(light.warningText, bg, what = "light G1 warning text")
    }

    @Test
    fun lightModalGlassText() {
        val bg = glass(GlassLevel.Modal, false, lightWorstBackdrop)
        assertAa(light.onSurface, bg, what = "light G3 onSurface")
        assertAa(light.onSurfaceVariant, bg, what = "light G3 onSurfaceVariant")
        assertAa(light.error, bg, what = "light G3 error")
        assertAa(light.primary, bg, what = "light G3 primary")
    }

    @Test
    fun darkCardGlassText() {
        val bg = glass(GlassLevel.Card, true, darkWorstBackdrop)
        assertAa(dark.onSurface, bg, what = "dark G1 onSurface")
        assertAa(dark.onSurfaceVariant, bg, what = "dark G1 onSurfaceVariant")
        assertAa(dark.primary, bg, what = "dark G1 primary")
        assertAa(dark.success, bg, what = "dark G1 success")
        assertAa(dark.warningText, bg, what = "dark G1 warning text")
    }

    @Test
    fun darkModalGlassText() {
        val bg = glass(GlassLevel.Modal, true, darkWorstBackdrop)
        assertAa(dark.onSurface, bg, what = "dark G3 onSurface")
        assertAa(dark.onSurfaceVariant, bg, what = "dark G3 onSurfaceVariant")
        assertAa(dark.error, bg, what = "dark G3 error")
    }

    @Test
    fun solidFallbackKeepsTextReadable() {
        listOf(light to false, dark to true).forEach { (c, isDark) ->
            listOf(GlassLevel.Card, GlassLevel.Float, GlassLevel.Modal).forEach { level ->
                val worst = if (isDark) darkWorstBackdrop else lightWorstBackdrop
                val bg = Contrast.over(GlassTokens.of(level, isDark).solidFill, worst)
                assertAa(c.onSurface, bg, what = "solid $level dark=$isDark onSurface")
                assertAa(c.onSurfaceVariant, bg, what = "solid $level dark=$isDark onSurfaceVariant")
            }
        }
    }

    @Test
    fun viewerGlassOverAnyPhoto() {
        listOf(Color.White, Color.Black, Color(0xFF808080)).forEach { photo ->
            val bg = glass(GlassLevel.Viewer, false, photo)
            assertAa(Color.White, bg, what = "GV white over $photo")
        }
        // Secondary viewer text is specified over dark photos only (design: 11.7 over black).
        assertAa(Color(0xFFC4C9D1), glass(GlassLevel.Viewer, false, Color.Black), what = "GV secondary over black")
    }

    @Test
    fun capsuleButtonLabels() {
        listOf(light, dark).forEach { c ->
            val modal = glass(GlassLevel.Modal, c.isDark, if (c.isDark) darkWorstBackdrop else lightWorstBackdrop)
            CapsuleRole.entries.forEach { role ->
                listOf(false, true).forEach { raised ->
                    val cc = capsuleColors(role, enabled = true, colors = c, raised = raised)
                    // The label sits in the vertical middle of the gradient.
                    val middle = Contrast.over(lerp(cc.containerTop, cc.containerBottom, 0.5f), modal)
                    assertAa(cc.content, middle, what = "$role raised=$raised dark=${c.isDark}")
                    if (!raised) assertEquals(cc.containerTop, cc.containerBottom, "flat $role must be solid")
                }
            }
        }
    }

    @Test
    fun disabledCapsuleIsFlatAndMuted() {
        CapsuleRole.entries.forEach { role ->
            val cc = capsuleColors(role, enabled = false, colors = light, raised = true)
            assertEquals(null, cc.glow)
            assertEquals(cc.containerTop, cc.containerBottom)
            assertTrue(cc.content.alpha < 1f)
        }
    }

    @Test
    fun raisedPrimaryHasGlowButDialogCapsuleDoesNot() {
        assertTrue(capsuleColors(CapsuleRole.Primary, true, light, raised = true).glow != null)
        assertEquals(null, capsuleColors(CapsuleRole.Primary, true, light, raised = false).glow)
        assertEquals(light.error, capsuleColors(CapsuleRole.Danger, true, light).containerBottom)
        assertEquals(light.error, capsuleColors(CapsuleRole.DangerOutlined, true, light).border)
    }

    @Test
    fun infoTags() {
        val (photoBg, photoFg) = infoTagColors(InfoTagStyle.OnPhoto, light, content = Color.Red)
        assertEquals(Color.White, photoFg)
        assertAa(photoFg, Contrast.over(photoBg, Color.White), what = "tag on white photo")
        listOf(light, dark).forEach { c ->
            val card = glass(GlassLevel.Card, c.isDark, if (c.isDark) darkWorstBackdrop else lightWorstBackdrop)
            val (bg, fg) = infoTagColors(InfoTagStyle.OnSurface, c)
            assertEquals(if (c.isDark) c.onSurface else c.onSurfaceVariant, fg)
            assertAa(fg, Contrast.over(bg, card), what = "surface tag dark=${c.isDark}")
        }
    }

    @Test
    fun iconsMeetNonTextContrast() {
        listOf(light, dark).forEach { c ->
            val card = glass(GlassLevel.Card, c.isDark, if (c.isDark) darkWorstBackdrop else lightWorstBackdrop)
            IconEmphasis.entries.forEach { e ->
                assertAa(iconButtonTint(e, c, enabled = true), card, Contrast.AA_LARGE, "icon $e dark=${c.isDark}")
            }
        }
    }

    @Test
    fun statusToneColors() {
        assertEquals(light.warningText, statusToneColor(StatusTone.Warning, light))
        assertEquals(light.warning, statusToneColor(StatusTone.Warning, light, forText = false))
        assertEquals(light.success, statusToneColor(StatusTone.Success, light))
        assertEquals(dark.error, statusToneColor(StatusTone.Error, dark))
        assertEquals(dark.primary, statusToneColor(StatusTone.Active, dark))
    }

    @Test
    fun deviceMetricIconTonesReadableOnCardAndInsetGlass() {
        // Device page metric icons (signal by level, written, last write) sit on an inset block
        // (onSurface 5–7 %) inside a G1 card; they use the text-safe tone colours, so they must
        // pass AA text contrast, not only the 3.0 non-text minimum.
        val tones = SignalLevel.entries.map { signalTone(it) } + listOf(WrittenMetricTone, LastWriteMetricTone)
        listOf(light to lightWorstBackdrop, dark to darkWorstBackdrop).forEach { (colors, backdrop) ->
            val card = glass(GlassLevel.Card, colors.isDark, backdrop)
            val inset = Contrast.over(colors.onSurface.copy(alpha = if (colors.isDark) 0.07f else 0.05f), card)
            tones.forEach { tone ->
                val fg = statusIconColor(tone, colors)
                assertAa(fg, card, what = "${if (colors.isDark) "dark" else "light"} card $tone icon")
                assertAa(fg, inset, what = "${if (colors.isDark) "dark" else "light"} inset $tone icon")
            }
        }
    }

    @Test
    fun accentToneIsTheThemeTealAndDistinctFromPrimary() {
        listOf(light, dark).forEach { c ->
            assertEquals(c.accentTeal, statusToneColor(StatusTone.Accent, c))
            assertTrue(c.accentTeal != c.primary && c.accentTeal != c.success, "teal must be its own colour")
        }
    }
}
