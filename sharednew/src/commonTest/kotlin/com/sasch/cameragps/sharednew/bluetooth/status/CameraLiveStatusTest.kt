package com.sasch.cameragps.sharednew.bluetooth.status

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CameraLiveStatusTest {
    @Test
    fun rssiMapsToFourLevelsWithInclusiveLowerBounds() {
        assertEquals(SignalLevel.Excellent, signalLevelFromRssi(-30))
        assertEquals(SignalLevel.Excellent, signalLevelFromRssi(-60))
        assertEquals(SignalLevel.Good, signalLevelFromRssi(-61))
        assertEquals(SignalLevel.Good, signalLevelFromRssi(-70))
        assertEquals(SignalLevel.Fair, signalLevelFromRssi(-71))
        assertEquals(SignalLevel.Fair, signalLevelFromRssi(-80))
        assertEquals(SignalLevel.Poor, signalLevelFromRssi(-81))
        assertEquals(SignalLevel.Poor, signalLevelFromRssi(-100))
        assertEquals(listOf(4, 3, 2, 1, 0), SignalLevel.entries.map { it.bars }.reversed())
    }

    @Test
    fun missingOrImplausibleRssiIsUnknown() {
        assertEquals(SignalLevel.Unknown, signalLevelFromRssi(null))
        assertEquals(SignalLevel.Unknown, signalLevelFromRssi(127)) // Android's "not available" marker
        assertEquals(SignalLevel.Unknown, signalLevelFromRssi(1))
        assertEquals(SignalLevel.Unknown, signalLevelFromRssi(-127))
    }

    @Test
    fun batteryFreshnessFollowsTheThirtyMinuteAndOneDayRules() {
        val minute = 60_000L
        assertEquals(BatteryFreshness.Fresh, batteryFreshness(0))
        assertEquals(BatteryFreshness.Fresh, batteryFreshness(30 * minute))
        assertEquals(BatteryFreshness.Stale, batteryFreshness(30 * minute + 1))
        assertEquals(BatteryFreshness.Stale, batteryFreshness(24 * 60 * minute))
        assertEquals(BatteryFreshness.Expired, batteryFreshness(24 * 60 * minute + 1))
        val battery = CameraBattery(80, obtainedAtMillis = 1_000, cameraId = "A")
        assertEquals(5_000, battery.ageMillis(6_000))
        assertEquals(BatteryFreshness.Fresh, battery.freshness(6_000))
        // A clock that went backwards must not produce a negative age.
        assertEquals(0, battery.ageMillis(500))
    }

    @Test
    fun batteryPercentIsValidated() {
        assertFailsWith<IllegalArgumentException> { CameraBattery(101, 0, "A") }
        assertFailsWith<IllegalArgumentException> { CameraBattery(-1, 0, "A") }
        assertNull(CameraLiveStatus(address = "A").battery)
    }
}
