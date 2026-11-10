package com.sasch.cameragps.sharednew.ui.device

import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSettingState
import com.sasch.cameragps.sharednew.bluetooth.status.SignalLevel
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone

/*
 * Pure presentation rules of the device page's metrics and the Ⓘ dialog rows. Colours are never
 * chosen here: a [StatusTone] resolves through the theme (`statusToneColor` / `statusIconColor`).
 */

/** Tone of the Bluetooth state (status dot of the card, value of the Ⓘ dialog row). */
fun connectionTone(headline: ConnectionHeadline): StatusTone = when (headline) {
    ConnectionHeadline.Connected -> StatusTone.Success
    ConnectionHeadline.Connecting, ConnectionHeadline.Disconnecting -> StatusTone.Active
    ConnectionHeadline.Disconnected -> StatusTone.Neutral
}

/** Signal icon colour by level: strong = green, fair = amber, weak = red, unknown = grey. */
fun signalTone(level: SignalLevel): StatusTone = when (level) {
    SignalLevel.Excellent, SignalLevel.Good -> StatusTone.Success
    SignalLevel.Fair -> StatusTone.Warning
    SignalLevel.Poor -> StatusTone.Error
    SignalLevel.Unknown -> StatusTone.Neutral
}

/** "Written" metric icon: the theme's primary (accent blue). */
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

/** Enabled = green with a ✓ (like "Location writing · Active"); everything else grey, no mark. */
fun settingTone(display: SettingDisplay): StatusTone =
    if (display == SettingDisplay.Enabled) StatusTone.Success else StatusTone.Neutral

/** Whether a row shows the green ✓ in front of its value. */
fun showsCheck(tone: StatusTone): Boolean = tone == StatusTone.Success

/**
 * Ⓘ dialog Bluetooth address: hidden by default, shown only after a tap, hidden again by the next
 * tap. The state is not saved, so every new opening of the dialog starts hidden.
 */
enum class AddressVisibility {
    Hidden,
    Shown;

    fun toggled(): AddressVisibility = if (this == Hidden) Shown else Hidden
}

/** What a hidden address shows: a fixed mask that says nothing about the address (not even its length). */
const val HIDDEN_ADDRESS_TEXT: String = "••••••"

/** The address as the Ⓘ row shows it: the full upper-case address only while [AddressVisibility.Shown]. */
fun addressText(address: String, visibility: AddressVisibility): String = when (visibility) {
    AddressVisibility.Shown -> address.uppercase()
    AddressVisibility.Hidden -> HIDDEN_ADDRESS_TEXT
}
