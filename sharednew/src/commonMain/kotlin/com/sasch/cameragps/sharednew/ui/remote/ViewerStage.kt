package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackdrop
import com.sasch.cameragps.sharednew.ui.components.glass.LocalGlassBackdrop
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.GlassTokens

/**
 * Dark canvas and explicit blur source for Viewer chrome. Pass null for live images so
 * GV surfaces use their solid fill without sampling frames or an enclosing page.
 */
@Composable
internal fun ViewerStage(backdrop: GlassBackdrop?, content: @Composable BoxScope.() -> Unit) {
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(AlphaTheme.colors.viewerBackground), content = content)
    }
}

internal fun Modifier.viewerChromeScrim(shape: Shape = AlphaShapes.inset): Modifier =
    background(GlassTokens.Viewer.solidFill, shape)
