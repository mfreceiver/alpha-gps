package com.sasch.cameragps.sharednew.ui.devicelist

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.camera_24px
import cameragps.sharednew.generated.resources.cancel
import cameragps.sharednew.generated.resources.delete
import cameragps.sharednew.generated.resources.delete_24px
import cameragps.sharednew.generated.resources.delete_device
import cameragps.sharednew.generated.resources.delete_device_confirmation
import cameragps.sharednew.generated.resources.enable_pairing_mode_continue
import cameragps.sharednew.generated.resources.enable_pairing_mode_message
import cameragps.sharednew.generated.resources.enable_pairing_mode_title
import cameragps.sharednew.generated.resources.guide_open_button
import cameragps.sharednew.generated.resources.keyboard_arrow_right_24px
import cameragps.sharednew.generated.resources.location_linking_disabled_by_camera
import cameragps.sharednew.generated.resources.m2_bluetooth_24px
import cameragps.sharednew.generated.resources.m2_list_connected_syncing
import cameragps.sharednew.generated.resources.m2_list_not_connected
import cameragps.sharednew.generated.resources.m2_list_section_paired
import cameragps.sharednew.generated.resources.nearby_cameras
import cameragps.sharednew.generated.resources.not_paired_tap_to_pair_again
import cameragps.sharednew.generated.resources.show_details
import cameragps.sharednew.generated.resources.tap_to_connect
import cameragps.sharednew.generated.resources.transmission_active
import cameragps.sharednew.generated.resources.transmission_inactive
import com.sasch.cameragps.sharednew.bluetooth.BluetoothDeviceInfo
import com.sasch.cameragps.sharednew.ui.components.ScrollbarLazyColumn
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSurface
import com.sasch.cameragps.sharednew.ui.components.glass.StatusDot
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTextStyles
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The shared device list (design v3): "Paired" / "Nearby" sections of glass rows (no divider),
 * swipe-to-delete on paired rows, and the delete-confirm and tap-to-pair [CapsuleDialog]s.
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
    onDelete: (BluetoothDeviceInfo) -> Unit,
    onOpenDetails: (BluetoothDeviceInfo) -> Unit,
    onOpenTroubleshooting: () -> Unit,
) {
    var deviceToDelete by remember { mutableStateOf<BluetoothDeviceInfo?>(null) }
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

    deviceToDelete?.let { device ->
        CapsuleDialog(
            onDismissRequest = { deviceToDelete = null },
            title = stringResource(Res.string.delete_device),
            message = stringResource(Res.string.delete_device_confirmation, device.name),
            icon = painterResource(Res.drawable.delete_24px),
            destructive = true,
            confirm = DialogAction(stringResource(Res.string.delete), onClick = {
                onDelete(device)
                deviceToDelete = null
            }),
            dismiss = DialogAction(stringResource(Res.string.cancel), onClick = { deviceToDelete = null }),
        )
    }

    val savedDevices = devices.filter { it.isSaved }
    val nearbyDevices = devices.filter { !it.isSaved }

    ScrollbarLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AlphaDimens.screenMargin),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = contentPadding,
    ) {
        if (savedDevices.isNotEmpty()) {
            item(key = "header_saved") {
                SectionHeader(title = stringResource(Res.string.m2_list_section_paired))
            }
            items(savedDevices, key = { it.identifier }) { device ->
                SwipeToDeleteDeviceCard(
                    device = device,
                    item = items[device.identifier.uppercase()],
                    hapticsEnabled = hapticsEnabled,
                    onConnect = { onConnect(device) },
                    onDeleteRequest = { deviceToDelete = device },
                    onOpenDetails = { onOpenDetails(device) },
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
                    colorFilter = ColorFilter.tint(AlphaTheme.colors.primary),
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = title,
                    style = AlphaTextStyles.dialogTitle,
                    color = AlphaTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message,
                    style = AlphaTextStyles.dialogBody,
                    color = AlphaTheme.colors.onSurfaceVariant,
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
        style = AlphaTextStyles.statusLabel.copy(fontSize = 13.sp),
        color = AlphaTheme.colors.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun SwipeToDeleteDeviceCard(
    device: BluetoothDeviceInfo,
    item: DeviceListItem?,
    hapticsEnabled: Boolean,
    onConnect: () -> Unit,
    onDeleteRequest: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenTroubleshooting: () -> Unit,
) {

    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDeleteRequest()

            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val isActive = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val colors = AlphaTheme.colors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = if (isActive) colors.errorContainer else colors.surfaceContainerHigh,
                        shape = AlphaShapes.row,
                    ),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Image(
                    painter = painterResource(Res.drawable.delete_24px),
                    contentDescription = stringResource(Res.string.delete_device),
                    colorFilter = ColorFilter.tint(colors.onErrorContainer),
                    modifier = Modifier.padding(end = 24.dp).size(24.dp),
                )
            }
        },
    ) {
        DeviceCard(
            device = device,
            item = item,
            hapticsEnabled = hapticsEnabled,
            onConnect = onConnect,
            onOpenDetails = onOpenDetails,
            onOpenTroubleshooting = onOpenTroubleshooting,
        )
    }
}


@Composable
private fun DeviceCard(
    device: BluetoothDeviceInfo,
    item: DeviceListItem?,
    hapticsEnabled: Boolean,
    onConnect: () -> Unit,
    onOpenDetails: () -> Unit,
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
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Icon tile: tinted by state (connected / problem / neutral).
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        when {
                            hasProblem -> colors.errorContainer
                            device.isConnected -> colors.primaryContainer
                            else -> colors.surfaceContainerHigh
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(Res.drawable.camera_24px),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(
                        when {
                            hasProblem -> colors.onErrorContainer
                            device.isConnected -> colors.onPrimaryContainer
                            else -> colors.onSurfaceVariant
                        },
                    ),
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 14.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = device.name,
                    style = AlphaTextStyles.buttonLarge,
                    color = colors.onSurface,
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
                        style = AlphaTextStyles.statusLabel.copy(fontSize = 13.sp),
                        color = if (device.isConnected) colors.success else colors.onSurfaceVariant,
                    )
                }
                if (!device.isPaired) {
                    Text(
                        text = stringResource(Res.string.not_paired_tap_to_pair_again),
                        style = AlphaTextStyles.statusLabel.copy(fontSize = 13.sp),
                        color = colors.error,
                    )
                }
                if (item?.locationDisabledByCamera == true) {
                    Text(
                        text = stringResource(Res.string.location_linking_disabled_by_camera),
                        style = AlphaTextStyles.statusLabel.copy(fontSize = 13.sp),
                        color = colors.error,
                    )
                    TextButton(
                        onClick = onOpenTroubleshooting,
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Text(
                            stringResource(Res.string.guide_open_button),
                            style = AlphaTextStyles.button,
                            color = colors.primary,
                        )
                    }
                }
            }
            GlassIconButton(
                icon = painterResource(Res.drawable.keyboard_arrow_right_24px),
                contentDescription = stringResource(Res.string.show_details),
                onClick = onOpenDetails,
            )
        }
    }
}
