package com.sasch.cameragps.sharednew.remote.wifi

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint

internal data class SonyJpegExtent(
    val width: Int,
    val height: Int,
    val byteCount: Int,
    val orientation: Int = 1
)

/** The camera's image region includes padding after EOI. Embedded thumbnail markers are not EOI. */
internal object SonyJpeg {
    fun inspect(data: ByteArray): SonyJpegExtent {
        fun byte(at: Int) = data[at].toInt() and 0xff
        fun uint16(at: Int) = (byte(at) shl 8) or byte(at + 1)
        require(data.size >= 4 && uint16(0) == 0xffd8) { "Invalid JPEG start" }
        var offset = 2
        var width = 0
        var height = 0
        var orientation = 0
        var inScan = false
        while (offset < data.size) {
            if (inScan) while (offset < data.size && byte(offset) != 0xff) offset++
            if (offset == data.size) break
            require(byte(offset) == 0xff) { "Invalid JPEG segment" }
            while (offset < data.size && byte(offset) == 0xff) offset++
            if (offset == data.size) break
            val marker = byte(offset++)
            if (inScan && (marker == 0 || marker in 0xd0..0xd7)) continue
            if (marker == 0xd9) {
                require(width > 0 && height > 0) { "Missing JPEG dimensions" }
                return SonyJpegExtent(width, height, offset, if (orientation in 1..8) orientation else 1)
            }
            require(marker != 0 && marker != 0xd8 && marker !in 0xd0..0xd7) { "Invalid JPEG marker" }
            if (marker == 1) continue
            require(offset + 2 <= data.size) { "Truncated JPEG length" }
            val length = uint16(offset)
            require(length >= 2 && length <= data.size - offset) { "Invalid JPEG length" }
            if (marker in 0xc0..0xcf && marker !in setOf(0xc4, 0xc8, 0xcc)) {
                require(length >= 8) { "Invalid JPEG dimensions" }
                height = uint16(offset + 3)
                width = uint16(offset + 5)
                require(width in 1..4096 && height in 1..4096) { "Invalid JPEG dimensions" }
            }
            if (marker == 0xe1 && orientation == 0) {
                orientation = parseExifOrientation(data, offset + 2, offset + length)
            }
            inScan = marker == 0xda
            offset += length
        }
        throw IllegalArgumentException("Missing JPEG end")
    }

    /** Lenient EXIF parse: any malformed metadata leaves orientation unset (defaults to 1). */
    private fun parseExifOrientation(data: ByteArray, start: Int, end: Int): Int {
        fun byte(at: Int) = data[at].toInt() and 0xff
        fun uint16(at: Int) = (byte(at) shl 8) or byte(at + 1)
        fun uint16le(at: Int) = byte(at) or (byte(at + 1) shl 8)
        fun uint32le(at: Int) = uint16le(at) or (uint16le(at + 2) shl 16)
        fun uint32be(at: Int) = (uint16(at) shl 16) or uint16(at + 2)
        if (end - start < 14) return 0
        if (data[start] != 'E'.code.toByte() || data[start + 1] != 'x'.code.toByte() ||
            data[start + 2] != 'i'.code.toByte() || data[start + 3] != 'f'.code.toByte() ||
            data[start + 4] != 0.toByte() || data[start + 5] != 0.toByte()
        ) return 0
        val tiff = start + 6
        val little = when {
            byte(tiff) == 0x49 && byte(tiff + 1) == 0x49 -> true // "II" little-endian
            byte(tiff) == 0x4d && byte(tiff + 1) == 0x4d -> false // "MM" big-endian
            else -> return 0
        }
        if ((if (little) uint16le(tiff + 2) else uint16(tiff + 2)) != 42) return 0
        val ifd = (if (little) uint32le(tiff + 4) else uint32be(tiff + 4)).toInt()
        if (ifd < 8 || tiff + ifd + 2 > end) return 0
        val entries = if (little) uint16le(tiff + ifd) else uint16(tiff + ifd)
        if (entries > 512 || tiff + ifd + 2 + entries * 12 > end) return 0
        repeat(entries) { index ->
            val entry = tiff + ifd + 2 + index * 12
            val tag = if (little) uint16le(entry) else uint16(entry)
            if (tag != 0x0112) return@repeat
            val type = if (little) uint16le(entry + 2) else uint16(entry + 2)
            val count = if (little) uint32le(entry + 4) else uint32be(entry + 4)
            if (type != 3 || count != 1) return 0
            val value = if (little) uint16le(entry + 8) else uint16(entry + 8)
            return if (value in 1..8) value else 0
        }
        return 0
    }
}

/**
 * Rotates a decoded JPEG bitmap upright per its EXIF orientation. Cameras only emit
 * 90-degree orientations (1/3/6/8); the mirrored variants 2/4/5/7 are practically
 * nonexistent from Sony bodies, so they fall back to the un-rotated pixels.
 */
internal fun ImageBitmap.rotateByExifOrientation(orientation: Int): ImageBitmap {
    if (orientation == 1) return this
    if (orientation != 3 && orientation != 6 && orientation != 8) return this
    val swap = orientation == 6 || orientation == 8
    val output = ImageBitmap(
        if (swap) height else width,
        if (swap) width else height
    )
    val canvas = Canvas(output)
    canvas.translate(output.width / 2f, output.height / 2f)
    when (orientation) {
        3 -> canvas.rotate(180f)
        6 -> canvas.rotate(90f)
        8 -> canvas.rotate(-90f)
    }
    canvas.drawImage(this, Offset(-width / 2f, -height / 2f), Paint())
    return output
}
