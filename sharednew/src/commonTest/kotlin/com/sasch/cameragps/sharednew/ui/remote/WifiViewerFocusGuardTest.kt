package com.sasch.cameragps.sharednew.ui.remote

import com.sasch.cameragps.sharednew.remote.wifi.WifiLoggingTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The viewer's focus moves (drawer entry, return to the strip) used to sit in `runCatching`, which
 * hid every failure. Only the "target not attached" [IllegalStateException] is expected now.
 * (TalkBack behaviour itself needs a device: sharednew has no Compose UI test dependency.)
 */
class WifiViewerFocusGuardTest : WifiLoggingTest() {
    @Test
    fun aSuccessfulRequestReportsTrue() {
        var requested = 0
        assertTrue(requestFocusGuarded("test") { requested++ })
        assertEquals(1, requested)
    }

    @Test
    fun anUnattachedTargetIsLoggedAndSkipped() {
        assertFalse(requestFocusGuarded("test") { throw IllegalStateException("FocusRequester is not initialized") })
    }

    @Test
    fun anyOtherFailureAndCancellationPropagate() {
        assertFailsWith<IllegalArgumentException> {
            requestFocusGuarded("test") { throw IllegalArgumentException("real bug") }
        }
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            requestFocusGuarded("test") { throw kotlinx.coroutines.CancellationException("cancelled") }
        }
    }
}
