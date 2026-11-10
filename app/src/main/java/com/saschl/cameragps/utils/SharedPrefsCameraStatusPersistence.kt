package com.saschl.cameragps.utils

import android.content.Context
import androidx.core.content.edit
import com.sasch.cameragps.sharednew.bluetooth.status.CameraStatusPersistence

/**
 * Keeps only "last camera-acknowledged GPS write" per device, in its own small preferences file
 * (no Room migration, nothing to clean up when a device is removed beyond [forget]).
 * `apply()` is asynchronous; the hub already throttles saves to once a minute.
 */
class SharedPrefsCameraStatusPersistence(context: Context) : CameraStatusPersistence {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadLastWriteAt(address: String): Long? =
        prefs.getLong(key(address), NONE).takeIf { it != NONE }

    override fun saveLastWriteAt(address: String, epochMillis: Long) {
        prefs.edit { putLong(key(address), epochMillis) }
    }

    fun forget(address: String) {
        prefs.edit { remove(key(address)) }
    }

    private fun key(address: String) = "last_write_" + address.uppercase()

    private companion object {
        const val PREFS_NAME = "camera_live_status"
        const val NONE = -1L
    }
}
