package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
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
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSurface
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTag
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTagStyle
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import com.sasch.cameragps.sharednew.ui.theme.GlassTintRole
import com.sasch.cameragps.sharednew.ui.theme.GlassTints
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
 * Full-width evenly distributed HUD glass bar for read-only & low-frequency status values:
 * shooting mode, focus mode, metering, and drive mode (values only, no labels).
 */
@Composable
internal fun RemoteTopReadOnlyStrip(
    propertiesState: CameraPropertiesState,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = buildList {
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
    }
    if (values.isEmpty()) return
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        level = GlassLevel.Viewer,
        shape = AlphaShapes.capsule,
        onClick = onOpenMore,
        onClickLabel = stringResource(Res.string.remote_params_title),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (value in values) {
                Text(
                    text = value,
                    style = AlphaType.Caption,
                    color = AlphaTheme.colors.onPhoto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * High-frequency 5-cell exposure bar pinned to the top of the bottom liquid-glass dock:
 * Shutter · Aperture · ISO · EV · WB. Writable cells open the in-place horizontal ruler dial below.
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
    val accentTint = GlassTints.of(GlassTintRole.Accent, colors).tint
    val neutralTint = GlassTints.of(GlassTintRole.Neutral, colors).tint
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (prop in primary) {
            val active = activeDialKey == prop.key
            val writing = propertiesState.writingKey == prop.key
            val label = stringResource(cameraPropertyLabelRes(prop.key))
            val value = localizedPropertyValue(prop.key, prop.rawValue, prop.displayValue, compact = true)
            if (prop.writable) {
                GlassSurface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = AlphaDimens.minTouchTarget),
                    level = GlassLevel.Viewer,
                    shape = AlphaShapes.inset,
                    tint = if (active) accentTint else neutralTint,
                    onClick = { onSelectKey(prop.key) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ExposureCellContent(
                        label = label,
                        value = value,
                        active = active,
                        writable = true,
                        writing = writing,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = AlphaDimens.minTouchTarget)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ExposureCellContent(
                        label = label,
                        value = value,
                        active = false,
                        writable = false,
                        writing = writing,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExposureCellContent(
    label: String,
    value: String,
    active: Boolean,
    writable: Boolean,
    writing: Boolean,
) {
    val colors = AlphaTheme.colors
    Column(
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
                color = if (active || writable) colors.onPhoto else colors.onPhotoMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * In-place horizontal physical-style ruler dial ("fixed center needle, moving scale") replacing the
 * shutter row when adjusting Aperture / ISO / EV / WB.
 * - Fixed center pointer stays anchored at the horizontal center.
 * - Half-viewport start/end padding ensures every stop (including first and last) centers under the needle.
 * - Dragging scrubs with haptic detents and commits on settle; tapping left/right stops nudges smoothly.
 */
@Composable
internal fun RemoteHorizontalDialDeck(
    property: CameraShootingProperty,
    propertiesState: CameraPropertiesState,
    hapticsEnabled: Boolean,
    onSetProperty: (CameraShootingPropertyKey, Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.runtime.key(property.key) {
        val colors = AlphaTheme.colors
        val haptics = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val currentIndex = property.options.indexOfFirst { it.rawValue == property.rawValue }.coerceAtLeast(0)
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
        val fling = rememberSnapFlingBehavior(lazyListState = listState)
        var userDragged by remember { mutableStateOf(false) }
        var lastHapticIndex by remember { mutableIntStateOf(currentIndex) }
        val busy = propertiesState.writingKey != null || propertiesState.loading

        val centeredIndex by remember(property.options.size, currentIndex) {
            derivedStateOf {
                val info = listState.layoutInfo
                if (info.visibleItemsInfo.isEmpty()) {
                    currentIndex
                } else {
                    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
                    info.visibleItemsInfo.minByOrNull { item ->
                        abs((item.offset + item.size / 2) - viewportCenter)
                    }?.index ?: currentIndex
                }
            }
        }

        LaunchedEffect(centeredIndex) {
            if (listState.isScrollInProgress && centeredIndex != lastHapticIndex) {
                lastHapticIndex = centeredIndex
                if (hapticsEnabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            } else {
                lastHapticIndex = centeredIndex
            }
        }

        LaunchedEffect(listState.isScrollInProgress, centeredIndex) {
            if (listState.isScrollInProgress) {
                userDragged = true
                return@LaunchedEffect
            }
            if (userDragged && !busy) {
                val target = property.options.getOrNull(centeredIndex)
                if (target != null && target.rawValue != property.rawValue) {
                    delay(120)
                    userDragged = false
                    onSetProperty(property.key, target.rawValue)
                } else {
                    userDragged = false
                }
            }
        }

        LaunchedEffect(currentIndex) {
            if (!listState.isScrollInProgress && !userDragged && property.options.isNotEmpty()) {
                listState.animateScrollToItem(currentIndex)
            }
        }

        val quickResetRaw = when (property.key) {
            CameraShootingPropertyKey.Iso -> 0x00ffffffL.takeIf { raw -> property.options.any { it.rawValue == raw } }
            CameraShootingPropertyKey.ExposureBias -> 0L.takeIf { raw -> property.options.any { it.rawValue == raw } }
            CameraShootingPropertyKey.WhiteBalance -> 0x0002L.takeIf { raw -> property.options.any { it.rawValue == raw } }
            else -> null
        }
        val centeredOption = property.options.getOrNull(centeredIndex)
        val liveReadout = centeredOption?.let {
            localizedPropertyValue(property.key, it.rawValue, it.label, compact = true)
        } ?: localizedPropertyValue(property.key, property.rawValue, property.displayValue, compact = true)

        val itemWidth = when (property.key) {
            CameraShootingPropertyKey.WhiteBalance -> 84.dp
            else -> 64.dp
        }

        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(cameraPropertyLabelRes(property.key)),
                            style = AlphaType.Caption,
                            color = colors.onPhotoSecondary,
                            maxLines = 1,
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
                                style = AlphaType.Tag,
                                color = statusColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
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
                            onClick = {
                                userDragged = false
                                onSetProperty(property.key, quickResetRaw)
                            },
                            role = CapsuleRole.Tonal,
                            size = CapsuleSize.Small,
                            glass = true,
                            glassLevel = GlassLevel.Viewer,
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
                Text(
                    text = liveReadout,
                    style = AlphaType.Heading,
                    color = colors.onPhoto,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // Recessed glass ruler trough with fixed center indicator needle and moving scale.
            GlassSurface(
                modifier = Modifier.fillMaxWidth().height(68.dp),
                level = GlassLevel.Viewer,
                shape = AlphaShapes.inset,
                tint = GlassTints.of(GlassTintRole.Neutral, colors).tint,
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val sidePadding = ((maxWidth - itemWidth) / 2).coerceAtLeast(0.dp)
                    LazyRow(
                        state = listState,
                        flingBehavior = fling,
                        contentPadding = PaddingValues(horizontal = sidePadding),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(property.options, key = { _, item -> item.rawValue }) { index, option ->
                            val isCentered = index == centeredIndex
                            val distance = abs(index - centeredIndex)
                            val itemAlpha = when (distance) {
                                0 -> 1f
                                1 -> 0.82f
                                2 -> 0.52f
                                else -> 0.28f
                            }
                            val text = localizedPropertyValue(property.key, option.rawValue, option.label, compact = true)
                            Column(
                                modifier = Modifier
                                    .width(itemWidth)
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = itemAlpha }
                                    .clickable(enabled = !busy, role = Role.Button) {
                                        userDragged = false
                                        scope.launch { listState.animateScrollToItem(index) }
                                        if (option.rawValue != property.rawValue) {
                                            if (hapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            onSetProperty(property.key, option.rawValue)
                                        }
                                    }
                                    .padding(top = 12.dp, bottom = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                // Engraved tick marks: minor - major - minor.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Box(
                                        Modifier
                                            .width(1.dp)
                                            .height(7.dp)
                                            .background(colors.onPhotoMuted.copy(alpha = 0.45f)),
                                    )
                                    Box(
                                        Modifier
                                            .width(if (isCentered) 2.dp else 1.5.dp)
                                            .height(if (isCentered) 16.dp else 12.dp)
                                            .background(if (isCentered) colors.onPhoto else colors.onPhotoSecondary),
                                    )
                                    Box(
                                        Modifier
                                            .width(1.dp)
                                            .height(7.dp)
                                            .background(colors.onPhotoMuted.copy(alpha = 0.45f)),
                                    )
                                }
                                Text(
                                    text = text,
                                    style = if (isCentered) AlphaType.Label else AlphaType.Caption,
                                    color = if (isCentered) colors.onPhoto else colors.onPhotoSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    // Fixed center pointer (stationary needle anchored at dead center).
                    val pointerColor = colors.accent
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Canvas(Modifier.width(10.dp).height(6.dp)) {
                            val triangle = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width / 2f, size.height)
                                close()
                            }
                            drawPath(triangle, color = pointerColor)
                        }
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(22.dp)
                                .background(pointerColor),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Liquid-glass modal bottom drawer for low-frequency & read-only settings (Mode, Focus Mode,
 * Metering, Flash, Drive) plus Refresh & Diagnostic Probe. Every block and option uses glass capsules.
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
                .background(colors.viewerBackground.copy(alpha = 0.56f))
                .clickable(onClick = onDismiss),
        )
        GlassSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            level = GlassLevel.Modal,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            contentPadding = PaddingValues(horizontal = AlphaDimens.screenMargin, vertical = 14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
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
                        role = CapsuleRole.Tonal,
                        size = CapsuleSize.Small,
                        glass = true,
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
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                            role = CapsuleRole.Tonal,
                            size = CapsuleSize.Regular,
                            glass = true,
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
}

@Composable
private fun LowFreqPropertyBlock(
    property: CameraShootingProperty,
    busy: Boolean,
    writingThis: Boolean,
    onPickOption: (Long) -> Unit,
) {
    val colors = AlphaTheme.colors
    val accentTint = GlassTints.of(GlassTintRole.Accent, colors).tint
    val neutralTint = GlassTints.of(GlassTintRole.Neutral, colors).tint
    val currentText = localizedPropertyValue(property.key, property.rawValue, property.displayValue)
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        level = GlassLevel.Card,
        shape = AlphaShapes.card,
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                val rows = property.options.chunked(3)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (option in row) {
                                val selected = option.rawValue == property.rawValue
                                val optionText = localizedPropertyValue(property.key, option.rawValue, option.label)
                                GlassSurface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 40.dp),
                                    level = GlassLevel.Viewer,
                                    shape = AlphaShapes.capsule,
                                    tint = if (selected) accentTint else neutralTint,
                                    enabled = !busy && !selected,
                                    onClick = { onPickOption(option.rawValue) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = optionText,
                                        style = if (selected) AlphaType.Label else AlphaType.Caption,
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
}
