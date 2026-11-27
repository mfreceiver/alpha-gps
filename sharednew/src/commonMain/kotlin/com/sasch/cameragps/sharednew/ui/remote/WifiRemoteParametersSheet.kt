package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.baseline_view_list_24
import cameragps.sharednew.generated.resources.close_24px
import cameragps.sharednew.generated.resources.m0_dialog_close
import cameragps.sharednew.generated.resources.refresh_24px
import cameragps.sharednew.generated.resources.remote_params_applied
import cameragps.sharednew.generated.resources.remote_params_done
import cameragps.sharednew.generated.resources.remote_params_empty
import cameragps.sharednew.generated.resources.remote_params_readonly
import cameragps.sharednew.generated.resources.remote_params_refresh
import cameragps.sharednew.generated.resources.remote_params_rejected
import cameragps.sharednew.generated.resources.remote_params_title
import cameragps.sharednew.generated.resources.remote_params_write_failed
import cameragps.sharednew.generated.resources.remote_probe
import cameragps.sharednew.generated.resources.remote_probe_failed
import cameragps.sharednew.generated.resources.remote_probe_reading
import cameragps.sharednew.generated.resources.remote_probe_recorded
import cameragps.sharednew.generated.resources.remote_probe_unsupported
import cameragps.sharednew.generated.resources.remote_prop_aperture
import cameragps.sharednew.generated.resources.remote_prop_drive
import cameragps.sharednew.generated.resources.remote_prop_ev
import cameragps.sharednew.generated.resources.remote_prop_flash
import cameragps.sharednew.generated.resources.remote_prop_focus_mode
import cameragps.sharednew.generated.resources.remote_prop_iso
import cameragps.sharednew.generated.resources.remote_prop_metering
import cameragps.sharednew.generated.resources.remote_prop_mode
import cameragps.sharednew.generated.resources.remote_prop_shutter
import cameragps.sharednew.generated.resources.remote_prop_wb
import cameragps.sharednew.generated.resources.remote_status_remaining_shots
import cameragps.sharednew.generated.resources.remote_val_auto
import cameragps.sharednew.generated.resources.remote_val_bulb
import cameragps.sharednew.generated.resources.remote_val_drive_cont_hi
import cameragps.sharednew.generated.resources.remote_val_drive_cont_hi_plus
import cameragps.sharednew.generated.resources.remote_val_drive_cont_lo
import cameragps.sharednew.generated.resources.remote_val_drive_cont_mid
import cameragps.sharednew.generated.resources.remote_val_drive_self_10
import cameragps.sharednew.generated.resources.remote_val_drive_self_2
import cameragps.sharednew.generated.resources.remote_val_drive_self_5
import cameragps.sharednew.generated.resources.remote_val_drive_single
import cameragps.sharednew.generated.resources.remote_val_flash_auto
import cameragps.sharednew.generated.resources.remote_val_flash_fill
import cameragps.sharednew.generated.resources.remote_val_flash_off
import cameragps.sharednew.generated.resources.remote_val_flash_rear
import cameragps.sharednew.generated.resources.remote_val_flash_slow
import cameragps.sharednew.generated.resources.remote_val_flash_wireless
import cameragps.sharednew.generated.resources.remote_val_iso_ext
import cameragps.sharednew.generated.resources.remote_val_meter_avg
import cameragps.sharednew.generated.resources.remote_val_meter_center
import cameragps.sharednew.generated.resources.remote_val_meter_highlight
import cameragps.sharednew.generated.resources.remote_val_meter_multi
import cameragps.sharednew.generated.resources.remote_val_meter_spot_large
import cameragps.sharednew.generated.resources.remote_val_meter_spot_std
import cameragps.sharednew.generated.resources.remote_val_mode_auto
import cameragps.sharednew.generated.resources.remote_val_mode_iauto
import cameragps.sharednew.generated.resources.remote_val_mode_movie
import cameragps.sharednew.generated.resources.remote_val_mode_mr
import cameragps.sharednew.generated.resources.remote_val_wb_awb
import cameragps.sharednew.generated.resources.remote_val_wb_cloudy
import cameragps.sharednew.generated.resources.remote_val_wb_ctemp
import cameragps.sharednew.generated.resources.remote_val_wb_custom
import cameragps.sharednew.generated.resources.remote_val_wb_daylight
import cameragps.sharednew.generated.resources.remote_val_wb_flash
import cameragps.sharednew.generated.resources.remote_val_wb_fluor_cool
import cameragps.sharednew.generated.resources.remote_val_wb_fluor_day_white
import cameragps.sharednew.generated.resources.remote_val_wb_fluor_daylight
import cameragps.sharednew.generated.resources.remote_val_wb_fluor_warm
import cameragps.sharednew.generated.resources.remote_val_wb_incandescent
import cameragps.sharednew.generated.resources.remote_val_wb_shade
import cameragps.sharednew.generated.resources.remote_val_wb_underwater
import com.sasch.cameragps.sharednew.remote.wifi.CameraPropertiesState
import com.sasch.cameragps.sharednew.remote.wifi.CameraShootingProperty
import com.sasch.cameragps.sharednew.remote.wifi.CameraShootingPropertyKey
import com.sasch.cameragps.sharednew.remote.wifi.PropertyProbeStatus
import com.sasch.cameragps.sharednew.remote.wifi.PropertyWriteStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiCaptureStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleSize
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressCircle
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressSize
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTag
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTagStyle
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal fun cameraPropertyLabelRes(key: CameraShootingPropertyKey): StringResource = when (key) {
    CameraShootingPropertyKey.ExposureProgram -> Res.string.remote_prop_mode
    CameraShootingPropertyKey.ShutterSpeed -> Res.string.remote_prop_shutter
    CameraShootingPropertyKey.Aperture -> Res.string.remote_prop_aperture
    CameraShootingPropertyKey.Iso -> Res.string.remote_prop_iso
    CameraShootingPropertyKey.ExposureBias -> Res.string.remote_prop_ev
    CameraShootingPropertyKey.WhiteBalance -> Res.string.remote_prop_wb
    CameraShootingPropertyKey.FocusMode -> Res.string.remote_prop_focus_mode
    CameraShootingPropertyKey.MeteringMode -> Res.string.remote_prop_metering
    CameraShootingPropertyKey.FlashMode -> Res.string.remote_prop_flash
    CameraShootingPropertyKey.DriveMode -> Res.string.remote_prop_drive
}

/** Localized display label for a camera property value (Chinese / English via composeResources). */
@Composable
internal fun localizedPropertyValue(
    key: CameraShootingPropertyKey,
    raw: Long?,
    fallback: String,
    compact: Boolean = false,
): String {
    if (raw == null) return fallback
    return when (key) {
        CameraShootingPropertyKey.ShutterSpeed -> when (raw) {
            0L -> stringResource(Res.string.remote_val_bulb)
            else -> fallback
        }
        CameraShootingPropertyKey.Iso -> when {
            raw == 0x00ffffffL -> stringResource(Res.string.remote_val_auto)
            ((raw ushr 24) and 0xffL) == 0x10L ->
                stringResource(Res.string.remote_val_iso_ext, (raw and 0x00ffffffL).toInt())
            ((raw ushr 24) and 0xffL) == 0x20L -> fallback
            compact -> (raw and 0x00ffffffL).toString()
            else -> fallback
        }
        CameraShootingPropertyKey.ExposureBias -> if (compact) fallback.removeSuffix(" EV") else fallback
        CameraShootingPropertyKey.ExposureProgram -> when (raw.toInt()) {
            0x48000, 0x08000 -> stringResource(Res.string.remote_val_mode_auto)
            0x48001, 0x08001 -> stringResource(Res.string.remote_val_mode_iauto)
            0x78050 -> stringResource(Res.string.remote_val_mode_mr, 1)
            0x78051 -> stringResource(Res.string.remote_val_mode_mr, 2)
            0x78052 -> stringResource(Res.string.remote_val_mode_mr, 3)
            0x78053 -> stringResource(Res.string.remote_val_mode_mr, 4)
            0x80050, 0x98059 -> stringResource(Res.string.remote_val_mode_movie, "P")
            0x80051, 0x9805a -> stringResource(Res.string.remote_val_mode_movie, "A")
            0x80052, 0x9805b -> stringResource(Res.string.remote_val_mode_movie, "S")
            0x80053, 0x9805c -> stringResource(Res.string.remote_val_mode_movie, "M")
            else -> fallback
        }
        CameraShootingPropertyKey.WhiteBalance -> when (raw.toInt()) {
            0x0002 -> stringResource(Res.string.remote_val_wb_awb)
            0x0004 -> stringResource(Res.string.remote_val_wb_daylight)
            0x8011 -> stringResource(Res.string.remote_val_wb_shade)
            0x8010 -> stringResource(Res.string.remote_val_wb_cloudy)
            0x0006 -> stringResource(Res.string.remote_val_wb_incandescent)
            0x8001 -> stringResource(Res.string.remote_val_wb_fluor_warm)
            0x8002 -> stringResource(Res.string.remote_val_wb_fluor_cool)
            0x8003 -> stringResource(Res.string.remote_val_wb_fluor_day_white)
            0x8004 -> stringResource(Res.string.remote_val_wb_fluor_daylight)
            0x0007 -> stringResource(Res.string.remote_val_wb_flash)
            0x8030 -> stringResource(Res.string.remote_val_wb_underwater)
            0x8012 -> stringResource(Res.string.remote_val_wb_ctemp)
            0x8020 -> stringResource(Res.string.remote_val_wb_custom, 1)
            0x8021 -> stringResource(Res.string.remote_val_wb_custom, 2)
            0x8022 -> stringResource(Res.string.remote_val_wb_custom, 3)
            else -> fallback
        }
        CameraShootingPropertyKey.MeteringMode -> when (raw.toInt()) {
            0x8001 -> stringResource(Res.string.remote_val_meter_multi)
            0x8002 -> stringResource(Res.string.remote_val_meter_center)
            0x8003 -> stringResource(Res.string.remote_val_meter_avg)
            0x8004 -> stringResource(Res.string.remote_val_meter_spot_std)
            0x8005 -> stringResource(Res.string.remote_val_meter_spot_large)
            0x8006 -> stringResource(Res.string.remote_val_meter_highlight)
            else -> fallback
        }
        CameraShootingPropertyKey.FlashMode -> when (raw.toInt()) {
            0x0000, 0x0002 -> stringResource(Res.string.remote_val_flash_off)
            0x0001 -> stringResource(Res.string.remote_val_flash_auto)
            0x0003 -> stringResource(Res.string.remote_val_flash_fill)
            0x8001 -> stringResource(Res.string.remote_val_flash_slow)
            0x8003 -> stringResource(Res.string.remote_val_flash_rear)
            0x8004 -> stringResource(Res.string.remote_val_flash_wireless)
            else -> fallback
        }
        CameraShootingPropertyKey.DriveMode -> when (raw.toInt()) {
            0x00001 -> stringResource(Res.string.remote_val_drive_single)
            0x00002, 0x10002 -> stringResource(Res.string.remote_val_drive_cont_lo)
            0x18015 -> stringResource(Res.string.remote_val_drive_cont_mid)
            0x18010 -> stringResource(Res.string.remote_val_drive_cont_hi)
            0x18012 -> stringResource(Res.string.remote_val_drive_cont_hi_plus)
            0x38004 -> stringResource(Res.string.remote_val_drive_self_10)
            0x38003 -> stringResource(Res.string.remote_val_drive_self_5)
            0x38005 -> stringResource(Res.string.remote_val_drive_self_2)
            else -> fallback
        }
        CameraShootingPropertyKey.Aperture, CameraShootingPropertyKey.FocusMode -> fallback
    }
}

/**
 * Compact top status strip for read-only & low-frequency telemetry:
 * shooting mode, focus mode, metering, drive, battery %, and remaining shots.
 */
@Composable
internal fun RemoteTopReadOnlyStrip(
    propertiesState: CameraPropertiesState,
    modifier: Modifier = Modifier,
) {
    val parts = buildList {
        propertiesState.byKey(CameraShootingPropertyKey.ExposureProgram)?.let {
            add(localizedPropertyValue(it.key, it.rawValue, it.displayValue, compact = true))
        }
        propertiesState.byKey(CameraShootingPropertyKey.FocusMode)?.let {
            add(localizedPropertyValue(it.key, it.rawValue, it.displayValue, compact = true))
        }
        propertiesState.byKey(CameraShootingPropertyKey.MeteringMode)?.let {
            add(localizedPropertyValue(it.key, it.rawValue, it.displayValue, compact = true))
        }
        propertiesState.byKey(CameraShootingPropertyKey.DriveMode)?.let {
            add(localizedPropertyValue(it.key, it.rawValue, it.displayValue, compact = true))
        }
        propertiesState.batteryPercent?.let { add("$it%") }
        propertiesState.remainingShots?.let {
            add(stringResource(Res.string.remote_status_remaining_shots, it))
        }
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        style = AlphaType.Caption,
        color = AlphaTheme.colors.onPhotoSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .viewerChromeScrim()
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * High-frequency 5-cell exposure bar pinned to the top of the bottom control dock:
 * Shutter · Aperture · ISO · EV · WB. Writable cells open the in-place horizontal dial below.
 */
@Composable
internal fun RemoteExposureBar(
    propertiesState: CameraPropertiesState,
    activeDialKey: CameraShootingPropertyKey?,
    onSelectKey: (CameraShootingPropertyKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = propertiesState.properties.filter { it.key.primaryBar }
    if (primary.isEmpty()) return
    val colors = AlphaTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (prop in primary) {
            val active = activeDialKey == prop.key
            val writing = propertiesState.writingKey == prop.key
            val label = stringResource(cameraPropertyLabelRes(prop.key))
            val value = localizedPropertyValue(prop.key, prop.rawValue, prop.displayValue, compact = true)
            val cellShape = RoundedCornerShape(10.dp)
            val cellBg = when {
                active -> colors.accent.copy(alpha = 0.28f)
                prop.writable -> colors.onPhoto.copy(alpha = 0.08f)
                else -> colors.viewerSheet
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = AlphaDimens.minTouchTarget)
                    .clip(cellShape)
                    .background(cellBg)
                    .then(
                        if (prop.writable) {
                            Modifier.clickable(role = Role.Button) { onSelectKey(prop.key) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = AlphaType.Tag,
                    color = if (active) colors.onPhoto else colors.onPhotoMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (writing) {
                    GlassProgressCircle(onPhoto = true, size = GlassProgressSize.Small)
                } else {
                    Text(
                        text = value,
                        style = AlphaType.Label,
                        color = when {
                            active -> colors.onPhoto
                            prop.writable -> colors.onPhoto
                            else -> colors.onPhotoMuted
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * In-place horizontal dial replacing the shutter row when adjusting Shutter / Aperture / ISO / EV / WB.
 * Keeps the upper live view completely unobstructed.
 */
@Composable
internal fun RemoteHorizontalDialDeck(
    property: CameraShootingProperty,
    propertiesState: CameraPropertiesState,
    onSetProperty: (CameraShootingPropertyKey, Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AlphaTheme.colors
    val listState = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val currentIndex = property.options.indexOfFirst { it.rawValue == property.rawValue }.coerceAtLeast(0)
    LaunchedEffect(property.key, currentIndex) {
        if (property.options.isNotEmpty()) {
            listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
        }
    }
    val busy = propertiesState.writingKey != null || propertiesState.loading
    val quickResetRaw = when (property.key) {
        CameraShootingPropertyKey.Iso -> 0x00ffffffL.takeIf { raw -> property.options.any { it.rawValue == raw } }
        CameraShootingPropertyKey.ExposureBias -> 0L.takeIf { raw -> property.options.any { it.rawValue == raw } }
        CameraShootingPropertyKey.WhiteBalance -> 0x0002L.takeIf { raw -> property.options.any { it.rawValue == raw } }
        else -> null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(cameraPropertyLabelRes(property.key)),
                style = AlphaType.Label,
                color = colors.onPhoto,
            )
            val statusText = when (propertiesState.writeStatus) {
                PropertyWriteStatus.Applied -> stringResource(Res.string.remote_params_applied)
                PropertyWriteStatus.Rejected, PropertyWriteStatus.Unsupported ->
                    stringResource(Res.string.remote_params_rejected)
                PropertyWriteStatus.Failed -> stringResource(Res.string.remote_params_write_failed)
                else -> null
            }
            if (statusText != null) {
                val statusColor = when (propertiesState.writeStatus) {
                    PropertyWriteStatus.Applied -> colors.onPhotoSecondary
                    PropertyWriteStatus.Rejected, PropertyWriteStatus.Unsupported -> colors.onPhotoWarning
                    else -> colors.onPhotoDanger
                }
                Text(
                    text = statusText,
                    style = AlphaType.Caption,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (quickResetRaw != null && property.rawValue != quickResetRaw) {
                val quickLabel = localizedPropertyValue(
                    property.key,
                    quickResetRaw,
                    property.options.first { it.rawValue == quickResetRaw }.label,
                    compact = true,
                )
                CapsuleButton(
                    text = quickLabel,
                    onClick = { onSetProperty(property.key, quickResetRaw) },
                    role = CapsuleRole.Tonal,
                    size = CapsuleSize.Small,
                    enabled = !busy,
                )
            }
            CapsuleButton(
                text = stringResource(Res.string.remote_params_done),
                onClick = onClose,
                role = CapsuleRole.GlassPrimary,
                size = CapsuleSize.Small,
                glassLevel = GlassLevel.Viewer,
            )
        }
        LazyRow(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            itemsIndexed(property.options, key = { _, item -> item.rawValue }) { _, option ->
                val selected = option.rawValue == property.rawValue
                val text = localizedPropertyValue(property.key, option.rawValue, option.label, compact = true)
                Column(
                    modifier = Modifier
                        .widthIn(min = 56.dp)
                        .heightIn(min = AlphaDimens.minTouchTarget)
                        .clip(AlphaShapes.capsule)
                        .background(
                            if (selected) colors.accent else colors.onPhoto.copy(alpha = 0.08f),
                        )
                        .clickable(enabled = !busy && !selected, role = Role.Button) {
                            onSetProperty(property.key, option.rawValue)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(if (selected) 8.dp else 5.dp)
                            .background(if (selected) colors.onPhoto else colors.onPhotoMuted),
                    )
                    Text(
                        text = text,
                        style = AlphaType.Label,
                        color = if (selected) colors.onPhoto else colors.onPhotoSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Opaque dark bottom drawer (`viewerSheet` #1B1F24, matching WifiPhotoViewer details drawer)
 * for low-frequency & read-only settings (Mode, Focus Mode, Metering, Flash, Drive) plus Refresh & Diagnostic Probe.
 */
@Composable
internal fun RemoteMoreSettingsDrawer(
    visible: Boolean,
    state: WifiRemoteState,
    onSetProperty: (CameraShootingPropertyKey, Long) -> Unit,
    onRefresh: () -> Unit,
    onProbe: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = onDismiss,
    )
    val colors = AlphaTheme.colors
    val secondary = state.cameraProperties.properties.filter { !it.key.primaryBar }
    val busy = state.cameraProperties.writingKey != null || state.cameraProperties.loading

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.viewerSheet.copy(alpha = 0.65f))
                .clickable(onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(colors.viewerSheet)
                .navigationBarsPadding()
                .padding(horizontal = AlphaDimens.screenMargin, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.remote_params_title),
                    style = AlphaType.Heading,
                    color = colors.onPhoto,
                    modifier = Modifier.weight(1f),
                )
                CapsuleButton(
                    text = stringResource(Res.string.remote_params_refresh),
                    onClick = onRefresh,
                    role = CapsuleRole.GlassPrimary,
                    size = CapsuleSize.Small,
                    glassLevel = GlassLevel.Viewer,
                    icon = painterResource(Res.drawable.refresh_24px),
                    enabled = !busy,
                    loading = state.cameraProperties.loading,
                )
                GlassIconButton(
                    icon = painterResource(Res.drawable.close_24px),
                    contentDescription = stringResource(Res.string.m0_dialog_close),
                    onClick = onDismiss,
                    container = GlassLevel.Viewer,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (secondary.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.remote_params_empty),
                        style = AlphaType.Body,
                        color = colors.onPhotoSecondary,
                    )
                } else {
                    for (prop in secondary) {
                        LowFreqPropertyBlock(
                            property = prop,
                            busy = busy,
                            writingThis = state.cameraProperties.writingKey == prop.key,
                            onPickOption = { raw -> onSetProperty(prop.key, raw) },
                        )
                    }
                }

                if (onProbe != null) {
                    val probeText = when (state.propertyProbe) {
                        PropertyProbeStatus.Idle -> stringResource(Res.string.remote_probe)
                        PropertyProbeStatus.Reading -> stringResource(Res.string.remote_probe_reading)
                        PropertyProbeStatus.Recorded -> stringResource(Res.string.remote_probe_recorded, state.probeAttempt)
                        PropertyProbeStatus.Unsupported -> stringResource(Res.string.remote_probe_unsupported)
                        PropertyProbeStatus.Failed -> stringResource(Res.string.remote_probe_failed)
                    }
                    CapsuleButton(
                        text = probeText,
                        onClick = onProbe,
                        role = CapsuleRole.GlassPrimary,
                        size = CapsuleSize.Small,
                        glassLevel = GlassLevel.Viewer,
                        icon = painterResource(Res.drawable.baseline_view_list_24),
                        enabled = state.isControlReady && !state.photoBrowser.open &&
                            state.capture != WifiCaptureStatus.Shooting &&
                            state.propertyProbe != PropertyProbeStatus.Reading,
                        loading = state.propertyProbe == PropertyProbeStatus.Reading,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun LowFreqPropertyBlock(
    property: CameraShootingProperty,
    busy: Boolean,
    writingThis: Boolean,
    onPickOption: (Long) -> Unit,
) {
    val colors = AlphaTheme.colors
    val currentText = localizedPropertyValue(property.key, property.rawValue, property.displayValue)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.onPhoto.copy(alpha = 0.06f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(cameraPropertyLabelRes(property.key)),
                style = AlphaType.Body,
                color = colors.onPhoto,
                modifier = Modifier.weight(1f),
            )
            if (writingThis) {
                GlassProgressCircle(onPhoto = true, size = GlassProgressSize.Small)
            }
            if (!property.writable) {
                InfoTag(
                    text = stringResource(Res.string.remote_params_readonly),
                    style = InfoTagStyle.OnPhoto,
                    contentColor = colors.onPhotoMuted,
                )
            }
            Text(
                text = currentText,
                style = AlphaType.Label,
                color = if (property.writable) colors.onPhoto else colors.onPhotoSecondary,
            )
        }
        if (property.writable && property.options.isNotEmpty()) {
            // Flat 3-column grid so every option is readable at a glance without horizontal clipping.
            val rows = property.options.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (option in row) {
                            val selected = option.rawValue == property.rawValue
                            val optionText = localizedPropertyValue(property.key, option.rawValue, option.label)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                                    .clip(AlphaShapes.capsule)
                                    .background(
                                        if (selected) colors.accent else colors.onPhoto.copy(alpha = 0.08f),
                                    )
                                    .clickable(enabled = !busy && !selected, role = Role.Button) {
                                        onPickOption(option.rawValue)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = optionText,
                                    style = AlphaType.Caption,
                                    color = if (selected) colors.onPhoto else colors.onPhotoSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
