package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.refresh_24px
import cameragps.sharednew.generated.resources.remote_params_all
import cameragps.sharednew.generated.resources.remote_params_applied
import cameragps.sharednew.generated.resources.remote_params_editable
import cameragps.sharednew.generated.resources.remote_params_empty
import cameragps.sharednew.generated.resources.remote_params_readonly
import cameragps.sharednew.generated.resources.remote_params_refresh
import cameragps.sharednew.generated.resources.remote_params_rejected
import cameragps.sharednew.generated.resources.remote_params_title
import cameragps.sharednew.generated.resources.remote_params_write_failed
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
import com.sasch.cameragps.sharednew.remote.wifi.CameraPropertiesState
import com.sasch.cameragps.sharednew.remote.wifi.CameraShootingProperty
import com.sasch.cameragps.sharednew.remote.wifi.CameraShootingPropertyKey
import com.sasch.cameragps.sharednew.remote.wifi.PropertyWriteStatus
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleSize
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBottomSheet
import com.sasch.cameragps.sharednew.ui.components.glass.GlassInsetBlock
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressCircle
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressSize
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSurface
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTag
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

/**
 * Compact horizontal shooting-parameter bar shown over the live view.
 * Tapping any chip opens the in-page parameter sheet focused on that property.
 */
@Composable
internal fun RemoteParameterStrip(
    propertiesState: CameraPropertiesState,
    onOpenProperty: (CameraShootingPropertyKey?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = propertiesState.properties.filter { it.key.primaryBar }
    if (primary.isEmpty() && !propertiesState.loading) return
    val colors = AlphaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (primary.isEmpty() && propertiesState.loading) {
            GlassSurface(
                level = GlassLevel.Viewer,
                shape = AlphaShapes.capsule,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                GlassProgressCircle(onPhoto = true, size = GlassProgressSize.Small)
            }
        } else {
            for (prop in primary) {
                val label = stringResource(cameraPropertyLabelRes(prop.key))
                val writing = propertiesState.writingKey == prop.key
                GlassSurface(
                    level = GlassLevel.Viewer,
                    shape = AlphaShapes.capsule,
                    onClick = { onOpenProperty(prop.key) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = AlphaType.Tag,
                            color = colors.onPhotoMuted,
                            maxLines = 1,
                        )
                        if (writing) {
                            GlassProgressCircle(onPhoto = true, size = GlassProgressSize.Small)
                        } else {
                            Text(
                                text = prop.displayValue,
                                style = AlphaType.Label,
                                color = if (prop.writable) colors.onPhoto else colors.onPhotoSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            GlassSurface(
                level = GlassLevel.Viewer,
                shape = AlphaShapes.capsule,
                onClick = { onOpenProperty(null) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = stringResource(Res.string.remote_params_all),
                    style = AlphaType.Label,
                    color = colors.onPhoto,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * In-page bottom sheet listing all detected shooting parameters (read-only & writable)
 * with live option pickers for every writable property.
 */
@Composable
internal fun RemoteParametersSheet(
    visible: Boolean,
    propertiesState: CameraPropertiesState,
    selectedKey: CameraShootingPropertyKey?,
    onSelectKey: (CameraShootingPropertyKey?) -> Unit,
    onSetProperty: (CameraShootingPropertyKey, Long) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassBottomSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.remote_params_title),
    ) {
        val colors = AlphaTheme.colors
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.remote_params_title),
                    style = AlphaType.Heading,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                CapsuleButton(
                    text = stringResource(Res.string.remote_params_refresh),
                    onClick = onRefresh,
                    role = CapsuleRole.Secondary,
                    size = CapsuleSize.Small,
                    icon = painterResource(Res.drawable.refresh_24px),
                    enabled = !propertiesState.loading && propertiesState.writingKey == null,
                    loading = propertiesState.loading,
                )
            }
            val statusText = when (propertiesState.writeStatus) {
                PropertyWriteStatus.Applied -> stringResource(Res.string.remote_params_applied)
                PropertyWriteStatus.Rejected, PropertyWriteStatus.Unsupported -> stringResource(Res.string.remote_params_rejected)
                PropertyWriteStatus.Failed -> stringResource(Res.string.remote_params_write_failed)
                PropertyWriteStatus.Idle, PropertyWriteStatus.Writing -> null
            }
            if (statusText != null) {
                val statusColor = when (propertiesState.writeStatus) {
                    PropertyWriteStatus.Applied -> colors.success
                    PropertyWriteStatus.Rejected, PropertyWriteStatus.Unsupported -> colors.warningText
                    else -> colors.danger
                }
                Text(
                    text = statusText,
                    style = AlphaType.Caption,
                    color = statusColor,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (propertiesState.properties.isEmpty()) {
                Text(
                    text = stringResource(Res.string.remote_params_empty),
                    style = AlphaType.Body,
                    color = colors.textSecondary,
                )
            } else {
                for (prop in propertiesState.properties) {
                    ParameterSheetRow(
                        property = prop,
                        expanded = selectedKey == prop.key && prop.writable,
                        busy = propertiesState.writingKey != null || propertiesState.loading,
                        writingThis = propertiesState.writingKey == prop.key,
                        onToggleExpand = {
                            if (prop.writable) {
                                onSelectKey(if (selectedKey == prop.key) null else prop.key)
                            }
                        },
                        onPickOption = { raw -> onSetProperty(prop.key, raw) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ParameterSheetRow(
    property: CameraShootingProperty,
    expanded: Boolean,
    busy: Boolean,
    writingThis: Boolean,
    onToggleExpand: () -> Unit,
    onPickOption: (Long) -> Unit,
) {
    val colors = AlphaTheme.colors
    GlassInsetBlock(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(cameraPropertyLabelRes(property.key)),
                        style = AlphaType.Body,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    InfoTag(
                        text = stringResource(
                            if (property.writable) Res.string.remote_params_editable else Res.string.remote_params_readonly,
                        ),
                    )
                }
                if (writingThis) {
                    GlassProgressCircle(size = GlassProgressSize.Small)
                }
                if (property.writable) {
                    CapsuleButton(
                        text = property.displayValue,
                        onClick = onToggleExpand,
                        role = if (expanded) CapsuleRole.Primary else CapsuleRole.Tonal,
                        size = CapsuleSize.Small,
                        enabled = !busy,
                    )
                } else {
                    Text(
                        text = property.displayValue,
                        style = AlphaType.Label,
                        color = colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
            if (expanded && property.options.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (option in property.options) {
                        val selected = option.rawValue == property.rawValue
                        CapsuleButton(
                            text = option.label,
                            onClick = { onPickOption(option.rawValue) },
                            role = if (selected) CapsuleRole.Primary else CapsuleRole.Secondary,
                            size = CapsuleSize.Small,
                            enabled = !busy && !selected,
                        )
                    }
                }
            }
        }
    }
}
