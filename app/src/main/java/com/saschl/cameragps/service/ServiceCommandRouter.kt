package com.saschl.cameragps.service

import android.content.Intent
import com.sasch.cameragps.sharednew.bluetooth.SonyBluetoothConstants
import java.util.Locale

class ServiceCommandRouter {
    companion object {
        const val ACTION_DEVICE_DISAPPEARED = "com.saschl.cameragps.ACTION_DEVICE_DISAPPEARED"
        const val ACTION_DISCONNECT_DEVICE = "com.saschl.cameragps.ACTION_DISCONNECT_DEVICE"
    }

    fun route(intent: Intent?): ServiceCommand {
        val action = intent?.action
        val address = intent?.getStringExtra("address")?.uppercase(Locale.getDefault())

        if (action == ACTION_DEVICE_DISAPPEARED) {
            return if (address == null) {
                ServiceCommand.Ignore("Device disappearance ignored because address is missing")
            } else {
                ServiceCommand.DeviceDisappeared(address)
            }
        }

        if (action == ACTION_DISCONNECT_DEVICE) {
            return if (address == null) {
                ServiceCommand.Ignore("Disconnect ignored because address is missing")
            } else {
                ServiceCommand.Disconnect(address)
            }
        }

        if (action == SonyBluetoothConstants.ACTION_REQUEST_SHUTDOWN) {
            return if (address == null) {
                ServiceCommand.Ignore("Shutdown request ignored because address is missing")
            } else {
                ServiceCommand.Shutdown(address)
            }
        }

        return if (address == null) {
            ServiceCommand.ReconnectAlwaysOn
        } else {
            ServiceCommand.Connect(address)
        }
    }
}

