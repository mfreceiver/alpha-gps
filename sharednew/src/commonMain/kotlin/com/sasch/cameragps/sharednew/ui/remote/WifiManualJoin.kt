package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.wifi_remote_manual_join_continue
import cameragps.sharednew.generated.resources.wifi_remote_manual_join_hint
import cameragps.sharednew.generated.resources.wifi_remote_network_name
import cameragps.sharednew.generated.resources.wifi_remote_network_password
import cameragps.sharednew.generated.resources.wifi_remote_settings
import com.sasch.cameragps.sharednew.remote.wifi.CameraWifiCredentials
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTextField
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import org.jetbrains.compose.resources.stringResource

/**
 * Manual join fallback: the camera's network name and password as read-only [GlassTextField]s
 * (selectable for copying), then "Wi-Fi settings" (secondary) and "continue" (primary).
 */
@Composable
internal fun WifiManualJoin(
    credentials: CameraWifiCredentials,
    onWifiSettings: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(Res.string.wifi_remote_manual_join_hint),
            style = AlphaType.Body,
            color = AlphaTheme.colors.textSecondary,
        )
        GlassTextField(
            credentials.ssid,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = stringResource(Res.string.wifi_remote_network_name),
            modifier = Modifier.fillMaxWidth()
        )
        GlassTextField(
            credentials.password,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = stringResource(Res.string.wifi_remote_network_password),
            modifier = Modifier.fillMaxWidth()
        )
        CapsuleButton(
            text = stringResource(Res.string.wifi_remote_settings),
            onClick = onWifiSettings,
            role = CapsuleRole.Secondary,
        )
        CapsuleButton(
            text = stringResource(Res.string.wifi_remote_manual_join_continue),
            onClick = onContinue,
        )
    }
}
