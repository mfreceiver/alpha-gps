package com.saschl.cameragps.ui.device

import android.Manifest
import android.annotation.SuppressLint
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.header_device_list
import cameragps.sharednew.generated.resources.m2_list_grant
import cameragps.sharednew.generated.resources.m2_warning_24px
import cameragps.sharednew.generated.resources.permissions_missing_description
import cameragps.sharednew.generated.resources.permissions_missing_title
import cameragps.sharednew.generated.resources.settings
import cameragps.sharednew.generated.resources.settings_24px
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.device.SharedDevicesScreen
import com.sasch.cameragps.sharednew.ui.devicelist.DeviceListLayout
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.saschl.cameragps.service.AssociatedDeviceCompat
import com.saschl.cameragps.ui.AssociatedDevicesList
import com.saschl.cameragps.ui.getPermissionDescription
import com.saschl.cameragps.ui.pairing.PairingManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun DevicesScreen(
    deviceManager: CompanionDeviceManager,
    isBluetoothEnabled: Boolean,
    isLocationEnabled: Boolean,
    associatedDevices: List<AssociatedDeviceCompat>,
    onDeviceAssociated: (AssociatedDeviceCompat) -> Unit,
    onConnect: (AssociatedDeviceCompat) -> Unit,
    onDisassociate: (AssociatedDeviceCompat) -> Unit,
    onSettingsClick: () -> Unit = {},
    onTroubleshootingClick: () -> Unit = {},
) {
    val context = LocalContext.current
    // State for managing pairing after association
    var pendingPairingDevice by remember { mutableStateOf<AssociatedDeviceCompat?>(null) }

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    }

    val missingPermissions = requiredPermissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    SharedDevicesScreen(
        title = stringResource(Res.string.header_device_list),
        topBarActions = {
            // Help and the activity log are reached from Settings; only the settings icon stays here.
            GlassIconButton(
                icon = painterResource(Res.drawable.settings_24px),
                contentDescription = stringResource(Res.string.settings),
                onClick = onSettingsClick,
            )
        }
    ) {

        DeviceListLayout(
            onNeedHelp = onTroubleshootingClick,
            header = {
                if (missingPermissions.isNotEmpty()) {
                    val missingPermissionNames = missingPermissions
                        .mapNotNull { permission ->
                            getPermissionDescription(permission)?.let { res ->
                                " - ${stringResource(res)}"
                            }
                        }
                        .joinToString("\n")
                    MissingPermissionsCard(
                        description = stringResource(
                            Res.string.permissions_missing_description,
                            missingPermissionNames
                        ),
                        onOpenSettings = {
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            context.startActivity(intent)
                        },
                    )
                }

                ScanForDevicesMenu(
                    deviceManager,
                    isBluetoothEnabled,
                    isLocationEnabled,
                    associatedDevices,
                    onSetPairingDevice = { device -> pendingPairingDevice = device },
                    onDeviceAssociated = onDeviceAssociated,
                    onTroubleshootingClick = onTroubleshootingClick
                )
            },
        ) {
            AssociatedDevicesList(
                associatedDevices = associatedDevices,
                onConnect = onConnect,
                onDisassociate = onDisassociate,
                onOpenTroubleshooting = onTroubleshootingClick,
            )
        }

        // Handle pairing for newly associated device
        pendingPairingDevice?.let { device ->
            PairingManager(
                device = device,
                deviceManager = deviceManager,
                onPairingComplete = {
                    Timber.i("Pairing completed for newly associated device ${device.name}")
                    onDeviceAssociated(device)
                    pendingPairingDevice = null
                },
                onPairingCancelled = {
                    Timber.i("Pairing cancelled for newly associated device ${device.name}")
                    // Still add the device even if pairing was cancelled
                    onDeviceAssociated(device)
                    pendingPairingDevice = null
                }
            )
        }
    }
}

/** Missing-permission notice (design v3 "list_perm"): glass card, warning icon, tonal action. */
@Composable
private fun MissingPermissionsCard(description: String, onOpenSettings: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlphaDimens.screenMargin, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painterResource(Res.drawable.m2_warning_24px),
                contentDescription = null,
                tint = AlphaTheme.colors.error,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(Res.string.permissions_missing_title),
                style = AlphaType.Label,
                color = AlphaTheme.colors.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = description,
            style = AlphaType.Body,
            color = AlphaTheme.colors.onSurfaceVariant,
        )
        CapsuleButton(
            text = stringResource(Res.string.m2_list_grant),
            onClick = onOpenSettings,
            role = CapsuleRole.Tonal,
        )
    }
}
