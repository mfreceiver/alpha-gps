package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.mw_connect_failed
import cameragps.sharednew.generated.resources.mw_connect_grant
import cameragps.sharednew.generated.resources.wifi_remote_approval
import cameragps.sharednew.generated.resources.wifi_remote_auto_connect
import cameragps.sharednew.generated.resources.wifi_remote_auto_setup
import cameragps.sharednew.generated.resources.wifi_remote_automatic_heading
import cameragps.sharednew.generated.resources.wifi_remote_blocked_heading
import cameragps.sharednew.generated.resources.wifi_remote_closing
import cameragps.sharednew.generated.resources.wifi_remote_connect
import cameragps.sharednew.generated.resources.wifi_remote_connected
import cameragps.sharednew.generated.resources.wifi_remote_disconnect
import cameragps.sharednew.generated.resources.wifi_remote_ip
import cameragps.sharednew.generated.resources.wifi_remote_joining
import cameragps.sharednew.generated.resources.wifi_remote_manual
import cameragps.sharednew.generated.resources.wifi_remote_manual_hide
import cameragps.sharednew.generated.resources.wifi_remote_opening
import cameragps.sharednew.generated.resources.wifi_remote_prepare_only
import cameragps.sharednew.generated.resources.wifi_remote_prepare_only_hint
import cameragps.sharednew.generated.resources.wifi_remote_preparing
import cameragps.sharednew.generated.resources.wifi_remote_settings
import cameragps.sharednew.generated.resources.wifi_remote_setup
import cameragps.sharednew.generated.resources.wifi_remote_setup_detect_address
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteFailure
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.ui.components.ScreenContent
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressCircle
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTextField
import com.sasch.cameragps.sharednew.ui.components.glass.GlassWarningCard
import com.sasch.cameragps.sharednew.ui.components.glass.StatusLine
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import com.sasch.cameragps.sharednew.ui.components.glass.overlayFocusLevel
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import org.jetbrains.compose.resources.stringResource

/** Connection controls inside the caller's page scaffold and GlassBackground. */
@Composable
internal fun WifiConnectFallbackContent(
    state: WifiRemoteState,
    supportsAutomaticConnection: Boolean,
    supportsManualSetup: Boolean,
    supportsAddressDiscovery: Boolean,
    host: String,
    onHostChange: (String) -> Unit,
    manual: Boolean,
    onToggleManual: () -> Unit,
    onAutoConnect: () -> Unit,
    onManualConnect: () -> Unit,
    onWifiSettings: () -> Unit,
    onContinueManualJoin: () -> Unit,
    onDisconnect: () -> Unit,
    networkPermissionMessage: String,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    notice: String? = null,
    connectionBlocked: Boolean = false,
    onAppSettings: (() -> Unit)? = null,
    wifiSettingsLabel: String = stringResource(Res.string.wifi_remote_settings),
    openingSessionMessage: String = stringResource(Res.string.wifi_remote_opening),
    idleMessage: String? = null,
    readyContent: @Composable () -> Unit = {},
) {
    val busy = state.phase != WifiRemotePhase.Idle && state.phase != WifiRemotePhase.Failed
    val supportsGuidedSetup = supportsAutomaticConnection || supportsManualSetup
    ScreenContent(
        modifier = modifier.overlayFocusLevel().verticalScroll(scrollState)
            .padding(horizontal = AlphaDimens.screenMargin, vertical = 12.dp),
        maxWidth = 560.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap)) {
            if (notice != null) {
                GlassWarningCard(
                    title = stringResource(Res.string.wifi_remote_blocked_heading),
                    message = notice,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            val failure = state.failure
            if (failure != null || state.phase == WifiRemotePhase.Failed) {
                val (actionLabel, onAction) = when (failure) {
                    WifiRemoteFailure.NetworkPermissionDenied -> stringResource(Res.string.mw_connect_grant) to onAppSettings
                    WifiRemoteFailure.WifiDisabled -> wifiSettingsLabel to onWifiSettings
                    else -> null to null
                }
                GlassWarningCard(
                    title = stringResource(Res.string.mw_connect_failed),
                    message = wifiFailureMessage(failure, networkPermissionMessage),
                    tone = StatusTone.Error,
                    actionLabel = actionLabel,
                    actionModifier = Modifier.fillMaxWidth(),
                    onAction = onAction,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (!busy && supportsGuidedSetup) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(Res.string.wifi_remote_automatic_heading),
                        style = AlphaType.Heading,
                        color = AlphaTheme.colors.textPrimary,
                    )
                    Text(
                        stringResource(
                            if (supportsAutomaticConnection) Res.string.wifi_remote_auto_setup
                            else Res.string.wifi_remote_prepare_only_hint,
                        ),
                        style = AlphaType.Body,
                        color = AlphaTheme.colors.textSecondary,
                    )
                    CapsuleButton(
                        text = stringResource(
                            if (supportsAutomaticConnection) Res.string.wifi_remote_auto_connect
                            else Res.string.wifi_remote_prepare_only,
                        ),
                        onClick = onAutoConnect,
                        enabled = !connectionBlocked,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CapsuleButton(
                        text = stringResource(
                            if (manual) Res.string.wifi_remote_manual_hide else Res.string.wifi_remote_manual,
                        ),
                        onClick = onToggleManual,
                        role = CapsuleRole.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (!busy && (manual || !supportsGuidedSetup)) {
                WifiManualConnectCard(
                    supportsAddressDiscovery = supportsAddressDiscovery,
                    host = host,
                    onHostChange = onHostChange,
                    onManualConnect = onManualConnect,
                    onWifiSettings = onWifiSettings,
                    wifiSettingsLabel = wifiSettingsLabel,
                    enabled = !connectionBlocked,
                    primary = !supportsGuidedSetup,
                )
            }
            if (state.phase == WifiRemotePhase.Idle && idleMessage != null) {
                Text(idleMessage, style = AlphaType.Body, color = AlphaTheme.colors.textSecondary)
            }
            val progress = when (state.phase) {
                WifiRemotePhase.PreparingCamera -> stringResource(Res.string.wifi_remote_preparing)
                WifiRemotePhase.OpeningSession -> openingSessionMessage
                WifiRemotePhase.AwaitingNetworkApproval -> stringResource(Res.string.wifi_remote_approval)
                WifiRemotePhase.JoiningNetwork -> stringResource(Res.string.wifi_remote_joining)
                WifiRemotePhase.Closing -> stringResource(Res.string.wifi_remote_closing)
                else -> null
            }
            if (progress != null || state.phase == WifiRemotePhase.Ready) {
                GlassCard(Modifier.fillMaxWidth()) {
                    if (progress != null) {
                        Row(
                            Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GlassProgressCircle()
                            Text(progress, style = AlphaType.Body, color = AlphaTheme.colors.textPrimary)
                        }
                    } else {
                        StatusLine(
                            title = stringResource(Res.string.wifi_remote_connected, state.cameraName),
                            tone = StatusTone.Success,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        readyContent()
                    }
                }
            }
            state.manualNetwork?.let { credentials ->
                WifiManualJoin(credentials, onWifiSettings, onContinueManualJoin)
            }
            if (busy) {
                CapsuleButton(
                    text = stringResource(Res.string.wifi_remote_disconnect),
                    onClick = onDisconnect,
                    role = CapsuleRole.Secondary,
                    enabled = state.phase != WifiRemotePhase.Closing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WifiManualConnectCard(
    supportsAddressDiscovery: Boolean,
    host: String,
    onHostChange: (String) -> Unit,
    onManualConnect: () -> Unit,
    onWifiSettings: () -> Unit,
    wifiSettingsLabel: String,
    enabled: Boolean,
    primary: Boolean,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(Res.string.wifi_remote_manual),
            style = AlphaType.Heading,
            color = AlphaTheme.colors.textPrimary,
        )
        Text(
            stringResource(
                if (supportsAddressDiscovery) Res.string.wifi_remote_setup_detect_address
                else Res.string.wifi_remote_setup,
            ),
            style = AlphaType.Body,
            color = AlphaTheme.colors.textSecondary,
        )
        CapsuleButton(
            text = wifiSettingsLabel,
            onClick = onWifiSettings,
            role = CapsuleRole.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        GlassTextField(
            value = host,
            onValueChange = onHostChange,
            label = stringResource(Res.string.wifi_remote_ip),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        CapsuleButton(
            text = stringResource(Res.string.wifi_remote_connect),
            onClick = onManualConnect,
            role = if (primary) CapsuleRole.Primary else CapsuleRole.Tonal,
            enabled = enabled && (supportsAddressDiscovery || host.isNotBlank()),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
