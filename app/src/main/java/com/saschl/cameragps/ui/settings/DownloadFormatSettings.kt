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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.m9_download_format_jpeg_desc
import cameragps.sharednew.generated.resources.r_download_format
import cameragps.sharednew.generated.resources.r_download_format_hint
import cameragps.sharednew.generated.resources.r_format_ask
import cameragps.sharednew.generated.resources.r_format_ask_desc
import cameragps.sharednew.generated.resources.r_format_jpg_raw_desc
import cameragps.sharednew.generated.resources.r_format_raw_desc
import com.sasch.cameragps.sharednew.remote.wifi.WifiDownloadFormatSetting
import com.sasch.cameragps.sharednew.ui.settings.SharedRadioOption
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.saschl.cameragps.utils.PreferencesManager
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Label and explanation of each download format option (format names are not localized). */
private fun WifiDownloadFormatSetting.labelText(ask: String): String = when (this) {
    WifiDownloadFormatSetting.Ask -> ask
    WifiDownloadFormatSetting.Jpg -> "JPG"
    WifiDownloadFormatSetting.Raw -> "RAW"
    WifiDownloadFormatSetting.JpgRaw -> "JPG + RAW"
}

private val WifiDownloadFormatSetting.description: StringResource
    get() = when (this) {
        WifiDownloadFormatSetting.Ask -> Res.string.r_format_ask_desc
        WifiDownloadFormatSetting.Jpg -> Res.string.m9_download_format_jpeg_desc
        WifiDownloadFormatSetting.Raw -> Res.string.r_format_raw_desc
        WifiDownloadFormatSetting.JpgRaw -> Res.string.r_format_jpg_raw_desc
    }

/**
 * Rows of the "Photo transfer" settings group: the download format used by single and batch
 * downloads — ask each time (default), JPG, RAW or JPG + RAW. Stored under the existing key
 * `wifi_default_download_format` ([PreferencesManager.wifiDownloadFormat]).
 */
@Composable
internal fun DownloadFormatSettingsRows() {
    val context = LocalContext.current
    Column {
        Text(
            text = stringResource(Res.string.r_download_format),
            style = AlphaType.Label,
            color = AlphaTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.r_download_format_hint),
            style = AlphaType.Caption,
            color = AlphaTheme.colors.textSecondary,
        )
    }
    var format by remember { mutableStateOf(PreferencesManager.wifiDownloadFormat(context)) }
    val askLabel = stringResource(Res.string.r_format_ask)
    Column(Modifier.selectableGroup()) {
        WifiDownloadFormatSetting.entries.forEach { option ->
            SharedRadioOption(
                label = option.labelText(askLabel),
                description = stringResource(option.description),
                selected = format == option,
                onClick = {
                    PreferencesManager.setWifiDownloadFormat(context, option)
                    format = option
                },
            )
        }
    }
}
