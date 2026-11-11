package com.sasch.cameragps.sharednew.bluetooth.location

import com.diamondedge.logging.FixedLogLevel
import com.diamondedge.logging.KmLogging
import com.diamondedge.logging.PlatformLogger
import com.sasch.cameragps.sharednew.bluetooth.SonyBluetoothConstants
import com.sasch.cameragps.sharednew.bluetooth.coordinator.BleGattPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class LocationTransmissionStateTest {
    @BeforeTest
    fun disablePlatformLogging() {
        KmLogging.setLoggers()
    }

    @AfterTest
    fun restorePlatformLogging() {
        KmLogging.setLoggers(PlatformLogger(FixedLogLevel(true)))
    }

    @Test
    fun requiresQueuedLocationAndClearsOnDisconnectOrAppDisable() = runTest {
        val f = Fixture(backgroundScope)
        f.manager.onDeviceReady("A")
        runCurrent()
        assertTrue(f.manager.isActive.value)
        assertFalse(f.manager.isTransmitting.value)
        f.source.locations.emit(fix())
        runCurrent()
        assertTrue(f.manager.isTransmitting.value)
        f.allowed = false
        f.manager.updateTracking()
        assertFalse(f.manager.isTransmitting.value)
        f.allowed = true
        f.manager.onDeviceReady("A")
        runCurrent()
        f.source.locations.emit(fix())
        runCurrent()
        assertTrue(f.manager.isTransmitting.value)
        f.ready.clear()
        f.manager.updateTracking()
        assertFalse(f.manager.isTransmitting.value)
    }

    @Test
    fun rejectedWritesAndUnavailableLocationSourceDoNotReportTransmission() = runTest {
        val f = Fixture(backgroundScope)
        f.port.acceptWrites = false
        f.manager.onDeviceReady("A")
        runCurrent()
        f.source.locations.emit(fix())
        runCurrent()
        assertFalse(f.manager.isTransmitting.value)
        f.port.acceptWrites = true
        advanceTimeBy(SonyBluetoothConstants.LOCATION_UPDATE_INTERVAL_MS)
        runCurrent()
        assertTrue(f.manager.isTransmitting.value)
        f.manager.shutdown()
        assertFalse(f.manager.isTransmitting.value)
        f.source.canStart = false
        f.manager.onDeviceReady("A")
        runCurrent()
        assertFalse(f.manager.isActive.value)
        assertFalse(f.manager.isTransmitting.value)
    }

    @Test
    fun reportsOnlyAcceptedPacketsAsQueuedAndNeverTheirContent() = runTest {
        val f = Fixture(backgroundScope)
        f.manager.onDeviceReady("A")
        runCurrent()
        f.source.locations.emit(fix())
        runCurrent()
        assertEquals(listOf("A"), f.queued)
        f.port.acceptWrites = false
        advanceTimeBy(SonyBluetoothConstants.LOCATION_UPDATE_INTERVAL_MS)
        runCurrent()
        assertEquals(listOf("A"), f.queued)
        f.port.acceptWrites = true
        advanceTimeBy(SonyBluetoothConstants.LOCATION_UPDATE_INTERVAL_MS)
        runCurrent()
        assertEquals(listOf("A", "A"), f.queued)
    }

    @Test
    fun exposesTheLatestFixAccuracyWithoutChangingWhatIsSent() = runTest {
        val f = Fixture(backgroundScope)
        assertEquals(null, f.manager.latestFix.value)
        f.manager.onDeviceReady("A")
        runCurrent()
        val first = fix()
        f.source.locations.emit(first)
        runCurrent()
        assertEquals(PhoneLocationFix(5.0, first.timestampMillis), f.manager.latestFix.value)
        assertEquals(f.manager.latestFix.value, f.fixes.last())
        assertEquals(listOf("A"), f.queued)
        // Seen even while sending is not allowed (the gate decides sending, not what the phone sees).
        f.allowed = false
        val second = GeoLocation(52.52, 13.405, 42.0, first.timestampMillis + 1_000)
        f.source.locations.emit(second)
        runCurrent()
        assertEquals(PhoneLocationFix(42.0, second.timestampMillis), f.manager.latestFix.value)
        assertEquals(listOf("A"), f.queued)
        f.manager.shutdown()
        assertEquals(null, f.manager.latestFix.value)
        assertEquals(null, f.fixes.last())
    }

    @Test
    fun aThrowingFixObserverChangesNothingAboutGatingThrottlingOrSending() = runTest {
        suspend fun scenario(scope: CoroutineScope, throwing: Boolean): List<String> {
            val f = Fixture(scope, observer = if (throwing) { _ -> error("observer boom") } else null)
            val trace = mutableListOf<String>()
            fun snap(label: String) {
                trace += "$label queued=${f.queued} writes=${f.port.writes} " +
                    "tx=${f.manager.isTransmitting.value} active=${f.manager.isActive.value} " +
                    "latest=${f.manager.latestFix.value?.accuracyMeters}"
            }
            f.manager.onDeviceReady("A")
            runCurrent()
            val first = fix()
            f.source.locations.emit(first)
            runCurrent()
            snap("first")
            // Much worse accuracy and recent: gated out, nothing sent.
            f.source.locations.emit(GeoLocation(52.52, 13.405, 500.0, first.timestampMillis + 1_000))
            runCurrent()
            snap("gated")
            // Better fix: accepted, picked up by the periodic tick (throttle), not sent at once.
            f.source.locations.emit(GeoLocation(52.53, 13.406, 3.0, first.timestampMillis + 2_000))
            runCurrent()
            snap("throttled")
            advanceTimeBy(SonyBluetoothConstants.LOCATION_UPDATE_INTERVAL_MS)
            runCurrent()
            snap("tick")
            // Transmission not allowed: observed, but nothing sent.
            f.allowed = false
            f.source.locations.emit(GeoLocation(52.54, 13.407, 4.0, first.timestampMillis + 3_000))
            runCurrent()
            snap("blocked")
            f.allowed = true
            // The collector survived every observer failure: later fixes are still processed.
            f.source.locations.emit(GeoLocation(52.55, 13.408, 2.0, first.timestampMillis + 4_000))
            runCurrent()
            snap("after")
            f.manager.shutdown()
            snap("shutdown")
            assertEquals(if (throwing) 6 else 0, f.observerFailures)
            return trace
        }

        val withObserver = scenario(backgroundScope, throwing = true)
        val without = scenario(backgroundScope, throwing = false)
        assertEquals(without, withObserver)
        // Sanity: the scenario really sent packets (the comparison is not vacuous).
        assertTrue(without.any { it.startsWith("tick") && "queued=[A, A]" in it })
    }

    @Test
    fun aThrowingObserverDoesNotMakeShutdownThrow() = runTest {
        val f = Fixture(backgroundScope, observer = { error("observer boom") })
        f.manager.onDeviceReady("A")
        runCurrent()
        f.source.locations.emit(fix())
        runCurrent()
        f.manager.shutdown()
        assertEquals(null, f.manager.latestFix.value)
        assertFalse(f.manager.isActive.value)
        assertFalse(f.manager.isTransmitting.value)
    }

    @Test
    fun cancellationFromTheObserverIsNotSwallowed() = runTest {
        val f = Fixture(backgroundScope, observer = { throw CancellationException("cancelled") })
        assertFailsWith<CancellationException> { f.manager.shutdown() }
    }

    private class Fixture(scope: CoroutineScope, observer: ((PhoneLocationFix?) -> Unit)? = null) {
        var observerFailures = 0
        val source = FakeSource()
        val port = FakePort()
        val ready = mutableSetOf("A")
        var allowed = true
        val queued = mutableListOf<String>()
        val fixes = mutableListOf<PhoneLocationFix?>()
        val manager = LocationTransmissionManager(
            source, { ready }, { null }, port, scope, { allowed },
            onPacketQueued = { queued += it },
            onFixObserved = {
                fixes += it
                if (observer != null) {
                    try {
                        observer(it)
                    } catch (e: Exception) {
                        observerFailures++
                        throw e
                    }
                }
            },
        )
    }

    private class FakeSource : LocationSource {
        override val locations = MutableSharedFlow<GeoLocation>()
        var canStart = true
        override fun start() = canStart
        override fun stop() = Unit
        override fun hasPreciseAuthorization() = true
    }

    private class FakePort : BleGattPort {
        override fun setAutoCorrectionState(
            identifier: String,
            setting: com.sasch.cameragps.sharednew.bluetooth.session.CameraAutoCorrectionSetting,
            state: com.sasch.cameragps.sharednew.bluetooth.session.CameraSettingState,
        ) = Unit
        var acceptWrites = true
        var writes = 0
        override fun writeCharacteristic(
            identifier: String,
            characteristicUuid: String,
            value: ByteArray
        ): Boolean {
            writes++
            return acceptWrites
        }

        override fun subscribeToNotifications(identifier: String, characteristicUuid: String) =
            false

        override fun isConnected(identifier: String) = true
        override fun hasRemoteControlCharacteristic(identifier: String) = false
        override fun isRemoteFeatureActive(identifier: String) = false
        override fun setRemoteFeatureActive(identifier: String, active: Boolean) = Unit
        override fun setShutterSequenceActive(identifier: String, active: Boolean) = Unit
        override fun setLocationDisabledByCamera(identifier: String, disabled: Boolean) = Unit
        override fun isLocationDisabledByCamera(identifier: String) = false
        override fun readCharacteristic(identifier: String, characteristicUuid: String) = false
        override fun hasCharacteristic(identifier: String, characteristicUuid: String) = true
    }

    private companion object {
        fun fix() = GeoLocation(52.52, 13.405, 5.0, Clock.System.now().toEpochMilliseconds())
    }
}
