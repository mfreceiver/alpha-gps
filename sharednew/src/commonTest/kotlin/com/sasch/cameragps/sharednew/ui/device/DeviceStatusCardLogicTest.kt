package com.sasch.cameragps.sharednew.ui.device

import com.sasch.cameragps.sharednew.bluetooth.status.GpsWriteStats
import com.sasch.cameragps.sharednew.bluetooth.status.TimeSyncOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

/** Batch Q: the phone location signal cell and the status card's warning-only subtitle. */
class DeviceStatusCardLogicTest {
    private val now = 1_000_000_000L

    @Test
    fun locationSignalBoundariesAreInclusive() {
        assertEquals(LocationSignalLevel.Strong, locationSignalLevel(3.0, now, now))
        assertEquals(LocationSignalLevel.Strong, locationSignalLevel(10.0, now, now))
        assertEquals(LocationSignalLevel.Medium, locationSignalLevel(10.01, now, now))
        assertEquals(LocationSignalLevel.Medium, locationSignalLevel(30.0, now, now))
        assertEquals(LocationSignalLevel.Weak, locationSignalLevel(30.01, now, now))
        assertEquals(LocationSignalLevel.Weak, locationSignalLevel(500.0, now, now))
    }

    @Test
    fun missingOrInvalidAccuracyIsUnknown() {
        assertEquals(LocationSignalLevel.Unknown, locationSignalLevel(null, now, now))
        assertEquals(LocationSignalLevel.Unknown, locationSignalLevel(5.0, null, now))
        assertEquals(LocationSignalLevel.Unknown, locationSignalLevel(-1.0, now, now))
        // Android reports 0 when the provider has no accuracy estimate.
        assertEquals(LocationSignalLevel.Unknown, locationSignalLevel(0.0, now, now))
        assertEquals(LocationSignalLevel.Unknown, locationSignalLevel(Double.NaN, now, now))
        assertEquals(LocationSignalLevel.Unknown, locationSignalLevel(Double.POSITIVE_INFINITY, now, now))
    }

    @Test
    fun aFixOlderThanTwoMinutesIsUnknown() {
        assertEquals(LocationSignalLevel.Strong, locationSignalLevel(5.0, now - LOCATION_FIX_MAX_AGE_MILLIS, now))
        assertEquals(LocationSignalLevel.Unknown, locationSignalLevel(5.0, now - LOCATION_FIX_MAX_AGE_MILLIS - 1, now))
        // A fix stamped slightly ahead of the phone clock is still current.
        assertEquals(LocationSignalLevel.Medium, locationSignalLevel(20.0, now + 1_000, now))
    }

    @Test
    fun aHealthyConnectedLinkHasNoSubtitle() {
        TimeSyncOutcome.entries
            .filter { it != TimeSyncOutcome.Failed && it != TimeSyncOutcome.Rejected }
            .forEach { outcome ->
                assertEquals(
                    StatusCardAlert.None,
                    statusCardAlert(ConnectionHeadline.Connected, false, GpsWriteStats(writtenCount = 3, lastWriteAtMillis = now), outcome),
                    "$outcome",
                )
            }
        assertEquals(StatusCardAlert.None, statusCardAlert(ConnectionHeadline.Connected, false, null, null))
    }

    @Test
    fun onlyAConnectedLinkShowsAnAlert() {
        listOf(ConnectionHeadline.Connecting, ConnectionHeadline.Disconnecting, ConnectionHeadline.Disconnected).forEach {
            assertEquals(StatusCardAlert.None, statusCardAlert(it, true, null, TimeSyncOutcome.Failed), "$it")
        }
    }

    @Test
    fun timeSyncProblemsAreReported() {
        assertEquals(
            StatusCardAlert.TimeSyncFailed,
            statusCardAlert(ConnectionHeadline.Connected, false, null, TimeSyncOutcome.Failed),
        )
        assertEquals(
            StatusCardAlert.TimeSyncRejected,
            statusCardAlert(ConnectionHeadline.Connected, false, null, TimeSyncOutcome.Rejected),
        )
    }

    @Test
    fun aWriteFailureCountsOnlyUntilTheNextSuccessfulWrite() {
        val failing = GpsWriteStats(writtenCount = 2, failedCount = 1, lastWriteAtMillis = now - 10, lastFailureAtMillis = now)
        assertEquals(StatusCardAlert.LocationWriteFailing, statusCardAlert(ConnectionHeadline.Connected, false, failing, null))
        val neverWritten = GpsWriteStats(failedCount = 1, lastFailureAtMillis = now)
        assertEquals(StatusCardAlert.LocationWriteFailing, statusCardAlert(ConnectionHeadline.Connected, false, neverWritten, null))
        val recovered = failing.copy(writtenCount = 3, lastWriteAtMillis = now + 10)
        assertEquals(StatusCardAlert.None, statusCardAlert(ConnectionHeadline.Connected, false, recovered, null))
    }

    @Test
    fun alertsArePrioritised() {
        val failing = GpsWriteStats(failedCount = 1, lastFailureAtMillis = now)
        // Camera-side location off (transfer paused) beats a write error, which beats time sync.
        assertEquals(
            StatusCardAlert.LocationDisabledByCamera,
            statusCardAlert(ConnectionHeadline.Connected, true, failing, TimeSyncOutcome.Failed),
        )
        assertEquals(
            StatusCardAlert.LocationWriteFailing,
            statusCardAlert(ConnectionHeadline.Connected, false, failing, TimeSyncOutcome.Failed),
        )
    }
}
