package com.sasch.cameragps.sharednew.ui.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.guide_accuracy_description
import cameragps.sharednew.generated.resources.guide_accuracy_step_battery
import cameragps.sharednew.generated.resources.guide_accuracy_step_precise
import cameragps.sharednew.generated.resources.guide_accuracy_step_precise_path_android
import cameragps.sharednew.generated.resources.guide_accuracy_title
import cameragps.sharednew.generated.resources.guide_intro
import cameragps.sharednew.generated.resources.guide_link_location_a6400
import cameragps.sharednew.generated.resources.guide_link_location_zve10
import cameragps.sharednew.generated.resources.guide_link_remote_a6400
import cameragps.sharednew.generated.resources.guide_location_link_description
import cameragps.sharednew.generated.resources.guide_location_link_step_enable
import cameragps.sharednew.generated.resources.guide_location_link_step_enable_path
import cameragps.sharednew.generated.resources.guide_location_link_step_remote_note
import cameragps.sharednew.generated.resources.guide_location_link_step_time
import cameragps.sharednew.generated.resources.guide_location_link_step_time_path
import cameragps.sharednew.generated.resources.guide_location_link_title
import cameragps.sharednew.generated.resources.guide_remote_description
import cameragps.sharednew.generated.resources.guide_remote_step_app
import cameragps.sharednew.generated.resources.guide_remote_step_camera
import cameragps.sharednew.generated.resources.guide_remote_step_camera_path
import cameragps.sharednew.generated.resources.guide_remote_title
import cameragps.sharednew.generated.resources.guide_repair_description
import cameragps.sharednew.generated.resources.guide_repair_step_camera
import cameragps.sharednew.generated.resources.guide_repair_step_camera_path
import cameragps.sharednew.generated.resources.guide_repair_step_pair_again
import cameragps.sharednew.generated.resources.guide_repair_step_phone
import cameragps.sharednew.generated.resources.guide_repair_step_phone_path_android
import cameragps.sharednew.generated.resources.guide_repair_step_sony_app
import cameragps.sharednew.generated.resources.guide_repair_step_sony_app_path
import cameragps.sharednew.generated.resources.guide_repair_title
import cameragps.sharednew.generated.resources.guide_startup_description
import cameragps.sharednew.generated.resources.guide_startup_step_disable
import cameragps.sharednew.generated.resources.guide_startup_title
import com.sasch.cameragps.sharednew.ui.components.ScreenContent
import com.sasch.cameragps.sharednew.ui.components.ScrollbarLazyColumn
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassInsetBlock
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSurface
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class GuideStep(
    val text: StringResource,
    val menuPath: StringResource? = null,
)

private data class GuideSection(
    val title: StringResource,
    val description: StringResource,
    val steps: List<GuideStep>,
    val links: List<Pair<StringResource, String>> = emptyList(),
)

/**
 * Visual step-by-step guide for the most common issues, shared by both platforms.
 * Each platform wraps it in its own scaffold (see the Android `TroubleshootingScreen`
 * and `IosTroubleshootingScreen`); hoist [listState] to drive the top bar's `scrolled`.
 */
@Composable
fun TroubleshootingGuideContent(
    innerPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState(),
) {
    val sections = listOf(
        GuideSection(
            title = Res.string.guide_location_link_title,
            description = Res.string.guide_location_link_description,
            steps = listOf(
                GuideStep(
                    Res.string.guide_location_link_step_enable,
                    Res.string.guide_location_link_step_enable_path
                ),
                GuideStep(
                    Res.string.guide_location_link_step_time,
                    Res.string.guide_location_link_step_time_path
                ),
                GuideStep(Res.string.guide_location_link_step_remote_note),
            ),
            links = listOf(
                Res.string.guide_link_location_a6400 to SonyDocLinks.LOCATION_LINK_MENU_A6400,
                Res.string.guide_link_location_zve10 to SonyDocLinks.LOCATION_LINK_MENU_ZVE10,
            ),
        ),
        GuideSection(
            title = Res.string.guide_repair_title,
            description = Res.string.guide_repair_description,
            steps = listOf(
                GuideStep(
                    Res.string.guide_repair_step_phone,
                    Res.string.guide_repair_step_phone_path_android
                ),
                GuideStep(
                    Res.string.guide_repair_step_camera,
                    Res.string.guide_repair_step_camera_path
                ),
                GuideStep(
                    Res.string.guide_repair_step_sony_app,
                    Res.string.guide_repair_step_sony_app_path
                ),
                GuideStep(Res.string.guide_repair_step_pair_again),
            ),
        ),
        GuideSection(
            title = Res.string.guide_remote_title,
            description = Res.string.guide_remote_description,
            steps = listOf(
                GuideStep(
                    Res.string.guide_remote_step_camera,
                    Res.string.guide_remote_step_camera_path
                ),
                GuideStep(Res.string.guide_remote_step_app),
            ),
            links = listOf(
                Res.string.guide_link_remote_a6400 to SonyDocLinks.BLUETOOTH_REMOTE_MENU_A6400,
            ),
        ),
        GuideSection(
            title = Res.string.guide_accuracy_title,
            description = Res.string.guide_accuracy_description,
            steps = buildList {
                add(
                    GuideStep(
                        Res.string.guide_accuracy_step_precise,
                        Res.string.guide_accuracy_step_precise_path_android
                    )
                )
                add(GuideStep(Res.string.guide_accuracy_step_battery))
            },
        ),
        GuideSection(
            title = Res.string.guide_startup_title,
            description = Res.string.guide_startup_description,
            steps = listOf(GuideStep(Res.string.guide_startup_step_disable)),
        ),
    )

    ScreenContent(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        ScrollbarLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = AlphaDimens.screenMargin),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.guide_intro),
                    style = AlphaType.Body,
                    color = AlphaTheme.colors.textSecondary,
                )
            }
            itemsIndexed(sections) { index, section ->
                GuideSectionCard(number = index + 1, section = section)
            }
        }
    }
}

/** One problem: a G1 card with a numbered heading, its explanation, the steps and doc links. */
@Composable
private fun GuideSectionCard(number: Int, section: GuideSection) {
    val uriHandler = LocalUriHandler.current
    val colors = AlphaTheme.colors
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Section number on a small floating glass disc (decorative: the order is the list order).
            GlassSurface(
                modifier = Modifier.size(32.dp).clearAndSetSemantics { },
                level = GlassLevel.Float,
                shape = CircleShape,
                contentAlignment = Alignment.Center,
            ) {
                Text(text = number.toString(), style = AlphaType.Label, color = colors.accent)
            }
            Text(
                text = stringResource(section.title),
                style = AlphaType.Heading,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Text(
            text = stringResource(section.description),
            style = AlphaType.Body,
            color = colors.textSecondary,
        )
        section.steps.forEachIndexed { index, step ->
            GuideStepRow(number = index + 1, step = step)
        }
        section.links.forEach { (labelRes, url) ->
            val label = stringResource(labelRes)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AlphaDimens.minTouchTarget)
                    .clickable(role = Role.Button) { uriHandler.openUri(url) },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = label,
                    style = AlphaType.Label,
                    color = colors.accent,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }
    }
}

/** A numbered step; a camera / phone menu path is shown below it on an inset block. */
@Composable
private fun GuideStepRow(number: Int, step: GuideStep) {
    val colors = AlphaTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassInsetBlock(
            modifier = Modifier.size(24.dp),
            contentPadding = PaddingValues(0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number.toString(), style = AlphaType.Tag, color = colors.textSecondary)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(step.text),
                style = AlphaType.Body,
                color = colors.textPrimary,
            )
            step.menuPath?.let { pathRes ->
                GlassInsetBlock(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        text = stringResource(pathRes),
                        style = AlphaType.Caption,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}
