package com.saschl.cameragps.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.bg_conditions_title
import cameragps.sharednew.generated.resources.bg_rule_back_body
import cameragps.sharednew.generated.resources.bg_rule_back_title
import cameragps.sharednew.generated.resources.bg_rule_disconnect_body
import cameragps.sharednew.generated.resources.bg_rule_disconnect_title
import cameragps.sharednew.generated.resources.bg_rule_download_body
import cameragps.sharednew.generated.resources.bg_rule_download_title
import cameragps.sharednew.generated.resources.bg_rule_home_body
import cameragps.sharednew.generated.resources.bg_rule_home_title
import cameragps.sharednew.generated.resources.bg_rule_swipe_body
import cameragps.sharednew.generated.resources.bg_rule_swipe_title
import cameragps.sharednew.generated.resources.bg_rules_footnote
import cameragps.sharednew.generated.resources.bg_rules_title
import cameragps.sharednew.generated.resources.bg_state_ble
import cameragps.sharednew.generated.resources.bg_state_ble_value
import cameragps.sharednew.generated.resources.bg_state_fgs
import cameragps.sharednew.generated.resources.bg_state_fgs_off
import cameragps.sharednew.generated.resources.bg_state_fgs_on
import cameragps.sharednew.generated.resources.bg_state_title
import cameragps.sharednew.generated.resources.bg_state_wifi
import cameragps.sharednew.generated.resources.bg_state_wifi_closing
import cameragps.sharednew.generated.resources.bg_state_wifi_connecting
import cameragps.sharednew.generated.resources.bg_state_wifi_off
import cameragps.sharednew.generated.resources.bg_state_wifi_ready
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import com.sasch.cameragps.sharednew.ui.device.DeviceConnectionStatus
import com.sasch.cameragps.sharednew.ui.device.deviceConnectionStatus
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsGroup
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.saschl.cameragps.AppServices
import com.saschl.cameragps.service.LocationSenderService
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * "Background & running conditions": a live three-line state (cameras / Wi-Fi / background service)
 * and what Home, lock, app switch, swipe-away, root Back and a manual disconnect do. The wording
 * mirrors `BackgroundWifiPolicy` and `AppExitGate` (see docs/wifi-architecture.md): Wi-Fi closes at
 * once unless the user accepted a download (kept up to 30 minutes), swipe / root Back always close
 * Wi-Fi, and Bluetooth, GPS and the foreground service are never closed by leaving the app.
 */
@Composable
internal fun BackgroundConditionsScreen(onBackClick: () -> Unit) {
    SettingsSectionScreen(
        title = stringResource(Res.string.bg_conditions_title),
        onBackClick = onBackClick,
    ) {
        val services = AppServices.from(LocalContext.current)
        val lifecycleOwner = LocalLifecycleOwner.current
        val sessions by services.orchestrator.sessions.collectAsState()
        // Not observable sources: polled slowly only while this page is at least STARTED.
        val keepActive by produceState(0, lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    value = services.deviceDao.getKeepActiveDeviceCount()
                    delay(POLL_MILLIS)
                }
            }
        }
        val serviceRunning by produceState(LocationSenderService.isRunning, lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    value = LocationSenderService.isRunning
                    delay(POLL_MILLIS)
                }
            }
        }
        val connected = sessions.values.count { deviceConnectionStatus(it) == DeviceConnectionStatus.Connected }
        val wifiPhase = aggregateWifiPhase(sessions.values.map { it.wifiRemote.phase })

        SharedSettingsGroup(title = stringResource(Res.string.bg_state_title)) {
            StateRow(
                label = stringResource(Res.string.bg_state_ble),
                value = stringResource(Res.string.bg_state_ble_value, connected, keepActive),
            )
            StateRow(
                label = stringResource(Res.string.bg_state_wifi),
                value = stringResource(
                    when (wifiPhase) {
                        WifiRemotePhase.Idle, WifiRemotePhase.Failed -> Res.string.bg_state_wifi_off
                        WifiRemotePhase.Ready -> Res.string.bg_state_wifi_ready
                        WifiRemotePhase.Closing -> Res.string.bg_state_wifi_closing
                        else -> Res.string.bg_state_wifi_connecting
                    },
                ),
            )
            StateRow(
                label = stringResource(Res.string.bg_state_fgs),
                value = stringResource(if (serviceRunning) Res.string.bg_state_fgs_on else Res.string.bg_state_fgs_off),
            )
        }

        SharedSettingsGroup(title = stringResource(Res.string.bg_rules_title)) {
            RuleBlock(Res.string.bg_rule_home_title, Res.string.bg_rule_home_body)
            RuleBlock(Res.string.bg_rule_download_title, Res.string.bg_rule_download_body)
            RuleBlock(Res.string.bg_rule_swipe_title, Res.string.bg_rule_swipe_body)
            RuleBlock(Res.string.bg_rule_back_title, Res.string.bg_rule_back_body)
            RuleBlock(Res.string.bg_rule_disconnect_title, Res.string.bg_rule_disconnect_body)
        }
        Text(
            text = stringResource(Res.string.bg_rules_footnote),
            style = AlphaType.Caption,
            color = AlphaTheme.colors.textMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** Most advanced Wi-Fi phase across cameras: Ready > Closing > in progress > Failed/Idle. */
private fun aggregateWifiPhase(phases: List<WifiRemotePhase>): WifiRemotePhase = when {
    WifiRemotePhase.Ready in phases -> WifiRemotePhase.Ready
    WifiRemotePhase.Closing in phases -> WifiRemotePhase.Closing
    else -> phases.firstOrNull { it != WifiRemotePhase.Idle && it != WifiRemotePhase.Failed }
        ?: WifiRemotePhase.Idle
}

@Composable
private fun StateRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = AlphaType.Body,
            color = AlphaTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = AlphaType.Label,
            color = AlphaTheme.colors.textPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RuleBlock(
    title: org.jetbrains.compose.resources.StringResource,
    body: org.jetbrains.compose.resources.StringResource,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = stringResource(title), style = AlphaType.Label, color = AlphaTheme.colors.textPrimary)
        Text(text = stringResource(body), style = AlphaType.Body, color = AlphaTheme.colors.textSecondary)
    }
}

private const val POLL_MILLIS = 1_000L
