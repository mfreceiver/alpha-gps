package com.saschl.cameragps.service.coordinator

import com.sasch.cameragps.sharednew.database.devices.CameraDevice
import com.sasch.cameragps.sharednew.database.devices.CameraDeviceDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: the removal flow used to send the shutdown request before the
 * Room delete was committed; the service then read an enabled-device count one
 * too high and never stopped for an empty device list. The coordinator must
 * guarantee the delete is visible before any shutdown judgement runs.
 */
class DeviceRemovalCoordinatorTest {

    private class FakeDao(devices: List<CameraDevice>) : CameraDeviceDAO {
        val table = devices.toMutableList()
        val events = mutableListOf<String>()

        override suspend fun getAllCameraDevices(): List<CameraDevice> = table.toList()

        override fun observeAllDevices(): Flow<List<CameraDevice>> = MutableStateFlow(table.toList())

        override suspend fun insertDevice(device: CameraDevice) {
            table += device
        }

        override suspend fun setDeviceName(deviceId: String, name: String, isCustom: Boolean) = Unit

        override suspend fun getDeviceName(address: String): String? =
            table.firstOrNull { it.mac == address.uppercase() }?.deviceName

        override suspend fun deleteDevice(device: CameraDevice) {
            events += "delete:${device.mac}"
            table.removeAll { it.mac == device.mac.uppercase() }
        }

        override suspend fun setDeviceEnabled(deviceId: String, enabled: Boolean) = Unit

        override suspend fun isDeviceAlwaysOnEnabled(address: String) = false

        override suspend fun setAlwaysOnEnabled(deviceId: String, enabled: Boolean) = Unit

        override suspend fun isDeviceEnabled(address: String): Boolean =
            table.firstOrNull { it.mac == address.uppercase() }?.deviceEnabled ?: false

        override suspend fun findDeviceEnabled(address: String): Boolean? =
            table.firstOrNull { it.mac == address.uppercase() }?.deviceEnabled

        override suspend fun getAlwaysOnEnabledDeviceCount(): Int = 0

        override suspend fun getEnabledDeviceCount(): Int = table.count { it.deviceEnabled }

        override suspend fun setRemoteControlEnabled(deviceId: String, enabled: Boolean): Int = 0

        override suspend fun isRemoteControlEnabled(address: String): Boolean = false
    }

    private fun device(mac: String) = CameraDevice(mac = mac, deviceEnabled = true)

    @Test
    fun `shutdown judgement sees the committed delete of the last device`() = runBlocking {
        val dao = FakeDao(listOf(device("AA:01")))
        var stopServiceDecision: Boolean? = null
        val coordinator = DeviceRemovalCoordinator(dao) { _ ->
            dao.events += "shutdown-request"
            // The service's judgement, read at request time — exactly where it
            // used to race ahead of the un-committed delete and see count = 1.
            stopServiceDecision = dao.getEnabledDeviceCount() == 0
        }

        coordinator.remove(device("AA:01"))

        assertEquals(listOf("delete:AA:01", "shutdown-request"), dao.events)
        assertEquals("service must stop once the last device is deleted", true, stopServiceDecision)
    }

    @Test
    fun `removing one of several devices does not stop the service`() = runBlocking {
        val dao = FakeDao(listOf(device("AA:01"), device("AA:02")))
        var stopServiceDecision: Boolean? = null
        val coordinator = DeviceRemovalCoordinator(dao) { _ ->
            stopServiceDecision = dao.getEnabledDeviceCount() == 0
        }

        coordinator.remove(device("AA:01"))

        assertTrue("remaining device must keep the enabled count above zero", dao.getEnabledDeviceCount() == 1)
        assertEquals("service must not stop while another enabled device remains", false, stopServiceDecision)
    }
}
