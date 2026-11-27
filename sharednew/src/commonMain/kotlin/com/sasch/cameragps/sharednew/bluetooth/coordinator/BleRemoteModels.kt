package com.sasch.cameragps.sharednew.bluetooth.coordinator

import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.status.CameraLiveStatus

/** Read-only single-shot BLE shutter state exposed to the R1 UI. */
enum class BleShutterPhase {
    Idle,
    Preparing,
    Ready,
    Pressing,
    Releasing,
    Uncertain,
    Failed,
}

enum class BleShutterFailureReason {
    NotConnected,
    RemoteControlUnavailable,
    WifiOccupied,
    CommandRejected,
    Disconnected,
    Timeout,
}

data class BleShutterState(
    val phase: BleShutterPhase = BleShutterPhase.Idle,
    val connectionGeneration: Long = 0L,
    val operationId: Long = 0L,
    val exposureStarted: Boolean = false,
    val releaseUncertain: Boolean = false,
    val failureReason: BleShutterFailureReason? = null,
) {
    val canTrigger: Boolean get() = phase == BleShutterPhase.Ready
}

/**
 * Compatibility between BLE remote control and phone-to-camera GPS location linking.
 * Only models with documented mutual exclusion are marked [KnownMutuallyExclusive];
 * user-renamed cameras or unverified models always report [Unverified].
 */
enum class BleLocationCompatibility {
    KnownMutuallyExclusive,
    Unverified,
}

private val KNOWN_MUTUALLY_EXCLUSIVE_MODELS = listOf(
    "ILCE-6400",
    "A6400",
    "Α6400", // Greek Alpha uppercase
)

fun assessBleLocationCompatibility(
    deviceName: String?,
    isCustomName: Boolean = false,
): BleLocationCompatibility {
    if (isCustomName || deviceName.isNullOrBlank()) return BleLocationCompatibility.Unverified
    val normalized = deviceName.trim().uppercase()
    return if (KNOWN_MUTUALLY_EXCLUSIVE_MODELS.any { normalized == it || normalized.startsWith("$it ") }) {
        BleLocationCompatibility.KnownMutuallyExclusive
    } else {
        BleLocationCompatibility.Unverified
    }
}

/**
 * Snapshot of GPS write state captured around BLE remote control usage, used to detect
 * whether location linking actually resumed after leaving BLE remote control.
 */
data class GpsRecoveryBaseline(
    val hadPriorWriteAtEntry: Boolean,
    val writtenCountAtExit: Int,
    val lastWriteAtMillisAtExit: Long?,
    val active: Boolean = true,
) {
    internal var diagnostics: com.sasch.cameragps.sharednew.database.logging.RemoteDiagnostics? = null
}

enum class GpsRecoveryStatus {
    /** Not observing recovery (inactive or disconnected). */
    Inactive,
    /** Camera explicitly reported location link disabled (`locationDisabledByCamera == true`). */
    DisabledByCamera,
    /** New successful GPS write ack observed after exit, where camera had prior writes. */
    Restored,
    /** New successful GPS write ack observed after exit, where camera had no prior writes. */
    FirstWriteReceived,
    /** BLE is Transmitting and not disabled by camera, waiting for a phone location fix. */
    WaitingForLocationFix,
    /** BLE is Transmitting with a fix opportunity, but no new write ack confirmed yet. */
    Unconfirmed,
}

fun captureGpsRecoveryBaseline(
    hadPriorWriteAtEntry: Boolean,
    liveStatus: CameraLiveStatus?,
): GpsRecoveryBaseline = GpsRecoveryBaseline(
    hadPriorWriteAtEntry = hadPriorWriteAtEntry,
    writtenCountAtExit = liveStatus?.gps?.writtenCount ?: 0,
    lastWriteAtMillisAtExit = liveStatus?.gps?.lastWriteAtMillis,
    active = true,
)

/**
 * Evaluates post-exit GPS recovery strictly from new camera-acknowledged GATT writes
 * (`writtenCount` / `lastWriteAtMillis` advancing past [baseline]) and `locationDisabledByCamera`.
 * BLE `Transmitting` alone or cached timestamps never report [Restored] or [FirstWriteReceived].
 */
fun evaluateGpsRecoveryStatus(
    baseline: GpsRecoveryBaseline?,
    session: CameraSession?,
    liveStatus: CameraLiveStatus?,
    hasRecentLocationFix: Boolean,
): GpsRecoveryStatus {
    if (baseline == null || !baseline.active) return GpsRecoveryStatus.Inactive
    if (session == null ||
        session.phase == BleSessionPhase.Disconnected ||
        session.phase == BleSessionPhase.Error
    ) {
        return GpsRecoveryStatus.Inactive
    }
    if (session.locationDisabledByCamera) return GpsRecoveryStatus.DisabledByCamera
    if (session.phase != BleSessionPhase.Transmitting) return GpsRecoveryStatus.Unconfirmed

    val writtenCount = liveStatus?.gps?.writtenCount ?: 0
    val lastWriteAt = liveStatus?.gps?.lastWriteAtMillis
    val newWriteObserved = writtenCount > baseline.writtenCountAtExit ||
        (lastWriteAt != null && lastWriteAt != baseline.lastWriteAtMillisAtExit)
    if (newWriteObserved) {
        return if (baseline.hadPriorWriteAtEntry) {
            GpsRecoveryStatus.Restored
        } else {
            GpsRecoveryStatus.FirstWriteReceived
        }
    }

    return if (!hasRecentLocationFix) {
        GpsRecoveryStatus.WaitingForLocationFix
    } else {
        GpsRecoveryStatus.Unconfirmed
    }
}
