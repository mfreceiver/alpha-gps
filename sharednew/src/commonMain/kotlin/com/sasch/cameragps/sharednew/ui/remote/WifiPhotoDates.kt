package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.m2_error_24px
import cameragps.sharednew.generated.resources.mw_dates_reload
import cameragps.sharednew.generated.resources.mw_photo_library_24px
import cameragps.sharednew.generated.resources.mw_weekday_1
import cameragps.sharednew.generated.resources.mw_weekday_2
import cameragps.sharednew.generated.resources.mw_weekday_3
import cameragps.sharednew.generated.resources.mw_weekday_4
import cameragps.sharednew.generated.resources.mw_weekday_5
import cameragps.sharednew.generated.resources.mw_weekday_6
import cameragps.sharednew.generated.resources.mw_weekday_7
import cameragps.sharednew.generated.resources.wifi_remote_browser_failed
import cameragps.sharednew.generated.resources.wifi_remote_dates_count
import cameragps.sharednew.generated.resources.wifi_remote_dates_empty
import cameragps.sharednew.generated.resources.wifi_remote_dates_loading
import cameragps.sharednew.generated.resources.wifi_remote_dates_unknown
import com.sasch.cameragps.sharednew.remote.wifi.DateGroup
import com.sasch.cameragps.sharednew.remote.wifi.UNKNOWN_DATE_KEY
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoBrowserState
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressCircle
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressSize
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSurface
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import com.sasch.cameragps.sharednew.ui.theme.WindowClass
import com.sasch.cameragps.sharednew.ui.theme.currentWindowClass
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The label of a day: the camera-local date as recorded, or "Unknown date" for the leftover group. */
@Composable
internal fun dateGroupLabel(group: DateGroup): String = dateKeyLabel(group.key)

@Composable
internal fun dateKeyLabel(key: String): String =
    if (key == UNKNOWN_DATE_KEY) stringResource(Res.string.wifi_remote_dates_unknown) else key

@Composable
private fun weekdayLabel(weekday: Int): String = stringResource(
    when (weekday) {
        1 -> Res.string.mw_weekday_1
        2 -> Res.string.mw_weekday_2
        3 -> Res.string.mw_weekday_3
        4 -> Res.string.mw_weekday_4
        5 -> Res.string.mw_weekday_5
        6 -> Res.string.mw_weekday_6
        else -> Res.string.mw_weekday_7
    }
)

/** Only the weekday ("Mon"), or empty for the unknown group — no relative words like "today". */
@Composable
private fun daySubtitle(key: String): String {
    val weekday = dateKeyWeekday(key) ?: return ""
    return weekdayLabel(weekday)
}

/**
 * The day list shown before any photo grid (newest day first, "unknown date" last). The whole card
 * is already enumerated, so this costs no camera traffic; opening a day is a local view change.
 * While the card is being read it only says so (no honest progress exists); a failed read offers
 * "read again". One column on a phone, a wider centred column on near-square windows and two
 * columns in landscape.
 */
@Composable
internal fun WifiPhotoDateList(
    browser: WifiPhotoBrowserState,
    enabled: Boolean,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    coverThumbnails: Map<Long, ImageBitmap> = emptyMap(),
    onVisibleCovers: (Long, List<Long>) -> Unit = { _, _ -> },
) {
    val windowClass = currentWindowClass()
    when {
        browser.failed -> CenteredMessage(modifier) {
            PhotoBrowserMessage(
                message = stringResource(Res.string.wifi_remote_browser_failed),
                isError = true,
                actionLabel = stringResource(Res.string.mw_dates_reload),
                actionEnabled = enabled,
                onAction = onRetry,
            )
        }

        browser.loading && browser.dates.isEmpty() -> CenteredMessage(modifier) {
            GlassProgressCircle(size = GlassProgressSize.Large)
            Text(
                stringResource(Res.string.wifi_remote_dates_loading),
                style = AlphaType.Body, color = AlphaTheme.colors.textSecondary,
            )
        }

        browser.dates.isEmpty() -> CenteredMessage(modifier) {
            PhotoBrowserMessage(stringResource(Res.string.wifi_remote_dates_empty))
        }

        else -> {
            val columns = if (windowClass == WindowClass.Landscape) 2 else 1
            val gridState = rememberLazyGridState()
            LaunchedEffect(gridState, browser.browserGeneration, browser.dates) {
                snapshotFlow {
                    gridState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                        browser.dates.getOrNull(info.index)?.coverHandle
                    }
                }.distinctUntilChanged().collect { visibleHandles ->
                    onVisibleCovers(browser.browserGeneration, visibleHandles)
                }
            }
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyVerticalGrid(
                    GridCells.Fixed(columns),
                    Modifier.let {
                        if (windowClass == WindowClass.NearSquare) it.widthIn(max = 640.dp) else it
                    }.fillMaxSize(),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = AlphaDimens.screenMargin,
                        end = AlphaDimens.screenMargin,
                        top = 8.dp,
                        bottom = 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(browser.dates, key = { it.key }) { group ->
                        DateRow(group, group.coverHandle?.let(coverThumbnails::get), enabled, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateRow(
    group: DateGroup,
    cover: ImageBitmap?,
    enabled: Boolean,
    onOpen: (String) -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth().heightIn(min = AlphaDimens.rowMinHeight),
        level = GlassLevel.Card,
        shape = AlphaShapes.row,
        onClick = { onOpen(group.key) },
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = AlphaDimens.screenMargin, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(AlphaShapes.inset)
                    .background(
                        if (cover != null) AlphaTheme.colors.thumbnailFrame
                        else AlphaTheme.colors.textMuted.copy(alpha = 0.14f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (cover != null) {
                    FittedThumbnail(cover, Modifier.fillMaxSize())
                } else {
                    Icon(
                        painterResource(Res.drawable.mw_photo_library_24px),
                        contentDescription = null,
                        tint = AlphaTheme.colors.textMuted,
                        modifier = Modifier.size(AlphaDimens.iconSize),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(dateGroupLabel(group), style = AlphaType.Heading, color = AlphaTheme.colors.textPrimary)
                val subtitle = daySubtitle(group.key)
                if (subtitle.isNotEmpty()) Text(
                    subtitle, style = AlphaType.Caption,
                    color = AlphaTheme.colors.textSecondary
                )
            }
            Text(
                stringResource(Res.string.wifi_remote_dates_count, group.count),
                style = AlphaType.Body, color = AlphaTheme.colors.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun CenteredMessage(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

@Composable
internal fun PhotoBrowserMessage(
    message: String,
    isError: Boolean = false,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
) {
    Icon(
        painterResource(if (isError) Res.drawable.m2_error_24px else Res.drawable.mw_photo_library_24px),
        contentDescription = null,
        tint = if (isError) AlphaTheme.colors.danger else AlphaTheme.colors.textSecondary,
        modifier = Modifier.size(48.dp),
    )
    Text(
        message, textAlign = TextAlign.Center,
        style = AlphaType.Body, color = AlphaTheme.colors.textSecondary,
    )
    if (actionLabel != null) {
        CapsuleButton(actionLabel, onAction, role = CapsuleRole.Tonal, enabled = actionEnabled)
    }
}
