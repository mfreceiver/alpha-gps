package com.sasch.cameragps.sharednew.remote.wifi

/**
 * Tolerant reader of the shooting parameters in the head of a JPEG (the first ~64 KB of the
 * original). Unlike [SonyJpeg.inspect] it needs neither EOI nor a complete file, never throws, and
 * every missing/garbled tag only drops that one field. All offsets are checked in Long space against
 * the bytes actually available, so hostile offsets cannot overflow into negative indexing.
 */
internal object ExifMetadataParser {
    /** `null` when [head] is not a JPEG at all; an empty [PhotoExif] when it carries no readable EXIF. */
    fun parse(head: ByteArray): PhotoExif? {
        if (head.size < 4 || head[0] != 0xff.toByte() || head[1] != 0xd8.toByte()) return null
        fun byte(at: Int) = head[at].toInt() and 0xff
        var exif: PhotoExif? = null
        var width: Int? = null
        var height: Int? = null
        var offset = 2
        while (offset + 4 <= head.size) {
            if (byte(offset) != 0xff) break
            while (offset < head.size && byte(offset) == 0xff) offset++
            if (offset >= head.size) break
            val marker = byte(offset++)
            if (marker == 0 || marker == 1 || marker in 0xd0..0xd8) continue
            if (marker == 0xd9 || marker == 0xda) break
            if (offset + 2 > head.size) break
            val length = (byte(offset) shl 8) or byte(offset + 1)
            if (length < 2) break
            val segmentEnd = offset.toLong() + length
            val available = minOf(segmentEnd, head.size.toLong()).toInt()
            if (marker == 0xe1 && exif == null) exif = parseExifSegment(head, offset + 2, available)
            if (marker in 0xc0..0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc &&
                length >= 8 && offset + 7 <= head.size
            ) {
                val h = (byte(offset + 3) shl 8) or byte(offset + 4)
                val w = (byte(offset + 5) shl 8) or byte(offset + 6)
                if (w > 0 && h > 0) {
                    width = w
                    height = h
                }
            }
            if (segmentEnd >= head.size) break
            offset = segmentEnd.toInt()
        }
        val base = exif ?: PhotoExif.EMPTY
        return base.copy(
            pixelWidth = base.pixelWidth ?: width,
            pixelHeight = base.pixelHeight ?: height,
        )
    }

    private fun parseExifSegment(data: ByteArray, start: Int, end: Int): PhotoExif? {
        if (end - start < 14) return null
        val header = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0) // "Exif\0\0"
        for (i in header.indices) if (data[start + i] != header[i]) return null
        val tiff = start + 6
        val little = when {
            data[tiff] == 0x49.toByte() && data[tiff + 1] == 0x49.toByte() -> true
            data[tiff] == 0x4d.toByte() && data[tiff + 1] == 0x4d.toByte() -> false
            else -> return null
        }
        val reader = Tiff(data, tiff, end, little)
        if (reader.u16(2) != 42) return null
        val ifd0 = reader.u32(4) ?: return null

        var model: String? = null
        var exifIfd: Long? = null
        reader.entries(ifd0) { tag, type, count, valueAt ->
            when (tag) {
                0x0110 -> model = reader.ascii(type, count, valueAt)
                0x8769 -> exifIfd = reader.integer(type, count, valueAt)
            }
        }

        var aperture: Double? = null
        var exposure: Double? = null
        var iso: Int? = null
        var recommendedIso: Long? = null
        var isoSpeed: Long? = null
        var focal: Double? = null
        var focal35: Int? = null
        var bias: Double? = null
        var lens: String? = null
        var pixelWidth: Int? = null
        var pixelHeight: Int? = null
        exifIfd?.let { pointer ->
            reader.entries(pointer) { tag, type, count, valueAt ->
                when (tag) {
                    0x829A -> exposure = reader.rational(type, count, valueAt)?.takeIf { it > 0 }
                    0x829D -> aperture = reader.rational(type, count, valueAt)?.takeIf { it > 0 }
                    0x8827 -> iso = reader.integer(type, count, valueAt)?.toInt()
                    0x8832 -> recommendedIso = reader.integer(type, count, valueAt)
                    0x8833 -> isoSpeed = reader.integer(type, count, valueAt)
                    0x9204 -> bias = reader.rational(type, count, valueAt)
                    0x920A -> focal = reader.rational(type, count, valueAt)?.takeIf { it > 0 }
                    0xA002 -> pixelWidth = reader.integer(type, count, valueAt)?.toInt()
                    0xA003 -> pixelHeight = reader.integer(type, count, valueAt)?.toInt()
                    0xA405 -> focal35 = reader.integer(type, count, valueAt)?.toInt()?.takeIf { it > 0 }
                    0xA434 -> lens = reader.ascii(type, count, valueAt)
                }
            }
        }
        // Sony writes 65535 ("see the exposure index tags") for Auto ISO.
        val resolvedIso = iso?.takeIf { it in 1..0xfffe }
            ?: (isoSpeed ?: recommendedIso)?.toInt()?.takeIf { it in 1..0xfffffe }
        return PhotoExif(
            apertureF = aperture,
            exposureTimeSeconds = exposure,
            iso = resolvedIso,
            focalLengthMm = focal,
            focalLength35mm = focal35,
            exposureBiasEv = bias,
            lensModel = lens,
            cameraModel = model,
            pixelWidth = pixelWidth?.takeIf { it > 0 },
            pixelHeight = pixelHeight?.takeIf { it > 0 },
        )
    }

    /** Bounds-checked view of the TIFF block: every position is relative to the TIFF header. */
    private class Tiff(val data: ByteArray, val base: Int, val end: Int, val little: Boolean) {
        private val length: Long get() = end.toLong() - base

        private fun byteAt(rel: Long): Int = data[base + rel.toInt()].toInt() and 0xff

        fun u16(rel: Long): Int? {
            if (rel < 0 || length - rel < 2) return null
            val a = byteAt(rel)
            val b = byteAt(rel + 1)
            return if (little) a or (b shl 8) else (a shl 8) or b
        }

        fun u16(rel: Int) = u16(rel.toLong())

        fun u32(rel: Long): Long? {
            if (rel < 0 || length - rel < 4) return null
            var value = 0L
            for (i in 0 until 4) {
                val b = byteAt(rel + i).toLong()
                value = if (little) value or (b shl (8 * i)) else (value shl 8) or b
            }
            return value
        }

        fun u32(rel: Int) = u32(rel.toLong())

        /** Visits up to 512 entries of the IFD at [ifd]; callbacks get the position of the value bytes. */
        inline fun entries(ifd: Long, visit: (tag: Int, type: Int, count: Long, valueAt: Long) -> Unit) {
            val entryCount = u16(ifd) ?: return
            if (entryCount > 512) return
            for (index in 0 until entryCount) {
                val entry = ifd + 2 + index.toLong() * 12
                val tag = u16(entry) ?: return
                val type = u16(entry + 2) ?: return
                val count = u32(entry + 4) ?: return
                val size = (typeSize(type) ?: continue).toLong() * count
                val valueAt = when {
                    size <= 4 -> entry + 8
                    else -> u32(entry + 8) ?: continue
                }
                if (valueAt < 0 || size > length - valueAt) continue
                visit(tag, type, count, valueAt)
            }
        }

        fun typeSize(type: Int): Int? = when (type) {
            1, 2, 6, 7 -> 1
            3, 8 -> 2
            4, 9, 11 -> 4
            5, 10, 12 -> 8
            else -> null
        }

        /** First BYTE/SHORT/LONG value. */
        fun integer(type: Int, count: Long, at: Long): Long? {
            if (count < 1) return null
            return when (type) {
                1 -> byteAt(at).toLong()
                3 -> u16(at)?.toLong()
                4 -> u32(at)
                else -> null
            }
        }

        /** First RATIONAL / SRATIONAL value as a Double; a zero denominator is "unknown". */
        fun rational(type: Int, count: Long, at: Long): Double? {
            if (count < 1 || (type != 5 && type != 10)) return null
            val numerator = u32(at) ?: return null
            val denominator = u32(at + 4) ?: return null
            if (denominator == 0L) return null
            return if (type == 10) numerator.toInt().toDouble() / denominator.toInt()
            else numerator.toDouble() / denominator.toDouble()
        }

        /** ASCII text up to the first NUL, control characters removed, at most 128 chars. */
        fun ascii(type: Int, count: Long, at: Long): String? {
            if (type != 2 || count < 1) return null
            val limit = minOf(count, 128L).toInt()
            val text = StringBuilder()
            for (i in 0 until limit) {
                val b = byteAt(at + i)
                if (b == 0) break
                if (b >= 32 && b != 127) text.append(b.toChar())
            }
            return text.toString().trim().takeIf { it.isNotEmpty() }
        }
    }
}
