package com.sasch.cameragps.sharednew.ui.remote

import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WifiAutoConnectTest {
    private fun decide(
        enabled: Boolean = true,
        attempted: Boolean = false,
        acknowledged: Boolean = true,
        supported: Boolean = true,
        phase: WifiRemotePhase = WifiRemotePhase.Idle,
        other: Boolean = false,
    ) = shouldAutoConnectOnEntry(enabled, attempted, acknowledged, supported, phase, other)

    @Test
    fun entryOnAnIdleSessionConnectsAutomatically() = assertTrue(decide())

    @Test
    fun firesOnlyOncePerEntry() = assertFalse(decide(attempted = true))

    @Test
    fun waitsForTheExperimentalNotice() = assertFalse(decide(acknowledged = false))

    @Test
    fun neverStartsOnTopOfARunningOrFailedSession() {
        for (phase in WifiRemotePhase.entries) {
            if (phase == WifiRemotePhase.Idle) continue
            assertFalse(decide(phase = phase), "phase $phase must not auto-connect")
        }
    }

    @Test
    fun respectsOptOutPlatformSupportAndOtherCameras() {
        assertFalse(decide(enabled = false))
        assertFalse(decide(supported = false))
        assertFalse(decide(other = true))
    }
}
