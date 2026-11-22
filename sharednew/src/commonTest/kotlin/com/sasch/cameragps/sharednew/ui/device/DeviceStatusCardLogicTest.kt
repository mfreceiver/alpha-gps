package com.sasch.cameragps.sharednew.ui.device

import com.sasch.cameragps.sharednew.bluetooth.BleFailure
import com.sasch.cameragps.sharednew.bluetooth.BleFailureReason
import com.sasch.cameragps.sharednew.bluetooth.status.GpsWriteStats
import com.sasch.cameragps.sharednew.bluetooth.status.TimeSyncOutcome
import com.sasch.cameragps.sharednew.ui.help.GuideTopic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun rssiIsStaleOnlyAfterThirtySeconds() {
        assertEquals(false, isRssiStale(now - RSSI_STALE_AFTER_MILLIS, now))
        assertEquals(true, isRssiStale(now - RSSI_STALE_AFTER_MILLIS - 1, now))
        assertEquals(true, isRssiStale(null, now))
    }

    @Test
    fun rssiStaleWaitIsTheExactTimeToExpiry() {
        val now = 1_000_000L
        assertEquals(null, rssiStaleInMillis(null, now))
        assertEquals(null, rssiStaleInMillis(now - RSSI_STALE_AFTER_MILLIS - 1, now))
        val wait = rssiStaleInMillis(now - 10_000, now)!!
        assertEquals(false, isRssiStale(now - 10_000, now + wait - 1))
        assertEquals(true, isRssiStale(now - 10_000, now + wait))
        // A reading stamped slightly ahead of the phone clock is fresh.
        assertEquals(false, isRssiStale(now + 1_000, now))
    }

    @Test
    fun weakSignalIsStrictlyBelowMinus85AndFresh() {
        assertEquals(false, isSignalWeak(-85, now, now))
        assertEquals(true, isSignalWeak(-86, now, now))
        assertEquals(true, isSignalWeak(-120, now, now))
        // Stale or missing readings never claim a weak signal; neither do "not available" values.
        assertEquals(false, isSignalWeak(-90, now - RSSI_STALE_AFTER_MILLIS - 1, now))
        assertEquals(false, isSignalWeak(-90, null, now))
        assertEquals(false, isSignalWeak(null, now, now))
        assertEquals(false, isSignalWeak(-127, now, now))
        assertEquals(false, isSignalWeak(127, now, now))
    }

    @Test
    fun weakSignalAlertYieldsToEveryOtherAlertAndNeedsAConnectedLink() {
        assertEquals(
            StatusCardAlert.WeakSignal,
            statusCardAlert(ConnectionHeadline.Connected, false, null, null, weakSignal = true),
        )
        assertEquals(
            StatusCardAlert.TimeSyncFailed,
            statusCardAlert(ConnectionHeadline.Connected, false, null, TimeSyncOutcome.Failed, weakSignal = true),
        )
        assertEquals(
            StatusCardAlert.LocationDisabledByCamera,
            statusCardAlert(ConnectionHeadline.Connected, true, null, null, weakSignal = true),
        )
        assertEquals(
            StatusCardAlert.None,
            statusCardAlert(ConnectionHeadline.Disconnected, false, null, null, weakSignal = true),
        )
    }

    @Test
    fun bleFailureReasonsMapToDifferentiatedAlertsAndRepairGuideTopic() {
        val expected = mapOf(
            BleFailureReason.BondLost to StatusCardAlert.BleBondLost,
            BleFailureReason.AuthenticationFailed to StatusCardAlert.BleAuthenticationFailed,
            BleFailureReason.Timeout to StatusCardAlert.BleTimeout,
            BleFailureReason.OutOfRange to StatusCardAlert.BleOutOfRange,
            BleFailureReason.BluetoothOff to StatusCardAlert.BleBluetoothOff,
            BleFailureReason.PermissionMissing to StatusCardAlert.BlePermissionMissing,
            BleFailureReason.GattFailure to StatusCardAlert.BleGattFailed,
            BleFailureReason.Unknown to StatusCardAlert.BleUnknown,
        )
        expected.forEach { (reason, alert) ->
            assertEquals(
                alert,
                statusCardAlert(
                    ConnectionHeadline.Disconnected,
                    false,
                    null,
                    null,
                    bleFailure = BleFailure(reason),
                ),
            )
        }
        assertEquals(GuideTopic.Repair, statusCardAlertTopic(StatusCardAlert.BleBondLost))
        assertEquals(GuideTopic.Repair, statusCardAlertTopic(StatusCardAlert.BleAuthenticationFailed))
        assertNull(statusCardAlertTopic(StatusCardAlert.BleTimeout))
        assertNull(statusCardAlertTopic(StatusCardAlert.BleOutOfRange))
        assertNull(statusCardAlertTopic(StatusCardAlert.BleGattFailed))
        // Connecting or connected headlines do not surface a stale disconnect failure.
        assertEquals(
            StatusCardAlert.None,
            statusCardAlert(
                ConnectionHeadline.Connecting,
                false,
                null,
                null,
                bleFailure = BleFailure(BleFailureReason.Timeout),
            ),
        )
    }
}
