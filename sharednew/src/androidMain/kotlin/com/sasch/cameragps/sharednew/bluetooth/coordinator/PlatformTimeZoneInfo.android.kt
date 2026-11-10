package com.sasch.cameragps.sharednew.bluetooth.coordinator

import java.time.Instant
import java.time.ZoneId

actual class PlatformTimeZoneInfo actual constructor() {
    private var instant: Instant = Instant.now()
    private var zone: ZoneId = ZoneId.systemDefault()

    /**
     * Test seam: capture the offsets for an explicit zone and instant. The
     * values are stored at construction; the no-arg actual constructor uses
     * the system defaults.
     */
    internal constructor(instant: Instant, zone: ZoneId) : this() {
        this.instant = instant
        this.zone = zone
    }

    actual val standardOffsetMinutes: Int
        get() = zone.rules.getStandardOffset(instant).totalSeconds / 60

    actual val dstOffsetMinutes: Int
        get() = if (zone.rules.isDaylightSavings(instant)) {
            zone.rules.getDaylightSavings(instant).toMinutes().toInt()
        } else 0
}
