package com.saschl.cameragps.service.coordinator

import com.saschl.cameragps.service.transport.GattConnectionRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicePresenceCoordinatorTest {
    @Test
    fun `camera can stop advertising and keep transmitting until GATT disconnects`() = runBlocking {
        val fixture = Fixture()
        fixture.connect(CAMERA, active = true)

        repeat(3) { fixture.coordinator.onDeviceDisappeared(CAMERA) }
        assertEquals(0, fixture.shutdowns)
        assertTrue(fixture.hasHandle(CAMERA))

        fixture.disconnect(CAMERA)
        fixture.coordinator.onDeviceDisconnected(CAMERA)
        assertEquals(1, fixture.shutdowns)
    }

    @Test
    fun `GATT disconnect before disappearance waits for presence loss`() = runBlocking {
        val fixture = Fixture()
        fixture.connect(CAMERA, active = true)

        fixture.disconnect(CAMERA)
        fixture.coordinator.onDeviceDisconnected(CAMERA)
        assertEquals(0, fixture.shutdowns)

        fixture.coordinator.onDeviceDisappeared(CAMERA)
        assertEquals(1, fixture.shutdowns)
    }

    @Test
    fun `disappearance without an established connection still stops an idle service`() =
        runBlocking {
            val fixture = Fixture()
            fixture.coordinator.onDeviceAppeared(CAMERA)

            fixture.coordinator.onDeviceDisappeared(CAMERA)

            assertEquals(1, fixture.shutdowns)
        }

    @Test
    fun `always on preserves reconnect after both disappearance and disconnect`() = runBlocking {
        val fixture = Fixture()
        fixture.connect(CAMERA, active = true)
        fixture.setKeepActive(CAMERA, true)
        fixture.coordinator.onDeviceDisappeared(CAMERA)

        fixture.disconnect(CAMERA)
        fixture.coordinator.onDeviceDisconnected(CAMERA)
        fixture.coordinator.onDeviceDisappeared(CAMERA)

        assertEquals(0, fixture.shutdowns)
        // The keep-active camera's parked handle is preserved for autoConnect.
        assertTrue(fixture.hasHandle(CAMERA))
    }

    @Test
    fun `another connected camera keeps service alive until its own disconnect`() = runBlocking {
        val fixture = Fixture()
        fixture.connect(CAMERA, active = true)
        fixture.connect(OTHER_CAMERA, active = true)
        fixture.coordinator.onDeviceDisappeared(CAMERA)
        fixture.coordinator.onDeviceDisappeared(OTHER_CAMERA)

        fixture.disconnect(CAMERA)
        fixture.coordinator.onDeviceDisconnected(CAMERA)
        assertEquals(0, fixture.shutdowns)

        fixture.disconnect(OTHER_CAMERA)
        fixture.coordinator.onDeviceDisconnected(OTHER_CAMERA)
        assertEquals(1, fixture.shutdowns)
    }

    @Test
    fun `other camera with presence keeps its reconnect opportunity after first camera leaves`() =
        runBlocking {
            val fixture = Fixture()
            fixture.connect(CAMERA, active = true)
            fixture.connect(OTHER_CAMERA, active = true)
            fixture.coordinator.onDeviceAppeared(OTHER_CAMERA)
            fixture.coordinator.onDeviceDisappeared(CAMERA)
            fixture.disconnect(CAMERA)
            fixture.coordinator.onDeviceDisconnected(CAMERA)

            fixture.disconnect(OTHER_CAMERA)
            fixture.coordinator.onDeviceDisconnected(OTHER_CAMERA)
            assertEquals(0, fixture.shutdowns)
            // Present (not disappeared), so its parked handle survives the retire sweep.
            assertTrue(fixture.hasHandle(OTHER_CAMERA))

            fixture.coordinator.onDeviceDisappeared(OTHER_CAMERA)
            assertEquals(1, fixture.shutdowns)
        }

    @Test
    fun `reappearance clears prior disappearance regardless of address case`() = runBlocking {
        val fixture = Fixture()
        fixture.connect(CAMERA, active = true)
        fixture.coordinator.onDeviceDisappeared(CAMERA.lowercase())
        fixture.coordinator.onDeviceAppeared(CAMERA)
        fixture.disconnect(CAMERA)

        fixture.coordinator.onDeviceDisconnected(CAMERA.lowercase())

        assertEquals(0, fixture.shutdowns)
    }

    @Test
    fun `disconnect matches a disappearance reported with different address case`() = runBlocking {
        val fixture = Fixture()
        fixture.connect(CAMERA, active = true)
        fixture.coordinator.onDeviceDisappeared(CAMERA.lowercase())
        fixture.disconnect(CAMERA)

        fixture.coordinator.onDeviceDisconnected(CAMERA)

        assertEquals(1, fixture.shutdowns)
    }

    @Test
    fun `delayed disconnect cannot stop an already reconnected camera`() = runBlocking {
        val fixture = Fixture()
        fixture.connect(CAMERA, active = true)
        fixture.coordinator.onDeviceDisappeared(CAMERA)

        fixture.reconnect(CAMERA)
        fixture.coordinator.onDeviceDisconnected(CAMERA)

        assertEquals(0, fixture.shutdowns)
        assertTrue(fixture.hasHandle(CAMERA))
    }

    @Test
    fun `GATT reconnect during database lookup prevents shutdown`() = runBlocking {
        val fixture = Fixture()
        fixture.connect(CAMERA, active = true)
        val resumeLookup = CompletableDeferred<Unit>()
        fixture.beforeAlwaysOnCheck = { resumeLookup.await() }
        val disappearance = launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.coordinator.onDeviceDisappeared(CAMERA)
        }

        fixture.reconnect(CAMERA)
        resumeLookup.complete(Unit)
        disappearance.join()

        assertEquals(0, fixture.shutdowns)
    }

    // ---- parked-handle retirement (keep-active off default): docs/todo.md presence policy ----

    @Test
    fun `single non-keep-active camera may stop after disappearance then GATT disconnect`() =
        runBlocking {
            val fixture = Fixture()
            fixture.connect(CAMERA, active = true)

            fixture.coordinator.onDeviceDisappeared(CAMERA)
            // Still linked: alive, and the retire sweep must not touch an active handle.
            assertEquals(0, fixture.shutdowns)
            assertTrue(fixture.hasHandle(CAMERA))

            fixture.disconnect(CAMERA)
            fixture.coordinator.onDeviceDisconnected(CAMERA)
            assertEquals(1, fixture.shutdowns)
        }

    @Test
    fun `single non-keep-active camera may stop after GATT disconnect then disappearance`() =
        runBlocking {
            val fixture = Fixture()
            fixture.connect(CAMERA, active = true)

            fixture.disconnect(CAMERA)
            fixture.coordinator.onDeviceDisconnected(CAMERA)
            assertEquals(0, fixture.shutdowns)

            fixture.coordinator.onDeviceDisappeared(CAMERA)
            assertEquals(1, fixture.shutdowns)
        }

    @Test
    fun `pending or active other camera keeps the service when the first camera leaves`() =
        runBlocking {
            val fixture = Fixture()
            fixture.connect(CAMERA, active = true)
            fixture.connect(OTHER_CAMERA, active = true)

            fixture.coordinator.onDeviceDisappeared(CAMERA)
            fixture.disconnect(CAMERA)
            fixture.coordinator.onDeviceDisconnected(CAMERA)
            assertEquals(0, fixture.shutdowns)

            // The other camera degrades to a pending (parked) connect that was never reported
            // absent: its handle still counts as demand — no shutdown from the first camera's exit.
            fixture.disconnect(OTHER_CAMERA)
            fixture.coordinator.onDeviceDisconnected(OTHER_CAMERA)
            assertEquals(0, fixture.shutdowns)
            assertTrue(fixture.hasHandle(OTHER_CAMERA))

            fixture.coordinator.onDeviceDisappeared(CAMERA)
            assertEquals(0, fixture.shutdowns)
            assertTrue(fixture.hasHandle(OTHER_CAMERA))
        }

    @Test
    fun `all non-keep-active cameras leaving lets the service stop`() = runBlocking {
        val fixture = Fixture()
        fixture.connect(CAMERA, active = true)
        fixture.connect(OTHER_CAMERA, active = true)

        fixture.coordinator.onDeviceDisappeared(CAMERA)
        fixture.coordinator.onDeviceDisappeared(OTHER_CAMERA)
        assertEquals(0, fixture.shutdowns)

        fixture.disconnect(CAMERA)
        fixture.coordinator.onDeviceDisconnected(CAMERA)
        assertEquals(0, fixture.shutdowns)

        fixture.disconnect(OTHER_CAMERA)
        fixture.coordinator.onDeviceDisconnected(OTHER_CAMERA)
        assertEquals(1, fixture.shutdowns)
        assertFalse(fixture.hasHandle(CAMERA))
        assertFalse(fixture.hasHandle(OTHER_CAMERA))
    }

    @Test
    fun `keep-active camera keeps the service and its parked handle while away`() = runBlocking {
        val fixture = Fixture()
        fixture.connect(CAMERA, active = true)
        fixture.setKeepActive(CAMERA, true)

        fixture.coordinator.onDeviceDisappeared(CAMERA)
        fixture.disconnect(CAMERA)
        fixture.coordinator.onDeviceDisconnected(CAMERA)

        assertEquals(0, fixture.shutdowns)
        assertTrue(fixture.hasHandle(CAMERA))
    }

    /**
     * Real [GattConnectionRegistry] wiring: handles live in the registry exactly as in
     * production, and the coordinator's probes read (and retire through) it — no mocked
     * connected set. A distinct object per address stands in for the BluetoothGatt.
     */
    private class Fixture {
        private val registry = GattConnectionRegistry<Any>()
        private val gatts = mutableMapOf<String, Any>()
        private val keepActive = mutableSetOf<String>()
        var shutdowns = 0
        var beforeAlwaysOnCheck: (suspend () -> Unit)? = null

        val coordinator = ServicePresenceCoordinator(
            hasConnectedDevices = { registry.size > 0 },
            hasAlwaysOnDevices = {
                beforeAlwaysOnCheck?.invoke()
                keepActive.isNotEmpty()
            },
            isHandleParked = { address ->
                registry.entry(address.uppercase())?.isActive == false
            },
            isDeviceKeepActive = { address -> address.uppercase() in keepActive },
            retireParkedHandle = { address ->
                registry.retireIfParked(address.uppercase())
            },
            onShutdownRequested = { shutdowns++ },
        )

        fun setKeepActive(address: String, enabled: Boolean) {
            if (enabled) keepActive.add(address.uppercase()) else keepActive.remove(address.uppercase())
        }

        fun hasHandle(address: String): Boolean = registry.has(address.uppercase())

        /** connect(): registers a handle; pending connects start inactive, live links active. */
        fun connect(address: String, active: Boolean) {
            val id = address.uppercase()
            val gatt = Any()
            check(registry.register(id, gatt))
            gatts[id] = gatt
            if (active) registry.update(gatt, id) { it.isActive = true }
        }

        /** GATT Disconnected: the handle parks on autoConnect (stays registered, inactive). */
        fun disconnect(address: String) {
            val id = address.uppercase()
            registry.update(gatts.getValue(id), id) { it.isActive = false }
        }

        /** GATT Connected of the parked handle: active again. */
        fun reconnect(address: String) {
            val id = address.uppercase()
            registry.update(gatts.getValue(id), id) { it.isActive = true }
        }
    }

    private companion object {
        const val CAMERA = "AA:BB:CC:DD:EE:01"
        const val OTHER_CAMERA = "AA:BB:CC:DD:EE:02"
    }
}
