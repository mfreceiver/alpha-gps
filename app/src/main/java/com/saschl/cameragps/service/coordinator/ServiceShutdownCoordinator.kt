package com.saschl.cameragps.service.coordinator

import android.annotation.SuppressLint
import com.sasch.cameragps.sharednew.database.devices.CameraDevice
import com.sasch.cameragps.sharednew.database.devices.CameraDeviceDAO
import com.saschl.cameragps.service.transport.AndroidBleTransport
import timber.log.Timber

class ServiceShutdownCoordinator(
    private val deviceDao: CameraDeviceDAO,
    private val transport: AndroidBleTransport,
    private val onShutdownRequested: (Int?) -> Unit,
) {
    /**
     * No explicit address: triggered by BOOT_COMPLETED / MY_PACKAGE_REPLACED.
     * Every saved, enabled device qualifies for automatic reconnection. The
     * legacy alwaysOnEnabled flag is deliberately not consulted: the Android
     * detail page no longer exposes it, and re-normalizing it there must not
     * silently revoke the accepted "connect after boot" behavior.
     */
    @SuppressLint("MissingPermission")
    suspend fun handleNoAddress(startId: Int) {
        if (deviceDao.getEnabledDeviceCount() == 0) {
            Timber.i("No saved devices found, shutting down service")
            onShutdownRequested(startId)
            return
        }

        runCatching {
            bootReconnectTargets(deviceDao.getAllCameraDevices())
                .forEach { device ->
                    runCatching {
                        transport.connect(device.mac)
                    }.onFailure { handleGattConnectionFailure(startId, device) }
                }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleGattConnectionFailure(startId: Int, cameraDevice: CameraDevice) {
        Timber.e("Failed to connect to device ${cameraDevice.deviceName}, bluetooth is likely disabled")
    }

    @SuppressLint("MissingPermission")
    suspend fun handleShutdownRequest(address: String, startId: Int) {
        Timber.i("Shutdown requested for device $address")

        if (address == "all") {
            handleShutdownAllDevices(startId)
            return
        }

        // Explicit stop requests are user actions; saved devices are paused too.
        transport.pauseDevice(address)
        if (transport.connectedCount() == 0 && deviceDao.getEnabledDeviceCount() == 0) {
            Timber.d("No connected or saved cameras remaining, shutting down service")
            onShutdownRequested(startId)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleShutdownAllDevices(startId: Int) {
        if (deviceDao.getEnabledDeviceCount() == 0) {
            Timber.i("No saved devices found, disconnecting all cameras and shutting down service")
            transport.disconnectAll()
            onShutdownRequested(startId)
        } else {
            Timber.i("At least one saved device found, not shutting down service")
        }
    }
}

/** Devices eligible for automatic reconnection after BOOT / package replacement. */
internal fun bootReconnectTargets(devices: List<CameraDevice>): List<CameraDevice> =
    devices.filter { it.deviceEnabled }
