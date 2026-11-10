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
    /** Attention accent (dots, icons). Use [warningText] for words — the light amber is too pale. */
    val warning: Color,
    val warningText: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    /**
     * Secondary accent (teal / cyan) for informational icons that should read as "data", not as a
     * state — e.g. the "last write" metric of the device page. Text-safe on G1/G3 glass.
     */
    val accentTeal: Color,
    // Backdrop G0
    /** Scrim behind G3 surfaces, alpha included (light 32 % / dark 52 %). */
    val scrim: Color,
    val glowBlue: Color,
    val glowViolet: Color,
    val glowTeal: Color,
    /** Opacity of the three backdrop glows: top-left blue, right violet, bottom-left teal. */
    val glowAlphas: List<Float>,
)

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
    warningContainer = Color(0xFFFFE9B3),
    onWarningContainer = Color(0xFF261900),
    accentTeal = Color(0xFF006A6A),
    scrim = Color.Black.copy(alpha = 0.32f),
    glowBlue = Color(0xFFA8C7FA),
    glowViolet = Color(0xFFCDBDF7),
    glowTeal = Color(0xFFA6E6D6),
    glowAlphas = listOf(0.60f, 0.42f, 0.36f),
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
    warningContainer = Color(0xFF5C4200),
    onWarningContainer = Color(0xFFFFE9B3),
    accentTeal = Color(0xFF6FD8D8),
    scrim = Color.Black.copy(alpha = 0.52f),
    glowBlue = Color(0xFF0B57D0),
    glowViolet = Color(0xFF5B3FA8),
    glowTeal = Color(0xFF0E6B5C),
    glowAlphas = listOf(0.42f, 0.30f, 0.26f),
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

/** Rating star colours (design v1, kept in v3). Coloured only in the viewer's detail panel. */
object RatingColors {
    val star1 = Color(0xFF90A4AE)
    val star2 = Color(0xFF4FC3F7)
    val star3 = Color(0xFF66BB6A)
    val star4 = Color(0xFFFFB300)
    val star5 = Color(0xFFFF7043)

    /** Colour for a 1–5 rating; anything else falls back to the 1-star grey. */
    fun forRating(rating: Int): Color = when (rating) {
        2 -> star2
        3 -> star3
        4 -> star4
        5 -> star5
        else -> star1
    }
}
