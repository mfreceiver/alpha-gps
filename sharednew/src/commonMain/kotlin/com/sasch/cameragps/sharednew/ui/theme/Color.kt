package com.sasch.cameragps.sharednew.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * AlphaGPS fixed blue palette (design v3, page 6: seed #0B57D0, no dynamic colour).
 *
 * Material roles are exposed through [toColorScheme]; the extra roles the Material scheme has no
 * slot for (status colours, the G0 backdrop glow, the scrim strength) live here and are read with
 * [AlphaTheme.colors].
 *
 * Status colours are for icons, dots and short status words only — never for whole surfaces
 * (except the permission / attention strips, which use the *Container* variants).
 */
@Immutable
data class AlphaColors(
    val isDark: Boolean,
    // Core
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    /** G0 base colour under the backdrop glow. */
    val background: Color,
    val surface: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    // Status
    /** Success / OK: connected, corrected, located, saved. */
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    /**
     * Legacy pale amber (light #F9A825) — decorative only, fails 3:1 on light glass. Use
     * [warningIcon] for icons / dots and [warningText] for words.
     */
    val warning: Color,
    /** Warning words (role `warning`, text): ≥ 4.5:1 on every glass level. */
    val warningText: Color,
    /** Warning icons / dots (role `warning`, non-text): ≥ 3:1 on every glass level. */
    val warningIcon: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    /**
     * Secondary accent (teal / cyan) for informational icons that should read as "data", not as a
     * state — e.g. the "last write" metric of the device page. Text-safe on G1/G3 glass.
     */
    val accentTeal: Color,
    /**
     * Bluetooth brand blue for the Bluetooth status icon of the device page only — a recognisable
     * glyph colour, not a state. Light #0082FC (brand); dark #4DA6FF (lightened: the brand value is
     * only ≈2.4:1 on dark card glass). Non-text: ≥ 3:1 on G1 card glass (PaletteContrastTest).
     */
    val bluetoothBlue: Color,
    // Text roles that have no Material slot
    /** Role `textMuted`: disabled / placeholder / auxiliary counts. Opaque, ≥ 3:1 on every glass level. */
    val textMuted: Color,
    /** Role `onPhoto`: text / icons on Viewer glass or directly over a photo (always white). */
    val onPhoto: Color,
    /** Secondary text over a photo that must still meet 4.5:1 (white 85 %). */
    val onPhotoSecondary: Color,
    /** De-emphasised marks over a photo, ≥ 3:1 only (white 72 %). */
    val onPhotoMuted: Color,
    /**
     * Warning text / icons on Viewer glass or over a photo (e.g. "no JPG"). The Viewer is dark in
     * both themes, so this is a light amber in both — the light theme's [warningText] (dark brown)
     * falls to ~1:1 on Viewer glass over a white photo. ≥ 4.5:1 on every Viewer background.
     */
    val onPhotoWarning: Color,
    /** Error text / destructive icons on Viewer glass or over a photo; light red in both themes (see [onPhotoWarning]). */
    val onPhotoDanger: Color,
    // Backdrop G0
    /** Scrim behind G3 surfaces, alpha included (light 32 % / dark 52 %). */
    val scrim: Color,
    /** Low-contrast coloured light orbs of the G0 backdrop (D1), drawn in order over [background]. */
    val orbs: List<BackdropOrb>,
    // Thumbnails
    /**
     * Fill of a photo tile / filmstrip cell, visible around a fitted camera thumbnail. Deliberately
     * near-black in both themes so the camera's baked-in letterbox bars blend into the frame instead
     * of reading as borders (the decision against trimming them is recorded in docs/todo.md).
     */
    val thumbnailFrame: Color,
    /** Scrim behind an unselected corner badge on a near-black thumbnail frame. */
    val thumbnailBadgeScrim: Color,
    /** Stroke of an unselected corner badge and of the filmstrip's current frame (on dark frames). */
    val thumbnailBadgeStroke: Color,
) {
    // The eight colour roles (audit §3.4). `success` and `warningText` / `warningIcon` (role
    // `warning`) are constructor fields above; `textMuted` and `onPhoto` too.
    /** Role `textPrimary`: main text, titles. */
    val textPrimary: Color get() = onSurface
    /** Role `textSecondary`: secondary text, descriptions, default icon tint. */
    val textSecondary: Color get() = onSurfaceVariant
    /** Role `accent`: emphasis, links, selection, primary buttons. */
    val accent: Color get() = primary
    /** Role `danger`: errors and destructive actions. */
    val danger: Color get() = error
}

/**
 * One soft radial light of the G0 backdrop: centre ([x], [y]) as fractions of the canvas width /
 * height, [radius] as a fraction of the canvas' **shorter** side (so landscape does not blow the
 * orbs up), peak [color] opacity [alpha] fading to transparent at the radius.
 */
@Immutable
data class BackdropOrb(val x: Float, val y: Float, val radius: Float, val color: Color, val alpha: Float)

val LightAlphaColors = AlphaColors(
    isDark = false,
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF565E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF6B4FA8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE9DDFF),
    onTertiaryContainer = Color(0xFF22005D),
    background = Color(0xFFF3F6FC),
    surface = Color(0xFFF9FAFE),
    surfaceDim = Color(0xFFD9DAE0),
    surfaceBright = Color(0xFFF9FAFE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F6FC),
    surfaceContainer = Color(0xFFEDF1F7),
    surfaceContainerHigh = Color(0xFFE7EBF2),
    surfaceContainerHighest = Color(0xFFE1E6ED),
    onSurface = Color(0xFF191C20),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF72777F),
    outlineVariant = Color(0xFFC2C7CF),
    inverseSurface = Color(0xFF2E3135),
    inverseOnSurface = Color(0xFFEFF0F7),
    inversePrimary = Color(0xFFA8C7FA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    success = Color(0xFF1B6E3A),
    successContainer = Color(0xFFC8F0D2),
    onSuccessContainer = Color(0xFF00210D),
    warning = Color(0xFFF9A825),
    warningText = Color(0xFF7A5200),
    // #F9A825 is 1.7:1 on light glass; this deeper amber keeps the hue at ≥ 4.4:1.
    warningIcon = Color(0xFFA15C00),
    warningContainer = Color(0xFFFFE9B3),
    onWarningContainer = Color(0xFF261900),
    accentTeal = Color(0xFF006A6A),
    bluetoothBlue = Color(0xFF0082FC),
    textMuted = Color(0xFF696E75),
    onPhoto = Color.White,
    onPhotoSecondary = Color.White.copy(alpha = 0.85f),
    onPhotoMuted = Color.White.copy(alpha = 0.72f),
    onPhotoWarning = Color(0xFFFFE08A),
    onPhotoDanger = Color(0xFFFFDAD6),
    scrim = Color.Black.copy(alpha = 0.32f),
    orbs = listOf(
        BackdropOrb(0.10f, 0.08f, 0.80f, Color(0xFFA8C7FA), 0.55f), // blue, top-left (main light)
        BackdropOrb(1.00f, 0.42f, 0.66f, Color(0xFFCDBDF7), 0.40f), // violet, right
        BackdropOrb(0.18f, 0.92f, 0.72f, Color(0xFFA6E6D6), 0.36f), // teal, bottom-left
        BackdropOrb(0.86f, 0.04f, 0.42f, Color(0xFFBEE3FF), 0.38f), // sky, behind the top bar
        BackdropOrb(0.72f, 0.80f, 0.46f, Color(0xFFF6D3E4), 0.24f), // faint rose, warm counterpoint
    ),
    thumbnailFrame = Color(0xFF0E0E10),
    thumbnailBadgeScrim = Color.Black.copy(alpha = 0.25f),
    thumbnailBadgeStroke = Color.White,
)

val DarkAlphaColors = AlphaColors(
    isDark = true,
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283041),
    secondaryContainer = Color(0xFF3C4758),
    onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary = Color(0xFFCFBCFF),
    onTertiary = Color(0xFF381E72),
    tertiaryContainer = Color(0xFF4F378A),
    onTertiaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF0E1217),
    surface = Color(0xFF101418),
    surfaceDim = Color(0xFF101418),
    surfaceBright = Color(0xFF36393E),
    surfaceContainerLowest = Color(0xFF0B0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2125),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32363B),
    onSurface = Color(0xFFE0E2E8),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
    inverseSurface = Color(0xFFE0E2E8),
    inverseOnSurface = Color(0xFF2E3135),
    inversePrimary = Color(0xFF0B57D0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    success = Color(0xFF7DDB93),
    successContainer = Color(0xFF0B5227),
    onSuccessContainer = Color(0xFFC8F0D2),
    warning = Color(0xFFFFC857),
    warningText = Color(0xFFFFC857),
    warningIcon = Color(0xFFFFC857),
    warningContainer = Color(0xFF5C4200),
    onWarningContainer = Color(0xFFFFE9B3),
    accentTeal = Color(0xFF6FD8D8),
    bluetoothBlue = Color(0xFF4DA6FF),
    textMuted = Color(0xFFA3A8B0),
    onPhoto = Color.White,
    onPhotoSecondary = Color.White.copy(alpha = 0.85f),
    onPhotoMuted = Color.White.copy(alpha = 0.72f),
    onPhotoWarning = Color(0xFFFFE08A),
    onPhotoDanger = Color(0xFFFFDAD6),
    scrim = Color.Black.copy(alpha = 0.52f),
    orbs = listOf(
        BackdropOrb(0.10f, 0.08f, 0.80f, Color(0xFF0B57D0), 0.40f), // blue, top-left (main light)
        BackdropOrb(1.00f, 0.42f, 0.66f, Color(0xFF5B3FA8), 0.30f), // violet, right
        BackdropOrb(0.18f, 0.92f, 0.72f, Color(0xFF0E6B5C), 0.26f), // teal, bottom-left
        BackdropOrb(0.86f, 0.04f, 0.42f, Color(0xFF1A6FA8), 0.26f), // sky, behind the top bar
        BackdropOrb(0.72f, 0.80f, 0.46f, Color(0xFF7A3466), 0.18f), // faint plum, warm counterpoint
    ),
    thumbnailFrame = Color(0xFF0E0E10),
    thumbnailBadgeScrim = Color.Black.copy(alpha = 0.25f),
    thumbnailBadgeStroke = Color.White,
)

/** Material 3 scheme for this palette; every role is set so no M3 baseline (purple) leaks through. */
fun AlphaColors.toColorScheme(): ColorScheme =
    if (isDark) {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary, onSecondary = onSecondary,
            secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            background = background, onBackground = onSurface,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceContainerHighest, onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary,
            inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
            error = error, onError = onError,
            errorContainer = errorContainer, onErrorContainer = onErrorContainer,
            outline = outline, outlineVariant = outlineVariant,
            scrim = Color.Black,
            surfaceBright = surfaceBright, surfaceDim = surfaceDim,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = surfaceContainerLowest,
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary, onSecondary = onSecondary,
            secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            background = background, onBackground = onSurface,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceContainerHighest, onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary,
            inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
            error = error, onError = onError,
            errorContainer = errorContainer, onErrorContainer = onErrorContainer,
            outline = outline, outlineVariant = outlineVariant,
            scrim = Color.Black,
            surfaceBright = surfaceBright, surfaceDim = surfaceDim,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = surfaceContainerLowest,
        )
    }
