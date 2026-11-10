package com.sasch.cameragps.sharednew.ui.remote

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.wifi_remote_viewer_deselect
import cameragps.sharednew.generated.resources.wifi_remote_viewer_select
import cameragps.sharednew.generated.resources.wifi_remote_batch_progress
import cameragps.sharednew.generated.resources.check_24px
import cameragps.sharednew.generated.resources.close_24px
import cameragps.sharednew.generated.resources.dismiss_button
import cameragps.sharednew.generated.resources.download_24px
import cameragps.sharednew.generated.resources.wifi_remote_batch_download_jpg
import cameragps.sharednew.generated.resources.wifi_remote_batch_download_none
import cameragps.sharednew.generated.resources.wifi_remote_batch_download_size
import cameragps.sharednew.generated.resources.search_24px
import cameragps.sharednew.generated.resources.m2_warning_24px
import cameragps.sharednew.generated.resources.b1_tile_size
import cameragps.sharednew.generated.resources.b1_tile_size_not_downloaded
import cameragps.sharednew.generated.resources.wifi_remote_browser_empty
import cameragps.sharednew.generated.resources.wifi_remote_browser_failed
import cameragps.sharednew.generated.resources.wifi_remote_batch_skip_hint
import cameragps.sharednew.generated.resources.wifi_remote_batch_summary
import cameragps.sharednew.generated.resources.wifi_remote_batch_summary_no_skip
import cameragps.sharednew.generated.resources.wifi_remote_preview_photo
import cameragps.sharednew.generated.resources.wifi_remote_skip_no_jpg
import cameragps.sharednew.generated.resources.wifi_remote_skip_saved
import cameragps.sharednew.generated.resources.wifi_remote_skip_too_large
import cameragps.sharednew.generated.resources.d1_tile_too_large
import cameragps.sharednew.generated.resources.d1_tile_saved
import cameragps.sharednew.generated.resources.wifi_remote_conversion_failed
import cameragps.sharednew.generated.resources.wifi_remote_converting_jpeg
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
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.WifiTransferFailureReason
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy
import com.sasch.cameragps.sharednew.remote.wifi.WifiSkipReason
import com.sasch.cameragps.sharednew.remote.wifi.formatBadge
import com.sasch.cameragps.sharednew.remote.wifi.captureDownloadSize
import com.sasch.cameragps.sharednew.remote.wifi.formatByteSize
import com.sasch.cameragps.sharednew.remote.wifi.groupCameraPhotos
import com.sasch.cameragps.sharednew.remote.wifi.shouldShowBatchSummary
import com.sasch.cameragps.sharednew.remote.wifi.thumbnailWindow
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraCapture
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.glass
import com.sasch.cameragps.sharednew.ui.components.glass.glassContentColor
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleSize
import com.sasch.cameragps.sharednew.ui.components.glass.GlassFloatingBar
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTag
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTagStyle
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
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
    gridOptions: WifiGridOptions = WifiGridOptions(),
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
    val gridSize = gridOptions.gridSize
    val gridState = rememberLazyGridState()
    // A different day (or the legacy flat list) starts at the top; returning from the viewer keeps
    // the scroll position because the day key does not change.
    var gridDay by rememberSaveable { mutableStateOf(browser.dateKey) }
    LaunchedEffect(browser.dateKey) {
        if (gridDay != browser.dateKey) {
            gridState.scrollToItem(0)
            gridDay = browser.dateKey
        }
    }
    // The grid reports where the user is looking so the thumbnail loader serves that area first.
    // A header item (error/empty text) shifts the capture indices by one.
    val headerItems = if (browser.failed || captures.isEmpty()) 1 else 0
    val datesScreen = browser.grouped && browser.dateKey == null
    // Long-press-and-drag range selection. The pointer handler is keyed on the day, never on the
    // selection, and reads the live day through rememberUpdatedState.
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
    // Resting at the top/bottom edge scrolls the day slowly; a range never crosses into another day.
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
        // A day loads only what the viewport shows: the visible captures (stable keys, header
        // filtered out) plus two rows of margin; scrolling replaces the demand, so requests for tiles
        // that scrolled away are dropped before they reach the camera. The report is tied to the
        // page revision, so a layout of an earlier day can never steer the loader of the current one.
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
    // The batch covers the whole cross-day selection snapshot under the settings' download format:
    // JPG-only fetches the JPGs (captures without one are skipped and counted), Original everything.
    val plan = viewModel.batchPlan(policy, state)
    val summary = viewModel.batchSummary
    // Completion is latched in the ViewModel (noteBatchCompletion), so a preview round-trip — which
    // clears the transfer result — keeps the finished batch line. A busy/failed/cancelled transfer
    // always shows its own status instead.
    val showSummary = summary != null && shouldShowBatchSummary(viewModel.batchSummarySettled(), transfer)
    val showTransfer = transfer.busy || transfer.status != WifiImageTransferStatus.Idle
    val bottomBar = !datesScreen && (selecting || showSummary || showTransfer)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // One thin progress line for everything that is loading: the catalog or the thumbnails.
            // An active transfer takes precedence — the thumbnail loader is held during it anyway.
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                if (browserProgressVisible(browser.loading, thumbnailsLoading, transfer.busy)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (viewModel.backBlockedNotice) {
                Text(
                    stringResource(Res.string.wifi_remote_back_busy),
                    style = MaterialTheme.typography.bodySmall,
                    color = AlphaTheme.colors.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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
                val tileShape = RoundedCornerShape(gridSize.cornerDp.dp)
                LazyVerticalGrid(
                    state = gridState,
                    // Small / medium / large: 4 / 3 / 2 columns on a phone, more on wider windows.
                    columns = GridCells.Adaptive(minSize = gridSize.minTileDp.dp),
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
                                    viewModel.endRangeSelection()
                                }
                            }
                        } else Modifier
                    ),
                    contentPadding = PaddingValues(
                        start = GridMargin, end = GridMargin, top = 4.dp,
                        // Room for the floating download bar so the last row stays reachable.
                        bottom = (if (bottomBar) FloatingBarSpace else 8.dp) + navBottom,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (headerItems > 0) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(
                                    if (browser.failed) Res.string.wifi_remote_browser_failed
                                    else Res.string.wifi_remote_browser_empty
                                ),
                                color = if (browser.failed) AlphaTheme.colors.error else AlphaTheme.colors.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp).alpha(if (browser.failed || !browser.loading) 1f else 0f),
                            )
                        }
                    }
                    items(captures, key = { it.id }) { capture ->
                        PhotoGridTile(
                            capture = capture,
                            image = thumbnails[capture.thumbnail.handle],
                            spinner = tileSpinnerVisible(browser.loading, thumbnailsLoading, transfer.busy),
                            isSelected = capture.id in selectedIds,
                            selecting = selecting,
                            busy = busy,
                            actionsEnabled = actionsEnabled,
                            options = gridOptions,
                            policy = policy,
                            saved = browser.savedDownloads,
                            shape = tileShape,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
        if (bottomBar) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, AlphaTheme.colors.scrim.copy(alpha = 0.28f))
                        )
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp + navBottom),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    showTransfer && !showSummary -> GlassFloatingBar(
                        Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                        shape = AlphaShapes.row,
                        contentPadding = PaddingValues(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            PhotoTransferStatus(transfer)
                        }
                        if (transfer.busy) GlassIconButton(
                            painterResource(Res.drawable.close_24px),
                            contentDescription = stringResource(Res.string.wifi_remote_transfer_cancel),
                            onClick = { controller.cancelPhotoOperation(identifier) },
                        ) else if (!selecting) GlassIconButton(
                            painterResource(Res.drawable.close_24px),
                            contentDescription = stringResource(Res.string.dismiss_button),
                            onClick = { controller.clearPhotoTransferResult(identifier) },
                        )
                    }
                    showSummary -> GlassFloatingBar(
                        Modifier.widthIn(max = 480.dp),
                        shape = AlphaShapes.row,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(
                            if (summary.skippedCount == 0) stringResource(
                                Res.string.wifi_remote_batch_summary_no_skip, summary.downloaded
                            ) else stringResource(
                                Res.string.wifi_remote_batch_summary, summary.downloaded,
                                summary.skippedCount, skipReasonsText(summary.skipped)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (selecting && !transfer.busy) {
                    if (plan.skippedCount > 0) Text(
                        stringResource(
                            Res.string.wifi_remote_batch_skip_hint,
                            plan.skippedCount, skipReasonsText(plan.skipped)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = AlphaTheme.colors.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    // A plan with nothing in it stays tappable: the tap records the
                    // "downloaded 0, skipped N" result instead of doing nothing.
                    // The glass primary capsule floats on its own: no extra bar ring around it and no
                    // dark glow (the old Primary-in-GlassFloatingBar read as a thick dark outline).
                    CapsuleButton(
                        text = when {
                            plan.files.isEmpty() -> stringResource(Res.string.wifi_remote_batch_download_none)
                            policy == WifiFormatPolicy.JpegOnly -> stringResource(
                                Res.string.wifi_remote_batch_download_jpg,
                                plan.files.size, formatByteSize(plan.totalBytes)
                            )
                            else -> stringResource(
                                Res.string.wifi_remote_batch_download_size,
                                plan.files.size, formatByteSize(plan.totalBytes)
                            )
                        },
                        onClick = { viewModel.requestBatchDownload(policy, state, onDownload) },
                        icon = painterResource(Res.drawable.download_24px),
                        role = CapsuleRole.GlassPrimary,
                        size = CapsuleSize.Large,
                        enabled = !busy,
                    )
                }
            }
        }
    }
}

/**
 * One tile of the day grid: a fixed square *frame* (near-black fill, the theme's
 * `thumbnailFrame`) with the photo fitted whole and centred inside it — never cropped, and the
 * camera's baked-in letterbox bars are deliberately not trimmed (they blend into the frame).
 * The corner elements are anchored to the frame, not to the photo
 * ([tileCorner]): preview top-left, size tag top-right, format tag bottom-left, check box bottom-right,
 * each [TILE_CORNER_INSET_DP] from the edges. The preview never changes the selection.
 */
@Composable
private fun PhotoGridTile(
    capture: WifiCameraCapture,
    image: ImageBitmap?,
    spinner: Boolean,
    isSelected: Boolean,
    selecting: Boolean,
    busy: Boolean,
    actionsEnabled: Boolean,
    options: WifiGridOptions,
    policy: WifiFormatPolicy,
    saved: Set<WifiPhotoDownload>,
    shape: RoundedCornerShape,
    viewModel: WifiRemoteViewModel,
) {
    // The long-press label says what the gesture will do to this anchor tile.
    val toggleLabel = stringResource(
        if (isSelected) Res.string.wifi_remote_viewer_deselect
        else Res.string.wifi_remote_viewer_select
    )
    val inset = TILE_CORNER_INSET_DP.dp
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
            .background(AlphaTheme.colors.thumbnailFrame)
    ) {
        // The clickable layer is a sibling of the preview button below, so the magnifier is its own
        // accessibility node and never toggles selection.
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
                // Accessibility long-click only: the pointer long press (and the drag after it) has
                // exactly one owner, the grid-level gesture, so a press can never toggle twice.
                onLongClick(label = toggleLabel) {
                    if (!busy) viewModel.togglePhotoSelection(capture.id)
                    true
                }
            },
        ) {
            PhotoTile(image, spinner)
            if (isSelected) {
                Box(
                    Modifier.fillMaxSize()
                        .background(AlphaTheme.colors.primary.copy(alpha = 0.22f))
                        .border(3.dp, AlphaTheme.colors.primary, shape)
                )
            }
            if (options.showSize) {
                // The same saved-aware plan as the floating button: the tag shows what a download
                // will fetch; a capture whose files are all saved shows its saved state instead of a
                // size (nothing more will be downloaded); the tags' byte counts match the button's plan.
                val size = captureDownloadSize(capture, policy, saved)
                when (size.reason) {
                    WifiSkipReason.AlreadySaved -> InfoTag(
                        stringResource(Res.string.d1_tile_saved),
                        modifier = Modifier.align(tileCorner(TileElement.SizeTag).alignment).padding(inset),
                        style = InfoTagStyle.OnPhoto,
                        icon = painterResource(Res.drawable.check_24px),
                    )
                    WifiSkipReason.TooLarge -> InfoTag(
                        stringResource(Res.string.d1_tile_too_large),
                        modifier = Modifier.align(tileCorner(TileElement.SizeTag).alignment).padding(inset),
                        style = InfoTagStyle.OnPhoto,
                        icon = painterResource(Res.drawable.m2_warning_24px),
                    )
                    else -> formatSizeTag(size.bytes)?.let { text ->
                        val description = stringResource(
                            if (size.reason == WifiSkipReason.NoJpeg) {
                                Res.string.b1_tile_size_not_downloaded
                            } else Res.string.b1_tile_size,
                            text
                        )
                        InfoTag(
                            text,
                            modifier = Modifier.align(tileCorner(TileElement.SizeTag).alignment).padding(inset)
                                .semantics { contentDescription = description },
                            style = InfoTagStyle.OnPhoto,
                            // Not part of a JPG-only download (RAW-only): marked, never implied.
                            icon = if (size.reason == WifiSkipReason.NoJpeg) {
                                painterResource(Res.drawable.m2_warning_24px)
                            } else null,
                        )
                    }
                }
            }
            if (options.showFormat) capture.formatBadge()?.let { format ->
                InfoTag(
                    format,
                    modifier = Modifier.align(tileCorner(TileElement.FormatTag).alignment).padding(inset),
                    style = InfoTagStyle.OnPhoto,
                )
            }
        }
        // Checkbox and preview are always there, selecting or not. Both are siblings of the clickable
        // layer, so each is its own accessibility node and never double-fires the tile tap. A
        // load/transfer in flight disables them (the ViewModel refuses too).
        val checkboxCorner = tileCorner(TileElement.Checkbox).alignment
        Box(
            Modifier.align(checkboxCorner).size(TileActionSize).clickable(
                enabled = actionsEnabled,
                role = Role.Checkbox,
                onClick = { viewModel.togglePhotoSelection(capture.id) },
            ).semantics {
                contentDescription = toggleLabel
                selected = isSelected
            }.padding(inset),
            contentAlignment = checkboxCorner,
        ) { SelectionCheck(isSelected) }
        val previewLabel = stringResource(Res.string.wifi_remote_preview_photo)
        val previewCorner = tileCorner(TileElement.Preview).alignment
        Box(
            Modifier.align(previewCorner).size(TileActionSize).clickable(
                enabled = actionsEnabled,
                role = Role.Button,
                onClick = { viewModel.previewPhoto(capture.id) },
            ).semantics { contentDescription = previewLabel }.padding(inset),
            contentAlignment = previewCorner,
        ) {
            // Small square glass button inside the larger touch target.
            Box(
                Modifier.size(TILE_BADGE_DP.dp).glass(GlassLevel.Float, SquareBadgeShape, shadow = false),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(Res.drawable.search_24px), contentDescription = null,
                    tint = glassContentColor(GlassLevel.Float), modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/** Compose alignment of a frame corner. */
private val TileCorner.alignment: Alignment
    get() = when (this) {
        TileCorner.TopStart -> Alignment.TopStart
        TileCorner.TopEnd -> Alignment.TopEnd
        TileCorner.BottomStart -> Alignment.BottomStart
        TileCorner.BottomEnd -> Alignment.BottomEnd
    }

@Composable
internal fun skipReasonsText(skipped: Map<WifiSkipReason, Int>): String =
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

/** Outer margin of the day grid (design v3: tiles nearly edge to edge). */
private val GridMargin = 6.dp

/** Bottom room kept free under the grid for the floating download bar (56 dp + gaps). */
private val FloatingBarSpace = 96.dp

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
private val TileActionSize = TILE_ACTION_DP.dp

/** Small corner radius: the overlays read as squares, not circles. */
private val SquareBadgeShape = RoundedCornerShape(3.dp)

/** Square check box at a tile corner: hollow when unselected, filled with a tick when selected. */
@Composable
internal fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(TILE_BADGE_DP.dp).clip(SquareBadgeShape).then(
            if (selected) Modifier.background(MaterialTheme.colorScheme.primary)
            else Modifier.background(AlphaTheme.colors.thumbnailBadgeScrim)
                .border(2.dp, AlphaTheme.colors.thumbnailBadgeStroke, SquareBadgeShape)
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
 * Grid thumbnail: the whole picture fitted into the square frame and centred (never cropped). The
 * camera's baked-in letterbox bars are not trimmed; the near-black frame makes them invisible.
 * Bitmaps are already upright.
 */
@Composable
private fun PhotoTile(image: ImageBitmap?, loading: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (image != null) {
            FittedThumbnail(image, Modifier.fillMaxSize())
        } else if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(Res.string.wifi_remote_preview_unavailable),
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp))
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
