package com.sasch.cameragps.sharednew.bluetooth.session

import com.diamondedge.logging.FixedLogLevel
import com.diamondedge.logging.KmLogging
import com.diamondedge.logging.PlatformLogger
import com.sasch.cameragps.sharednew.bluetooth.BleFailure
import com.sasch.cameragps.sharednew.bluetooth.BleFailureReason
import com.sasch.cameragps.sharednew.bluetooth.BleFailureStage
import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.location.GeoLocation
import com.sasch.cameragps.sharednew.bluetooth.location.LocationSource
import com.sasch.cameragps.sharednew.bluetooth.transport.BlePeripheralTransport
import com.sasch.cameragps.sharednew.bluetooth.transport.BleTransportEvent
import com.sasch.cameragps.sharednew.database.devices.CameraDevice
import com.sasch.cameragps.sharednew.database.devices.CameraDeviceDAO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class BleFailureMappingTest {

    @BeforeTest
    fun disablePlatformLogging() {
        KmLogging.setLoggers()
    }

    @AfterTest
    fun restorePlatformLogging() {
        KmLogging.setLoggers(PlatformLogger(FixedLogLevel(true)))
    }

    private class FakeTransport : BlePeripheralTransport {
        val emitted = MutableSharedFlow<BleTransportEvent>(extraBufferCapacity = 32)
        override val events: Flow<BleTransportEvent> = emitted
        override fun isConnected(identifier: String) = false
        override fun hasCharacteristic(identifier: String, characteristicUuid: String) = false
        override fun supportsWriteWithResponse(identifier: String, characteristicUuid: String) = false
        override fun initiateWrite(identifier: String, characteristicUuid: String, value: ByteArray) = false
        override fun initiateRead(identifier: String, characteristicUuid: String) = false
        override fun initiateSubscribe(identifier: String, characteristicUuid: String, enable: Boolean) = false
        override fun initiateDiscoverServices(identifier: String) = false
    }

    private class FakeDeviceDao : CameraDeviceDAO {
        override suspend fun getAllCameraDevices(): List<CameraDevice> = emptyList()
        override fun observeAllDevices(): Flow<List<CameraDevice>> = flowOf(emptyList())
        override suspend fun insertDevice(device: CameraDevice) = Unit
        override suspend fun setDeviceName(deviceId: String, name: String, isCustom: Boolean) = Unit
        override suspend fun getDeviceName(address: String): String? = null
        override suspend fun deleteDevice(device: CameraDevice) = Unit
        override suspend fun setDeviceEnabled(deviceId: String, enabled: Boolean) = Unit
        override suspend fun isDeviceAlwaysOnEnabled(address: String): Boolean = false
        override suspend fun setAlwaysOnEnabled(deviceId: String, enabled: Boolean) = Unit
        override suspend fun isDeviceEnabled(address: String): Boolean = true
        override suspend fun findDeviceEnabled(address: String): Boolean? = true
        override suspend fun getAlwaysOnEnabledDeviceCount(): Int = 0
        override suspend fun getKeepActiveDeviceCount(): Int = 0
        override suspend fun getEnabledDeviceCount(): Int = 1
        override suspend fun setRemoteControlEnabled(deviceId: String, enabled: Boolean): Int = 0
        override suspend fun isRemoteControlEnabled(address: String): Boolean = false
    }

    private fun newOrchestrator(scope: CoroutineScope, transport: BlePeripheralTransport): CameraSessionOrchestrator =
        CameraSessionOrchestrator(
            transport = transport,
            locationSource = object : LocationSource {
                override val locations: Flow<GeoLocation> = emptyFlow()
                override fun start() = true
                override fun stop() = Unit
                override fun hasPreciseAuthorization() = true
            },
            deviceDao = FakeDeviceDao(),
            scope = scope,
        ).also { it.start() }

    @Test
    fun onConnectFailedRecordsReasonAndSurvivesInLiveStatus() = runTest(UnconfinedTestDispatcher()) {
        val o = newOrchestrator(backgroundScope, FakeTransport())
        o.onConnectRequested("aa:bb")
        o.onConnectFailed("aa:bb", BleFailureReason.BondLost)

        val session = o.registry.get("AA:BB")!!
        assertEquals(BleSessionPhase.Error, session.phase)
        assertEquals(BleFailure(BleFailureReason.BondLost, BleFailureStage.Connect), session.failure)
        assertEquals(
            BleFailure(BleFailureReason.BondLost, BleFailureStage.Connect),
            o.liveStatus.statusOf("AA:BB")?.lastFailure,
        )

        // New connect attempt clears the previous failure.
        o.onConnectRequested("AA:BB")
        assertNull(o.registry.get("AA:BB")?.failure)
        assertNull(o.liveStatus.statusOf("AA:BB")?.lastFailure)
    }

    @Test
    fun disconnectStatusCodesMapToExpectedFailureReasons() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport()
        val o = newOrchestrator(backgroundScope, transport)

        // Timeout during Connecting -> Timeout
        o.onConnectRequested("AA:01")
        transport.emitted.tryEmit(BleTransportEvent.Disconnected("AA:01", statusCode = 8, isManual = false))
        assertEquals(BleFailureReason.Timeout, o.liveStatus.statusOf("AA:01")?.lastFailure?.reason)

        // Peer terminated (19) -> Unknown (not necessarily out of range)
        o.onConnectRequested("AA:02")
        transport.emitted.tryEmit(BleTransportEvent.Disconnected("AA:02", statusCode = 19, isManual = false))
        assertEquals(BleFailureReason.Unknown, o.liveStatus.statusOf("AA:02")?.lastFailure?.reason)

        // GATT 133 -> GattFailure
        o.onConnectRequested("AA:03")
        transport.emitted.tryEmit(BleTransportEvent.Disconnected("AA:03", statusCode = 133, isManual = false))
        assertEquals(BleFailureReason.GattFailure, o.liveStatus.statusOf("AA:03")?.lastFailure?.reason)

        // Manual disconnect clears failure even if statusCode were non-null
        o.onConnectRequested("AA:04")
        o.onConnectFailed("AA:04", BleFailureReason.BluetoothOff)
        transport.emitted.tryEmit(BleTransportEvent.Disconnected("AA:04", statusCode = null, isManual = true))
        assertNull(o.liveStatus.statusOf("AA:04")?.lastFailure)
    }

    @Test
    fun retryDiscoveryAndHandshakeRecoversFromDiscoverError() = runTest(UnconfinedTestDispatcher()) {
        var connected = true
        var failDiscover = true
        val transport = object : BlePeripheralTransport {
            val emitted = MutableSharedFlow<BleTransportEvent>(extraBufferCapacity = 32)
            override val events: Flow<BleTransportEvent> = emitted
            override fun isConnected(identifier: String) = connected
            override fun hasCharacteristic(identifier: String, characteristicUuid: String) = false
            override fun supportsWriteWithResponse(identifier: String, characteristicUuid: String) = false
            override fun initiateWrite(identifier: String, characteristicUuid: String, value: ByteArray) = false
            override fun initiateRead(identifier: String, characteristicUuid: String) = false
            override fun initiateSubscribe(identifier: String, characteristicUuid: String, enable: Boolean) = false
            override fun initiateDiscoverServices(identifier: String): Boolean {
                emitted.tryEmit(BleTransportEvent.ServicesDiscovered(identifier, success = !failDiscover))
                return true
            }
        }
        val o = newOrchestrator(backgroundScope, transport)
        o.onConnectRequested("AA:05")
        transport.emitted.tryEmit(BleTransportEvent.Connected("AA:05"))
        assertEquals(BleSessionPhase.Error, o.registry.get("AA:05")?.phase)
        assertEquals(BleFailureReason.GattFailure, o.liveStatus.statusOf("AA:05")?.lastFailure?.reason)

        // Explicit retry on the connected handle clears failure and restarts discovery.
        failDiscover = false
        o.retryDiscoveryAndHandshake("AA:05")
        assertNull(o.liveStatus.statusOf("AA:05")?.lastFailure)
        kotlin.test.assertNotEquals(BleSessionPhase.Error, o.registry.get("AA:05")?.phase)
    }
}
