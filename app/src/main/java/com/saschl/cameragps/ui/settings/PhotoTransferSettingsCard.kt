package com.saschl.cameragps.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.m9_download_format_hint
import cameragps.sharednew.generated.resources.m9_download_format_jpeg_desc
import cameragps.sharednew.generated.resources.m9_photo_transfer_title
import cameragps.sharednew.generated.resources.m9_viewer_exif_params
import cameragps.sharednew.generated.resources.m9_viewer_exif_params_desc
import cameragps.sharednew.generated.resources.m9_viewer_histogram
import cameragps.sharednew.generated.resources.m9_viewer_histogram_desc
import cameragps.sharednew.generated.resources.m9_viewer_info_note
import cameragps.sharednew.generated.resources.m9_viewer_info_title
import cameragps.sharednew.generated.resources.m9_viewer_lens
import cameragps.sharednew.generated.resources.m9_viewer_lens_desc
import cameragps.sharednew.generated.resources.wifi_remote_default_download_format
import cameragps.sharednew.generated.resources.wifi_remote_default_download_format_jpeg
import cameragps.sharednew.generated.resources.wifi_remote_default_download_format_original
import cameragps.sharednew.generated.resources.wifi_remote_default_download_format_original_desc
import com.sasch.cameragps.sharednew.ui.settings.SharedRadioOption
import com.sasch.cameragps.sharednew.ui.settings.SharedToggleRow
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.saschl.cameragps.utils.PreferencesManager
import org.jetbrains.compose.resources.stringResource

/**
 * "Photo transfer" settings group: default download format (batch and single downloads) and the
 * three preview-info switches (histogram / shooting parameters / lens, all default off). The
 * preview screen consumes the viewer_* preferences.
 */
@Composable
internal fun PhotoTransferSettingsCard() {
    val context = LocalContext.current

    SettingsCard(title = stringResource(Res.string.m9_photo_transfer_title)) {
        Column {
            Text(
                text = stringResource(Res.string.wifi_remote_default_download_format),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = AlphaTheme.colors.onSurface,
            )
            Text(
                text = stringResource(Res.string.m9_download_format_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = AlphaTheme.colors.onSurfaceVariant,
            )
        }
        var jpegOnlyDefault by remember {
            mutableStateOf(PreferencesManager.isWifiDefaultDownloadFormatJpeg(context))
        }
        Column(Modifier.selectableGroup()) {
            SharedRadioOption(
                label = stringResource(Res.string.wifi_remote_default_download_format_original),
                description = stringResource(Res.string.wifi_remote_default_download_format_original_desc),
                selected = !jpegOnlyDefault,
                onClick = {
                    PreferencesManager.setWifiDefaultDownloadFormatJpeg(context, false)
                    jpegOnlyDefault = false
                },
            )
            SharedRadioOption(
                label = stringResource(Res.string.wifi_remote_default_download_format_jpeg),
                description = stringResource(Res.string.m9_download_format_jpeg_desc),
                selected = jpegOnlyDefault,
                onClick = {
                    PreferencesManager.setWifiDefaultDownloadFormatJpeg(context, true)
                    jpegOnlyDefault = true
                },
            )
        }

        Text(
            text = stringResource(Res.string.m9_viewer_info_title),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = AlphaTheme.colors.onSurface,
            modifier = Modifier
                .padding(top = 4.dp)
                .semantics { heading() },
        )
        var showHistogram by remember {
            mutableStateOf(PreferencesManager.isViewerShowHistogram(context))
        }
        SharedToggleRow(
            title = stringResource(Res.string.m9_viewer_histogram),
            description = stringResource(Res.string.m9_viewer_histogram_desc),
            checked = showHistogram,
            onCheckedChange = {
                PreferencesManager.setViewerShowHistogram(context, it)
                showHistogram = it
            },
        )
        var showExifParams by remember {
            mutableStateOf(PreferencesManager.isViewerShowExifParams(context))
        }
        SharedToggleRow(
            title = stringResource(Res.string.m9_viewer_exif_params),
            description = stringResource(Res.string.m9_viewer_exif_params_desc),
            checked = showExifParams,
            onCheckedChange = {
                PreferencesManager.setViewerShowExifParams(context, it)
                showExifParams = it
            },
        )
        var showLens by remember {
            mutableStateOf(PreferencesManager.isViewerShowLens(context))
        }
        SharedToggleRow(
            title = stringResource(Res.string.m9_viewer_lens),
            description = stringResource(Res.string.m9_viewer_lens_desc),
            checked = showLens,
            onCheckedChange = {
                PreferencesManager.setViewerShowLens(context, it)
                showLens = it
            },
        )
        Text(
            text = stringResource(Res.string.m9_viewer_info_note),
            style = MaterialTheme.typography.bodySmall,
            color = AlphaTheme.colors.onSurfaceVariant,
        )
    }
}
