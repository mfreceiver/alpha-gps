package com.sasch.cameragps.sharednew.remote.wifi

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.test.Test
import kotlin.test.assertSame

class ExifOrientationRotationTest {
    private class FakeBitmap(override val width: Int = 4, override val height: Int = 3) : ImageBitmap {
        override val colorSpace = ColorSpaces.Srgb
        override val hasAlpha = false
        override val config = ImageBitmapConfig.Argb8888
        override fun prepareToDraw() = Unit
        override fun readPixels(
            buffer: IntArray, startX: Int, startY: Int, width: Int,
            height: Int, bufferOffset: Int, stride: Int
        ) = Unit
    }

    @Test
    fun uprightAndUnsupportedOrientationsReturnOriginalBitmap() {
        val bitmap = FakeBitmap()
        // 1 = already upright; 2/4/5/7 are mirrored variants Sony cameras do not emit —
        // they intentionally fall back to the un-rotated pixels.
        for (orientation in listOf(1, 2, 4, 5, 7)) {
            assertSame(bitmap, bitmap.rotateByExifOrientation(orientation), "orientation $orientation")
        }
    }
}
