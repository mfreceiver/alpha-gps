package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.BackdropOrb
import com.sasch.cameragps.sharednew.ui.theme.DarkAlphaColors
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import com.sasch.cameragps.sharednew.ui.theme.GlassRenderMode
import com.sasch.cameragps.sharednew.ui.theme.GlassTokens
import com.sasch.cameragps.sharednew.ui.theme.LightAlphaColors
import com.sasch.cameragps.sharednew.ui.theme.tintOver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pure logic of the B0 glass components (no composition). */
class GlassComponentLogicTest {
    private val light = LightAlphaColors

    // ---- GlassSegmented ------------------------------------------------------------------------

    @Test
    fun segmentedSelectionIsClamped() {
        assertEquals(-1, segmentedSelection(count = 0, requested = 0))
        assertEquals(0, segmentedSelection(3, -2))
        assertEquals(1, segmentedSelection(3, 1))
        assertEquals(2, segmentedSelection(3, 7))
    }

    @Test
    fun segmentSliderCoversTheSelectedSegment() {
        // 3 segments in 306 px with 3 px padding → 100 px each.
        assertEquals(3f to 100f, segmentSliderBounds(0f, 3, 306f, 3f))
        assertEquals(203f to 100f, segmentSliderBounds(2f, 3, 306f, 3f))
        // Mid-animation between 0 and 1.
        assertEquals(53f to 100f, segmentSliderBounds(0.5f, 3, 306f, 3f))
        assertEquals(3f to 0f, segmentSliderBounds(3f, 3, 306f, 3f))
        assertEquals(3f to 0f, segmentSliderBounds(-1f, 3, 306f, 3f))
        assertEquals(3f to 0f, segmentSliderBounds(Float.NaN, 3, 306f, 3f))
        assertEquals(3f to 0f, segmentSliderBounds(0f, 0, 306f, 3f))
    }

    // ---- GlassCheckbox / GlassRadio ------------------------------------------------------------

    @Test
    fun checkboxCyclesStates() {
        assertEquals(ToggleableState.On, nextCheckState(ToggleableState.Off))
        assertEquals(ToggleableState.Off, nextCheckState(ToggleableState.On))
        // A partial selection ("some selected") completes on tap, like select-all.
        assertEquals(ToggleableState.On, nextCheckState(ToggleableState.Indeterminate))
    }

    @Test
    fun checkboxColoursPerState() {
        val off = glassCheckboxColors(ToggleableState.Off, light, onPhoto = false)
        assertEquals(Color.Transparent, off.fill)
        assertEquals(light.textSecondary, off.rim)
        val photo = glassCheckboxColors(ToggleableState.Off, light, onPhoto = true)
        assertEquals(GlassTokens.Viewer.fill, photo.fill, "unselected on a photo is Viewer glass")
        val on = glassCheckboxColors(ToggleableState.On, light, onPhoto = true)
        assertEquals(light.accent, on.fill)
        assertEquals(on, glassCheckboxColors(ToggleableState.Indeterminate, light, onPhoto = true))
        // The glyph is onPrimary: white on the light theme's deep blue, navy on the dark theme's light blue.
        assertEquals(light.onPrimary, on.glyph)
        assertEquals(DarkAlphaColors.onPrimary, glassCheckboxColors(ToggleableState.On, DarkAlphaColors, false).glyph)
    }

    @Test
    fun radioRingAndDot() {
        assertEquals(light.textSecondary to Color.Transparent, glassRadioColors(false, light))
        assertEquals(light.accent to light.accent, glassRadioColors(true, light))
    }

    // ---- GlassSnackbar -------------------------------------------------------------------------

    @Test
    fun snackbarTimeouts() {
        assertEquals(3_000L, snackbarTimeoutMillis(GlassSnackbarDuration.Short, hasAction = false))
        assertEquals(5_000L, snackbarTimeoutMillis(GlassSnackbarDuration.Short, hasAction = true))
        assertEquals(6_000L, snackbarTimeoutMillis(GlassSnackbarDuration.Long, hasAction = false))
        assertNull(snackbarTimeoutMillis(GlassSnackbarDuration.Indefinite, hasAction = true))
    }

    @Test
    fun snackbarQueueShowsOneAtATimeInOrder() {
        val q = SnackbarQueue<String>()
        assertNull(q.current)
        assertTrue(q.enqueue("a"))
        assertTrue(q.enqueue("b"))
        assertTrue(q.enqueue("c"))
        assertEquals("a", q.current)
        assertEquals(3, q.size)
        assertTrue(q.dismiss("a"))
        assertEquals("b", q.current)
        assertTrue(q.dismiss("b"))
        assertEquals("c", q.current)
        assertTrue(q.dismiss("c"))
        assertNull(q.current)
        assertEquals(0, q.size)
    }

    @Test
    fun snackbarQueueIgnoresStaleDismissAndDuplicates() {
        val q = SnackbarQueue<String>()
        q.enqueue("a")
        q.enqueue("b")
        // A late timer of something that is not on screen must not skip the current message.
        assertFalse(q.dismiss("b"))
        assertEquals("a", q.current)
        // The same message shown or waiting is not queued twice.
        assertFalse(q.enqueue("a"))
        assertFalse(q.enqueue("b"))
        assertEquals(2, q.size)
        q.clear()
        assertNull(q.current)
        assertEquals(0, q.size)
    }

    @Test
    fun snackbarHostStateAdvances() {
        val s = GlassSnackbarHostState()
        s.show("one")
        s.show("two", actionLabel = "Undo")
        val first = s.current!!
        assertEquals("one", first.message)
        s.show("one") // duplicate of the visible message: dropped
        s.dismiss(first)
        assertEquals("two", s.current!!.message)
        assertTrue(s.current!!.id != first.id)
        s.dismiss(first) // stale
        assertEquals("two", s.current!!.message)
        s.dismiss(s.current!!)
        assertNull(s.current)
    }

    // ---- GlassPopover --------------------------------------------------------------------------

    private val container = IntSize(1000, 2000)

    @Test
    fun popoverBelowEndAlignsToTheAnchorEnd() {
        val anchor = IntRect(900, 100, 948, 148)
        val pos = popoverPosition(anchor, IntSize(300, 400), container, PopoverPlacement.BelowEnd, gap = 8, margin = 16)
        assertEquals(IntOffset(948 - 300, 156), pos)
    }

    @Test
    fun popoverIsClampedIntoTheContainer() {
        // Start-aligned at the right edge would overflow → clamped to width - margin - popup.
        val anchor = IntRect(900, 100, 948, 148)
        val pos = popoverPosition(anchor, IntSize(300, 400), container, PopoverPlacement.BelowStart, gap = 8, margin = 16)
        assertEquals(1000 - 16 - 300, pos.x)
        // End-aligned at the left edge would go negative → margin.
        val left = popoverPosition(IntRect(0, 100, 48, 148), IntSize(300, 400), container, PopoverPlacement.BelowEnd, 8, 16)
        assertEquals(16, left.x)
        // Wider than the container → pinned at the margin.
        val wide = popoverPosition(IntRect(0, 100, 48, 148), IntSize(2000, 400), container, PopoverPlacement.BelowEnd, 8, 16)
        assertEquals(16, wide.x)
    }

    @Test
    fun popoverFlipsWhenThePreferredSideDoesNotFit() {
        val nearBottom = IntRect(500, 1800, 548, 1848)
        val flipped = popoverPosition(nearBottom, IntSize(300, 400), container, PopoverPlacement.BelowEnd, 8, 16)
        assertEquals(1800 - 8 - 400, flipped.y)
        val nearTop = IntRect(500, 20, 548, 68)
        val down = popoverPosition(nearTop, IntSize(300, 400), container, PopoverPlacement.AboveEnd, 8, 16)
        assertEquals(68 + 8, down.y)
        // Above preferred and fits → above.
        val above = popoverPosition(nearBottom, IntSize(300, 400), container, PopoverPlacement.AboveStart, 8, 16)
        assertEquals(1800 - 8 - 400, above.y)
        assertEquals(500, above.x)
    }

    @Test
    fun popoverTallerThanBothSidesIsClamped() {
        val mid = IntRect(500, 900, 548, 948)
        // Fits neither side: kept below, then pulled up so its bottom stays inside the margin.
        val pos = popoverPosition(mid, IntSize(300, 1900), container, PopoverPlacement.BelowEnd, 8, 16)
        assertEquals(2000 - 16 - 1900, pos.y)
        val tiny = popoverPosition(mid, IntSize(300, 400), IntSize(1000, 300), PopoverPlacement.BelowEnd, 8, 16)
        assertEquals(16, tiny.y)
    }

    // ---- GlassBottomSheet / GlassTopBar / progress / switch ------------------------------------

    @Test
    fun sheetDismissByDistanceOrFling() {
        assertFalse(sheetShouldDismiss(dragOffset = 100f, sheetHeight = 1000f, velocity = 0f))
        assertTrue(sheetShouldDismiss(350f, 1000f, 0f))
        assertTrue(sheetShouldDismiss(10f, 1000f, SHEET_DISMISS_VELOCITY))
        assertFalse(sheetShouldDismiss(10f, 1000f, SHEET_DISMISS_VELOCITY - 1f))
        assertFalse(sheetShouldDismiss(10f, 0f, 0f))
    }

    @Test
    fun topBarScrolledOnlyAwayFromTheTop() {
        assertFalse(topBarScrolled(0, 0))
        assertTrue(topBarScrolled(0, 1))
        assertTrue(topBarScrolled(2, 0))
    }

    @Test
    fun progressSweepIsClamped() {
        assertEquals(0f, progressSweep(-1f))
        assertEquals(0f, progressSweep(Float.NaN))
        assertEquals(180f, progressSweep(0.5f))
        assertEquals(360f, progressSweep(2f))
        assertEquals(light.onPhoto, glassProgressColors(light, onPhoto = true).second)
        assertEquals(light.accent, glassProgressColors(light, onPhoto = false).second)
    }

    @Test
    fun switchGeometryFollowsN1() {
        assertEquals(52.dp, GlassSwitchTrackWidth)
        assertEquals(32.dp, GlassSwitchTrackHeight)
        assertEquals(0.dp, switchThumbOffset(false, 24.dp))
        // 4 dp inset on both sides: 52 - 8 - 24 = 20; a pressed 28 dp thumb stays inside.
        assertEquals(20.dp, switchThumbOffset(true, 24.dp))
        assertEquals(16.dp, switchThumbOffset(true, 28.dp))
    }

    // ---- RevealValueRow / StatusRow / text field / warning card --------------------------------

    @Test
    fun maskKeepsSeparatorsAndSuffix() {
        assertEquals("••:••:••:••:••:••", maskValue("AA:BB:CC:DD:EE:FF"))
        assertEquals("••:••:••:••:••:FF", maskValue("AA:BB:CC:DD:EE:FF", visibleSuffix = 2))
        assertEquals("AB-12", maskValue("AB-12", visibleSuffix = 10))
        assertEquals("", maskValue(""))
        assertEquals("•• •", maskValue("ab c", visibleSuffix = -3))
    }

    @Test
    fun statusValueAndIconTonesAreSeparate() {
        // N11: a tinted metric — icon in the tone, value in normal text colour.
        val tinted = StatusItem("Written", "12", tone = StatusTone.Neutral, iconTone = StatusTone.Success)
        assertEquals(light.textPrimary, statusValueColor(tinted, light))
        assertEquals(light.success, statusItemIconColor(tinted, light))
        // A warning value: the text uses the darker text-safe amber, the icon the icon-safe amber.
        val warn = StatusItem("GPS", "Weak", tone = StatusTone.Warning)
        assertEquals(light.warningText, statusValueColor(warn, light))
        assertEquals(light.warningIcon, statusItemIconColor(warn, light))
        // valueTone overrides the value only.
        val split = StatusItem("Signal", "Good", tone = StatusTone.Success, valueTone = StatusTone.Neutral)
        assertEquals(light.textPrimary, statusValueColor(split, light))
        assertEquals(light.success, statusItemIconColor(split, light))
    }

    @Test
    fun textFieldBorderPriority() {
        assertEquals(Color.Transparent to 0.dp, textFieldBorder(focused = false, isError = false, enabled = true, colors = light))
        assertEquals(light.accent to 2.dp, textFieldBorder(true, false, true, light))
        assertEquals(light.danger to 2.dp, textFieldBorder(true, true, true, light))
        assertEquals(light.danger to 2.dp, textFieldBorder(false, true, true, light))
        assertEquals(Color.Transparent to 0.dp, textFieldBorder(true, true, false, light))
    }

    @Test
    fun warningCardUsesTheIconToneForItsEdge() {
        val (edge, tint) = warningCardColors(StatusTone.Warning, light)
        assertEquals(light.warningIcon, edge)
        assertTrue(tint.alpha in 0.01f..0.15f, "a faint tint, not a container colour")
        assertEquals(light.danger, warningCardColors(StatusTone.Error, light).first)
    }

    // ---- glass material ------------------------------------------------------------------------

    @Test
    fun nestedGlassNeverBlursTwice() {
        val token = GlassTokens.LightFloat
        val top = resolveGlassPaint(token, GlassRenderMode.Blur, hasBackdrop = true, depth = 0)
        assertTrue(top.blur && top.shadow)
        assertEquals(token.fill, top.fill)
        val nested = resolveGlassPaint(token, GlassRenderMode.Blur, hasBackdrop = true, depth = 1)
        assertFalse(nested.blur)
        assertFalse(nested.shadow)
        assertEquals(token.fill, nested.fill, "nested glass stays translucent over its parent")
        val solid = resolveGlassPaint(token, GlassRenderMode.Solid, hasBackdrop = true, depth = 0)
        assertFalse(solid.blur)
        assertEquals(token.solidFill, solid.fill)
        assertFalse(resolveGlassPaint(token, GlassRenderMode.Blur, true, 0, shadow = false).shadow)
    }

    @Test
    fun tintIsFoldedIntoTheFill() {
        val base = Color.White.copy(alpha = 0.5f)
        assertEquals(base, tintOver(Color.Transparent, base))
        val opaque = tintOver(Color.Red, base)
        assertEquals(1f, opaque.alpha, 0.001f)
        assertEquals(1f, opaque.red, 0.001f)
        val half = tintOver(Color.Black.copy(alpha = 0.5f), Color.White)
        assertEquals(1f, half.alpha, 0.001f)
        assertEquals(0.5f, half.red, 0.01f)
        // Source-over of two translucent layers: 1 - (1 - .5)(1 - .5) = .75.
        assertEquals(0.75f, tintOver(Color.Black.copy(alpha = 0.5f), base).alpha, 0.01f)
        val tinted = resolveGlassPaint(GlassTokens.LightFloat, GlassRenderMode.Blur, true, 0, tint = light.accent.copy(alpha = 0.3f))
        assertTrue(tinted.fill.alpha > GlassTokens.LightFloat.fill.alpha)
    }

    @Test
    fun orbProfileAndBackdropSampling() {
        assertEquals(1f, orbIntensity(0f))
        assertEquals(0.55f, orbIntensity(0.45f), 0.0001f)
        assertEquals(0f, orbIntensity(1f))
        assertEquals(0f, orbIntensity(3f))
        assertTrue(orbIntensity(0.2f) > orbIntensity(0.6f))
        val c = LightAlphaColors
        val orb = c.orbs.first()
        val (center, _) = orbGeometry(orb, 1000f, 1000f)
        val atCenter = backdropColorAt(c, center.x, center.y, 1000f, 1000f)
        assertTrue(atCenter != c.background, "an orb centre is tinted")
        assertEquals(1f, atCenter.alpha, 0.001f)
    }

    @Test
    fun orbsScaleWithTheShorterSide() {
        val orb = BackdropOrb(x = 0.5f, y = 0.25f, radius = 0.5f, color = Color.Blue, alpha = 0.3f)
        assertEquals(Offset(500f, 500f) to 500f, orbGeometry(orb, 1000f, 2000f))
        // Landscape: the radius follows the height, not the width.
        assertEquals(Offset(1000f, 250f) to 500f, orbGeometry(orb, 2000f, 1000f))
    }

    @Test
    fun glassMaterialTokensCarryAllSixEffects() {
        listOf(false, true).forEach { dark ->
            listOf(GlassLevel.Card, GlassLevel.Float, GlassLevel.Modal).forEach { level ->
                val t = GlassTokens.of(level, dark)
                assertTrue(t.rimWidth >= 1.dp, "$level edge light width")
                assertTrue(t.rimShade.alpha > 0f, "$level bottom-right rim shade")
                assertTrue(t.innerHighlight.alpha > 0f && t.innerShadow.alpha > 0f, "$level inner bands")
                assertTrue(t.ambientShadow.blur > t.keyShadow.blur, "$level ambient wider than key")
                assertTrue(t.ambientShadow.color.alpha > 0f && t.keyShadow.color.alpha > 0f)
                assertTrue(t.noise in 0.03f..0.06f, "$level noise ${t.noise}")
                if (dark) assertTrue(t.rimTop >= 0.30f, "$level dark rim visible (was .22)")
            }
        }
        // Light card fill is tinted (not pure white on the near-white page).
        val fill = GlassTokens.LightCard.fill
        assertTrue(fill.blue > fill.red, "light card fill carries a cool tint")
    }

    @Test
    fun capsuleGlassRolesMapToTints() {
        assertEquals(com.sasch.cameragps.sharednew.ui.theme.GlassTintRole.Accent, capsuleGlassTintRole(CapsuleRole.Primary))
        assertEquals(com.sasch.cameragps.sharednew.ui.theme.GlassTintRole.Accent, capsuleGlassTintRole(CapsuleRole.GlassPrimary))
        assertEquals(com.sasch.cameragps.sharednew.ui.theme.GlassTintRole.Danger, capsuleGlassTintRole(CapsuleRole.Danger))
        assertEquals(com.sasch.cameragps.sharednew.ui.theme.GlassTintRole.Neutral, capsuleGlassTintRole(CapsuleRole.Secondary))
        assertEquals(com.sasch.cameragps.sharednew.ui.theme.GlassTintRole.AccentSoft, capsuleGlassTintRole(CapsuleRole.Tonal))
    }
}
