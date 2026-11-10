package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
import com.sasch.cameragps.sharednew.ui.components.glass.GlassAnchorState
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassPopover
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressCircle
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSegmented
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSwitch
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTopBar
import com.sasch.cameragps.sharednew.ui.components.glass.glassAnchor
import com.sasch.cameragps.sharednew.ui.components.glass.rememberGlassAnchor
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
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
    val title = when {
        selecting && includesOtherDates -> stringResource(
            Res.string.wifi_remote_selection_count_other_dates, selectedIds.size
        )
        selecting -> stringResource(Res.string.wifi_remote_selection_count, selectedIds.size)
        dateKey != null -> dateKeyLabel(dateKey)
        else -> stringResource(Res.string.wifi_remote_browser_title)
    }
    val subtitle = if (!selecting && dateKey != null && !browser.loading) stringResource(
        Res.string.wifi_remote_dates_count,
        remember(browser.photos) { groupCameraPhotos(browser.photos).size }
    ) else null
    // Transparent at rest over the page backdrop (the browser's Scaffold keeps content below it).
    GlassTopBar(
        title = title,
        subtitle = subtitle,
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
                    val anchor = rememberGlassAnchor()
                    GlassIconButton(
                        painterResource(Res.drawable.mw_grid_view_24px),
                        contentDescription = stringResource(Res.string.mw_view_options),
                        onClick = { open = true },
                        modifier = Modifier.glassAnchor(anchor),
                    )
                    WifiGridOptionsPopover(
                        expanded = open,
                        anchor = anchor,
                        options = gridOptions,
                        onChange = onGridOptionsChange,
                        onDismiss = { open = false },
                    )
                }
                browser.loading -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    GlassProgressCircle()
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
 * View options popover under the grid icon (in-page [GlassPopover], no platform window): tile size
 * as a [GlassSegmented] (small / medium / large) and two independent tag switches (file format,
 * file size). Changes apply at once; it closes on an outside tap or Back.
 */
@Composable
private fun WifiGridOptionsPopover(
    expanded: Boolean,
    anchor: GlassAnchorState,
    options: WifiGridOptions,
    onChange: (WifiGridOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassPopover(
        expanded = expanded,
        onDismissRequest = onDismiss,
        anchor = anchor,
        modifier = Modifier.width(248.dp),
        paneTitle = stringResource(Res.string.mw_view_options),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val sizes = WifiGridSize.entries
            GlassSegmented(
                options = sizes.map { size ->
                    stringResource(
                        when (size) {
                            WifiGridSize.Small -> Res.string.mw_grid_small
                            WifiGridSize.Medium -> Res.string.mw_grid_medium
                            WifiGridSize.Large -> Res.string.mw_grid_large
                        }
                    )
                },
                selectedIndex = sizes.indexOf(options.gridSize),
                onSelect = { index -> onChange(options.withSize(sizes[index])) },
            )
            OptionSwitch(stringResource(Res.string.mw_tag_format), options.showFormat) {
                onChange(options.copy(showFormat = it))
            }
            OptionSwitch(stringResource(Res.string.mw_tag_size), options.showSize) {
                onChange(options.copy(showSize = it))
            }
        }
    }
}

/** A whole-row switch (N1): the row is the toggle target, the [GlassSwitch] only draws the state. */
@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = AlphaType.Body, color = AlphaTheme.colors.textPrimary)
        GlassSwitch(checked = checked, onCheckedChange = null)
    }
}
