package com.sasch.cameragps.sharednew.bluetooth.coordinator

import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.status.CameraLiveStatus
import com.sasch.cameragps.sharednew.bluetooth.status.GpsWriteStats
import kotlin.test.Test
import kotlin.test.assertEquals

class BleRemoteModelsTest {

    @Test
    fun assessBleLocationCompatibility_identifiesKnownExclusiveAndUnverified() {
        assertEquals(
            BleLocationCompatibility.KnownMutuallyExclusive,
            assessBleLocationCompatibility("ILCE-6400", isCustomName = false),
        )
        assertEquals(
            BleLocationCompatibility.KnownMutuallyExclusive,
            assessBleLocationCompatibility("ilce-6400", isCustomName = false),
        )
        assertEquals(
            BleLocationCompatibility.Unverified,
            assessBleLocationCompatibility("ILCE-6400", isCustomName = true),
        )
        assertEquals(
            BleLocationCompatibility.Unverified,
            assessBleLocationCompatibility("ILCE-7M4", isCustomName = false),
        )
        assertEquals(
            BleLocationCompatibility.Unverified,
            assessBleLocationCompatibility("ILCE-6700", isCustomName = false),
        )
        assertEquals(
            BleLocationCompatibility.Unverified,
            assessBleLocationCompatibility(null, isCustomName = false),
        )
    }

    @Test
    fun evaluateGpsRecoveryStatus_requiresNewWriteAckAndRespectsCameraDisabled() {
        val id = "AA:BB:CC:DD:EE:FF"
        val baselineWithPrior = captureGpsRecoveryBaseline(
            hadPriorWriteAtEntry = true,
            liveStatus = CameraLiveStatus(
                address = id,
                gps = GpsWriteStats(writtenCount = 3, lastWriteAtMillis = 1_000L),
            ),
        )

        // Transmitting alone without a new write ack must not claim Restored.
        val transmittingSession = CameraSession(id, phase = BleSessionPhase.Transmitting)
        val unchangedStatus = CameraLiveStatus(
            address = id,
            phase = BleSessionPhase.Transmitting,
            gps = GpsWriteStats(writtenCount = 3, lastWriteAtMillis = 1_000L),
        )
        assertEquals(
            GpsRecoveryStatus.WaitingForLocationFix,
            evaluateGpsRecoveryStatus(baselineWithPrior, transmittingSession, unchangedStatus, hasRecentLocationFix = false),
        )
        assertEquals(
            GpsRecoveryStatus.Unconfirmed,
            evaluateGpsRecoveryStatus(baselineWithPrior, transmittingSession, unchangedStatus, hasRecentLocationFix = true),
        )

        // Camera explicitly reporting location disabled takes precedence.
        assertEquals(
            GpsRecoveryStatus.DisabledByCamera,
            evaluateGpsRecoveryStatus(
                baselineWithPrior,
                transmittingSession.copy(locationDisabledByCamera = true),
                unchangedStatus,
                hasRecentLocationFix = true,
            ),
        )

        // New write ack observed -> Restored when prior write existed, FirstWriteReceived otherwise.
        val newWriteStatus = CameraLiveStatus(
            address = id,
            phase = BleSessionPhase.Transmitting,
            gps = GpsWriteStats(writtenCount = 4, lastWriteAtMillis = 2_000L),
        )
        assertEquals(
            GpsRecoveryStatus.Restored,
            evaluateGpsRecoveryStatus(baselineWithPrior, transmittingSession, newWriteStatus, hasRecentLocationFix = true),
        )

        val baselineNoPrior = captureGpsRecoveryBaseline(
            hadPriorWriteAtEntry = false,
            liveStatus = CameraLiveStatus(address = id),
        )
        assertEquals(
            GpsRecoveryStatus.FirstWriteReceived,
            evaluateGpsRecoveryStatus(
                baselineNoPrior,
                transmittingSession,
                CameraLiveStatus(address = id, gps = GpsWriteStats(writtenCount = 1, lastWriteAtMillis = 3_000L)),
                hasRecentLocationFix = true,
            ),
        )

        // Disconnected session ends observation.
        assertEquals(
            GpsRecoveryStatus.Inactive,
            evaluateGpsRecoveryStatus(
                baselineWithPrior,
                transmittingSession.copy(phase = BleSessionPhase.Disconnected),
                newWriteStatus,
                hasRecentLocationFix = true,
            ),
        )
    }
}
