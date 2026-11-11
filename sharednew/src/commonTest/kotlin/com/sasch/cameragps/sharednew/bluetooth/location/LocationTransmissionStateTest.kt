package com.sasch.cameragps.sharednew.bluetooth.location

import com.diamondedge.logging.FixedLogLevel
import com.diamondedge.logging.KmLogging
import com.diamondedge.logging.PlatformLogger
import com.sasch.cameragps.sharednew.bluetooth.SonyBluetoothConstants
import com.sasch.cameragps.sharednew.bluetooth.coordinator.BleGattPort
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

    private class Fixture(scope: CoroutineScope) {
        val source = FakeSource()
        val port = FakePort()
        val ready = mutableSetOf("A")
        var allowed = true
        val queued = mutableListOf<String>()
        val fixes = mutableListOf<PhoneLocationFix?>()
        val manager = LocationTransmissionManager(
            source, { ready }, { null }, port, scope, { allowed },
            onPacketQueued = { queued += it },
            onFixObserved = { fixes += it },
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
        override fun writeCharacteristic(
            identifier: String,
            characteristicUuid: String,
            value: ByteArray
        ) = acceptWrites

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
