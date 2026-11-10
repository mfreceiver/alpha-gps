package com.sasch.cameragps.sharednew.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** Palette of the enclosing [CameraGpsTheme]; light palette when read outside one (tests). */
val LocalAlphaColors = staticCompositionLocalOf { LightAlphaColors }

/**
 * How glass surfaces render below the enclosing [CameraGpsTheme]. [GlassRenderMode.Solid] is the
 * performance / test fallback: no backdrop blur, near-opaque fills (see [GlassTokens]).
 */
val LocalGlassRenderMode = staticCompositionLocalOf { GlassRenderMode.Blur }

private val LightColorScheme = LightAlphaColors.toColorScheme()
private val DarkColorScheme = DarkAlphaColors.toColorScheme()

/**
 * The single theme entry of the app (the app module wraps its content in it; there is no second
 * theme in `app/`). Fixed blue palette — dynamic colour is deliberately not used (design v3 page 6).
 *
 * @param glassRenderMode [GlassRenderMode.Blur] by default; pass [GlassRenderMode.Solid] to turn all
 *   backdrop blur off (low-end devices, screenshots, tests).
 */
@Composable
fun CameraGpsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    glassRenderMode: GlassRenderMode = GlassRenderMode.Blur,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkAlphaColors else LightAlphaColors
    CompositionLocalProvider(
        LocalAlphaColors provides colors,
        LocalGlassRenderMode provides glassRenderMode,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = AlphaMaterialTypography,
            content = content,
        )
    }
}

/** Accessors for the AlphaGPS-specific theme values (Material roles stay on [MaterialTheme]). */
object AlphaTheme {
    val colors: AlphaColors
        @Composable @ReadOnlyComposable get() = LocalAlphaColors.current

    val glassRenderMode: GlassRenderMode
        @Composable @ReadOnlyComposable get() = LocalGlassRenderMode.current
}
