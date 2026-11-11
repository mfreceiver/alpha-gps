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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.cancel
import cameragps.sharednew.generated.resources.device_connection_connect
import cameragps.sharednew.generated.resources.m2_age_days
import cameragps.sharednew.generated.resources.m2_age_hours
import cameragps.sharednew.generated.resources.m2_age_just_now
import cameragps.sharednew.generated.resources.m2_age_minutes
import cameragps.sharednew.generated.resources.m2_bt_connected
import cameragps.sharednew.generated.resources.m2_bt_connecting
import cameragps.sharednew.generated.resources.m2_bt_disconnected
import cameragps.sharednew.generated.resources.m2_bt_disconnecting
import cameragps.sharednew.generated.resources.m2_card_location
import cameragps.sharednew.generated.resources.m2_cell_last_write
import cameragps.sharednew.generated.resources.m2_error_24px
import cameragps.sharednew.generated.resources.m2_photo_library_24px
import cameragps.sharednew.generated.resources.m2_photo_transfer
import cameragps.sharednew.generated.resources.m2_photo_transfer_bt_failed
import cameragps.sharednew.generated.resources.m2_photo_transfer_hint
import cameragps.sharednew.generated.resources.m2_photo_transfer_hint_bt_first
import cameragps.sharednew.generated.resources.m2_schedule_24px
import cameragps.sharednew.generated.resources.m2_upload_24px
import cameragps.sharednew.generated.resources.m2_value_none
import cameragps.sharednew.generated.resources.m2_gps_fixed_24px
import cameragps.sharednew.generated.resources.q_alert_location_disabled
import cameragps.sharednew.generated.resources.q_alert_location_write_failed
import cameragps.sharednew.generated.resources.q_alert_time_sync_failed
import cameragps.sharednew.generated.resources.q_alert_time_sync_rejected
import cameragps.sharednew.generated.resources.q_cell_location_signal
import cameragps.sharednew.generated.resources.q_cell_location_writes
import cameragps.sharednew.generated.resources.q_location_accuracy
import com.sasch.cameragps.sharednew.bluetooth.location.PhoneLocationFix
import com.sasch.cameragps.sharednew.bluetooth.status.CameraLiveStatus
import com.sasch.cameragps.sharednew.bluetooth.status.SignalLevel
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleSize
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressCircle
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressSize
import com.sasch.cameragps.sharednew.ui.components.glass.StatusItem
import com.sasch.cameragps.sharednew.ui.components.glass.StatusRow
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import com.sasch.cameragps.sharednew.ui.components.glass.statusToneColor
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.WindowClass
import com.sasch.cameragps.sharednew.ui.theme.windowClassOf
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Clock

/**
 * Shared device page body (design v3, task M2). The host draws the top bar (title = camera name,
 * Ⓘ) and the [com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground].
 *
 * - One status card (connection + location transfer as one unit, one glass level and spacing):
 *   the Bluetooth state as the title, with one warning line under it only when something is wrong
 *   ([statusCardAlert]); on the right the cached camera battery (hidden when unknown), the
 *   Bluetooth glyph in its signal ring, and "Cancel" while connecting / "Connect" when off
 *   ([connectionCardTrailing]); then the "Location transfer" caption over three tinted cells
 *   (phone location signal / location writes / last write). The manual disconnect lives in the Ⓘ
 *   dialog ([DeviceDialogs]) and is persisted by [DeviceDetailViewModel.disconnect]; nothing
 *   reconnects until the user taps Connect.
 * - "Photo transfer" pinned to the bottom (52 dp, aligned with the card edges): opens the Wi-Fi
 *   flow ([onWifiRemote]) once Bluetooth is ready, connecting Bluetooth first when needed
 *   ([DeviceDetailViewModel.requestPhotoTransfer]).
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
    val sessions by viewModel.sessions.collectAsState()
    val session = sessions[deviceId.uppercase()]
    val status = deviceConnectionStatus(session)
    val pending by viewModel.pending.collectAsState()
    val photoTransfer by viewModel.photoTransfer.collectAsState()
    val live by remember(deviceId) { viewModel.liveStatus(deviceId) }.collectAsState(initial = null)
    val phoneFix by viewModel.phoneLocation.collectAsState(initial = null)
    val now by produceState(Clock.System.now().toEpochMilliseconds()) {
        while (true) {
            delay(TICK_MILLIS)
            value = Clock.System.now().toEpochMilliseconds()
        }
    }
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

    val statusCard: @Composable () -> Unit = {
        val headline = connectionHeadline(status, pending)
        DeviceStatusCard(
            headline = headline,
            trailing = connectionCardTrailing(status, pending),
            alert = statusCardAlert(
                headline = headline,
                locationDisabledByCamera = session?.locationDisabledByCamera == true,
                gps = live?.gps,
                timeSync = live?.timeSync?.outcome,
            ),
            live = live,
            phoneFix = phoneFix,
            nowMillis = now,
            onConnect = { viewModel.connect(deviceId) },
            onCancelConnecting = { viewModel.cancelConnecting(deviceId) },
        )
    }
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
                    statusCard()
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
                    statusCard()
                }
                photoEntry(Modifier.widthIn(max = maxContentWidth).fillMaxWidth())
            }
        }
    }
}

/**
 * The page's one status card: connection state (+ one warning line only when something is wrong),
 * battery and Bluetooth signal on the right, then the location-transfer metrics. One glass level,
 * one padding and one caption style, so connection and transfer read as one unit.
 */
@Composable
private fun DeviceStatusCard(
    headline: ConnectionHeadline,
    trailing: ConnectionCardTrailing,
    alert: StatusCardAlert,
    live: CameraLiveStatus?,
    phoneFix: PhoneLocationFix?,
    nowMillis: Long,
    onConnect: () -> Unit,
    onCancelConnecting: () -> Unit,
) {
    val colors = AlphaTheme.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val title = stringResource(
                when (headline) {
                    ConnectionHeadline.Connected -> Res.string.m2_bt_connected
                    ConnectionHeadline.Connecting -> Res.string.m2_bt_connecting
                    ConnectionHeadline.Disconnecting -> Res.string.m2_bt_disconnecting
                    ConnectionHeadline.Disconnected -> Res.string.m2_bt_disconnected
                },
            )
            val alertText = statusCardAlertText(alert)
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = AlphaType.Heading, color = colors.textPrimary)
                if (alertText != null) {
                    Text(alertText, style = AlphaType.Caption, color = statusToneColor(StatusTone.Warning, colors))
                }
            }
            Row(
                Modifier.padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val battery = batteryDisplay(live?.battery, nowMillis)
                if (battery is BatteryDisplay.Value) BatteryBlock(battery)
                val connected = trailing == ConnectionCardTrailing.ConnectedIndicator
                BluetoothSignalRing(
                    connected = connected,
                    level = if (connected && live?.connected == true) live.signalLevel else SignalLevel.Unknown,
                )
                // The two actions the page needs stay here; a connected link is a state, not a button
                // (the manual disconnect lives in Ⓘ).
                when (trailing) {
                    ConnectionCardTrailing.ConnectedIndicator -> Unit
                    ConnectionCardTrailing.CancelConnecting -> CapsuleButton(
                        text = stringResource(Res.string.cancel),
                        onClick = onCancelConnecting,
                        role = CapsuleRole.Secondary,
                    )
                    ConnectionCardTrailing.Connect -> CapsuleButton(
                        text = stringResource(Res.string.device_connection_connect),
                        onClick = onConnect,
                        role = CapsuleRole.Tonal,
                    )
                    ConnectionCardTrailing.Busy -> GlassProgressCircle(
                        modifier = Modifier.padding(12.dp),
                        size = GlassProgressSize.Medium,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        LocationMetrics(live = live, phoneFix = phoneFix, nowMillis = nowMillis)
    }
}

/**
 * "Location transfer" caption + three cells: the phone's location signal (fix accuracy radius,
 * [locationSignalLevel]), location writes, last write; icons tinted by meaning.
 */
@Composable
private fun LocationMetrics(live: CameraLiveStatus?, phoneFix: PhoneLocationFix?, nowMillis: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.m2_card_location),
            style = AlphaType.Caption,
            color = AlphaTheme.colors.textSecondary,
        )
        val none = stringResource(Res.string.m2_value_none)
        val fixLevel = locationSignalLevel(phoneFix?.accuracyMeters, phoneFix?.timeMillis, nowMillis)
        StatusRow(
            items = listOf(
                StatusItem(
                    label = stringResource(Res.string.q_cell_location_signal),
                    value = if (fixLevel == LocationSignalLevel.Unknown || phoneFix == null) {
                        none
                    } else {
                        stringResource(Res.string.q_location_accuracy, phoneFix.accuracyMeters.roundToInt().coerceAtLeast(1))
                    },
                    icon = painterResource(Res.drawable.m2_gps_fixed_24px),
                    iconTone = locationSignalTone(fixLevel),
                ),
                StatusItem(
                    label = stringResource(Res.string.q_cell_location_writes),
                    value = live?.gps?.writtenCount?.toString() ?: none,
                    icon = painterResource(Res.drawable.m2_upload_24px),
                    iconTone = WrittenMetricTone,
                ),
                StatusItem(
                    label = stringResource(Res.string.m2_cell_last_write),
                    value = ageText(relativeAge(live?.lastWriteAtMillis, nowMillis)) ?: none,
                    icon = painterResource(Res.drawable.m2_schedule_24px),
                    iconTone = LastWriteMetricTone,
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    colorFilter = ColorFilter.tint(AlphaTheme.colors.danger),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(Res.string.m2_photo_transfer_bt_failed),
                    style = AlphaType.Caption,
                    color = AlphaTheme.colors.danger,
                )
            }
        } else {
            Text(
                stringResource(
                    if (bluetoothReady) Res.string.m2_photo_transfer_hint else Res.string.m2_photo_transfer_hint_bt_first,
                ),
                style = AlphaType.Caption,
                color = AlphaTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CapsuleButton(
            text = stringResource(Res.string.m2_photo_transfer),
            onClick = onClick,
            role = CapsuleRole.Primary,
            size = CapsuleSize.Medium,
            icon = painterResource(Res.drawable.m2_photo_library_24px),
            enabled = enabled,
            loading = entry == PhotoTransferEntry.ConnectingBluetooth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun statusCardAlertText(alert: StatusCardAlert): String? = when (alert) {
    StatusCardAlert.None -> null
    StatusCardAlert.LocationDisabledByCamera -> stringResource(Res.string.q_alert_location_disabled)
    StatusCardAlert.LocationWriteFailing -> stringResource(Res.string.q_alert_location_write_failed)
    StatusCardAlert.TimeSyncFailed -> stringResource(Res.string.q_alert_time_sync_failed)
    StatusCardAlert.TimeSyncRejected -> stringResource(Res.string.q_alert_time_sync_rejected)
}

@Composable
private fun ageText(age: RelativeAge): String? = when (age) {
    RelativeAge.Never -> null
    RelativeAge.JustNow -> stringResource(Res.string.m2_age_just_now)
    is RelativeAge.Minutes -> stringResource(Res.string.m2_age_minutes, age.value.toInt())
    is RelativeAge.Hours -> stringResource(Res.string.m2_age_hours, age.value.toInt())
    is RelativeAge.Days -> stringResource(Res.string.m2_age_days, age.value.toInt())
}

private const val TICK_MILLIS = 30_000L
