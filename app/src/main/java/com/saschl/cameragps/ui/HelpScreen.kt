package com.saschl.cameragps.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.faq_background_work_answer
import cameragps.sharednew.generated.resources.faq_background_work_question
import cameragps.sharednew.generated.resources.faq_camera_not_appearing_answer
import cameragps.sharednew.generated.resources.faq_camera_not_appearing_question
import cameragps.sharednew.generated.resources.faq_camera_not_connecting_answer
import cameragps.sharednew.generated.resources.faq_camera_not_connecting_question
import cameragps.sharednew.generated.resources.faq_connect_camera_answer
import cameragps.sharednew.generated.resources.faq_connect_camera_question
import cameragps.sharednew.generated.resources.faq_disconnects_answer
import cameragps.sharednew.generated.resources.faq_disconnects_question
import cameragps.sharednew.generated.resources.faq_enable_service_answer
import cameragps.sharednew.generated.resources.faq_enable_service_question
import cameragps.sharednew.generated.resources.faq_gps_accuracy_answer
import cameragps.sharednew.generated.resources.faq_gps_accuracy_question
import cameragps.sharednew.generated.resources.faq_permissions_answer
import cameragps.sharednew.generated.resources.faq_permissions_question
import cameragps.sharednew.generated.resources.guide_intro
import cameragps.sharednew.generated.resources.guide_title
import cameragps.sharednew.generated.resources.help_about_title
import cameragps.sharednew.generated.resources.help_faq_title
import cameragps.sharednew.generated.resources.help_need_more_description
import cameragps.sharednew.generated.resources.help_need_more_title
import cameragps.sharednew.generated.resources.help_report_issue
import cameragps.sharednew.generated.resources.how_about_privacy
import cameragps.sharednew.generated.resources.how_about_privacy_answer
import cameragps.sharednew.generated.resources.is_there_documenation
import cameragps.sharednew.generated.resources.is_there_documenation_answer
import cameragps.sharednew.generated.resources.m9_help_about_entry_desc
import com.sasch.cameragps.sharednew.ui.components.ScrollbarLazyColumn
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSurface
import com.sasch.cameragps.sharednew.ui.components.page.GlassPageScaffold
import com.sasch.cameragps.sharednew.ui.help.AlphaGpsLinks
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsNavRow
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.saschl.cameragps.ui.about.AboutScreen
import com.saschl.cameragps.ui.about.LicenseTextScreen
import com.saschl.cameragps.ui.about.LicensesScreen
import com.saschl.cameragps.ui.about.PrivacyScreen
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class FaqItem(
    val questionRes: StringResource,
    val answerRes: StringResource,
    val containsLink: Boolean = false
)

/**
 * Help (FAQ) page. About / open-source licenses / privacy are sub-pages of the help flow (the About
 * entry is the first card), so the app-level navigation keeps a single Help destination.
 */
@Composable
fun HelpScreen(
    onBackClick: () -> Unit,
    onTroubleshootingClick: () -> Unit = {}
) {
    val backStack = rememberNavBackStack(HelpDestination.Faq)

    fun navigateBack() {
        if (backStack.lastIndex > 0) {
            backStack.removeAt(backStack.lastIndex)
        } else {
            onBackClick()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { navigateBack() }
    ) { destination ->
        when (destination) {
            HelpDestination.Faq -> NavEntry(destination) {
                HelpFaqScreen(
                    onBackClick = { navigateBack() },
                    onAboutClick = { backStack.add(HelpDestination.About) },
                    onTroubleshootingClick = onTroubleshootingClick,
                )
            }

            HelpDestination.About -> NavEntry(destination) {
                AboutScreen(
                    onBackClick = { navigateBack() },
                    onOpenGplText = { backStack.add(HelpDestination.GplText) },
                    onOpenLicenses = { backStack.add(HelpDestination.Licenses) },
                    onOpenPrivacy = { backStack.add(HelpDestination.Privacy) },
                )
            }

            HelpDestination.Licenses -> NavEntry(destination) {
                LicensesScreen(
                    onBackClick = { navigateBack() },
                    onOpenLicense = { id -> backStack.add(HelpDestination.LicenseText(id)) },
                )
            }

            is HelpDestination.LicenseText -> NavEntry(destination) {
                LicenseTextScreen(licenseId = destination.id, onBackClick = { navigateBack() })
            }

            HelpDestination.GplText -> NavEntry(destination) {
                LicenseTextScreen(licenseId = null, gplText = true, onBackClick = { navigateBack() })
            }

            HelpDestination.Privacy -> NavEntry(destination) {
                PrivacyScreen(onBackClick = { navigateBack() })
            }

            else -> NavEntry(destination) {}
        }
    }
}

@Composable
private fun HelpFaqScreen(
    onBackClick: () -> Unit,
    onAboutClick: () -> Unit,
    onTroubleshootingClick: () -> Unit,
) {
    val faqItems = remember {
        listOf(
            FaqItem(Res.string.is_there_documenation, Res.string.is_there_documenation_answer, containsLink = true),
            FaqItem(Res.string.how_about_privacy, Res.string.how_about_privacy_answer),
            FaqItem(Res.string.faq_enable_service_question, Res.string.faq_enable_service_answer),
            FaqItem(Res.string.faq_connect_camera_question, Res.string.faq_connect_camera_answer),
            FaqItem(Res.string.faq_camera_not_appearing_question, Res.string.faq_camera_not_appearing_answer),
            FaqItem(Res.string.faq_camera_not_connecting_question, Res.string.faq_camera_not_connecting_answer),
            FaqItem(Res.string.faq_permissions_question, Res.string.faq_permissions_answer),
            FaqItem(Res.string.faq_gps_accuracy_question, Res.string.faq_gps_accuracy_answer),
            FaqItem(Res.string.faq_background_work_question, Res.string.faq_background_work_answer),
            FaqItem(Res.string.faq_disconnects_question, Res.string.faq_disconnects_answer),
        )
    }

    GlassPageScaffold(
        title = stringResource(Res.string.help_faq_title),
        onBackClick = onBackClick,
    ) { innerPadding ->
        ScrollbarLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
        ) {
            item {
                SharedSettingsNavRow(
                    title = stringResource(Res.string.help_about_title),
                    description = stringResource(Res.string.m9_help_about_entry_desc),
                    onClick = onAboutClick,
                )
            }
            item {
                SharedSettingsNavRow(
                    title = stringResource(Res.string.guide_title),
                    description = stringResource(Res.string.guide_intro),
                    onClick = onTroubleshootingClick,
                )
            }

            items(faqItems) { faq ->
                FaqRow(faq = faq, initiallyExpanded = faq == faqItems.first())
            }

            item {
                GlassCard {
                    Text(
                        text = stringResource(Res.string.help_need_more_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AlphaTheme.colors.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.help_need_more_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlphaTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FaqRow(faq: FaqItem, initiallyExpanded: Boolean) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = { expanded = !expanded },
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(faq.questionRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AlphaTheme.colors.onSurface,
            )
            if (expanded) {
                if (faq.containsLink) {
                    val linkStyle = TextLinkStyles(
                        style = SpanStyle(
                            color = AlphaTheme.colors.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    val answer = stringResource(faq.answerRes)
                    val reportIssue = stringResource(Res.string.help_report_issue)
                    Text(
                        text = buildAnnotatedString {
                            append(answer)
                            append("\n")
                            withLink(LinkAnnotation.Url(AlphaGpsLinks.README, linkStyle)) {
                                append(AlphaGpsLinks.REPOSITORY)
                            }
                            append("\n")
                            withLink(LinkAnnotation.Url(AlphaGpsLinks.ISSUES, linkStyle)) {
                                append(reportIssue)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlphaTheme.colors.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(faq.answerRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlphaTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private sealed interface HelpDestination : NavKey {
    @Serializable
    data object Faq : HelpDestination

    @Serializable
    data object About : HelpDestination

    @Serializable
    data object Licenses : HelpDestination

    @Serializable
    data class LicenseText(val id: String) : HelpDestination

    @Serializable
    data object GplText : HelpDestination

    @Serializable
    data object Privacy : HelpDestination
}
