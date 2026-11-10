package com.sasch.cameragps.sharednew.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.cancel_button
import cameragps.sharednew.generated.resources.device_info_address
import cameragps.sharednew.generated.resources.device_info_auto_area
import cameragps.sharednew.generated.resources.device_info_auto_time
import cameragps.sharednew.generated.resources.device_info_keep_active
import cameragps.sharednew.generated.resources.device_info_keep_active_hint
import cameragps.sharednew.generated.resources.device_info_mtu
import cameragps.sharednew.generated.resources.device_info_mtu_value
import cameragps.sharednew.generated.resources.device_info_setting_enabled
import cameragps.sharednew.generated.resources.device_info_setting_off
import cameragps.sharednew.generated.resources.device_info_setting_unknown
import cameragps.sharednew.generated.resources.device_info_setting_unsupported
import cameragps.sharednew.generated.resources.device_info_title
import cameragps.sharednew.generated.resources.device_remove_confirm_message
import cameragps.sharednew.generated.resources.device_remove_confirm_title
import cameragps.sharednew.generated.resources.dialog_ok
import cameragps.sharednew.generated.resources.remove
import cameragps.sharednew.generated.resources.rename_camera_title
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSettingState
import org.jetbrains.compose.resources.stringResource

/**
 * The one dialog a device screen can show at a time. A single route instead of several booleans
 * means Info, Rename and the delete confirmation can never stack on top of each other.
 */
enum class DeviceDialogRoute { None, Info, Rename, ConfirmDelete }

@Composable
fun rememberDeviceDialogRoute(key: Any? = null): MutableState<DeviceDialogRoute> =
    rememberSaveable(
        key,
        stateSaver = Saver<DeviceDialogRoute, Any>(
            save = { it.ordinal },
            restore = { DeviceDialogRoute.entries[it as Int] },
        ),
    ) { mutableStateOf(DeviceDialogRoute.None) }

/** What a camera setting row says; the camera's own report decides, never an assumption. */
enum class SettingDisplay { Enabled, Off, Unsupported, Unknown }

fun settingDisplay(session: CameraSession?, state: CameraSettingState?): SettingDisplay = when {
    session == null || state == null -> SettingDisplay.Unknown
    state.supported == false -> SettingDisplay.Unsupported
    state.enabled == true -> SettingDisplay.Enabled
    state.enabled == false -> SettingDisplay.Off
    else -> SettingDisplay.Unknown
}

/**
 * Info / Rename / delete-confirmation dialogs of a camera. Info is a scrollable settings sheet:
 * identity, the keep-active switch, the camera's auto time/area state (read-only — the app keeps
 * them on itself) and the Bluetooth MTU when the link has reported one. Delete only ever goes
 * through [onDelete] after the confirmation route; a null [onDelete] hides it.
 */
@Composable
fun DeviceDialogs(
    route: DeviceDialogRoute,
    onRoute: (DeviceDialogRoute) -> Unit,
    viewModel: DeviceDetailViewModel,
    deviceId: String,
    deviceName: String,
    onDelete: (() -> Unit)?,
) {
    when (route) {
        DeviceDialogRoute.None -> Unit
        DeviceDialogRoute.Info -> {
            val state = viewModel.uiState.collectAsState().value
            val sessions by viewModel.sessions.collectAsState()
            val session = sessions[deviceId.uppercase()]
            AlertDialog(
                onDismissRequest = { onRoute(DeviceDialogRoute.None) },
                title = { Text(stringResource(Res.string.device_info_title)) },
                text = {
                    Column(
                        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(deviceName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(Res.string.device_info_address, deviceId),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(stringResource(Res.string.device_info_keep_active))
                                Text(
                                    stringResource(Res.string.device_info_keep_active_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.isAlwaysOnEnabled,
                                onCheckedChange = { viewModel.setAlwaysOnEnabled(it, deviceId) },
                            )
                        }
                        SettingRow(
                            stringResource(Res.string.device_info_auto_time),
                            settingDisplay(session, session?.autoTimeCorrection),
                        )
                        SettingRow(
                            stringResource(Res.string.device_info_auto_area),
                            settingDisplay(session, session?.autoAreaAdjustment),
                        )
                        session?.mtu?.let { mtu ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(Res.string.device_info_mtu))
                                Text(
                                    stringResource(Res.string.device_info_mtu_value, mtu),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onRoute(DeviceDialogRoute.Rename) }) {
                                Text(stringResource(Res.string.rename_camera_title))
                            }
                            if (onDelete != null) {
                                TextButton(onClick = { onRoute(DeviceDialogRoute.ConfirmDelete) }) {
                                    Text(
                                        stringResource(Res.string.remove),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onRoute(DeviceDialogRoute.None) }) {
                        Text(stringResource(Res.string.dialog_ok))
                    }
                },
            )
        }

        DeviceDialogRoute.Rename -> RenameCameraDialog(
            currentName = deviceName,
            onDismiss = { onRoute(DeviceDialogRoute.Info) },
            onConfirm = { newName ->
                viewModel.renameDevice(deviceId, newName)
                onRoute(DeviceDialogRoute.Info)
            },
        )

        DeviceDialogRoute.ConfirmDelete -> AlertDialog(
            onDismissRequest = { onRoute(DeviceDialogRoute.Info) },
            title = { Text(stringResource(Res.string.device_remove_confirm_title)) },
            text = { Text(stringResource(Res.string.device_remove_confirm_message, deviceName)) },
            confirmButton = {
                TextButton(onClick = {
                    onRoute(DeviceDialogRoute.None)
                    onDelete?.invoke()
                }) {
                    Text(stringResource(Res.string.remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onRoute(DeviceDialogRoute.Info) }) {
                    Text(stringResource(Res.string.cancel_button))
                }
            },
        )
    }
}

@Composable
private fun SettingRow(label: String, display: SettingDisplay) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            stringResource(
                when (display) {
                    SettingDisplay.Enabled -> Res.string.device_info_setting_enabled
                    SettingDisplay.Off -> Res.string.device_info_setting_off
                    SettingDisplay.Unsupported -> Res.string.device_info_setting_unsupported
                    SettingDisplay.Unknown -> Res.string.device_info_setting_unknown
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
    }
}
