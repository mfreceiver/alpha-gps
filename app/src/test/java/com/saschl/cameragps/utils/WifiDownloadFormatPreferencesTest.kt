package com.saschl.cameragps.utils

import android.content.SharedPreferences
import com.sasch.cameragps.sharednew.remote.wifi.WifiDownloadFormatSetting
import org.junit.Assert.assertEquals
import org.junit.Test

/** Reading and writing the download format under the existing key, including values of older versions. */
class WifiDownloadFormatPreferencesTest {
    private val key = PreferencesManager.KEY_WIFI_DEFAULT_DOWNLOAD_FORMAT

    @Test
    fun theKeyIsUnchanged() {
        assertEquals("wifi_default_download_format", key)
    }

    @Test
    fun missingValueIsTheNewDefaultAskEachTime() {
        assertEquals(WifiDownloadFormatSetting.Ask, PreferencesManager.wifiDownloadFormatFrom(MemoryPreferences()))
    }

    @Test
    fun valuesOfOlderVersionsKeepTheirMeaning() {
        // 0.3.0 and earlier wrote "original" (every file = JPG + RAW) or "jpeg" (JPG only).
        assertEquals(WifiDownloadFormatSetting.JpgRaw, PreferencesManager.wifiDownloadFormatFrom(MemoryPreferences(key to "original")))
        assertEquals(WifiDownloadFormatSetting.Jpg, PreferencesManager.wifiDownloadFormatFrom(MemoryPreferences(key to "jpeg")))
    }

    @Test
    fun unknownOrWronglyTypedValuesReadAsTheDefault() {
        for (value in listOf<Any>("png", "", "ASK", true, 3)) {
            assertEquals(
                "stored $value",
                WifiDownloadFormatSetting.Ask,
                PreferencesManager.wifiDownloadFormatFrom(MemoryPreferences(key to value)),
            )
        }
    }

    @Test
    fun everyFormatRoundTrips() {
        val prefs = MemoryPreferences()
        for (format in WifiDownloadFormatSetting.entries) {
            PreferencesManager.writeWifiDownloadFormat(prefs, format)
            assertEquals(format, PreferencesManager.wifiDownloadFormatFrom(prefs))
        }
        assertEquals(
            listOf("ask", "jpeg", "raw", "original"),
            WifiDownloadFormatSetting.entries.map { format ->
                PreferencesManager.writeWifiDownloadFormat(prefs, format)
                prefs.all[key]
            },
        )
    }
}

/** Minimal in-memory [SharedPreferences] (the app's unit tests run without Robolectric). */
private class MemoryPreferences(vararg initial: Pair<String, Any>) : SharedPreferences {
    private val values = mutableMapOf<String, Any?>(*initial)

    override fun getAll(): Map<String, *> = values.toMap()
    override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) =
        @Suppress("UNCHECKED_CAST") (values[key] as? MutableSet<String> ?: defValues)
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { pending[key] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) values.clear()
            pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
        }
    }
}
