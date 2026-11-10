package com.saschl.cameragps.utils

import com.sasch.cameragps.sharednew.ui.remote.WifiGridOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class WifiGridPreferencesTest {
    @Test
    fun emptyPreferencesGiveTheDefaults() {
        assertEquals(WifiGridOptions(), PreferencesManager.wifiGridOptionsFrom(emptyMap<String, Any>()))
    }

    @Test
    fun storedOptionsAreRead() {
        val stored = mapOf(
            PreferencesManager.KEY_MW_WIFI_GRID_SIZE to "Medium",
            PreferencesManager.KEY_MW_WIFI_GRID_SHOW_FORMAT to true,
            PreferencesManager.KEY_MW_WIFI_GRID_SHOW_SIZE to true,
        )
        assertEquals(
            WifiGridOptions(size = "Medium", showFormat = true, showSize = true),
            PreferencesManager.wifiGridOptionsFrom(stored),
        )
    }

    @Test
    fun keysOfRemovedFeaturesAreIgnored() {
        // An install upgraded from 0.2.0 still carries the protection switch and the three viewer
        // info switches; they are neither migrated nor allowed to change (or break) anything.
        val legacy = mapOf(
            PreferencesManager.KEY_MW_WIFI_GRID_SIZE to "Large",
            PreferencesManager.KEY_MW_WIFI_GRID_SHOW_FORMAT to false,
            "mw_wifi_grid_show_protection" to true,
            "viewer_show_histogram" to true,
            "viewer_show_exif_params" to true,
            "viewer_show_lens" to true,
        )
        assertEquals(WifiGridOptions(size = "Large"), PreferencesManager.wifiGridOptionsFrom(legacy))
    }

    @Test
    fun valuesOfTheWrongTypeFallBackInsteadOfThrowing() {
        val broken = mapOf(
            PreferencesManager.KEY_MW_WIFI_GRID_SIZE to 3,
            PreferencesManager.KEY_MW_WIFI_GRID_SHOW_FORMAT to "yes",
            PreferencesManager.KEY_MW_WIFI_GRID_SHOW_SIZE to null,
        )
        assertEquals(WifiGridOptions(), PreferencesManager.wifiGridOptionsFrom(broken))
    }
}
