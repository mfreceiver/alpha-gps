package com.saschl.cameragps.ui

import android.companion.CompanionDeviceManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.no_devices_message
import cameragps.sharednew.generated.resources.no_devices_title
import com.sasch.cameragps.sharednew.bluetooth.BluetoothDeviceInfo
import com.sasch.cameragps.sharednew.ui.device.DeviceDialogRoute
import com.sasch.cameragps.sharednew.ui.device.DeviceDialogs
import com.sasch.cameragps.sharednew.ui.devicelist.DeviceListViewModel
import com.sasch.cameragps.sharednew.ui.devicelist.EmptyStateCard
import com.sasch.cameragps.sharednew.ui.devicelist.SharedDeviceList
import com.saschl.cameragps.AppServices
import com.saschl.cameragps.service.AssociatedDeviceCompat
import com.saschl.cameragps.ui.device.SCREENSHOT_MODE
import com.saschl.cameragps.ui.device.mockDeviceListItems
import com.saschl.cameragps.ui.device.rememberDeviceDetailViewModel
import com.saschl.cameragps.utils.PreferencesManager
import org.jetbrains.compose.resources.stringResource

/**
 * Android host for the shared device list: maps CDM associations to the shared
 * identity model and wires the callbacks back to CDM/service semantics. A row's ⓘ opens the same
 * camera-info dialog as the device page ([DeviceDialogs] on the camera's shared device view model),
 * so rename / keep active / connect·disconnect / remove work here too, connected or not.
 */
@Composable
fun AssociatedDevicesList(
    associatedDevices: List<AssociatedDeviceCompat>,
    deviceManager: CompanionDeviceManager,
    onConnect: (AssociatedDeviceCompat) -> Unit,
    onDisassociate: (AssociatedDeviceCompat) -> Unit,
    onOpenTroubleshooting: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: DeviceListViewModel = viewModel {
        DeviceListViewModel(AndroidDeviceListDataSource(AppServices.from(context)))
    }
    val realItems by viewModel.items.collectAsState()
    val items = if (SCREENSHOT_MODE) mockDeviceListItems else realItems
    // Address of the camera whose info dialog is open (survives rotation; null = closed).
    var infoAddress by rememberSaveable { mutableStateOf<String?>(null) }

    if (associatedDevices.isEmpty()) {
        EmptyStateCard(
            title = stringResource(Res.string.no_devices_title),
            message = stringResource(Res.string.no_devices_message),
        )
        return
    }

    val devices = associatedDevices.map { compat ->
        BluetoothDeviceInfo(
            identifier = compat.address.uppercase(),
            // A name the user chose wins over the association name; CDM is left
            // alone, so its own record keeps whatever the pairing flow set.
            name = items[compat.address.uppercase()]?.customName ?: compat.name,
            // Drives the card's status line; mirrors the old "remote text only
            // while transmitting" gating
            isConnected = items[compat.address.uppercase()]?.isTransmissionActive == true,
            isSaved = true,
            isPaired = compat.isPaired,
        )
    }

    fun resolve(info: BluetoothDeviceInfo): AssociatedDeviceCompat? =
        associatedDevices.firstOrNull { it.address.uppercase() == info.identifier }

    SharedDeviceList(
        devices = devices,
        items = items,
        // Read on every recomposition so a toggle in settings applies on return
        hapticsEnabled = PreferencesManager.isHapticsEnabled(context),
        onConnect = { }, // tap-to-pair path: Android never lists unsaved devices
        onOpenInfo = { info -> infoAddress = resolve(info)?.address },
        onOpenDetails = { info -> resolve(info)?.let(onConnect) },
        onOpenTroubleshooting = onOpenTroubleshooting,
    )

    // A removed (or otherwise vanished) camera closes its dialog with it.
    val infoDevice = infoAddress?.let { address -> associatedDevices.firstOrNull { it.address.equals(address, ignoreCase = true) } }
    if (infoDevice != null) {
        DeviceInfoDialogHost(
            device = infoDevice,
            deviceManager = deviceManager,
            onClosed = { infoAddress = null },
            onDisassociate = onDisassociate,
        )
    } else if (infoAddress != null) {
        LaunchedEffect(infoAddress) { infoAddress = null }
    }
}

@Composable
private fun DeviceInfoDialogHost(
    device: AssociatedDeviceCompat,
    deviceManager: CompanionDeviceManager,
    onClosed: () -> Unit,
    onDisassociate: (AssociatedDeviceCompat) -> Unit,
) {
    val viewModel = rememberDeviceDetailViewModel(device, deviceManager)
    LaunchedEffect(device.address) {
        viewModel.load(device.address, device.name)
        viewModel.refreshCameraSettings(device.address)
    }
    // Opening the host means "show the info dialog"; leaving every dialog closes the host.
    var route by rememberSaveable(device.address) { mutableStateOf(DeviceDialogRoute.Info) }
    val resolvedName = viewModel.uiState.collectAsState().value.deviceName.ifEmpty { device.name }
    DeviceDialogs(
        route = route,
        onRoute = { next ->
            route = next
            if (next == DeviceDialogRoute.None) onClosed()
        },
        viewModel = viewModel,
        deviceId = device.address,
        deviceName = resolvedName,
        onDelete = { onDisassociate(device) },
    )
}
