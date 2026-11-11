package com.sasch.cameragps.sharednew.ui.devicelist

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.camera_24px
import cameragps.sharednew.generated.resources.cancel
import cameragps.sharednew.generated.resources.enable_pairing_mode_continue
import cameragps.sharednew.generated.resources.enable_pairing_mode_message
import cameragps.sharednew.generated.resources.enable_pairing_mode_title
import cameragps.sharednew.generated.resources.guide_open_button
import cameragps.sharednew.generated.resources.info_24px
import cameragps.sharednew.generated.resources.location_linking_disabled_by_camera
import cameragps.sharednew.generated.resources.m2_bluetooth_24px
import cameragps.sharednew.generated.resources.m2_list_connected_syncing
import cameragps.sharednew.generated.resources.m2_list_not_connected
import cameragps.sharednew.generated.resources.m2_list_section_paired
import cameragps.sharednew.generated.resources.nearby_cameras
import cameragps.sharednew.generated.resources.not_paired_tap_to_pair_again
import cameragps.sharednew.generated.resources.q_list_info_a11y
import cameragps.sharednew.generated.resources.q_list_open_device
import cameragps.sharednew.generated.resources.tap_to_connect
import cameragps.sharednew.generated.resources.transmission_active
import cameragps.sharednew.generated.resources.transmission_inactive
import com.sasch.cameragps.sharednew.bluetooth.BluetoothDeviceInfo
import com.sasch.cameragps.sharednew.ui.components.ScrollbarLazyColumn
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleSize
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconTile
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSurface
import com.sasch.cameragps.sharednew.ui.components.glass.StatusDot
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The shared device list (design v3): "Paired" / "Nearby" sections of glass rows (no divider) and
 * the tap-to-pair [CapsuleDialog]. A paired row opens its device page anywhere on the card; its
 * trailing ⓘ ([onOpenInfo]) opens the host's camera-info dialog, which is also where a camera is
 * removed (no swipe-to-delete).
 * Derived per-row state comes from [DeviceListItem]s looked up by uppercased identifier; platform
 * hosts own the surrounding empty states, navigation and callbacks.
 */
@Composable
fun SharedDeviceList(
    devices: List<BluetoothDeviceInfo>,
    items: Map<String, DeviceListItem>,
    /** User setting: shutter press/completion haptics (default on). */
    hapticsEnabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(top = 8.dp, bottom = 16.dp),
    onConnect: (BluetoothDeviceInfo) -> Unit,
    onOpenInfo: (BluetoothDeviceInfo) -> Unit,
    onOpenDetails: (BluetoothDeviceInfo) -> Unit,
    onOpenTroubleshooting: () -> Unit,
) {
    var deviceToPair by remember { mutableStateOf<BluetoothDeviceInfo?>(null) }

    // Pairing mode hint dialog for first-time connections
    deviceToPair?.let { device ->
        CapsuleDialog(
            onDismissRequest = { deviceToPair = null },
            title = stringResource(Res.string.enable_pairing_mode_title),
            message = stringResource(Res.string.enable_pairing_mode_message),
            icon = painterResource(Res.drawable.m2_bluetooth_24px),
            confirm = DialogAction(stringResource(Res.string.enable_pairing_mode_continue), onClick = {
                onConnect(device)
                deviceToPair = null
            }),
            dismiss = DialogAction(stringResource(Res.string.cancel), onClick = { deviceToPair = null }),
        )
    }

    val savedDevices = devices.filter { it.isSaved }
    val nearbyDevices = devices.filter { !it.isSaved }

    // The screen margin goes into the content padding (not an outer padding + scrollbar gutter):
    // cards then span exactly the width of "Add camera" and the other header elements, and the
    // scroll indicator is drawn inside the margin.
    ScrollbarLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = withScreenMargin(contentPadding, LocalLayoutDirection.current),
        scrollbarGutter = 0.dp,
    ) {
        if (savedDevices.isNotEmpty()) {
            item(key = "header_saved") {
                SectionHeader(title = stringResource(Res.string.m2_list_section_paired))
            }
            items(savedDevices, key = { it.identifier }) { device ->
                DeviceCard(
                    device = device,
                    item = items[device.identifier.uppercase()],
                    hapticsEnabled = hapticsEnabled,
                    onConnect = { onConnect(device) },
                    onOpenDetails = { onOpenDetails(device) },
                    onOpenInfo = { onOpenInfo(device) },
                    onOpenTroubleshooting = onOpenTroubleshooting,
                )
            }
        }

        if (nearbyDevices.isNotEmpty()) {
            item(key = "header_nearby") {
                // Spacing instead of a divider between the sections.
                SectionHeader(
                    title = stringResource(Res.string.nearby_cameras),
                    modifier = if (savedDevices.isNotEmpty()) Modifier.padding(top = 12.dp) else Modifier,
                )
            }
            items(nearbyDevices, key = { it.identifier }) { device ->
                DeviceCard(
                    device = device,
                    item = items[device.identifier.uppercase()],
                    hapticsEnabled = hapticsEnabled,
                    onConnect = { deviceToPair = device },
                    onOpenDetails = { onOpenDetails(device) },
                    onOpenInfo = null,
                    onOpenTroubleshooting = onOpenTroubleshooting,
                )
            }
        }
    }
}

/** Empty / explanatory state: big camera icon, title, message and an optional primary action. */
@Composable
fun EmptyStateCard(
    title: String,
    message: String,
    /** Null hides the action button, for a card that only explains something. */
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = AlphaDimens.screenMargin),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painterResource(Res.drawable.camera_24px),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(AlphaTheme.colors.accent),
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = title,
                    style = AlphaType.Heading,
                    color = AlphaTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message,
                    style = AlphaType.Body,
                    color = AlphaTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                if (actionLabel != null) {
                    CapsuleButton(text = actionLabel, onClick = onAction, role = CapsuleRole.Primary)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = AlphaType.Caption,
        color = AlphaTheme.colors.textSecondary,
        modifier = modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun DeviceCard(
    device: BluetoothDeviceInfo,
    item: DeviceListItem?,
    hapticsEnabled: Boolean,
    onConnect: () -> Unit,
    onOpenDetails: () -> Unit,
    /** Saved rows only: the trailing ⓘ (camera info dialog). Null = no trailing button. */
    onOpenInfo: (() -> Unit)?,
    onOpenTroubleshooting: () -> Unit,
) {
    val colors = AlphaTheme.colors
    val isTransmissionActive = item?.isTransmissionActive == true
    val hasProblem = !device.isPaired || item?.locationDisabledByCamera == true
    GlassSurface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        shape = AlphaShapes.row,
        // Saved devices connect automatically; a tap opens their page. Unsaved devices keep tap-to-pair.
        onClick = { if (device.isSaved) onOpenDetails() else onConnect() },
        onClickLabel = if (device.isSaved) stringResource(Res.string.q_list_open_device) else null,
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (onOpenInfo != null) 4.dp else 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Icon tile: tinted by state (problem / connected / neutral).
            GlassIconTile(
                icon = painterResource(Res.drawable.camera_24px),
                tone = deviceTileTone(hasProblem = hasProblem, connected = device.isConnected),
            )
            Column(
                modifier = Modifier.weight(1f).padding(start = 14.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = device.name,
                    style = AlphaType.Label,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val transmissionStatusDescription = when {
                    item?.locationDisabledByCamera == true ->
                        stringResource(Res.string.location_linking_disabled_by_camera)

                    isTransmissionActive -> stringResource(Res.string.transmission_active)
                    else -> stringResource(Res.string.transmission_inactive)
                }
                val statusText = when {
                    device.isConnected -> stringResource(Res.string.m2_list_connected_syncing)
                    !device.isSaved -> stringResource(Res.string.tap_to_connect)
                    else -> stringResource(Res.string.m2_list_not_connected)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StatusDot(
                        tone = if (isTransmissionActive) StatusTone.Success else StatusTone.Neutral,
                        modifier = Modifier.semantics { contentDescription = transmissionStatusDescription },
                    )
                    Text(
                        text = statusText,
                        style = AlphaType.Caption,
                        color = if (device.isConnected) colors.success else colors.textSecondary,
                    )
                }
                if (!device.isPaired) {
                    Text(
                        text = stringResource(Res.string.not_paired_tap_to_pair_again),
                        style = AlphaType.Caption,
                        color = colors.danger,
                    )
                }
                if (item?.locationDisabledByCamera == true) {
                    Text(
                        text = stringResource(Res.string.location_linking_disabled_by_camera),
                        style = AlphaType.Caption,
                        color = colors.danger,
                    )
                    CapsuleButton(
                        text = stringResource(Res.string.guide_open_button),
                        onClick = onOpenTroubleshooting,
                        role = CapsuleRole.Tonal,
                        size = CapsuleSize.Small,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (onOpenInfo != null) {
                // Its own 48 dp target: consumes the tap, so the card (open page) does not fire.
                GlassIconButton(
                    icon = painterResource(Res.drawable.info_24px),
                    contentDescription = stringResource(Res.string.q_list_info_a11y, device.name),
                    onClick = onOpenInfo,
                )
            }
        }
    }
}

/** Tone of a device row's icon tile: a problem (unpaired / location off) wins over "connected". */
internal fun deviceTileTone(hasProblem: Boolean, connected: Boolean): StatusTone = when {
    hasProblem -> StatusTone.Error
    connected -> StatusTone.Active
    else -> StatusTone.Neutral
}

/**
 * [padding] plus the horizontal screen margin on both sides, so list rows line up with the
 * full-width header elements of the device list (they all sit at [AlphaDimens.screenMargin]).
 */
internal fun withScreenMargin(padding: PaddingValues, layoutDirection: LayoutDirection): PaddingValues =
    PaddingValues(
        start = padding.calculateStartPadding(layoutDirection) + AlphaDimens.screenMargin,
        top = padding.calculateTopPadding(),
        end = padding.calculateEndPadding(layoutDirection) + AlphaDimens.screenMargin,
        bottom = padding.calculateBottomPadding(),
    )
