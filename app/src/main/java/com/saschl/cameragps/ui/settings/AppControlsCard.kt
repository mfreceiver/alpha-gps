package com.saschl.cameragps.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.app_controls
import cameragps.sharednew.generated.resources.enable_app
import cameragps.sharednew.generated.resources.enable_app_description
import cameragps.sharednew.generated.resources.enable_auto_start
import cameragps.sharednew.generated.resources.enable_auto_start_description
import cameragps.sharednew.generated.resources.haptic_feedback
import cameragps.sharednew.generated.resources.haptic_feedback_description
import cameragps.sharednew.generated.resources.reset_welcome
import cameragps.sharednew.generated.resources.will_show_welcome
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSnackbarHost
import com.sasch.cameragps.sharednew.ui.components.glass.rememberGlassSnackbarHostState
import com.sasch.cameragps.sharednew.ui.settings.SharedToggleRow
import com.saschl.cameragps.service.LocationSenderService
import com.saschl.cameragps.utils.PreferencesManager
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AppControlsCard(
    isAppEnabled: Boolean,
    onAppEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val willShowWelcomeText = stringResource(Res.string.will_show_welcome)
    // In-page confirmation (no system Toast window); the host draws into the page's GlassBackground.
    val snackbar = rememberGlassSnackbarHostState()
    GlassSnackbarHost(snackbar)

    SettingsCard(title = stringResource(Res.string.app_controls)) {
        SharedToggleRow(
            title = stringResource(Res.string.enable_app),
            description = stringResource(Res.string.enable_app_description),
            checked = isAppEnabled,
            onCheckedChange = { enabled ->
                onAppEnabledChange(enabled)
                context.stopService(
                    Intent(
                        context.applicationContext,
                        LocationSenderService::class.java
                    )
                )
            },
        )

        var isAutoStartAfterRebootEnabled by remember {
            mutableStateOf(PreferencesManager.getAutoStartAfterBootEnabled(context))
        }
        SharedToggleRow(
            title = stringResource(Res.string.enable_auto_start),
            description = stringResource(Res.string.enable_auto_start_description),
            checked = isAutoStartAfterRebootEnabled,
            onCheckedChange = { enabled ->
                PreferencesManager.setAutoStartAfterBootEnabled(context, enabled)
                isAutoStartAfterRebootEnabled = enabled
            },
        )

        var isHapticsEnabled by remember {
            mutableStateOf(PreferencesManager.isHapticsEnabled(context))
        }
        SharedToggleRow(
            title = stringResource(Res.string.haptic_feedback),
            description = stringResource(Res.string.haptic_feedback_description),
            checked = isHapticsEnabled,
            onCheckedChange = { enabled ->
                PreferencesManager.setHapticsEnabled(context, enabled)
                isHapticsEnabled = enabled
            },
        )

        CapsuleButton(
            text = stringResource(Res.string.reset_welcome),
            onClick = {
                snackbar.show(willShowWelcomeText)
                PreferencesManager.showFirstLaunch(context)
                PreferencesManager.setPermissionsIgnored(context, false)
            },
            role = CapsuleRole.Tonal,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
