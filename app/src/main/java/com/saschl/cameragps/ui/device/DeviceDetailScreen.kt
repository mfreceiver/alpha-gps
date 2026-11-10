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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.arrow_back_24px
import cameragps.sharednew.generated.resources.back
import cameragps.sharednew.generated.resources.device_info_title
import cameragps.sharednew.generated.resources.info_24px
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionOrchestrator
import com.sasch.cameragps.sharednew.bluetooth.status.CameraLiveStatus
import com.sasch.cameragps.sharednew.database.LogDatabase
import com.sasch.cameragps.sharednew.database.devices.CameraDevice
import com.sasch.cameragps.sharednew.database.devices.CameraDeviceDAO
import com.sasch.cameragps.sharednew.database.getDatabaseBuilder
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTopBar
import com.sasch.cameragps.sharednew.ui.components.glass.LocalGlassOverlayHost
import com.sasch.cameragps.sharednew.ui.device.DeviceDetailContent
import com.sasch.cameragps.sharednew.ui.device.DeviceDetailDataSource
import com.sasch.cameragps.sharednew.ui.device.DeviceDetailViewModel
import com.sasch.cameragps.sharednew.ui.device.DeviceDialogRoute
import com.sasch.cameragps.sharednew.ui.device.DeviceDialogs
import com.sasch.cameragps.sharednew.ui.device.DeviceLiveStatusSource
import com.sasch.cameragps.sharednew.ui.device.rememberDeviceDialogRoute
import com.saschl.cameragps.AppServices
import com.saschl.cameragps.service.AssociatedDeviceCompat
import com.saschl.cameragps.ui.pairing.startDevicePresenceObservation
import com.saschl.cameragps.ui.pairing.stopDevicePresenceObservation
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The orchestrator's live-status hub as the device page sees it (read-only + signal lease). */
private class OrchestratorLiveStatusSource(private val orchestrator: CameraSessionOrchestrator) :
    DeviceLiveStatusSource {
    override fun observe(address: String): Flow<CameraLiveStatus?> = orchestrator.liveStatus.observe(address)
    override fun acquireSignalMonitoring(address: String): () -> Unit {
        val lease = orchestrator.acquireSignalMonitoring(address)
        return { lease.release() }
    }
}

private fun createAndroidDataSource(dao: CameraDeviceDAO): DeviceDetailDataSource {
    return object : DeviceDetailDataSource {
        override suspend fun ensureDeviceExists(deviceId: String, deviceName: String?) {
            // Companion/pairing flows own the row's settings, but a rename needs
            // a row to update: insert-if-absent never touches an existing one.
            dao.insertDevice(CameraDevice(mac = deviceId.uppercase()))
        }

        override suspend fun isDeviceEnabled(deviceId: String) = dao.isDeviceEnabled(deviceId)
        override suspend fun isAlwaysOnEnabled(deviceId: String) =
            dao.isDeviceAlwaysOnEnabled(deviceId)

        override suspend fun setDeviceEnabled(deviceId: String, enabled: Boolean) {
            dao.setDeviceEnabled(deviceId, enabled)
        }

        override suspend fun setAlwaysOnEnabled(deviceId: String, enabled: Boolean) {
            dao.setAlwaysOnEnabled(deviceId, enabled)
        }

        override suspend fun getDeviceName(deviceId: String) = dao.getDeviceName(deviceId)

        // Database only: the CompanionDeviceManager association keeps its own
        // name, which Android never shows to the user once a custom one exists.
        override suspend fun setDeviceName(deviceId: String, name: String) {
            dao.setDeviceName(deviceId, name, isCustom = true)
        }
    }
}

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
    val context = LocalContext.current
    var wifiRemoteOpen by rememberSaveable(device.address) { mutableStateOf(false) }
    if (wifiRemoteOpen) {
        AndroidWifiRemoteScreen(device.address, onClose = { wifiRemoteOpen = false })
        return
    }

    val viewModel: DeviceDetailViewModel = viewModel(key = device.address) {
        val dao = LogDatabase.getRoomDatabase(getDatabaseBuilder(context)).cameraDeviceDao()
        val orchestrator = AppServices.from(context).orchestrator
        DeviceDetailViewModel(
            dataSource = createAndroidDataSource(dao),
            serviceActions = AndroidDeviceDetailServiceActions(
                context.applicationContext,
                observePresence = { enabled ->
                    if (enabled) startDevicePresenceObservation(deviceManager, device)
                    else stopDevicePresenceObservation(deviceManager, device)
                },
            ),
            cameraSettings = orchestrator,
            liveStatusSource = OrchestratorLiveStatusSource(orchestrator),
        )
    }

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
