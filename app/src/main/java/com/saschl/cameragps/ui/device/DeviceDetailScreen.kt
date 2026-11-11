package com.saschl.cameragps.ui.device

import android.Manifest
import android.companion.CompanionDeviceManager
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresPermission
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.arrow_back_24px
import cameragps.sharednew.generated.resources.back
import cameragps.sharednew.generated.resources.device_info_title
import cameragps.sharednew.generated.resources.info_24px
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTopBar
import com.sasch.cameragps.sharednew.ui.components.glass.LocalGlassOverlayHost
import com.sasch.cameragps.sharednew.ui.device.DeviceDetailContent
import com.sasch.cameragps.sharednew.ui.device.DeviceDialogRoute
import com.sasch.cameragps.sharednew.ui.device.DeviceDialogs
import com.sasch.cameragps.sharednew.ui.device.rememberDeviceDialogRoute
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
) {
    var wifiRemoteOpen by rememberSaveable(device.address) { mutableStateOf(false) }
    if (wifiRemoteOpen) {
        AndroidWifiRemoteScreen(device.address, onClose = { wifiRemoteOpen = false })
        return
    }

    val viewModel = rememberDeviceDetailViewModel(device, deviceManager)

    val resolvedName = viewModel.uiState.collectAsState().value.deviceName.ifEmpty { device.name }
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
        BackHandler(enabled = overlayHost?.hasOverlay != true) { onClose() }

        DeviceDetailContent(
            onWifiRemote = { wifiRemoteOpen = true },
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
