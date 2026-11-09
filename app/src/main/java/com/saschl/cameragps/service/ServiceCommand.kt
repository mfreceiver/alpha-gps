package com.saschl.cameragps.service

import com.sasch.cameragps.sharednew.bluetooth.coordinator.RemoteCommand

sealed interface ServiceCommand {
    data class Connect(val address: String) : ServiceCommand
    data class Shutdown(val address: String) : ServiceCommand
    /** The user manually disconnected this camera: close its link for real. */
    data class Disconnect(val address: String) : ServiceCommand
    data class DeviceDisappeared(val address: String) : ServiceCommand
    data class SendRemoteCommand(val address: String, val command: RemoteCommand) : ServiceCommand
    data object ReconnectAlwaysOn : ServiceCommand
    data class Ignore(val reason: String) : ServiceCommand
}

