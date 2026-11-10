package com.sasch.cameragps.sharednew.ui.device

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.cancel
import cameragps.sharednew.generated.resources.cancel_button
import cameragps.sharednew.generated.resources.device_connection_connect
import cameragps.sharednew.generated.resources.m2_age_days
import cameragps.sharednew.generated.resources.m2_age_hours
import cameragps.sharednew.generated.resources.m2_age_just_now
import cameragps.sharednew.generated.resources.m2_age_minutes
import cameragps.sharednew.generated.resources.m2_battery_unknown
import cameragps.sharednew.generated.resources.m2_battery_value
import cameragps.sharednew.generated.resources.m2_battery_value_hours
import cameragps.sharednew.generated.resources.m2_battery_value_minutes
import cameragps.sharednew.generated.resources.m2_bluetooth_24px
import cameragps.sharednew.generated.resources.m2_bluetooth_disabled_24px
import cameragps.sharednew.generated.resources.m2_bt_auto_reconnect
import cameragps.sharednew.generated.resources.m2_bt_connected
import cameragps.sharednew.generated.resources.m2_bt_connecting
import cameragps.sharednew.generated.resources.m2_bt_disconnected
import cameragps.sharednew.generated.resources.m2_bt_disconnecting
import cameragps.sharednew.generated.resources.m2_bt_manual_off
import cameragps.sharednew.generated.resources.m2_card_location
import cameragps.sharednew.generated.resources.m2_cell_last_write
import cameragps.sharednew.generated.resources.m2_cell_signal
import cameragps.sharednew.generated.resources.m2_cell_written
import cameragps.sharednew.generated.resources.m2_disconnect_bt
import cameragps.sharednew.generated.resources.m2_disconnect_bt_confirm
import cameragps.sharednew.generated.resources.m2_disconnect_bt_message
import cameragps.sharednew.generated.resources.m2_disconnect_bt_title
import cameragps.sharednew.generated.resources.m2_error_24px
import cameragps.sharednew.generated.resources.m2_photo_library_24px
import cameragps.sharednew.generated.resources.m2_photo_transfer
import cameragps.sharednew.generated.resources.m2_photo_transfer_bt_failed
import cameragps.sharednew.generated.resources.m2_photo_transfer_hint
import cameragps.sharednew.generated.resources.m2_photo_transfer_hint_bt_first
import cameragps.sharednew.generated.resources.m2_schedule_24px
import cameragps.sharednew.generated.resources.m2_signal_24px
import cameragps.sharednew.generated.resources.m2_signal_excellent
import cameragps.sharednew.generated.resources.m2_signal_fair
import cameragps.sharednew.generated.resources.m2_signal_good
import cameragps.sharednew.generated.resources.m2_signal_poor
import cameragps.sharednew.generated.resources.m2_time_sync_applied
import cameragps.sharednew.generated.resources.m2_time_sync_failed
import cameragps.sharednew.generated.resources.m2_time_sync_in_progress
import cameragps.sharednew.generated.resources.m2_time_sync_not_run
import cameragps.sharednew.generated.resources.m2_time_sync_rejected
import cameragps.sharednew.generated.resources.m2_time_sync_unconfirmed
import cameragps.sharednew.generated.resources.m2_time_sync_unsupported
import cameragps.sharednew.generated.resources.m2_upload_24px
import cameragps.sharednew.generated.resources.m2_value_none
import com.sasch.cameragps.sharednew.bluetooth.status.CameraLiveStatus
import com.sasch.cameragps.sharednew.bluetooth.status.SignalLevel
import com.sasch.cameragps.sharednew.bluetooth.status.TimeSyncOutcome
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleSize
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.IconEmphasis
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTag
import com.sasch.cameragps.sharednew.ui.components.glass.StatusItem
import com.sasch.cameragps.sharednew.ui.components.glass.StatusLine
import com.sasch.cameragps.sharednew.ui.components.glass.StatusRow
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTextStyles
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.WindowClass
import com.sasch.cameragps.sharednew.ui.theme.windowClassOf
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Shared device page body (design v3, task M2). The host draws the top bar (title = camera name,
 * Ⓘ) and the [com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground].
 *
 * - Connection card: status line (Bluetooth state; while connected the time-correction state is
 *   its supporting text) with the connection control in a fixed trailing slot — a small
 *   disconnect icon (confirmed in a danger dialog), "Cancel" while connecting, "Connect" when off.
 *   A manual disconnect is persisted by [DeviceDetailViewModel.disconnect]; nothing reconnects
 *   until the user taps Connect.
 * - Location card: one row of three cells (signal / written / last write).
 * - "Photo transfer" pinned to the bottom: opens the Wi-Fi flow ([onWifiRemote]) once Bluetooth is
 *   ready, connecting Bluetooth first when needed ([DeviceDetailViewModel.requestPhotoTransfer]).
 *
 * Auto time correction / auto area adjustment have no switches: they are kept on automatically
 * ([DeviceDetailViewModel.enforceAutoCorrection]). The page never opens a Wi-Fi session on its
 * own (the battery shown is whatever the last photo transfer cached).
 */
@Composable
fun DeviceDetailContent(
    viewModel: DeviceDetailViewModel,
    deviceId: String,
    deviceName: String? = null,
    modifier: Modifier = Modifier,
    onDeviceReEnabled: (() -> Unit)? = null,
    onWifiRemote: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state = viewModel.uiState.collectAsState().value
    val sessions by viewModel.sessions.collectAsState()
    val session = sessions[deviceId.uppercase()]
    val status = deviceConnectionStatus(session)
    val pending by viewModel.pending.collectAsState()
    val photoTransfer by viewModel.photoTransfer.collectAsState()
    val live by remember(deviceId) { viewModel.liveStatus(deviceId) }.collectAsState(initial = null)
    val now by produceState(Clock.System.now().toEpochMilliseconds()) {
        while (true) {
            delay(TICK_MILLIS)
            value = Clock.System.now().toEpochMilliseconds()
        }
    }
    var confirmDisconnect by rememberSaveable(deviceId) { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshCameraSettings(deviceId)
    }

    // Signal polling only while the page is visible.
    LifecycleStartEffect(deviceId) {
        val release = viewModel.acquireSignalMonitoring(deviceId)
        onStopOrDispose { release() }
    }

    LaunchedEffect(deviceId, deviceName) {
        viewModel.load(deviceId, deviceName, onDeviceReEnabled)
    }

    // Keep the camera's auto time/area settings on whenever the camera reports them off.
    LaunchedEffect(session?.phase, session?.autoTimeCorrection, session?.autoAreaAdjustment) {
        viewModel.enforceAutoCorrection(deviceId)
    }

    // Bluetooth became ready for a pending "Photo transfer" tap: hand over to the Wi-Fi flow.
    LaunchedEffect(photoTransfer) {
        if (photoTransfer == PhotoTransferEntry.Ready) {
            viewModel.photoTransferOpened()
            onWifiRemote?.invoke()
        }
    }

    // Leaving the page drops a pending "connect, then open" (Bluetooth itself stays as it is).
    DisposableEffect(deviceId) {
        onDispose { viewModel.abandonPhotoTransfer() }
    }

    val connectionCard: @Composable () -> Unit = {
        ConnectionCard(
            headline = connectionHeadline(status, pending),
            action = connectionAction(status, pending),
            manuallyOff = !state.isDeviceEnabled,
            live = live,
            nowMillis = now,
            onConnect = { viewModel.connect(deviceId) },
            onCancelConnecting = { viewModel.cancelConnecting(deviceId) },
            onDisconnect = { confirmDisconnect = true },
        )
    }
    val locationCard: @Composable () -> Unit = { LocationCard(live = live, nowMillis = now) }
    val photoEntry: @Composable (Modifier) -> Unit = { entryModifier ->
        if (onWifiRemote != null) {
            PhotoTransferEntryBlock(
                entry = photoTransfer,
                bluetoothReady = status == DeviceConnectionStatus.Connected,
                enabled = pending != PendingConnection.Disconnecting,
                onClick = { viewModel.requestPhotoTransfer(deviceId) },
                modifier = entryModifier,
            )
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().padding(contentPadding)) {
        val windowClass = windowClassOf(maxWidth.value, maxHeight.value)
        if (windowClass == WindowClass.Landscape) {
            // C: cards on the left, the photo entry on the right (thumb reach on both sides).
            Row(
                Modifier.fillMaxSize().padding(horizontal = AlphaDimens.screenMargin, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(AlphaDimens.screenMargin),
            ) {
                Column(
                    Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap),
                ) {
                    connectionCard()
                    locationCard()
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    photoEntry(Modifier.fillMaxWidth())
                }
            }
        } else {
            // A (portrait) / B (near square, width-limited and centred).
            val maxContentWidth = if (windowClass == WindowClass.NearSquare) 560.dp else maxWidth
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = AlphaDimens.screenMargin)
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .widthIn(max = maxContentWidth)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap),
                ) {
                    connectionCard()
                    locationCard()
                }
                photoEntry(Modifier.widthIn(max = maxContentWidth).fillMaxWidth())
            }
        }
    }

    if (confirmDisconnect) {
        CapsuleDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = stringResource(Res.string.m2_disconnect_bt_title),
            message = stringResource(Res.string.m2_disconnect_bt_message),
            icon = painterResource(Res.drawable.m2_bluetooth_disabled_24px),
            destructive = true,
            confirm = DialogAction(stringResource(Res.string.m2_disconnect_bt_confirm), onClick = {
                confirmDisconnect = false
                viewModel.disconnect(deviceId)
            }),
            dismiss = DialogAction(stringResource(Res.string.cancel_button), onClick = {
                confirmDisconnect = false
            }),
        )
    }
}

@Composable
private fun ConnectionCard(
    headline: ConnectionHeadline,
    action: ConnectionAction,
    manuallyOff: Boolean,
    live: CameraLiveStatus?,
    nowMillis: Long,
    onConnect: () -> Unit,
    onCancelConnecting: () -> Unit,
    onDisconnect: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tone = when (headline) {
                ConnectionHeadline.Connected -> StatusTone.Success
                ConnectionHeadline.Connecting, ConnectionHeadline.Disconnecting -> StatusTone.Active
                ConnectionHeadline.Disconnected -> StatusTone.Neutral
            }
            val title = stringResource(
                when (headline) {
                    ConnectionHeadline.Connected -> Res.string.m2_bt_connected
                    ConnectionHeadline.Connecting -> Res.string.m2_bt_connecting
                    ConnectionHeadline.Disconnecting -> Res.string.m2_bt_disconnecting
                    ConnectionHeadline.Disconnected -> Res.string.m2_bt_disconnected
                },
            )
            val supporting = when (headline) {
                ConnectionHeadline.Connected -> timeSyncText(live?.timeSync?.outcome ?: TimeSyncOutcome.NotRun)
                ConnectionHeadline.Disconnected ->
                    stringResource(if (manuallyOff) Res.string.m2_bt_manual_off else Res.string.m2_bt_auto_reconnect)
                else -> null
            }
            StatusLine(
                title = title,
                tone = tone,
                supportingText = supporting,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            // Fixed trailing slot: the control changes, its place does not.
            Box(
                Modifier.padding(start = 8.dp).heightIn(min = AlphaDimens.minTouchTarget),
                contentAlignment = Alignment.CenterEnd,
            ) {
                when (action) {
                    ConnectionAction.Disconnect -> GlassIconButton(
                        icon = painterResource(Res.drawable.m2_bluetooth_disabled_24px),
                        contentDescription = stringResource(Res.string.m2_disconnect_bt),
                        onClick = onDisconnect,
                        emphasis = IconEmphasis.Subtle,
                    )

                    ConnectionAction.CancelConnecting -> TextButton(
                        onClick = onCancelConnecting,
                        modifier = Modifier.heightIn(min = AlphaDimens.minTouchTarget),
                    ) {
                        Text(
                            stringResource(Res.string.cancel),
                            style = AlphaTextStyles.button,
                            color = AlphaTheme.colors.primary,
                        )
                    }

                    ConnectionAction.Connect -> CapsuleButton(
                        text = stringResource(Res.string.device_connection_connect),
                        onClick = onConnect,
                        role = CapsuleRole.Tonal,
                        icon = painterResource(Res.drawable.m2_bluetooth_24px),
                    )

                    ConnectionAction.None -> CircularProgressIndicator(
                        modifier = Modifier.padding(12.dp).size(24.dp),
                        strokeWidth = 2.dp,
                        color = AlphaTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
        InfoTag(text = batteryText(batteryDisplay(live?.battery, nowMillis)))
    }
}

@Composable
private fun LocationCard(live: CameraLiveStatus?, nowMillis: Long) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(Res.string.m2_card_location),
            style = AlphaTextStyles.statusLabel,
            color = AlphaTheme.colors.onSurfaceVariant,
        )
        val none = stringResource(Res.string.m2_value_none)
        val signal = if (live?.connected == true) live.signalLevel else SignalLevel.Unknown
        StatusRow(
            items = listOf(
                StatusItem(
                    label = stringResource(Res.string.m2_cell_signal),
                    value = signalText(signal) ?: none,
                    icon = painterResource(Res.drawable.m2_signal_24px),
                    tone = if (signal == SignalLevel.Poor) StatusTone.Warning else StatusTone.Neutral,
                ),
                StatusItem(
                    label = stringResource(Res.string.m2_cell_written),
                    value = live?.gps?.writtenCount?.toString() ?: none,
                    icon = painterResource(Res.drawable.m2_upload_24px),
                ),
                StatusItem(
                    label = stringResource(Res.string.m2_cell_last_write),
                    value = ageText(relativeAge(live?.lastWriteAtMillis, nowMillis)) ?: none,
                    icon = painterResource(Res.drawable.m2_schedule_24px),
                ),
            ),
        )
    }
}

@Composable
private fun PhotoTransferEntryBlock(
    entry: PhotoTransferEntry,
    bluetoothReady: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (entry == PhotoTransferEntry.Failed) {
            // Red icon + short text, no fill; tapping the button again is the retry.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Image(
                    painterResource(Res.drawable.m2_error_24px),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(AlphaTheme.colors.error),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(Res.string.m2_photo_transfer_bt_failed),
                    style = AlphaTextStyles.statusLabel.copy(fontSize = AlphaTextStyles.dialogBody.fontSize),
                    color = AlphaTheme.colors.error,
                )
            }
        } else {
            Text(
                stringResource(
                    if (bluetoothReady) Res.string.m2_photo_transfer_hint else Res.string.m2_photo_transfer_hint_bt_first,
                ),
                style = AlphaTextStyles.statusLabel.copy(fontSize = AlphaTextStyles.dialogBody.fontSize),
                color = AlphaTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        CapsuleButton(
            text = stringResource(Res.string.m2_photo_transfer),
            onClick = onClick,
            role = CapsuleRole.Primary,
            size = CapsuleSize.Hero,
            icon = painterResource(Res.drawable.m2_photo_library_24px),
            enabled = enabled,
            loading = entry == PhotoTransferEntry.ConnectingBluetooth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun timeSyncText(outcome: TimeSyncOutcome): String = stringResource(
    when (outcome) {
        TimeSyncOutcome.Applied -> Res.string.m2_time_sync_applied
        TimeSyncOutcome.InProgress -> Res.string.m2_time_sync_in_progress
        TimeSyncOutcome.Unconfirmed -> Res.string.m2_time_sync_unconfirmed
        TimeSyncOutcome.Rejected -> Res.string.m2_time_sync_rejected
        TimeSyncOutcome.Failed -> Res.string.m2_time_sync_failed
        TimeSyncOutcome.Unsupported -> Res.string.m2_time_sync_unsupported
        TimeSyncOutcome.NotRun -> Res.string.m2_time_sync_not_run
    },
)

@Composable
private fun signalText(level: SignalLevel): String? = when (level) {
    SignalLevel.Excellent -> stringResource(Res.string.m2_signal_excellent)
    SignalLevel.Good -> stringResource(Res.string.m2_signal_good)
    SignalLevel.Fair -> stringResource(Res.string.m2_signal_fair)
    SignalLevel.Poor -> stringResource(Res.string.m2_signal_poor)
    SignalLevel.Unknown -> null
}

@Composable
private fun ageText(age: RelativeAge): String? = when (age) {
    RelativeAge.Never -> null
    RelativeAge.JustNow -> stringResource(Res.string.m2_age_just_now)
    is RelativeAge.Minutes -> stringResource(Res.string.m2_age_minutes, age.value.toInt())
    is RelativeAge.Hours -> stringResource(Res.string.m2_age_hours, age.value.toInt())
    is RelativeAge.Days -> stringResource(Res.string.m2_age_days, age.value.toInt())
}

@Composable
private fun batteryText(display: BatteryDisplay): String = when (display) {
    BatteryDisplay.Unknown -> stringResource(Res.string.m2_battery_unknown)
    is BatteryDisplay.Value -> {
        val percent = "${display.percent}%"
        val minutes = display.ageMinutes
        when {
            minutes == null -> stringResource(Res.string.m2_battery_value, percent)
            minutes < 60 -> stringResource(Res.string.m2_battery_value_minutes, percent, minutes.toInt())
            else -> stringResource(Res.string.m2_battery_value_hours, percent, (minutes / 60).toInt())
        }
    }
}

private const val TICK_MILLIS = 30_000L
