package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WifiConnectionHoldComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun catalogCompletionNeverComposesTheListBeforeTheReadyHold() {
        var frame: WifiConnectionFrame? by mutableStateOf(WifiConnectionFrame.Progress(WifiConnectStep.ReadCatalog))
        val rendered = mutableListOf<WifiConnectionFrame?>()
        compose.setContent {
            val shown = rememberShownWifiConnectionFrame(frame, ready = true)
            SideEffect { rendered.add(shown) }
        }
        compose.waitForIdle()
        compose.runOnIdle { rendered.clear(); frame = null }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(rendered.isEmpty())
            assertEquals(WifiConnectionFrame.Ready, rendered.first())
        }
        compose.mainClock.advanceTimeBy(CONNECTION_READY_HOLD_MS + 64)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(null, rendered.last()) }
    }
}
