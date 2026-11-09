package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.wifi_remote_viewer_deselect
import cameragps.sharednew.generated.resources.wifi_remote_viewer_select
import cameragps.sharednew.generated.resources.wifi_remote_batch_progress
import cameragps.sharednew.generated.resources.check_24px
import cameragps.sharednew.generated.resources.search_24px
import cameragps.sharednew.generated.resources.wifi_remote_browser_empty
import cameragps.sharednew.generated.resources.wifi_remote_browser_failed
import cameragps.sharednew.generated.resources.wifi_remote_batch_skip_hint
import cameragps.sharednew.generated.resources.wifi_remote_batch_summary
import cameragps.sharednew.generated.resources.wifi_remote_batch_summary_no_skip
import cameragps.sharednew.generated.resources.wifi_remote_download_format_size
import cameragps.sharednew.generated.resources.wifi_remote_format_raw_only
import cameragps.sharednew.generated.resources.wifi_remote_photo_no_jpg
import cameragps.sharednew.generated.resources.wifi_remote_preview_photo
import cameragps.sharednew.generated.resources.wifi_remote_skip_no_jpg
import cameragps.sharednew.generated.resources.wifi_remote_skip_saved
import cameragps.sharednew.generated.resources.wifi_remote_skip_too_large
import cameragps.sharednew.generated.resources.wifi_remote_conversion_failed
import cameragps.sharednew.generated.resources.wifi_remote_converting_jpeg
import cameragps.sharednew.generated.resources.wifi_remote_download_format
import cameragps.sharednew.generated.resources.wifi_remote_download_saved
import cameragps.sharednew.generated.resources.wifi_remote_download_too_large
import cameragps.sharednew.generated.resources.wifi_remote_preview_unavailable
import cameragps.sharednew.generated.resources.wifi_remote_selection_start
import cameragps.sharednew.generated.resources.wifi_remote_storage_permission
import cameragps.sharednew.generated.resources.wifi_remote_transfer_cancel
import cameragps.sharednew.generated.resources.wifi_remote_transfer_cancelled
import cameragps.sharednew.generated.resources.wifi_remote_transfer_failed
import cameragps.sharednew.generated.resources.wifi_remote_transfer_failed_catalog_changed
import cameragps.sharednew.generated.resources.wifi_remote_transfer_failed_protocol
import cameragps.sharednew.generated.resources.wifi_remote_transfer_failed_redownload_copy
import cameragps.sharednew.generated.resources.wifi_remote_transfer_failed_storage
import cameragps.sharednew.generated.resources.wifi_remote_transfer_failed_timeout
import cameragps.sharednew.generated.resources.wifi_remote_transfer_progress
import cameragps.sharednew.generated.resources.wifi_remote_transfer_saved
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferState
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoDownload
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoDownloadFormat
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.WifiTransferFailureReason
import com.sasch.cameragps.sharednew.remote.wifi.WifiDetailPlan
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatBadge
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy
import com.sasch.cameragps.sharednew.remote.wifi.WifiSkipReason
import com.sasch.cameragps.sharednew.remote.wifi.formatBadge
import com.sasch.cameragps.sharednew.remote.wifi.formatByteSize
import com.sasch.cameragps.sharednew.remote.wifi.formatLabel
import com.sasch.cameragps.sharednew.remote.wifi.groupCameraPhotos
import com.sasch.cameragps.sharednew.remote.wifi.shouldShowBatchSummary
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WifiPhotoBrowser(
    state: WifiRemoteState, viewModel: WifiRemoteViewModel,
    onDownload: (List<WifiPhotoDownload>) -> Unit,
    defaultDownloadFormatJpeg: Boolean = false,
) {
    val controller = viewModel.controller
    val identifier = viewModel.identifier
    val browser = state.photoBrowser
    val policy = WifiFormatPolicy.of(defaultDownloadFormatJpeg)
    val thumbnails by controller.thumbnails.collectAsState()
    val thumbnailsLoading by controller.thumbnailsLoading.collectAsState()
    val captures = remember(browser.photos) { groupCameraPhotos(browser.photos) }
    val transfer = state.imageTransfer
    val busy = browser.loading || transfer.busy
    val selectedIds = viewModel.selectedPhotoIds
    val selecting = selectedIds.isNotEmpty()
    val gridState = rememberLazyGridState()
    var gridOffset by rememberSaveable { mutableStateOf(browser.offset) }
    LaunchedEffect(browser.offset) {
        if (gridOffset != browser.offset) {
            gridState.scrollToItem(0)
            gridOffset = browser.offset
        }
    }
    // The grid reports where the user is looking so the thumbnail loader serves that area first.
    // A header item (error/empty text) shifts the capture indices by one.
    val headerItems = if (browser.failed || captures.isEmpty()) 1 else 0
    LaunchedEffect(gridState, headerItems, browser.browserGeneration) {
        snapshotFlow { gridState.firstVisibleItemIndex }.collect { first ->
            controller.focusThumbnails(identifier, (first - headerItems).coerceAtLeast(0))
        }
    }
    // JPG-only: the batch is the selection's JPGs; RAW-only/saved/oversize captures are skipped.
    // The select/download actions live in the top bar (WifiPhotoBrowserTopBar); the plan is
    // shared through the ViewModel so both show the same numbers.
    val jpegPlan = viewModel.jpegBatchPlan(policy, state)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (selecting && jpegPlan != null && jpegPlan.skippedCount > 0) Text(
            stringResource(
                Res.string.wifi_remote_batch_skip_hint,
                jpegPlan.skippedCount, skipReasonsText(jpegPlan.skipped)
            ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        val summary = viewModel.batchSummary
        // Completion is latched in the ViewModel (noteBatchCompletion), so a preview
        // round-trip — which clears the transfer result — keeps the finished batch line.
        // A busy/failed/cancelled transfer always shows its own status instead.
        if (summary != null && shouldShowBatchSummary(viewModel.batchSummarySettled(), transfer)) {
            Text(
                if (summary.skippedCount == 0) stringResource(
                    Res.string.wifi_remote_batch_summary_no_skip, summary.downloaded
                ) else stringResource(
                    Res.string.wifi_remote_batch_summary, summary.downloaded,
                    summary.skippedCount, skipReasonsText(summary.skipped)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        } else PhotoTransferStatus(transfer)
        if (busy) TextButton(onClick = { controller.cancelPhotoOperation(identifier) }) {
            Text(stringResource(Res.string.wifi_remote_transfer_cancel))
        }
        // One progress indicator for everything that is loading: a catalog page or the
        // thumbnails. An active transfer takes precedence — the thumbnail loader is
        // held during it anyway, so its "loading" is a paused wait, not progress.
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (browserProgressVisible(browser.loading, thumbnailsLoading, transfer.busy)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        LazyVerticalGrid(
            state = gridState,
            // As many columns as fit the width (4 on a 360 dp phone, more on wider screens).
            columns = GridCells.Adaptive(minSize = TileMinSize), modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (headerItems > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (browser.failed) Text(
                            stringResource(Res.string.wifi_remote_browser_failed),
                            color = MaterialTheme.colorScheme.error
                        )
                        if (!browser.loading && captures.isEmpty() && !browser.failed) {
                            Text(stringResource(Res.string.wifi_remote_browser_empty))
                        }
                    }
                }
            }
            items(captures, key = { it.id }) { capture ->
                val isSelected = capture.id in selectedIds
                val selectLabel = stringResource(Res.string.wifi_remote_selection_start)
                val tileShape = RoundedCornerShape(4.dp)
                val badge = capture.formatBadge(policy, state.canConvertHeif)
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(tileShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    // The clickable layer is a sibling of the preview button below, so the
                    // magnifier is its own accessibility node and never toggles selection.
                    Box(
                        Modifier.fillMaxSize().combinedClickable(
                            enabled = !selecting || !busy,
                            role = if (selecting) Role.Checkbox else Role.Button,
                            onClick = {
                                if (selecting) viewModel.togglePhotoSelection(capture.id)
                                else viewModel.openPhoto(capture.id)
                            },
                            onLongClickLabel = selectLabel,
                            onLongClick = {
                                if (!busy) {
                                    viewModel.togglePhotoSelection(capture.id)
                                }
                            },
                        ).semantics {
                            contentDescription = capture.name
                            if (selecting) selected = isSelected
                        },
                    ) {
                        PhotoTile(
                            thumbnails[capture.thumbnail.handle],
                            tileSpinnerVisible(browser.loading, thumbnailsLoading, transfer.busy)
                        )
                        if (isSelected) {
                            Box(
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                    .border(3.dp, MaterialTheme.colorScheme.primary, tileShape)
                            )
                        }
                        val badgeText = when (badge) {
                            WifiFormatBadge.None -> null
                            is WifiFormatBadge.Label -> badge.text
                            WifiFormatBadge.RawOnly -> stringResource(Res.string.wifi_remote_format_raw_only)
                        }
                        if (badgeText != null) Surface(
                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                            color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                badgeText,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    // Checkbox (bottom-right) and preview (top-left) are always there, selecting
                    // or not. Both are siblings of the clickable layer, so each is its own
                    // accessibility node and never double-fires the tile tap. A load/transfer in
                    // flight disables them (the ViewModel refuses too; this keeps feedback honest).
                    val toggleLabel = stringResource(
                        if (isSelected) Res.string.wifi_remote_viewer_deselect
                        else Res.string.wifi_remote_viewer_select
                    )
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(TileActionSize).clickable(
                            enabled = !busy,
                            role = Role.Checkbox,
                            onClick = { viewModel.togglePhotoSelection(capture.id) },
                        ).semantics {
                            contentDescription = toggleLabel
                            selected = isSelected
                        },
                        contentAlignment = Alignment.Center
                    ) { SelectionCheck(isSelected) }
                    val previewLabel = stringResource(Res.string.wifi_remote_preview_photo)
                    Box(
                        Modifier.align(Alignment.TopStart).size(TileActionSize).clickable(
                            enabled = !busy,
                            role = Role.Button,
                            onClick = { viewModel.previewPhoto(capture.id) },
                        ).semantics { contentDescription = previewLabel },
                        contentAlignment = Alignment.Center
                    ) {
                        // Square, with a smaller fill than the touch target around it.
                        Box(
                            Modifier.size(24.dp)
                                .background(Color.Black.copy(alpha = 0.55f), SquareBadgeShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(Res.drawable.search_24px), contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
        WifiPhotoPagerBar(
            browser = browser, enabled = !busy,
            onPage = { offset -> controller.browsePhotos(identifier, offset) },
        )
    }
    if (viewModel.showBatchOptions && selecting && policy == WifiFormatPolicy.Original) {
        PhotoBatchDownloadSheet(
            // The batch receives the whole cross-page selection snapshot, in selection order.
            viewModel.selectedPhotos.values.toList(), state,
            onDismiss = viewModel::dismissBatchOptions,
            onDownload = { downloads ->
                viewModel.dismissBatchOptions()
                // An Original batch is a NEW download: drop any latched JpegOnly summary
                // first so it cannot re-show or settle off this batch's own result.
                viewModel.downloadOriginalBatch(downloads, onDownload)
            },
        )
    }
}

@Composable
private fun skipReasonsText(skipped: Map<WifiSkipReason, Int>): String =
    skipped.entries.filter { it.value > 0 }.map { (reason, count) ->
        stringResource(
            when (reason) {
                WifiSkipReason.NoJpeg -> Res.string.wifi_remote_skip_no_jpg
                WifiSkipReason.AlreadySaved -> Res.string.wifi_remote_skip_saved
                WifiSkipReason.TooLarge -> Res.string.wifi_remote_skip_too_large
            }, count
        )
    }.joinToString(" · ")

/** Minimum tile width: the grid fits as many columns as the screen width allows. */
private val TileMinSize = 80.dp

/** Touch target of the checkbox / preview overlays on a grid tile. */
private val TileActionSize = 40.dp

/** Small corner radius: the overlays read as squares, not circles. */
private val SquareBadgeShape = RoundedCornerShape(3.dp)

/** Square check box at a tile corner: hollow when unselected, filled with a tick when selected. */
@Composable
internal fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).clip(SquareBadgeShape).then(
            if (selected) Modifier.background(MaterialTheme.colorScheme.primary)
            else Modifier.background(Color.Black.copy(alpha = 0.25f)).border(2.dp, Color.White, SquareBadgeShape)
        ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(
            painterResource(Res.drawable.check_24px), contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Grid thumbnail: scaled to fit the square tile so the whole picture is visible (never cropped);
 * the tile background shows around the shorter side. Bitmaps are already upright.
 */
@Composable
private fun PhotoTile(image: ImageBitmap?, loading: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(Res.string.wifi_remote_preview_unavailable),
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp))
        }
    }
}

/**
 * JPG-only download actions: one tap downloads, progress/result show in the page's own
 * transfer status. Without a JPG the page says so and offers the remaining original by hand.
 */
@Composable
internal fun PhotoJpegOnlyActions(
    plan: WifiDetailPlan, state: WifiRemoteState,
    onDownload: (Long, WifiPhotoDownloadFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val browser = state.photoBrowser
    val idle = !browser.loading && !state.imageTransfer.busy
    when (plan) {
        WifiDetailPlan.ChooseFormat -> Unit
        is WifiDetailPlan.DownloadJpeg -> {
            val target = plan.target
            val saved = target.download in browser.savedDownloads
            Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { onDownload(target.download.handle, target.download.format) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = idle && target.file.downloadable && !saved
                ) {
                    val size = target.knownSize
                    Text(
                        if (size != null) stringResource(
                            Res.string.wifi_remote_download_format_size, "JPG", formatByteSize(size)
                        ) else stringResource(Res.string.wifi_remote_download_format, "JPG")
                    )
                }
                if (saved) Text(stringResource(Res.string.wifi_remote_download_saved))
                if (!target.file.downloadable) Text(stringResource(Res.string.wifi_remote_download_too_large))
            }
        }

        is WifiDetailPlan.NoJpeg -> Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.wifi_remote_photo_no_jpg))
            plan.fallback?.let { file ->
                val saved = WifiPhotoDownload(file.handle) in browser.savedDownloads
                Button(
                    onClick = { onDownload(file.handle, WifiPhotoDownloadFormat.Original) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = idle && file.downloadable && !saved
                ) {
                    Text(
                        stringResource(
                            Res.string.wifi_remote_download_format_size, file.formatLabel, formatByteSize(file.size)
                        )
                    )
                }
                if (saved) Text(stringResource(Res.string.wifi_remote_download_saved))
                if (!file.downloadable) Text(stringResource(Res.string.wifi_remote_download_too_large))
            }
        }
    }
}

@Composable
internal fun PhotoTransferStatus(transfer: WifiImageTransferState) {
    if (transfer.totalFiles > 1) {
        Text(
            stringResource(
                Res.string.wifi_remote_batch_progress,
                transfer.completedFiles,
                transfer.totalFiles
            )
        )
    }
    when (transfer.status) {
        WifiImageTransferStatus.Downloading -> {
            Text(stringResource(Res.string.wifi_remote_transfer_progress, transfer.filename))
            LinearProgressIndicator(progress = {
                if (transfer.totalBytes > 0) transfer.bytesReceived.toFloat() / transfer.totalBytes else 0f
            }, modifier = Modifier.fillMaxWidth())
        }
        WifiImageTransferStatus.Converting -> {
            Text(stringResource(Res.string.wifi_remote_converting_jpeg))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        WifiImageTransferStatus.ConversionFailed -> Text(stringResource(Res.string.wifi_remote_conversion_failed))
        WifiImageTransferStatus.Saved -> Text(stringResource(Res.string.wifi_remote_transfer_saved, transfer.filename))
        WifiImageTransferStatus.Failed -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(
                    when (transfer.failureReason) {
                        WifiTransferFailureReason.CatalogChanged -> Res.string.wifi_remote_transfer_failed_catalog_changed
                        WifiTransferFailureReason.Timeout -> Res.string.wifi_remote_transfer_failed_timeout
                        WifiTransferFailureReason.Storage -> Res.string.wifi_remote_transfer_failed_storage
                        WifiTransferFailureReason.Protocol -> Res.string.wifi_remote_transfer_failed_protocol
                        null, WifiTransferFailureReason.Unknown -> Res.string.wifi_remote_transfer_failed
                    }
                )
            )
            // Already-saved files are session-memory only: re-downloading after a reconnect
            // duplicates them, so connection-related failures warn about that explicitly.
            if (transfer.failureReason == WifiTransferFailureReason.Timeout ||
                transfer.failureReason == WifiTransferFailureReason.Protocol
            ) {
                Text(
                    stringResource(Res.string.wifi_remote_transfer_failed_redownload_copy),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        WifiImageTransferStatus.Cancelled -> Text(stringResource(Res.string.wifi_remote_transfer_cancelled))
        WifiImageTransferStatus.PermissionDenied -> Text(stringResource(Res.string.wifi_remote_storage_permission))
        WifiImageTransferStatus.Idle -> Unit
    }
}

/**
 * The top progress bar covers catalog pages and the thumbnail loader, but an
 * active transfer takes precedence — during it the loader is held and its
 * "loading" flag is a paused wait, not progress.
 */
internal fun browserProgressVisible(
    browserLoading: Boolean,
    thumbnailsLoading: Boolean,
    transferBusy: Boolean,
): Boolean = !transferBusy && (browserLoading || thumbnailsLoading)

/**
 * A tile shows the in-progress spinner for catalog/thumbnail loading only; while
 * a transfer holds the loader, pending tiles sit silent instead.
 */
internal fun tileSpinnerVisible(
    browserLoading: Boolean,
    thumbnailsLoading: Boolean,
    transferBusy: Boolean,
): Boolean = browserLoading || (thumbnailsLoading && !transferBusy)
