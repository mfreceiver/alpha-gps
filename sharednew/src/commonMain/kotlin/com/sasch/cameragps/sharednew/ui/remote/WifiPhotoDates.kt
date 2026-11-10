package com.sasch.cameragps.sharednew.ui.remote

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
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
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSurface
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import com.sasch.cameragps.sharednew.ui.theme.WindowClass
import com.sasch.cameragps.sharednew.ui.theme.currentWindowClass
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
) {
    val windowClass = currentWindowClass()
    when {
        browser.failed -> CenteredMessage(modifier) {
            Text(
                stringResource(Res.string.wifi_remote_browser_failed), textAlign = TextAlign.Center,
                color = AlphaTheme.colors.onSurfaceVariant
            )
            CapsuleButton(
                text = stringResource(Res.string.mw_dates_reload), onClick = onRetry,
                role = CapsuleRole.Tonal, enabled = !browser.loading,
            )
        }

        browser.loading && browser.dates.isEmpty() -> CenteredMessage(modifier) {
            CircularProgressIndicator(Modifier.size(32.dp))
            Text(stringResource(Res.string.wifi_remote_dates_loading), color = AlphaTheme.colors.onSurfaceVariant)
        }

        browser.dates.isEmpty() -> CenteredMessage(modifier) {
            Icon(
                painterResource(Res.drawable.mw_photo_library_24px), null,
                tint = AlphaTheme.colors.onSurfaceVariant, modifier = Modifier.size(48.dp)
            )
            Text(
                stringResource(Res.string.wifi_remote_dates_empty), textAlign = TextAlign.Center,
                color = AlphaTheme.colors.onSurfaceVariant
            )
        }

        else -> {
            val columns = if (windowClass == WindowClass.Landscape) 2 else 1
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyVerticalGrid(
                    GridCells.Fixed(columns),
                    Modifier.fillMaxSize().let {
                        if (windowClass == WindowClass.NearSquare) it.widthIn(max = 640.dp) else it
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(browser.dates, key = { it.key }) { group ->
                        DateRow(group, enabled, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateRow(group: DateGroup, enabled: Boolean, onOpen: (String) -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        level = GlassLevel.Card,
        shape = AlphaShapes.row,
        onClick = { onOpen(group.key) },
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(dateGroupLabel(group), style = MaterialTheme.typography.titleMedium)
                val subtitle = daySubtitle(group.key)
                if (subtitle.isNotEmpty()) Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = AlphaTheme.colors.onSurfaceVariant
                )
            }
            Text(
                stringResource(Res.string.wifi_remote_dates_count, group.count),
                style = MaterialTheme.typography.bodyMedium, color = AlphaTheme.colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CenteredMessage(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}
