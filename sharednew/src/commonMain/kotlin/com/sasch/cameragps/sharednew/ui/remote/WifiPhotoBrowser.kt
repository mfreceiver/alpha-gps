package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
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
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy
import com.sasch.cameragps.sharednew.remote.wifi.WifiSkipReason
import com.sasch.cameragps.sharednew.remote.wifi.formatBadge
import com.sasch.cameragps.sharednew.remote.wifi.formatByteSize
import com.sasch.cameragps.sharednew.remote.wifi.formatLabel
import com.sasch.cameragps.sharednew.remote.wifi.groupCameraPhotos
import com.sasch.cameragps.sharednew.remote.wifi.shouldShowBatchSummary
import com.sasch.cameragps.sharednew.remote.wifi.thumbnailWindow
import cameragps.sharednew.generated.resources.wifi_remote_back_busy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val datesScreen = browser.grouped && browser.dateKey == null
    // Long-press-and-drag range selection. The pointer handler is keyed on the page, never on the
    // selection, and reads the live page through rememberUpdatedState.
    val latestCaptures by rememberUpdatedState(captures)
    val latestHeader by rememberUpdatedState(headerItems)
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val actionPx = with(density) { TileActionSize.toPx() }
    val drag = remember { RangeDragState() }
    var dragActive by remember { mutableStateOf(false) }
    val gestureEnabled = !busy && !datesScreen
    fun visibleTiles(): List<TileBounds> = gridState.layoutInfo.visibleItemsInfo
        .filter { it.index >= latestHeader }
        .map {
            TileBounds(
                it.index - latestHeader, it.offset.x.toFloat(), it.offset.y.toFloat(),
                it.size.width.toFloat(), it.size.height.toFloat()
            )
        }
    // Moves the range end to the tile under the finger (a gap keeps the previous end). False = the
    // gesture is stale and stops writing.
    fun dragUpdate(): Boolean {
        val session = drag.session ?: return false
        val hit = hitTestTile(visibleTiles(), drag.x, drag.y, 0f)
        if (hit != null) drag.end = hit.captureIndex
        return viewModel.updateRangeSelection(session, drag.end)
    }
    // Resting at the top/bottom edge scrolls the current page slowly; it never turns pages.
    LaunchedEffect(dragActive) {
        if (!dragActive) return@LaunchedEffect
        val edge = with(density) { 56.dp.toPx() }
        val maxStep = with(density) { 8.dp.toPx() }
        while (true) {
            delay(16)
            val step = autoScrollStep(drag.y, gridState.layoutInfo.viewportSize.height.toFloat(), edge, maxStep)
            if (step != 0f) {
                gridState.scrollBy(step)
                dragUpdate()
            }
        }
    }
    val actionsEnabled = !busy && !dragActive
    if (browser.grouped) {
        // Date pages load only what the viewport shows: the visible captures (stable keys, header
        // filtered out) plus a row or two of margin. The report is tied to the page revision, so a
        // layout of an earlier page can never steer the loader of the current one.
        LaunchedEffect(gridState, headerItems, browser.pageRevision, captures, datesScreen) {
            if (datesScreen) return@LaunchedEffect
            snapshotFlow {
                val items = gridState.layoutInfo.visibleItemsInfo.filter { it.index >= headerItems }
                val firstRow = items.minOfOrNull { it.offset.y }
                items.map { it.index - headerItems } to items.count { it.offset.y == firstRow }
            }.distinctUntilChanged().collect { (visible, columns) ->
                val window = thumbnailWindow(visible, columns, captures.size)
                controller.requestThumbnails(
                    identifier, browser.pageRevision,
                    window.visible.map { captures[it].id }, window.margin.map { captures[it].id }
                )
            }
        }
    } else {
        LaunchedEffect(gridState, headerItems, browser.browserGeneration) {
            snapshotFlow { gridState.firstVisibleItemIndex }.collect { first ->
                controller.focusThumbnails(identifier, (first - headerItems).coerceAtLeast(0))
            }
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
        if (viewModel.backBlockedNotice) {
            Text(
                stringResource(Res.string.wifi_remote_back_busy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            LaunchedEffect(Unit) {
                delay(BackNoticeMillis)
                viewModel.dismissBackBlockedNotice()
            }
        }
        if (datesScreen) {
            WifiPhotoDateList(
                browser, enabled = !busy,
                onOpen = viewModel::openDate,
                onRetry = { controller.browseDates(identifier) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            LazyVerticalGrid(
                state = gridState,
                // As many columns as fit the width (4 on a 360 dp phone, more on wider screens).
                columns = GridCells.Adaptive(minSize = TileMinSize),
                modifier = Modifier.weight(1f).then(
                    if (gestureEnabled) Modifier.pointerInput(gestureEnabled, browser.pageRevision) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val hit = hitTestTile(visibleTiles(), down.position.x, down.position.y, actionPx)
                            // The checkbox / magnifier corners stay independent actions.
                            if (hit == null || hit.zone != TileZone.Body) return@awaitEachGesture
                            val pressed = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                            val anchorId = latestCaptures.getOrNull(hit.captureIndex)?.id ?: return@awaitEachGesture
                            val session = viewModel.beginRangeSelection(anchorId) ?: return@awaitEachGesture
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            drag.start(session, hit.captureIndex, pressed.position.x, pressed.position.y)
                            dragActive = true
                            pressed.consume()
                            try {
                                var following = true
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    // Swallow everything until the finger lifts: the tile click and
                                    // the grid scroll must not react to a drag selection.
                                    event.changes.forEach { it.consume() }
                                    if (!change.pressed) break
                                    if (following) {
                                        drag.x = change.position.x
                                        drag.y = change.position.y
                                        following = dragUpdate()
                                    }
                                }
                            } finally {
                                drag.clear()
                                dragActive = false
                            }
                        }
                    } else Modifier
                ),
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
                    // The long-press label says what the gesture will do to this anchor tile.
                    val toggleLabel = stringResource(
                        if (isSelected) Res.string.wifi_remote_viewer_deselect
                        else Res.string.wifi_remote_viewer_select
                    )
                    val tileShape = RoundedCornerShape(4.dp)
                    val badgeText = capture.formatBadge()
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(tileShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        // The clickable layer is a sibling of the preview button below, so the
                        // magnifier is its own accessibility node and never toggles selection.
                        Box(
                            Modifier.fillMaxSize().clickable(
                                enabled = !selecting || !busy,
                                role = if (selecting) Role.Checkbox else Role.Button,
                                onClick = {
                                    if (selecting) viewModel.togglePhotoSelection(capture.id)
                                    else viewModel.openPhoto(capture.id)
                                },
                            ).semantics {
                                contentDescription = capture.name
                                if (selecting) selected = isSelected
                                // Accessibility long-click only: the pointer long press (and the drag
                                // after it) has exactly one owner, the grid-level gesture, so a press
                                // can never toggle twice.
                                onLongClick(label = toggleLabel) {
                                    if (!busy) viewModel.togglePhotoSelection(capture.id)
                                    true
                                }
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
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(TileActionSize).clickable(
                                enabled = actionsEnabled,
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
                                enabled = actionsEnabled,
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
                browser = browser, enabled = actionsEnabled,
                onPage = { offset ->
                    if (browser.grouped) viewModel.openDatePage(offset)
                    else controller.browsePhotos(identifier, offset)
                },
            )
        }
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

/** How long the "can't go back right now" hint stays visible. */
private const val BackNoticeMillis = 3_000L

/** Minimum tile width: the grid fits as many columns as the screen width allows. */
private val TileMinSize = 80.dp

/** Mutable state of the running drag selection (pointer position, session and range end). */
private class RangeDragState {
    var session: RangeSelectionSession? = null
    var end: Int = 0
    var x: Float = 0f
    var y: Float = 0f

    fun start(session: RangeSelectionSession, end: Int, x: Float, y: Float) {
        this.session = session; this.end = end; this.x = x; this.y = y
    }

    fun clear() {
        session = null
    }
}

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
