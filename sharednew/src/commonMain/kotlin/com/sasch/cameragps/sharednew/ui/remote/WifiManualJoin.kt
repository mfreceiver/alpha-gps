package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.content_copy_24px
import cameragps.sharednew.generated.resources.wifi_remote_copy_password
import cameragps.sharednew.generated.resources.wifi_remote_copy_ssid
import cameragps.sharednew.generated.resources.wifi_remote_manual_join_continue
import cameragps.sharednew.generated.resources.wifi_remote_manual_join_hint
import cameragps.sharednew.generated.resources.wifi_remote_network_name
import cameragps.sharednew.generated.resources.wifi_remote_network_password
import cameragps.sharednew.generated.resources.wifi_remote_password_copied
import cameragps.sharednew.generated.resources.wifi_remote_settings
import cameragps.sharednew.generated.resources.wifi_remote_ssid_copied
import com.sasch.cameragps.sharednew.remote.wifi.CameraWifiCredentials
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSnackbarHost
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTextField
import com.sasch.cameragps.sharednew.ui.components.glass.rememberGlassSnackbarHostState
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Join the camera network in system settings, then continue opening the session here. */
@Composable
internal fun WifiManualJoin(
    credentials: CameraWifiCredentials,
    onWifiSettings: () -> Unit,
    onContinue: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = rememberGlassSnackbarHostState()
    val ssidCopied = stringResource(Res.string.wifi_remote_ssid_copied)
    val passwordCopied = stringResource(Res.string.wifi_remote_password_copied)
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(Res.string.wifi_remote_manual_join_hint),
            style = AlphaType.Body,
            color = AlphaTheme.colors.textSecondary,
        )
        WifiCredentialField(
            value = credentials.ssid,
            label = stringResource(Res.string.wifi_remote_network_name),
            copyLabel = stringResource(Res.string.wifi_remote_copy_ssid),
            onCopy = {
                scope.launch {
                    clipboard.setClipEntry(wifiCredentialClip(credentials.ssid, sensitive = false))
                    snackbar.show(ssidCopied)
                }
            },
        )
        WifiCredentialField(
            value = credentials.password,
            label = stringResource(Res.string.wifi_remote_network_password),
            copyLabel = stringResource(Res.string.wifi_remote_copy_password),
            onCopy = {
                scope.launch {
                    clipboard.setClipEntry(wifiCredentialClip(credentials.password, sensitive = true))
                    snackbar.show(passwordCopied)
                }
            },
        )
        CapsuleButton(
            text = stringResource(Res.string.wifi_remote_settings),
            onClick = onWifiSettings,
            role = CapsuleRole.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        CapsuleButton(
            text = stringResource(Res.string.wifi_remote_manual_join_continue),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    GlassSnackbarHost(snackbar)
}

@Composable
private fun WifiCredentialField(value: String, label: String, copyLabel: String, onCopy: () -> Unit) {
    GlassTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = label,
        modifier = Modifier.fillMaxWidth(),
        trailing = {
            GlassIconButton(
                icon = painterResource(Res.drawable.content_copy_24px),
                contentDescription = copyLabel,
                onClick = onCopy,
            )
        },
    )
}

internal expect fun wifiCredentialClip(value: String, sensitive: Boolean): ClipEntry
