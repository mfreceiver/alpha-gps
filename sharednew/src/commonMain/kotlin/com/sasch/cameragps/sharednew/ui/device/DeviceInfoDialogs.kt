package com.sasch.cameragps.sharednew.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.check_circle_24px
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.a1_bt_connected_short
import cameragps.sharednew.generated.resources.a1_bt_connecting_short
import cameragps.sharednew.generated.resources.a1_bt_disconnected_short
import cameragps.sharednew.generated.resources.a1_bt_disconnecting_short
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
import cameragps.sharednew.generated.resources.delete_24px
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
import cameragps.sharednew.generated.resources.m2_schedule_24px
import cameragps.sharednew.generated.resources.m2_warning_24px
import cameragps.sharednew.generated.resources.q_alert_time_sync_failed
import cameragps.sharednew.generated.resources.q_alert_time_sync_rejected
import cameragps.sharednew.generated.resources.q_info_auto_area_off
import cameragps.sharednew.generated.resources.q_info_time_sync
import cameragps.sharednew.generated.resources.q_info_time_sync_applied
import cameragps.sharednew.generated.resources.q_info_time_sync_unconfirmed
import cameragps.sharednew.generated.resources.q_info_auto_time_area
import cameragps.sharednew.generated.resources.q_info_auto_time_off
import cameragps.sharednew.generated.resources.remove
import cameragps.sharednew.generated.resources.rename_camera_hint
import cameragps.sharednew.generated.resources.rename_camera_label
import cameragps.sharednew.generated.resources.rename_camera_save
import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSettingState
import com.sasch.cameragps.sharednew.bluetooth.status.TimeSyncOutcome
import com.sasch.cameragps.sharednew.bluetooth.status.TimeSyncStatus
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleSize
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassInsetBlock
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSwitch
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTextField
import com.sasch.cameragps.sharednew.ui.components.glass.IconEmphasis
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import com.sasch.cameragps.sharednew.ui.components.glass.statusToneColor
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlinx.coroutines.delay
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.wrapContentHeight
import cameragps.sharednew.generated.resources.bg_conditions_title

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
 * ✕ share the header row; below it the name with ✎ (rename) on the left and the link control on the
 * right (Disconnect → confirmation → [DeviceDetailViewModel.disconnect], the same persisted manual
 * disconnect as before; Cancel while connecting; Connect when off), then text-only status rows —
 * Bluetooth state, GPS writing, the camera's auto time & area state in one row (read-only, the app
 * keeps them on) — the keep-active switch and "Remove camera". No address, MTU, firmware, serial or
 * help here. Used by the device page and by the device list's ⓘ (also for a camera that is not
 * connected). Delete only ever goes through [onDelete] after the confirmation route; a null
 * [onDelete] hides it.
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
            val live by remember(deviceId) { viewModel.liveStatus(deviceId) }.collectAsState(initial = null)
            val now by rememberTickingNowMillis()
            val openBackgroundConditions = LocalOpenBackgroundConditions.current
            DeviceInfoDialog(
                deviceName = deviceName,
                session = session,
                pending = pending,
                timeSync = live?.timeSync,
                nowMillis = now,
                onOpenBackgroundConditions = openBackgroundConditions?.let { open ->
                    {
                        // Leave the dialog first so Back from the explainer returns to the page.
                        onRoute(DeviceDialogRoute.None)
                        open()
                    }
                },
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
internal fun DeviceInfoDialog(
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
    timeSync: TimeSyncStatus? = null,
    nowMillis: Long = 0L,
    /** Opens the "Background & running conditions" explainer; null hides the link. */
    onOpenBackgroundConditions: (() -> Unit)? = null,
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
            val status = deviceConnectionStatus(session)
            val headline = connectionHeadline(status, pending)

            // Name with ✎ right beside it on the left, the link control on the right. A long name
            // is cut with an ellipsis; the capsule keeps its full width.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        deviceName,
                        style = AlphaType.Heading,
                        color = colors.textPrimary,
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
                    confirmed = headline == ConnectionHeadline.Connected,
                )
            }

            InfoRow(stringResource(Res.string.m2_info_location)) {
                val writing = locationWritingActive(session)
                InfoStatusValue(
                    stringResource(if (writing) Res.string.m2_info_location_active else Res.string.m2_info_location_idle),
                    tone = if (writing) StatusTone.Success else StatusTone.Neutral,
                    confirmed = writing,
                )
            }
            InfoRow(stringResource(Res.string.q_info_auto_time_area)) {
                AutoCorrectionValue(autoCorrectionSummary(session))
            }
            // Positive / neutral confirmation of the last time correction (the warning cases
            // reuse the status card's wording).
            timeSyncInfo(timeSync, nowMillis)?.let { info ->
                InfoRow(stringResource(Res.string.q_info_time_sync)) { TimeSyncValue(info) }
            }

            GlassInsetBlock(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // The whole row is the toggle (one target, one announcement); the switch only draws.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = keepActive,
                            role = Role.Switch,
                            onValueChange = onKeepActiveChange,
                        ),
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                stringResource(Res.string.device_info_keep_active),
                                style = AlphaType.Label,
                                color = colors.textPrimary,
                            )
                            Text(
                                stringResource(Res.string.m2_info_keep_active_hint),
                                style = AlphaType.Caption,
                                color = colors.textSecondary,
                            )
                        }
                        GlassSwitch(checked = keepActive, onCheckedChange = null)
                    }
                    if (onOpenBackgroundConditions != null) {
                        // Its own target below the toggle row (so it never flips the switch); the text
                        // wraps, so 200 % font scale grows the row instead of cutting the label.
                        Text(
                            stringResource(Res.string.bg_conditions_title),
                            style = AlphaType.Label,
                            color = colors.accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = AlphaDimens.minTouchTarget)
                                .clickable(role = Role.Button, onClick = onOpenBackgroundConditions)
                                .wrapContentHeight(Alignment.CenterVertically),
                        )
                    }
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

private val InfoValueStyle = AlphaType.Body

/**
 * Opens the app's "Background & running conditions" explainer. Provided by the host (the page lives
 * in the app module); null = no such page, the dialog shows no link.
 */
val LocalOpenBackgroundConditions = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Wall clock that refreshes every [tickMillis] so relative ages in a dialog do not freeze. */
@Composable
internal fun rememberTickingNowMillis(tickMillis: Long = 30_000L): State<Long> =
    produceState(Clock.System.now().toEpochMilliseconds()) {
        while (true) {
            delay(tickMillis)
            value = Clock.System.now().toEpochMilliseconds()
        }
    }

@Composable
private fun InfoRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = AlphaType.Body,
            color = AlphaTheme.colors.textSecondary,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { value() }
    }
}

/** Confirmed values use an accessible icon; other values retain their text. */
@Composable
private fun InfoStatusValue(text: String, tone: StatusTone, confirmed: Boolean = false) {
    if (confirmed) {
        Icon(painterResource(Res.drawable.check_circle_24px), contentDescription = text, tint = AlphaTheme.colors.success, modifier = Modifier.size(20.dp))
    } else {
        Text(text, style = InfoValueStyle, color = statusToneColor(tone, AlphaTheme.colors), maxLines = 1)
    }
}

/** What the info dialog's time-sync row shows; [atMillis] is when the outcome was reached. */
internal enum class TimeSyncInfoKind { Applied, Unconfirmed, Rejected, Failed }

internal data class TimeSyncInfo(val kind: TimeSyncInfoKind, val age: RelativeAge)

/** Outcomes that never ran, are still running or are unsupported say nothing and hide the row. */
internal fun timeSyncInfo(status: TimeSyncStatus?, nowMillis: Long): TimeSyncInfo? {
    val kind = when (status?.outcome) {
        TimeSyncOutcome.Applied -> TimeSyncInfoKind.Applied
        TimeSyncOutcome.Unconfirmed -> TimeSyncInfoKind.Unconfirmed
        TimeSyncOutcome.Rejected -> TimeSyncInfoKind.Rejected
        TimeSyncOutcome.Failed -> TimeSyncInfoKind.Failed
        else -> return null
    }
    return TimeSyncInfo(kind, relativeAge(status.atMillis, nowMillis))
}

/** Icon beside the text (never colour alone): applied = check, unconfirmed = neutral clock, errors = warning. */
@Composable
private fun TimeSyncValue(info: TimeSyncInfo) {
    val colors = AlphaTheme.colors
    val age = ageText(info.age)
    val text = when (info.kind) {
        TimeSyncInfoKind.Applied -> stringResource(Res.string.q_info_time_sync_applied, age ?: "")
        TimeSyncInfoKind.Unconfirmed -> stringResource(Res.string.q_info_time_sync_unconfirmed, age ?: "")
        TimeSyncInfoKind.Rejected -> stringResource(Res.string.q_alert_time_sync_rejected)
        TimeSyncInfoKind.Failed -> stringResource(Res.string.q_alert_time_sync_failed)
    }.removeSuffix(" · ").trim()
    val tint = when (info.kind) {
        TimeSyncInfoKind.Applied -> colors.success
        TimeSyncInfoKind.Unconfirmed -> colors.textSecondary
        TimeSyncInfoKind.Rejected, TimeSyncInfoKind.Failed -> statusToneColor(StatusTone.Warning, colors)
    }
    val icon = when (info.kind) {
        TimeSyncInfoKind.Applied -> Res.drawable.check_circle_24px
        TimeSyncInfoKind.Unconfirmed -> Res.drawable.m2_schedule_24px
        TimeSyncInfoKind.Rejected, TimeSyncInfoKind.Failed -> Res.drawable.m2_warning_24px
    }
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    Text(text, style = InfoValueStyle, color = if (info.kind == TimeSyncInfoKind.Unconfirmed) colors.textSecondary else tint)
}

/** Compact in-row action (32 dp capsule, 48 dp touch target). */
@Composable
private fun InfoChip(text: String, role: CapsuleRole, onClick: () -> Unit) {
    CapsuleButton(text = text, onClick = onClick, role = role, size = CapsuleSize.Small)
}

@Composable
private fun AutoCorrectionValue(summary: AutoCorrectionSummary) {
    InfoStatusValue(
        text = stringResource(
            when (summary) {
                AutoCorrectionSummary.Enabled -> Res.string.a1_setting_enabled
                AutoCorrectionSummary.TimeNotEnabled -> Res.string.q_info_auto_time_off
                AutoCorrectionSummary.AreaNotEnabled -> Res.string.q_info_auto_area_off
                AutoCorrectionSummary.NeitherEnabled -> Res.string.a1_setting_off
                AutoCorrectionSummary.Unsupported -> Res.string.a1_setting_unsupported
                AutoCorrectionSummary.Unknown -> Res.string.a1_setting_unknown
            },
        ),
        tone = autoCorrectionTone(summary),
        confirmed = summary == AutoCorrectionSummary.Enabled,
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
            GlassTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = stringResource(Res.string.rename_camera_label),
                supportingText = stringResource(Res.string.rename_camera_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
