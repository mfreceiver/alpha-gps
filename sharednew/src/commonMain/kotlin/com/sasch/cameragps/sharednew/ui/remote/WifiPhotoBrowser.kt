package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.wifi_remote_batch_progress
import cameragps.sharednew.generated.resources.check_24px
import cameragps.sharednew.generated.resources.search_24px
import cameragps.sharednew.generated.resources.wifi_remote_browser_empty
import cameragps.sharednew.generated.resources.wifi_remote_browser_failed
import cameragps.sharednew.generated.resources.wifi_remote_browser_next
import cameragps.sharednew.generated.resources.wifi_remote_browser_page
import cameragps.sharednew.generated.resources.wifi_remote_browser_previous
import cameragps.sharednew.generated.resources.wifi_remote_browser_refresh
import cameragps.sharednew.generated.resources.wifi_remote_browser_tap_hint
import cameragps.sharednew.generated.resources.wifi_remote_batch_download_jpg
import cameragps.sharednew.generated.resources.wifi_remote_batch_download_jpg_none
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
import cameragps.sharednew.generated.resources.wifi_remote_download
import cameragps.sharednew.generated.resources.wifi_remote_download_format
import cameragps.sharednew.generated.resources.wifi_remote_download_saved
import cameragps.sharednew.generated.resources.wifi_remote_download_too_large
import cameragps.sharednew.generated.resources.wifi_remote_jpeg_copy
import cameragps.sharednew.generated.resources.wifi_remote_jpeg_copy_hint
import cameragps.sharednew.generated.resources.wifi_remote_photo_captured
import cameragps.sharednew.generated.resources.wifi_remote_photo_close
import cameragps.sharednew.generated.resources.wifi_remote_photo_download_hint
import cameragps.sharednew.generated.resources.wifi_remote_photo_format
import cameragps.sharednew.generated.resources.wifi_remote_photo_size
import cameragps.sharednew.generated.resources.wifi_remote_preview_unavailable
import cameragps.sharednew.generated.resources.wifi_remote_selection_all
import cameragps.sharednew.generated.resources.wifi_remote_selection_clear
import cameragps.sharednew.generated.resources.wifi_remote_selection_count
import cameragps.sharednew.generated.resources.wifi_remote_selection_start
import cameragps.sharednew.generated.resources.wifi_remote_storage_permission
import cameragps.sharednew.generated.resources.wifi_remote_transfer_cancel
import cameragps.sharednew.generated.resources.wifi_remote_transfer_cancelled
import cameragps.sharednew.generated.resources.wifi_remote_transfer_failed
import cameragps.sharednew.generated.resources.wifi_remote_transfer_progress
import cameragps.sharednew.generated.resources.wifi_remote_transfer_saved
import com.sasch.cameragps.sharednew.remote.wifi.SonyImageTransfer
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraCapture
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferState
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoDownload
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoDownloadFormat
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.WifiDetailPlan
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatBadge
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy
import com.sasch.cameragps.sharednew.remote.wifi.WifiSkipReason
import com.sasch.cameragps.sharednew.remote.wifi.defaultSelectedPhotoHandle
import com.sasch.cameragps.sharednew.remote.wifi.detailPlan
import com.sasch.cameragps.sharednew.remote.wifi.formatBadge
import com.sasch.cameragps.sharednew.remote.wifi.formatByteSize
import com.sasch.cameragps.sharednew.remote.wifi.formatLabel
import com.sasch.cameragps.sharednew.remote.wifi.groupCameraPhotos
import com.sasch.cameragps.sharednew.remote.wifi.jpegOnlyBatchPlan
import com.sasch.cameragps.sharednew.remote.wifi.shouldShowBatchSummary
import com.sasch.cameragps.sharednew.remote.wifi.toSummary
import com.sasch.cameragps.sharednew.ui.components.verticalScrollbar
import kotlinx.coroutines.launch
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
    var showBatchOptions by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selecting) {
        if (!selecting) showBatchOptions = false
    }
    // JPG-only: the batch is the selection's JPGs; RAW-only/saved/oversize captures are skipped.
    val jpegPlan = if (policy == WifiFormatPolicy.JpegOnly) {
        jpegOnlyBatchPlan(
            viewModel.selectedPhotos.values.toList(), browser.savedDownloads, state.canConvertHeif
        )
    } else null
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (selecting) {
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(Res.string.wifi_remote_selection_count, selectedIds.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = viewModel::selectAllPhotos,
                    enabled = !busy && captures.any { it.id !in selectedIds }
                ) {
                    Text(stringResource(Res.string.wifi_remote_selection_all))
                }
                TextButton(onClick = viewModel::clearPhotoSelection, enabled = !busy) {
                    Text(stringResource(Res.string.wifi_remote_selection_clear))
                }
            }
            if (jpegPlan != null) {
                Button(
                    onClick = {
                        // Direct download: no format sheet. Remember the skip counts so the
                        // result line can say "downloaded N, skipped M".
                        viewModel.recordBatchSummary(jpegPlan.toSummary())
                        onDownload(jpegPlan.downloads)
                    },
                    enabled = !busy && jpegPlan.downloads.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (jpegPlan.downloads.isEmpty()) stringResource(Res.string.wifi_remote_batch_download_jpg_none)
                        else stringResource(
                            Res.string.wifi_remote_batch_download_jpg,
                            jpegPlan.downloads.size, formatByteSize(jpegPlan.totalBytes)
                        )
                    )
                }
                if (jpegPlan.skippedCount > 0) Text(
                    stringResource(
                        Res.string.wifi_remote_batch_skip_hint,
                        jpegPlan.skippedCount, skipReasonsText(jpegPlan.skipped)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Button(
                    onClick = { showBatchOptions = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.wifi_remote_download))
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(Res.string.wifi_remote_browser_tap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                TextButton(
                    onClick = { controller.browsePhotos(identifier) },
                    enabled = !busy
                ) {
                    Text(stringResource(Res.string.wifi_remote_browser_refresh))
                }
            }
        }
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
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3), modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (browser.loading || browser.failed || captures.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (browser.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
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
                        PhotoTile(thumbnails[capture.thumbnail.handle], browser.loading)
                        if (isSelected) {
                            Box(
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                    .border(3.dp, MaterialTheme.colorScheme.primary, tileShape)
                            )
                        }
                        if (selecting) SelectionCheck(
                            isSelected,
                            Modifier.align(Alignment.TopEnd).padding(6.dp)
                        )
                        val badgeText = when (badge) {
                            WifiFormatBadge.None -> null
                            is WifiFormatBadge.Label -> badge.text
                            WifiFormatBadge.RawOnly -> stringResource(Res.string.wifi_remote_format_raw_only)
                        }
                        if (badgeText != null) Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
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
                    if (selecting) IconButton(
                        onClick = { viewModel.previewPhoto(capture.id) },
                        // Refuse while a load/transfer is in flight: previewPhoto guards too,
                        // but a disabled button also keeps the click feedback honest.
                        enabled = !busy,
                        modifier = Modifier.align(Alignment.BottomStart).padding(2.dp).size(40.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    ) {
                        Icon(
                            painterResource(Res.drawable.search_24px),
                            contentDescription = stringResource(Res.string.wifi_remote_preview_photo),
                            tint = Color.White, modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            controller.browsePhotos(
                                identifier,
                                maxOf(0, browser.offset - SonyImageTransfer.PAGE_SIZE)
                            )
                        },
                        enabled = !busy && browser.offset > 0
                    ) { Text(stringResource(Res.string.wifi_remote_browser_previous)) }
                    if (browser.totalObjects > 0) Text(
                        stringResource(
                            Res.string.wifi_remote_browser_page,
                            browser.offset / SonyImageTransfer.PAGE_SIZE + 1
                        )
                    )
                    OutlinedButton(
                        onClick = {
                            controller.browsePhotos(
                                identifier,
                                browser.offset + SonyImageTransfer.PAGE_SIZE
                            )
                        },
                        enabled = !busy && browser.hasMore
                    ) { Text(stringResource(Res.string.wifi_remote_browser_next)) }
                }
            }
        }
    }
    if (showBatchOptions && selecting && policy == WifiFormatPolicy.Original) {
        PhotoBatchDownloadSheet(
            // The batch receives the whole cross-page selection snapshot, in selection order.
            viewModel.selectedPhotos.values.toList(), state,
            onDismiss = { showBatchOptions = false },
            onDownload = { downloads ->
                showBatchOptions = false
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

/** Round check mark at a tile corner: hollow when unselected, filled with a tick when selected. */
@Composable
internal fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).clip(CircleShape).then(
            if (selected) Modifier.background(MaterialTheme.colorScheme.primary)
            else Modifier.background(Color.Black.copy(alpha = 0.25f)).border(2.dp, Color.White, CircleShape)
        ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(
            painterResource(Res.drawable.check_24px), contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)
        )
    }
}

/** Grid thumbnail: fills the square tile, centre-cropped (bitmaps are already upright). */
@Composable
private fun PhotoTile(image: ImageBitmap?, loading: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(Res.string.wifi_remote_preview_unavailable),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun PhotoPreview(image: ImageBitmap?, loading: Boolean) {
    // Bitmaps arrive already rotated upright; pick the ratio from the effective aspect.
    val ratio = if (image != null && image.height > image.width) 2f / 3f else 3f / 2f
    Box(
        Modifier.fillMaxWidth().aspectRatio(ratio).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(Res.string.wifi_remote_preview_unavailable),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoDetailsSheet(
    capture: WifiCameraCapture, image: ImageBitmap?, state: WifiRemoteState,
    previewLoading: Boolean,
    onDismiss: () -> Unit, onDownload: (Long, WifiPhotoDownloadFormat) -> Unit, onCancel: () -> Unit,
    policy: WifiFormatPolicy = WifiFormatPolicy.Original,
) {
    val browser = state.photoBrowser
    val transfer = state.imageTransfer
    val plan = capture.detailPlan(policy, state.canConvertHeif)
    var selectedHandle by rememberSaveable(capture.id) {
        // Original mode: preselect the first downloadable file so JPG+RAW pairs start with
        // an enabled download button. (JPG-only mode does not use the picker at all.)
        mutableStateOf(defaultSelectedPhotoHandle(capture.files))
    }
    var selectedFormat by rememberSaveable(capture.id) { mutableStateOf(WifiPhotoDownloadFormat.Original) }
    val selected = capture.files.firstOrNull { it.handle == selectedHandle }
    val selectedDownload = selected?.let { WifiPhotoDownload(it.handle, selectedFormat) }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        val scrollState = rememberScrollState()
        // Keep the sheet's hidden anchor at the full window height.
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .nestedScroll(PhotoSheetScrollConnection)
                    .verticalScrollbar(scrollState.scrollIndicatorState)
                    .verticalScroll(scrollState).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(capture.name, style = MaterialTheme.typography.headlineSmall)
                PhotoPreview(image, browser.loading || previewLoading)
                if (capture.preview.capturedAt.isNotBlank()) {
                    Text(
                        stringResource(
                            Res.string.wifi_remote_photo_captured,
                            capture.preview.capturedAt.replace('T', ' ')
                        )
                    )
                }
                if (plan is WifiDetailPlan.ChooseFormat) Text(
                    stringResource(Res.string.wifi_remote_photo_format),
                    style = MaterialTheme.typography.titleMedium
                )
                if (plan is WifiDetailPlan.ChooseFormat) Column(Modifier.selectableGroup()) {
                    capture.files.forEach { file ->
                        val formats = if (state.canConvertHeif && file.mimeType == "image/heif")
                            WifiPhotoDownloadFormat.entries else listOf(WifiPhotoDownloadFormat.Original)
                        formats.forEach { format ->
                            val converted = format == WifiPhotoDownloadFormat.Jpeg
                            val saved =
                                WifiPhotoDownload(file.handle, format) in browser.savedDownloads
                            val isSelected =
                                file.handle == selectedHandle && format == selectedFormat
                            Row(
                                Modifier.fillMaxWidth().selectable(
                                    selected = isSelected,
                                    enabled = !transfer.busy && file.downloadable && !saved,
                                    role = Role.RadioButton,
                                    onClick = {
                                        selectedHandle = file.handle; selectedFormat = format
                                    }
                                ).padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected, onClick = null,
                                    enabled = !transfer.busy && file.downloadable && !saved
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (converted) stringResource(Res.string.wifi_remote_jpeg_copy) else file.formatLabel,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        if (converted) file.filename.substringBeforeLast('.') + ".jpg" else file.filename,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (converted) Text(
                                        stringResource(Res.string.wifi_remote_jpeg_copy_hint),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    else Text(
                                        stringResource(
                                            Res.string.wifi_remote_photo_size,
                                            (file.size + 1023) / 1024
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (saved) Text(stringResource(Res.string.wifi_remote_download_saved))
                                    if (!file.downloadable) Text(stringResource(Res.string.wifi_remote_download_too_large))
                                }
                            }
                        }
                    }
                }
                PhotoTransferStatus(transfer)
                if (transfer.busy) TextButton(onClick = onCancel) {
                    Text(stringResource(Res.string.wifi_remote_transfer_cancel))
                }
                Text(
                    stringResource(Res.string.wifi_remote_photo_download_hint),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (plan is WifiDetailPlan.ChooseFormat) Button(
                    onClick = { selected?.let { onDownload(it.handle, selectedFormat) } },
                    modifier = Modifier.weight(1f),
                    enabled = !browser.loading && !transfer.busy && selected != null && selected.downloadable &&
                            selectedDownload !in browser.savedDownloads
                ) {
                    Text(
                        if (selected == null) stringResource(Res.string.wifi_remote_download)
                        else stringResource(
                            Res.string.wifi_remote_download_format,
                            if (selectedFormat == WifiPhotoDownloadFormat.Jpeg) "JPEG" else selected.formatLabel
                        )
                    )
                } else PhotoJpegOnlyActions(plan, state, onDownload, Modifier.weight(1f))
                TextButton(onClick = {
                    scope.launch {
                        sheetState.hide()
                        if (!sheetState.isVisible) onDismiss()
                    }
                }) {
                    Text(stringResource(Res.string.wifi_remote_photo_close))
                }
            }
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
        WifiImageTransferStatus.Failed -> Text(stringResource(Res.string.wifi_remote_transfer_failed))
        WifiImageTransferStatus.Cancelled -> Text(stringResource(Res.string.wifi_remote_transfer_cancelled))
        WifiImageTransferStatus.PermissionDenied -> Text(stringResource(Res.string.wifi_remote_storage_permission))
        WifiImageTransferStatus.Idle -> Unit
    }
}
