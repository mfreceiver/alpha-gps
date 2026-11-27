package com.sasch.cameragps.sharednew.ui.help

import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import cameragps.sharednew.generated.resources.guide_accuracy_cause
import cameragps.sharednew.generated.resources.guide_accuracy_description
import cameragps.sharednew.generated.resources.guide_accuracy_step_battery
import cameragps.sharednew.generated.resources.guide_accuracy_step_precise
import cameragps.sharednew.generated.resources.guide_accuracy_step_precise_path_android
import cameragps.sharednew.generated.resources.guide_accuracy_title
import cameragps.sharednew.generated.resources.guide_common_cause_prefix
import cameragps.sharednew.generated.resources.guide_intro
import cameragps.sharednew.generated.resources.guide_link_location_a6400
import cameragps.sharednew.generated.resources.guide_link_location_zve10
import cameragps.sharednew.generated.resources.guide_link_remote_a6400
import cameragps.sharednew.generated.resources.guide_location_link_cause
import cameragps.sharednew.generated.resources.guide_location_link_description
import cameragps.sharednew.generated.resources.guide_location_link_step_enable
import cameragps.sharednew.generated.resources.guide_location_link_step_enable_path
import cameragps.sharednew.generated.resources.guide_location_link_remote_note_title
import cameragps.sharednew.generated.resources.guide_location_link_step_remote_note
import cameragps.sharednew.generated.resources.guide_location_link_step_time
import cameragps.sharednew.generated.resources.guide_location_link_step_time_path
import cameragps.sharednew.generated.resources.guide_location_link_title
import cameragps.sharednew.generated.resources.guide_remote_cause
import cameragps.sharednew.generated.resources.guide_remote_description
import cameragps.sharednew.generated.resources.guide_remote_step_app
import cameragps.sharednew.generated.resources.guide_remote_step_camera
import cameragps.sharednew.generated.resources.guide_remote_step_camera_path
import cameragps.sharednew.generated.resources.guide_remote_title
import cameragps.sharednew.generated.resources.guide_repair_cause
import cameragps.sharednew.generated.resources.guide_repair_description
import cameragps.sharednew.generated.resources.guide_repair_step_camera
import cameragps.sharednew.generated.resources.guide_repair_step_camera_path
import cameragps.sharednew.generated.resources.guide_repair_step_pair_again
import cameragps.sharednew.generated.resources.guide_repair_step_phone
import cameragps.sharednew.generated.resources.guide_repair_step_phone_path_android
import cameragps.sharednew.generated.resources.guide_repair_step_sony_app
import cameragps.sharednew.generated.resources.guide_repair_step_sony_app_path
import cameragps.sharednew.generated.resources.guide_repair_title
import cameragps.sharednew.generated.resources.guide_startup_cause
import cameragps.sharednew.generated.resources.guide_startup_description
import cameragps.sharednew.generated.resources.guide_startup_step_disable
import cameragps.sharednew.generated.resources.guide_startup_title
import com.sasch.cameragps.sharednew.ui.components.ScreenContent
import com.sasch.cameragps.sharednew.ui.components.ScrollbarLazyColumn
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassInsetBlock
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSurface
import com.sasch.cameragps.sharednew.ui.components.glass.GlassWarningCard
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import org.jetbrains.compose.resources.StringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private data class GuideStep(
    val text: StringResource,
    val menuPath: StringResource? = null,
)

/**
 * Deep-link target of the guide: opening it with a topic scrolls to that problem card and
 * highlights it for a moment; without one the guide starts at the top.
 */
enum class GuideTopic {
    /** "Enable location & time zone linking" — camera-side linking and time settings. */
    LocationLink,

    /** "Camera connects but GPS doesn't work" — remove the old pairing and pair again. */
    Repair,

    /** "Turn off the Bluetooth remote" — remote / location-linking exclusivity. */
    Remote,
}

/** A warning-styled note shown under a section's steps ([GlassWarningCard]). */
private data class GuideNote(val title: StringResource, val message: StringResource)

private data class GuideSection(
    val topic: GuideTopic?,
    val title: StringResource,
    val commonCause: StringResource,
    val description: StringResource,
    val steps: List<GuideStep>,
    val links: List<Pair<StringResource, String>> = emptyList(),
    val note: GuideNote? = null,
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
    topic: GuideTopic? = null,
) {
    val sections = listOf(
        GuideSection(
            topic = GuideTopic.LocationLink,
            title = Res.string.guide_location_link_title,
            commonCause = Res.string.guide_location_link_cause,
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
            ),
            note = GuideNote(
                Res.string.guide_location_link_remote_note_title,
                Res.string.guide_location_link_step_remote_note,
            ),
            links = listOf(
                Res.string.guide_link_location_a6400 to SonyDocLinks.LOCATION_LINK_MENU_A6400,
                Res.string.guide_link_location_zve10 to SonyDocLinks.LOCATION_LINK_MENU_ZVE10,
            ),
        ),
        GuideSection(
            topic = GuideTopic.Repair,
            title = Res.string.guide_repair_title,
            commonCause = Res.string.guide_repair_cause,
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
            topic = GuideTopic.Remote,
            title = Res.string.guide_remote_title,
            commonCause = Res.string.guide_remote_cause,
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
            topic = null,
            title = Res.string.guide_accuracy_title,
            commonCause = Res.string.guide_accuracy_cause,
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
            topic = null,
            title = Res.string.guide_startup_title,
            commonCause = Res.string.guide_startup_cause,
            description = Res.string.guide_startup_description,
            steps = listOf(GuideStep(Res.string.guide_startup_step_disable)),
        ),
    )

    // Deep link: scroll the requested card into view (item 0 is the intro) and mark it for a few
    // seconds; no topic = start at the top, nothing highlighted.
    var highlighted by remember(topic) { mutableStateOf(topic) }
    // Two effects on purpose: the scroll can be cancelled by the user's own gesture (animateScrollToItem
    // throws a CancellationException from the scroll mutex), which must not take the highlight clock
    // with it. The scroll runs in its own child scope; the highlight always clears after its delay.
    LaunchedEffect(topic) {
        if (topic == null) return@LaunchedEffect
        val index = sections.indexOfFirst { it.topic == topic }
        if (index >= 0) {
            launch {
                try {
                    listState.animateScrollToItem(index + 1)
                } catch (_: CancellationException) {
                    // Interrupted by a user gesture: leave the list where the user put it.
                }
            }
        }
    }
    LaunchedEffect(topic) {
        if (topic == null) return@LaunchedEffect
        delay(HIGHLIGHT_MILLIS)
        highlighted = null
    }

    ScreenContent(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        ScrollbarLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap),
            scrollbarGutter = 0.dp,
            contentPadding = PaddingValues(
                start = AlphaDimens.screenMargin, end = AlphaDimens.screenMargin, top = 8.dp, bottom = 32.dp,
            ),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.guide_intro),
                    style = AlphaType.Body,
                    color = AlphaTheme.colors.textSecondary,
                )
            }
            itemsIndexed(sections) { index, section ->
                GuideSectionCard(
                    number = index + 1,
                    section = section,
                    highlighted = section.topic != null && section.topic == highlighted,
                )
            }
        }
    }
}

/** One problem: a G1 card with a numbered heading, its explanation, the steps and doc links. */
@Composable
private fun GuideSectionCard(number: Int, section: GuideSection, highlighted: Boolean) {
    val uriHandler = LocalUriHandler.current
    val colors = AlphaTheme.colors
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) Modifier.border(2.dp, colors.accent, AlphaShapes.card) else Modifier,
            ),
    ) {
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
            text = stringResource(Res.string.guide_common_cause_prefix, stringResource(section.commonCause)),
            style = AlphaType.Caption,
            color = colors.textSecondary,
        )
        Text(
            text = stringResource(section.description),
            style = AlphaType.Body,
            color = colors.textSecondary,
        )
        section.steps.forEachIndexed { index, step ->
            GuideStepRow(
                number = index + 1,
                text = stringResource(step.text),
                menuPath = step.menuPath?.let { stringResource(it) },
            )
        }
        section.note?.let { note ->
            GlassWarningCard(
                title = stringResource(note.title),
                message = stringResource(note.message),
            )
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

/** A numbered step; a camera / phone menu path (or expected screen) is shown below it on an inset block. */
@Composable
fun GuideStepRow(number: Int, text: String, menuPath: String? = null) {
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
                text = text,
                style = AlphaType.Body,
                color = colors.textPrimary,
            )
            menuPath?.let { path ->
                GlassInsetBlock(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        text = path,
                        style = AlphaType.Caption,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}

/** How long a deep-linked card keeps its highlight border. */
private const val HIGHLIGHT_MILLIS = 3_000L
