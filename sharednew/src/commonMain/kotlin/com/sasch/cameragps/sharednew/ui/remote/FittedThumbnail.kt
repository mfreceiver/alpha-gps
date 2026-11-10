package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale

/**
 * A camera thumbnail fitted whole into its box and centred (ContentScale.Fit, never cropped).
 * The camera's baked-in black letterbox bars are NOT trimmed: the tile frame is near-black
 * (see [com.sasch.cameragps.sharednew.ui.theme.AlphaColors.thumbnailFrame]) so they blend in.
 * Whether a thumbnail really carries black bars is measured by the diagnostics logged in
 * WifiRemoteController's thumbnail loader (tag "AlphaThumb"), not by the display path.
 */
@Composable
internal fun FittedThumbnail(image: ImageBitmap, modifier: Modifier = Modifier) {
    val painter = remember(image) { BitmapPainter(image) }
    Image(painter, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
}
