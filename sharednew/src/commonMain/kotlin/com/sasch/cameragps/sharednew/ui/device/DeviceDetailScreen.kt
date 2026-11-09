package com.sasch.cameragps.sharednew.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.camera_24px
import cameragps.sharednew.generated.resources.cancel_button
import cameragps.sharednew.generated.resources.connected
import cameragps.sharednew.generated.resources.device_connection_connect
import cameragps.sharednew.generated.resources.device_connection_connecting
import cameragps.sharednew.generated.resources.device_connection_disconnect
import cameragps.sharednew.generated.resources.device_connection_disconnecting
import cameragps.sharednew.generated.resources.device_connection_status
import cameragps.sharednew.generated.resources.keyboard_arrow_right_24px
import cameragps.sharednew.generated.resources.rename_camera_hint
import cameragps.sharednew.generated.resources.rename_camera_label
import cameragps.sharednew.generated.resources.rename_camera_save
import cameragps.sharednew.generated.resources.rename_camera_title
import cameragps.sharednew.generated.resources.tap_to_connect
import cameragps.sharednew.generated.resources.wifi_remote_entry_hint
import cameragps.sharednew.generated.resources.wifi_remote_title
import com.sasch.cameragps.sharednew.ui.components.ScreenContent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Shared device detail content: camera name with its connection status, and one
 * large "photo transfer" entry pinned to the bottom (thumb reach). Platform hosts
 * wrap this with their own scaffold/toolbar (title, remove, info).
 *
 * Auto time correction / auto area adjustment have no switches: they are kept on
 * automatically ([DeviceDetailViewModel.enforceAutoCorrection]).
 */
@Composable
fun DeviceDetailContent(
    viewModel: DeviceDetailViewModel,
    deviceId: String,
    deviceName: String? = null,
    modifier: Modifier = Modifier,
    onDeviceReEnabled: (() -> Unit)? = null,
    onPresentSystemRename: (() -> Unit)? = null,
    renameEnabled: Boolean = true,
    onWifiRemote: (() -> Unit)? = null,
    /** Android: show the connect/disconnect button under the status (iOS has no such control yet). */
    connectionControls: Boolean = false,
) {
    val state = viewModel.uiState.collectAsState().value
    val sessions by viewModel.sessions.collectAsState()
    val session = sessions[deviceId.uppercase()]
    val status = deviceConnectionStatus(session)
    val pending by viewModel.pending.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshCameraSettings(deviceId)
    }

    // Reload on a name change too: an iOS system rename lands asynchronously,
    // through the accessory snapshot rather than through this screen.
    LaunchedEffect(deviceId, deviceName) {
        viewModel.load(deviceId, deviceName, onDeviceReEnabled)
    }

    // Keep the camera's auto time/area settings on whenever the camera reports them off.
    LaunchedEffect(session?.phase, session?.autoTimeCorrection, session?.autoAreaAdjustment) {
        viewModel.enforceAutoCorrection(deviceId)
    }

    ScreenContent(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                DeviceNameBlock(
                    currentName = state.deviceName.ifEmpty { deviceName.orEmpty() },
                    status = status,
                    renameEnabled = renameEnabled,
                    onPresentSystemRename = onPresentSystemRename,
                    onRenameInApp = { newName -> viewModel.renameDevice(deviceId, newName) },
                )
                if (connectionControls) {
                    val button = deviceConnectionButton(status, pending)
                    OutlinedButton(
                        onClick = {
                            when (button) {
                                DeviceConnectionButton.Connect -> viewModel.connect(deviceId)
                                DeviceConnectionButton.Disconnect -> viewModel.disconnect(deviceId)
                                else -> Unit
                            }
                        },
                        enabled = button.enabled,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(
                            stringResource(
                                when (button) {
                                    DeviceConnectionButton.Connect -> Res.string.device_connection_connect
                                    DeviceConnectionButton.Connecting -> Res.string.device_connection_connecting
                                    DeviceConnectionButton.Disconnect -> Res.string.device_connection_disconnect
                                    DeviceConnectionButton.Disconnecting -> Res.string.device_connection_disconnecting
                                },
                            ),
                        )
                    }
                }
            }

            if (onWifiRemote != null) {
                Button(
                    onClick = onWifiRemote,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(20.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            painterResource(Res.drawable.camera_24px),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                stringResource(Res.string.wifi_remote_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stringResource(Res.string.wifi_remote_entry_hint),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(
                            painterResource(Res.drawable.keyboard_arrow_right_24px),
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

/** Camera name (with rename) and, directly beneath it, the connection status entry. */
@Composable
private fun DeviceNameBlock(
    currentName: String,
    status: DeviceConnectionStatus,
    renameEnabled: Boolean,
    onPresentSystemRename: (() -> Unit)?,
    onRenameInApp: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.rename_camera_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (currentName.isNotEmpty()) {
                Text(text = currentName, style = MaterialTheme.typography.titleMedium)
            }
            ConnectionStatusEntry(status)
        }
        if (renameEnabled) {
            TextButton(
                onClick = {
                    if (onPresentSystemRename != null) onPresentSystemRename() else showDialog = true
                },
            ) {
                Text(stringResource(Res.string.rename_camera_title))
            }
        }
    }

    if (showDialog) {
        RenameCameraDialog(
            currentName = currentName,
            onDismiss = { showDialog = false },
            onConfirm = { newName ->
                showDialog = false
                onRenameInApp(newName)
            },
        )
    }
}

@Composable
private fun ConnectionStatusEntry(status: DeviceConnectionStatus) {
    val label = when (status) {
        DeviceConnectionStatus.Connected -> stringResource(Res.string.connected)
        DeviceConnectionStatus.Connecting -> stringResource(Res.string.device_connection_connecting)
        DeviceConnectionStatus.Disconnected -> stringResource(Res.string.tap_to_connect)
    }
    val dot = when (status) {
        DeviceConnectionStatus.Connected -> Color(0xFF2E7D32)
        DeviceConnectionStatus.Connecting -> Color(0xFFF9A825)
        DeviceConnectionStatus.Disconnected -> MaterialTheme.colorScheme.outline
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(8.dp)
                .background(dot, CircleShape),
        )
        Text(
            text = stringResource(Res.string.device_connection_status, label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun RenameCameraDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.rename_camera_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.rename_camera_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(Res.string.rename_camera_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            // A blank name would erase the camera's identity in the list.
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(Res.string.rename_camera_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel_button))
            }
        },
    )
}
