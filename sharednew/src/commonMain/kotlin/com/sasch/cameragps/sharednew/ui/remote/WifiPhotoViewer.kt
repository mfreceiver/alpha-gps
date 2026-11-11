package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.animation.Crossfade
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import cameragps.sharednew.generated.resources.m7_viewer_download_busy
import cameragps.sharednew.generated.resources.m7_viewer_download_too_large
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSnackbarDuration
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSnackbarHost
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSnackbarHostState
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import com.sasch.cameragps.sharednew.remote.wifi.PhotoMetadataState
import kotlinx.coroutines.flow.StateFlow
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
import cameragps.sharednew.generated.resources.r_viewer_no_raw
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
import com.sasch.cameragps.sharednew.remote.wifi.WifiDownloadFormatSetting
import com.sasch.cameragps.sharednew.remote.wifi.policy
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
    downloadFormat: WifiDownloadFormatSetting = WifiDownloadFormatSetting.Default,
    /**
     * The portrait details drawer was opened (true — called from the opening tap / swipe, before the
     * drawer's first frame) or closed (false — also when the viewer leaves composition with the
     * drawer open). Diagnostics only (the Android host times the drawer's frames).
     */
    onDetailsVisibleChanged: (Boolean) -> Unit = {},
) {
    val browser = state.photoBrowser
    val transfer = state.imageTransfer
    /** Null = "ask each time": the download button opens the format chooser. */
    val policy = downloadFormat.policy
    val captures = remember(browser.photos) { groupCameraPhotos(browser.photos) }
    val controller = viewModel.controller
    val thumbnails by controller.thumbnails.collectAsState()
    val preview by controller.photoPreview.collectAsState()
    // The drawer's open flag lives in a stable holder that this function never reads: only the
    // background's semantics gate and the drawer layer (ViewerBody) observe it, so opening or
    // closing the drawer does not recompose this scope, the pager or the filmstrip.
    val visibilityCallback by rememberUpdatedState(onDetailsVisibleChanged)
    val drawer = rememberSaveable(saver = ViewerDrawerState.saver { visibilityCallback(it) }) {
        ViewerDrawerState(initiallyOpen = false) { visibilityCallback(it) }
    }
    DisposableEffect(viewModel) {
        onDispose {
            controller.clearPhotoPreview(viewModel.identifier)
            // Closing (Back, the back arrow, disconnect) stops any pending EXIF read.
            viewModel.stopViewerMetadata()
            if (drawer.open) visibilityCallback(false)
        }
    }
    val windowClass = currentWindowClass()
    val portrait = windowClass == WindowClass.Portrait
    CameraGpsTheme(darkTheme = true, glassRenderMode = LocalGlassRenderMode.current) {
        key(browser.offset) {
            viewerCompositionProbe("viewer")
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

            // Metadata of the shown photo, extended (EXIF) included whether or not the details are
            // visible: asked on entry, after every settled swipe and after a download (which
            // interrupts a pending read); the loader's 250 ms debounce makes it "read once the user
            // stays on the photo". A swipe that starts pauses a pending read (the photo is being
            // left). The pager's motion is observed in the effect, not in composition.
            LaunchedEffect(viewModel.viewedPhotoIndex, browser.pageRevision, transfer.busy) {
                snapshotFlow { pager.isScrollInProgress }.distinctUntilChanged().collect { swiping ->
                    when {
                        viewerWantsExtendedMetadata(transfer.busy, swiping) ->
                            viewModel.showViewerMetadata(extended = true)
                        swiping && !transfer.busy -> viewModel.pauseViewerMetadata()
                    }
                }
            }

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
            val info = current?.let { viewerInfoStrip(it, policy, browser.savedDownloads) }
            val download = current?.let {
                viewerDownloadState(it, policy, browser.savedDownloads, transfer, browser.loading)
            }
            val selected = current != null && viewModel.isPhotoSelected(current.id)
            val openDetails: (() -> Unit)? = if (portrait) remember(drawer) { { drawer.show() } } else null
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
                capture = current,
                image = shownImage,
                metadata = controller.metadata,
                download = download,
                transfer = transfer,
                selected = selected,
                selectEnabled = current != null && !browser.loading && !transfer.busy,
                onBack = viewModel::closePhoto,
                onToggleSelected = { value -> current?.let { viewModel.setPhotoSelected(it.id, value) } },
                onDownload = { downloads -> viewModel.downloadSingleCapture(downloads, onDownload) },
                onCancel = { controller.cancelPhotoOperation(viewModel.identifier) },
                onOpenDetails = openDetails,
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
                ViewerBody(windowClass, parts, drawer, stripFocus, photo, strip, film)
            }
        }
    }
}

/**
 * Open flag of the portrait details drawer. A stable holder rather than a plain `Boolean` so the
 * composable that owns it ([WifiPhotoViewer]) never reads it: only the background's semantics gate
 * and the drawer layer do, which keeps the pager and the filmstrip out of the recomposition when the
 * drawer opens or closes. [show] / [hide] report the change to [onChanged] synchronously (diagnostics
 * hook, before the next frame).
 */
@Stable
internal class ViewerDrawerState(initiallyOpen: Boolean, private val onChanged: (Boolean) -> Unit = {}) {
    var open by mutableStateOf(initiallyOpen)
        private set

    fun show() {
        if (open) return
        open = true
        onChanged(true)
    }

    fun hide() {
        if (!open) return
        open = false
        onChanged(false)
    }

    companion object {
        fun saver(onChanged: (Boolean) -> Unit): Saver<ViewerDrawerState, Boolean> =
            Saver(save = { it.open }, restore = { ViewerDrawerState(it, onChanged) })
    }
}

/**
 * Everything the three layouts place; built by [WifiPhotoViewer] when its data changes (not when
 * the drawer opens). Immutable by contract — every field is read-only and never mutated after
 * construction — so Compose may skip the layouts when the same instance is passed again.
 *
 * @property capture the shown capture; its details (metadata from [metadata], histogram of [image])
 *   are derived inside the drawer / side panel, so a metadata result or the histogram only
 *   recomposes the details, not the page.
 */
@Immutable
internal class ViewerParts(
    val title: String,
    val capturedAt: String?,
    val position: String?,
    val selectedCount: Int,
    val info: ViewerInfoStrip?,
    val capture: WifiCameraCapture?,
    val image: ImageBitmap?,
    val metadata: StateFlow<PhotoMetadataState>,
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
 * The viewer's screen below the theme: the layout for [windowClass] and, in portrait, the details
 * drawer over it. Split out of [WifiPhotoViewer] so the drawer is an independent subtree: the drawer
 * flag ([ViewerDrawerState.open]) is read only by [ViewerSemanticsGate] and [ViewerDetailsDrawerLayer];
 * the layouts ([PortraitViewer] / [WideViewer]) receive the same arguments and are skipped.
 */
@Composable
internal fun ViewerBody(
    windowClass: WindowClass,
    parts: ViewerParts,
    drawer: ViewerDrawerState,
    stripFocus: FocusRequester,
    photo: @Composable (Modifier) -> Unit,
    strip: @Composable (Modifier) -> Unit,
    film: @Composable (Modifier) -> Unit,
) {
    val portrait = windowClass == WindowClass.Portrait
    val hasDetails = parts.capture != null
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // While the portrait details drawer is up it is modal: the viewer below (photo, download,
        // selection, filmstrip) is hidden from accessibility services, exactly like a dialog behind
        // a GlassBackground overlay host — this screen has none, so the isolation is applied here.
        // The wide layouts' side panel is not modal and never hides the photo.
        ViewerSemanticsGate(hidden = { viewerHidesBackgroundSemantics(portrait, drawer.open, hasDetails) }) {
            when (windowClass) {
                WindowClass.Portrait -> PortraitViewer(parts, photo, strip, film)
                WindowClass.NearSquare -> WideViewer(parts, photo, strip, film, landscape = false)
                WindowClass.Landscape -> WideViewer(parts, photo, strip, film, landscape = true)
            }
        }
        if (portrait) ViewerDetailsDrawerLayer(drawer, parts, stripFocus)
    }
}

/**
 * Applies `clearAndSetSemantics` to [content] while [hidden] says so. [hidden] is read here, in this
 * small scope, so a change re-runs only this function; [content] is the same lambda and the layouts
 * inside it get unchanged arguments.
 */
@Composable
private fun ViewerSemanticsGate(hidden: () -> Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().then(if (hidden()) Modifier.clearAndSetSemantics { } else Modifier)) {
        content()
    }
}

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
    viewerCompositionProbe("layout")
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
        ViewerActionRow(parts, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
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
    viewerCompositionProbe("layout")
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
    viewerCompositionProbe("pager")
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
        viewerCompositionProbe("page")
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
                if (info.missingFormat) stringResource(Res.string.m7_viewer_only_format, format) else format,
                color = viewerFormatColor(info.missingFormat, AlphaTheme.colors),
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

/** Enter animation of the portrait details drawer (scrim fade + sheet slide), ms. */
const val VIEWER_DETAILS_ENTER_MILLIS = 280
private const val VIEWER_DETAILS_EXIT_MILLIS = 200

/**
 * The portrait details drawer as its own subtree: the only place besides the semantics gate that
 * reads [ViewerDrawerState.open]. The enter / exit animation is one [Animatable] whose value is read
 * only inside `graphicsLayer` blocks (scrim alpha, sheet translationY), so animating redraws two
 * layers and recomposes nothing; the drawer stays composed until the exit animation has finished.
 */
@Composable
private fun ViewerDetailsDrawerLayer(drawer: ViewerDrawerState, parts: ViewerParts, stripFocus: FocusRequester) {
    val capture = parts.capture
    val open = drawer.open && capture != null
    val progress = remember { Animatable(0f) }
    LaunchedEffect(open) {
        progress.animateTo(
            if (open) 1f else 0f,
            if (open) tween(VIEWER_DETAILS_ENTER_MILLIS, easing = FastOutSlowInEasing)
            else tween(VIEWER_DETAILS_EXIT_MILLIS, easing = FastOutLinearInEasing),
        )
    }
    val leaving by remember { derivedStateOf { progress.value > 0f } }
    if (capture == null || !(open || leaving)) return
    val onClose = remember(drawer, stripFocus) {
        {
            drawer.hide()
            // Focus returns to the strip that opened the drawer.
            requestFocusGuarded("details strip") { stripFocus.requestFocus() }
            Unit
        }
    }
    ViewerDetailsDrawer(capture, parts.metadata, parts.image, open, progress = { progress.value }, onClose = onClose)
}

/**
 * Portrait details: an opaque dark sheet ([AlphaColors.viewerSheet]) over a scrim — no blur, so
 * nothing of the viewer shows through and opening it costs no backdrop pass. Closes on Back (before
 * the viewer itself, only while [open]), a tap on the scrim, the close button or a downward drag on
 * its title row. [progress] (0 → 1) drives the scrim alpha and the sheet's slide.
 */
@Composable
private fun ViewerDetailsDrawer(
    capture: WifiCameraCapture,
    metadata: StateFlow<PhotoMetadataState>,
    image: ImageBitmap?,
    open: Boolean,
    progress: () -> Float,
    onClose: () -> Unit,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = open,
        onBackCompleted = onClose,
    )
    val closeLabel = stringResource(Res.string.b1_details_close)
    val title = stringResource(Res.string.b1_details_title)
    // The drawer is its own accessibility pane (like CapsuleDialog) and takes focus on entry.
    val sheetFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusGuarded("details drawer") { sheetFocus.requestFocus() } }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = progress() }
                .background(AlphaTheme.colors.scrim)
                .pointerInput(onClose) { detectTapGestures { onClose() } }
        )
        Box(
            Modifier.align(Alignment.BottomCenter)
                .widthIn(max = PortraitStripMaxWidth).fillMaxWidth()
                .maxHeightFraction(DRAWER_MAX_HEIGHT_FRACTION)
                .graphicsLayer { translationY = (1f - progress()) * size.height }
                .clip(DrawerShape)
                .background(AlphaTheme.colors.viewerSheet)
                // Taps on the sheet's plain areas must not fall through to the scrim (which closes).
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics {
                    isTraversalGroup = true
                    paneTitle = title
                }
                .focusRequester(sheetFocus)
                .focusable(),
        ) {
            // The sheet is opaque: its controls must not sample (blur) the photo behind it.
            CompositionLocalProvider(LocalGlassBackdrop provides null) {
                Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
                    Row(
                        Modifier.fillMaxWidth().detailsDragDownToClose(onClose)
                            .padding(start = 20.dp, end = 12.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title, color = AlphaTheme.colors.onPhoto, style = AlphaType.Heading,
                            modifier = Modifier.weight(1f),
                        )
                        GlassIconButton(
                            painterResource(Res.drawable.close_24px),
                            contentDescription = closeLabel,
                            onClick = onClose,
                            container = GlassLevel.Viewer,
                        )
                    }
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(DetailsSpacing),
                    ) {
                        ViewerDetailsContent(capture, metadata, image)
                    }
                }
            }
        }
    }
}

private const val DRAWER_MAX_HEIGHT_FRACTION = 0.75f

/** Caps the height at [fraction] of the incoming maximum (a cheap layout modifier, no subcomposition). */
private fun Modifier.maxHeightFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val max = if (constraints.hasBoundedHeight) (constraints.maxHeight * fraction).toInt() else constraints.maxHeight
    val placeable = measurable.measure(constraints.copy(minHeight = minOf(constraints.minHeight, max), maxHeight = max))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** Closes the drawer on a downward, clearly vertical drag ([detailsSwipeCloses]) of its title row. */
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

private val DetailsSpacing = 10.dp

/** Row height reserved by a details row and its placeholder, so a value replacing it does not jump. */
private val DetailRowHeight = 24.dp
private val HistogramHeight = 64.dp

/**
 * The details shared by the portrait drawer and the wide side panel, derived here (not in the page)
 * so a metadata result or the histogram recomposes only the details: capture time, resolution,
 * camera, lens, focal length (and the 35 mm equivalent when it differs), aperture / shutter / ISO /
 * exposure bias in a compact two-column grid, then each file's format and size next to the
 * histogram of the shown preview. Unknown values are left out; while the extended read is pending
 * the fields it may still fill show placeholders of the same height.
 */
@Composable
private fun ColumnScope.ViewerDetailsContent(
    capture: WifiCameraCapture,
    metadata: StateFlow<PhotoMetadataState>,
    image: ImageBitmap?,
) {
    val metadataState by metadata.collectAsState()
    val details = remember(capture, metadataState) { viewerDetails(capture, metadataState) }
    val histogram by produceState<FloatArray?>(null, image) {
        value = null
        // Down-sampled and off the main thread: a preview can be several megapixels.
        value = image?.let { withContext(Dispatchers.Default) { it.luminanceHistogram() } }
    }
    val pending = viewerPendingExifFields(details)
    val values = details.exif.toMap()
    details.capturedAt?.let { DetailRow(stringResource(Res.string.b1_detail_captured), it) }
    details.resolutionText?.let { DetailRow(stringResource(Res.string.b1_detail_resolution), it) }
    ViewerExifField.entries.filter { !it.inGrid }.forEach { field ->
        val value = values[field]
        when {
            value != null -> DetailRow(exifFieldLabel(field), value)
            field in pending -> PlaceholderRow(exifFieldLabel(field))
        }
    }
    val grid = ViewerExifField.entries.filter { it.inGrid && (it in values || it in pending) }
    grid.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            pair.forEach { field -> ExifGridCell(exifFieldLabel(field), values[field], Modifier.weight(1f)) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (details.files.isNotEmpty()) {
                Text(stringResource(Res.string.b1_detail_files), color = AlphaTheme.colors.onPhotoSecondary, style = AlphaType.Caption)
                details.files.forEach { file ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(file.format, color = AlphaTheme.colors.onPhoto, style = AlphaType.Label, maxLines = 1)
                        if (file.bytes > 0) Text(formatByteSize(file.bytes), color = AlphaTheme.colors.onPhoto, style = AlphaType.Body, maxLines = 1)
                    }
                }
            }
        }
        // Same two equal columns and 16 dp gap as the exposure grid above: the histogram starts at the
        // left edge of the second grid column ("Exposure compensation") and ends at the details' right edge.
        if (image != null) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.m7_viewer_histogram), color = AlphaTheme.colors.onPhotoSecondary, style = AlphaType.Caption)
            // Fixed height whether or not the bins are computed yet: no jump when they arrive.
            Box(Modifier.fillMaxWidth().height(HistogramHeight)) {
                histogram?.let { HistogramGraph(it, Modifier.fillMaxSize()) }
            }
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
    Row(
        Modifier.fillMaxWidth().heightIn(min = DetailRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = AlphaTheme.colors.onPhotoSecondary, style = AlphaType.Caption)
        Spacer(Modifier.weight(1f))
        Text(value, color = AlphaTheme.colors.onPhoto, style = AlphaType.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A [DetailRow] whose value is still being read: same height, a bar instead of the value. */
@Composable
private fun PlaceholderRow(label: String) {
    Row(
        Modifier.fillMaxWidth().height(DetailRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = AlphaTheme.colors.onPhotoSecondary, style = AlphaType.Caption)
        Spacer(Modifier.weight(1f))
        PlaceholderBar(Modifier.width(96.dp))
    }
}

/** One cell of the exposure grid: label above, value (or its placeholder, same height) below. */
@Composable
private fun ExifGridCell(label: String, value: String?, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = AlphaTheme.colors.onPhotoSecondary, style = AlphaType.Caption, maxLines = 1)
        Box(Modifier.fillMaxWidth().height(DetailRowHeight), contentAlignment = Alignment.CenterStart) {
            if (value != null) Text(value, color = AlphaTheme.colors.onPhoto, style = AlphaType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else PlaceholderBar(Modifier.fillMaxWidth(0.6f))
        }
    }
}

@Composable
private fun PlaceholderBar(modifier: Modifier) {
    val loading = stringResource(Res.string.m7_viewer_metadata_loading)
    Box(
        modifier.height(10.dp).clip(RoundedCornerShape(5.dp))
            .background(AlphaTheme.colors.onPhoto.copy(alpha = 0.14f))
            .semantics { contentDescription = loading }
    )
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
                verticalArrangement = Arrangement.spacedBy(DetailsSpacing),
            ) {
                parts.capture?.let { ViewerDetailsContent(it, parts.metadata, parts.image) }
            }
            ViewerActionRow(parts, Modifier.fillMaxWidth(), spacing = 8.dp, balanced = false)
        }
    }
}

/**
 * The bottom action row of the viewer: [ViewerDownloadArea] and the check box. Its height is fixed
 * ([viewerActionSlotHeight]) and independent of the download phase, so starting / finishing /
 * failing a download never moves the photo or the filmstrip above it. [balanced] (portrait) puts a
 * 48 dp spacer at the start to mirror the check box and keep the download control centred.
 */
@Composable
private fun ViewerActionRow(parts: ViewerParts, modifier: Modifier, spacing: Dp = 0.dp, balanced: Boolean = true) {
    val height = viewerActionSlotHeight(with(LocalDensity.current) { AlphaType.Label.lineHeight.toDp() })
    Row(
        modifier.height(height).testTag(VIEWER_ACTION_ROW_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (balanced) Spacer(Modifier.size(AlphaDimens.minTouchTarget))
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            ViewerDownloadArea(parts, height)
        }
        ViewerSelectionToggle(parts)
    }
}

/**
 * The one download control, swapped **in place** inside a slot of constant height: a single-line
 * capsule in every phase (Download / Saved / Download RAW / disabled with a short reason / the
 * progress capsule). Longer explanations never take a row of their own: a phase's reason ("This
 * photo has no JPG", the full size-limit sentence…) is the control's accessibility state, and the
 * result of a finished transfer (failed / cancelled / no storage access / conversion failed) is an
 * in-page [GlassSnackbarHost] message lifted above this row — it overlays, it never pushes.
 */
@Composable
private fun ViewerDownloadArea(parts: ViewerParts, slotHeight: Dp) {
    val transfer = parts.transfer
    // Transfer results: announced once when they happen while the viewer is up (never for a state
    // the viewer was opened with), cleared as soon as a new transfer starts.
    val snackbar = remember { GlassSnackbarHostState() }
    val notice = viewerTransferNotice(transfer)
    var seenStatus by remember { mutableStateOf(transfer.status) }
    LaunchedEffect(transfer.status, transfer.failureReason) {
        val action = viewerNoticeAction(seenStatus, transfer.status)
        seenStatus = transfer.status
        when (action) {
            ViewerNoticeAction.Clear -> snackbar.clear()
            ViewerNoticeAction.Show -> notice?.let {
                snackbar.clear()
                snackbar.show(
                    it,
                    duration = if (transfer.status == WifiImageTransferStatus.Cancelled) {
                        GlassSnackbarDuration.Short
                    } else GlassSnackbarDuration.Long,
                )
            }
            ViewerNoticeAction.None -> Unit
        }
    }
    // Above the action row (the host already clears the navigation bar + 16 dp), so the Download
    // button stays reachable for a retry while the message is up.
    GlassSnackbarHost(snackbar, Modifier.padding(bottom = slotHeight + 8.dp))

    val download = parts.download ?: return
    // "Ask each time": the button opens the in-page format chooser; the pick is used for this download only.
    var asking by remember(parts.capture?.id) { mutableStateOf(false) }
    if (asking && download.asks) {
        val options = singleChooserOptions(download.choices)
        WifiFormatChooserDialog(
            options = options,
            onConfirm = { policy ->
                asking = false
                download.choices.firstOrNull { it.policy == policy }?.let { parts.onDownload(it.plan.downloads) }
            },
            onDismiss = { asking = false },
        )
    } else if (asking) {
        SideEffect { asking = false }
    }
    // A short cross-fade between controls, keyed by the phase only (progress ticks never re-fade);
    // both sit in the same fixed-size box, so it changes no layout. The outgoing control keeps the
    // last data of its own phase while it fades out.
    val latest = remember { HashMap<ViewerDownloadPhase, Pair<ViewerDownloadState, WifiImageTransferState>>() }
    latest[download.phase] = download to transfer
    Crossfade(
        targetState = download.phase,
        modifier = Modifier.fillMaxSize(),
        animationSpec = tween(VIEWER_SLOT_FADE_MILLIS),
        label = "viewerDownloadSlot",
    ) { phase ->
        val (shown, shownTransfer) = if (phase == download.phase) download to transfer
        else latest[phase] ?: (download to transfer)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ViewerDownloadControl(shown, shownTransfer, parts, onAsk = { asking = true })
        }
    }
}

@Composable
private fun ViewerDownloadControl(
    download: ViewerDownloadState,
    transfer: WifiImageTransferState,
    parts: ViewerParts,
    onAsk: () -> Unit,
) {
    // Every control fills the slot's height, so they all look the same size at any font scale.
    val fill = Modifier.fillMaxHeight()
    val reason = when (download.phase) {
        ViewerDownloadPhase.NoJpeg -> stringResource(Res.string.wifi_remote_photo_no_jpg)
        ViewerDownloadPhase.NoRaw -> stringResource(Res.string.r_viewer_no_raw)
        ViewerDownloadPhase.OtherTransfer -> stringResource(Res.string.m7_viewer_other_transfer)
        ViewerDownloadPhase.TooLarge -> stringResource(Res.string.wifi_remote_download_too_large)
        else -> null
    }
    val described = reason?.let { fill.semantics { stateDescription = it } } ?: fill
    when (download.phase) {
        ViewerDownloadPhase.Downloading -> DownloadProgressCapsule(transfer, parts.onCancel, fill)
        // Every viewer button is the Viewer-glass variant (GV fill + edge light, label in the
        // photo-overlay colour) so it reads as frosted glass even where it sits on the black
        // letterbox below the photo, and never as an opaque light pill.
        ViewerDownloadPhase.Saved -> CapsuleButton(
            text = stringResource(Res.string.wifi_remote_download_saved), onClick = {},
            modifier = fill,
            icon = painterResource(Res.drawable.check_24px), enabled = false,
            glass = true, glassLevel = GlassLevel.Viewer,
        )
        // The info strip already says "Only RAW" in the warning colour; the full reason is the
        // button's state for TalkBack.
        ViewerDownloadPhase.NoJpeg, ViewerDownloadPhase.NoRaw -> CapsuleButton(
            text = stringResource(Res.string.m7_viewer_download_format, download.manualFormat.orEmpty()),
            onClick = { parts.onDownload(download.downloads) },
            modifier = described,
            role = CapsuleRole.Secondary,
            icon = painterResource(Res.drawable.download_24px),
            enabled = download.enabled,
            glass = true, glassLevel = GlassLevel.Viewer,
        )
        // Disabled with the reason as the label itself (short form; the full sentence is the state).
        ViewerDownloadPhase.OtherTransfer, ViewerDownloadPhase.TooLarge -> CapsuleButton(
            text = stringResource(
                if (download.phase == ViewerDownloadPhase.TooLarge) Res.string.m7_viewer_download_too_large
                else Res.string.m7_viewer_download_busy
            ),
            onClick = {},
            modifier = described,
            role = CapsuleRole.GlassPrimary,
            enabled = false,
            glassLevel = GlassLevel.Viewer,
        )
        else -> CapsuleButton(
            text = stringResource(Res.string.wifi_remote_download),
            onClick = { if (download.asks) onAsk() else parts.onDownload(download.downloads) },
            modifier = fill,
            // Frosted Viewer glass tinted with the accent (not an opaque light-blue fill).
            role = CapsuleRole.GlassPrimary,
            icon = painterResource(Res.drawable.download_24px),
            enabled = download.enabled,
            glassLevel = GlassLevel.Viewer,
        )
    }
}

/** Message of a finished transfer worth telling (a save shows as the greyed "Saved" button instead). */
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

/**
 * Glass progress capsule, the same height as the button it replaces: ring + "Downloading 43%" and,
 * when there is room on the line, the bytes (and files of a two-file capture) + a ✕ that cancels.
 */
@Composable
private fun DownloadProgressCapsule(transfer: WifiImageTransferState, onCancel: () -> Unit, modifier: Modifier) {
    val fraction = viewerTransferFraction(transfer)
    GlassSurface(
        modifier = modifier, level = GlassLevel.Viewer, shape = AlphaShapes.capsule,
        contentPadding = PaddingValues(start = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Determinate while bytes arrive, indeterminate while converting / without a size.
            GlassProgressCircle(
                progress = fraction?.takeIf { transfer.status == WifiImageTransferStatus.Downloading },
                onPhoto = true,
                strokeWidth = 3.dp,
            )
            val title = if (transfer.status == WifiImageTransferStatus.Converting) {
                stringResource(Res.string.wifi_remote_converting_jpeg)
            } else {
                stringResource(Res.string.m7_viewer_downloading, "${((fraction ?: 0f) * 100).toInt()}%")
            }
            val bytes = if (transfer.totalBytes > 0) {
                formatByteSize(transfer.bytesReceived) + " / " + formatByteSize(transfer.totalBytes)
            } else null
            val files = if (transfer.totalFiles > 1) {
                stringResource(Res.string.wifi_remote_batch_progress, transfer.completedFiles, transfer.totalFiles)
            } else null
            ProgressLine(
                title = title,
                detail = listOfNotNull(bytes, files).takeIf { it.isNotEmpty() }?.joinToString(" · "),
                modifier = Modifier.weight(1f, fill = false),
            )
            GlassIconButton(
                icon = painterResource(Res.drawable.close_24px),
                contentDescription = stringResource(Res.string.wifi_remote_transfer_cancel),
                onClick = onCancel,
                tint = AlphaTheme.colors.onPhoto,
            )
        }
    }
}

/**
 * One line: [title] (Label, ellipsized only if it alone is too wide) then [detail] (Caption) on the
 * same baseline — the detail is dropped whole rather than cut when the line has no room for it.
 */
@Composable
private fun ProgressLine(title: String, detail: String?, modifier: Modifier) {
    Layout(
        content = {
            Text(
                title, color = AlphaTheme.colors.onPhoto, style = AlphaType.Label,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(detail, color = AlphaTheme.colors.onPhotoSecondary, style = AlphaType.Caption, maxLines = 1)
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = 8.dp.roundToPx()
        val head = measurables[0].measure(loose)
        val room = if (constraints.hasBoundedWidth) constraints.maxWidth - head.width - gap else Int.MAX_VALUE
        val tail = measurables.getOrNull(1)
            ?.takeIf { room > 0 && it.maxIntrinsicWidth(Constraints.Infinity) <= room }
            ?.measure(loose.copy(maxWidth = room.coerceAtLeast(0)))
        val headBase = head[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: head.height
        val tailBase = tail?.let { t -> t[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: t.height } ?: 0
        val ascent = maxOf(headBase, tailBase)
        val height = maxOf(ascent - headBase + head.height, tail?.let { ascent - tailBase + it.height } ?: 0)
        val width = head.width + (tail?.let { gap + it.width } ?: 0)
        layout(width, height) {
            head.place(0, ascent - headBase)
            tail?.place(head.width + gap, ascent - tailBase)
        }
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
    viewerCompositionProbe("filmstrip")
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

/**
 * Test hook: called with a slot name ("viewer" = the page scope, "layout", "pager", "page", "filmstrip") each time that slot composes, so
 * a JVM Compose test can count recompositions of the real viewer. Null (and free) in the app.
 */
internal val LocalViewerCompositionProbe = staticCompositionLocalOf<((String) -> Unit)?> { null }

// Non-restartable, hence never skipped: it runs whenever the calling scope (re)composes.
@Composable
@NonRestartableComposable
private fun viewerCompositionProbe(slot: String) {
    val probe = LocalViewerCompositionProbe.current ?: return
    SideEffect { probe(slot) }
}
