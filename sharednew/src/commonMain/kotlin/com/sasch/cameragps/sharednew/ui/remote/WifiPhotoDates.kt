package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.wifi_remote_browser_failed
import cameragps.sharednew.generated.resources.wifi_remote_dates_count
import cameragps.sharednew.generated.resources.wifi_remote_dates_empty
import cameragps.sharednew.generated.resources.wifi_remote_dates_loading
import cameragps.sharednew.generated.resources.wifi_remote_dates_unknown
import cameragps.sharednew.generated.resources.wifi_remote_viewer_retry
import com.sasch.cameragps.sharednew.remote.wifi.DateGroup
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoBrowserState
import org.jetbrains.compose.resources.stringResource

/** The label of a day: the camera-local date as recorded, or "Unknown date" for the leftover group. */
@Composable
internal fun dateGroupLabel(group: DateGroup): String = dateKeyLabel(group.key)

@Composable
internal fun dateKeyLabel(key: String): String =
    if (key == com.sasch.cameragps.sharednew.remote.wifi.UNKNOWN_DATE_KEY) {
        stringResource(Res.string.wifi_remote_dates_unknown)
    } else key

/**
 * The day list shown before any photo grid. While the card is being enumerated it only says so (the
 * enumeration has no honest progress to report); a failure offers a retry; otherwise one row per day
 * with its capture count. Opening a day is a local view change.
 */
@Composable
internal fun WifiPhotoDateList(
    browser: WifiPhotoBrowserState,
    enabled: Boolean,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        browser.failed -> Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.wifi_remote_browser_failed),
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = onRetry, enabled = !browser.loading) {
                Text(stringResource(Res.string.wifi_remote_viewer_retry))
            }
        }

        browser.loading && browser.dates.isEmpty() ->
            Text(stringResource(Res.string.wifi_remote_dates_loading), modifier)

        browser.dates.isEmpty() -> Text(stringResource(Res.string.wifi_remote_dates_empty), modifier)

        else -> LazyColumn(modifier) {
            items(browser.dates, key = { it.key }) { group ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = enabled) { onOpen(group.key) }
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(dateGroupLabel(group), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(Res.string.wifi_remote_dates_count, group.count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
