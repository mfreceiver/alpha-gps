package com.saschl.cameragps.service.coordinator

import com.sasch.cameragps.sharednew.database.devices.CameraDevice
import com.sasch.cameragps.sharednew.database.devices.CameraDeviceDAO

/**
 * Serializes device removal: the Room delete must be committed before the
 * shutdown judgement runs. The service's shutdown coordinator decides from the
 * current enabled-device count; if the delete were still in flight (or only
 * queued behind it), the count read one device too high, the service stayed
 * alive, and an empty device list kept its foreground service and GATT
 * connections. [AndroidBleTransport.pauseDevice] does not close GATT, so
 * nothing else would clean up afterwards.
 */
class DeviceRemovalCoordinator(
    private val deviceDao: CameraDeviceDAO,
    /** Fires the shutdown request for the removed device (address, upper-cased). */
    private val requestShutdown: suspend (String) -> Unit,
) {
    suspend fun remove(device: CameraDevice) {
        deviceDao.deleteDevice(device)
        // Only sent once the delete above is persisted: the receiver of this
        // request re-reads the device table and must see the removal.
        requestShutdown(device.mac.uppercase())
    }
}
