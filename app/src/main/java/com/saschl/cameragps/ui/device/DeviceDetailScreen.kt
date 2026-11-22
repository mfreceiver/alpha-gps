package com.saschl.cameragps.ui.device

import android.Manifest
import android.companion.CompanionDeviceManager
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.annotation.RequiresPermission
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.arrow_back_24px
import cameragps.sharednew.generated.resources.back
import cameragps.sharednew.generated.resources.device_info_title
import cameragps.sharednew.generated.resources.info_24px
import com.sasch.cameragps.sharednew.bluetooth.coordinator.GpsRecoveryBaseline
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTopBar
import com.sasch.cameragps.sharednew.ui.components.glass.LocalGlassOverlayHost
import com.sasch.cameragps.sharednew.ui.device.DeviceConnectionStatus
import com.sasch.cameragps.sharednew.ui.device.DeviceDetailContent
import com.sasch.cameragps.sharednew.ui.device.DeviceDialogRoute
import com.sasch.cameragps.sharednew.ui.device.DeviceDialogs
import com.sasch.cameragps.sharednew.ui.device.deviceConnectionStatus
import com.sasch.cameragps.sharednew.ui.device.rememberDeviceDialogRoute
import com.sasch.cameragps.sharednew.ui.help.GuideTopic
import com.sasch.cameragps.sharednew.ui.remote.RemoteControlChooserScreen
import com.sasch.cameragps.sharednew.ui.remote.RemoteRoute
import com.sasch.cameragps.sharednew.ui.remote.remoteRouteOf
import com.saschl.cameragps.service.AssociatedDeviceCompat
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Device page host (design v3): title = camera name, back and Ⓘ as icon buttons on a transparent
 * [GlassTopBar] over the app's [com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground]. Back only
 * leaves the page; Bluetooth stays as it is (a disconnect is an explicit, confirmed action).
 */
@Composable
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun DeviceDetailScreen(
    device: AssociatedDeviceCompat,
    deviceManager: CompanionDeviceManager,
    onDisassociate: (device: AssociatedDeviceCompat) -> Unit,
    associationId: Int,
    onClose: () -> Unit,
    onOpenTroubleshooting: (GuideTopic?) -> Unit = {},
) {
    var wifiRemoteOpen by rememberSaveable(device.address) { mutableStateOf(false) }
    // R1/R2 remote shooting: in-page routes (the picker never connects by itself).
    var remoteRoute by rememberSaveable(device.address) { mutableStateOf(RemoteRoute.None.name) }
    var gpsRecoveryBaseline by remember(device.address) { mutableStateOf<GpsRecoveryBaseline?>(null) }
    val viewModel = rememberDeviceDetailViewModel(device, deviceManager)
    val resolvedName = viewModel.uiState.collectAsState().value.deviceName.ifEmpty { device.name }
    if (wifiRemoteOpen) {
        AndroidWifiRemoteScreen(device.address, onClose = { wifiRemoteOpen = false })
        return
    }
    when (remoteRouteOf(remoteRoute)) {
        RemoteRoute.None -> Unit
        RemoteRoute.Chooser -> {
            val sessions by viewModel.sessions.collectAsState()
            RemoteControlChooserScreen(
                bluetoothReady = deviceConnectionStatus(sessions[device.address.uppercase()]) == DeviceConnectionStatus.Connected,
                onBle = { remoteRoute = RemoteRoute.Ble.name },
                onWifi = { remoteRoute = RemoteRoute.Wifi.name },
                onBack = { remoteRoute = RemoteRoute.None.name },
            )
            return
        }
        RemoteRoute.Ble -> {
            AndroidBleShutterScreen(
                identifier = device.address,
                deviceName = device.name,
                isCustomName = resolvedName != device.name,
                onClose = { remoteRoute = RemoteRoute.None.name },
                onLeft = { gpsRecoveryBaseline = it },
                onOpenGuide = { onOpenTroubleshooting(it) },
            )
            return
        }
        RemoteRoute.Wifi -> {
            AndroidWifiRemoteControlScreen(device.address, onClose = { remoteRoute = RemoteRoute.Chooser.name })
            return
        }
    }
    var dialogRoute by rememberDeviceDialogRoute(device.address)
    val overlayHost = LocalGlassOverlayHost.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = resolvedName,
                navigationIcon = {
                    GlassIconButton(
                        icon = painterResource(Res.drawable.arrow_back_24px),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onClose,
                    )
                },
                actions = {
                    // Rename, keep-active and remove live inside the Ⓘ dialog.
                    GlassIconButton(
                        icon = painterResource(Res.drawable.info_24px),
                        contentDescription = stringResource(Res.string.device_info_title),
                        onClick = { dialogRoute = DeviceDialogRoute.Info },
                    )
                },
            )
        },
    ) { innerPadding ->
        // An open in-tree dialog handles Back itself (closes the dialog, not the page).
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = overlayHost?.hasOverlay != true,
            onBackCompleted = onClose,
        )

        DeviceDetailContent(
            onWifiRemote = { wifiRemoteOpen = true },
            onRemoteControl = { remoteRoute = RemoteRoute.Chooser.name },
            gpsRecoveryBaseline = gpsRecoveryBaseline,
            onGpsRecoveryResolved = { gpsRecoveryBaseline = null },
            onOpenTroubleshooting = onOpenTroubleshooting,
            viewModel = viewModel,
            deviceId = device.address,
            deviceName = device.name,
            contentPadding = innerPadding,
        )
    }

    DeviceDialogs(
        route = dialogRoute,
        onRoute = { dialogRoute = it },
        viewModel = viewModel,
        deviceId = device.address,
        deviceName = resolvedName,
        // Still the caller's second confirmation path: the route's confirm step runs first.
        onDelete = { onDisassociate(device) },
    )
}
