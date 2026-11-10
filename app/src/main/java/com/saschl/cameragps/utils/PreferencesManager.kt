package com.saschl.cameragps.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.saschl.cameragps.service.TransmissionSoundEvent
import com.saschl.cameragps.service.TransmissionSoundMode
import com.sasch.cameragps.sharednew.ui.remote.WifiGridOptions

object PreferencesManager {
    private const val PREFS_NAME = "camera_gps_prefs"
    private const val KEY_FIRST_LAUNCH = "is_first_launch"
    private const val KEY_WIFI_REMOTE_NOTICE_ACKNOWLEDGED = "wifi_remote_notice_acknowledged"
    private const val KEY_APP_ENABLED = "app_enabled"
    private const val KEY_BATTERY_OPTIMIZATION_DIALOG_DISMISSED = "battery_optimization_dialog_dismissed"
    private const val KEY_LOG_LEVEL = "log_level"
    private const val KEY_IGNORE_PERMISSIONS = "ignore_permissions"
    private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
    private const val KEY_WIFI_DEFAULT_DOWNLOAD_FORMAT = "wifi_default_download_format"
    private const val KEY_TRANSMISSION_EVENT_SOUNDS_ENABLED = "transmission_event_sounds_enabled"
    private const val KEY_SOUND_LOCATION_ACQUIRED = "sound_location_acquired"
    private const val KEY_SOUND_LOCATION_INVALID = "sound_location_invalid"
    private const val KEY_SOUND_CAMERA_CONNECTED = "sound_camera_connected"
    private const val KEY_SOUND_CAMERA_DISCONNECTED = "sound_camera_disconnected"
    private const val KEY_SOUND_MODE_LOCATION_ACQUIRED = "sound_mode_location_acquired"
    private const val KEY_SOUND_MODE_LOCATION_INVALID = "sound_mode_location_invalid"
    private const val KEY_SOUND_MODE_CAMERA_CONNECTED = "sound_mode_camera_connected"
    private const val KEY_SOUND_MODE_CAMERA_DISCONNECTED = "sound_mode_camera_disconnected"
    private const val KEY_SOUND_URI_LOCATION_ACQUIRED = "sound_uri_location_acquired"
    private const val KEY_SOUND_URI_LOCATION_INVALID = "sound_uri_location_invalid"
    private const val KEY_SOUND_URI_CAMERA_CONNECTED = "sound_uri_camera_connected"
    private const val KEY_SOUND_URI_CAMERA_DISCONNECTED = "sound_uri_camera_disconnected"
    private const val SOUND_MODE_DEFAULT = "default"
    private const val SOUND_MODE_SILENT = "silent"
    private const val SOUND_MODE_CUSTOM = "custom"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getLastSeenReleaseVersion(context: Context): String? =
        getPreferences(context).getString("last_seen_release_version", null)

    fun isWifiRemoteNoticeAcknowledged(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_WIFI_REMOTE_NOTICE_ACKNOWLEDGED, false)

    fun acknowledgeWifiRemoteNotice(context: Context) {
        getPreferences(context).edit { putBoolean(KEY_WIFI_REMOTE_NOTICE_ACKNOWLEDGED, true) }
    }

    fun setLastSeenReleaseVersion(context: Context, version: String) {
        getPreferences(context).edit { putString("last_seen_release_version", version) }
    }

    fun isFirstLaunch(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchCompleted(context: Context) {
        getPreferences(context).edit {
            putBoolean(KEY_FIRST_LAUNCH, false)
        }
    }
    fun showFirstLaunch(context: Context) {
        getPreferences(context).edit {
            putBoolean(KEY_FIRST_LAUNCH, true)
        }
    }

    fun isAppEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_APP_ENABLED, true)
    }

    fun setAppEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit {
            putBoolean(KEY_APP_ENABLED, enabled)
        }
    }


    fun logLevel(context: Context): Int {
        return getPreferences(context).getInt(KEY_LOG_LEVEL, Log.INFO)
    }

    fun setLogLevel(context: Context, logLevel: Int) {
        getPreferences(context).edit {
            putInt(KEY_LOG_LEVEL, logLevel)
        }
    }

    fun isHapticsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_HAPTICS_ENABLED, true)
    }

    /** Default Wi-Fi photo download format: "original" (JPG+RAW, default) or "jpeg" (JPG only). */
    fun isWifiDefaultDownloadFormatJpeg(context: Context): Boolean {
        return getPreferences(context).getString(KEY_WIFI_DEFAULT_DOWNLOAD_FORMAT, "original") == "jpeg"
    }

    fun setWifiDefaultDownloadFormatJpeg(context: Context, jpegOnly: Boolean) {
        getPreferences(context).edit {
            putString(KEY_WIFI_DEFAULT_DOWNLOAD_FORMAT, if (jpegOnly) "jpeg" else "original")
        }
    }

    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit {
            putBoolean(KEY_HAPTICS_ENABLED, enabled)
        }
    }

    fun setAutoStartAfterBootEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit {
            putBoolean("auto_start_after_boot", enabled)
        }
    }

    fun getAutoStartAfterBootEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean("auto_start_after_boot", false)
    }

    fun isPermissionsIgnored(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IGNORE_PERMISSIONS, false)
    }

    fun setPermissionsIgnored(context: Context, ignored: Boolean) {
        getPreferences(context).edit {
            putBoolean(KEY_IGNORE_PERMISSIONS, ignored)
        }
    }

    fun isTransmissionEventSoundsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_TRANSMISSION_EVENT_SOUNDS_ENABLED, false)
    }

    fun setTransmissionEventSoundsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit {
            putBoolean(KEY_TRANSMISSION_EVENT_SOUNDS_ENABLED, enabled)
        }
    }

    fun isTransmissionEventEnabled(context: Context, event: TransmissionSoundEvent): Boolean {
        val key = when (event) {
            TransmissionSoundEvent.LOCATION_ACQUIRED -> KEY_SOUND_LOCATION_ACQUIRED
            TransmissionSoundEvent.LOCATION_INVALID -> KEY_SOUND_LOCATION_INVALID
            TransmissionSoundEvent.CAMERA_CONNECTED -> KEY_SOUND_CAMERA_CONNECTED
            TransmissionSoundEvent.CAMERA_DISCONNECTED -> KEY_SOUND_CAMERA_DISCONNECTED
        }
        return getPreferences(context).getBoolean(key, true)
    }

    fun setTransmissionEventEnabled(
        context: Context,
        event: TransmissionSoundEvent,
        enabled: Boolean
    ) {
        val key = when (event) {
            TransmissionSoundEvent.LOCATION_ACQUIRED -> KEY_SOUND_LOCATION_ACQUIRED
            TransmissionSoundEvent.LOCATION_INVALID -> KEY_SOUND_LOCATION_INVALID
            TransmissionSoundEvent.CAMERA_CONNECTED -> KEY_SOUND_CAMERA_CONNECTED
            TransmissionSoundEvent.CAMERA_DISCONNECTED -> KEY_SOUND_CAMERA_DISCONNECTED
        }
        getPreferences(context).edit {
            putBoolean(key, enabled)
        }
    }

    fun getTransmissionEventSoundMode(
        context: Context,
        event: TransmissionSoundEvent
    ): TransmissionSoundMode {
        val modeKey = soundModeKey(event)
        val stored = getPreferences(context).getString(modeKey, SOUND_MODE_DEFAULT)
        return when (stored) {
            SOUND_MODE_SILENT -> TransmissionSoundMode.SILENT
            SOUND_MODE_CUSTOM -> TransmissionSoundMode.CUSTOM
            else -> TransmissionSoundMode.DEFAULT
        }
    }

    fun setTransmissionEventSoundMode(
        context: Context,
        event: TransmissionSoundEvent,
        mode: TransmissionSoundMode
    ) {
        val modeValue = when (mode) {
            TransmissionSoundMode.DEFAULT -> SOUND_MODE_DEFAULT
            TransmissionSoundMode.SILENT -> SOUND_MODE_SILENT
            TransmissionSoundMode.CUSTOM -> SOUND_MODE_CUSTOM
        }
        getPreferences(context).edit {
            putString(soundModeKey(event), modeValue)
        }
    }

    fun getTransmissionEventSoundUri(context: Context, event: TransmissionSoundEvent): String? {
        return getPreferences(context).getString(soundUriKey(event), null)
    }

    fun setTransmissionEventSoundUri(
        context: Context,
        event: TransmissionSoundEvent,
        uri: String?
    ) {
        getPreferences(context).edit {
            if (uri == null) {
                remove(soundUriKey(event))
            } else {
                putString(soundUriKey(event), uri)
            }
        }
    }

    private fun soundModeKey(event: TransmissionSoundEvent): String = when (event) {
        TransmissionSoundEvent.LOCATION_ACQUIRED -> KEY_SOUND_MODE_LOCATION_ACQUIRED
        TransmissionSoundEvent.LOCATION_INVALID -> KEY_SOUND_MODE_LOCATION_INVALID
        TransmissionSoundEvent.CAMERA_CONNECTED -> KEY_SOUND_MODE_CAMERA_CONNECTED
        TransmissionSoundEvent.CAMERA_DISCONNECTED -> KEY_SOUND_MODE_CAMERA_DISCONNECTED
    }

    private fun soundUriKey(event: TransmissionSoundEvent): String = when (event) {
        TransmissionSoundEvent.LOCATION_ACQUIRED -> KEY_SOUND_URI_LOCATION_ACQUIRED
        TransmissionSoundEvent.LOCATION_INVALID -> KEY_SOUND_URI_LOCATION_INVALID
        TransmissionSoundEvent.CAMERA_CONNECTED -> KEY_SOUND_URI_CAMERA_CONNECTED
        TransmissionSoundEvent.CAMERA_DISCONNECTED -> KEY_SOUND_URI_CAMERA_DISCONNECTED
    }

    // --- Photo viewer info toggles (read by the viewer; default off) ---------------------------

    private const val KEY_VIEWER_SHOW_HISTOGRAM = "viewer_show_histogram"
    private const val KEY_VIEWER_SHOW_EXIF_PARAMS = "viewer_show_exif_params"
    private const val KEY_VIEWER_SHOW_LENS = "viewer_show_lens"

    /** Show the histogram in the photo viewer's info area. */
    fun isViewerShowHistogram(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_VIEWER_SHOW_HISTOGRAM, false)

    fun setViewerShowHistogram(context: Context, enabled: Boolean) {
        getPreferences(context).edit { putBoolean(KEY_VIEWER_SHOW_HISTOGRAM, enabled) }
    }

    /** Show aperture / shutter / ISO / focal length / exposure compensation in the viewer. */
    fun isViewerShowExifParams(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_VIEWER_SHOW_EXIF_PARAMS, false)

    fun setViewerShowExifParams(context: Context, enabled: Boolean) {
        getPreferences(context).edit { putBoolean(KEY_VIEWER_SHOW_EXIF_PARAMS, enabled) }
    }

    /** Show the lens information in the viewer. */
    fun isViewerShowLens(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_VIEWER_SHOW_LENS, false)

    fun setViewerShowLens(context: Context, enabled: Boolean) {
        getPreferences(context).edit { putBoolean(KEY_VIEWER_SHOW_LENS, enabled) }
    }

    // ---- Wi-Fi photo grid view options (wave 2 / Wi-Fi screen; keys prefixed mw_) ----

    /** Wi-Fi day-grid view options; default small tiles with every tag off. */
    fun getWifiGridOptions(context: Context): WifiGridOptions =
        wifiGridOptionsFrom(getPreferences(context).all)

    fun setWifiGridOptions(context: Context, options: WifiGridOptions) {
        getPreferences(context).edit {
            putString(KEY_MW_WIFI_GRID_SIZE, options.size)
            putBoolean(KEY_MW_WIFI_GRID_SHOW_FORMAT, options.showFormat)
            putBoolean(KEY_MW_WIFI_GRID_SHOW_SIZE, options.showSize)
        }
    }

    /**
     * Grid options from the stored preference map. Only the size / format / size-tag keys are read;
     * keys of removed features (the 0.2.0 protection switch `mw_wifi_grid_show_protection`, the
     * viewer histogram / parameter / lens switches) stay in old installs untouched and are ignored.
     * A value of the wrong type falls back to the default instead of throwing.
     */
    internal fun wifiGridOptionsFrom(stored: Map<String, *>): WifiGridOptions {
        val defaults = WifiGridOptions()
        return WifiGridOptions(
            size = stored[KEY_MW_WIFI_GRID_SIZE] as? String ?: defaults.size,
            showFormat = stored[KEY_MW_WIFI_GRID_SHOW_FORMAT] as? Boolean ?: defaults.showFormat,
            showSize = stored[KEY_MW_WIFI_GRID_SHOW_SIZE] as? Boolean ?: defaults.showSize,
        )
    }

    internal const val KEY_MW_WIFI_GRID_SIZE = "mw_wifi_grid_size"
    internal const val KEY_MW_WIFI_GRID_SHOW_FORMAT = "mw_wifi_grid_show_format"
    internal const val KEY_MW_WIFI_GRID_SHOW_SIZE = "mw_wifi_grid_show_size"
}
