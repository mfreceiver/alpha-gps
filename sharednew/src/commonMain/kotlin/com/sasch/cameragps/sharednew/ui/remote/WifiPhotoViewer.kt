package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cameragps.sharednew.generated.resources.b1_details_close
import cameragps.sharednew.generated.resources.b1_details_open
import cameragps.sharednew.generated.resources.b1_details_title
import cameragps.sharednew.generated.resources.b1_detail_captured
import cameragps.sharednew.generated.resources.b1_detail_files
import cameragps.sharednew.generated.resources.b1_detail_format
import cameragps.sharednew.generated.resources.b1_detail_resolution
import cameragps.sharednew.generated.resources.b1_exif_aperture
import cameragps.sharednew.generated.resources.b1_exif_bias
import cameragps.sharednew.generated.resources.b1_exif_camera
import cameragps.sharednew.generated.resources.b1_exif_focal
import cameragps.sharednew.generated.resources.b1_exif_iso
import cameragps.sharednew.generated.resources.b1_exif_shutter
import cameragps.sharednew.generated.resources.d1_exif_focal_35mm
import cameragps.sharednew.generated.resources.chevron_right_24px
import cameragps.sharednew.generated.resources.close_24px
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.arrow_back_24px
import cameragps.sharednew.generated.resources.check_24px
import cameragps.sharednew.generated.resources.download_24px
import cameragps.sharednew.generated.resources.m7_viewer_download_format
import cameragps.sharednew.generated.resources.m7_viewer_downloading
import cameragps.sharednew.generated.resources.m7_viewer_file_size
import cameragps.sharednew.generated.resources.m7_viewer_filmstrip_item
import cameragps.sharednew.generated.resources.m7_viewer_histogram
import cameragps.sharednew.generated.resources.m7_viewer_lens
import cameragps.sharednew.generated.resources.m7_viewer_metadata_loading
import cameragps.sharednew.generated.resources.m7_viewer_only_format
import cameragps.sharednew.generated.resources.m7_viewer_other_transfer
import cameragps.sharednew.generated.resources.m7_viewer_selected_count
import cameragps.sharednew.generated.resources.m7_viewer_shooting_params
import cameragps.sharednew.generated.resources.wifi_remote_batch_progress
import cameragps.sharednew.generated.resources.wifi_remote_browser_empty
import cameragps.sharednew.generated.resources.wifi_remote_browser_failed
import cameragps.sharednew.generated.resources.wifi_remote_browser_loading
import cameragps.sharednew.generated.resources.wifi_remote_browser_title
import cameragps.sharednew.generated.resources.wifi_remote_conversion_failed
import cameragps.sharednew.generated.resources.wifi_remote_converting_jpeg
import cameragps.sharednew.generated.resources.wifi_remote_download
import cameragps.sharednew.generated.resources.wifi_remote_download_saved
import cameragps.sharednew.generated.resources.wifi_remote_download_too_large
import cameragps.sharednew.generated.resources.wifi_remote_photo_no_jpg
import cameragps.sharednew.generated.resources.wifi_remote_preview_unavailable
import cameragps.sharednew.generated.resources.wifi_remote_storage_permission
import cameragps.sharednew.generated.resources.wifi_remote_transfer_cancel
import cameragps.sharednew.generated.resources.wifi_remote_transfer_cancelled
import cameragps.sharednew.generated.resources.wifi_remote_transfer_failed
import cameragps.sharednew.generated.resources.wifi_remote_transfer_failed_catalog_changed
import cameragps.sharednew.generated.resources.wifi_remote_transfer_failed_protocol
import cameragps.sharednew.generated.resources.wifi_remote_transfer_failed_storage
import cameragps.sharednew.generated.resources.wifi_remote_transfer_failed_timeout
import cameragps.sharednew.generated.resources.wifi_remote_viewer_back
import cameragps.sharednew.generated.resources.wifi_remote_viewer_deselect
import cameragps.sharednew.generated.resources.wifi_remote_viewer_position
import cameragps.sharednew.generated.resources.wifi_remote_viewer_retry
import cameragps.sharednew.generated.resources.wifi_remote_viewer_select
import cameragps.sharednew.generated.resources.wifi_remote_viewer_transfer_wait
import com.diamondedge.logging.logging
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraCapture
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferState
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoDownload
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.WifiTransferFailureReason
import com.sasch.cameragps.sharednew.remote.wifi.formatByteSize
import com.sasch.cameragps.sharednew.remote.wifi.groupCameraPhotos
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCheckbox
import com.sasch.cameragps.sharednew.ui.components.glass.GlassFloatingBar
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressCircle
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressSize
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSurface
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTag
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTagStyle
import com.sasch.cameragps.sharednew.ui.components.glass.LocalGlassBackdrop
import com.sasch.cameragps.sharednew.ui.components.glass.glassSource
import com.sasch.cameragps.sharednew.ui.components.glass.rememberGlassBackdrop
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import com.sasch.cameragps.sharednew.ui.theme.LocalGlassRenderMode
import com.sasch.cameragps.sharednew.ui.theme.WindowClass
import com.sasch.cameragps.sharednew.ui.theme.currentWindowClass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Width of the details panel of the near-square (B) and landscape (C) layouts (design page 21). */
private val DetailsPanelWidth = 360.dp

/** Info strip width caps: portrait / foldable never stretches it across a wide window. */
private val PortraitStripMaxWidth = 560.dp
private val WideStripMaxWidth = 420.dp

private val StripShape = RoundedCornerShape(20.dp)
private val DrawerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val FilmstripCell = 40.dp
private val FilmstripShape = RoundedCornerShape(6.dp)

/**
 * Full-screen viewer of the open day (design v3 pages 20–21): top bar (name, capture time, k / N),
 * status tags, the photo pager, the single-layer glass info strip (format · the size the default
 * download format fetches), the filmstrip, the single download button and the selection check box.
 * Always dark. Layout follows [currentWindowClass]: A portrait = chrome above / below the photo, the
 * details (all readable metadata + histogram) in a bottom drawer opened by swiping up from the lower
 * photo area or tapping the strip; B near-square and C landscape = photo left and the same details
 * in a panel on the right with the download button.
 */
@Composable
internal fun WifiPhotoViewer(
    state: WifiRemoteState,
    viewModel: WifiRemoteViewModel,
    onDownload: (List<WifiPhotoDownload>) -> Unit,
    defaultDownloadFormatJpeg: Boolean = false,
) {
    val browser = state.photoBrowser
    val transfer = state.imageTransfer
    val policy = WifiFormatPolicy.of(defaultDownloadFormatJpeg)
    val captures = remember(browser.photos) { groupCameraPhotos(browser.photos) }
    val controller = viewModel.controller
    val thumbnails by controller.thumbnails.collectAsState()
    val preview by controller.photoPreview.collectAsState()
    val metadataState by controller.metadata.collectAsState()
    DisposableEffect(viewModel) {
        onDispose {
            controller.clearPhotoPreview(viewModel.identifier)
            // Closing (Back, the back arrow, disconnect) stops any pending EXIF read.
            viewModel.stopViewerMetadata()
        }
    }
    val portrait = currentWindowClass() == WindowClass.Portrait
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    // Basic metadata is free; the extended read only runs while the details are visible (drawer open,
    // or the wide layouts' panel). Re-asked after every settled swipe (the controller cancels the
    // previous read), after a download (which interrupts a pending read) and when the drawer opens or
    // closes — closing re-asks for the basic data only, which cancels a pending extended read.
    val detailsVisible = viewerWantsExtendedMetadata(portrait, drawerOpen)
    LaunchedEffect(viewModel.viewedPhotoIndex, detailsVisible, browser.pageRevision, transfer.busy) {
        if (!transfer.busy) viewModel.showViewerMetadata(extended = detailsVisible)
    }
    CameraGpsTheme(darkTheme = true, glassRenderMode = LocalGlassRenderMode.current) {
        key(browser.offset) {
            val preceding = if (browser.offset > 0) 1 else 0
            val count = captures.size + preceding + if (browser.hasMore) 1 else 0
            val initialPage =
                ((viewModel.viewedPhotoIndex ?: browser.offset) - browser.offset + preceding)
                    .coerceIn(0, maxOf(0, count - 1))
            val pager = rememberPagerState(initialPage = initialPage) { count }
            val filmstrip = rememberLazyListState(
                initialFirstVisibleItemIndex = (initialPage - preceding - 2).coerceAtLeast(0)
            )
            val scope = rememberCoroutineScope()
            val currentIndex = pager.currentPage - preceding
            val current = captures.getOrNull(currentIndex)
            val changingPage = browser.loading && (viewModel.viewedPhotoIndex ?: browser.offset) !in
                    browser.offset until browser.offset + captures.size
            // Swiping and the filmstrip are locked while a download runs (design page 21 ⑥).
            val navigationEnabled = !changingPage && !transfer.busy

            // Thumbnails on demand: the shown capture, the visible filmstrip and a small margin —
            // never the whole day (the grid's loader and LRU cache serve them).
            if (browser.grouped) LaunchedEffect(captures, browser.pageRevision) {
                snapshotFlow {
                    val visible = filmstrip.layoutInfo.visibleItemsInfo
                    (pager.currentPage - preceding) to
                            if (visible.isEmpty()) IntRange.EMPTY else visible.first().index..visible.last().index
                }.distinctUntilChanged().collect { (shown, visible) ->
                    val (now, margin) = filmstripThumbnailDemand(shown, visible, captures.size)
                    controller.requestThumbnails(
                        viewModel.identifier, browser.pageRevision,
                        now.map { captures[it].id }, margin.map { captures[it].id },
                    )
                }
            }
            LaunchedEffect(pager.settledPage, browser.loading, browser.failed, transfer.busy) {
                if (captures.isNotEmpty()) {
                    val settledIndex = pager.settledPage - preceding
                    if (settledIndex in captures.indices || !browser.failed) {
                        viewModel.viewPhotoAt(browser.offset + settledIndex)
                    }
                }
            }
            LaunchedEffect(currentIndex) {
                if (currentIndex in captures.indices) {
                    val visible = filmstrip.layoutInfo.visibleItemsInfo
                    val inView = visible.isNotEmpty() &&
                            currentIndex in (visible.first().index + 1) until visible.last().index
                    if (!inView) filmstrip.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
                }
            }

            val shownPreview = preview?.takeIf { it.handle == current?.preview?.handle }
            val shownImage = shownPreview?.image ?: current?.let { thumbnails[it.thumbnail.handle] }
            var histogram by remember { mutableStateOf<FloatArray?>(null) }
            LaunchedEffect(shownImage, detailsVisible) {
                histogram = null
                val image = shownImage
                if (detailsVisible && image != null) {
                    // Down-sampled and off the main thread: a preview can be several megapixels.
                    histogram = withContext(Dispatchers.Default) { image.luminanceHistogram() }
                }
            }
            val info = current?.let { viewerInfoStrip(it, policy, browser.savedDownloads) }
            val details = current?.let { viewerDetails(it, metadataState) }
            val download = current?.let {
                viewerDownloadState(it, policy, browser.savedDownloads, transfer, browser.loading)
            }
            val selected = current != null && viewModel.isPhotoSelected(current.id)
            val parts = ViewerParts(
                title = current?.name ?: stringResource(Res.string.wifi_remote_browser_title),
                capturedAt = current?.let { viewerCaptureTimeText(it.preview.capturedAt) },
                position = current?.let {
                    stringResource(
                        Res.string.wifi_remote_viewer_position,
                        browser.offset + currentIndex + 1,
                        viewerPositionTotal(browser.grouped, captures.size, browser.totalObjects),
                    )
                },
                selectedCount = viewModel.selectedPhotoIds.size,
                info = info,
                details = details,
                histogram = histogram,
                download = download,
                transfer = transfer,
                selected = selected,
                selectEnabled = current != null && !browser.loading && !transfer.busy,
                onBack = viewModel::closePhoto,
                onToggleSelected = { value -> current?.let { viewModel.setPhotoSelected(it.id, value) } },
                onDownload = { downloads -> viewModel.downloadSingleCapture(downloads, onDownload) },
                onCancel = { controller.cancelPhotoOperation(viewModel.identifier) },
                onOpenDetails = if (portrait) ({ drawerOpen = true }) else null,
            )
            val backdrop = rememberGlassBackdrop()
            // Focus home of the info strip: the details drawer returns focus here when it closes.
            val stripFocus = remember { FocusRequester() }
            val photo: @Composable (Modifier) -> Unit = { modifier ->
                ViewerPager(
                    pager = pager,
                    captures = captures,
                    preceding = preceding,
                    state = state,
                    previewImage = { capture ->
                        preview?.takeIf { it.handle == capture.preview.handle }?.image
                            ?: thumbnails[capture.thumbnail.handle]
                    },
                    previewLoading = { capture ->
                        preview?.takeIf { it.handle == capture.preview.handle }?.loading == true
                    },
                    userScrollEnabled = navigationEnabled,
                    onRetry = { page -> viewModel.viewPhotoAt(browser.offset + page - preceding) },
                    modifier = modifier.glassSource(backdrop),
                )
            }
            val strip: @Composable (Modifier) -> Unit = { modifier ->
                parts.info?.let { ViewerInfoStripBar(it, parts.onOpenDetails, modifier.focusRequester(stripFocus)) }
            }
            val film: @Composable (Modifier) -> Unit = { modifier ->
                ViewerFilmstrip(
                    captures = captures,
                    thumbnails = thumbnails,
                    current = currentIndex,
                    enabled = navigationEnabled,
                    state = filmstrip,
                    onSelect = { index -> scope.launch { pager.animateScrollToPage(index + preceding) } },
                    modifier = modifier,
                )
            }
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                // While the portrait details drawer is up it is modal: the viewer below (photo,
                // download, selection, filmstrip) is hidden from accessibility services, exactly
                // like a dialog behind a GlassBackground overlay host — this screen has none, so the
                // isolation is applied here. The wide layouts' side panel is not modal and never
                // hides the photo.
                val drawerVisible = viewerHidesBackgroundSemantics(portrait, drawerOpen, parts.details != null)
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(
                        Modifier.fillMaxSize().then(
                            if (drawerVisible) Modifier.clearAndSetSemantics { } else Modifier
                        )
                    ) {
                        when (currentWindowClass()) {
                            WindowClass.Portrait -> PortraitViewer(parts, photo, strip, film)
                            WindowClass.NearSquare -> WideViewer(parts, photo, strip, film, landscape = false)
                            WindowClass.Landscape -> WideViewer(parts, photo, strip, film, landscape = true)
                        }
                    }
                    val shownDetails = parts.details
                    AnimatedVisibility(
                        visible = drawerVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        if (shownDetails != null) {
                            ViewerDetailsDrawer(
                                shownDetails, parts.histogram,
                                onClose = {
                                    drawerOpen = false
                                    // Focus returns to the strip that opened the drawer.
                                    requestFocusGuarded("details strip") { stripFocus.requestFocus() }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Everything the three layouts place; built once per composition by [WifiPhotoViewer]. */
private class ViewerParts(
    val title: String,
    val capturedAt: String?,
    val position: String?,
    val selectedCount: Int,
    val info: ViewerInfoStrip?,
    val details: ViewerDetails?,
    val histogram: FloatArray?,
    val download: ViewerDownloadState?,
    val transfer: WifiImageTransferState,
    val selected: Boolean,
    val selectEnabled: Boolean,
    val onBack: () -> Unit,
    val onToggleSelected: (Boolean) -> Unit,
    val onDownload: (List<WifiPhotoDownload>) -> Unit,
    val onCancel: () -> Unit,
    /** Opens the portrait details drawer; null in the wide layouts (their panel is always shown). */
    val onOpenDetails: (() -> Unit)?,
)

/**
 * A — tall portrait: top bar + status tags, photo with the strip over its bottom, filmstrip, actions.
 * An upward swipe that starts in the lower [DETAILS_SWIPE_ZONE_FRACTION] of the photo area (where the
 * strip lies) opens the details drawer; see [detailsSwipeUpToOpen].
 */
@Composable
private fun PortraitViewer(
    parts: ViewerParts,
    photo: @Composable (Modifier) -> Unit,
    strip: @Composable (Modifier) -> Unit,
    film: @Composable (Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        ViewerTopBar(parts)
        ViewerStatusTags(parts, Modifier.padding(start = 16.dp, bottom = 4.dp))
        Box(Modifier.weight(1f).fillMaxWidth().detailsSwipeUpToOpen(parts.onOpenDetails)) {
            photo(Modifier.fillMaxSize())
            strip(
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp)
                    .widthIn(max = PortraitStripMaxWidth).fillMaxWidth(),
            )
        }
        film(Modifier.fillMaxWidth().padding(top = 4.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(48.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ViewerDownloadArea(parts) }
            ViewerSelectionToggle(parts)
        }
    }
}

/**
 * B near-square (photo yields width to a 360 dp details panel; strip bottom-left, filmstrip its own
 * row) and C landscape (top bar over the photo with the tags merged into it; strip and filmstrip
 * share the bottom row). The panel carries the details (same content as the portrait drawer), the
 * download button and the check box.
 */
@Composable
private fun WideViewer(
    parts: ViewerParts,
    photo: @Composable (Modifier) -> Unit,
    strip: @Composable (Modifier) -> Unit,
    film: @Composable (Modifier) -> Unit,
    landscape: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val panelWidth: Dp = minOf(DetailsPanelWidth, maxWidth * 0.45f)
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (landscape) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        photo(Modifier.fillMaxSize())
                        ViewerTopBar(parts, Modifier.align(Alignment.TopStart)) {
                            ViewerStatusTags(parts)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        strip(Modifier.weight(1f).widthIn(max = WideStripMaxWidth))
                        film(Modifier.weight(1f))
                    }
                } else {
                    ViewerTopBar(parts)
                    ViewerStatusTags(parts, Modifier.padding(start = 16.dp, bottom = 4.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        photo(Modifier.fillMaxSize())
                        strip(
                            Modifier.align(Alignment.BottomStart).padding(12.dp)
                                .widthIn(max = WideStripMaxWidth).fillMaxWidth(),
                        )
                    }
                    film(Modifier.fillMaxWidth().padding(vertical = 6.dp))
                }
            }
            ViewerDetailsPanel(parts, Modifier.width(panelWidth).fillMaxHeight().padding(8.dp))
        }
    }
}

@Composable
private fun ViewerTopBar(
    parts: ViewerParts,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassIconButton(
            painterResource(Res.drawable.arrow_back_24px),
            contentDescription = stringResource(Res.string.wifi_remote_viewer_back),
            onClick = parts.onBack,
            // Round GV glass keeps the arrow legible where the bar lies over the photo (layout C).
            container = GlassLevel.Viewer,
        )
        Column(Modifier.weight(1f)) {
            Text(
                parts.title, color = AlphaTheme.colors.onPhoto, style = AlphaType.Heading,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            parts.capturedAt?.let {
                Text(
                    it, color = AlphaTheme.colors.onPhotoSecondary, style = AlphaType.Caption,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
        parts.position?.let { Text(it, color = AlphaTheme.colors.onPhotoSecondary, style = AlphaType.Label) }
    }
}

/** Status tag: the shared selection count. Informational, not clickable. */
@Composable
private fun ViewerStatusTags(parts: ViewerParts, modifier: Modifier = Modifier) {
    if (parts.selectedCount == 0) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (parts.selectedCount > 0) InfoTag(
            stringResource(Res.string.m7_viewer_selected_count, parts.selectedCount), style = InfoTagStyle.OnPhoto,
        )
    }
}

@Composable
private fun ViewerPager(
    pager: PagerState,
    captures: List<WifiCameraCapture>,
    preceding: Int,
    state: WifiRemoteState,
    previewImage: (WifiCameraCapture) -> ImageBitmap?,
    previewLoading: (WifiCameraCapture) -> Boolean,
    userScrollEnabled: Boolean,
    onRetry: (Int) -> Unit,
    modifier: Modifier,
) {
    val browser = state.photoBrowser
    if (captures.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.wifi_remote_browser_empty), color = AlphaTheme.colors.onPhoto, style = AlphaType.Body)
        }
        return
    }
    HorizontalPager(
        state = pager, modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        key = { page -> captures.getOrNull(page - preceding)?.id ?: "boundary:$page" },
    ) { page ->
        val capture = captures.getOrNull(page - preceding)
        val image = capture?.let(previewImage)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                // Page boundaries of the legacy flat browser only; a day is complete.
                capture == null -> Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        browser.failed -> {
                            Text(stringResource(Res.string.wifi_remote_browser_failed), color = AlphaTheme.colors.onPhoto, style = AlphaType.Body)
                            CapsuleButton(
                                text = stringResource(Res.string.wifi_remote_viewer_retry),
                                onClick = { onRetry(page) },
                                role = CapsuleRole.Secondary,
                                enabled = !browser.loading && !state.imageTransfer.busy,
                                glass = true,
                                glassLevel = GlassLevel.Viewer,
                            )
                        }
                        state.imageTransfer.busy ->
                            Text(stringResource(Res.string.wifi_remote_viewer_transfer_wait), color = AlphaTheme.colors.onPhoto, style = AlphaType.Body)
                        else -> {
                            GlassProgressCircle(onPhoto = true, size = GlassProgressSize.Large)
                            Text(stringResource(Res.string.wifi_remote_browser_loading), color = AlphaTheme.colors.onPhoto, style = AlphaType.Body)
                        }
                    }
                }
                // Bitmaps arrive already upright (EXIF orientation applied at decode).
                image != null -> Image(
                    image, contentDescription = capture.name,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                )
                browser.loading || previewLoading(capture) ->
                    GlassProgressCircle(onPhoto = true, size = GlassProgressSize.Large)
                else -> Text(stringResource(Res.string.wifi_remote_preview_unavailable), color = AlphaTheme.colors.onPhoto, style = AlphaType.Body)
            }
        }
    }
}

/**
 * Single-layer glass info strip: "JPG + RAW · 24.6 MB" on one viewer-glass layer, no tag chips. The
 * size is what the default download format fetches (the grid tag's number, with its unit here). With
 * [onOpenDetails] (portrait) the strip is a button opening the details drawer, marked by a chevron.
 */
@Composable
private fun ViewerInfoStripBar(
    info: ViewerInfoStrip,
    onOpenDetails: (() -> Unit)?,
    modifier: Modifier,
) {
    GlassSurface(
        modifier = modifier, level = GlassLevel.Viewer, shape = StripShape,
        onClick = onOpenDetails,
        onClickLabel = onOpenDetails?.let { stringResource(Res.string.b1_details_open) },
        contentPadding = PaddingValues(start = 14.dp, end = if (onOpenDetails != null) 8.dp else 14.dp, top = 8.dp, bottom = 8.dp),
    ) {
        val sizeLabel = stringResource(Res.string.m7_viewer_file_size)
        val size = formatByteSize(info.sizeBytes)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val format = info.formatText
            if (format != null) Text(
                if (info.noJpeg) stringResource(Res.string.m7_viewer_only_format, format) else format,
                color = if (info.noJpeg) AlphaTheme.colors.warningText else AlphaTheme.colors.onPhoto,
                style = AlphaType.Label, maxLines = 1,
            )
            if (format != null && info.sizeBytes > 0) {
                Text("·", color = AlphaTheme.colors.onPhotoMuted, style = AlphaType.Label)
            }
            if (info.sizeBytes > 0) Text(
                size, color = AlphaTheme.colors.onPhotoSecondary, style = AlphaType.Label, maxLines = 1,
                modifier = Modifier.semantics { contentDescription = "$sizeLabel $size" },
            )
            Spacer(Modifier.weight(1f))
            if (onOpenDetails != null) Image(
                painterResource(Res.drawable.chevron_right_24px), contentDescription = null,
                modifier = Modifier.size(20.dp).rotate(-90f),
                colorFilter = ColorFilter.tint(AlphaTheme.colors.onPhotoSecondary),
            )
        }
    }
}

/**
 * Opens the details drawer on an upward swipe that starts in the lower part of the photo area
 * ([inDetailsSwipeZone]) and is clearly vertical ([detailsSwipeOpens]). It observes the pointer without
 * consuming it until the swipe qualifies, so the pager keeps horizontal page swipes (once the pager
 * consumes a drag, this gesture gives up) and taps still reach the strip.
 */
private fun Modifier.detailsSwipeUpToOpen(onOpen: (() -> Unit)?): Modifier {
    if (onOpen == null) return this
    return pointerInput(onOpen) {
        val distance = DETAILS_SWIPE_DISTANCE_DP.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!inDetailsSwipeZone(down.position.y, size.height.toFloat())) return@awaitEachGesture
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || change.isConsumed) break
                val moved = change.position - down.position
                if (detailsSwipeOpens(moved.x, moved.y, distance)) {
                    change.consume()
                    onOpen()
                    break
                }
            }
        }
    }
}

/**
 * Portrait details: a viewer-glass bottom drawer over a scrim. Closes on Back (before the viewer
 * itself), a tap on the scrim, the close button or a downward drag on its handle.
 */
@Composable
private fun ViewerDetailsDrawer(details: ViewerDetails, histogram: FloatArray?, onClose: () -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = onClose,
    )
    val closeLabel = stringResource(Res.string.b1_details_close)
    val title = stringResource(Res.string.b1_details_title)
    // The drawer is its own accessibility pane (like CapsuleDialog) and takes focus on entry.
    val sheetFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusGuarded("details drawer") { sheetFocus.requestFocus() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight * 0.75f
        Box(
            Modifier.fillMaxSize()
                .background(AlphaTheme.colors.scrim)
                .pointerInput(onClose) { detectTapGestures { onClose() } }
        )
        GlassSurface(
            modifier = Modifier.align(Alignment.BottomCenter)
                .widthIn(max = PortraitStripMaxWidth).fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                // Taps on the sheet's plain areas must not fall through to the scrim (which closes).
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics {
                    isTraversalGroup = true
                    paneTitle = title
                }
                .focusRequester(sheetFocus)
                .focusable(),
            level = GlassLevel.Viewer,
            shape = DrawerShape,
            contentPadding = PaddingValues(0.dp),
        ) {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
                Row(
                    Modifier.fillMaxWidth().detailsDragDownToClose(onClose)
                        .padding(start = 20.dp, end = 8.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally).padding(start = 48.dp)
                                .size(width = 36.dp, height = 4.dp)
                                .background(AlphaTheme.colors.onPhotoMuted, AlphaShapes.capsule)
                        )
                        Text(
                            stringResource(Res.string.b1_details_title), color = AlphaTheme.colors.onPhoto,
                            style = AlphaType.Heading,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    GlassIconButton(
                        painterResource(Res.drawable.close_24px),
                        contentDescription = closeLabel,
                        onClick = onClose,
                        container = GlassLevel.Viewer,
                    )
                }
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ViewerDetailsContent(details, histogram)
                }
            }
        }
    }
}

/** Closes the drawer on a downward, clearly vertical drag ([detailsSwipeCloses]) of its handle row. */
private fun Modifier.detailsDragDownToClose(onClose: () -> Unit): Modifier = pointerInput(onClose) {
    val distance = DETAILS_SWIPE_DISTANCE_DP.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed || change.isConsumed) break
            val moved = change.position - down.position
            if (detailsSwipeCloses(moved.x, moved.y, distance)) {
                change.consume()
                onClose()
                break
            }
        }
    }
}

/**
 * The details shared by the portrait drawer and the wide side panel: name and capture time, format
 * and every file with its own size, resolution, the shooting parameters read from EXIF (placeholder
 * bars while the read is pending) and the histogram of the shown preview. What is unknown is left out.
 */
@Composable
private fun ColumnScope.ViewerDetailsContent(details: ViewerDetails, histogram: FloatArray?) {
    details.filename?.let {
        Text(it, color = AlphaTheme.colors.onPhoto, style = AlphaType.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    details.capturedAt?.let { DetailRow(stringResource(Res.string.b1_detail_captured), it) }
    details.formatText?.let { DetailRow(stringResource(Res.string.b1_detail_format), it) }
    if (details.files.isNotEmpty()) PanelBlock(stringResource(Res.string.b1_detail_files)) {
        details.files.forEach { file ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.format, color = AlphaTheme.colors.onPhoto, style = AlphaType.Label, modifier = Modifier.width(44.dp))
                Text(
                    file.filename, color = AlphaTheme.colors.onPhotoSecondary, style = AlphaType.Caption,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (file.bytes > 0) Text(formatByteSize(file.bytes), color = AlphaTheme.colors.onPhoto, style = AlphaType.Caption, maxLines = 1)
            }
        }
    }
    details.resolutionText?.let { DetailRow(stringResource(Res.string.b1_detail_resolution), it) }
    if (details.loading) {
        PanelBlock(stringResource(Res.string.m7_viewer_shooting_params)) {
            val loading = stringResource(Res.string.m7_viewer_metadata_loading)
            Column(
                Modifier.fillMaxWidth().semantics { contentDescription = loading },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PlaceholderBar(Modifier.fillMaxWidth(0.8f))
                PlaceholderBar(Modifier.fillMaxWidth(0.5f))
            }
        }
    } else if (details.exif.isNotEmpty()) {
        PanelBlock(stringResource(Res.string.m7_viewer_shooting_params)) {
            details.exif.forEach { (field, value) -> DetailRow(exifFieldLabel(field), value) }
        }
    }
    histogram?.let {
        PanelBlock(stringResource(Res.string.m7_viewer_histogram)) {
            HistogramGraph(it, Modifier.fillMaxWidth().height(72.dp))
        }
    }
}

@Composable
private fun exifFieldLabel(field: ViewerExifField): String = stringResource(
    when (field) {
        ViewerExifField.Aperture -> Res.string.b1_exif_aperture
        ViewerExifField.Shutter -> Res.string.b1_exif_shutter
        ViewerExifField.Iso -> Res.string.b1_exif_iso
        ViewerExifField.ExposureBias -> Res.string.b1_exif_bias
        ViewerExifField.FocalLength -> Res.string.b1_exif_focal
        ViewerExifField.FocalLength35mm -> Res.string.d1_exif_focal_35mm
        ViewerExifField.Lens -> Res.string.m7_viewer_lens
        ViewerExifField.Camera -> Res.string.b1_exif_camera
    }
)

/** "Label        value" on one line; the label soft, the value white and right-aligned. */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = AlphaTheme.colors.onPhotoSecondary, style = AlphaType.Caption)
        Spacer(Modifier.weight(1f))
        Text(value, color = AlphaTheme.colors.onPhoto, style = AlphaType.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaceholderBar(modifier: Modifier) {
    Box(modifier.height(10.dp).clip(RoundedCornerShape(5.dp)).background(AlphaTheme.colors.onPhoto.copy(alpha = 0.14f)))
}

@Composable
private fun HistogramGraph(bins: FloatArray, modifier: Modifier) {
    val label = stringResource(Res.string.m7_viewer_histogram)
    val bar = AlphaTheme.colors.onPhotoMuted
    Canvas(modifier.semantics { contentDescription = label }) {
        if (bins.isEmpty()) return@Canvas
        val barWidth = size.width / bins.size
        bins.forEachIndexed { index, value ->
            val h = value.coerceIn(0f, 1f) * size.height
            if (h > 0f) drawRect(
                color = bar,
                topLeft = Offset(index * barWidth, size.height - h),
                size = Size(barWidth, h),
            )
        }
    }
}

/** Right-hand panel of the B / C layouts: the details on top, download and check box below. */
@Composable
private fun ViewerDetailsPanel(parts: ViewerParts, modifier: Modifier) {
    GlassSurface(
        modifier = modifier, level = GlassLevel.Viewer, shape = AlphaShapes.card,
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                parts.details?.let { ViewerDetailsContent(it, parts.histogram) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ViewerDownloadArea(parts) }
                ViewerSelectionToggle(parts)
            }
        }
    }
}

@Composable
private fun PanelBlock(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = AlphaTheme.colors.onPhotoSecondary, style = AlphaType.Caption)
        content()
    }
}

/** The one download button and what it needs around it (hint, last result, progress capsule). */
@Composable
private fun ViewerDownloadArea(parts: ViewerParts) {
    val download = parts.download ?: return
    val transfer = parts.transfer
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val hint = when (download.phase) {
            ViewerDownloadPhase.NoJpeg -> stringResource(Res.string.wifi_remote_photo_no_jpg)
            ViewerDownloadPhase.OtherTransfer -> stringResource(Res.string.m7_viewer_other_transfer)
            ViewerDownloadPhase.TooLarge -> stringResource(Res.string.wifi_remote_download_too_large)
            else -> viewerTransferNotice(transfer)
        }
        hint?.let {
            Text(
                it, style = AlphaType.Caption,
                color = if (download.phase == ViewerDownloadPhase.Ready) AlphaTheme.colors.danger else AlphaTheme.colors.onPhotoSecondary,
            )
        }
        when (download.phase) {
            ViewerDownloadPhase.Downloading -> DownloadProgressCapsule(transfer, parts.onCancel)
            // Every viewer button is the Viewer-glass variant (GV fill + edge light, label in the
            // photo-overlay colour) so it reads as frosted glass even where it sits on the black
            // letterbox below the photo, and never as an opaque light pill.
            ViewerDownloadPhase.Saved -> CapsuleButton(
                text = stringResource(Res.string.wifi_remote_download_saved), onClick = {},
                icon = painterResource(Res.drawable.check_24px), enabled = false,
                glass = true, glassLevel = GlassLevel.Viewer,
            )
            ViewerDownloadPhase.NoJpeg -> CapsuleButton(
                text = stringResource(Res.string.m7_viewer_download_format, download.manualFormat.orEmpty()),
                onClick = { parts.onDownload(download.downloads) },
                role = CapsuleRole.Secondary,
                icon = painterResource(Res.drawable.download_24px),
                enabled = download.enabled,
                glass = true, glassLevel = GlassLevel.Viewer,
            )
            else -> CapsuleButton(
                text = stringResource(Res.string.wifi_remote_download),
                onClick = { parts.onDownload(download.downloads) },
                // Frosted Viewer glass tinted with the accent (not an opaque light-blue fill).
                role = CapsuleRole.GlassPrimary,
                icon = painterResource(Res.drawable.download_24px),
                enabled = download.enabled,
                glassLevel = GlassLevel.Viewer,
            )
        }
    }
}

/** Last finished transfer result worth a line (a save shows as the greyed "Saved" button instead). */
@Composable
private fun viewerTransferNotice(transfer: WifiImageTransferState): String? = when (transfer.status) {
    WifiImageTransferStatus.Failed -> stringResource(
        when (transfer.failureReason) {
            WifiTransferFailureReason.CatalogChanged -> Res.string.wifi_remote_transfer_failed_catalog_changed
            WifiTransferFailureReason.Timeout -> Res.string.wifi_remote_transfer_failed_timeout
            WifiTransferFailureReason.Storage -> Res.string.wifi_remote_transfer_failed_storage
            WifiTransferFailureReason.Protocol -> Res.string.wifi_remote_transfer_failed_protocol
            null, WifiTransferFailureReason.Unknown -> Res.string.wifi_remote_transfer_failed
        }
    )
    WifiImageTransferStatus.ConversionFailed -> stringResource(Res.string.wifi_remote_conversion_failed)
    WifiImageTransferStatus.Cancelled -> stringResource(Res.string.wifi_remote_transfer_cancelled)
    WifiImageTransferStatus.PermissionDenied -> stringResource(Res.string.wifi_remote_storage_permission)
    else -> null
}

/** Glass progress capsule: ring + "Downloading 43%" + bytes (and files of a two-file capture) + Cancel. */
@Composable
private fun DownloadProgressCapsule(transfer: WifiImageTransferState, onCancel: () -> Unit) {
    val fraction = viewerTransferFraction(transfer)
    GlassFloatingBar(
        level = GlassLevel.Viewer,
        contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Determinate while bytes arrive, indeterminate while converting / without a size.
        GlassProgressCircle(
            progress = fraction?.takeIf { transfer.status == WifiImageTransferStatus.Downloading },
            onPhoto = true,
            strokeWidth = 3.dp,
        )
        Column {
            Text(
                if (transfer.status == WifiImageTransferStatus.Converting) {
                    stringResource(Res.string.wifi_remote_converting_jpeg)
                } else {
                    stringResource(Res.string.m7_viewer_downloading, "${((fraction ?: 0f) * 100).toInt()}%")
                },
                color = AlphaTheme.colors.onPhoto, style = AlphaType.Label, maxLines = 1,
            )
            val bytes = if (transfer.totalBytes > 0) {
                formatByteSize(transfer.bytesReceived) + " / " + formatByteSize(transfer.totalBytes)
            } else null
            val files = if (transfer.totalFiles > 1) {
                stringResource(Res.string.wifi_remote_batch_progress, transfer.completedFiles, transfer.totalFiles)
            } else null
            listOfNotNull(bytes, files).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString(" · "), color = AlphaTheme.colors.onPhotoSecondary, style = AlphaType.Caption, maxLines = 1)
            }
        }
        CapsuleButton(
            text = stringResource(Res.string.wifi_remote_transfer_cancel),
            onClick = onCancel,
            role = CapsuleRole.Secondary,
            glass = true, glassLevel = GlassLevel.Viewer,
        )
    }
}

/** Square check box (the grid's), always shown; shares the grid's selection set. */
@Composable
private fun ViewerSelectionToggle(parts: ViewerParts) {
    val label = stringResource(
        if (parts.selected) Res.string.wifi_remote_viewer_deselect else Res.string.wifi_remote_viewer_select
    )
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
            .toggleable(
                value = parts.selected, enabled = parts.selectEnabled, role = Role.Checkbox,
                onValueChange = parts.onToggleSelected,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // Visual only: the 48 dp cell above owns the toggle and its semantics.
        GlassCheckbox(parts.selected, onCheckedChange = null, enabled = parts.selectEnabled, onPhoto = true)
    }
}

/** Small translucent thumbnails of the whole day; the current one framed, a tap jumps to it. */
@Composable
private fun ViewerFilmstrip(
    captures: List<WifiCameraCapture>,
    thumbnails: Map<Long, ImageBitmap>,
    current: Int,
    enabled: Boolean,
    state: LazyListState,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    if (captures.size < 2) return
    LazyRow(
        state = state,
        modifier = modifier.height(FilmstripCell + 8.dp).alpha(if (enabled) 1f else 0.5f),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(captures, key = { _, capture -> capture.id }) { index, capture ->
            val isCurrent = index == current
            val label = stringResource(Res.string.m7_viewer_filmstrip_item, index + 1, captures.size)
            Box(
                Modifier.size(FilmstripCell).clip(FilmstripShape)
                    .background(AlphaTheme.colors.thumbnailFrame)
                    .then(
                        if (isCurrent) Modifier.border(
                            2.dp, AlphaTheme.colors.thumbnailBadgeStroke, FilmstripShape
                        ) else Modifier
                    )
                    .clickable(enabled = enabled && !isCurrent, role = Role.Button) { onSelect(index) }
                    .semantics {
                        contentDescription = label
                        selected = isCurrent
                    },
            ) {
                thumbnails[capture.thumbnail.handle]?.let {
                    // Fitted whole like the grid tiles: the strip never crops a photo.
                    FittedThumbnail(it, Modifier.fillMaxSize().alpha(if (isCurrent) 1f else 0.62f))
                }
            }
        }
    }
}

private val focusLog = logging("WifiPhotoViewer")

/**
 * Moves focus for accessibility (drawer entry / return to the strip). Compose throws
 * [IllegalStateException] when the target is not attached yet or already left the composition —
 * the only failure that is expected here (logged, the user just keeps the current focus). Anything
 * else is a real bug and propagates instead of being swallowed; cancellation is always rethrown.
 * Returns whether focus was requested.
 */
internal fun requestFocusGuarded(what: String, request: () -> Unit): Boolean =
    try {
        request()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled // on the JVM a CancellationException *is* an IllegalStateException
    } catch (failure: IllegalStateException) {
        focusLog.w { "focus request ($what) skipped: ${failure::class.simpleName}: ${failure.message}" }
        false
    }
