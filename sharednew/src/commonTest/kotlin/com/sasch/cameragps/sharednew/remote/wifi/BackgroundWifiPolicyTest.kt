package com.sasch.cameragps.sharednew.remote.wifi

import kotlin.test.Test
import kotlin.test.assertEquals

class BackgroundWifiPolicyTest {
    @Test
    fun foregroundKeepsWifiRegardlessOfDownload() {
        for (downloading in listOf(false, true)) {
            assertEquals(BackgroundWifiPolicy.Action.Keep, BackgroundWifiPolicy.decide(true, downloading))
        }
    }

    @Test
    fun homeWithoutUserDownloadClosesImmediately() {
        assertEquals(BackgroundWifiPolicy.Action.Close, BackgroundWifiPolicy.decide(false, false))
    }

    @Test
    fun homeWithUserDownloadWaitsOnlyUntilDownloadFinishes() {
        assertEquals(BackgroundWifiPolicy.Action.FinishDownload, BackgroundWifiPolicy.decide(false, true))
        assertEquals(BackgroundWifiPolicy.Action.Close, BackgroundWifiPolicy.decide(false, false))
    }

    @Test
    fun returningForegroundCancelsDeferredClose() {
        assertEquals(BackgroundWifiPolicy.Action.FinishDownload, BackgroundWifiPolicy.decide(false, true))
        assertEquals(BackgroundWifiPolicy.Action.Keep, BackgroundWifiPolicy.decide(true, true))
        assertEquals(BackgroundWifiPolicy.Action.Keep, BackgroundWifiPolicy.decide(true, false))
    }

    @Test
    fun explicitExitOverridesForegroundAndDownloadException() {
        for (foreground in listOf(false, true)) {
            for (downloading in listOf(false, true)) {
                assertEquals(
                    BackgroundWifiPolicy.Action.Close,
                    BackgroundWifiPolicy.decide(foreground, downloading, explicitExit = true),
                )
            }
        }
    }
}
