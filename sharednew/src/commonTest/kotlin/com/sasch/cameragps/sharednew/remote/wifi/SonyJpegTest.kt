package com.sasch.cameragps.sharednew.remote.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SonyJpegTest {
    @Test
    fun ignoresThumbnailEndMarkersAndStuffedEntropy() {
        val app = bytes(0xff, 0xe1, 0, 6, 0xff, 0xd9, 0xff, 0xd8)
        val sof = testJpeg().copyOfRange(2, 15)
        val scan = bytes(0xff, 0xda, 0, 8, 1, 1, 0, 0, 0x3f, 0)
        val jpeg = bytes(0xff, 0xd8) + app + sof + scan +
            bytes(1, 0xff, 0, 0xd9, 0xff, 0xd0, 2, 0xff, 0xd9)
        assertEquals(SonyJpegExtent(640, 424, jpeg.size), SonyJpeg.inspect(jpeg + ByteArray(34)))
        assertFailsWith<IllegalArgumentException> { SonyJpeg.inspect(jpeg.copyOf(jpeg.size - 2)) }
    }

    @Test
    fun rejectsTruncatedLengthsAndOversizedDimensions() {
        assertFailsWith<IllegalArgumentException> { SonyJpeg.inspect(bytes(0xff, 0xd8, 0xff, 0xe1, 0xff, 0xff)) }
        val tooWide = testJpeg().also { it[9] = 0x20 }
        assertFailsWith<IllegalArgumentException> { SonyJpeg.inspect(tooWide) }
    }

    @Test
    fun parsesExifOrientationInBothEndianNesses() {
        assertEquals(6, SonyJpeg.inspect(jpegWithExifOrientation(6, littleEndian = true)).orientation)
        assertEquals(8, SonyJpeg.inspect(jpegWithExifOrientation(8, littleEndian = false)).orientation)
        assertEquals(3, SonyJpeg.inspect(jpegWithExifOrientation(3, littleEndian = true)).orientation)
        assertEquals(1, SonyJpeg.inspect(jpegWithExifOrientation(1, littleEndian = false)).orientation)
    }

    @Test
    fun defaultsOrientationWithoutUsableExif() {
        // No APP1 at all.
        assertEquals(1, SonyJpeg.inspect(testJpeg()).orientation)
        // APP1 present but not Exif payload.
        // "XMPxx" (5) + two NULs = 7 payload bytes; segment length covers 2 + 7.
        val notExif = bytes(0xff, 0xd8, 0xff, 0xe1, 0, 9) + "XMPxx\u0000\u0000".encodeToByteArray() +
            testJpeg().copyOfRange(2, testJpeg().size)
        assertEquals(1, SonyJpeg.inspect(notExif).orientation)
        // Orientation outside the valid 1..8 range falls back to 1.
        assertEquals(1, SonyJpeg.inspect(jpegWithExifOrientation(9, littleEndian = true)).orientation)
        assertEquals(1, SonyJpeg.inspect(jpegWithExifOrientation(0, littleEndian = false)).orientation)
    }

    @Test
    fun rejectsHostileIfdOffsetsInBothEndianNesses() {
        // An IFD offset near Int.MAX_VALUE used to overflow the additive bounds check
        // (`tiff + ifd + 2 > end`) into a negative value and crash with a negative index;
        // it must instead degrade to the default orientation.
        assertEquals(1, SonyJpeg.inspect(jpegWithExifOrientation(6, littleEndian = true, ifdOffset = 0x7fffffff)).orientation)
        assertEquals(1, SonyJpeg.inspect(jpegWithExifOrientation(6, littleEndian = false, ifdOffset = 0x7fffffff)).orientation)
        assertEquals(1, SonyJpeg.inspect(jpegWithExifOrientation(6, littleEndian = true, ifdOffset = 0x7ffffff0)).orientation)
    }

    private fun jpegWithExifOrientation(
        orientation: Int,
        littleEndian: Boolean,
        ifdOffset: Int = 8,
    ): ByteArray {
        fun u16(value: Int) = if (littleEndian) bytes(value and 0xff, value shr 8)
        else bytes(value shr 8, value and 0xff)

        fun u32(value: Int) = if (littleEndian)
            bytes(value and 0xff, (value shr 8) and 0xff, (value shr 16) and 0xff, (value shr 24) and 0xff)
        else bytes((value shr 24) and 0xff, (value shr 16) and 0xff, (value shr 8) and 0xff, value and 0xff)

        val tiff = (if (littleEndian) bytes(0x49, 0x49) else bytes(0x4d, 0x4d)) + u16(42) + u32(ifdOffset) +
            u16(1) + u16(0x0112) + u16(3) + u32(1) + u16(orientation) + bytes(0, 0) + u32(0)
        val payload = "Exif".encodeToByteArray() + bytes(0, 0) + tiff
        val base = testJpeg()
        val sof = base.copyOfRange(2, 15)
        val tail = base.copyOfRange(15, base.size)
        // JPEG segment lengths are always big-endian, independent of the TIFF endian.
        return bytes(0xff, 0xd8) + bytes(0xff, 0xe1) +
                bytes((payload.size + 2) shr 8, (payload.size + 2) and 0xff) +
                payload + sof + tail
    }

    private fun bytes(vararg values: Int) = values.map(Int::toByte).toByteArray()
}
