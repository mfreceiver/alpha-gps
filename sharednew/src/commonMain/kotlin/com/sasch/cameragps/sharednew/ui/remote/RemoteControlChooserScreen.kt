package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.arrow_back_24px
import cameragps.sharednew.generated.resources.back
import cameragps.sharednew.generated.resources.m2_bluetooth_24px
import cameragps.sharednew.generated.resources.mw_wifi_24px
import cameragps.sharednew.generated.resources.remote_chooser_no_auto_connect
import cameragps.sharednew.generated.resources.remote_chooser_title
import cameragps.sharednew.generated.resources.remote_mode_ble_desc
import cameragps.sharednew.generated.resources.remote_mode_ble_title
import cameragps.sharednew.generated.resources.remote_mode_wifi_desc
import cameragps.sharednew.generated.resources.remote_mode_wifi_title
import cameragps.sharednew.generated.resources.remote_need_bluetooth
import cameragps.sharednew.generated.resources.remote_start_ble
import cameragps.sharednew.generated.resources.remote_start_wifi
import com.sasch.cameragps.sharednew.ui.components.ScreenContent
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconTile
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTopBar
import com.sasch.cameragps.sharednew.ui.components.glass.isScrolledUnderTopBar
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Remote-shooting mode picker. Opening it never starts a connection: both entries are plain
 * navigation, named by what they can do (Bluetooth shutter: no live view, saves power; Wi-Fi live
 * view remote). They need a ready Bluetooth link ([bluetoothReady]); until then both are disabled
 * with a one-line hint.
 */
@Composable
fun RemoteControlChooserScreen(
    bluetoothReady: Boolean,
    onBle: () -> Unit,
    onWifi: () -> Unit,
    onBack: () -> Unit,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = onBack,
    )
    val scroll = rememberScrollState()
    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopBar(
                    title = stringResource(Res.string.remote_chooser_title),
                    scrolled = scroll.isScrolledUnderTopBar,
                    navigationIcon = {
                        GlassIconButton(
                            icon = painterResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.back),
                            onClick = onBack,
                        )
                    },
                )
            },
        ) { padding ->
            ScreenContent(
                modifier = Modifier.fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scroll)
                    .padding(horizontal = AlphaDimens.screenMargin, vertical = 8.dp),
                maxWidth = 560.dp,
            ) {
                val blockedReason = if (bluetoothReady) null else stringResource(Res.string.remote_need_bluetooth)
                Column(verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap)) {
                    Text(
                        stringResource(Res.string.remote_chooser_no_auto_connect),
                        style = AlphaType.Caption,
                        color = AlphaTheme.colors.textSecondary,
                    )
                    ModeCard(
                        title = stringResource(Res.string.remote_mode_ble_title),
                        description = stringResource(Res.string.remote_mode_ble_desc),
                        icon = painterResource(Res.drawable.m2_bluetooth_24px),
                        action = stringResource(Res.string.remote_start_ble),
                        role = CapsuleRole.Tonal,
                        blockedReason = blockedReason,
                        onClick = onBle,
                    )
                    ModeCard(
                        title = stringResource(Res.string.remote_mode_wifi_title),
                        description = stringResource(Res.string.remote_mode_wifi_desc),
                        icon = painterResource(Res.drawable.mw_wifi_24px),
                        action = stringResource(Res.string.remote_start_wifi),
                        role = CapsuleRole.Primary,
                        blockedReason = blockedReason,
                        onClick = onWifi,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    icon: Painter,
    action: String,
    role: CapsuleRole,
    blockedReason: String?,
    onClick: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassIconTile(icon)
            Text(title, style = AlphaType.Heading, color = AlphaTheme.colors.textPrimary)
        }
        Text(description, style = AlphaType.Body, color = AlphaTheme.colors.textSecondary)
        if (blockedReason != null) {
            Text(blockedReason, style = AlphaType.Caption, color = AlphaTheme.colors.warningText)
        }
        CapsuleButton(
            text = action,
            onClick = onClick,
            role = role,
            enabled = blockedReason == null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
