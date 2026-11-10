package com.sasch.cameragps.sharednew.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The six fixed text tiers of AlphaGPS (0.3.0 style unification, docs/ui-audit-2026-11.md §3.4) plus
 * [Mono] for logs / licence texts. UI code takes text sizes **only** from here — no `fontSize =`,
 * no `.copy(fontSize = …)`, no `MaterialTheme.typography.*` (enforced by
 * `scripts/ui_style_guard.sh`). Styles carry no colour; colours come from the [AlphaColors] roles.
 *
 * | Tier | sp / line | Weight | Use |
 * |---|---|---|---|
 * | [Title] | 22 / 28 | SemiBold | page title (top bar), device name in the Ⓘ dialog, welcome / permission headline |
 * | [Heading] | 18 / 24 | SemiBold | dialog title, card / group title, device card name, empty state title |
 * | [Body] | 15 / 22 | Normal | body text, dialog message, row description, text field input |
 * | [Label] | 15 / 20 | SemiBold | every button label (all sizes), emphasised values, row titles |
 * | [Caption] | 13 / 18 | Normal | secondary text, status labels, hints, error lines |
 * | [Tag] | 11 / 14 | SemiBold | info tags / badges only — never for tappable or essential text |
 *
 * `letterSpacing` is 0 everywhere; sizes are `sp` and follow the system font scale.
 */
object AlphaType {
    val Title: TextStyle = tier(22.sp, 28.sp, FontWeight.SemiBold)
    val Heading: TextStyle = tier(18.sp, 24.sp, FontWeight.SemiBold)
    val Body: TextStyle = tier(15.sp, 22.sp, FontWeight.Normal)
    val Label: TextStyle = tier(15.sp, 20.sp, FontWeight.SemiBold)
    val Caption: TextStyle = tier(13.sp, 18.sp, FontWeight.Normal)
    val Tag: TextStyle = tier(11.sp, 14.sp, FontWeight.SemiBold)

    /** Monospace 12 / 16 for the log viewer and licence texts only (outside the six tiers). */
    val Mono: TextStyle = tier(12.sp, 16.sp, FontWeight.Normal, FontFamily.Monospace)

    /** The six tiers in descending visual weight (tests iterate over them). */
    val tiers: List<TextStyle> get() = listOf(Title, Heading, Body, Label, Caption, Tag)

    private fun tier(size: TextUnit, line: TextUnit, weight: FontWeight, family: FontFamily = FontFamily.Default) =
        TextStyle(fontFamily = family, fontWeight = weight, fontSize = size, lineHeight = line, letterSpacing = 0.sp)
}

/**
 * Material typography mapped onto the same six tiers, so a Material / third-party component that
 * reads `MaterialTheme.typography` (or plain `Text` without a style → `bodyLarge`) never grows back
 * to the M3 defaults (32 sp headlines, 0.5 sp letter spacing, …).
 */
val AlphaMaterialTypography = Typography(
    displayLarge = AlphaType.Title,
    displayMedium = AlphaType.Title,
    displaySmall = AlphaType.Title,
    headlineLarge = AlphaType.Title,
    headlineMedium = AlphaType.Title,
    headlineSmall = AlphaType.Title,
    titleLarge = AlphaType.Title,
    titleMedium = AlphaType.Heading,
    titleSmall = AlphaType.Label,
    bodyLarge = AlphaType.Body,
    bodyMedium = AlphaType.Body,
    bodySmall = AlphaType.Caption,
    labelLarge = AlphaType.Label,
    labelMedium = AlphaType.Caption,
    labelSmall = AlphaType.Tag,
)

/**
 * **Transitional aliases** of the pre-0.3.0 component styles, each now pointing at its [AlphaType]
 * tier (audit §3.5 mapping). Pages still using them keep compiling and already render in the new
 * tiers; migrate call sites to [AlphaType] directly — this object is deleted once no page uses it.
 */
object AlphaTextStyles {
    val topBarTitle: TextStyle get() = AlphaType.Title
    val dialogTitle: TextStyle get() = AlphaType.Heading
    val dialogBody: TextStyle get() = AlphaType.Body
    val button: TextStyle get() = AlphaType.Label
    val buttonSmall: TextStyle get() = AlphaType.Label
    val buttonLarge: TextStyle get() = AlphaType.Label
    val buttonHero: TextStyle get() = AlphaType.Label
    val statusValue: TextStyle get() = AlphaType.Label
    val statusLabel: TextStyle get() = AlphaType.Caption
    val tag: TextStyle get() = AlphaType.Tag
}
