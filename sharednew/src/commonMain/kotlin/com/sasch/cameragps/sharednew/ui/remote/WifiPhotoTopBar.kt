package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.arrow_back_24px
import cameragps.sharednew.generated.resources.back
import cameragps.sharednew.generated.resources.close_24px
import cameragps.sharednew.generated.resources.mw_grid_large
import cameragps.sharednew.generated.resources.mw_grid_medium
import cameragps.sharednew.generated.resources.mw_grid_small
import cameragps.sharednew.generated.resources.mw_grid_view_24px
import cameragps.sharednew.generated.resources.mw_tag_format
import cameragps.sharednew.generated.resources.mw_tag_size
import cameragps.sharednew.generated.resources.mw_view_options
import cameragps.sharednew.generated.resources.refresh_24px
import cameragps.sharednew.generated.resources.wifi_remote_browser_refresh
import cameragps.sharednew.generated.resources.wifi_remote_browser_title
import cameragps.sharednew.generated.resources.wifi_remote_dates_count
import cameragps.sharednew.generated.resources.wifi_remote_selection_clear
import cameragps.sharednew.generated.resources.wifi_remote_selection_count
import cameragps.sharednew.generated.resources.wifi_remote_selection_count_other_dates
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.dateKey
import com.sasch.cameragps.sharednew.remote.wifi.groupCameraPhotos
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.glass
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Top bar of the photo browser (design v3):
 *
 * - day list: back (ends the Wi-Fi session), "Camera photos", refresh (spinner while reading);
 * - day grid: back to the day list, the day and its count, view options (grid size and tags);
 * - selecting: cancel selection (✕) and "N selected". Downloading is the floating button.
 *
 * There is no separate disconnect action: leaving the day list is the end of the session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WifiPhotoBrowserTopBar(
    state: WifiRemoteState,
    viewModel: WifiRemoteViewModel,
    gridOptions: WifiGridOptions,
    onGridOptionsChange: (WifiGridOptions) -> Unit,
    onBack: () -> Unit,
) {
    val browser = state.photoBrowser
    val selectedIds = viewModel.selectedPhotoIds
    val selecting = selectedIds.isNotEmpty()
    val dateKey = browser.dateKey
    // Selections survive switching days; say so when the count includes captures not on this day.
    val includesOtherDates = browser.grouped && dateKey != null &&
            viewModel.selectedPhotos.values.any { it.dateKey != dateKey }
    val gridShown = !browser.grouped || dateKey != null
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        title = {
            Column {
                Text(
                    when {
                        selecting && includesOtherDates -> stringResource(
                            Res.string.wifi_remote_selection_count_other_dates, selectedIds.size
                        )
                        selecting -> stringResource(Res.string.wifi_remote_selection_count, selectedIds.size)
                        dateKey != null -> dateKeyLabel(dateKey)
                        else -> stringResource(Res.string.wifi_remote_browser_title)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!selecting && dateKey != null && !browser.loading) Text(
                    stringResource(
                        Res.string.wifi_remote_dates_count,
                        remember(browser.photos) { groupCameraPhotos(browser.photos).size }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = AlphaTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        navigationIcon = {
            if (selecting) GlassIconButton(
                painterResource(Res.drawable.close_24px),
                contentDescription = stringResource(Res.string.wifi_remote_selection_clear),
                onClick = viewModel::clearPhotoSelection,
            ) else GlassIconButton(
                painterResource(Res.drawable.arrow_back_24px),
                contentDescription = stringResource(Res.string.back),
                onClick = onBack,
            )
        },
        actions = {
            when {
                selecting -> Unit
                gridShown -> {
                    var open by rememberSaveable { mutableStateOf(false) }
                    Box {
                        GlassIconButton(
                            painterResource(Res.drawable.mw_grid_view_24px),
                            contentDescription = stringResource(Res.string.mw_view_options),
                            onClick = { open = true },
                        )
                        if (open) WifiGridOptionsPopover(
                            options = gridOptions,
                            onChange = onGridOptionsChange,
                            onDismiss = { open = false },
                        )
                    }
                }
                browser.loading -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                else -> GlassIconButton(
                    painterResource(Res.drawable.refresh_24px),
                    contentDescription = stringResource(Res.string.wifi_remote_browser_refresh),
                    // A refresh re-enumerates the card and starts a new catalog generation.
                    onClick = {
                        if (browser.grouped) viewModel.controller.browseDates(viewModel.identifier)
                        else viewModel.controller.browsePhotos(viewModel.identifier)
                    },
                    enabled = !state.imageTransfer.busy,
                )
            }
        },
    )
}

/**
 * View options popover under the grid icon: tile size (small / medium / large) and two independent
 * tag switches (file format, file size). Changes apply at once; it closes on an outside tap or Back.
 */
@Composable
private fun WifiGridOptionsPopover(
    options: WifiGridOptions,
    onChange: (WifiGridOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    // Anchored under the 48 dp icon it belongs to.
    val below = with(LocalDensity.current) { 48.dp.roundToPx() }
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, below),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier.width(232.dp).glass(GlassLevel.Modal, AlphaShapes.popover).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(40.dp)
                    .background(AlphaTheme.colors.onSurface.copy(alpha = 0.06f), AlphaShapes.capsule)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (size in WifiGridSize.entries) {
                    val selected = options.gridSize == size
                    Box(
                        Modifier.weight(1f).height(34.dp)
                            .background(
                                if (selected) AlphaTheme.colors.primary else Color.Transparent,
                                AlphaShapes.capsule
                            )
                            .selectable(selected, role = Role.RadioButton) { onChange(options.withSize(size)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(
                                when (size) {
                                    WifiGridSize.Small -> Res.string.mw_grid_small
                                    WifiGridSize.Medium -> Res.string.mw_grid_medium
                                    WifiGridSize.Large -> Res.string.mw_grid_large
                                }
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) AlphaTheme.colors.onPrimary else AlphaTheme.colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            OptionSwitch(stringResource(Res.string.mw_tag_format), null, options.showFormat, true) {
                onChange(options.copy(showFormat = it))
            }
            OptionSwitch(stringResource(Res.string.mw_tag_size), null, options.showSize, true) {
                onChange(options.copy(showSize = it))
            }
        }
    }
}

@Composable
private fun OptionSwitch(
    label: String, note: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label, style = MaterialTheme.typography.bodyLarge,
                color = AlphaTheme.colors.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
            )
            if (note != null) Text(
                note, style = MaterialTheme.typography.bodySmall, color = AlphaTheme.colors.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
