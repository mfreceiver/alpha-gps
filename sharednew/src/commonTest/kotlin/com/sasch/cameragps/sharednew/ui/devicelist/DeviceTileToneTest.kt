package com.sasch.cameragps.sharednew.ui.devicelist

import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import kotlin.test.Test
import kotlin.test.assertEquals

/** The device row's icon tile: a problem must stay visible even while the camera is connected. */
class DeviceTileToneTest {
    @Test
    fun problemWinsOverConnected() {
        assertEquals(StatusTone.Error, deviceTileTone(hasProblem = true, connected = true))
        assertEquals(StatusTone.Error, deviceTileTone(hasProblem = true, connected = false))
    }

    @Test
    fun connectedIsActiveAndIdleIsNeutral() {
        assertEquals(StatusTone.Active, deviceTileTone(hasProblem = false, connected = true))
        assertEquals(StatusTone.Neutral, deviceTileTone(hasProblem = false, connected = false))
    }
}
