package com.saschl.cameragps.service.coordinator

import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.location.GeoLocation
import com.sasch.cameragps.sharednew.bluetooth.location.LocationSource
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionOrchestrator
import com.sasch.cameragps.sharednew.bluetooth.session.OrchestratorEvent
import com.sasch.cameragps.sharednew.bluetooth.transport.BlePeripheralTransport
import com.sasch.cameragps.sharednew.bluetooth.transport.BleTransportEvent
import com.saschl.cameragps.service.transport.GattConnectionRegistry
import com.sasch.cameragps.sharednew.database.devices.CameraDevice
import com.sasch.cameragps.sharednew.database.devices.CameraDeviceDAO
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration regression for the presence-sweep retirement of a GATT handle whose connect was
 * still pending — it never delivered any Disconnected, so retiring it must end the stuck
 * `Connecting` session above (via the synthetic Disconnected the transport emits), while the
 * other camera's handle and the service survive.
 *
 * Wires the real [GattConnectionRegistry], [ServicePresenceCoordinator] and
 * [CameraSessionOrchestrator]; only the Android Bluetooth layer is replaced by the fake
 * transport, whose `retireParkedHandle` wiring mirrors `AndroidBleTransport.retireParkedHandle`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingHandleRetirementTest {

    @Test
    fun `sweep retires a never-connected pending handle, ends its Connecting session, keeps the other camera`() {
        val orchestratorThread = newSingleThreadContext("orchestrator")
        try {
            val fixture = Fixture(orchestratorThread)

            // Camera A: Connect requested, handle registered, but the stack has not delivered
            // any connection-state callback yet (link still pending, never disconnected).
            fixture.connectRequested(CAMERA)
            assertEquals(BleSessionPhase.Connecting, fixture.session(CAMERA).phase)

            // Camera B keeps the service alive with its own live link (connection demand).
            fixture.connectRequested(OTHER_CAMERA)
            fixture.linkEstablished(OTHER_CAMERA)

            // CDM disappearance of A triggers the presence sweep.
            runBlocking { fixture.presence.onDeviceDisappeared(CAMERA) }

            // A's handle is released and its session left Connecting for good (removable row).
            fixture.onOrchestratorThread {
                assertEquals(null, fixture.orchestrator.registry.get(CAMERA))
            }
            assertFalse(fixture.registry.has(CAMERA))
            // B is untouched and the service was never asked to stop.
            assertTrue(fixture.registry.has(OTHER_CAMERA))
            assertEquals(0, fixture.shutdowns)

            // A can connect again: fresh session, fresh handle.
            fixture.connectRequested(CAMERA)
            assertEquals(BleSessionPhase.Connecting, fixture.session(CAMERA).phase)
            assertTrue(fixture.registry.has(CAMERA))
        } finally {
            orchestratorThread.close()
        }
    }

    @Test
    fun `sweep retire of a parked handle that already reported its disconnect adds no synthetic event`() {
        val orchestratorThread = newSingleThreadContext("orchestrator")
        try {
            val fixture = Fixture(orchestratorThread)

            fixture.connectRequested(CAMERA)
            fixture.linkEstablished(CAMERA)
            // On the orchestrator thread: the collector launched in the Fixture's init is
            // subscribed before this real Disconnected is emitted, so the count below is exact.
            fixture.onOrchestratorThread { fixture.linkDropped(CAMERA) }
            fixture.onOrchestratorThread {
                // The real Disconnected already ended the session.
                assertEquals(null, fixture.orchestrator.registry.get(CAMERA))
            }

            runBlocking { fixture.presence.onDeviceDisappeared(CAMERA) }

            assertFalse(fixture.registry.has(CAMERA))
            // No synthetic second Disconnected: exactly one device-disconnect event total.
            fixture.onOrchestratorThread {
                assertEquals(listOf(CAMERA), fixture.deviceDisconnectedEvents)
            }
            // Single absent camera with its handle retired: the sweep may stop the service.
            assertEquals(1, fixture.shutdowns)
        } finally {
            orchestratorThread.close()
        }
    }

    private class Fixture(thread: CoroutineContext) {
        val registry = GattConnectionRegistry<Any>()
        val gatts = mutableMapOf<String, Any>()
        val channel = Channel<BleTransportEvent>(Channel.UNLIMITED)
        var shutdowns = 0
        val deviceDisconnectedEvents = mutableListOf<String>()

        val transport = object : BlePeripheralTransport {
            override val events: Flow<BleTransportEvent> = channel.receiveAsFlow()
            override fun isConnected(identifier: String) = false
            override fun hasCharacteristic(identifier: String, characteristicUuid: String) = false
            override fun supportsWriteWithResponse(identifier: String, characteristicUuid: String) = false
            override fun initiateWrite(identifier: String, characteristicUuid: String, value: ByteArray) = false
            override fun initiateRead(identifier: String, characteristicUuid: String) = false
            override fun initiateSubscribe(identifier: String, characteristicUuid: String, enable: Boolean) = false
            override fun initiateDiscoverServices(identifier: String) = false
        }

        val scope = CoroutineScope(thread + SupervisorJob())

        val orchestrator =
            CameraSessionOrchestrator(transport, FakeSource(), FakeDao(), scope).also { it.start() }

        init {
            scope.launch {
                orchestrator.events.collect { event ->
                    if (event is OrchestratorEvent.DeviceDisconnected) {
                        deviceDisconnectedEvents += event.identifier
                    }
                }
            }
        }

        val presence = ServicePresenceCoordinator(
            hasConnectedDevices = { registry.size > 0 },
            hasAlwaysOnDevices = { false },
            isHandleParked = { address -> registry.entry(address.uppercase())?.isActive == false },
            isDeviceKeepActive = { false },
            retireParkedHandle = { address ->
                // Mirrors AndroidBleTransport.retireParkedHandle: a handle that never reported
                // a disconnect owes the session a synthetic Disconnected.
                val entry = registry.retireIfParked(address.uppercase())
                if (entry != null && !entry.disconnectedReported) {
                    channel.trySend(BleTransportEvent.Disconnected(address.uppercase(), statusCode = null))
                }
            },
            onShutdownRequested = { shutdowns++ },
        )

        /** Connect command: upper session enters Connecting, transport registers a pending handle. */
        fun connectRequested(id: String) {
            orchestrator.onConnectRequested(id)
            val gatt = Any()
            check(registry.register(id, gatt))
            gatts[id] = gatt
        }

        /** GATT Connected of a pending handle: the link is live (no further events emulated). */
        fun linkEstablished(id: String) {
            registry.update(gatts.getValue(id), id) { it.isActive = true }
        }

        /** GATT Disconnected: handle parks on autoConnect and reports its disconnect. */
        fun linkDropped(id: String) {
            registry.update(gatts.getValue(id), id) {
                it.isActive = false
                it.disconnectedReported = true
            }
            channel.trySend(BleTransportEvent.Disconnected(id, statusCode = 8))
        }

        fun session(id: String) = orchestrator.registry.get(id)!!

        /** Runs [block] after every event queued so far was processed by the orchestrator. */
        fun onOrchestratorThread(block: () -> Unit) = runBlocking {
            scope.launch { block() }.join()
        }
    }

    private class FakeSource : LocationSource {
        override val locations = emptyFlow<GeoLocation>()
        var active = false
        override fun start(): Boolean {
            active = true; return true
        }

        override fun stop() {
            active = false
        }

        override fun hasPreciseAuthorization() = true
    }

    private class FakeDao : CameraDeviceDAO {
        override suspend fun getAllCameraDevices() = emptyList<CameraDevice>()
        override fun observeAllDevices() = flowOf(emptyList<CameraDevice>())
        override suspend fun insertDevice(device: CameraDevice) = Unit
        override suspend fun setDeviceName(deviceId: String, name: String, isCustom: Boolean) = Unit
        override suspend fun getDeviceName(address: String): String? = null
        override suspend fun deleteDevice(device: CameraDevice) = Unit
        override suspend fun setDeviceEnabled(deviceId: String, enabled: Boolean) = Unit
        override suspend fun isDeviceAlwaysOnEnabled(address: String) = false
        override suspend fun setAlwaysOnEnabled(deviceId: String, enabled: Boolean) = Unit
        override suspend fun isDeviceEnabled(address: String) = true
        override suspend fun findDeviceEnabled(address: String): Boolean? = true
        override suspend fun getAlwaysOnEnabledDeviceCount() = 0
        override suspend fun getKeepActiveDeviceCount() = 0
        override suspend fun getEnabledDeviceCount() = 0
        override suspend fun setRemoteControlEnabled(deviceId: String, enabled: Boolean) = 0
        override suspend fun isRemoteControlEnabled(address: String) = false
    }

    private companion object {
        const val CAMERA = "AA:BB:CC:DD:EE:01"
        const val OTHER_CAMERA = "AA:BB:CC:DD:EE:02"
    }
}
