package com.sasch.cameragps.sharednew.remote.wifi

/** Builds synthetic JPEG heads with an EXIF APP1 segment; shared by the metadata tests. */
internal object ExifTestData {
    class Entry(val tag: Int, val type: Int, val count: Int, val data: ByteArray)

    fun bytes(value: Long, size: Int, little: Boolean): ByteArray =
        ByteArray(size) { i ->
            val shift = 8 * (if (little) i else size - 1 - i)
            (value ushr shift).toByte()
        }

    fun short(tag: Int, value: Int, little: Boolean = true) = Entry(tag, 3, 1, bytes(value.toLong(), 2, little))
    fun long(tag: Int, value: Long, little: Boolean = true) = Entry(tag, 4, 1, bytes(value, 4, little))
    fun rational(tag: Int, num: Long, den: Long, little: Boolean = true) =
        Entry(tag, 5, 1, bytes(num, 4, little) + bytes(den, 4, little))
    fun srational(tag: Int, num: Int, den: Int, little: Boolean = true) =
        Entry(tag, 10, 1, bytes(num.toLong() and 0xffffffffL, 4, little) + bytes(den.toLong(), 4, little))
    fun ascii(tag: Int, text: String) =
        Entry(tag, 2, text.length + 1, text.encodeToByteArray() + byteArrayOf(0))

    /** TIFF block: IFD0 (+ ExifIFD pointer when [exif] is not empty), ExifIFD, out-of-line value area. */
    fun tiff(ifd0: List<Entry>, exif: List<Entry>, little: Boolean = true): ByteArray {
        fun ifdSize(entries: Int) = 2 + entries * 12 + 4
        val withPointer = ifd0.size + if (exif.isEmpty()) 0 else 1
        val ifd0Offset = 8
        val exifOffset = ifd0Offset + ifdSize(withPointer)
        var dataOffset = exifOffset + if (exif.isEmpty()) 0 else ifdSize(exif.size)
        val area = mutableListOf<Byte>()
        fun encode(entries: List<Entry>): ByteArray {
            var out = bytes(entries.size.toLong(), 2, little)
            for (entry in entries) {
                out += bytes(entry.tag.toLong(), 2, little) + bytes(entry.type.toLong(), 2, little) +
                        bytes(entry.count.toLong(), 4, little)
                if (entry.data.size <= 4) {
                    out += entry.data + ByteArray(4 - entry.data.size)
                } else {
                    out += bytes((dataOffset + area.size).toLong(), 4, little)
                    area += entry.data.toList()
                }
            }
            return out + ByteArray(4)
        }
        val pointer = if (exif.isEmpty()) emptyList() else listOf(long(0x8769, exifOffset.toLong(), little))
        val header = (if (little) byteArrayOf(0x49, 0x49) else byteArrayOf(0x4d, 0x4d)) +
                bytes(42, 2, little) + bytes(ifd0Offset.toLong(), 4, little)
        val ifd0Bytes = encode(ifd0 + pointer)
        val exifBytes = if (exif.isEmpty()) byteArrayOf() else encode(exif)
        return header + ifd0Bytes + exifBytes + area.toByteArray()
    }

    private fun segment(marker: Int, payload: ByteArray) =
        byteArrayOf(0xff.toByte(), marker.toByte()) + bytes((payload.size + 2).toLong(), 2, false) + payload

    fun sof(width: Int, height: Int) = segment(
        0xc0,
        byteArrayOf(8) + bytes(height.toLong(), 2, false) + bytes(width.toLong(), 2, false) +
                byteArrayOf(1, 1, 0x11, 0),
    )

    fun jpeg(tiff: ByteArray?, width: Int = 6000, height: Int = 4000, vararg extra: ByteArray): ByteArray {
        var out = byteArrayOf(0xff.toByte(), 0xd8.toByte())
        if (tiff != null) out += segment(0xe1, "Exif".encodeToByteArray() + byteArrayOf(0, 0) + tiff)
        for (e in extra) out += e
        out += sof(width, height)
        out += byteArrayOf(0xff.toByte(), 0xda.toByte(), 0, 2) + ByteArray(32) { 1 }
        return out
    }

    /** A realistic Sony-like set of shooting parameters. */
    fun sampleExif(little: Boolean = true): ByteArray = tiff(
        ifd0 = listOf(ascii(0x0110, "ILCE-7M4"), short(0x0112, 1, little)),
        exif = listOf(
            rational(0x829A, 1, 250, little),
            rational(0x829D, 28, 10, little),
            short(0x8827, 400, little),
            srational(0x9204, -7, 10, little),
            rational(0x920A, 50, 1, little),
            long(0xA002, 6000, little),
            long(0xA003, 4000, little),
            short(0xA405, 75, little),
            ascii(0xA434, "FE 50mm F1.8"),
        ),
        little = little,
    )
}
