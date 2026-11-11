package com.sasch.cameragps.sharednew.ui.device

import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSettingState
import com.sasch.cameragps.sharednew.bluetooth.status.SignalLevel
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone

/*
 * Pure presentation rules of the device page's metrics and the Ⓘ dialog rows. Colours are never
 * chosen here: a [StatusTone] resolves through the theme (`statusToneColor` / `statusIconColor`).
 */

/** Tone of the Bluetooth state (value of the Ⓘ dialog row). */
fun connectionTone(headline: ConnectionHeadline): StatusTone = when (headline) {
    ConnectionHeadline.Connected -> StatusTone.Success
    ConnectionHeadline.Connecting, ConnectionHeadline.Disconnecting -> StatusTone.Active
    ConnectionHeadline.Disconnected -> StatusTone.Neutral
}

/** Signal colour by level: strong = green, fair = amber, weak = red, unknown = grey. */
fun signalTone(level: SignalLevel): StatusTone = when (level) {
    SignalLevel.Excellent, SignalLevel.Good -> StatusTone.Success
    SignalLevel.Fair -> StatusTone.Warning
    SignalLevel.Poor -> StatusTone.Error
    SignalLevel.Unknown -> StatusTone.Neutral
}

/**
 * Share of the signal ring that is drawn for a level (Excellent 100 %, Good 75 %, Fair 50 %,
 * Poor 25 %); Unknown draws only the track.
 */
fun signalArcFraction(level: SignalLevel): Float = when (level) {
    SignalLevel.Excellent -> 1f
    SignalLevel.Good -> 0.75f
    SignalLevel.Fair -> 0.5f
    SignalLevel.Poor -> 0.25f
    SignalLevel.Unknown -> 0f
}

/** Phone location-fix colour, same scheme as [signalTone]: strong green, medium amber, weak red. */
fun locationSignalTone(level: LocationSignalLevel): StatusTone = when (level) {
    LocationSignalLevel.Strong -> StatusTone.Success
    LocationSignalLevel.Medium -> StatusTone.Warning
    LocationSignalLevel.Weak -> StatusTone.Error
    LocationSignalLevel.Unknown -> StatusTone.Neutral
}

/** "Location writes" metric icon: the theme's primary (accent blue). */
val WrittenMetricTone: StatusTone = StatusTone.Active

/** "Last write" metric icon: the secondary teal accent. */
val LastWriteMetricTone: StatusTone = StatusTone.Accent

/**
 * What the Ⓘ dialog says about a camera auto setting (auto time / auto area). Only a connected
 * camera's own report counts: a disconnected (or unknown) camera reads "unknown", even if an old
 * session still holds a value.
 */
fun infoSettingDisplay(session: CameraSession?, state: CameraSettingState?): SettingDisplay =
    if (deviceConnectionStatus(session) != DeviceConnectionStatus.Connected) {
        SettingDisplay.Unknown
    } else {
        settingDisplay(session, state)
    }

/**
 * The Ⓘ dialog's single "Auto time & area" row. The camera reports the two settings separately
 * (they can differ), so the row is only "enabled" when both are, and otherwise names the one that
 * is not.
 */
enum class AutoCorrectionSummary {
    /** Both settings on. */
    Enabled,

    /** Auto time correction is off or unsupported; area adjustment is on (or not yet known). */
    TimeNotEnabled,

    /** Auto area adjustment is off or unsupported; time correction is on (or not yet known). */
    AreaNotEnabled,

    /** Both are off (or one off, the other unsupported). */
    NeitherEnabled,

    /** The camera supports neither. */
    Unsupported,

    /** Not connected, or the camera has not reported yet. */
    Unknown,
}

fun autoCorrectionSummary(session: CameraSession?): AutoCorrectionSummary {
    val time = infoSettingDisplay(session, session?.autoTimeCorrection)
    val area = infoSettingDisplay(session, session?.autoAreaAdjustment)
    fun SettingDisplay.notEnabled() = this == SettingDisplay.Off || this == SettingDisplay.Unsupported
    return when {
        time == SettingDisplay.Enabled && area == SettingDisplay.Enabled -> AutoCorrectionSummary.Enabled
        time == SettingDisplay.Unsupported && area == SettingDisplay.Unsupported -> AutoCorrectionSummary.Unsupported
        time.notEnabled() && area.notEnabled() -> AutoCorrectionSummary.NeitherEnabled
        time.notEnabled() -> AutoCorrectionSummary.TimeNotEnabled
        area.notEnabled() -> AutoCorrectionSummary.AreaNotEnabled
        else -> AutoCorrectionSummary.Unknown
    }
}

/**
 * Text colour of the auto row: on = green; something the app keeps on but the camera reports off
 * = amber; unsupported / unknown = grey.
 */
fun autoCorrectionTone(summary: AutoCorrectionSummary): StatusTone = when (summary) {
    AutoCorrectionSummary.Enabled -> StatusTone.Success
    AutoCorrectionSummary.TimeNotEnabled,
    AutoCorrectionSummary.AreaNotEnabled,
    AutoCorrectionSummary.NeitherEnabled -> StatusTone.Warning
    AutoCorrectionSummary.Unsupported, AutoCorrectionSummary.Unknown -> StatusTone.Neutral
}
