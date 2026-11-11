package com.sasch.cameragps.sharednew.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.sasch.cameragps.sharednew.bluetooth.status.SignalLevel
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.IconEmphasis
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTagStyle
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import com.sasch.cameragps.sharednew.ui.components.glass.backdropColorAt
import com.sasch.cameragps.sharednew.ui.components.glass.capsuleColors
import com.sasch.cameragps.sharednew.ui.components.glass.glassCheckboxColors
import com.sasch.cameragps.sharednew.ui.components.glass.glassProgressColors
import com.sasch.cameragps.sharednew.ui.components.glass.glassRadioColors
import com.sasch.cameragps.sharednew.ui.components.glass.glassSwitchColors
import com.sasch.cameragps.sharednew.ui.components.glass.iconButtonTint
import com.sasch.cameragps.sharednew.ui.components.glass.infoTagColors
import com.sasch.cameragps.sharednew.ui.components.glass.statusIconColor
import com.sasch.cameragps.sharednew.ui.components.glass.statusToneColor
import com.sasch.cameragps.sharednew.ui.components.glass.textFieldBorder
import com.sasch.cameragps.sharednew.ui.device.LastWriteMetricTone
import com.sasch.cameragps.sharednew.ui.device.LocationSignalLevel
import com.sasch.cameragps.sharednew.ui.device.WrittenMetricTone
import com.sasch.cameragps.sharednew.ui.device.locationSignalTone
import com.sasch.cameragps.sharednew.ui.device.signalTone
import com.sasch.cameragps.sharednew.ui.remote.ViewerDownloadPhase
import com.sasch.cameragps.sharednew.ui.remote.viewerDownloadHintColor
import com.sasch.cameragps.sharednew.ui.remote.viewerFormatColor
import androidx.compose.ui.state.ToggleableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrast of the palette on glass (design v3 + audit §3.4 / D1). The backdrop is no longer one
 * hard-coded blob: every case is checked over **all** candidate backdrop points derived from the
 * theme's own [AlphaColors.orbs] (bare background, each orb's peak, all orbs stacked), with the glass
 * fill composited on top, with and without the top sheen, and for glass nested in a card. Text must
 * pass WCAG AA (4.5, large text 3.0), non-text 3.0. A failing cell is fixed in the token, never by
 * lowering a threshold.
 */
class PaletteContrastTest {
    private val light = LightAlphaColors
    private val dark = DarkAlphaColors
    private val photos = listOf(Color.White, Color.Black, Color(0xFF808080))

    private fun assertAa(fg: Color, bg: Color, min: Float = Contrast.AA_TEXT, what: String) {
        val ratio = Contrast.ratio(Contrast.over(fg, bg), bg)
        assertTrue(ratio >= min, "$what: contrast $ratio < $min")
    }

    private fun minRatio(fg: Color, bgs: List<Color>): Double =
        bgs.minOf { Contrast.ratio(Contrast.over(fg, it), it) }

    /** Canvas sizes the backdrop is sampled on: phone portrait / square / landscape / tablet. */
    private val canvases = listOf(1080f to 2400f, 1600f to 1600f, 2400f to 1080f, 1600f to 2560f)
    private val sampled = mutableMapOf<Boolean, List<Color>>()

    /**
     * Every G0 backdrop point a glass surface can sit on: the real orb field ([backdropColorAt],
     * same profile as the drawing) sampled on a 17 × 17 grid of each canvas. Blur only averages
     * these, so the extremes bound what glass ever samples.
     */
    private fun backdrops(c: AlphaColors): List<Color> = sampled.getOrPut(c.isDark) {
        canvases.flatMap { (w, h) ->
            (0..16).flatMap { i -> (0..16).map { j -> backdropColorAt(c, w * i / 16f, h * j / 16f, w, h) } }
        }.distinct()
    }

    /** What text sees on glass of [level]: fill over the backdrop, plus the sheen at the top, plus nesting. */
    private fun glassBackgrounds(level: GlassLevel, c: AlphaColors, under: List<Color> = if (level == GlassLevel.Viewer) photos else backdrops(c)): List<Color> {
        val t = GlassTokens.of(level, c.isDark)
        val plain = under.map { Contrast.over(t.fill, it) }
        val sheen = plain.map { Contrast.over(GlassHighlight.copy(alpha = t.sheen), it) }
        val nested = if (level == GlassLevel.Viewer) emptyList() else {
            // Glass nested in a card is a translucent layer over the card (no second blur).
            val card = GlassTokens.of(GlassLevel.Card, c.isDark)
            under.map { Contrast.over(t.fill, Contrast.over(card.fill, it)) }
        }
        return plain + sheen + nested
    }

    // ---- 6 tiers × 8 colour roles × 4 glass levels × light / dark -------------------------------

    /** WCAG "large text": ≥ 18.66 sp bold or ≥ 24 sp. Only Title qualifies. */
    private fun isLarge(style: TextStyle): Boolean {
        val size = style.fontSize.value
        val bold = (style.fontWeight ?: FontWeight.Normal) >= FontWeight.SemiBold
        return size >= 24f || (bold && size >= 18.66f)
    }

    private data class Role(val name: String, val color: Color, val min: Float?)

    /**
     * Roles valid on theme glass (Card / Float / Modal) and on the photo viewer (GV). min = null:
     * the tier decides (4.5 / 3.0 large). textMuted / onPhotoMuted are 3:1 roles (placeholders,
     * disabled hints, timestamps on photos) — never for body copy that must be read.
     */
    private fun themeRoles(c: AlphaColors) = listOf(
        Role("textPrimary", c.textPrimary, null),
        Role("textSecondary", c.textSecondary, null),
        Role("textMuted", c.textMuted, Contrast.AA_LARGE),
        Role("accent", c.accent, null),
        Role("success", c.success, null),
        Role("warning", c.warningText, null),
        Role("danger", c.danger, null),
    )

    private fun viewerRoles(c: AlphaColors) = listOf(
        Role("onPhoto", c.onPhoto, null),
        Role("onPhotoSecondary", c.onPhotoSecondary, null),
        Role("onPhotoMuted", c.onPhotoMuted, Contrast.AA_LARGE),
        Role("onPhotoWarning", c.onPhotoWarning, null),
        Role("onPhotoDanger", c.onPhotoDanger, null),
    )

    private val tiers = listOf(
        "Title" to AlphaType.Title, "Heading" to AlphaType.Heading, "Body" to AlphaType.Body,
        "Label" to AlphaType.Label, "Caption" to AlphaType.Caption, "Tag" to AlphaType.Tag,
    )

    @Test
    fun typeTiersAreTheSixAgreedSizes() {
        assertEquals(listOf(22f, 18f, 15f, 15f, 13f, 11f), tiers.map { it.second.fontSize.value })
        assertEquals(listOf(28f, 24f, 22f, 20f, 18f, 14f), tiers.map { it.second.lineHeight.value })
        tiers.forEach { (name, s) -> assertEquals(0f, s.letterSpacing.value, "$name letterSpacing") }
        assertEquals(12f, AlphaType.Mono.fontSize.value)
        // Only Title is WCAG-large; Heading (18 sp semibold) is just below 18.66 sp → 4.5 applies.
        assertEquals(listOf(true, false, false, false, false, false), tiers.map { isLarge(it.second) })
    }

    @Test
    fun materialTypographySlotsUseTheSameTiers() {
        val t = AlphaMaterialTypography
        assertEquals(AlphaType.Title, t.titleLarge)
        assertEquals(AlphaType.Title, t.headlineSmall)
        assertEquals(AlphaType.Heading, t.titleMedium)
        assertEquals(AlphaType.Label, t.titleSmall)
        assertEquals(AlphaType.Body, t.bodyLarge)
        assertEquals(AlphaType.Body, t.bodyMedium)
        assertEquals(AlphaType.Caption, t.bodySmall)
        assertEquals(AlphaType.Label, t.labelLarge)
        assertEquals(AlphaType.Tag, t.labelSmall)
        val allowed = AlphaType.tiers.map { it.fontSize }.toSet()
        listOf(
            t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium,
            t.labelMedium,
        ).forEach { assertTrue(it.fontSize in allowed, "M3 slot ${it.fontSize} is not one of the tiers") }
    }

    @Test
    fun textContrastMatrix() {
        val failures = mutableListOf<String>()
        val report = StringBuilder("tier × role × level × theme — min contrast over all backdrop points\n")
        listOf(light, dark).forEach { c ->
            GlassLevel.entries.forEach { level ->
                val bgs = glassBackgrounds(level, c)
                val roles = if (level == GlassLevel.Viewer) viewerRoles(c) else themeRoles(c)
                roles.forEach { role ->
                    val ratio = minRatio(role.color, bgs)
                    report.append("${if (c.isDark) "dark " else "light"} $level ${role.name}: ${(ratio * 100).toInt() / 100.0}\n")
                    tiers.forEach { (tierName, style) ->
                        val min = role.min ?: if (isLarge(style)) Contrast.AA_LARGE else Contrast.AA_TEXT
                        if (ratio < min) failures += "${if (c.isDark) "dark" else "light"} $level $tierName ${role.name}: $ratio < $min"
                    }
                }
            }
        }
        println(report)
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun solidFallbackKeepsTextReadable() {
        listOf(light, dark).forEach { c ->
            listOf(GlassLevel.Card, GlassLevel.Float, GlassLevel.Modal).forEach { level ->
                val bgs = backdrops(c).map { Contrast.over(GlassTokens.of(level, c.isDark).solidFill, it) }
                themeRoles(c).forEach { role ->
                    val min = role.min ?: Contrast.AA_TEXT
                    assertTrue(minRatio(role.color, bgs) >= min, "solid $level dark=${c.isDark} ${role.name}")
                }
            }
        }
    }

    @Test
    fun mutedRolesStayAtLeastThreeToOneEachTheme() {
        listOf(light, dark).forEach { c ->
            listOf(GlassLevel.Card, GlassLevel.Float, GlassLevel.Modal).forEach { level ->
                assertTrue(minRatio(c.textMuted, glassBackgrounds(level, c)) >= Contrast.AA_LARGE, "textMuted $level dark=${c.isDark}")
            }
        }
        assertTrue(minRatio(light.onPhotoMuted, glassBackgrounds(GlassLevel.Viewer, light)) >= Contrast.AA_LARGE)
        // Muted is visibly quieter than secondary, otherwise it is not a separate role.
        listOf(light, dark).forEach { c -> assertTrue(c.textMuted != c.textSecondary) }
    }

    /**
     * Everything Viewer chrome can sit on, in either theme (the Viewer does not follow the theme):
     * Viewer glass (70 %) over a white / black / grey photo with and without the sheen, the bare
     * black viewer background (portrait download row, letterbox) and the solid Viewer fill (94 %)
     * on black, plus the opaque details sheet ([AlphaColors.viewerSheet]) and the unblurred Viewer
     * glass of its close button on it (with and without the sheen).
     */
    private fun viewerSurfaces(c: AlphaColors): List<Color> {
        val solid = Contrast.over(GlassTokens.of(GlassLevel.Viewer, c.isDark).solidFill, Color.Black)
        return glassBackgrounds(GlassLevel.Viewer, c) + Color.Black + solid + c.viewerSheet +
            glassBackgrounds(GlassLevel.Viewer, c, under = listOf(c.viewerSheet))
    }

    @Test
    fun viewerSheetIsOpaqueAndSetOffTheBlackViewer() {
        listOf(light, dark).forEach { c ->
            assertEquals(1f, c.viewerSheet.alpha, "the details sheet must not show the viewer through")
            // A visible (if quiet) edge against the black viewer behind the scrim.
            assertTrue(Contrast.ratio(c.viewerSheet, Color.Black) >= 1.2, "sheet vs black ${Contrast.ratio(c.viewerSheet, Color.Black)}")
        }
        assertEquals(light.viewerSheet, dark.viewerSheet, "the Viewer is dark in both themes")
    }

    @Test
    fun viewerForegroundsReadableOnEveryViewerSurface() {
        val failures = mutableListOf<String>()
        listOf(light, dark).forEach { c ->
            val bgs = viewerSurfaces(c)
            val text = listOf(
                "onPhoto" to c.onPhoto, "onPhotoSecondary" to c.onPhotoSecondary,
                "onPhotoWarning" to c.onPhotoWarning, "onPhotoDanger" to c.onPhotoDanger,
            )
            text.forEach { (name, fg) ->
                val r = minRatio(fg, bgs)
                if (r < Contrast.AA_TEXT) failures += "dark=${c.isDark} text $name: $r < ${Contrast.AA_TEXT}"
            }
            // Icons / marks: muted text role and the warning / danger glyphs, 3:1.
            val icons = listOf(
                "onPhotoMuted" to c.onPhotoMuted, "onPhotoWarning icon" to c.onPhotoWarning,
                "onPhotoDanger icon" to c.onPhotoDanger,
                "viewer IconEmphasis.Danger" to iconButtonTint(IconEmphasis.Danger, c, enabled = true, onViewer = true),
            )
            icons.forEach { (name, fg) ->
                val r = minRatio(fg, bgs)
                if (r < Contrast.AA_LARGE) failures += "dark=${c.isDark} icon $name: $r < ${Contrast.AA_LARGE}"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun viewerWarningAndErrorTextUseThePhotoRoles() {
        // The Viewer call sites (info strip "only RAW", download failure hint): every state's
        // colour must read on every Viewer surface in both themes — the theme warningText (#7A5200)
        // and danger (#BA1A1A) were ≈ 1.0–3.3:1 here.
        listOf(light, dark).forEach { c ->
            val bgs = viewerSurfaces(c)
            listOf(true, false).forEach { missingFormat ->
                val r = minRatio(viewerFormatColor(missingFormat, c), bgs)
                assertTrue(r >= Contrast.AA_TEXT, "info strip format missingFormat=$missingFormat dark=${c.isDark}: $r")
            }
            ViewerDownloadPhase.entries.forEach { phase ->
                val r = minRatio(viewerDownloadHintColor(phase, c), bgs)
                assertTrue(r >= Contrast.AA_TEXT, "download hint $phase dark=${c.isDark}: $r")
            }
            assertEquals(c.onPhotoWarning, viewerFormatColor(missingFormat = true, c))
            assertEquals(c.onPhotoDanger, viewerDownloadHintColor(ViewerDownloadPhase.Ready, c))
            assertEquals(c.onPhotoDanger, iconButtonTint(IconEmphasis.Danger, c, enabled = true, onViewer = true))
            // Still distinguishable from plain onPhoto text (they carry the warning / error meaning).
            assertTrue(c.onPhotoWarning != c.onPhoto && c.onPhotoDanger != c.onPhoto && c.onPhotoWarning != c.onPhotoDanger)
        }
    }

    @Test
    fun warningIconMeetsNonTextContrastOnAllLevels() {
        listOf(light, dark).forEach { c ->
            listOf(GlassLevel.Card, GlassLevel.Float, GlassLevel.Modal).forEach { level ->
                assertTrue(minRatio(c.warningIcon, glassBackgrounds(level, c)) >= Contrast.AA_LARGE, "warningIcon $level dark=${c.isDark}")
            }
        }
        // The old pale amber is what failed (≈ 1.7:1 on light glass) — it must not be the icon colour.
        assertTrue(light.warningIcon != light.warning)
    }

    // ---- palette -------------------------------------------------------------------------------

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
    fun roleAliasesPointAtTheSchemeColours() {
        listOf(light, dark).forEach { c ->
            assertEquals(c.onSurface, c.textPrimary)
            assertEquals(c.onSurfaceVariant, c.textSecondary)
            assertEquals(c.primary, c.accent)
            assertEquals(c.error, c.danger)
            assertEquals(Color.White, c.onPhoto)
            assertTrue(c.onPhotoMuted.alpha < c.onPhotoSecondary.alpha && c.onPhotoSecondary.alpha < 1f)
        }
    }

    @Test
    fun backdropOrbsAreLowContrast() {
        // D1: coloured light, not blobs — no orb point may move the backdrop far from the background,
        // or dark text on Card glass (the thinnest fill) would lose its margin.
        listOf(light, dark).forEach { c ->
            assertTrue(c.orbs.size >= 3)
            // …but visible: the brightest point differs from the plain background.
            assertTrue(backdrops(c).maxOf { Contrast.ratio(it, c.background) } > 1.1, "dark=${c.isDark} orbs invisible")
            c.orbs.forEach { o ->
                assertTrue(o.alpha in 0.05f..0.6f, "orb alpha ${o.alpha}")
                assertTrue(o.radius in 0.3f..1f, "orb radius ${o.radius}")
            }
            backdrops(c).forEach { b ->
                assertTrue(Contrast.ratio(b, c.background) < 1.5, "dark=${c.isDark} orb point $b too strong against the background")
            }
        }
    }

    // ---- components ----------------------------------------------------------------------------

    @Test
    fun capsuleButtonLabels() {
        listOf(light, dark).forEach { c ->
            val modal = glassBackgrounds(GlassLevel.Modal, c)
            CapsuleRole.entries.filter { it != CapsuleRole.GlassPrimary }.forEach { role ->
                listOf(false, true).forEach { raised ->
                    val cc = capsuleColors(role, enabled = true, colors = c, raised = raised)
                    // The label sits in the vertical middle of the gradient.
                    val middle = modal.map { Contrast.over(lerp(cc.containerTop, cc.containerBottom, 0.5f), it) }
                    assertTrue(minRatio(cc.content, middle) >= Contrast.AA_TEXT, "$role raised=$raised dark=${c.isDark}")
                    if (!raised) assertEquals(cc.containerTop, cc.containerBottom, "flat $role must be solid")
                    assertTrue(!cc.glass)
                }
            }
        }
    }

    @Test
    fun glassCapsulesAreTranslucentAndReadableEverywhere() {
        listOf(light, dark).forEach { c ->
            CapsuleRole.entries.forEach { role ->
                val cc = capsuleColors(role, enabled = true, colors = c, glass = true)
                assertTrue(cc.glass, "$role glass")
                assertEquals(null, cc.glow, "glass capsules have no glow ring")
                assertTrue(cc.containerTop.alpha < 1f, "$role glass tint must be translucent")
                // The tint sits on floating glass, which sits on the page or on a photo.
                val under = backdrops(c) + photos
                val bgs = glassBackgrounds(GlassLevel.Float, c, under).map { Contrast.over(cc.containerTop, it) }
                assertTrue(minRatio(cc.content, bgs) >= Contrast.AA_TEXT, "glass $role dark=${c.isDark}: ${minRatio(cc.content, bgs)}")
            }
            // GlassPrimary is always the glass variant.
            assertTrue(capsuleColors(CapsuleRole.GlassPrimary, true, c).glass)
        }
        // On the viewer (always dark, Viewer glass) the label is onPhoto over any photo.
        val viewer = capsuleColors(CapsuleRole.GlassPrimary, true, dark, glass = true)
        val bgs = glassBackgrounds(GlassLevel.Viewer, dark).map { Contrast.over(viewer.containerTop, it) }
        assertTrue(minRatio(dark.onPhoto, bgs) >= Contrast.AA_TEXT, "viewer download ${minRatio(dark.onPhoto, bgs)}")
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
    fun raisedPrimaryGlowIsSoftAccentNeverDark() {
        listOf(light, dark).forEach { c ->
            val raised = capsuleColors(CapsuleRole.Primary, true, c, raised = true).glow
            assertTrue(raised != null)
            assertEquals(c.accent.copy(alpha = 1f), raised.copy(alpha = 1f), "glow is accent-tinted, not black")
            assertTrue(raised.alpha <= 0.25f)
            assertEquals(null, capsuleColors(CapsuleRole.Primary, true, c, raised = false).glow)
        }
        assertEquals(light.error, capsuleColors(CapsuleRole.Danger, true, light).containerBottom)
        assertEquals(light.error, capsuleColors(CapsuleRole.DangerOutlined, true, light).border)
    }

    @Test
    fun infoTags() {
        val photo = infoTagColors(InfoTagStyle.OnPhoto, light, content = Color.Red)
        assertEquals(light.onPhoto, photo.content)
        photos.forEach { p ->
            assertAa(photo.content, Contrast.over(photo.background, p), what = "tag on photo $p")
        }
        listOf(light, dark).forEach { c ->
            val tag = infoTagColors(InfoTagStyle.OnSurface, c)
            assertEquals(Color.Transparent, tag.background, "tags on glass have no fill (N13)")
            assertTrue(tag.stroke.alpha > 0f)
            listOf(GlassLevel.Card, GlassLevel.Float, GlassLevel.Modal).forEach { level ->
                assertTrue(minRatio(tag.content, glassBackgrounds(level, c)) >= Contrast.AA_TEXT, "surface tag $level dark=${c.isDark}")
            }
        }
    }

    @Test
    fun iconsMeetNonTextContrast() {
        listOf(light, dark).forEach { c ->
            listOf(GlassLevel.Card, GlassLevel.Float, GlassLevel.Modal).forEach { level ->
                IconEmphasis.entries.forEach { e ->
                    val r = minRatio(iconButtonTint(e, c, enabled = true), glassBackgrounds(level, c))
                    assertTrue(r >= Contrast.AA_LARGE, "icon $e $level dark=${c.isDark}: $r")
                }
            }
        }
        listOf(light, dark).forEach { c ->
            IconEmphasis.entries.forEach { e ->
                val r = minRatio(iconButtonTint(e, c, enabled = true, onViewer = true), viewerSurfaces(c))
                assertTrue(r >= Contrast.AA_LARGE, "viewer icon $e dark=${c.isDark}: $r")
            }
        }
    }

    @Test
    fun statusToneColors() {
        assertEquals(light.warningText, statusToneColor(StatusTone.Warning, light))
        assertEquals(light.warningIcon, statusToneColor(StatusTone.Warning, light, forText = false))
        assertEquals(light.warningIcon, statusIconColor(StatusTone.Warning, light))
        assertEquals(light.success, statusToneColor(StatusTone.Success, light))
        assertEquals(dark.error, statusToneColor(StatusTone.Error, dark))
        assertEquals(dark.primary, statusToneColor(StatusTone.Active, dark))
        assertEquals(light.textSecondary, statusToneColor(StatusTone.Neutral, light))
    }

    @Test
    fun deviceMetricIconTonesReadableOnCardAndInsetGlass() {
        // Device page metric icons (signal by level, written, last write) sit on an inset block
        // (onSurface 5–7 %) inside a G1 card.
        val tones = SignalLevel.entries.map { signalTone(it) } +
            LocationSignalLevel.entries.map { locationSignalTone(it) } +
            listOf(WrittenMetricTone, LastWriteMetricTone)
        listOf(light, dark).forEach { colors ->
            val card = glassBackgrounds(GlassLevel.Card, colors)
            val inset = card.map { Contrast.over(colors.onSurface.copy(alpha = if (colors.isDark) 0.07f else 0.05f), it) }
            tones.forEach { tone ->
                val fg = statusIconColor(tone, colors)
                assertTrue(minRatio(fg, card) >= Contrast.AA_LARGE, "card $tone icon dark=${colors.isDark}")
                assertTrue(minRatio(fg, inset) >= Contrast.AA_LARGE, "inset $tone icon dark=${colors.isDark}")
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

    @Test
    fun bluetoothBlueIconReadableOnCardGlass() {
        // The device page's Bluetooth glyph (brand blue) sits directly on the G1 status card,
        // inside the signal ring; non-text, so ≥ 3:1 against every card backdrop in both themes.
        listOf(light, dark).forEach { c ->
            val ratio = minRatio(c.bluetoothBlue, glassBackgrounds(GlassLevel.Card, c))
            assertTrue(ratio >= Contrast.AA_LARGE, "bluetoothBlue dark=${c.isDark} ratio=$ratio")
        }
    }

    @Test
    fun glassSwitchStatesAreDistinguishable() {
        listOf(light, dark).forEach { c ->
            val modal = glassBackgrounds(GlassLevel.Modal, c) + glassBackgrounds(GlassLevel.Card, c)
            val on = glassSwitchColors(true, c)
            val off = glassSwitchColors(false, c)
            // Non-text contrast: the thumb against the "on" track, the "on" track against the glass,
            // and the "off" outline against the glass (the off thumb is white on a pale veil — the
            // outline carries the boundary).
            assertAa(on.thumb, on.track, Contrast.AA_LARGE, "on thumb dark=${c.isDark}")
            assertTrue(minRatio(on.track, modal) >= Contrast.AA_LARGE, "on track dark=${c.isDark}")
            assertTrue(minRatio(off.rim, modal) >= Contrast.AA_LARGE, "off outline dark=${c.isDark}")
            assertTrue(off.track.alpha < 1f, "the off track is a glass veil")
            assertTrue(on.track != off.track)
        }
    }

    @Test
    fun selectionControlsMeetNonTextContrast() {
        listOf(light, dark).forEach { c ->
            val glass = glassBackgrounds(GlassLevel.Card, c) + glassBackgrounds(GlassLevel.Modal, c)
            val (ringOff, _) = glassRadioColors(false, c)
            val (ringOn, dot) = glassRadioColors(true, c)
            assertTrue(minRatio(ringOff, glass) >= Contrast.AA_LARGE, "radio ring dark=${c.isDark}")
            assertTrue(minRatio(ringOn, glass) >= Contrast.AA_LARGE)
            assertTrue(minRatio(dot, glass) >= Contrast.AA_LARGE)
            val unchecked = glassCheckboxColors(ToggleableState.Off, c, onPhoto = false)
            assertTrue(minRatio(unchecked.rim!!, glass) >= Contrast.AA_LARGE, "checkbox outline dark=${c.isDark}")
            val checked = glassCheckboxColors(ToggleableState.On, c, onPhoto = false)
            assertTrue(minRatio(checked.fill, glass) >= Contrast.AA_LARGE, "checkbox fill dark=${c.isDark}")
            assertAa(checked.glyph, checked.fill, Contrast.AA_LARGE, "check glyph dark=${c.isDark}")
            val (border, _) = textFieldBorder(focused = true, isError = false, enabled = true, colors = c)
            assertTrue(minRatio(border, glass) >= Contrast.AA_LARGE, "focused field border dark=${c.isDark}")
            val (track, indicator) = glassProgressColors(c, onPhoto = false)
            assertTrue(minRatio(indicator, glass.map { Contrast.over(track, it) }) >= Contrast.AA_LARGE, "progress dark=${c.isDark}")
        }
        // Thumbnail checkbox over any photo: the white rim on Viewer glass is the boundary.
        val photoBox = glassCheckboxColors(ToggleableState.Off, dark, onPhoto = true)
        photos.forEach { p -> assertAa(photoBox.rim!!, Contrast.over(photoBox.fill, p), Contrast.AA_LARGE, "photo checkbox over $p") }
    }
}
