package com.saschl.cameragps.ui.device

import android.content.Context
import android.content.Intent
import com.sasch.cameragps.sharednew.bluetooth.SonyBluetoothConstants
import com.sasch.cameragps.sharednew.ui.device.DeviceDetailServiceActions
import com.saschl.cameragps.service.LocationSenderService
import com.saschl.cameragps.service.ServiceCommandRouter
import timber.log.Timber

class AndroidDeviceDetailServiceActions(
    private val context: Context,
    /** Starts/stops the CompanionDeviceManager presence observation of this camera. */
    private val observePresence: (enabled: Boolean) -> Unit = {},
) : DeviceDetailServiceActions {

    override fun connect(deviceAddress: String) {
        // An explicit user Connect takes the same service path as the automatic ones; the service
        // re-reads the (just re-enabled) flag before it connects.
        startAlwaysOn(deviceAddress)
    }

    override fun disconnect(deviceAddress: String) {
        val intent = Intent(context, LocationSenderService::class.java).apply {
            action = ServiceCommandRouter.ACTION_DISCONNECT_DEVICE
            putExtra("address", deviceAddress.uppercase())
        }
        // The service ignores it (and ends) when no foreground session exists, so nothing is
        // promoted just to disconnect.
        runCatching { context.startService(intent) }
            .onFailure { Timber.w(it, "Could not deliver disconnect for $deviceAddress") }
    }

    override fun setPresenceObservation(deviceAddress: String, enabled: Boolean) {
        runCatching { observePresence(enabled) }
            .onFailure { Timber.w(it, "Presence observation change failed for $deviceAddress") }
    }

    override fun startAlwaysOn(deviceAddress: String) {
        if (!LocationSenderService.hasLocationPermission(context)) {
            Timber.w("Location permission missing, not starting always-on for $deviceAddress")
            return
        }
        val normalized = deviceAddress.uppercase()
        val intent = Intent(context, LocationSenderService::class.java).apply {
            putExtra("address", normalized)
        }
        context.startForegroundService(intent)
    }

    override fun requestShutdown(deviceAddress: String) {
        val normalized = deviceAddress.uppercase()
        val shutdownIntent = Intent(context, LocationSenderService::class.java).apply {
            action = SonyBluetoothConstants.ACTION_REQUEST_SHUTDOWN
            putExtra("address", normalized)
        }
        context.startService(shutdownIntent)
    }
}
