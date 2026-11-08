package com.saschl.cameragps.service

import android.content.Intent
import com.sasch.cameragps.sharednew.bluetooth.SonyBluetoothConstants
import com.sasch.cameragps.sharednew.bluetooth.coordinator.RemoteCommand
import java.util.Locale

class ServiceCommandRouter {
    companion object {
        const val ACTION_DEVICE_DISAPPEARED = "com.saschl.cameragps.ACTION_DEVICE_DISAPPEARED"

        /** Extra holding a [RemoteCommand] enum name for [SonyBluetoothConstants.ACTION_SEND_REMOTE_COMMAND]. */
        const val EXTRA_REMOTE_COMMAND = "remoteCommand"
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

        if (action == SonyBluetoothConstants.ACTION_SEND_REMOTE_COMMAND) {
            val command = RemoteCommand.fromName(intent.getStringExtra(EXTRA_REMOTE_COMMAND))
            return when {
                address == null ->
                    ServiceCommand.Ignore("Remote command ignored because address is missing")

                command == null ->
                    ServiceCommand.Ignore("Remote command ignored because command is missing or unknown")

                else -> ServiceCommand.SendRemoteCommand(address, command)
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

