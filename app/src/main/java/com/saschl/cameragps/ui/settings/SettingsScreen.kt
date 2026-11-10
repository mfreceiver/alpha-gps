package com.saschl.cameragps.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.a1_settings_help_desc
import cameragps.sharednew.generated.resources.a1_settings_help_title
import cameragps.sharednew.generated.resources.a1_settings_logs_desc
import cameragps.sharednew.generated.resources.a1_settings_logs_title
import cameragps.sharednew.generated.resources.battery_optimization_settings_title
import cameragps.sharednew.generated.resources.event_sounds_open_section_description
import cameragps.sharednew.generated.resources.event_sounds_title
import cameragps.sharednew.generated.resources.settings
import com.sasch.cameragps.sharednew.ui.settings.SharedLanguageSettingsCard
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsColumn
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsNavRow
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsScreen
import com.sasch.cameragps.sharednew.whatsnew.WhatsNewSettingsCard
import com.sasch.cameragps.sharednew.whatsnew.WhatsNewState
import com.saschl.cameragps.utils.PreferencesManager
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    whatsNew: WhatsNewState,
    onBackClick: () -> Unit,
    onHelpClick: () -> Unit = {},
    onLogsClick: () -> Unit = {},
) {
    val settingsBackStack = rememberNavBackStack(SettingsDestination.Overview)

    fun navigateBack() {
        if (settingsBackStack.lastIndex > 0) {
            settingsBackStack.removeAt(settingsBackStack.lastIndex)
        } else {
            onBackClick()
        }
    }

    NavDisplay(
        backStack = settingsBackStack,
        onBack = { navigateBack() }
    ) { destination ->
        when (destination) {
            SettingsDestination.Overview -> {
                NavEntry(SettingsDestination.Overview) {
                    SettingsOverviewScreen(
                        whatsNew = whatsNew,
                        onBackClick = { navigateBack() },
                        onHelpClick = onHelpClick,
                        onLogsClick = onLogsClick,
                        onOpenDestination = { nextDestination ->
                            settingsBackStack.add(nextDestination)
                        }
                    )
                }
            }

            SettingsDestination.EventSounds -> {
                NavEntry(SettingsDestination.EventSounds) {
                    EventSoundsScreen(onBackClick = { navigateBack() })
                }
            }

            SettingsDestination.BatteryOptimization -> {
                NavEntry(SettingsDestination.BatteryOptimization) {
                    BatteryOptimizationSettingsScreen(onBackClick = { navigateBack() })
                }
            }

            else -> {
                NavEntry(destination) {}
            }
        }
    }
}

@Composable
private fun SettingsOverviewScreen(
    whatsNew: WhatsNewState,
    onBackClick: () -> Unit,
    onHelpClick: () -> Unit,
    onLogsClick: () -> Unit,
    onOpenDestination: (SettingsDestination) -> Unit,
) {
    SharedSettingsScreen(
        title = stringResource(Res.string.settings),
        onBackClick = onBackClick,
    ) { paddingValues ->
        SharedSettingsColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            AppControlsInlineSection()
            PhotoTransferSettingsCard()
            LogLevelSettingsCard()
            SharedSettingsNavRow(
                title = stringResource(Res.string.event_sounds_title),
                description = stringResource(Res.string.event_sounds_open_section_description),
                onClick = { onOpenDestination(SettingsDestination.EventSounds) }
            )
            SharedSettingsNavRow(
                title = stringResource(Res.string.battery_optimization_settings_title),
                onClick = { onOpenDestination(SettingsDestination.BatteryOptimization) }
            )
            SharedLanguageSettingsCard()
            // Help and the activity log moved here from the device list's top bar.
            SharedSettingsNavRow(
                title = stringResource(Res.string.a1_settings_help_title),
                description = stringResource(Res.string.a1_settings_help_desc),
                onClick = onHelpClick
            )
            SharedSettingsNavRow(
                title = stringResource(Res.string.a1_settings_logs_title),
                description = stringResource(Res.string.a1_settings_logs_desc),
                onClick = onLogsClick
            )
            WhatsNewSettingsCard(whatsNew)
        }
    }
}

@Composable
private fun AppControlsInlineSection() {
    val context = LocalContext.current
    var isAppEnabled by remember {
        mutableStateOf(PreferencesManager.isAppEnabled(context))
    }

    AppControlsCard(
        isAppEnabled = isAppEnabled,
        onAppEnabledChange = { enabled ->
            isAppEnabled = enabled
            PreferencesManager.setAppEnabled(context, enabled)
        }
    )
}

@Composable
private fun BatteryOptimizationSettingsScreen(onBackClick: () -> Unit) {
    SettingsSectionScreen(
        title = stringResource(Res.string.battery_optimization_settings_title),
        onBackClick = onBackClick
    ) {
        BatteryOptimizationCard()
    }
}


private sealed interface SettingsDestination : NavKey {
    @Serializable
    data object Overview : SettingsDestination

    @Serializable
    data object EventSounds : SettingsDestination

    @Serializable
    data object BatteryOptimization : SettingsDestination

    @Serializable
    data object Language : SettingsDestination

}
