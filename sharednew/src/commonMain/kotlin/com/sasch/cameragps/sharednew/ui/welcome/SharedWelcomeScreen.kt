package com.sasch.cameragps.sharednew.ui.welcome

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.app_name_ui
import cameragps.sharednew.generated.resources.camera_24px
import cameragps.sharednew.generated.resources.welcome_bluetooth_24px
import cameragps.sharednew.generated.resources.welcome_get_started_button
import cameragps.sharednew.generated.resources.welcome_gps_body
import cameragps.sharednew.generated.resources.welcome_gps_label
import cameragps.sharednew.generated.resources.welcome_gps_title
import cameragps.sharednew.generated.resources.welcome_location_24px
import cameragps.sharednew.generated.resources.welcome_next
import cameragps.sharednew.generated.resources.welcome_page_position
import cameragps.sharednew.generated.resources.welcome_pair_body
import cameragps.sharednew.generated.resources.welcome_pair_label
import cameragps.sharednew.generated.resources.welcome_pair_title
import cameragps.sharednew.generated.resources.welcome_ready_24px
import cameragps.sharednew.generated.resources.welcome_ready_body
import cameragps.sharednew.generated.resources.welcome_ready_label
import cameragps.sharednew.generated.resources.welcome_ready_title
import cameragps.sharednew.generated.resources.welcome_remote_body
import cameragps.sharednew.generated.resources.welcome_remote_label
import cameragps.sharednew.generated.resources.welcome_remote_title
import cameragps.sharednew.generated.resources.welcome_skip
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleSize
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconTile
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private enum class WelcomePage(
    val label: StringResource,
    val title: StringResource,
    val body: StringResource,
    val icon: DrawableResource,
) {
    Geotagging(
        Res.string.welcome_gps_label,
        Res.string.welcome_gps_title,
        Res.string.welcome_gps_body,
        Res.drawable.welcome_location_24px,
    ),
    Remote(
        Res.string.welcome_remote_label,
        Res.string.welcome_remote_title,
        Res.string.welcome_remote_body,
        Res.drawable.camera_24px,
    ),
    Pairing(
        Res.string.welcome_pair_label,
        Res.string.welcome_pair_title,
        Res.string.welcome_pair_body,
        Res.drawable.welcome_bluetooth_24px,
    ),
    Ready(
        Res.string.welcome_ready_label,
        Res.string.welcome_ready_title,
        Res.string.welcome_ready_body,
        Res.drawable.welcome_ready_24px,
    ),
}

/**
 * First-run introduction (design v3): pages of one glass card each over the [GlassBackground],
 * the app name with "Skip" on top, page dots and the "Next" / "Get started" capsule at the bottom.
 */
@Composable
fun SharedWelcomeScreen(onGetStarted: () -> Unit) {
    val pages = WelcomePage.entries
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val lastPage = pager.currentPage == pages.lastIndex
    val position =
        stringResource(Res.string.welcome_page_position, pager.currentPage + 1, pages.size)
    val colors = AlphaTheme.colors

    GlassBackground {
        Scaffold(containerColor = Color.Transparent) { insets ->
            Column(
                modifier = Modifier.fillMaxSize().padding(insets),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                        .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Res.string.app_name_ui),
                        style = AlphaType.Heading,
                        color = colors.textPrimary,
                    )
                    CapsuleButton(
                        text = stringResource(Res.string.welcome_skip),
                        onClick = onGetStarted,
                        role = CapsuleRole.Secondary,
                        size = CapsuleSize.Small,
                        glass = true,
                    )
                }

                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth().semantics {
                        stateDescription = position
                    },
                ) { index ->
                    WelcomeSlide(pages[index])
                }

                Column(
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        pages.forEachIndexed { index, _ ->
                            val selected = pager.currentPage == index
                            val width by animateDpAsState(if (selected) 28.dp else 8.dp)
                            Box(
                                Modifier.height(8.dp).width(width).background(
                                    color = if (selected) colors.accent else colors.textMuted,
                                    shape = CircleShape,
                                ),
                            )
                        }
                    }
                    CapsuleButton(
                        text = stringResource(
                            if (lastPage) Res.string.welcome_get_started_button else Res.string.welcome_next,
                        ),
                        onClick = {
                            if (lastPage) onGetStarted()
                            else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        },
                        role = CapsuleRole.Primary,
                        size = CapsuleSize.Large,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeSlide(page: WelcomePage) {
    val colors = AlphaTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlassCard(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconTile(
                        icon = painterResource(page.icon),
                        tone = StatusTone.Active,
                        size = 36.dp,
                    )
                    Text(
                        stringResource(page.label),
                        style = AlphaType.Label,
                        color = colors.accent,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(page.title),
                    modifier = Modifier.semantics { heading() },
                    style = AlphaType.Title,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(page.body),
                    style = AlphaType.Body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
