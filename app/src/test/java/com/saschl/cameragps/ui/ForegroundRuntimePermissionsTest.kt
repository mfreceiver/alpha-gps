package com.saschl.cameragps.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [foregroundRuntimePermissions]. The android.permission.* string
 * constants and Build.VERSION_CODES.TIRAMISU are compile-time constants, so this pure
 * function is testable on the JVM without Robolectric.
 */
class ForegroundRuntimePermissionsTest {

    private val fine = "android.permission.ACCESS_FINE_LOCATION"
    private val coarse = "android.permission.ACCESS_COARSE_LOCATION"
    private val bluetooth = "android.permission.BLUETOOTH_CONNECT"
    private val notifications = "android.permission.POST_NOTIFICATIONS"

    @Test
    fun api31And32RequestFineAndCoarseTogether() {
        listOf(31, 32).forEach { sdk ->
            val permissions = foregroundRuntimePermissions(sdk)
            assertTrue("API $sdk must request FINE", fine in permissions)
            assertTrue("API $sdk must request COARSE together with FINE", coarse in permissions)
            assertEquals(
                "API $sdk list",
                listOf(fine, coarse, bluetooth),
                permissions,
            )
        }
    }

    @Test
    fun api33Through36AddNotifications() {
        listOf(33, 34, 35, 36).forEach { sdk ->
            assertEquals(
                "API $sdk list",
                listOf(fine, coarse, bluetooth, notifications),
                foregroundRuntimePermissions(sdk),
            )
        }
    }
}
