package com.sasch.cameragps.sharednew.remote.wifi

import androidx.compose.ui.graphics.ImageBitmap
import com.diamondedge.logging.logging
import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal interface WifiRemoteConnection {
    val cameraName: String
    val canCapture: Boolean
    val canTransferImages: Boolean get() = false
    val canConvertHeif: Boolean get() = false
    val photoHandlesSurviveModeSwitch: Boolean get() = true
    suspend fun openPhotoBrowser(): CameraPhotoPage = error("Photo browsing unavailable")
    suspend fun photoPage(offset: Int): CameraPhotoPage = error("Photo browsing unavailable")

    /**
     * Enumerates the whole card and returns its days (newest first, unknown last) without showing any
     * page: nothing is addressable until [datePage].
     */
    suspend fun openCatalog(): List<DateGroup> = error("Photo browsing unavailable")

    /** One page of a single day from the enumerated catalog; becomes the displayed page. */
    suspend fun datePage(dateKey: String, offset: Int): CameraPhotoPage =
        error("Photo browsing unavailable")

    suspend fun photoThumbnail(handle: Long, stats: ThumbnailStatsSink? = null): ImageBitmap? = null
    suspend fun photoPreview(handle: Long): ImageBitmap? = null
    suspend fun downloadPhoto(
        handle: Long,
        format: WifiPhotoDownloadFormat,
        onProgress: (WifiImageTransferState) -> Unit
    ): WifiImageTransferState =
        WifiImageTransferState(WifiImageTransferStatus.Failed)

    suspend fun closePhotoBrowser() {}

    /** End transfer mode without reopening a shooting session during teardown. */
    suspend fun prepareToDisconnect() = closePhotoBrowser()

    /**
     * Whether this connection exposes a live-view stream at all. Browse-only sessions
     * never start the live-view HTTP stream; the controller reports the preview as
     * [WifiPreviewStatus.Unavailable] instead of collecting an immediately-complete flow.
     */
    val hasLiveViewStream: Boolean get() = true
    val images: Flow<ImageBitmap>
    val lost: Flow<Unit>
    suspend fun capture(): WifiCaptureStatus
    suspend fun turnOffWifi(): WifiShutdownStatus = WifiShutdownStatus.NotRequested
    suspend fun close()
}

internal fun interface WifiRemoteConnector {
    val supportsAddressDiscovery: Boolean get() = false

    /** A blank host requests address discovery when supported. */
    suspend fun open(host: String, scope: CoroutineScope): WifiRemoteConnection
}

internal fun interface WifiAutomaticConnector {
    suspend fun open(identifier: String, scope: CoroutineScope, onPhase: (WifiRemotePhase) -> Unit): WifiRemoteConnection
}

internal interface WifiManualSetupConnector {
    suspend fun prepare(identifier: String): CameraWifiCredentials?
    suspend fun open(
        credentials: CameraWifiCredentials,
        scope: CoroutineScope
    ): WifiRemoteConnection
}

internal class WifiRemoteConnectException(
    val failure: WifiRemoteFailure,
    cause: Throwable? = null
) :
    Exception("Wi-Fi remote connection failed", cause)

data class WifiPhotoPreview(
    val handle: Long,
    val image: ImageBitmap? = null,
    val loading: Boolean = true
)

/** App-owned, Main.immediate-confined. Images stay separate from per-camera session state. */
class WifiRemoteController internal constructor(
    private val scope: CoroutineScope,
    private val registry: CameraSessionRegistry,
    private val connector: WifiRemoteConnector,
    private val claimControls: suspend (String) -> Unit,
    private val releaseControls: (String) -> Unit,
    private val automaticConnector: WifiAutomaticConnector? = null,
    private val manualSetupConnector: WifiManualSetupConnector? = null,
) {
    private val log = logging()

    /** Wi-Fi photo-browser thumbnail diagnostics (see ThumbnailDiagnostics.kt). */
    private val thumbLog = logging("AlphaThumb")
    private var attempt = 0
    val supportsAutomaticConnection get() = automaticConnector != null
    val supportsManualSetup get() = manualSetupConnector != null
    val supportsAddressDiscovery get() = connector.supportsAddressDiscovery
    val sessions = registry.sessions
    private val _image = MutableStateFlow<ImageBitmap?>(null)
    val image = _image.asStateFlow()
    private val _owner = MutableStateFlow<String?>(null)
    val owner = _owner.asStateFlow()
    private var job: Job? = null
    private val _thumbnails = MutableStateFlow<Map<Long, ImageBitmap>>(emptyMap())
    val thumbnails = _thumbnails.asStateFlow()
    private val _thumbnailsLoading = MutableStateFlow(false)
    /** True while the current page still has thumbnails to fetch (including while paused). */
    val thumbnailsLoading = _thumbnailsLoading.asStateFlow()
    private val _photoPreview = MutableStateFlow<WifiPhotoPreview?>(null)
    val photoPreview = _photoPreview.asStateFlow()
    private var photoPreviewJob: Job? = null
    private var sessionScope: CoroutineScope? = null
    private var activeConnection: WifiRemoteConnection? = null
    private var previewJob: Job? = null
    private var photoJob: Job? = null
    /** Background batched thumbnail loader of the current page (independent of [photoJob]). */
    private var thumbJob: Job? = null
    /** Page-relative capture index the grid is showing; the loader serves it (and what follows) first. */
    private var thumbFocus = 0
    /**
     * While > 0 (a catalog load, download or screen preview is using the camera's single
     * command queue) the thumbnail loader waits between requests instead of competing.
     */
    private val thumbnailHolds = MutableStateFlow(0)
    /** Monotonic catalog generation: every (re)open or refresh starts a new one. */
    private var browserGeneration = 0L
    /** Full handle index of pages that loaded successfully within the current generation. */
    private var photoCatalog = emptyMap<Long, WifiCameraPhoto>()
    private var captureRequests: Channel<Unit>? = null
    private var manualJoin: CompletableDeferred<Unit>? = null

    fun connect(identifier: String, host: String) {
        start(identifier, WifiRemotePhase.OpeningSession) { _, sessionScope ->
            connector.open(host.trim(), sessionScope)
        }
    }

    fun connectAutomatically(identifier: String) {
        val automatic = automaticConnector ?: return
        start(identifier, WifiRemotePhase.PreparingCamera) { id, sessionScope ->
            automatic.open(id, sessionScope) { phase ->
                log.i { "Wi-Fi remote attempt=$attempt phase=$phase" }
                if (registry.get(id)?.wifiRemote?.phase != WifiRemotePhase.Closing) {
                    registry.updateWifiRemote(id, WifiRemoteState(phase = phase))
                }
            }
        }
    }

    fun prepareManualConnection(identifier: String) {
        val setup = manualSetupConnector ?: return
        start(identifier, WifiRemotePhase.PreparingCamera) { id, sessionScope ->
            val credentials = setup.prepare(id)
                ?: throw WifiRemoteConnectException(WifiRemoteFailure.CameraSetupFailed)
            currentCoroutineContext().ensureActive()
            val joined = CompletableDeferred<Unit>()
            manualJoin = joined
            try {
                registry.updateWifiRemote(
                    id, WifiRemoteState(
                        phase = WifiRemotePhase.AwaitingManualNetwork, manualNetwork = credentials
                    )
                )
                log.i { "Wi-Fi remote attempt=$attempt waiting for manual network join" }
                joined.await()
                setup.open(credentials, sessionScope)
            } finally {
                manualJoin = null
            }
        }
    }

    fun continueManualConnection(identifier: String) {
        if (_owner.value.equals(identifier, true) &&
            registry.get(identifier)?.wifiRemote?.phase == WifiRemotePhase.AwaitingManualNetwork
        ) {
            registry.updateWifiRemote(
                identifier,
                WifiRemoteState(phase = WifiRemotePhase.OpeningSession)
            )
            manualJoin?.complete(Unit)
        }
    }

    private fun start(identifier: String, phase: WifiRemotePhase,
                      open: suspend (String, CoroutineScope) -> WifiRemoteConnection) {
        if (job != null) {
            log.w { "Wi-Fi remote connect ignored: previous attempt=$attempt is still active or closing" }
            return
        }
        attempt++
        log.i { "Wi-Fi remote attempt=$attempt starting phase=$phase" }
        val id = identifier.uppercase()
        if (registry.get(id) == null) registry.upsert(id) { it.copy(phase = BleSessionPhase.Disconnected) }
        _owner.value = id
        _image.value = null
        registry.updateWifiRemote(id, WifiRemoteState(phase = phase))
        var entered = false
        val connecting = scope.launch(start = CoroutineStart.LAZY) {
            entered = true
            runSession(id, open)
        }
        job = connecting
        connecting.invokeOnCompletion {
            if (!entered && job === connecting) {
                registry.updateWifiRemote(id, WifiRemoteState())
                _owner.value = null
                job = null
            }
        }
        connecting.start()
    }

    fun capture(identifier: String) {
        val id = identifier.uppercase()
        val state = registry.get(id)?.wifiRemote ?: return
        if (_owner.value != id || state.phase != WifiRemotePhase.Ready || !state.canCaptureStill ||
            state.capture == WifiCaptureStatus.Shooting || state.photoBrowser.open || photoJob != null
        ) return
        registry.updateWifiRemote(
            id,
            state.copy(
                capture = WifiCaptureStatus.Shooting,
                imageTransfer = WifiImageTransferState()
            )
        )
        if (captureRequests?.trySend(Unit)?.isSuccess != true) registry.updateWifiRemote(id, state)
    }

    fun browsePhotos(identifier: String, offset: Int? = null, preferredPhotoIndex: Int? = null) {
        // A refresh (offset == null) opens a new catalog generation: previously loaded pages,
        // saved-download markers, and pending cross-page selections all become stale.
        val generation = if (offset == null) ++browserGeneration else 0L
        photoWork(identifier, { state ->
            state.copy(
                photoBrowser = state.photoBrowser.copy(
                    open = true, loading = true, failed = false,
                    browserGeneration = if (offset == null) generation
                    else state.photoBrowser.browserGeneration,
                    photos = if (offset == null) emptyList() else state.photoBrowser.photos,
                    savedDownloads = if (offset == null) emptySet() else state.photoBrowser.savedDownloads,
                    grouped = false, dates = emptyList(), dateKey = null
                )
            )
        }) { id, connection ->
            clearPhotoPreview(id)
            previewJob?.cancelAndJoin()
            previewJob = null
            _image.value = null
            thumbJob?.cancelAndJoin()
            thumbJob = null
            if (offset == null) {
                photoCatalog = emptyMap()
                _thumbnails.value = emptyMap()
            }
            updateReady(id) { it.copy(hasLiveView = false) }
            val page =
                if (offset == null) connection.openPhotoBrowser() else connection.photoPage(offset)
            _thumbnails.value = emptyMap()
            photoCatalog += page.photos.associateBy { it.handle }
            updateReady(id) {
                it.copy(
                    photoBrowser = it.photoBrowser.copy(
                        photos = page.photos,
                        offset = page.offset,
                        totalObjects = page.totalObjects,
                        hasMore = page.hasMore,
                        pageRevision = it.photoBrowser.pageRevision + 1
                    )
                )
            }
            val captures = groupCameraPhotos(page.photos)
            thumbFocus = preferredPhotoIndex?.minus(page.offset)
                ?.coerceIn(0, (captures.size - 1).coerceAtLeast(0)) ?: 0
            // The catalog is on screen now: `loading` ends with this work, and the thumbnails
            // arrive in small batches afterwards (paused while a transfer/preview runs).
            thumbJob = sessionScope?.launch { loadThumbnails(connection, page.offset, captures) }
        }
    }

    /**
     * Date-grouped entry (a new catalog generation, like a refresh): enumerates the whole card and
     * publishes its days. No page is shown and no thumbnail is requested until [browseDate]; a failure
     * leaves `failed` set so the same call can simply be retried.
     */
    fun browseDates(identifier: String) {
        val generation = ++browserGeneration
        photoWork(identifier, { state ->
            state.copy(
                photoBrowser = state.photoBrowser.copy(
                    open = true, loading = true, failed = false, browserGeneration = generation,
                    photos = emptyList(), offset = 0, totalObjects = 0, hasMore = false,
                    savedDownloads = emptySet(), grouped = true, dates = emptyList(), dateKey = null
                )
            )
        }) { id, connection ->
            clearPhotoPreview(id)
            previewJob?.cancelAndJoin()
            previewJob = null
            _image.value = null
            thumbJob?.cancelAndJoin()
            thumbJob = null
            photoCatalog = emptyMap()
            _thumbnails.value = emptyMap()
            updateReady(id) { it.copy(hasLiveView = false) }
            val dates = connection.openCatalog()
            updateReady(id) {
                it.copy(
                    photoBrowser = it.photoBrowser.copy(
                        dates = dates, totalObjects = dates.sumOf { group -> group.count },
                        pageRevision = it.photoBrowser.pageRevision + 1
                    )
                )
            }
        }
    }

    /**
     * Shows one page of [dateKey] from the already enumerated catalog. Never opens a new generation:
     * selections, saved markers and the handle index survive switching days and turning pages, and the
     * camera is not asked to enumerate again.
     */
    fun browseDate(identifier: String, dateKey: String, offset: Int = 0, preferredPhotoIndex: Int? = null) {
        val browser = registry.get(identifier.uppercase())?.wifiRemote?.photoBrowser ?: return
        if (!browser.open || !browser.grouped || browser.dates.none { it.key == dateKey }) return
        photoWork(identifier, { state ->
            state.copy(
                photoBrowser = state.photoBrowser.copy(
                    loading = true, failed = false,
                    photos = if (state.photoBrowser.dateKey == dateKey) state.photoBrowser.photos
                    else emptyList()
                )
            )
        }) { id, connection ->
            clearPhotoPreview(id)
            previewJob?.cancelAndJoin()
            previewJob = null
            _image.value = null
            thumbJob?.cancelAndJoin()
            thumbJob = null
            val page = connection.datePage(dateKey, offset)
            _thumbnails.value = emptyMap()
            photoCatalog += page.photos.associateBy { it.handle }
            updateReady(id) {
                it.copy(
                    photoBrowser = it.photoBrowser.copy(
                        photos = page.photos, offset = page.offset, totalObjects = page.totalObjects,
                        hasMore = page.hasMore, dateKey = dateKey,
                        pageRevision = it.photoBrowser.pageRevision + 1
                    )
                )
            }
            val captures = groupCameraPhotos(page.photos)
            thumbFocus = preferredPhotoIndex?.minus(page.offset)
                ?.coerceIn(0, (captures.size - 1).coerceAtLeast(0)) ?: 0
            thumbJob = sessionScope?.launch { loadThumbnails(connection, page.offset, captures) }
        }
    }

    /**
     * Back from a day to the day list. Purely local: it neither talks to the camera nor opens a new
     * generation, so selections and saved markers are kept. Ignored while a load or transfer runs.
     */
    fun showDates(identifier: String) {
        val id = identifier.uppercase()
        val state = registry.get(id)?.wifiRemote ?: return
        val browser = state.photoBrowser
        if (_owner.value != id || state.phase != WifiRemotePhase.Ready || !browser.open ||
            !browser.grouped || browser.dateKey == null || browser.loading || photoJob != null ||
            state.imageTransfer.busy
        ) return
        clearPhotoPreview(id)
        thumbJob?.cancel()
        _thumbnails.value = emptyMap()
        updateReady(id) {
            it.copy(
                photoBrowser = it.photoBrowser.copy(
                    photos = emptyList(), offset = 0, hasMore = false, dateKey = null,
                    totalObjects = it.photoBrowser.dates.sumOf { group -> group.count },
                    pageRevision = it.photoBrowser.pageRevision + 1
                )
            )
        }
    }

    /** The grid reports the first capture (page-relative) it shows so nearby thumbnails load first. */
    fun focusThumbnails(identifier: String, captureIndex: Int) {
        if (_owner.value.equals(identifier, true)) thumbFocus = captureIndex.coerceAtLeast(0)
    }

    private fun holdThumbnails() = thumbnailHolds.update { it + 1 }
    private fun releaseThumbnails() = thumbnailHolds.update { (it - 1).coerceAtLeast(0) }

    /**
     * Fetches the page's thumbnails in batches of [THUMBNAIL_BATCH_SIZE], each one published as soon
     * as it arrives. The next batch is chosen from what is still missing, starting at the grid's
     * current position. A failed or missing thumbnail only affects itself: already arrived ones are
     * kept and the loader moves on. Cancelling (page change, refresh, disconnect) stops after the
     * request in flight and keeps what arrived.
     */
    private suspend fun loadThumbnails(
        connection: WifiRemoteConnection, pageOffset: Int, captures: List<WifiCameraCapture>
    ) {
        val settled = HashSet<Int>()
        // "AlphaThumb" diagnostics: per-batch lines plus one per-page summary.
        val pageStats = ThumbnailPageStats(pageOffset, captures.size)
        var batchNumber = 0
        _thumbnailsLoading.value = true
        try {
            while (true) {
                val batch = nextThumbnailBatch(captures.size, settled, thumbFocus)
                if (batch.isEmpty()) break
                batchNumber++
                val started = TimeSource.Monotonic.markNow()
                var fetched = 0
                var requestMs = 0L
                var bytes = 0L
                for (index in batch) {
                    thumbnailHolds.first { it == 0 }
                    settled += index
                    val photo = captures[index].thumbnail
                    var fetchedBytes = 0L
                    var itemMs = 0L
                    val bitmap = try {
                        connection.photoThumbnail(photo.handle, ThumbnailStatsSink { _, received, ms ->
                            fetchedBytes = received.toLong()
                            itemMs = ms
                        })
                    } catch (_: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive() // a real cancel still propagates
                        thumbLog.w { "page=$pageOffset handle=${photo.handle} timed out" }
                        null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        thumbLog.w { "page=$pageOffset handle=${photo.handle} failed: ${failure.message}" }
                        null
                    }
                    if (bitmap == null) continue
                    val bitmapEstBytes = bitmap.width.toLong() * bitmap.height * 4
                    pageStats.record(ThumbnailSample(itemMs, fetchedBytes, bitmapEstBytes))
                    thumbLog.d {
                        "page=$pageOffset #$index handle=${photo.handle} cid=${photo.captureId ?: "-"} " +
                                "bytes=$fetchedBytes reqMs=$itemMs bitmap=${bitmap.width}x${bitmap.height} " +
                                "estBytes=$bitmapEstBytes"
                    }
                    _thumbnails.value += photo.handle to bitmap
                    fetched++
                    requestMs += itemMs
                    bytes += fetchedBytes
                }
                thumbLog.i {
                    thumbnailBatchLine(
                        pageOffset, batchNumber, fetched, batch.size, requestMs,
                        started.elapsedNow().inWholeMilliseconds, bytes
                    )
                }
            }
            pageStats.takeSummary()?.let { line -> thumbLog.i { line } }
        } catch (cancelled: CancellationException) {
            pageStats.takeCancelled()?.let { line -> thumbLog.i { line } }
            throw cancelled
        } finally {
            // A replacement loader only starts after this one was joined, so this cannot clobber it.
            _thumbnailsLoading.value = false
        }
    }

    fun showPhotoPreview(identifier: String, handle: Long) {
        val id = identifier.uppercase()
        val state = registry.get(id)?.wifiRemote ?: return
        val connection = activeConnection ?: return
        val parent = sessionScope ?: return
        val capture = groupCameraPhotos(state.photoBrowser.photos)
            .firstOrNull { it.preview.handle == handle } ?: return
        if (_owner.value != id || state.phase != WifiRemotePhase.Ready ||
            !state.photoBrowser.open || state.imageTransfer.busy ||
            _photoPreview.value?.handle == handle
        ) return
        clearPhotoPreview(id)
        _photoPreview.value = WifiPhotoPreview(handle)
        holdThumbnails()
        photoPreviewJob = parent.launch {
            var image: ImageBitmap? = null
            for (photo in capture.previewCandidates) {
                currentCoroutineContext().ensureActive()
                image = try {
                    connection.photoPreview(photo.handle)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    log.w { "${photo.formatLabel} preview failed: ${failure.message}" }
                    null
                }
                currentCoroutineContext().ensureActive()
                if (image != null) break
                log.d { "${photo.formatLabel} preview unavailable; trying remaining capture formats" }
            }
            currentCoroutineContext().ensureActive()
            _photoPreview.value = WifiPhotoPreview(handle, image, loading = false)
            if (image == null) log.d { "Camera screen preview unavailable; retaining thumbnail" }
        }.also { it.invokeOnCompletion { releaseThumbnails() } }
    }

    fun clearPhotoPreview(identifier: String) {
        if (!_owner.value.equals(identifier, true)) return
        photoPreviewJob?.cancel()
        photoPreviewJob = null
        _photoPreview.value = null
    }

    fun downloadPhoto(identifier: String, handle: Long, format: WifiPhotoDownloadFormat = WifiPhotoDownloadFormat.Original) {
        downloadPhotos(identifier, listOf(WifiPhotoDownload(handle, format)))
    }

    fun downloadPhotos(identifier: String, downloads: List<WifiPhotoDownload>) {
        val state = registry.get(identifier)?.wifiRemote ?: return
        if (!state.photoBrowser.open) return
        // Freeze the request against the current catalog generation so a batch that was
        // assembled earlier (e.g. behind a permission prompt) cannot download stale handles.
        val generation = state.photoBrowser.browserGeneration
        val photos = photoCatalog
        val pending = downloads.distinct().filter { download ->
            val photo = photos[download.handle]
            photo != null && photo.downloadable && download !in state.photoBrowser.savedDownloads &&
                    (download.format == WifiPhotoDownloadFormat.Original ||
                            (state.canConvertHeif && photo.mimeType == "image/heif"))
        }
        if (pending.isEmpty()) return
        photoWork(
            identifier,
            {
                it.copy(
                    imageTransfer = WifiImageTransferState(
                        WifiImageTransferStatus.Downloading,
                        totalFiles = pending.size
                    )
                )
            }) { id, connection ->
            val current = registry.get(id)?.wifiRemote
            if (current?.photoBrowser?.open != true ||
                current.photoBrowser.browserGeneration != generation
            ) {
                // The catalog was refreshed or closed after the batch was accepted.
                log.w { "Wi-Fi remote download batch expired: generation changed" }
                updateReady(id) {
                    it.copy(
                        imageTransfer = WifiImageTransferState(
                            WifiImageTransferStatus.Failed,
                            failureReason = WifiTransferFailureReason.CatalogChanged
                        )
                    )
                }
                return@photoWork
            }
            for ((index, download) in pending.withIndex()) {
                currentCoroutineContext().ensureActive()
                updateReady(id) {
                    it.copy(
                        imageTransfer = WifiImageTransferState(
                            WifiImageTransferStatus.Downloading,
                            filename = photos.getValue(download.handle).filename,
                            completedFiles = index, totalFiles = pending.size
                        )
                    )
                }
                val result =
                    connection.downloadPhoto(download.handle, download.format) { progress ->
                        updateReady(id) {
                            it.copy(
                                imageTransfer = progress.copy(
                                    completedFiles = index,
                                    totalFiles = pending.size
                                )
                            )
                        }
                    }
                val saved = result.status == WifiImageTransferStatus.Saved
                updateReady(id) {
                    it.copy(
                        imageTransfer = result.copy(
                            status = if (saved && index < pending.lastIndex) WifiImageTransferStatus.Downloading else result.status,
                            completedFiles = index + if (saved) 1 else 0, totalFiles = pending.size
                        ),
                        photoBrowser = it.photoBrowser.copy(
                            savedDownloads = if (saved) it.photoBrowser.savedDownloads + download
                            else it.photoBrowser.savedDownloads
                        )
                    )
                }
                if (!saved) break
            }
        }
    }

    fun leavePhotoBrowser(identifier: String) {
        photoWork(
            identifier,
            {
                it.copy(
                    photoBrowser = it.photoBrowser.copy(
                        loading = true,
                        failed = false
                    )
                )
            }) { id, connection ->
            clearPhotoPreview(id)
            previewJob?.cancelAndJoin()
            previewJob = null
            thumbJob?.cancelAndJoin()
            thumbJob = null
            connection.closePhotoBrowser()
            photoCatalog = emptyMap()
            _thumbnails.value = emptyMap()
            updateReady(id) {
                it.copy(
                    photoBrowser = WifiPhotoBrowserState(
                        savedDownloads =
                            if (connection.photoHandlesSurviveModeSwitch) it.photoBrowser.savedDownloads else emptySet()
                    ),
                    canCaptureStill = connection.canCapture,
                    canTransferImages = connection.canTransferImages,
                    imageTransfer = WifiImageTransferState(), preview = WifiPreviewStatus.Waiting
                )
            }
            startPreview(id, connection, sessionScope!!)
        }
    }

    fun cancelPhotoOperation(identifier: String) {
        if (_owner.value.equals(identifier, true)) photoJob?.cancel()
    }

    fun clearPhotoTransferResult(identifier: String) {
        if (!_owner.value.equals(identifier, true)) return
        updateReady(identifier.uppercase()) {
            if (it.imageTransfer.busy) it else it.copy(imageTransfer = WifiImageTransferState())
        }
    }

    fun imageStoragePermissionDenied(identifier: String) {
        updateReady(identifier.uppercase()) {
            it.copy(
                imageTransfer = WifiImageTransferState(
                    WifiImageTransferStatus.PermissionDenied
                )
            )
        }
    }

    private fun photoWork(
        identifier: String, starting: (WifiRemoteState) -> WifiRemoteState,
        action: suspend (String, WifiRemoteConnection) -> Unit
    ) {
        val id = identifier.uppercase()
        val state = registry.get(id)?.wifiRemote ?: return
        val connection = activeConnection ?: return
        val parent = sessionScope ?: return
        if (_owner.value != id || state.phase != WifiRemotePhase.Ready || !state.canTransferImages ||
            state.capture == WifiCaptureStatus.Shooting || photoJob != null
        ) return
        photoPreviewJob?.cancel()
        photoPreviewJob = null
        _photoPreview.value =
            _photoPreview.value?.takeIf { it.image != null }?.copy(loading = false)
        registry.updateWifiRemote(id, starting(state))
        holdThumbnails()
        val work = parent.launch(start = CoroutineStart.LAZY) {
            try {
                action(id, connection)
            } catch (_: TimeoutCancellationException) {
                updateReady(id) {
                    it.copy(
                        photoBrowser = it.photoBrowser.copy(failed = true),
                        imageTransfer = if (it.imageTransfer.busy) it.imageTransfer.copy(
                            status = WifiImageTransferStatus.Failed,
                            failureReason = WifiTransferFailureReason.Timeout
                        ) else it.imageTransfer
                    )
                }
            } catch (cancelled: CancellationException) {
                parent.coroutineContext.ensureActive()
                updateReady(id) {
                    it.copy(
                        photoBrowser = it.photoBrowser.copy(failed = it.photoBrowser.loading),
                        imageTransfer = if (it.imageTransfer.busy)
                            it.imageTransfer.copy(status = WifiImageTransferStatus.Cancelled) else it.imageTransfer
                    )
                }
            } catch (failure: Exception) {
                // No protocol/store signal is available at this level; keep the generic cause.
                log.w { "photo work failed: ${failure::class.simpleName}" }
                updateReady(id) {
                    it.copy(
                        photoBrowser = it.photoBrowser.copy(failed = true),
                        imageTransfer = if (it.imageTransfer.busy) it.imageTransfer.copy(
                            status = WifiImageTransferStatus.Failed,
                            failureReason = WifiTransferFailureReason.Unknown
                        ) else it.imageTransfer
                    )
                }
            } finally {
                updateReady(id) { it.copy(photoBrowser = it.photoBrowser.copy(loading = false)) }
            }
        }
        photoJob = work
        work.invokeOnCompletion {
            if (photoJob === work) {
                photoJob = null
                updateReady(id) {
                    it.copy(
                        photoBrowser = it.photoBrowser.copy(loading = false),
                        imageTransfer = if (it.imageTransfer.busy) it.imageTransfer.copy(
                            status = WifiImageTransferStatus.Cancelled
                        ) else it.imageTransfer
                    )
                }
            }
            releaseThumbnails()
        }
        work.start()
    }

    fun disconnect(identifier: String? = null) {
        val id = _owner.value ?: return
        if (identifier != null && !id.equals(identifier, true)) return
        log.i { "Wi-Fi remote attempt=$attempt disconnect requested" }
        registry.updateWifiRemote(id, WifiRemoteState(phase = WifiRemotePhase.Closing))
        job?.cancel()
    }

    suspend fun closeAndJoin(identifier: String? = null) {
        if (identifier != null && !_owner.value.equals(identifier, true)) return
        val closing = job
        disconnect(identifier)
        closing?.join()
    }

    fun permissionDenied(identifier: String) {
        if (job != null) return
        if (registry.get(identifier) == null) registry.upsert(identifier) { it.copy(phase = BleSessionPhase.Disconnected) }
        registry.updateWifiRemote(identifier, WifiRemoteState(WifiRemotePhase.Failed, WifiRemoteFailure.NetworkPermissionDenied))
    }

    private suspend fun runSession(id: String, open: suspend (String, CoroutineScope) -> WifiRemoteConnection) {
        var connection: WifiRemoteConnection? = null
        var failure: WifiRemoteFailure? = null
        var shutdown = WifiShutdownStatus.NotRequested
        try {
            log.i { "Wi-Fi remote attempt=$attempt claiming BLE remote controls" }
            claimControls(id)
            log.i { "Wi-Fi remote attempt=$attempt BLE remote controls claimed" }
            coroutineScope {
                val opened = open(id, this)
                connection = opened
                log.i { "Wi-Fi remote attempt=$attempt ready; starting live view" }
                val requests = Channel<Unit>(Channel.RENDEZVOUS)
                captureRequests = requests
                registry.updateWifiRemote(id, WifiRemoteState(phase = WifiRemotePhase.Ready,
                    canCaptureStill = opened.canCapture, cameraName = opened.cameraName,
                    canTransferImages = opened.canTransferImages,
                    canConvertHeif = opened.canConvertHeif
                )
                )
                launch {
                    opened.lost.first()
                    throw WifiRemoteConnectException(WifiRemoteFailure.NetworkLost)
                }
                sessionScope = this
                activeConnection = opened
                startPreview(id, opened, this)
                launch(start = CoroutineStart.UNDISPATCHED) {
                    var confirmationTimer: Job? = null
                    for (ignored in requests) {
                        confirmationTimer?.cancel()
                        val result = try { opened.capture() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { WifiCaptureStatus.Uncertain }
                        updateReady(id) { it.copy(capture = result) }
                        confirmationTimer = if (result == WifiCaptureStatus.Captured) launch {
                            delay(3.seconds)
                            updateReady(id) {
                                if (it.capture == WifiCaptureStatus.Captured) it.copy(capture = WifiCaptureStatus.Idle)
                                else it
                            }
                        } else null
                    }
                }
                awaitCancellation()
            }
        } catch (cancelled: CancellationException) {
            log.i { "Wi-Fi remote attempt=$attempt cancelled" }
            throw cancelled
        } catch (failed: WifiRemoteConnectException) {
            failure = failed.failure
            log.w { "Wi-Fi remote attempt=$attempt failed: $failure" }
        } catch (failed: Exception) {
            failure = WifiRemoteFailure.ProtocolError
            log.w { "Wi-Fi remote attempt=$attempt failed: ${failed.message}" }
        }
        finally {
            sessionScope = null
            activeConnection = null
            previewJob = null
            photoJob = null
            thumbJob = null
            _thumbnailsLoading.value = false
            thumbnailHolds.value = 0
            photoPreviewJob = null
            photoCatalog = emptyMap()
            _photoPreview.value = null
            _thumbnails.value = emptyMap()
            captureRequests?.close()
            captureRequests = null
            withContext(NonCancellable) {
                try {
                    if (failure == null) {
                        try {
                            withTimeoutOrNull(5.seconds) { connection?.prepareToDisconnect() }
                        } catch (_: Exception) { /* Socket closure also ends transfer mode. */
                        }
                    }
                    if (connection != null && failure == null && registry.get(id)?.wifiRemote?.phase == WifiRemotePhase.Closing) {
                        shutdown = try {
                            connection.turnOffWifi()
                        } catch (_: Exception) {
                            WifiShutdownStatus.Unconfirmed
                        }
                    }
                } finally {
                    try {
                        connection?.close()
                    } catch (_: Exception) { /* Best-effort socket teardown. */
                    } finally {
                        _image.value = null
                        registry.updateWifiRemote(
                            id, if (failure == null) WifiRemoteState(wifiShutdown = shutdown)
                            else WifiRemoteState(WifiRemotePhase.Failed, failure)
                        )
                        releaseControls(id)
                        _owner.value = null
                        job = null
                        log.i { "Wi-Fi remote attempt=$attempt cleanup complete; failure=$failure shutdown=$shutdown" }
                    }
                }
            }
        }
    }

    private fun startPreview(id: String, connection: WifiRemoteConnection, parent: CoroutineScope) {
        if (!connection.hasLiveViewStream) {
            log.i { "Wi-Fi live view unavailable for this connection (browse-only?)" }
            updateReady(id) { it.copy(hasLiveView = false, preview = WifiPreviewStatus.Unavailable) }
            return
        }
        previewJob = parent.launch {
            var receivedFrame = false
            log.i { "Wi-Fi live view starting" }
            try {
                connection.images.collect { image ->
                    if (!receivedFrame) {
                        log.i { "Wi-Fi live view first image ${image.width}x${image.height}" }
                        receivedFrame = true
                    }
                    _image.value = image
                    updateReady(id) {
                        it.copy(
                            hasLiveView = true,
                            preview = WifiPreviewStatus.Streaming
                        )
                    }
                }
                updateReady(id) { it.copy(hasLiveView = false, preview = WifiPreviewStatus.Failed) }
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                log.w { "Wi-Fi live view timed out: ${cancelled.message}" }
                updateReady(id) { it.copy(hasLiveView = false, preview = WifiPreviewStatus.Failed) }
            } catch (failure: Exception) {
                log.w { "Wi-Fi live view failed: ${failure.message}" }
                updateReady(id) { it.copy(hasLiveView = false, preview = WifiPreviewStatus.Failed) }
            } finally {
                log.i { "Wi-Fi live view stopped; receivedFrame=$receivedFrame" }
                _image.value = null
            }
        }
    }

    private fun updateReady(id: String, update: (WifiRemoteState) -> WifiRemoteState) {
        val state = registry.get(id)?.wifiRemote ?: return
        if (state.phase == WifiRemotePhase.Ready) registry.updateWifiRemote(id, update(state))
    }
}
