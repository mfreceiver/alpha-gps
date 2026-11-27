package com.sasch.cameragps.sharednew.remote.wifi

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SonyRemotePropertiesTest {

    @Test
    fun formatsRealIlce1PropertyValuesAccurately() {
        assertEquals("f/2.4", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.Aperture, 240L))
        assertEquals("f/4", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.Aperture, 400L))
        assertEquals("f/1.4", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.Aperture, 140L))
        assertEquals("A", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.ExposureProgram, 131075L))
        assertEquals("P", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.ExposureProgram, 65538L))
        assertEquals("1/90", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.ShutterSpeed, 65626L))
        assertEquals("30\"", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.ShutterSpeed, 19660810L))
        assertEquals("BULB", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.ShutterSpeed, 0L))
        assertEquals("AUTO", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.Iso, 16777215L))
        assertEquals("ISO 50 (L)", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.Iso, 268435506L))
        assertEquals("ISO 400", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.Iso, 400L))
        assertEquals("0.0 EV", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.ExposureBias, 0L))
        assertEquals("-0.5 EV", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.ExposureBias, 65036L))
        assertEquals("+1.5 EV", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.ExposureBias, 1500L))
        assertEquals("AWB", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.WhiteBalance, 2L))
        assertEquals("AF-C", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.FocusMode, 32772L))
        assertEquals("Multi", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.MeteringMode, 32769L))
        assertEquals("Fill", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.FlashMode, 3L))
        assertEquals("Single", SonyRemotePropertyFormatter.formatValue(CameraShootingPropertyKey.DriveMode, 1L))
    }

    @Test
    fun parsesDescriptorAccessEnabledAndCurrentChoices() {
        val raw = buildPropertyBlob(
            // Aperture 0x5007: UINT16(4), access=1, enabled=1, form=2, current=240, choices=[140, 240, 400]
            propEntry(code = 0x5007, type = 4, access = 1, enabled = 1, defaultVal = 240L, currentVal = 240L,
                currentChoices = listOf(140L, 240L, 400L), supportedChoices = listOf(140L, 240L, 400L)),
            // Mode 0x500e: UINT32(6), access=1, enabled=2 (display-only on physical dial), currentChoices=[]
            propEntry(code = 0x500e, type = 6, access = 1, enabled = 2, defaultVal = 131075L, currentVal = 131075L,
                currentChoices = emptyList(), supportedChoices = listOf(65538L, 131075L)),
        )
        val parsed = SonyCameraProperties.parse(raw, emptySet(), CameraShootingPropertyKey.allCodes)
        val shooting = SonyRemotePropertyFormatter.toShootingProperties(parsed, supportsWriteOp = true)

        val mode = shooting.first { it.key == CameraShootingPropertyKey.ExposureProgram }
        assertEquals("A", mode.displayValue)
        assertFalse(mode.writable, "enabled=2 with empty currentChoices must stay read-only")

        val aperture = shooting.first { it.key == CameraShootingPropertyKey.Aperture }
        assertEquals("f/2.4", aperture.displayValue)
        assertTrue(aperture.writable)
        assertEquals(listOf("f/1.4", "f/2.4", "f/4"), aperture.options.map { it.label })

        val readOnly = SonyRemotePropertyFormatter.toShootingProperties(parsed, supportsWriteOp = false)
        assertFalse(readOnly.first { it.key == CameraShootingPropertyKey.Aperture }.writable)
    }

    @Test
    fun extractsBatteryAndRemainingShotsIntoSnapshot() {
        val raw = buildPropertyBlob(
            propEntry(code = CameraShootingPropertyKey.SONY_BATTERY_LEVEL, type = 1, access = 0, enabled = 1,
                defaultVal = 82L, currentVal = 82L, currentChoices = emptyList(), supportedChoices = emptyList()),
            propEntry(code = CameraShootingPropertyKey.SONY_MEDIA_SLOT1_REMAINING_SHOTS, type = 6, access = 0, enabled = 2,
                defaultVal = 1240L, currentVal = 1240L, currentChoices = emptyList(), supportedChoices = emptyList()),
        )
        val parsed = SonyCameraProperties.parse(raw, emptySet(), CameraShootingPropertyKey.readCodes)
        val snapshot = SonyRemotePropertyFormatter.toSnapshot(parsed, supportsWriteOp = true)
        assertEquals(82, snapshot.batteryPercent)
        assertEquals(1240, snapshot.remainingShots)
    }

    @Test
    fun encodesLittleEndianScalarsForSetProperty() {
        assertContentEquals(byteArrayOf(0xf0.toByte(), 0x00), SonyCameraProperties.encodeScalar(4, 240L))
        assertContentEquals(byteArrayOf(0x00, 0x00, 0x01, 0x00), SonyCameraProperties.encodeScalar(6, 0x10000L))
        assertContentEquals(byteArrayOf(0x0c, 0xfe.toByte()), SonyCameraProperties.encodeScalar(3, -500L))
    }

    private fun buildPropertyBlob(vararg entries: ByteArray): ByteArray {
        val out = mutableListOf<Byte>()
        putLe(out, entries.size.toLong(), 8)
        entries.forEach { out.addAll(it.toList()) }
        return out.toByteArray()
    }

    private fun propEntry(
        code: Int,
        type: Int,
        access: Int,
        enabled: Int,
        defaultVal: Long,
        currentVal: Long,
        currentChoices: List<Long>,
        supportedChoices: List<Long>,
    ): ByteArray {
        val size = SonyCameraProperties.scalarByteSize(type)!!
        val out = mutableListOf<Byte>()
        putLe(out, code.toLong(), 2)
        putLe(out, type.toLong(), 2)
        putLe(out, access.toLong(), 1)
        putLe(out, enabled.toLong(), 1)
        putLe(out, defaultVal, size)
        putLe(out, currentVal, size)
        putLe(out, 2L, 1) // enum form
        putLe(out, currentChoices.size.toLong(), 2)
        currentChoices.forEach { putLe(out, it, size) }
        putLe(out, supportedChoices.size.toLong(), 2)
        supportedChoices.forEach { putLe(out, it, size) }
        return out.toByteArray()
    }

    private fun putLe(out: MutableList<Byte>, value: Long, bytes: Int) {
        repeat(bytes) { i -> out.add(((value ushr (8 * i)) and 0xff).toByte()) }
    }
}
