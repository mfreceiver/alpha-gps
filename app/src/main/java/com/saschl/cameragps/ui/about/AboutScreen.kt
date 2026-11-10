package com.saschl.cameragps.ui.about

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.help_about_description
import cameragps.sharednew.generated.resources.help_about_title
import cameragps.sharednew.generated.resources.m9_about_icon_credit
import cameragps.sharednew.generated.resources.m9_about_issues
import cameragps.sharednew.generated.resources.m9_about_license_body
import cameragps.sharednew.generated.resources.m9_about_license_open
import cameragps.sharednew.generated.resources.m9_about_license_title
import cameragps.sharednew.generated.resources.m9_about_licenses_entry
import cameragps.sharednew.generated.resources.m9_about_licenses_entry_desc
import cameragps.sharednew.generated.resources.m9_about_modified_body
import cameragps.sharednew.generated.resources.m9_about_modified_title
import cameragps.sharednew.generated.resources.m9_about_privacy_entry
import cameragps.sharednew.generated.resources.m9_about_privacy_entry_desc
import cameragps.sharednew.generated.resources.m9_about_source_repo
import cameragps.sharednew.generated.resources.m9_about_source_title
import cameragps.sharednew.generated.resources.m9_about_support_body
import cameragps.sharednew.generated.resources.m9_about_support_button
import cameragps.sharednew.generated.resources.m9_about_support_title
import cameragps.sharednew.generated.resources.m9_about_trademark
import cameragps.sharednew.generated.resources.m9_about_upstream_body
import cameragps.sharednew.generated.resources.m9_about_upstream_title
import cameragps.sharednew.generated.resources.m9_about_version_line
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSnackbarHost
import com.sasch.cameragps.sharednew.ui.components.glass.isScrolledUnderTopBar
import com.sasch.cameragps.sharednew.ui.components.glass.rememberGlassSnackbarHostState
import com.sasch.cameragps.sharednew.ui.components.page.GlassPageScaffold
import com.sasch.cameragps.sharednew.ui.help.AlphaGpsLinks
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsColumn
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsNavRow
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import org.jetbrains.compose.resources.stringResource

internal fun appVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty()

/**
 * About page: identity and version, upstream fork notice (Saschl/alpha-gps), modification
 * statement, GPL-3.0, source / feedback (GitHub only), licenses and privacy entries, the upstream
 * author's sponsor link, icon credit and the Sony trademark disclaimer.
 */
@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    onOpenGplText: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember { appVersionName(context) }
    val scrollState = rememberScrollState()
    val snackbar = rememberGlassSnackbarHostState()

    GlassPageScaffold(
        title = stringResource(Res.string.help_about_title),
        onBackClick = onBackClick,
        scrolled = scrollState.isScrolledUnderTopBar,
    ) { padding ->
        GlassSnackbarHost(snackbar)
        SharedSettingsColumn(Modifier.fillMaxSize().padding(padding), state = scrollState) {
            GlassCard {
                Text(
                    text = "AlphaGPS",
                    style = AlphaType.Title,
                    color = AlphaTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(Res.string.m9_about_version_line, version, context.packageName),
                    style = AlphaType.Caption,
                    color = AlphaTheme.colors.textSecondary,
                )
                BodyText(stringResource(Res.string.help_about_description))
            }

            GlassCard {
                SectionTitle(stringResource(Res.string.m9_about_upstream_title))
                BodyText(stringResource(Res.string.m9_about_upstream_body))
                LinkRow("Saschl/alpha-gps", AlphaGpsLinks.UPSTREAM, snackbar)
                SectionTitle(stringResource(Res.string.m9_about_modified_title), Modifier.padding(top = 4.dp))
                BodyText(stringResource(Res.string.m9_about_modified_body))
            }

            GlassCard {
                SectionTitle(stringResource(Res.string.m9_about_license_title))
                BodyText(stringResource(Res.string.m9_about_license_body))
                SharedSettingsNavRow(
                    title = stringResource(Res.string.m9_about_license_open),
                    onClick = onOpenGplText,
                )
                SharedSettingsNavRow(
                    title = stringResource(Res.string.m9_about_licenses_entry),
                    description = stringResource(Res.string.m9_about_licenses_entry_desc),
                    onClick = onOpenLicenses,
                )
            }

            GlassCard {
                SectionTitle(stringResource(Res.string.m9_about_source_title))
                LinkRow(stringResource(Res.string.m9_about_source_repo), AlphaGpsLinks.REPOSITORY, snackbar)
                LinkRow(stringResource(Res.string.m9_about_issues), AlphaGpsLinks.ISSUES, snackbar)
            }

            SharedSettingsNavRow(
                title = stringResource(Res.string.m9_about_privacy_entry),
                description = stringResource(Res.string.m9_about_privacy_entry_desc),
                onClick = onOpenPrivacy,
            )

            GlassCard {
                SectionTitle(stringResource(Res.string.m9_about_support_title))
                BodyText(stringResource(Res.string.m9_about_support_body))
                CapsuleButton(
                    text = stringResource(Res.string.m9_about_support_button),
                    onClick = { runCatching { uriHandler.openUri(AlphaGpsLinks.UPSTREAM_SPONSOR) } },
                    role = CapsuleRole.Tonal,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = stringResource(Res.string.m9_about_icon_credit),
                style = AlphaType.Caption,
                color = AlphaTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(Res.string.m9_about_trademark),
                style = AlphaType.Caption,
                color = AlphaTheme.colors.textSecondary,
            )
        }
    }
}
