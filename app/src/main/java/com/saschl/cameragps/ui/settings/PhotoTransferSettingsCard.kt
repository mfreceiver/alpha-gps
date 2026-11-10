package com.saschl.cameragps.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.m9_download_format_hint
import cameragps.sharednew.generated.resources.m9_download_format_jpeg_desc
import cameragps.sharednew.generated.resources.m9_photo_transfer_title
import cameragps.sharednew.generated.resources.wifi_remote_default_download_format
import cameragps.sharednew.generated.resources.wifi_remote_default_download_format_jpeg
import cameragps.sharednew.generated.resources.wifi_remote_default_download_format_original
import cameragps.sharednew.generated.resources.wifi_remote_default_download_format_original_desc
import com.sasch.cameragps.sharednew.ui.settings.SharedRadioOption
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.saschl.cameragps.utils.PreferencesManager
import org.jetbrains.compose.resources.stringResource

/**
 * "Photo transfer" settings group: the default download format (batch and single downloads). The
 * preview's details (shooting parameters, lens, histogram) are always available in its details
 * drawer / panel and need no switches.
 */
@Composable
internal fun PhotoTransferSettingsCard() {
    val context = LocalContext.current

    SettingsCard(title = stringResource(Res.string.m9_photo_transfer_title)) {
        Column {
            Text(
                text = stringResource(Res.string.wifi_remote_default_download_format),
                style = AlphaType.Label,
                color = AlphaTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.m9_download_format_hint),
                style = AlphaType.Caption,
                color = AlphaTheme.colors.textSecondary,
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

    }
}
