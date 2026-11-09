package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.arrow_back_24px
import cameragps.sharednew.generated.resources.close_24px
import cameragps.sharednew.generated.resources.download_24px
import cameragps.sharednew.generated.resources.link_off_24px
import cameragps.sharednew.generated.resources.refresh_24px
import cameragps.sharednew.generated.resources.select_all_24px
import cameragps.sharednew.generated.resources.wifi_remote_batch_download_jpg
import cameragps.sharednew.generated.resources.wifi_remote_batch_download_jpg_none
import cameragps.sharednew.generated.resources.wifi_remote_browser_refresh
import cameragps.sharednew.generated.resources.wifi_remote_browser_title
import cameragps.sharednew.generated.resources.wifi_remote_close
import cameragps.sharednew.generated.resources.wifi_remote_disconnect
import cameragps.sharednew.generated.resources.wifi_remote_download
import cameragps.sharednew.generated.resources.wifi_remote_selection_all
import cameragps.sharednew.generated.resources.wifi_remote_selection_clear
import cameragps.sharednew.generated.resources.wifi_remote_selection_count
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoDownload
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.formatByteSize
import com.sasch.cameragps.sharednew.remote.wifi.groupCameraPhotos
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * App bar of the photo grid. Everything is an icon button (with a content description):
 *
 * - browsing: back, title, refresh, disconnect;
 * - selecting: cancel selection (X), "N selected" as the title, select all on this page,
 *   download.
 *
 * Refresh and disconnect live here instead of in rows above the grid, so the grid gets
 * the whole body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WifiPhotoBrowserTopBar(
    state: WifiRemoteState,
    viewModel: WifiRemoteViewModel,
    policy: WifiFormatPolicy,
    onDownload: (List<WifiPhotoDownload>) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val browser = state.photoBrowser
    val busy = browser.loading || state.imageTransfer.busy
    val selectedIds = viewModel.selectedPhotoIds
    val selecting = selectedIds.isNotEmpty()
    val captures = remember(browser.photos) { groupCameraPhotos(browser.photos) }
    TopAppBar(
        title = {
            Text(
                if (selecting) stringResource(Res.string.wifi_remote_selection_count, selectedIds.size)
                else stringResource(Res.string.wifi_remote_browser_title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (selecting) {
                IconButton(onClick = viewModel::clearPhotoSelection) {
                    Icon(
                        painterResource(Res.drawable.close_24px),
                        contentDescription = stringResource(Res.string.wifi_remote_selection_clear),
                    )
                }
            } else {
                IconButton(onClick = onBack, enabled = !busy) {
                    Icon(
                        painterResource(Res.drawable.arrow_back_24px),
                        contentDescription = stringResource(Res.string.wifi_remote_close),
                    )
                }
            }
        },
        actions = {
            if (selecting) {
                IconButton(
                    onClick = viewModel::selectAllPhotos,
                    enabled = !busy && captures.any { it.id !in selectedIds },
                ) {
                    Icon(
                        painterResource(Res.drawable.select_all_24px),
                        contentDescription = stringResource(Res.string.wifi_remote_selection_all),
                    )
                }
                // JPG-only: the description carries the batch size ("Download 5 JPG (12 MB)")
                // that the old text button showed; an empty plan disables the action.
                val plan = viewModel.jpegBatchPlan(policy, state)
                val downloadLabel = when {
                    plan == null -> stringResource(Res.string.wifi_remote_download)
                    plan.downloads.isEmpty() -> stringResource(Res.string.wifi_remote_batch_download_jpg_none)
                    else -> stringResource(
                        Res.string.wifi_remote_batch_download_jpg,
                        plan.downloads.size, formatByteSize(plan.totalBytes)
                    )
                }
                IconButton(
                    onClick = { viewModel.requestBatchDownload(policy, state, onDownload) },
                    enabled = !busy && (plan == null || plan.downloads.isNotEmpty()),
                ) {
                    Icon(painterResource(Res.drawable.download_24px), contentDescription = downloadLabel)
                }
            } else {
                IconButton(
                    onClick = { viewModel.controller.browsePhotos(viewModel.identifier) },
                    enabled = !busy,
                ) {
                    Icon(
                        painterResource(Res.drawable.refresh_24px),
                        contentDescription = stringResource(Res.string.wifi_remote_browser_refresh),
                    )
                }
                // Always available, even mid-transfer: it ends the session and the transfer with it.
                IconButton(onClick = { viewModel.disconnectAndExit(onClose) }) {
                    Icon(
                        painterResource(Res.drawable.link_off_24px),
                        contentDescription = stringResource(Res.string.wifi_remote_disconnect),
                    )
                }
            }
        },
    )
}
