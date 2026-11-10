package com.saschl.cameragps.ui.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.dismiss_button
import cameragps.sharednew.generated.resources.guide_open_button
import cameragps.sharednew.generated.resources.m2_error_24px
import cameragps.sharednew.generated.resources.pairing_failed_device_message
import cameragps.sharednew.generated.resources.pairing_failed_hint_camera_pairing
import cameragps.sharednew.generated.resources.pairing_failed_hint_intro
import cameragps.sharednew.generated.resources.pairing_failed_hint_pairing_mode
import cameragps.sharednew.generated.resources.pairing_failed_hint_phone_pairing
import cameragps.sharednew.generated.resources.pairing_failed_title
import com.sasch.cameragps.sharednew.database.LogDatabase
import com.sasch.cameragps.sharednew.database.devices.CameraDevice
import com.sasch.cameragps.sharednew.database.getDatabaseBuilder
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.device.autoOpenDeviceAddress
import com.sasch.cameragps.sharednew.ui.theme.AlphaTextStyles
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.whatsnew.WhatsNewDialog
import com.sasch.cameragps.sharednew.whatsnew.WhatsNewState
import com.saschl.cameragps.AppServices
import com.saschl.cameragps.service.AssociatedDeviceCompat
import com.saschl.cameragps.service.BluetoothStateBroadcastReceiver
import com.saschl.cameragps.service.LocationSenderService
import com.saschl.cameragps.service.coordinator.DeviceRemovalCoordinator
import com.saschl.cameragps.service.getAssociatedDevices
import com.saschl.cameragps.ui.EnhancedLocationPermissionBox
import com.saschl.cameragps.ui.pairing.startDevicePresenceObservation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import timber.log.Timber

@SuppressLint("MissingPermission")
@Composable
fun CameraDeviceManager(
    whatsNew: WhatsNewState? = null,
    startupPromptActive: Boolean = false,
    onSettingsClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    onTroubleshootingClick: () -> Unit = {},
    onLogsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceManager = context.getSystemService<CompanionDeviceManager>()
    val devicesDao = LogDatabase.getRoomDatabase(
        getDatabaseBuilder(context)
    ).cameraDeviceDao()
    val adapter = context.getSystemService<BluetoothManager>()?.adapter
    val locationManager = context.getSystemService<LocationManager>()

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    var associatedDevices by remember {
        mutableStateOf(
            if (SCREENSHOT_MODE) {
                mockDevices
            } else {
                deviceManager!!.getAssociatedDevices(adapter!!)
            }
        )
    }

    var selectedDeviceAddress by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    // Set once the user backs out of the detail screen to the list, so the
    // single-camera shortcut below does not bounce them straight back in.
    var autoOpenDismissed by rememberSaveable { mutableStateOf(false) }

    // With exactly one saved camera the list adds nothing: open its detail screen
    // directly (computed, not an effect, so the list never flashes first).
    val effectiveDeviceAddress = selectedDeviceAddress ?: if (SCREENSHOT_MODE) {
        null
    } else {
        autoOpenDeviceAddress(associatedDevices.map { it.address }, autoOpenDismissed)
    }

    val selectedDevice = associatedDevices.firstOrNull() { it.address == effectiveDeviceAddress }

    var isBluetoothEnabled by remember {
        mutableStateOf(adapter?.isEnabled == true)
    }

    var isLocationEnabled by remember {
        mutableStateOf(
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                    locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        )
    }

    val pairingFailedAddress by AppServices.from(context).pairingFailedDevice.collectAsState()
    // Suppresses re-showing the pairing-failure dialog for the same device: the failed
    // camera keeps auto-reconnecting and may exhaust its pairing retries repeatedly.
    // AppServices clears pairingFailedDevice on a successful handshake, which re-arms it.
    var dismissedPairingFailedAddress by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pairingFailedAddress) {
        if (pairingFailedAddress == null) {
            dismissedPairingFailedAddress = null
        }
    }

    LaunchedEffect(associatedDevices) {
        if (SCREENSHOT_MODE) return@LaunchedEffect
        associatedDevices.forEach {
            devicesDao.insertDevice(
                CameraDevice(
                    deviceName = it.name,
                    mac = it.address.uppercase(),
                    alwaysOnEnabled = false,
                    deviceEnabled = true,
                )
            )
        }
    }

    DisposableEffect(context) {
        val bluetoothReceiver = BluetoothStateBroadcastReceiver { enabled ->
            isBluetoothEnabled = enabled
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context.applicationContext,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            context.applicationContext.unregisterReceiver(bluetoothReceiver)
        }
    }

    LaunchedEffect(lifecycleState) {
        if (SCREENSHOT_MODE) return@LaunchedEffect
        when (lifecycleState) {
            Lifecycle.State.RESUMED -> {
                associatedDevices = deviceManager!!.getAssociatedDevices(adapter!!)
                isBluetoothEnabled = adapter.isEnabled == true
                isLocationEnabled =
                    locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                            locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            }

            else -> { /* No action needed */
            }
        }
    }

    if (!SCREENSHOT_MODE && whatsNew?.pending == true && !startupPromptActive &&
        lifecycleState == Lifecycle.State.RESUMED && selectedDevice == null &&
        (pairingFailedAddress == null || pairingFailedAddress == dismissedPairingFailedAddress)
    ) {
        whatsNew.release?.let { WhatsNewDialog(it, onDismiss = whatsNew::dismiss) }
    }

    if (deviceManager == null || adapter == null) {
        Text(text = "No Companion device manager found. The device does not support it.")
    } else {
        // Shared by the detail screen's Remove button and the list's swipe-to-delete
        val disassociateDevice: (AssociatedDeviceCompat) -> Unit = { device ->
            associatedDevices.find { ass -> ass.address == device.address }
                ?.let { foundDevice ->
                    Timber.i("Disassociating device: ${foundDevice.name} (${foundDevice.address})")
                    scope.launch {

                        AppServices.from(context).wifiRemote.closeAndJoin(foundDevice.address)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                            deviceManager.stopObservingDevicePresence(
                                ObservingDevicePresenceRequest.Builder()
                                    .setAssociationId(foundDevice.id)
                                    .build()
                            )
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            @Suppress("DEPRECATION")
                            deviceManager.stopObservingDevicePresence(
                                foundDevice.address
                            )
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            deviceManager.disassociate(foundDevice.id)
                        } else {
                            @Suppress("DEPRECATION")
                            deviceManager.disassociate(foundDevice.address)
                        }

                        // Delete must commit before the shutdown request: the service
                        // judges from the enabled-device count and must not see the
                        // removed device anymore (DeviceRemovalCoordinator serializes this).
                        DeviceRemovalCoordinator(devicesDao) { address ->
                            val serviceIntent = Intent(
                                context.applicationContext,
                                LocationSenderService::class.java
                            ).apply {
                                action =
                                    com.sasch.cameragps.sharednew.bluetooth.SonyBluetoothConstants.ACTION_REQUEST_SHUTDOWN
                            }
                            serviceIntent.putExtra("address", address)
                            context.startService(serviceIntent)
                        }.remove(CameraDevice(foundDevice.address.uppercase()))

                        associatedDevices =
                            deviceManager.getAssociatedDevices(adapter)
                    }
                    selectedDeviceAddress = null
                    // Removing one of several cameras must not auto-open the one left over.
                    autoOpenDismissed = true
                }
        }

        Box {
            if (selectedDevice == null) {
                EnhancedLocationPermissionBox {
                    DevicesScreen(
                        deviceManager = deviceManager,
                        isBluetoothEnabled = isBluetoothEnabled,
                        isLocationEnabled = isLocationEnabled,
                        associatedDevices = associatedDevices,
                        onDeviceAssociated = {
                            if (!SCREENSHOT_MODE) {
                                scope.launch {
                                    devicesDao.insertDevice(
                                        CameraDevice(
                                            deviceName = it.name,
                                            mac = it.address.uppercase(),
                                            alwaysOnEnabled = false,
                                            deviceEnabled = true,
                                        )
                                    )
                                    delay(1000) // give the system a short time to breathe
                                    startDevicePresenceObservation(deviceManager, it)
                                    // Refresh the devices list to update pairing state
                                    associatedDevices = deviceManager.getAssociatedDevices(adapter)
                                }
                            }
                        },
                        onConnect = { device ->
                            if (!SCREENSHOT_MODE) {
                                selectedDeviceAddress = device.address
                            }
                        },
                        onDisassociate = disassociateDevice,
                        onSettingsClick = onSettingsClick,
                        onHelpClick = onHelpClick,
                        onTroubleshootingClick = onTroubleshootingClick,
                        onLogsClick = onLogsClick
                    )
                }
            } else {
                EnhancedLocationPermissionBox {
                deviceManager.getAssociatedDevices(adapter)
                    .find { it.address == selectedDevice?.address }?.id?.let {

                    DeviceDetailScreen(
                                device = selectedDevice!!,
                                deviceManager = deviceManager,
                                associationId = it,
                        onDisassociate = disassociateDevice,
                        onClose = {
                            selectedDeviceAddress = null
                            autoOpenDismissed = true
                        },
                            )
                        }
                    }
            }
        }

        pairingFailedAddress
            ?.takeIf { !SCREENSHOT_MODE && it != dismissedPairingFailedAddress }
            ?.let { failedAddress ->
                val failedDeviceName = associatedDevices
                    .find { it.address.equals(failedAddress, ignoreCase = true) }
                    ?.name
                    ?: failedAddress
                CapsuleDialog(
                    onDismissRequest = { dismissedPairingFailedAddress = failedAddress },
                    title = stringResource(Res.string.pairing_failed_title),
                    icon = painterResource(Res.drawable.m2_error_24px),
                    iconTint = AlphaTheme.colors.error,
                    confirm = DialogAction(
                        label = stringResource(Res.string.guide_open_button),
                        onClick = {
                            dismissedPairingFailedAddress = failedAddress
                            onTroubleshootingClick()
                        },
                    ),
                    dismiss = DialogAction(
                        label = stringResource(Res.string.dismiss_button),
                        onClick = { dismissedPairingFailedAddress = failedAddress },
                    ),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            stringResource(Res.string.pairing_failed_device_message, failedDeviceName),
                            stringResource(Res.string.pairing_failed_hint_intro),
                            stringResource(Res.string.pairing_failed_hint_pairing_mode),
                            stringResource(Res.string.pairing_failed_hint_camera_pairing),
                            stringResource(Res.string.pairing_failed_hint_phone_pairing),
                        ).forEach { line ->
                            Text(
                                text = line,
                                style = AlphaTextStyles.dialogBody,
                                color = AlphaTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
    }
}
