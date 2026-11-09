package com.saschl.cameragps.ui.device

import android.Manifest
import android.companion.CompanionDeviceManager
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.back
import cameragps.sharednew.generated.resources.device_info_title
import com.sasch.cameragps.sharednew.database.LogDatabase
import com.sasch.cameragps.sharednew.database.devices.CameraDevice
import com.sasch.cameragps.sharednew.database.devices.CameraDeviceDAO
import com.sasch.cameragps.sharednew.database.getDatabaseBuilder
import com.sasch.cameragps.sharednew.ui.device.DeviceDetailContent
import com.sasch.cameragps.sharednew.ui.device.DeviceDialogRoute
import com.sasch.cameragps.sharednew.ui.device.DeviceDialogs
import com.sasch.cameragps.sharednew.ui.device.rememberDeviceDialogRoute
import com.sasch.cameragps.sharednew.ui.device.DeviceDetailDataSource
import com.sasch.cameragps.sharednew.ui.device.DeviceDetailViewModel
import com.saschl.cameragps.AppServices
import com.saschl.cameragps.R
import com.saschl.cameragps.service.AssociatedDeviceCompat
import com.saschl.cameragps.ui.pairing.startDevicePresenceObservation
import com.saschl.cameragps.ui.pairing.stopDevicePresenceObservation
import org.jetbrains.compose.resources.stringResource

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun DeviceDetailScreen(
    device: AssociatedDeviceCompat,
    deviceManager: CompanionDeviceManager,
    onDisassociate: (device: AssociatedDeviceCompat) -> Unit,
    associationId: Int,
    onClose: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var wifiRemoteOpen by rememberSaveable(device.address) { mutableStateOf(false) }
    if (wifiRemoteOpen) {
        AndroidWifiRemoteScreen(device.address, onClose = { wifiRemoteOpen = false })
        return
    }

    val viewModel: DeviceDetailViewModel = viewModel(key = device.address) {
        val dao = LogDatabase.getRoomDatabase(getDatabaseBuilder(context)).cameraDeviceDao()
        DeviceDetailViewModel(
            dataSource = createAndroidDataSource(dao),
            serviceActions = AndroidDeviceDetailServiceActions(
                context.applicationContext,
                observePresence = { enabled ->
                    if (enabled) startDevicePresenceObservation(deviceManager, device)
                    else stopDevicePresenceObservation(deviceManager, device)
                },
            ),
            cameraSettings = AppServices.from(context).orchestrator,
        )
    }

    val resolvedName = viewModel.uiState.collectAsState().value.deviceName.ifEmpty { device.name }
    var dialogRoute by rememberDeviceDialogRoute(device.address)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = resolvedName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                actions = {
                    // Remove and rename live inside the info dialog now.
                    IconButton(onClick = { dialogRoute = DeviceDialogRoute.Info }) {
                        Icon(
                            painter = painterResource(R.drawable.info_24px),
                            contentDescription = stringResource(Res.string.device_info_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { innerPadding ->
        BackHandler { onClose() }

        DeviceDetailContent(
            onWifiRemote = { wifiRemoteOpen = true },
            viewModel = viewModel,
            deviceId = device.address,
            deviceName = device.name,
            modifier = Modifier.padding(innerPadding),
            // Rename lives in the info dialog; the screen offers connect/disconnect instead.
            renameEnabled = false,
            connectionControls = true,
        )
    }

    DeviceDialogs(
        route = dialogRoute,
        onRoute = { dialogRoute = it },
        viewModel = viewModel,
        deviceId = device.address,
        deviceName = resolvedName,
        onHelp = onHelpClick,
        // Still the caller's second confirmation path: the route's confirm step runs first.
        onDelete = { onDisassociate(device) },
    )
}
