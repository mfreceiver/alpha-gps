package com.saschl.cameragps.service

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Automatic reconnection entries (BOOT_COMPLETED / MY_PACKAGE_REPLACED) must
 * honor the app master switch: an enabled camera must not reconnect — nor
 * start a foreground service — while the user turned the app off.
 */
class RebootReceiverTest {

    @Test
    fun `package replaced reconnects an enabled camera when the app is on`() {
        assertTrue(
            shouldAutoStartAfter(
                Intent.ACTION_MY_PACKAGE_REPLACED,
                autoStartAfterBootEnabled = false,
                appEnabled = true,
            )
        )
    }

    @Test
    fun `boot reconnects when the boot toggle and the app are on`() {
        assertTrue(
            shouldAutoStartAfter(
                Intent.ACTION_BOOT_COMPLETED,
                autoStartAfterBootEnabled = true,
                appEnabled = true,
            )
        )
    }

    @Test
    fun `app master switch off means no reconnect after package replacement`() {
        assertFalse(
            shouldAutoStartAfter(
                Intent.ACTION_MY_PACKAGE_REPLACED,
                autoStartAfterBootEnabled = true,
                appEnabled = false,
            )
        )
    }

    @Test
    fun `app master switch off means no reconnect after boot`() {
        assertFalse(
            shouldAutoStartAfter(
                Intent.ACTION_BOOT_COMPLETED,
                autoStartAfterBootEnabled = true,
                appEnabled = false,
            )
        )
    }

    @Test
    fun `boot toggle off means no reconnect even with the app on`() {
        assertFalse(
            shouldAutoStartAfter(
                Intent.ACTION_BOOT_COMPLETED,
                autoStartAfterBootEnabled = false,
                appEnabled = true,
            )
        )
    }
}
