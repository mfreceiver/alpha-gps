package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** Thumbnails bigger than this are drawn as they are (the camera's letterboxed ones are 160×120). */
private const val LETTERBOX_SCAN_MAX_PIXELS = 640 * 480

/**
 * A camera thumbnail fitted whole into its box and centred (ContentScale.Fit, never cropped). Baked-in
 * black letterbox bars are trimmed first ([letterboxSpan]) so they do not show as black borders.
 */
@Composable
internal fun FittedThumbnail(image: ImageBitmap, modifier: Modifier = Modifier) {
    val painter = remember(image) {
        val (offset, size) = letterboxContent(image)
        BitmapPainter(image, offset, size)
    }
    Image(painter, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
}

/** The picture area of [image] without its letterbox bars; the whole image when there are none. */
private fun letterboxContent(image: ImageBitmap): Pair<IntOffset, IntSize> {
    val w = image.width
    val h = image.height
    val whole = IntOffset.Zero to IntSize(w, h)
    if (w <= 0 || h <= 0 || w.toLong() * h > LETTERBOX_SCAN_MAX_PIXELS) return whole
    val pixels = IntArray(w * h)
    // Reading pixels can fail for hardware-backed bitmaps; then nothing is trimmed.
    if (runCatching { image.readPixels(pixels) }.isFailure) return whole
    val rows = letterboxSpan(h) { y -> (0 until w).all { x -> isLetterboxBlack(pixels[y * w + x]) } }
    val cols = letterboxSpan(w) { x -> (rows).all { y -> isLetterboxBlack(pixels[y * w + x]) } }
    return IntOffset(cols.first, rows.first) to IntSize(cols.last - cols.first + 1, rows.last - rows.first + 1)
}
