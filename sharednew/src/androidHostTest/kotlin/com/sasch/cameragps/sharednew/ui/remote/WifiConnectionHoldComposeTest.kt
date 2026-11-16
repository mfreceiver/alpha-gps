package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WifiConnectionHoldComposeTest {
    @get:Rule val compose = createComposeRule()

    private fun recompose() {
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Test
    fun catalogCompletionKeepsReadyUntilTheFullHoldHasElapsed() {
        compose.mainClock.autoAdvance = false
        var frame: WifiConnectionFrame? by mutableStateOf(WifiConnectionFrame.Progress(WifiConnectStep.ReadCatalog))
        val rendered = mutableListOf<WifiConnectionFrame?>()
        compose.setContent {
            val shown = rememberShownWifiConnectionFrame(frame, ready = true)
            SideEffect { rendered.add(shown) }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { rendered.clear(); frame = null }
        recompose() // completion and launch of the hold
        compose.mainClock.advanceTimeBy(350)
        compose.runOnIdle {
            assertTrue(rendered.isNotEmpty())
            assertTrue("no list frame before 400 ms: $rendered", rendered.all { it == WifiConnectionFrame.Ready })
        }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertEquals(null, rendered.last()) }
    }

    @Test
    fun interruptionsCancelOldHoldWithoutShorteningTheNextSession() {
        compose.mainClock.autoAdvance = false
        var frame: WifiConnectionFrame? by mutableStateOf(WifiConnectionFrame.Progress(WifiConnectStep.ReadCatalog))
        var ready by mutableStateOf(true)
        val rendered = mutableListOf<WifiConnectionFrame?>()
        compose.setContent {
            val shown = rememberShownWifiConnectionFrame(frame, ready)
            SideEffect { rendered.add(shown) }
        }
        compose.mainClock.advanceTimeByFrame()
        val interruptions = listOf(WifiConnectionFrame.Closing, WifiConnectionFrame.Failed(null), null)
        interruptions.forEach { interruption ->
            compose.runOnIdle { frame = WifiConnectionFrame.Progress(WifiConnectStep.ReadCatalog); ready = true }
            recompose()
            compose.runOnIdle { frame = null }
            recompose()
            compose.mainClock.advanceTimeBy(128)
            compose.runOnIdle { frame = interruption; if (interruption == null) ready = false }
            recompose()
            compose.runOnIdle {
                assertEquals("interruption must immediately replace Ready", interruption, rendered.last())
                frame = WifiConnectionFrame.Progress(WifiConnectStep.ReadCatalog)
                ready = true
            }
            recompose()
            compose.runOnIdle { frame = null; rendered.clear() }
            recompose()
            // Cross the first session's deadline but remain before the new session's deadline.
            compose.mainClock.advanceTimeBy(300)
            compose.runOnIdle {
                assertFalse(rendered.isEmpty())
                assertTrue("old timer must not expose the new list: $rendered", rendered.all { it == WifiConnectionFrame.Ready })
            }
            compose.mainClock.advanceTimeBy(150)
            compose.runOnIdle { assertEquals(null, rendered.last()) }
        }
    }
}
