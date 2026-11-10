package com.sasch.cameragps.sharednew.remote.wifi

import com.sasch.cameragps.sharednew.remote.wifi.ExifTestData.ascii
import com.sasch.cameragps.sharednew.remote.wifi.ExifTestData.long
import com.sasch.cameragps.sharednew.remote.wifi.ExifTestData.rational
import com.sasch.cameragps.sharednew.remote.wifi.ExifTestData.short
import com.sasch.cameragps.sharednew.remote.wifi.ExifTestData.tiff
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExifMetadataParserTest {
    @Test
    fun parsesShootingParametersInBothByteOrders() {
        for (little in listOf(true, false)) {
            val exif = assertNotNull(ExifMetadataParser.parse(ExifTestData.jpeg(ExifTestData.sampleExif(little))))
            assertEquals(2.8, exif.apertureF!!, 1e-9)
            assertEquals("f/2.8", exif.apertureText)
            assertEquals("1/250", exif.exposureTimeText)
            assertEquals(400, exif.iso)
            assertEquals("ISO 400", exif.isoText)
            assertEquals("50mm", exif.focalLengthText)
            assertEquals(75, exif.focalLength35mm)
            assertEquals("-0.7 EV", exif.exposureBiasText)
            assertEquals("FE 50mm F1.8", exif.lensModel)
            assertEquals("ILCE-7M4", exif.cameraModel)
            assertEquals(6000, exif.pixelWidth)
            assertEquals(4000, exif.pixelHeight)
        }
    }

    @Test
    fun autoIsoUsesTheExposureIndexTagsInsteadOf65535() {
        val data = tiff(emptyList(), listOf(short(0x8827, 65535), long(0x8832, 1600)))
        assertEquals(1600, ExifMetadataParser.parse(ExifTestData.jpeg(data))!!.iso)
        val none = tiff(emptyList(), listOf(short(0x8827, 65535)))
        assertNull(ExifMetadataParser.parse(ExifTestData.jpeg(none))!!.iso)
    }

    @Test
    fun displayTextCoversSlowShuttersAndExposureBias() {
        fun exposure(num: Long, den: Long) =
            ExifMetadataParser.parse(ExifTestData.jpeg(tiff(emptyList(), listOf(rational(0x829A, num, den)))))!!
        assertEquals("1/8000", exposure(1, 8000).exposureTimeText)
        assertEquals("1/3", exposure(10, 30).exposureTimeText)
        assertEquals("0.5s", exposure(5, 10).exposureTimeText)
        assertEquals("2.5s", exposure(5, 2).exposureTimeText)
        assertEquals("30s", exposure(30, 1).exposureTimeText)
        assertNull(exposure(0, 1).exposureTimeSeconds)
        val zero = PhotoExif(exposureBiasEv = 0.0)
        assertEquals("0 EV", zero.exposureBiasText)
        assertEquals("+1.3 EV", PhotoExif(exposureBiasEv = 4.0 / 3).exposureBiasText)
        assertEquals("-0.3 EV", PhotoExif(exposureBiasEv = -1.0 / 3).exposureBiasText)
        assertEquals("f/16", PhotoExif(apertureF = 16.0).apertureText)
        assertEquals("f/1.4", PhotoExif(apertureF = 1.4).apertureText)
    }

    @Test
    fun zeroDenominatorsAndBlankStringsAreUnknownNotErrors() {
        val data = tiff(
            listOf(ascii(0x0110, "   ")),
            listOf(rational(0x829D, 28, 0), rational(0x920A, 0, 1), ascii(0xA434, "")),
        )
        val exif = ExifMetadataParser.parse(ExifTestData.jpeg(data))!!
        assertNull(exif.apertureF)
        assertNull(exif.focalLengthMm)
        assertNull(exif.lensModel)
        assertNull(exif.cameraModel)
    }

    @Test
    fun missingExifFallsBackToTheFrameHeaderDimensions() {
        val exif = ExifMetadataParser.parse(ExifTestData.jpeg(null, 4000, 3000))!!
        assertEquals(4000, exif.pixelWidth)
        assertEquals(3000, exif.pixelHeight)
        assertNull(exif.apertureF)
        // Exif dimensions win when both are present.
        val data = tiff(emptyList(), listOf(long(0xA002, 1920), long(0xA003, 1080)))
        assertEquals(1920, ExifMetadataParser.parse(ExifTestData.jpeg(data, 4000, 3000))!!.pixelWidth)
    }

    @Test
    fun nonJpegDataIsNotMetadata() {
        assertNull(ExifMetadataParser.parse(byteArrayOf()))
        assertNull(ExifMetadataParser.parse(byteArrayOf(1, 2, 3, 4, 5)))
        assertNull(ExifMetadataParser.parse(ByteArray(100)))
    }

    @Test
    fun skipsOtherSegmentsBeforeTheExifSegment() {
        val app0 = byteArrayOf(0xff.toByte(), 0xe0.toByte(), 0, 16) + "JFIF".encodeToByteArray() +
                byteArrayOf(0, 1, 1, 0, 0, 1, 0, 1, 0, 0)
        val data = ExifTestData.jpeg(ExifTestData.sampleExif(), 6000, 4000, app0)
        assertEquals(400, ExifMetadataParser.parse(data)!!.iso)
    }

    @Test
    fun aHeadCutInsideTheValueAreaKeepsEverythingBeforeTheCut() {
        val full = ExifTestData.jpeg(ExifTestData.sampleExif())
        val exif = ExifMetadataParser.parse(full)!!
        // Cut inside the last out-of-line value (the lens name; SOF + scan data are the final 55
        // bytes): every field before it survives.
        val cut = ExifMetadataParser.parse(full.copyOf(full.size - 55 - 5))!!
        assertEquals(exif.iso, cut.iso)
        assertEquals(exif.apertureF, cut.apertureF)
        assertNull(cut.lensModel)
    }

    @Test
    fun hostileOffsetsAndCountsNeverThrowOrIndexOutOfRange() {
        val hostile = listOf(
            // ExifIFD pointer far outside the data.
            tiff(listOf(long(0x8769, 0xffffffffL)), emptyList()),
            tiff(listOf(long(0x8769, 0x7fffffffL)), emptyList()),
            // Value offsets / counts that overflow Int arithmetic.
            tiff(listOf(ExifTestData.Entry(0x0110, 2, 0x7fffffff, ByteArray(4) { 0x7f })), emptyList()),
            tiff(emptyList(), listOf(ExifTestData.Entry(0x829D, 5, 0x7fffffff, ByteArray(4) { -1 }))),
            tiff(emptyList(), listOf(ExifTestData.Entry(0xA434, 2, -1, ByteArray(4) { -1 }))),
        )
        for (data in hostile) {
            val exif = ExifMetadataParser.parse(ExifTestData.jpeg(data))
            assertNotNull(exif)
            assertTrue(exif.apertureF == null && exif.lensModel == null && exif.cameraModel == null)
        }
        // Entry count 0xFFFF in IFD0.
        val bad = tiff(emptyList(), emptyList())
        bad[8] = 0xff.toByte()
        bad[9] = 0xff.toByte()
        assertNotNull(ExifMetadataParser.parse(ExifTestData.jpeg(bad)))
    }

    @Test
    fun randomCorruptionNeverThrows() {
        val random = Random(1234)
        val base = ExifTestData.jpeg(ExifTestData.sampleExif())
        repeat(2000) {
            val data = base.copyOf(random.nextInt(2, base.size + 1))
            repeat(random.nextInt(1, 6)) { data[random.nextInt(data.size)] = random.nextInt(256).toByte() }
            ExifMetadataParser.parse(data)
        }
    }
}
