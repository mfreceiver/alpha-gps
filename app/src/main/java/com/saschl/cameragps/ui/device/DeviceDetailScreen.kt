package com.saschl.cameragps.ui.device

import android.Manifest
import android.companion.CompanionDeviceManager
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import cameragps.sharednew.generated.resources.cancel_button
import cameragps.sharednew.generated.resources.device_info_address
import cameragps.sharednew.generated.resources.device_info_title
import cameragps.sharednew.generated.resources.device_remove_confirm_message
import cameragps.sharednew.generated.resources.device_remove_confirm_title
import cameragps.sharednew.generated.resources.dialog_ok
import cameragps.sharednew.generated.resources.help_menu_item
import cameragps.sharednew.generated.resources.remove
import com.sasch.cameragps.sharednew.database.LogDatabase
import com.sasch.cameragps.sharednew.database.devices.CameraDevice
import com.sasch.cameragps.sharednew.database.devices.CameraDeviceDAO
import com.sasch.cameragps.sharednew.database.getDatabaseBuilder
import com.sasch.cameragps.sharednew.ui.device.DeviceDetailContent
import com.sasch.cameragps.sharednew.ui.device.DeviceDetailDataSource
import com.sasch.cameragps.sharednew.ui.device.DeviceDetailViewModel
import com.saschl.cameragps.AppServices
import com.saschl.cameragps.R
import com.saschl.cameragps.service.AssociatedDeviceCompat
import com.saschl.cameragps.ui.pairing.startDevicePresenceObservation
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
            serviceActions = AndroidDeviceDetailServiceActions(context.applicationContext),
            cameraSettings = AppServices.from(context).orchestrator,
        )
    }

    val resolvedName = viewModel.uiState.collectAsState().value.deviceName.ifEmpty { device.name }
    var showRemoveConfirm by rememberSaveable(device.address) { mutableStateOf(false) }
    var showInfo by rememberSaveable(device.address) { mutableStateOf(false) }

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
                    IconButton(onClick = { showRemoveConfirm = true }) {
                        Icon(
                            painter = painterResource(R.drawable.delete_24px),
                            contentDescription = stringResource(Res.string.remove),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = { showInfo = true }) {
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
            // The enable switch is gone: a device an older version left disabled is
            // switched back on at load, so presence observation must resume too.
            onDeviceReEnabled = { startDevicePresenceObservation(deviceManager, device) },
        )
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text(stringResource(Res.string.device_remove_confirm_title)) },
            text = {
                Text(stringResource(Res.string.device_remove_confirm_message, resolvedName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirm = false
                        onDisassociate(device)
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text(stringResource(Res.string.cancel_button))
                }
            },
        )
    }

    if (showInfo) {
        // The MAC address lives only here, not in the screen body.
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(stringResource(Res.string.device_info_title)) },
            text = {
                Text(
                    text = resolvedName + "\n" +
                        stringResource(Res.string.device_info_address, device.address)
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(Res.string.dialog_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showInfo = false
                        onHelpClick()
                    },
                ) {
                    Text(stringResource(Res.string.help_menu_item))
                }
            },
        )
    }
}
