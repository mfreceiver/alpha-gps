package com.sasch.cameragps.sharednew.bluetooth.status

import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Read-only, per-camera status snapshot for the device page. Pure data: the screens format it,
 * nothing here knows about strings or time zones. All timestamps are epoch milliseconds from the
 * hub's clock; `null` means "never observed in this process" (or since the last reset documented on
 * the field).
 */
data class CameraLiveStatus(
    /** Upper-case MAC address — the same key as `CameraSessionRegistry.sessions`. */
    val address: String,
    /** BLE session phase; [BleSessionPhase.Disconnected] when only retained statistics remain. */
    val phase: BleSessionPhase = BleSessionPhase.Disconnected,
    val timeSync: TimeSyncStatus = TimeSyncStatus(),
    val gps: GpsWriteStats = GpsWriteStats(),
    /** Last successful RSSI read (dBm) of the active GATT link; null when never read or after repeated failures. */
    val rssiDbm: Int? = null,
    val rssiAtMillis: Long? = null,
    /** Negotiated ATT MTU (mirrors `CameraSession.mtu`); null while unknown. */
    val mtu: Int? = null,
    /** Last battery reading obtained over the Wi-Fi/PTP session, cached across sessions. */
    val battery: CameraBattery? = null,
) {
    /** Convenience: the 4-step signal bucket of [rssiDbm]. */
    val signalLevel: SignalLevel get() = signalLevelFromRssi(rssiDbm)

    /** Convenience: latest camera-acknowledged GPS write (see [GpsWriteStats.lastWriteAtMillis]). */
    val lastWriteAtMillis: Long? get() = gps.lastWriteAtMillis

    val connected: Boolean get() = phase != BleSessionPhase.Disconnected && phase != BleSessionPhase.Error
}

/** Four visible steps plus [Unknown] (no reading). [bars] is 0..4, ready to draw. */
enum class SignalLevel(val bars: Int) {
    Unknown(0),
    Poor(1),
    Fair(2),
    Good(3),
    Excellent(4),
}

/**
 * Maps a BLE RSSI (dBm) to a [SignalLevel]. Thresholds follow the usual BLE rule of thumb:
 * `>= -60` excellent, `>= -70` good, `>= -80` fair, otherwise poor. Values outside the range an
 * Android stack can report (`-127..0`; `127` and positive numbers mean "not available") are
 * [SignalLevel.Unknown].
 */
fun signalLevelFromRssi(rssiDbm: Int?): SignalLevel = when {
    rssiDbm == null || rssiDbm > 0 || rssiDbm <= -127 -> SignalLevel.Unknown
    rssiDbm >= -60 -> SignalLevel.Excellent
    rssiDbm >= -70 -> SignalLevel.Good
    rssiDbm >= -80 -> SignalLevel.Fair
    else -> SignalLevel.Poor
}

enum class TimeSyncOutcome {
    /** No time correction was attempted in this process for this camera. */
    NotRun,

    /** The correction packet is being exchanged. */
    InProgress,

    /** The camera confirmed it applied the time. */
    Applied,

    /** The packet was written but the camera sent no result (older firmware, or the 30 s wait ended). */
    Unconfirmed,

    /** The camera answered but rejected the setting; [TimeSyncStatus.rejectReason] has its code. */
    Rejected,

    /** The write could not be delivered or the exchange failed unexpectedly. */
    Failed,

    /** The camera does not offer direct time setting. */
    Unsupported,
}

/** Most recent time-correction result. [atMillis] is when [outcome] was reached (start time while in progress). */
data class TimeSyncStatus(
    val outcome: TimeSyncOutcome = TimeSyncOutcome.NotRun,
    val atMillis: Long? = null,
    /** Camera-reported reason code of a [TimeSyncOutcome.Rejected] outcome. */
    val rejectReason: Long? = null,
)

/**
 * Location-write counters of one camera. [queuedCount] and [writtenCount] are deliberately separate:
 * a packet that was merely accepted into the BLE operation queue is not "written" until the camera
 * acknowledged the GATT write (write-with-response). Counters are session-scoped (reset when the
 * link connects); the two timestamps survive disconnects so a disconnected camera can still show
 * "last written X ago".
 */
data class GpsWriteStats(
    /** Location packets handed to the BLE operation queue. */
    val queuedCount: Int = 0,
    /** GATT writes the camera acknowledged. */
    val writtenCount: Int = 0,
    /** GATT writes that failed, timed out or could not be started (cancellation on disconnect excluded). */
    val failedCount: Int = 0,
    val lastWriteAtMillis: Long? = null,
    val lastFailureAtMillis: Long? = null,
)

/**
 * Battery level obtained from the camera over PTP. It is only readable while a Wi-Fi/PTP session is
 * open, so it is a cached observation: show [percent] with its age ([freshness]).
 */
data class CameraBattery(
    val percent: Int,
    val obtainedAtMillis: Long,
    /** Camera the reading belongs to (the BLE address); the cache is keyed by it. */
    val cameraId: String,
) {
    init {
        require(percent in 0..100) { "Battery percent out of range" }
    }

    fun ageMillis(nowMillis: Long): Long = (nowMillis - obtainedAtMillis).coerceAtLeast(0)

    fun freshness(nowMillis: Long): BatteryFreshness = batteryFreshness(ageMillis(nowMillis))
}

/** Display policy for a cached battery value: <=30 min as is, up to 24 h greyed with its age, older unknown. */
enum class BatteryFreshness { Fresh, Stale, Expired }

fun batteryFreshness(ageMillis: Long): BatteryFreshness = when {
    ageMillis <= 30.minutes.inWholeMilliseconds -> BatteryFreshness.Fresh
    ageMillis <= 24.hours.inWholeMilliseconds -> BatteryFreshness.Stale
    else -> BatteryFreshness.Expired
}
