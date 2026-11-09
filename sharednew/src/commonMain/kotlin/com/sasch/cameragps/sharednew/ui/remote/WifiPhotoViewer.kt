package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.Image
import com.sasch.cameragps.sharednew.remote.wifi.formatLabel
import com.sasch.cameragps.sharednew.remote.wifi.singleDownloadAction
import com.sasch.cameragps.sharednew.remote.wifi.downloadOptions
import com.sasch.cameragps.sharednew.remote.wifi.WifiSingleDownloadAction
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraCapture
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.wifi_remote_photo_captured
import cameragps.sharednew.generated.resources.wifi_remote_photo_close
import cameragps.sharednew.generated.resources.wifi_remote_photo_format
import cameragps.sharednew.generated.resources.wifi_remote_photo_size
import cameragps.sharednew.generated.resources.wifi_remote_jpeg_copy_hint
import cameragps.sharednew.generated.resources.wifi_remote_jpeg_copy
import cameragps.sharednew.generated.resources.wifi_remote_download_too_large
import cameragps.sharednew.generated.resources.wifi_remote_download_saved
import cameragps.sharednew.generated.resources.wifi_remote_download
import cameragps.sharednew.generated.resources.wifi_remote_viewer_next_photo
import cameragps.sharednew.generated.resources.wifi_remote_viewer_previous_photo
import cameragps.sharednew.generated.resources.chevron_right_24px
import cameragps.sharednew.generated.resources.chevron_left_24px
import cameragps.sharednew.generated.resources.arrow_back_24px
import cameragps.sharednew.generated.resources.wifi_remote_browser_empty
import cameragps.sharednew.generated.resources.wifi_remote_browser_failed
import cameragps.sharednew.generated.resources.wifi_remote_browser_loading
import cameragps.sharednew.generated.resources.wifi_remote_browser_title
import cameragps.sharednew.generated.resources.wifi_remote_preview_unavailable
import cameragps.sharednew.generated.resources.wifi_remote_transfer_cancel
import cameragps.sharednew.generated.resources.wifi_remote_viewer_back
import cameragps.sharednew.generated.resources.wifi_remote_viewer_position
import cameragps.sharednew.generated.resources.wifi_remote_viewer_deselect
import cameragps.sharednew.generated.resources.wifi_remote_viewer_retry
import cameragps.sharednew.generated.resources.wifi_remote_viewer_select
import cameragps.sharednew.generated.resources.wifi_remote_viewer_transfer_wait
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoDownload
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.detailPlan
import com.sasch.cameragps.sharednew.remote.wifi.groupCameraPhotos
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WifiPhotoViewer(
    state: WifiRemoteState,
    viewModel: WifiRemoteViewModel,
    onDownload: (List<WifiPhotoDownload>) -> Unit,
    defaultDownloadFormatJpeg: Boolean = false,
) {
    val browser = state.photoBrowser
    val policy = WifiFormatPolicy.of(defaultDownloadFormatJpeg)
    val captures = remember(browser.photos) { groupCameraPhotos(browser.photos) }
    val thumbnails by viewModel.controller.thumbnails.collectAsState()
    val preview by viewModel.controller.photoPreview.collectAsState()
    DisposableEffect(viewModel) {
        onDispose { viewModel.controller.clearPhotoPreview(viewModel.identifier) }
    }
    key(browser.offset) {
        val preceding = if (browser.offset > 0) 1 else 0
        val count = captures.size + preceding + if (browser.hasMore) 1 else 0
        val initialPage =
            ((viewModel.viewedPhotoIndex ?: browser.offset) - browser.offset + preceding)
                .coerceIn(0, maxOf(0, count - 1))
        val pager = rememberPagerState(initialPage = initialPage) { count }
        val scope = rememberCoroutineScope()
        val current = captures.getOrNull(pager.currentPage - preceding)
        val index = browser.offset + pager.currentPage - preceding
        val changingPage = browser.loading && (viewModel.viewedPhotoIndex ?: browser.offset) !in
                browser.offset until browser.offset + captures.size

        LaunchedEffect(
            pager.settledPage,
            browser.loading,
            browser.failed,
            state.imageTransfer.busy
        ) {
            if (captures.isNotEmpty()) {
                val settledIndex = pager.settledPage - preceding
                if (settledIndex in captures.indices || !browser.failed) {
                    viewModel.viewPhotoAt(browser.offset + settledIndex)
                }
            }
        }
        PhotoViewerTheme {
            Column(
                Modifier.fillMaxSize().background(Color.Black)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = viewModel::closePhoto) {
                        Icon(
                            painterResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.wifi_remote_viewer_back),
                            tint = Color.White
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            current?.name ?: stringResource(Res.string.wifi_remote_browser_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // The capture time that used to sit in the details sheet.
                        val capturedAt = current?.preview?.capturedAt.orEmpty()
                        if (capturedAt.isNotBlank()) Text(
                            stringResource(
                                Res.string.wifi_remote_photo_captured,
                                capturedAt.replace('T', ' ')
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (current != null) Text(
                        stringResource(
                            Res.string.wifi_remote_viewer_position,
                            index + 1, browser.totalObjects
                        ), style = MaterialTheme.typography.labelLarge
                    )
                    if (current != null) {
                        // Select/deselect without leaving the viewer; the grid sees the same
                        // selection set when the viewer closes. Same gating as the grid tiles.
                        val isSelected = current.id in viewModel.selectedPhotoIds
                        val toggleLabel = stringResource(
                            if (isSelected) Res.string.wifi_remote_viewer_deselect
                            else Res.string.wifi_remote_viewer_select
                        )
                        IconButton(
                            onClick = { viewModel.togglePhotoSelection(current.id) },
                            enabled = !browser.loading && !state.imageTransfer.busy,
                            modifier = Modifier.semantics {
                                contentDescription = toggleLabel
                                selected = isSelected
                            },
                        ) {
                            SelectionCheck(isSelected)
                        }
                    }
                }
                if (captures.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.wifi_remote_browser_empty))
                    }
                } else HorizontalPager(
                    state = pager, modifier = Modifier.weight(1f).fillMaxWidth(),
                    userScrollEnabled = !changingPage,
                    key = { page -> captures.getOrNull(page - preceding)?.id ?: "boundary:$page" },
                ) { page ->
                    val capture = captures.getOrNull(page - preceding)
                    val screenPreview = preview?.takeIf { it.handle == capture?.preview?.handle }
                    val image =
                        screenPreview?.image ?: capture?.let { thumbnails[it.thumbnail.handle] }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            capture == null -> Column(
                                Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                when {
                                    browser.failed -> {
                                        Text(stringResource(Res.string.wifi_remote_browser_failed))
                                        TextButton(
                                            onClick = { viewModel.viewPhotoAt(browser.offset + page - preceding) },
                                            enabled = !browser.loading && !state.imageTransfer.busy
                                        ) {
                                            Text(stringResource(Res.string.wifi_remote_viewer_retry))
                                        }
                                    }

                                    state.imageTransfer.busy -> Text(stringResource(Res.string.wifi_remote_viewer_transfer_wait))
                                    else -> {
                                        CircularProgressIndicator(color = Color.White)
                                        Text(stringResource(Res.string.wifi_remote_browser_loading))
                                    }
                                }
                            }

                            image != null -> Image(
                                image, contentDescription = capture.name,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                            )

                            browser.loading || screenPreview?.loading == true -> CircularProgressIndicator(
                                color = Color.White
                            )
                            else -> Text(stringResource(Res.string.wifi_remote_preview_unavailable))
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PhotoTransferStatus(state.imageTransfer)
                    if (state.imageTransfer.busy || browser.loading) {
                        TextButton(onClick = { viewModel.controller.cancelPhotoOperation(viewModel.identifier) }) {
                            Text(stringResource(Res.string.wifi_remote_transfer_cancel))
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        enabled = pager.currentPage > 0 && !changingPage && !pager.isScrollInProgress
                    ) {
                        Icon(
                            painterResource(Res.drawable.chevron_left_24px),
                            contentDescription = stringResource(Res.string.wifi_remote_viewer_previous_photo)
                        )
                    }
                    // The download is right here — no details page in between.
                    if (current != null && policy == WifiFormatPolicy.JpegOnly) {
                        PhotoJpegOnlyActions(
                            current.detailPlan(policy, state.canConvertHeif), state,
                            onDownload = { handle, format ->
                                viewModel.downloadSinglePhoto(WifiPhotoDownload(handle, format), onDownload)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        ViewerDownloadButton(
                            capture = current,
                            state = state,
                            onDownload = { download -> viewModel.downloadSinglePhoto(download, onDownload) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    IconButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        enabled = pager.currentPage < count - 1 && !changingPage && !pager.isScrollInProgress
                    ) {
                        Icon(
                            painterResource(Res.drawable.chevron_right_24px),
                            contentDescription = stringResource(Res.string.wifi_remote_viewer_next_photo)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Original-format single-photo download. One downloadable choice downloads on tap; a pair
 * (JPG + RAW, HEIF + its JPEG copy…) first asks which one — a small picker, not a details page.
 */
@Composable
private fun ViewerDownloadButton(
    capture: WifiCameraCapture?,
    state: WifiRemoteState,
    onDownload: (WifiPhotoDownload) -> Unit,
    modifier: Modifier = Modifier,
) {
    val browser = state.photoBrowser
    var choosing by rememberSaveable { mutableStateOf(false) }
    val options = capture?.downloadOptions(state.canConvertHeif, browser.savedDownloads).orEmpty()
    val action = singleDownloadAction(options)
    val idle = !browser.loading && !state.imageTransfer.busy
    Button(
        onClick = {
            when (action) {
                is WifiSingleDownloadAction.Direct -> onDownload(action.option.download)
                is WifiSingleDownloadAction.Choose -> choosing = true
                WifiSingleDownloadAction.Unavailable,
                WifiSingleDownloadAction.NotDownloadable -> Unit
            }
        },
        enabled = capture != null && idle &&
                (action is WifiSingleDownloadAction.Direct || action is WifiSingleDownloadAction.Choose),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(
                when {
                    capture != null && action == WifiSingleDownloadAction.Unavailable ->
                        Res.string.wifi_remote_download_saved
                    capture != null && action == WifiSingleDownloadAction.NotDownloadable ->
                        Res.string.wifi_remote_download_too_large
                    else -> Res.string.wifi_remote_download
                }
            )
        )
    }
    if (choosing && action is WifiSingleDownloadAction.Choose) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(Res.string.wifi_remote_photo_format)) },
            text = {
                Column {
                    action.options.forEach { option ->
                        Column(
                            Modifier.fillMaxWidth().clickable(
                                enabled = option.enabled, role = Role.Button,
                                onClick = {
                                    choosing = false
                                    onDownload(option.download)
                                },
                            ).padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val alpha = if (option.enabled) 1f else 0.5f
                            Text(
                                if (option.converted) stringResource(Res.string.wifi_remote_jpeg_copy)
                                else option.file.formatLabel,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.alpha(alpha)
                            )
                            Text(
                                if (option.converted) option.file.filename.substringBeforeLast('.') + ".jpg"
                                else option.file.filename,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.alpha(alpha)
                            )
                            when {
                                option.saved -> Text(
                                    stringResource(Res.string.wifi_remote_download_saved),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                !option.file.downloadable -> Text(
                                    stringResource(Res.string.wifi_remote_download_too_large),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                option.converted -> Text(
                                    stringResource(Res.string.wifi_remote_jpeg_copy_hint),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                else -> Text(
                                    stringResource(Res.string.wifi_remote_photo_size, (option.file.size + 1023) / 1024),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosing = false }) {
                    Text(stringResource(Res.string.wifi_remote_photo_close))
                }
            },
        )
    }
}

private val PhotoViewerColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
)

@Composable
private fun PhotoViewerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PhotoViewerColors) {
        CompositionLocalProvider(
            LocalContentColor provides PhotoViewerColors.onSurface,
            content = content
        )
    }
}
