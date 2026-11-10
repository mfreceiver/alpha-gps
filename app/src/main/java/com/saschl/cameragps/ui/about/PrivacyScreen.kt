package com.saschl.cameragps.ui.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.m9_about_privacy_entry
import cameragps.sharednew.generated.resources.m9_privacy_conclusion
import cameragps.sharednew.generated.resources.m9_privacy_conclusion_desc
import cameragps.sharednew.generated.resources.m9_privacy_contact
import cameragps.sharednew.generated.resources.m9_privacy_full
import cameragps.sharednew.generated.resources.m9_privacy_position_note
import cameragps.sharednew.generated.resources.m9_privacy_publisher
import cameragps.sharednew.generated.resources.m9_privacy_use_bluetooth
import cameragps.sharednew.generated.resources.m9_privacy_use_bluetooth_desc
import cameragps.sharednew.generated.resources.m9_privacy_use_location
import cameragps.sharednew.generated.resources.m9_privacy_use_location_desc
import cameragps.sharednew.generated.resources.m9_privacy_use_logs
import cameragps.sharednew.generated.resources.m9_privacy_use_logs_desc
import cameragps.sharednew.generated.resources.m9_privacy_use_notifications
import cameragps.sharednew.generated.resources.m9_privacy_use_notifications_desc
import cameragps.sharednew.generated.resources.m9_privacy_use_wifi
import cameragps.sharednew.generated.resources.m9_privacy_use_wifi_desc
import cameragps.sharednew.generated.resources.m9_privacy_uses_title
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSnackbarHost
import com.sasch.cameragps.sharednew.ui.components.glass.isScrolledUnderTopBar
import com.sasch.cameragps.sharednew.ui.components.glass.rememberGlassSnackbarHostState
import com.sasch.cameragps.sharednew.ui.components.page.GlassPageScaffold
import com.sasch.cameragps.sharednew.ui.help.AlphaGpsLinks
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsColumn
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import org.jetbrains.compose.resources.stringResource

/**
 * In-app privacy summary (en / zh-CN). Mirrors the repository-root privacy.md; the full English
 * policy is one tap away on GitHub.
 */
@Composable
fun PrivacyScreen(onBackClick: () -> Unit) {
    val scrollState = rememberScrollState()
    val snackbar = rememberGlassSnackbarHostState()
    GlassPageScaffold(
        title = stringResource(Res.string.m9_about_privacy_entry),
        onBackClick = onBackClick,
        scrolled = scrollState.isScrolledUnderTopBar,
    ) { padding ->
        GlassSnackbarHost(snackbar)
        SharedSettingsColumn(Modifier.fillMaxSize().padding(padding), state = scrollState) {
            GlassCard {
                Text(
                    text = stringResource(Res.string.m9_privacy_conclusion),
                    style = AlphaType.Title,
                    color = AlphaTheme.colors.textPrimary,
                )
                BodyText(stringResource(Res.string.m9_privacy_conclusion_desc))
                BodyText(stringResource(Res.string.m9_privacy_position_note))
            }

            GlassCard {
                SectionTitle(stringResource(Res.string.m9_privacy_uses_title))
                UsageItem(Res.string.m9_privacy_use_location, Res.string.m9_privacy_use_location_desc)
                UsageItem(Res.string.m9_privacy_use_bluetooth, Res.string.m9_privacy_use_bluetooth_desc)
                UsageItem(Res.string.m9_privacy_use_wifi, Res.string.m9_privacy_use_wifi_desc)
                UsageItem(Res.string.m9_privacy_use_notifications, Res.string.m9_privacy_use_notifications_desc)
                UsageItem(Res.string.m9_privacy_use_logs, Res.string.m9_privacy_use_logs_desc)
            }

            GlassCard {
                BodyText(stringResource(Res.string.m9_privacy_publisher))
                LinkRow(stringResource(Res.string.m9_privacy_contact), AlphaGpsLinks.ISSUES, snackbar)
                LinkRow(stringResource(Res.string.m9_privacy_full), AlphaGpsLinks.PRIVACY, snackbar)
            }
        }
    }
}

@Composable
private fun UsageItem(
    title: org.jetbrains.compose.resources.StringResource,
    description: org.jetbrains.compose.resources.StringResource,
) {
    androidx.compose.foundation.layout.Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            text = stringResource(title),
            style = AlphaType.Label,
            color = AlphaTheme.colors.textPrimary,
        )
        BodyText(stringResource(description))
    }
}
