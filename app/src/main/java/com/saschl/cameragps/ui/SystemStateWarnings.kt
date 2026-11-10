package com.saschl.cameragps.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.bluetooth_disabled_message
import cameragps.sharednew.generated.resources.bluetooth_disabled_title
import cameragps.sharednew.generated.resources.location_services_disabled_message
import cameragps.sharednew.generated.resources.location_services_disabled_title
import cameragps.sharednew.generated.resources.open_bluetooth_settings
import cameragps.sharednew.generated.resources.open_location_settings
import com.sasch.cameragps.sharednew.ui.components.glass.GlassWarningCard
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import com.saschl.cameragps.R
import org.jetbrains.compose.resources.stringResource

/**
 * Bluetooth is off: nothing can reach the camera. An error-toned [GlassWarningCard] (red leading
 * edge + faint red tint instead of the old `errorContainer` card) with a shortcut to the settings.
 */
@Composable
fun BluetoothWarningCard() {
    val context = LocalContext.current
    SystemStateWarningCard(
        title = stringResource(Res.string.bluetooth_disabled_title),
        message = stringResource(Res.string.bluetooth_disabled_message),
        actionLabel = stringResource(Res.string.open_bluetooth_settings),
        onAction = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
    )
}

/** Location services are off: no position to write to the camera. Same card as [BluetoothWarningCard]. */
@Composable
fun LocationWarningCard() {
    val context = LocalContext.current
    SystemStateWarningCard(
        title = stringResource(Res.string.location_services_disabled_title),
        message = stringResource(Res.string.location_services_disabled_message),
        actionLabel = stringResource(Res.string.open_location_settings),
        onAction = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
    )
}

@Composable
private fun SystemStateWarningCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    GlassWarningCard(
        title = title,
        message = message,
        modifier = Modifier.padding(bottom = 8.dp),
        tone = StatusTone.Error,
        icon = painterResource(R.drawable.warning_24px),
        actionLabel = actionLabel,
        onAction = onAction,
    )
}
