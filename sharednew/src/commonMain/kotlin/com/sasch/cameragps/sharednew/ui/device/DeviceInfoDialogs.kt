package com.sasch.cameragps.sharednew.ui.device

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.a1_address_hidden
import cameragps.sharednew.generated.resources.a1_address_hide
import cameragps.sharednew.generated.resources.a1_address_show
import cameragps.sharednew.generated.resources.a1_bt_connected_short
import cameragps.sharednew.generated.resources.a1_bt_connecting_short
import cameragps.sharednew.generated.resources.a1_bt_disconnected_short
import cameragps.sharednew.generated.resources.a1_bt_disconnecting_short
import cameragps.sharednew.generated.resources.a1_info_bt_address
import cameragps.sharednew.generated.resources.a1_setting_enabled
import cameragps.sharednew.generated.resources.a1_setting_off
import cameragps.sharednew.generated.resources.a1_setting_unknown
import cameragps.sharednew.generated.resources.a1_setting_unsupported
import cameragps.sharednew.generated.resources.cancel
import cameragps.sharednew.generated.resources.device_connection_connect
import cameragps.sharednew.generated.resources.m2_bluetooth_disabled_24px
import cameragps.sharednew.generated.resources.m2_disconnect_bt_confirm
import cameragps.sharednew.generated.resources.m2_disconnect_bt_message
import cameragps.sharednew.generated.resources.m2_disconnect_bt_title
import cameragps.sharednew.generated.resources.cancel_button
import cameragps.sharednew.generated.resources.check_24px
import cameragps.sharednew.generated.resources.delete_24px
import cameragps.sharednew.generated.resources.device_info_auto_area
import cameragps.sharednew.generated.resources.device_info_auto_time
import cameragps.sharednew.generated.resources.device_info_keep_active
import cameragps.sharednew.generated.resources.device_info_title
import cameragps.sharednew.generated.resources.device_remove_confirm_message
import cameragps.sharednew.generated.resources.device_remove_confirm_title
import cameragps.sharednew.generated.resources.m2_edit_24px
import cameragps.sharednew.generated.resources.m2_info_bluetooth
import cameragps.sharednew.generated.resources.m2_info_keep_active_hint
import cameragps.sharednew.generated.resources.m2_info_location
import cameragps.sharednew.generated.resources.m2_info_location_active
import cameragps.sharednew.generated.resources.m2_info_location_idle
import cameragps.sharednew.generated.resources.m2_info_remove
import cameragps.sharednew.generated.resources.m2_info_rename
import cameragps.sharednew.generated.resources.m2_rename_title
import cameragps.sharednew.generated.resources.remove
import cameragps.sharednew.generated.resources.rename_camera_hint
import cameragps.sharednew.generated.resources.rename_camera_label
import cameragps.sharednew.generated.resources.rename_camera_save
import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSettingState
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleSize
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassInsetBlock
import com.sasch.cameragps.sharednew.ui.components.glass.IconEmphasis
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import com.sasch.cameragps.sharednew.ui.components.glass.statusIconColor
import com.sasch.cameragps.sharednew.ui.components.glass.statusToneColor
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTextStyles
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The one dialog a device screen can show at a time. A single route instead of several booleans
 * means Info, Rename and the delete confirmation can never stack on top of each other.
 */
enum class DeviceDialogRoute { None, Info, Rename, ConfirmDelete, ConfirmDisconnect }

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

/** Whether the Ⓘ dialog may claim GPS is being written: only a fully set-up link writes. */
fun locationWritingActive(session: CameraSession?): Boolean = session?.phase == BleSessionPhase.Transmitting

/**
 * Info / Rename / delete- and disconnect-confirmation dialogs of a camera (design v3 "Ⓘ"): title and
 * ✕ share the header row, the name with ✎ (rename) sits on its own line below, then only what
 * Bluetooth knows — link state (green ✓ when connected) with its action (Disconnect → confirmation
 * → [DeviceDetailViewModel.disconnect], the same persisted manual disconnect as before; Cancel while
 * connecting; Connect when off), the address (hidden until tapped, never logged), GPS writing, the
 * camera's auto time/area state (read-only, the app keeps them on) — the keep-active switch and
 * "Remove camera". No MTU, firmware, serial or help here. Delete only ever goes through [onDelete]
 * after the confirmation route; a null [onDelete] hides it.
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
            val pending by viewModel.pending.collectAsState()
            val session = sessions[deviceId.uppercase()]
            DeviceInfoDialog(
                deviceId = deviceId,
                deviceName = deviceName,
                session = session,
                pending = pending,
                onConnect = { viewModel.connect(deviceId) },
                onCancelConnecting = { viewModel.cancelConnecting(deviceId) },
                onDisconnect = { onRoute(DeviceDialogRoute.ConfirmDisconnect) },
                keepActive = state.isAlwaysOnEnabled,
                onKeepActiveChange = { viewModel.setAlwaysOnEnabled(it, deviceId) },
                onClose = { onRoute(DeviceDialogRoute.None) },
                onRename = { onRoute(DeviceDialogRoute.Rename) },
                onRemove = if (onDelete != null) {
                    { onRoute(DeviceDialogRoute.ConfirmDelete) }
                } else {
                    null
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

        DeviceDialogRoute.ConfirmDelete -> CapsuleDialog(
            onDismissRequest = { onRoute(DeviceDialogRoute.Info) },
            title = stringResource(Res.string.device_remove_confirm_title),
            message = stringResource(Res.string.device_remove_confirm_message, deviceName),
            icon = painterResource(Res.drawable.delete_24px),
            destructive = true,
            confirm = DialogAction(stringResource(Res.string.remove), onClick = {
                onRoute(DeviceDialogRoute.None)
                onDelete?.invoke()
            }),
            dismiss = DialogAction(stringResource(Res.string.cancel_button), onClick = {
                onRoute(DeviceDialogRoute.Info)
            }),
        )

        // Back to Ⓘ either way: it then shows "Disconnecting…" / the unchanged state.
        DeviceDialogRoute.ConfirmDisconnect -> CapsuleDialog(
            onDismissRequest = { onRoute(DeviceDialogRoute.Info) },
            title = stringResource(Res.string.m2_disconnect_bt_title),
            message = stringResource(Res.string.m2_disconnect_bt_message),
            icon = painterResource(Res.drawable.m2_bluetooth_disabled_24px),
            destructive = true,
            confirm = DialogAction(stringResource(Res.string.m2_disconnect_bt_confirm), onClick = {
                viewModel.disconnect(deviceId)
                onRoute(DeviceDialogRoute.Info)
            }),
            dismiss = DialogAction(stringResource(Res.string.cancel_button), onClick = {
                onRoute(DeviceDialogRoute.Info)
            }),
        )
    }
}

@Composable
private fun DeviceInfoDialog(
    deviceId: String,
    deviceName: String,
    session: CameraSession?,
    pending: PendingConnection,
    onConnect: () -> Unit,
    onCancelConnecting: () -> Unit,
    onDisconnect: () -> Unit,
    keepActive: Boolean,
    onKeepActiveChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onRename: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val colors = AlphaTheme.colors
    CapsuleDialog(
        onDismissRequest = onClose,
        confirm = null,
        dismiss = null,
        title = stringResource(Res.string.device_info_title),
        onClose = onClose,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Name with ✎ right beside it, on its own line under the title / ✕ header.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    deviceName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                GlassIconButton(
                    icon = painterResource(Res.drawable.m2_edit_24px),
                    contentDescription = stringResource(Res.string.m2_info_rename),
                    onClick = onRename,
                    emphasis = IconEmphasis.Primary,
                    touchTarget = AlphaDimens.minDenseTouchTarget + 4.dp,
                )
            }

            val status = deviceConnectionStatus(session)
            val headline = connectionHeadline(status, pending)
            InfoRow(stringResource(Res.string.m2_info_bluetooth)) {
                InfoStatusValue(
                    text = stringResource(
                        when (headline) {
                            ConnectionHeadline.Connected -> Res.string.a1_bt_connected_short
                            ConnectionHeadline.Connecting -> Res.string.a1_bt_connecting_short
                            ConnectionHeadline.Disconnecting -> Res.string.a1_bt_disconnecting_short
                            ConnectionHeadline.Disconnected -> Res.string.a1_bt_disconnected_short
                        },
                    ),
                    tone = connectionTone(headline),
                )
                when (connectionAction(status, pending)) {
                    ConnectionAction.Disconnect -> InfoChip(
                        text = stringResource(Res.string.m2_disconnect_bt_confirm),
                        role = CapsuleRole.DangerOutlined,
                        onClick = onDisconnect,
                    )
                    ConnectionAction.CancelConnecting -> InfoChip(
                        text = stringResource(Res.string.cancel),
                        role = CapsuleRole.Secondary,
                        onClick = onCancelConnecting,
                    )
                    ConnectionAction.Connect -> InfoChip(
                        text = stringResource(Res.string.device_connection_connect),
                        role = CapsuleRole.Tonal,
                        onClick = onConnect,
                    )
                    ConnectionAction.None -> Unit
                }
            }

            // Hidden until asked for; a new opening of the dialog starts hidden again.
            var addressVisibility by remember(deviceId) { mutableStateOf(AddressVisibility.Hidden) }
            val hiddenDescription = stringResource(Res.string.a1_address_hidden)
            InfoRow(stringResource(Res.string.a1_info_bt_address)) {
                Text(
                    addressText(deviceId, addressVisibility),
                    style = InfoValueStyle,
                    color = colors.onSurface,
                    maxLines = 1,
                    modifier = if (addressVisibility == AddressVisibility.Hidden) {
                        Modifier.clearAndSetSemantics { contentDescription = hiddenDescription }
                    } else {
                        Modifier
                    },
                )
                InfoChip(
                    text = stringResource(
                        if (addressVisibility == AddressVisibility.Hidden) Res.string.a1_address_show else Res.string.a1_address_hide,
                    ),
                    role = CapsuleRole.Secondary,
                    onClick = { addressVisibility = addressVisibility.toggled() },
                )
            }

            InfoRow(stringResource(Res.string.m2_info_location)) {
                val writing = locationWritingActive(session)
                InfoStatusValue(
                    stringResource(if (writing) Res.string.m2_info_location_active else Res.string.m2_info_location_idle),
                    tone = if (writing) StatusTone.Success else StatusTone.Neutral,
                )
            }
            InfoRow(stringResource(Res.string.device_info_auto_time)) {
                SettingValue(infoSettingDisplay(session, session?.autoTimeCorrection))
            }
            InfoRow(stringResource(Res.string.device_info_auto_area)) {
                SettingValue(infoSettingDisplay(session, session?.autoAreaAdjustment))
            }

            GlassInsetBlock(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            stringResource(Res.string.device_info_keep_active),
                            style = AlphaTextStyles.statusValue,
                            color = colors.onSurface,
                        )
                        Text(
                            stringResource(Res.string.m2_info_keep_active_hint),
                            style = AlphaTextStyles.statusLabel,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Switch(checked = keepActive, onCheckedChange = onKeepActiveChange)
                }
            }

            if (onRemove != null) {
                CapsuleButton(
                    text = stringResource(Res.string.m2_info_remove),
                    onClick = onRemove,
                    role = CapsuleRole.DangerOutlined,
                    icon = painterResource(Res.drawable.delete_24px),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val InfoValueStyle = AlphaTextStyles.dialogBody.copy(fontSize = 14.sp)

@Composable
private fun InfoRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = AlphaTextStyles.dialogBody,
            color = AlphaTheme.colors.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { value() }
    }
}

/** A row value in one style: green ✓ + text when it is a success, otherwise the tone's colour. */
@Composable
private fun InfoStatusValue(text: String, tone: StatusTone) {
    val colors = AlphaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showsCheck(tone)) {
            Image(
                painterResource(Res.drawable.check_24px),
                contentDescription = null,
                colorFilter = ColorFilter.tint(statusIconColor(tone, colors)),
                modifier = Modifier.size(18.dp),
            )
        }
        Text(text, style = InfoValueStyle, color = statusToneColor(tone, colors), maxLines = 1)
    }
}

/** Compact in-row action (32 dp capsule, 48 dp touch target). */
@Composable
private fun InfoChip(text: String, role: CapsuleRole, onClick: () -> Unit) {
    CapsuleButton(text = text, onClick = onClick, role = role, size = CapsuleSize.Small)
}

@Composable
private fun SettingValue(display: SettingDisplay) {
    InfoStatusValue(
        text = stringResource(
            when (display) {
                SettingDisplay.Enabled -> Res.string.a1_setting_enabled
                SettingDisplay.Off -> Res.string.a1_setting_off
                SettingDisplay.Unsupported -> Res.string.a1_setting_unsupported
                SettingDisplay.Unknown -> Res.string.a1_setting_unknown
            },
        ),
        tone = settingTone(display),
    )
}

/** In-app rename (CapsuleDialog form). A blank name would erase the camera's identity in the list. */
@Composable
internal fun RenameCameraDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(currentName) { mutableStateOf(currentName) }
    CapsuleDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.m2_rename_title),
        confirm = DialogAction(
            stringResource(Res.string.rename_camera_save),
            onClick = { onConfirm(text) },
            enabled = text.isNotBlank(),
        ),
        dismiss = DialogAction(stringResource(Res.string.cancel_button), onClick = onDismiss),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(Res.string.rename_camera_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(Res.string.rename_camera_hint),
                style = AlphaTextStyles.statusLabel,
                color = AlphaTheme.colors.onSurfaceVariant,
            )
        }
    }
}
