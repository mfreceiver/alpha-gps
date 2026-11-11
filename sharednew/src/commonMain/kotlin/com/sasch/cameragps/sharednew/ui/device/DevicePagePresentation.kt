package com.sasch.cameragps.sharednew.ui.device

import com.sasch.cameragps.sharednew.bluetooth.location.PhoneLocationFix
import com.sasch.cameragps.sharednew.bluetooth.status.BatteryFreshness
import com.sasch.cameragps.sharednew.bluetooth.status.CameraBattery
import com.sasch.cameragps.sharednew.bluetooth.status.CameraLiveStatus
import com.sasch.cameragps.sharednew.bluetooth.status.GpsWriteStats
import com.sasch.cameragps.sharednew.bluetooth.status.TimeSyncOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Read-only live status for the device page (signal, GPS write counters, time correction, cached
 * battery). Android passes the orchestrator's `liveStatus` hub; tests and hosts without it use [None].
 */
interface DeviceLiveStatusSource {
    fun observe(address: String): Flow<CameraLiveStatus?>

    /** Starts signal polling for [address] while the page is visible; call the result to release it. */
    fun acquireSignalMonitoring(address: String): () -> Unit

    /** Phone-level: accuracy and time of the phone's latest location fix (no coordinates). */
    fun observePhoneLocation(): Flow<PhoneLocationFix?> = flowOf(null)

    object None : DeviceLiveStatusSource {
        override fun observe(address: String): Flow<CameraLiveStatus?> = flowOf(null)
        override fun acquireSignalMonitoring(address: String): () -> Unit = {}
    }
}

/**
 * What the user can do about the Bluetooth link right now (see [connectionAction]). The Ⓘ dialog's
 * Bluetooth row offers all of them; the device page's connection card shows a state instead of
 * [Disconnect] (see [connectionCardTrailing]).
 */
enum class ConnectionAction {
    /** Ⓘ dialog: small danger-outlined "Disconnect" capsule with a confirmation dialog. */
    Disconnect,

    /** Plain text "Cancel" while the link is being set up (= stop trying, like a manual disconnect). */
    CancelConnecting,

    /** Tonal "Connect" capsule. */
    Connect,

    /** Disconnect in flight: nothing to tap. */
    None,
}

fun connectionAction(status: DeviceConnectionStatus, pending: PendingConnection): ConnectionAction = when {
    pending == PendingConnection.Disconnecting -> ConnectionAction.None
    status == DeviceConnectionStatus.Connected -> ConnectionAction.Disconnect
    status == DeviceConnectionStatus.Connecting || pending == PendingConnection.Connecting ->
        ConnectionAction.CancelConnecting
    else -> ConnectionAction.Connect
}

/** Fixed trailing slot of the device page's connection card. */
enum class ConnectionCardTrailing {
    /** Connected: a non-interactive "Bluetooth connected" status icon (disconnecting lives in Ⓘ). */
    ConnectedIndicator,

    /** "Cancel" while the link is being set up (= stop trying, like a manual disconnect). */
    CancelConnecting,

    /** Tonal "Connect" capsule. */
    Connect,

    /** Disconnect in flight: a spinner, nothing to tap. */
    Busy,
}

/**
 * The card keeps the two actions the page needs (connect / cancel) but reports a connected link as
 * a state: the manual disconnect is in the Ⓘ dialog.
 */
fun connectionCardTrailing(status: DeviceConnectionStatus, pending: PendingConnection): ConnectionCardTrailing =
    when (connectionAction(status, pending)) {
        ConnectionAction.Disconnect -> ConnectionCardTrailing.ConnectedIndicator
        ConnectionAction.CancelConnecting -> ConnectionCardTrailing.CancelConnecting
        ConnectionAction.Connect -> ConnectionCardTrailing.Connect
        ConnectionAction.None -> ConnectionCardTrailing.Busy
    }

/** Headline of the connection status line. */
enum class ConnectionHeadline { Connected, Connecting, Disconnecting, Disconnected }

fun connectionHeadline(status: DeviceConnectionStatus, pending: PendingConnection): ConnectionHeadline = when {
    pending == PendingConnection.Disconnecting -> ConnectionHeadline.Disconnecting
    status == DeviceConnectionStatus.Connected -> ConnectionHeadline.Connected
    status == DeviceConnectionStatus.Connecting || pending == PendingConnection.Connecting -> ConnectionHeadline.Connecting
    else -> ConnectionHeadline.Disconnected
}

/** What a tap on "Photo transfer" does for the current Bluetooth state. */
enum class PhotoTransferAction {
    /** Bluetooth is ready: open the Wi-Fi flow now. */
    Open,

    /** A link is already being set up: wait for it, then open. */
    Wait,

    /** No link: connect Bluetooth first (same as the Connect button), then open. */
    ConnectFirst,

    /** A manual disconnect is in flight. */
    Ignore,
}

fun photoTransferAction(status: DeviceConnectionStatus, pending: PendingConnection): PhotoTransferAction = when {
    pending == PendingConnection.Disconnecting -> PhotoTransferAction.Ignore
    status == DeviceConnectionStatus.Connected -> PhotoTransferAction.Open
    status == DeviceConnectionStatus.Connecting || pending == PendingConnection.Connecting -> PhotoTransferAction.Wait
    else -> PhotoTransferAction.ConnectFirst
}

/**
 * Photo-transfer entry state of the device page. [Ready] is a one-shot: the page opens the Wi-Fi
 * flow and acknowledges it with `photoTransferOpened`. [Failed] shows a short red line until the
 * next tap (a tap is the retry).
 */
enum class PhotoTransferEntry { Idle, ConnectingBluetooth, Ready, Failed }

/** Battery as the device page shows it. The page never opens a Wi-Fi session just to read it. */
sealed interface BatteryDisplay {
    /** No cached reading, or one older than the cache keeps (24 h). */
    data object Unknown : BatteryDisplay

    /** [ageMinutes] is only set when the reading is older than 30 minutes. */
    data class Value(val percent: Int, val ageMinutes: Long?) : BatteryDisplay
}

fun batteryDisplay(battery: CameraBattery?, nowMillis: Long): BatteryDisplay {
    if (battery == null) return BatteryDisplay.Unknown
    return when (battery.freshness(nowMillis)) {
        BatteryFreshness.Fresh -> BatteryDisplay.Value(battery.percent, ageMinutes = null)
        BatteryFreshness.Stale -> BatteryDisplay.Value(
            battery.percent,
            ageMinutes = battery.ageMillis(nowMillis) / 1.minutes.inWholeMilliseconds,
        )
        BatteryFreshness.Expired -> BatteryDisplay.Unknown
    }
}

/** Coarse quality of the phone's own location fix, from its horizontal accuracy radius. */
enum class LocationSignalLevel { Strong, Medium, Weak, Unknown }

/** A fix older than this no longer describes the phone's current location quality. */
const val LOCATION_FIX_MAX_AGE_MILLIS: Long = 2 * 60_000L

/** Accuracy radius (metres) up to which a fix reads as [LocationSignalLevel.Strong]. */
const val LOCATION_STRONG_MAX_METERS: Double = 10.0

/** Accuracy radius (metres) up to which a fix reads as [LocationSignalLevel.Medium]. */
const val LOCATION_MEDIUM_MAX_METERS: Double = 30.0

/**
 * ≤ 10 m strong, ≤ 30 m medium, larger weak. No fix, an accuracy that is not a positive finite
 * number (Android reports 0 when the provider has no estimate), or a fix older than
 * [LOCATION_FIX_MAX_AGE_MILLIS] is [LocationSignalLevel.Unknown]. A fix time slightly ahead of the
 * phone clock counts as fresh.
 */
fun locationSignalLevel(accuracyMeters: Double?, fixTimeMillis: Long?, nowMillis: Long): LocationSignalLevel {
    if (accuracyMeters == null || fixTimeMillis == null) return LocationSignalLevel.Unknown
    if (accuracyMeters.isNaN() || accuracyMeters.isInfinite() || accuracyMeters <= 0.0) {
        return LocationSignalLevel.Unknown
    }
    if (nowMillis - fixTimeMillis > LOCATION_FIX_MAX_AGE_MILLIS) return LocationSignalLevel.Unknown
    return when {
        accuracyMeters <= LOCATION_STRONG_MAX_METERS -> LocationSignalLevel.Strong
        accuracyMeters <= LOCATION_MEDIUM_MAX_METERS -> LocationSignalLevel.Medium
        else -> LocationSignalLevel.Weak
    }
}

/**
 * The one line under the status card's headline. A healthy link shows nothing; only a problem the
 * user may want to act on is reported (most important first).
 */
enum class StatusCardAlert {
    None,

    /** The camera reports its location linking as off: nothing is written until it is turned on. */
    LocationDisabledByCamera,

    /** The latest location write of this link failed and no write has succeeded since. */
    LocationWriteFailing,

    /** The time correction could not be written. */
    TimeSyncFailed,

    /** The camera answered the time correction with a rejection. */
    TimeSyncRejected,
}

/**
 * Only a connected link can have an alert (connecting / disconnected headlines say enough).
 * "Unconfirmed" and "unsupported" time sync are not errors and stay silent.
 */
fun statusCardAlert(
    headline: ConnectionHeadline,
    locationDisabledByCamera: Boolean,
    gps: GpsWriteStats?,
    timeSync: TimeSyncOutcome?,
): StatusCardAlert {
    if (headline != ConnectionHeadline.Connected) return StatusCardAlert.None
    if (locationDisabledByCamera) return StatusCardAlert.LocationDisabledByCamera
    if (gps != null && gps.failedCount > 0) {
        val failedAt = gps.lastFailureAtMillis
        val writtenAt = gps.lastWriteAtMillis
        if (failedAt != null && (writtenAt == null || failedAt > writtenAt)) {
            return StatusCardAlert.LocationWriteFailing
        }
    }
    return when (timeSync) {
        TimeSyncOutcome.Failed -> StatusCardAlert.TimeSyncFailed
        TimeSyncOutcome.Rejected -> StatusCardAlert.TimeSyncRejected
        else -> StatusCardAlert.None
    }
}

/** Coarse "x ago" of a past timestamp (last GPS write). */
sealed interface RelativeAge {
    data object Never : RelativeAge
    data object JustNow : RelativeAge
    data class Minutes(val value: Long) : RelativeAge
    data class Hours(val value: Long) : RelativeAge
    data class Days(val value: Long) : RelativeAge
}

fun relativeAge(atMillis: Long?, nowMillis: Long): RelativeAge {
    if (atMillis == null) return RelativeAge.Never
    val age = (nowMillis - atMillis).coerceAtLeast(0)
    return when {
        age < 1.minutes.inWholeMilliseconds -> RelativeAge.JustNow
        age < 1.hours.inWholeMilliseconds -> RelativeAge.Minutes(age / 1.minutes.inWholeMilliseconds)
        age < 1.days.inWholeMilliseconds -> RelativeAge.Hours(age / 1.hours.inWholeMilliseconds)
        else -> RelativeAge.Days(age / 1.days.inWholeMilliseconds)
    }
}
