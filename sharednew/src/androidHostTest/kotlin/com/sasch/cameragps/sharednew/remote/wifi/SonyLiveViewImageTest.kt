package com.sasch.cameragps.sharednew.remote.wifi

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ported from iosTest's SonyLiveViewImageTest (decoded with the iOS image
 * decoder there). Android's BitmapFactory is unavailable in JVM host tests, so
 * the payload is decoded with ImageIO instead — the assertion kept is that the
 * decoder's extracted, padded-frame image bytes are a complete, valid JPEG of
 * the expected geometry and fully opaque.
 */
class SonyLiveViewImageTest {
    @Test
    fun paddedFrameDecodesAsACompleteOpaqueJpeg() {
        val frame = SonyLiveViewFrameDecoder().feed(previewFrameFixture()).single()
        val image = ImageIO.read(ByteArrayInputStream(frame.imageBytes))
        assertEquals(2, image.width)
        assertEquals(2, image.height)
        val pixels = IntArray(4)
        image.getRGB(0, 0, 2, 2, pixels, 0, 2)
        assertEquals(4, pixels.count { (it ushr 24) == 255 })
    }
}
