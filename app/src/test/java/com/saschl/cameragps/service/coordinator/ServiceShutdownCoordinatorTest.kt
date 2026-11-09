package com.saschl.cameragps.service.coordinator

import com.sasch.cameragps.sharednew.database.devices.CameraDevice
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BOOT / MY_PACKAGE_REPLACED reconnection eligibility. The legacy
 * alwaysOnEnabled flag was zeroed by the Android detail page once its toggle
 * was removed; saved devices must still reconnect after reboot.
 */
class ServiceShutdownCoordinatorTest {
    private fun device(mac: String, enabled: Boolean = true, alwaysOn: Boolean = false) =
        CameraDevice(mac = mac, deviceEnabled = enabled, alwaysOnEnabled = alwaysOn)

    @Test
    fun `saved devices reconnect after boot even when the removed always-on flag was zeroed`() {
        val devices = listOf(device("AA:01", enabled = true, alwaysOn = false))
        assertEquals(devices, bootReconnectTargets(devices))
    }

    @Test
    fun `legacy always-on true devices keep reconnecting after boot`() {
        val devices = listOf(device("AA:01", enabled = true, alwaysOn = true))
        assertEquals(devices, bootReconnectTargets(devices))
    }

    @Test
    fun `disabled devices are skipped`() {
        val saved = device("AA:01", enabled = true)
        val disabled = device("AA:02", enabled = false)
        assertEquals(listOf(saved), bootReconnectTargets(listOf(saved, disabled)))
    }

    @Test
    fun `no saved devices means nothing to reconnect`() {
        assertEquals(emptyList<CameraDevice>(), bootReconnectTargets(emptyList()))
    }
}
