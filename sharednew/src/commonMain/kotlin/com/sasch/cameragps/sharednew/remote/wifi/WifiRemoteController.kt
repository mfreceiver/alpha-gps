package com.sasch.cameragps.sharednew.remote.wifi

import androidx.compose.ui.graphics.ImageBitmap
import com.diamondedge.logging.logging
import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal interface WifiRemoteConnection {
    val cameraName: String
    val canCapture: Boolean
    val canFocus: Boolean get() = false
    val canRecordMovie: Boolean get() = false
    val canReadProperties: Boolean get() = false
    val canWriteProperties: Boolean get() = false
    val canTransferImages: Boolean get() = false
    val canConvertHeif: Boolean get() = false
    val photoHandlesSurviveModeSwitch: Boolean get() = true

    /**
     * Enumerates the whole card and returns its days (newest first, unknown last) without showing any
     * day: nothing is addressable until [dayPhotos] (except day cover thumbnails).
     */
    suspend fun openCatalog(): List<DateGroup> = error("Photo browsing unavailable")

    /**
     * Every photo of a single day from the enumerated catalog (unpaged: offset 0, no `hasMore`);
     * becomes the displayed set that thumbnails and previews are limited to.
     */
    suspend fun dayPhotos(dateKey: String): CameraPhotoPage =
        error("Photo browsing unavailable")

    suspend fun photoThumbnail(handle: Long, stats: ThumbnailStatsSink? = null): ImageBitmap? = null
    suspend fun photoPreview(handle: Long): ImageBitmap? = null

    /**
     * The first [length] bytes of the original of a displayed photo (for EXIF), or `null` when this
     * connection cannot read partial data. Failures throw; they never affect other operations.
     */
    suspend fun photoMetadataHead(handle: Long, length: Int): ByteArray? = null
    suspend fun downloadPhoto(
        handle: Long,
        format: WifiPhotoDownloadFormat,
        onProgress: (WifiImageTransferState) -> Unit
    ): WifiImageTransferState =
        WifiImageTransferState(WifiImageTransferStatus.Failed)

    suspend fun closePhotoBrowser() {}

    /**
     * One-shot battery percentage (0..100) from the camera, `null` when the camera does not expose
     * it or the read failed. Best-effort and never throws.
     */
    suspend fun readBatteryPercent(): Int? = null

    /** End transfer mode without reopening a shooting session during teardown. */
    suspend fun prepareToDisconnect() = closePhotoBrowser()

    /**
     * Whether this connection exposes a live-view stream at all. Browse-only sessions
     * never start the live-view HTTP stream; the controller reports the preview as
     * [WifiPreviewStatus.Unavailable] instead of collecting an immediately-complete flow.
     */
    val hasLiveViewStream: Boolean get() = true
    val images: Flow<ImageBitmap>
    val liveViewFrames: Flow<WifiLiveViewFrame> get() = images.map { WifiLiveViewFrame(it) }
    val lost: Flow<Unit>
    val propertyChangedEvents: Flow<Unit> get() = emptyFlow()
    suspend fun probeProperties(): PropertyProbeStatus = PropertyProbeStatus.Unsupported
    suspend fun readShootingProperties(): List<CameraShootingProperty>? = null
    suspend fun setShootingProperty(
        key: CameraShootingPropertyKey,
        dataType: Int,
        rawValue: Long,
    ): PropertyWriteStatus = PropertyWriteStatus.Unsupported
    suspend fun focus(): RemoteActionStatus = RemoteActionStatus.Rejected
    suspend fun toggleMovieRecord(): RemoteActionStatus = RemoteActionStatus.Rejected
    suspend fun capture(): WifiCaptureStatus
    suspend fun turnOffWifi(): WifiShutdownStatus = WifiShutdownStatus.NotRequested
    suspend fun close()
}

internal fun interface WifiRemoteConnector {
    val supportsAddressDiscovery: Boolean get() = false

    /** A blank host requests address discovery when supported. */
    suspend fun open(host: String, scope: CoroutineScope): WifiRemoteConnection

    suspend fun open(
        host: String,
        scope: CoroutineScope,
        purpose: WifiSessionPurpose,
    ): WifiRemoteConnection = open(host, scope)
}

/**
 * Deliberately not a `fun interface` and without a default for [phonePreconditions]: every
 * implementation (including wrappers) must state whether it forwards the phone radio checks, so a
 * wrapper can no longer silently drop them and bypass the controller's preflight.
 */
internal interface WifiAutomaticConnector {
    suspend fun open(identifier: String, scope: CoroutineScope, onPhase: (WifiRemotePhase) -> Unit): WifiRemoteConnection

    suspend fun open(
        identifier: String,
        scope: CoroutineScope,
        purpose: WifiSessionPurpose,
        onPhase: (WifiRemotePhase) -> Unit,
    ): WifiRemoteConnection = open(identifier, scope, onPhase)

    /**
     * Phone-side preconditions the controller checks before anything else; null only when the
     * connector has no radio to ask. [AutomaticWifiRemoteConnector] exposes its [WifiPreconditions]
     * so the controller can fail fast without touching the camera's BLE control takeover.
     */
    val phonePreconditions: WifiPreconditions?
}

internal interface WifiManualSetupConnector {
    suspend fun prepare(identifier: String): CameraWifiCredentials?
    suspend fun open(
        credentials: CameraWifiCredentials,
        scope: CoroutineScope
    ): WifiRemoteConnection

    suspend fun open(
        credentials: CameraWifiCredentials,
        scope: CoroutineScope,
        purpose: WifiSessionPurpose,
    ): WifiRemoteConnection = open(credentials, scope)
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
    private val backgroundDownloadTimeoutMs: Long = 30 * 60 * 1_000L,
    /** Receives (identifier, percent) once per session when the camera reports its battery level. */
    private val onBatteryRead: (String, Int) -> Unit = { _, _ -> },
    /** Where the thumbnail letterbox diagnostics scan runs (a child of the loader); tests swap it. */
    private val diagnosticsDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val letterboxScan: (IntArray, Int, Int, () -> Unit) -> LetterboxBarCounts =
        { pixels, width, height, checkpoint -> letterboxBarCounts(pixels, width, height, checkpoint) },
    /** Delay without a newly decoded live-view frame before freezing the last frame and marking `liveViewPaused`. */
    private val liveViewStaleTimeoutMs: Long = 2_000L,
) {
    private val log = logging()

    /** Wi-Fi photo-browser thumbnail diagnostics (see ThumbnailDiagnostics.kt). */
    private val thumbLog = logging("AlphaThumb")

    /**
     * Letterbox scans run here, detached from every loader (a SupervisorJob: a scan can never fail a
     * loader) and tracked in [diagnosticJobs] (main-thread only) so [cancelDiagnostics] can stop them.
     */
    private val diagnosticsScope = CoroutineScope(SupervisorJob() + diagnosticsDispatcher)
    private val diagnosticJobs = ArrayList<Job>()

    private fun cancelDiagnostics() {
        diagnosticJobs.forEach { it.cancel() }
        diagnosticJobs.clear()
    }
    private var attempt = 0
    val supportsAutomaticConnection get() = automaticConnector != null
    val supportsManualSetup get() = manualSetupConnector != null
    val supportsAddressDiscovery get() = connector.supportsAddressDiscovery
    val sessions = registry.sessions
    private val _image = MutableStateFlow<ImageBitmap?>(null)
    val image = _image.asStateFlow()
    private val _liveViewFrame = MutableStateFlow<WifiLiveViewFrame?>(null)
    val liveViewFrame: StateFlow<WifiLiveViewFrame?> = _liveViewFrame.asStateFlow()
    private val _owner = MutableStateFlow<String?>(null)
    val owner = _owner.asStateFlow()
    private var job: Job? = null
    /** True while an attempt's phone preflight runs: nothing has been published or claimed yet. */
    private var preflightActive = false
    private val _thumbnails = MutableStateFlow<Map<Long, ImageBitmap>>(emptyMap())
    val thumbnails = _thumbnails.asStateFlow()
    private val _dateCoverThumbnails = MutableStateFlow<Map<Long, ImageBitmap>>(emptyMap())
    val dateCoverThumbnails = _dateCoverThumbnails.asStateFlow()
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
    private var userDownloadInProgress = false
    private var isForeground = true
    private var backgroundDownloadTimer: Job? = null
    private var backgroundEpoch = 0L

    /** Activity visibility is app-owned, not tied to the browser composition's lifetime. */
    fun onAppVisibilityChanged(visible: Boolean) {
        isForeground = visible
        applyBackgroundPolicy()
    }

    /** Navigation closes Wi-Fi; background disposal must not cancel an accepted download or another purpose's session. */
    fun onRemoteScreenDisposed(identifier: String, expectedPurpose: WifiSessionPurpose? = null) {
        val id = identifier.uppercase()
        if (!_owner.value.equals(id, true)) return
        val currentPurpose = registry.get(id)?.wifiRemote?.purpose
        if (expectedPurpose != null && currentPurpose != expectedPurpose) return
        if (isForeground) disconnect(identifier) else applyBackgroundPolicy()
    }

    private fun cancelBackgroundDownloadTimer() {
        backgroundEpoch++
        backgroundDownloadTimer?.cancel()
        backgroundDownloadTimer = null
    }

    private fun applyBackgroundPolicy() {
        val id = _owner.value ?: return
        when (BackgroundWifiPolicy.decide(isForeground, userDownloadInProgress)) {
            BackgroundWifiPolicy.Action.Keep -> cancelBackgroundDownloadTimer()
            BackgroundWifiPolicy.Action.Close -> disconnect(id)
            BackgroundWifiPolicy.Action.FinishDownload -> {
                if (backgroundDownloadTimer != null) return
                val epoch = backgroundEpoch
                val sessionAttempt = attempt
                backgroundDownloadTimer = scope.launch {
                    delay(backgroundDownloadTimeoutMs)
                    // A cancelled timer must not close a later visit or a new same-camera session.
                    if (epoch == backgroundEpoch && attempt == sessionAttempt &&
                        _owner.value == id && !isForeground && userDownloadInProgress
                    ) {
                        log.w { "Wi-Fi background download retention timed out; attempt=$sessionAttempt" }
                        disconnect(id)
                    }
                }
            }
        }
    }
    /** Background batched thumbnail loader of the current day (independent of [photoJob]). */
    private var thumbJob: Job? = null
    /** Serial cover-thumbnail loader for visible rows of the date list (independent of [photoJob]). */
    private var dateCoverJob: Job? = null
    /**
     * While > 0 (a catalog load, download or screen preview is using the camera's single
     * command queue) the thumbnail loader waits between requests instead of competing.
     */
    private val thumbnailHolds = MutableStateFlow(0)
    /** What the grid currently shows (conflated); only the grouped browser's loader consumes it. */
    private val thumbDemand = MutableStateFlow(ThumbnailDemand(-1, emptyList(), emptyList()))
    private data class CoverDemand(val generation: Long, val handles: List<Long>)
    private val coverDemand = MutableStateFlow(CoverDemand(-1L, emptyList()))
    private val settledCoverHandles = HashSet<Long>()
    /** Monotonic catalog generation: every (re)open or refresh starts a new one. */
    private var browserGeneration = 0L
    /** Full handle index of pages that loaded successfully within the current generation. */
    private var photoCatalog = emptyMap<Long, WifiCameraPhoto>()
    private var captureRequests: Channel<Unit>? = null

    /**
     * Metadata of the photo the viewer shows (see [PhotoMetadataLoader]): basic data at once, EXIF on
     * request through one debounced, cancellable partial read that waits for an idle camera.
     */
    private val metadataLoader = PhotoMetadataLoader(
        scope,
        basicFor = { photoCatalog[it]?.toBasicMetadata() },
        fetchHead = { handle, length -> activeConnection?.photoMetadataHead(handle, length) },
        awaitIdle = { thumbnailHolds.first { it == 0 } },
        hold = ::holdThumbnails,
        release = ::releaseThumbnails,
    )
    val metadata: StateFlow<PhotoMetadataState> = metadataLoader.state
    private var manualJoin: CompletableDeferred<Unit>? = null

    fun connect(
        identifier: String,
        host: String,
        purpose: WifiSessionPurpose = WifiSessionPurpose.Browse,
    ) {
        start(identifier, WifiRemotePhase.OpeningSession, purpose = purpose) { _, sessionScope ->
            connector.open(host.trim(), sessionScope, purpose)
        }
    }

    fun connectAutomatically(
        identifier: String,
        purpose: WifiSessionPurpose = WifiSessionPurpose.Browse,
    ) {
        val automatic = automaticConnector ?: return
        // Phone-side preconditions are the first step of the managed attempt: a disabled phone
        // Wi-Fi (or location services on API <= 32) must not wake the camera via BLE control
        // takeover and must not flash "preparing camera" first. The check runs inside the same
        // job/attempt gate as a normal start (so Back/disconnect cancel it, a repeated call is
        // ignored and a late result cannot touch a newer session). The connector re-checks as a
        // belt-and-braces guard for any caller that reaches it directly.
        val preconditions = automatic.phonePreconditions
        start(
            identifier, WifiRemotePhase.PreparingCamera, purpose = purpose,
            preflight = preconditions?.let { radio -> { checkPhonePreconditions(radio) } },
        ) { id, sessionScope ->
            automatic.open(id, sessionScope, purpose) { phase ->
                log.i { "Wi-Fi remote attempt=$attempt phase=$phase purpose=$purpose" }
                if (registry.get(id)?.wifiRemote?.phase != WifiRemotePhase.Closing) {
                    registry.updateWifiRemote(id, WifiRemoteState(phase = phase, purpose = purpose))
                }
            }
        }
    }

    /**
     * The failure the phone's radio state implies, or null when everything is satisfied. A check that
     * throws maps like the same failure inside the connector would: a typed
     * [WifiRemoteConnectException] keeps its failure, anything else is a protocol error;
     * cancellation propagates.
     */
    private suspend fun checkPhonePreconditions(radio: WifiPreconditions): WifiRemoteFailure? = try {
        when {
            !radio.isWifiEnabled() -> WifiRemoteFailure.WifiDisabled
            !radio.isLocationEnabled() -> WifiRemoteFailure.LocationServicesRequired
            else -> null
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: WifiRemoteConnectException) {
        failed.failure
    } catch (failed: Exception) {
        log.w { "Wi-Fi remote preflight failed: ${failed::class.simpleName}: ${failed.message}" }
        WifiRemoteFailure.ProtocolError
    }

    fun prepareManualConnection(
        identifier: String,
        purpose: WifiSessionPurpose = WifiSessionPurpose.Browse,
    ) {
        val setup = manualSetupConnector ?: return
        start(identifier, WifiRemotePhase.PreparingCamera, purpose = purpose) { id, sessionScope ->
            val credentials = setup.prepare(id)
                ?: throw WifiRemoteConnectException(WifiRemoteFailure.CameraSetupFailed)
            currentCoroutineContext().ensureActive()
            val joined = CompletableDeferred<Unit>()
            manualJoin = joined
            try {
                registry.updateWifiRemote(
                    id, WifiRemoteState(
                        phase = WifiRemotePhase.AwaitingManualNetwork,
                        purpose = purpose,
                        manualNetwork = credentials,
                    )
                )
                log.i { "Wi-Fi remote attempt=$attempt waiting for manual network join" }
                joined.await()
                setup.open(credentials, sessionScope, purpose)
            } finally {
                manualJoin = null
            }
        }
    }

    fun continueManualConnection(identifier: String) {
        val current = registry.get(identifier)?.wifiRemote ?: return
        if (_owner.value.equals(identifier, true) &&
            current.phase == WifiRemotePhase.AwaitingManualNetwork
        ) {
            registry.updateWifiRemote(
                identifier,
                WifiRemoteState(phase = WifiRemotePhase.OpeningSession, purpose = current.purpose)
            )
            manualJoin?.complete(Unit)
        }
    }

    /**
     * R2: Establish or switch a Wi-Fi session for [purpose].
     * - If another camera owns Wi-Fi or an accepted user download is in progress, refuses (`false`).
     * - If the same camera is already active/connecting with [purpose], reuses it (`true`).
     * - If the same camera is active/connecting with a different purpose, closes and joins the
     *   old session (discarding old frames, catalog, and handles) before starting [purpose].
     */
    suspend fun switchPurpose(
        identifier: String,
        purpose: WifiSessionPurpose,
        host: String? = null,
    ): Boolean {
        val id = identifier.uppercase()
        val currentOwner = _owner.value
        if (currentOwner != null && currentOwner != id) return false
        val current = registry.get(id)?.wifiRemote
        if (userDownloadInProgress || current?.imageTransfer?.busy == true) return false
        if (currentOwner == id && current != null &&
            current.purpose == purpose &&
            current.phase != WifiRemotePhase.Idle &&
            current.phase != WifiRemotePhase.Failed &&
            current.phase != WifiRemotePhase.Closing
        ) {
            return true
        }
        if (currentOwner == id || job != null) {
            closeAndJoin(id)
        }
        if (!isForeground) return false
        when {
            host != null -> connect(id, host, purpose)
            supportsAutomaticConnection -> connectAutomatically(id, purpose)
            supportsManualSetup -> prepareManualConnection(id, purpose)
            else -> return false
        }
        return true
    }

    /**
     * R2: Reopen only the HTTP live-view stream after a preview failure while the control channel
     * remains `Ready`.
     */
    fun retryLiveView(identifier: String) {
        val id = identifier.uppercase()
        val state = registry.get(id)?.wifiRemote ?: return
        val connection = activeConnection ?: return
        val parent = sessionScope ?: return
        if (_owner.value != id || state.phase != WifiRemotePhase.Ready ||
            !state.supportsLiveView || state.photoBrowser.open || photoJob != null ||
            (state.preview != WifiPreviewStatus.Failed && !state.liveViewPaused)
        ) return
        previewJob?.cancel()
        previewJob = null
        updateReady(id) { it.copy(preview = WifiPreviewStatus.Waiting, liveViewPaused = false) }
        startPreview(id, connection, parent)
    }

    /**
     * Starts one managed connection attempt. With a [preflight] the attempt first runs it inside the
     * job — before [phase] is published and before the BLE controls are claimed. A failing preflight
     * publishes `Failed` directly (no phase, no claim, no release); a cancelled one (disconnect, Back,
     * backgrounding) publishes nothing and starts nothing. The job/attempt gate is held for the whole
     * preflight, so a repeated start is ignored and a late result can never reach a newer session.
     */
    private fun start(identifier: String, phase: WifiRemotePhase,
                      purpose: WifiSessionPurpose = WifiSessionPurpose.Browse,
                      preflight: (suspend () -> WifiRemoteFailure?)? = null,
                      open: suspend (String, CoroutineScope) -> WifiRemoteConnection) {
        if (job != null) {
            log.w { "Wi-Fi remote connect ignored: previous attempt=$attempt is still active or closing" }
            return
        }
        // A connection is user-initiated from a visible activity; stale background callbacks
        // (for example a permission result) cannot silently start a new network request.
        if (!isForeground) return
        cancelBackgroundDownloadTimer()
        attempt++
        val thisAttempt = attempt
        log.i { "Wi-Fi remote attempt=$attempt starting phase=$phase purpose=$purpose" }
        val id = identifier.uppercase()
        if (registry.get(id) == null) registry.upsert(id) { it.copy(phase = BleSessionPhase.Disconnected) }
        _owner.value = id
        _image.value = null
        _liveViewFrame.value = null
        if (preflight == null) registry.updateWifiRemote(id, WifiRemoteState(phase = phase, purpose = purpose))
        preflightActive = preflight != null
        var entered = false
        var preflightFailure: WifiRemoteFailure? = null
        val connecting = scope.launch(start = CoroutineStart.LAZY) {
            if (preflight != null) {
                val failure = preflight()
                // A result that arrives after cancel/disconnect or for an older attempt is dropped.
                currentCoroutineContext().ensureActive()
                if (attempt != thisAttempt) return@launch
                preflightActive = false
                if (failure != null) {
                    log.i { "Wi-Fi remote attempt=$thisAttempt skipped by preflight: $failure" }
                    preflightFailure = failure
                    return@launch
                }
                registry.updateWifiRemote(id, WifiRemoteState(phase = phase, purpose = purpose))
            }
            entered = true
            runSession(id, purpose, open)
        }
        job = connecting
        connecting.invokeOnCompletion {
            if (!entered && job === connecting) {
                preflightActive = false
                val failure = preflightFailure
                registry.updateWifiRemote(
                    id, if (failure == null) WifiRemoteState()
                    else WifiRemoteState(WifiRemotePhase.Failed, failure, purpose = purpose)
                )
                _owner.value = null
                job = null
            }
        }
        connecting.start()
    }

    private var propertyProbe: Job? = null
    private var propertyJob: Job? = null
    private var actionJob: Job? = null

    fun probeProperties(identifier: String) {
        val id = identifier.uppercase()
        val state = registry.get(id)?.wifiRemote ?: return
        if (_owner.value != id || state.phase != WifiRemotePhase.Ready || state.photoBrowser.open ||
            state.capture == WifiCaptureStatus.Shooting || propertyProbe?.isActive == true) return
        val connection = activeConnection ?: return
        val parent = sessionScope ?: return
        updateReady(id) { it.copy(propertyProbe = PropertyProbeStatus.Reading, probeAttempt = it.probeAttempt + 1) }
        propertyProbe = parent.launch {
            val outcome = try {
                connection.probeProperties()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PropertyProbeStatus.Failed
            }
            val parsed = if (outcome == PropertyProbeStatus.Recorded) {
                try { connection.readShootingProperties() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            } else null
            if (activeConnection === connection) {
                updateReady(id) {
                    it.copy(
                        propertyProbe = outcome,
                        cameraProperties = if (parsed != null) {
                            it.cameraProperties.copy(properties = parsed, loading = false)
                        } else it.cameraProperties,
                    )
                }
            }
        }
    }

    /** Refreshes the live shooting-property snapshot for the Wi-Fi remote UI. */
    fun refreshProperties(identifier: String) {
        val id = identifier.uppercase()
        val state = registry.get(id)?.wifiRemote ?: return
        if (_owner.value != id || state.phase != WifiRemotePhase.Ready || state.photoBrowser.open ||
            state.capture == WifiCaptureStatus.Shooting || propertyJob?.isActive == true
        ) return
        val connection = activeConnection ?: return
        val parent = sessionScope ?: return
        if (!connection.canReadProperties) return
        updateReady(id) { it.copy(cameraProperties = it.cameraProperties.copy(loading = true)) }
        propertyJob = parent.launch {
            val parsed = try {
                connection.readShootingProperties()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (activeConnection === connection) {
                updateReady(id) {
                    it.copy(
                        cameraProperties = it.cameraProperties.copy(
                            properties = parsed ?: it.cameraProperties.properties,
                            loading = false,
                        ),
                    )
                }
            }
        }
    }

    /** Writes one shooting property over `0x9205` and re-reads all properties from the camera. */
    fun setCameraProperty(identifier: String, key: CameraShootingPropertyKey, rawValue: Long) {
        val id = identifier.uppercase()
        val state = registry.get(id)?.wifiRemote ?: return
        if (_owner.value != id || state.phase != WifiRemotePhase.Ready || state.photoBrowser.open ||
            state.capture == WifiCaptureStatus.Shooting || propertyJob?.isActive == true
        ) return
        val prop = state.cameraProperties.byKey(key) ?: return
        if (!prop.writable) return
        val connection = activeConnection ?: return
        val parent = sessionScope ?: return
        updateReady(id) {
            it.copy(
                cameraProperties = it.cameraProperties.copy(
                    writingKey = key,
                    writeStatus = PropertyWriteStatus.Writing,
                ),
            )
        }
        propertyJob = parent.launch {
            val status = try {
                connection.setShootingProperty(key, prop.dataType, rawValue)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PropertyWriteStatus.Failed
            }
            val refreshed = try {
                connection.readShootingProperties()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (activeConnection === connection) {
                updateReady(id) {
                    it.copy(
                        cameraProperties = it.cameraProperties.copy(
                            properties = refreshed ?: it.cameraProperties.properties,
                            loading = false,
                            writingKey = null,
                            writeStatus = status,
                        ),
                    )
                }
            }
        }
    }

    fun clearPropertyWriteStatus(identifier: String) {
        val id = identifier.uppercase()
        if (_owner.value != id) return
        updateReady(id) {
            it.copy(cameraProperties = it.cameraProperties.copy(writeStatus = PropertyWriteStatus.Idle))
        }
    }

    /** Standalone half-press AF focus trigger (`0xD2C1`). */
    fun focus(identifier: String) {
        val id = identifier.uppercase()
        val state = registry.get(id)?.wifiRemote ?: return
        if (_owner.value != id || state.phase != WifiRemotePhase.Ready || !state.canFocus ||
            state.capture == WifiCaptureStatus.Shooting || state.photoBrowser.open ||
            photoJob != null || actionJob?.isActive == true
        ) return
        val connection = activeConnection ?: return
        val parent = sessionScope ?: return
        updateReady(id) { it.copy(focusStatus = RemoteActionStatus.Running) }
        actionJob = parent.launch {
            val result = try {
                connection.focus()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RemoteActionStatus.Uncertain
            }
            if (activeConnection === connection) {
                updateReady(id) { it.copy(focusStatus = result) }
                if (result == RemoteActionStatus.Done) {
                    delay(2.seconds)
                    if (activeConnection === connection) {
                        updateReady(id) {
                            if (it.focusStatus == RemoteActionStatus.Done) it.copy(focusStatus = RemoteActionStatus.Idle)
                            else it
                        }
                    }
                }
            }
        }
    }

    /** Toggles movie recording (`0xD2C8`). */
    fun toggleMovieRecord(identifier: String) {
        val id = identifier.uppercase()
        val state = registry.get(id)?.wifiRemote ?: return
        if (_owner.value != id || state.phase != WifiRemotePhase.Ready || !state.canRecordMovie ||
            state.capture == WifiCaptureStatus.Shooting || state.photoBrowser.open ||
            photoJob != null || actionJob?.isActive == true
        ) return
        val connection = activeConnection ?: return
        val parent = sessionScope ?: return
        updateReady(id) { it.copy(movieActionStatus = RemoteActionStatus.Running) }
        actionJob = parent.launch {
            val result = try {
                connection.toggleMovieRecord()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RemoteActionStatus.Uncertain
            }
            if (activeConnection === connection) {
                updateReady(id) { it.copy(movieActionStatus = result) }
                if (result == RemoteActionStatus.Done) {
                    delay(2.seconds)
                    if (activeConnection === connection) {
                        updateReady(id) {
                            if (it.movieActionStatus == RemoteActionStatus.Done) it.copy(movieActionStatus = RemoteActionStatus.Idle)
                            else it
                        }
                    }
                }
            }
        }
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

    /**
     * Date-grouped entry (a new catalog generation, like a refresh): enumerates the whole card and
     * publishes its days. No page is shown and no thumbnail is requested until [browseDate] (or
     * [requestDateCovers] for visible cover rows); a failure leaves `failed` set so the same call
     * can simply be retried.
     */
    fun browseDates(identifier: String) {
        val generation = ++browserGeneration
        photoWork(identifier, { state ->
            state.copy(
                photoBrowser = state.photoBrowser.copy(
                    open = true, loading = true, failed = false, browserGeneration = generation,
                    photos = emptyList(), totalObjects = 0,
                    savedDownloads = emptySet(), dates = emptyList(), dateKey = null
                )
            )
        }) { id, connection ->
            clearPhotoPreview(id)
            previewJob?.cancelAndJoin()
            previewJob = null
            _image.value = null
            _liveViewFrame.value = null
            thumbJob?.cancelAndJoin()
            thumbJob = null
            dateCoverJob?.cancelAndJoin()
            dateCoverJob = null
            coverDemand.value = CoverDemand(-1L, emptyList())
            settledCoverHandles.clear()
            photoCatalog = emptyMap()
            _thumbnails.value = emptyMap()
            _dateCoverThumbnails.value = emptyMap()
            updateReady(id) { it.copy(hasLiveView = false, liveViewPaused = false) }
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
     * Shows the whole day [dateKey] (no paging) from the already enumerated catalog. Never opens a new
     * generation: selections, saved markers and the handle index survive switching days, and the
     * camera is not asked to enumerate again. Every call publishes a new `pageRevision`, which is the
     * identity of this view of the day: thumbnail demand and range gestures of an earlier view are
     * ignored. [preferredPhotoIndex] (day index) names a capture whose thumbnail is wanted first.
     */
    fun browseDate(
        identifier: String, dateKey: String, preferredPhotoIndex: Int? = null,
        expectedRevision: Long? = null,
    ) {
        val browser = registry.get(identifier.uppercase())?.wifiRemote?.photoBrowser ?: return
        if (!browser.open || browser.dates.none { it.key == dateKey }) return
        // A request made against an earlier view of the grid (A -> B -> A) must not replace this one.
        if (expectedRevision != null && expectedRevision != browser.pageRevision) return
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
            _liveViewFrame.value = null
            thumbJob?.cancelAndJoin()
            thumbJob = null
            dateCoverJob?.cancelAndJoin()
            dateCoverJob = null
            coverDemand.value = CoverDemand(-1L, emptyList())
            thumbDemand.value = ThumbnailDemand(-1, emptyList(), emptyList())
            val page = connection.dayPhotos(dateKey)
            _thumbnails.value = emptyMap()
            photoCatalog += page.photos.associateBy { it.handle }
            val revision = (registry.get(id)?.wifiRemote?.photoBrowser?.pageRevision ?: 0L) + 1
            updateReady(id) {
                it.copy(
                    photoBrowser = it.photoBrowser.copy(
                        photos = page.photos, totalObjects = page.totalObjects,
                        dateKey = dateKey, pageRevision = revision
                    )
                )
            }
            val captures = groupCameraPhotos(page.photos)
            // Nothing is fetched until the grid reports its viewport (see requestThumbnails); a viewer
            // crossing into this page names the capture it is about to show instead.
            preferredPhotoIndex?.let { captures.getOrNull(it) }?.let { shown ->
                thumbDemand.value = ThumbnailDemand(revision, listOf(shown.id), emptyList())
            }
            thumbJob = sessionScope?.launch {
                loadThumbnailsOnDemand(connection, page.offset, revision, captures)
            }
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
            browser.dateKey == null || browser.loading || photoJob != null ||
            state.imageTransfer.busy
        ) return
        clearPhotoPreview(id)
        thumbJob?.cancel()
        _thumbnails.value = emptyMap()
        updateReady(id) {
            it.copy(
                photoBrowser = it.photoBrowser.copy(
                    photos = emptyList(), dateKey = null,
                    totalObjects = it.photoBrowser.dates.sumOf { group -> group.count },
                    pageRevision = it.photoBrowser.pageRevision + 1
                )
            )
        }
    }

    private fun holdThumbnails() = thumbnailHolds.update { it + 1 }
    private fun releaseThumbnails() = thumbnailHolds.update { (it - 1).coerceAtLeast(0) }

    private class ThumbnailFetch(val bytes: Long, val ms: Long)

    /**
     * One thumbnail request, published into [thumbnails] on success. A timeout, failure or missing
     * thumbnail is logged and yields null (it never fails the page); real cancellation propagates.
     */
    private suspend fun fetchThumbnail(
        connection: WifiRemoteConnection, pageOffset: Int, index: Int, photo: WifiCameraPhoto,
        pageStats: ThumbnailPageStats
    ): ThumbnailFetch? {
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
        } ?: return null
        val bitmapEstBytes = bitmap.width.toLong() * bitmap.height * 4
        pageStats.record(ThumbnailSample(itemMs, fetchedBytes, bitmapEstBytes))
        thumbLog.d {
            "page=$pageOffset #$index handle=${photo.handle} cid=${photo.captureId ?: "-"} " +
                    "bytes=$fetchedBytes reqMs=$itemMs bitmap=${bitmap.width}x${bitmap.height} " +
                    "estBytes=$bitmapEstBytes"
        }
        _thumbnails.value += photo.handle to bitmap
        // Fire-and-forget: the pixel scan runs away from this loader so it can never delay the
        // next thumbnail, and nothing ever waits for it (it runs on a worker thread, so a loader that
        // had to join it would be held up by real time). It is not orphaned either: every cancel of a
        // loader (day switch, refresh, disconnect) and the end of the session cancel the scans still
        // pending via [cancelDiagnostics]; the log line is the only side effect.
        if (index < 3) {
            diagnosticJobs.removeAll { it.isCompleted }
            diagnosticJobs += diagnosticsScope.launch {
                logLetterboxDiagnostics(pageOffset, index, photo.handle, bitmap)
            }
        }
        return ThumbnailFetch(fetchedBytes, itemMs)
    }

    /**
     * "AlphaThumb" diagnostics for the first three thumbnails of a page: decoded size plus the
     * near-black edge lines measured off the composition thread (`diagnosticsDispatcher`); the scan
     * only produces a log line, never touches what is displayed. It checks for cancellation between
     * lines and never fails its loader: a real failure is logged with its class and message.
     */
    private suspend fun logLetterboxDiagnostics(pageOffset: Int, index: Int, handle: Long, bitmap: ImageBitmap) {
        val context = currentCoroutineContext()
        val bars = try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.readPixels(pixels)
            yield()
            letterboxScan(pixels, bitmap.width, bitmap.height) { context.ensureActive() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            thumbLog.w {
                "page=$pageOffset #$index handle=$handle letterbox scan failed: " +
                        "${failure::class.simpleName}: ${failure.message}"
            }
            null
        }
        thumbLog.i {
            "page=$pageOffset #$index handle=$handle decoded=${bitmap.width}x${bitmap.height} " +
                    "nearBlackBars=${bars ?: "unavailable"}"
        }
    }

    /**
     * The grid reports which captures (stable keys) are on screen and which neighbours deserve a
     * margin. Latest report wins (conflated); a report for another page revision is ignored by the
     * loader, so a late layout of an earlier page can never steer the current one.
     */
    fun requestThumbnails(identifier: String, pageRevision: Long, visible: List<String>, margin: List<String>) {
        if (_owner.value.equals(identifier, true)) {
            thumbDemand.value = ThumbnailDemand(pageRevision, visible, margin)
        }
    }

    /**
     * Reports the cover handles currently visible in the date list. Lazily requests at most one
     * thumbnail per day per catalog generation, serialized behind [thumbnailHolds] and guarded by
     * [browserGeneration] so stale responses never overwrite a refreshed or disconnected session.
     */
    fun requestDateCovers(identifier: String, generation: Long, visibleHandles: List<Long>) {
        val id = identifier.uppercase()
        if (_owner.value != id) return
        val state = registry.get(id)?.wifiRemote ?: return
        val browser = state.photoBrowser
        if (state.phase != WifiRemotePhase.Ready || !browser.open ||
            browser.dateKey != null || generation != browserGeneration
        ) return
        val distinct = visibleHandles.distinct()
        coverDemand.value = CoverDemand(generation, distinct)
        if (dateCoverJob?.isActive == true) return
        val hasPending = distinct.any { it !in settledCoverHandles && it !in _dateCoverThumbnails.value }
        if (!hasPending) return
        val connection = activeConnection ?: return
        dateCoverJob = sessionScope?.launch { loadDateCovers(connection, id, generation) }
    }

    private suspend fun loadDateCovers(
        connection: WifiRemoteConnection,
        id: String,
        generation: Long,
    ) {
        while (true) {
            val demand = coverDemand.value
            if (demand.generation != generation || generation != browserGeneration) break
            val next = demand.handles.firstOrNull {
                it !in settledCoverHandles && it !in _dateCoverThumbnails.value
            }
            if (next == null) {
                val seen = demand
                val updated = coverDemand.first { it != seen }
                if (updated.generation != generation || generation != browserGeneration) break
                continue
            }
            thumbnailHolds.first { it == 0 }
            currentCoroutineContext().ensureActive()
            val current = coverDemand.value
            if (current.generation != generation || generation != browserGeneration) break
            if (next !in current.handles) continue
            if (next in settledCoverHandles || next in _dateCoverThumbnails.value) continue
            settledCoverHandles += next
            val bitmap = try {
                connection.photoThumbnail(next)
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                thumbLog.w { "cover handle=$next timed out" }
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                thumbLog.w { "cover handle=$next failed: ${failure.message}" }
                null
            } ?: continue
            currentCoroutineContext().ensureActive()
            if (generation == browserGeneration && _owner.value == id) {
                _dateCoverThumbnails.value += next to bitmap
            }
        }
    }

    /**
     * Demand-driven loader of one date page (the grouped browser). It sleeps without any camera
     * traffic until the grid reports what is visible, then serves visible captures first and their
     * margin second, at most [THUMBNAIL_BATCH_SIZE] per batch. Before every request it re-checks that
     * the target is still wanted and not yet cached; the request in flight is never cancelled because
     * the viewport moved. Fetched or failed captures are settled for this page: failures are not
     * retried until the page is loaded again. [thumbnailsLoading] is true while the current demand
     * still has pending captures (also while paused by a hold), never for far-away tiles.
     */
    private suspend fun loadThumbnailsOnDemand(
        connection: WifiRemoteConnection, pageOffset: Int, revision: Long,
        captures: List<WifiCameraCapture>
    ) {
        val indexOf = captures.withIndex().associate { it.value.id to it.index }
        val indexOfHandle = captures.withIndex().associate { it.value.thumbnail.handle to it.index }
        val settled = HashSet<Int>()
        val pageStats = ThumbnailPageStats(pageOffset, captures.size)
        var batchNumber = 0
        fun window(demand: ThumbnailDemand): Pair<List<Int>, List<Int>> =
            if (demand.pageRevision != revision) emptyList<Int>() to emptyList()
            else demand.visible.mapNotNull { indexOf[it] } to demand.margin.mapNotNull { indexOf[it] }

        fun pick(demand: ThumbnailDemand): List<Int> =
            window(demand).let { (visible, margin) -> demandedThumbnailBatch(visible, margin, settled) }
        try {
            while (true) {
                var demand = thumbDemand.value
                var batch = pick(demand)
                while (batch.isEmpty()) {
                    _thumbnailsLoading.value = false
                    if (settled.size == captures.size) pageStats.takeSummary()?.let { line -> thumbLog.i { line } }
                    val seen = demand
                    demand = thumbDemand.first { it != seen }
                    batch = pick(demand)
                }
                _thumbnailsLoading.value = true
                batchNumber++
                val started = TimeSource.Monotonic.markNow()
                var fetched = 0
                var requestMs = 0L
                var bytes = 0L
                for (index in batch) {
                    thumbnailHolds.first { it == 0 }
                    val wanted = window(thumbDemand.value).let { (visible, margin) ->
                        index in visible || index in margin
                    }
                    if (!wanted) continue
                    val photo = captures[index].thumbnail
                    settled += index
                    if (photo.handle in _thumbnails.value) continue
                    val fetch = fetchThumbnail(connection, pageOffset, index, photo, pageStats) ?: continue
                    fetched++
                    requestMs += fetch.ms
                    bytes += fetch.bytes
                    evictFarThumbnails(captures, indexOfHandle, settled, window(thumbDemand.value))
                }
                thumbLog.i {
                    thumbnailBatchLine(
                        pageOffset, batchNumber, fetched, batch.size, requestMs,
                        started.elapsedNow().inWholeMilliseconds, bytes
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            cancelDiagnostics()
            pageStats.takeCancelled()?.let { line -> thumbLog.i { line } }
            throw cancelled
        } finally {
            _thumbnailsLoading.value = false
        }
    }

    /**
     * Keeps the decoded thumbnails of an (unpaged, possibly long) day bounded: beyond
     * [THUMBNAIL_CACHE_LIMIT] the oldest ones outside the current demand are dropped and un-settled,
     * so scrolling back to them asks for them again instead of showing a permanent placeholder.
     */
    private fun evictFarThumbnails(
        captures: List<WifiCameraCapture>, indexOfHandle: Map<Long, Int>, settled: MutableSet<Int>,
        window: Pair<List<Int>, List<Int>>,
    ) {
        val keep = (window.first + window.second).mapNotNull { captures.getOrNull(it)?.thumbnail?.handle }.toSet()
        val evicted = thumbnailsToEvict(_thumbnails.value, keep)
        if (evicted.isEmpty()) return
        _thumbnails.value -= evicted.toSet()
        evicted.forEach { handle -> indexOfHandle[handle]?.let(settled::remove) }
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

    /**
     * Starts reading the metadata of [handle] (a photo of the displayed page) and publishes it on
     * [metadata]. Call on viewer entry and after every swipe; the previous unfinished read is
     * cancelled. With [extended] false only the free basic data is produced; with true, JPEGs get
     * their shooting parameters after a 250 ms debounce. Never affects preview or download.
     */
    fun loadMetadata(identifier: String, handle: Long, extended: Boolean = false) {
        val id = identifier.uppercase()
        val state = registry.get(id)?.wifiRemote ?: return
        if (_owner.value != id || state.phase != WifiRemotePhase.Ready || !state.photoBrowser.open) return
        metadataLoader.request(handle, browserGeneration, extended)
    }

    /** Stops any pending read and resets [metadata] to [PhotoMetadataState.Idle] (viewer closed). */
    fun cancelMetadata(identifier: String) {
        if (_owner.value.equals(identifier, true)) metadataLoader.cancel()
    }

    /**
     * Stops a pending / in-flight extended read without forgetting what is shown (a pending read
     * falls back to the basic data): the viewer's pager started moving away from the photo. The
     * next [loadMetadata] (the settled page) asks again; cached results are kept.
     */
    fun pauseMetadata(identifier: String) {
        if (_owner.value.equals(identifier, true)) metadataLoader.interrupt()
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
            }, userDownload = true) { id, connection ->
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
            dateCoverJob?.cancelAndJoin()
            dateCoverJob = null
            coverDemand.value = CoverDemand(-1L, emptyList())
            settledCoverHandles.clear()
            connection.closePhotoBrowser()
            photoCatalog = emptyMap()
            _thumbnails.value = emptyMap()
            _dateCoverThumbnails.value = emptyMap()
            updateReady(id) {
                it.copy(
                    photoBrowser = WifiPhotoBrowserState(
                        savedDownloads =
                            if (connection.photoHandlesSurviveModeSwitch) it.photoBrowser.savedDownloads else emptySet()
                    ),
                    canCaptureStill = connection.canCapture,
                    canTransferImages = connection.canTransferImages,
                    supportsLiveView = connection.hasLiveViewStream,
                    liveViewPaused = false,
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
        userDownload: Boolean = false,
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
        metadataLoader.interrupt()
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
        if (userDownload) userDownloadInProgress = true
        work.invokeOnCompletion {
            val completedDownload = photoJob === work && userDownload
            if (photoJob === work) {
                photoJob = null
                if (userDownload) userDownloadInProgress = false
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
            if (completedDownload) applyBackgroundPolicy()
        }
        work.start()
    }

    fun disconnect(identifier: String? = null) {
        val id = _owner.value ?: return
        if (identifier != null && !id.equals(identifier, true)) return
        cancelBackgroundDownloadTimer()
        log.i { "Wi-Fi remote attempt=$attempt disconnect requested" }
        // Nothing exists to close while only the phone preflight runs: cancelling it publishes nothing.
        if (!preflightActive) registry.updateWifiRemote(id, WifiRemoteState(phase = WifiRemotePhase.Closing))
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

    private suspend fun runSession(
        id: String,
        purpose: WifiSessionPurpose,
        open: suspend (String, CoroutineScope) -> WifiRemoteConnection,
    ) {
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
                registry.updateWifiRemote(
                    id,
                    WifiRemoteState(
                        phase = WifiRemotePhase.Ready,
                        purpose = purpose,
                        canCaptureStill = opened.canCapture,
                        canFocus = opened.canFocus,
                        canRecordMovie = opened.canRecordMovie,
                        supportsLiveView = opened.hasLiveViewStream,
                        cameraName = opened.cameraName,
                        canTransferImages = opened.canTransferImages,
                        canConvertHeif = opened.canConvertHeif,
                        cameraProperties = CameraPropertiesState(canWrite = opened.canWriteProperties),
                    ),
                )
                launch {
                    opened.lost.first()
                    throw WifiRemoteConnectException(WifiRemoteFailure.NetworkLost)
                }
                sessionScope = this
                activeConnection = opened
                launch {
                    val percent = try { opened.readBatteryPercent() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                    if (percent != null) onBatteryRead(id, percent)
                }
                if (purpose == WifiSessionPurpose.RemoteControl && opened.canReadProperties) {
                    refreshProperties(id)
                    launch {
                        var debounceJob: Job? = null
                        opened.propertyChangedEvents.collect {
                            debounceJob?.cancel()
                            debounceJob = launch {
                                delay(300)
                                refreshProperties(id)
                            }
                        }
                    }
                }
                startPreview(id, opened, this)
                launch(start = CoroutineStart.UNDISPATCHED) {
                    var confirmationTimer: Job? = null
                    for (ignored in requests) {
                        confirmationTimer?.cancel()
                        val result = try { opened.capture() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { WifiCaptureStatus.Uncertain }
                        updateReady(id) { it.copy(capture = result) }
                        if (opened.canReadProperties) refreshProperties(id)
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
            cancelDiagnostics()
            sessionScope = null
            activeConnection = null
            previewJob = null
            propertyProbe = null
            propertyJob = null
            actionJob = null
            photoJob = null
            userDownloadInProgress = false
            cancelBackgroundDownloadTimer()
            thumbJob = null
            dateCoverJob = null
            coverDemand.value = CoverDemand(-1L, emptyList())
            settledCoverHandles.clear()
            _thumbnailsLoading.value = false
            thumbnailHolds.value = 0
            photoPreviewJob = null
            photoCatalog = emptyMap()
            metadataLoader.clear()
            _photoPreview.value = null
            _thumbnails.value = emptyMap()
            _dateCoverThumbnails.value = emptyMap()
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
                        _liveViewFrame.value = null
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
            log.i { "Wi-Fi live view not used for this connection (photo transfer only)" }
            updateReady(id) {
                it.copy(
                    hasLiveView = false,
                    supportsLiveView = false,
                    liveViewPaused = false,
                    preview = WifiPreviewStatus.Unavailable,
                )
            }
            return
        }
        previewJob = parent.launch {
            var receivedFrame = false
            var staleWatchdog: Job? = null
            log.i { "Wi-Fi live view starting" }
            try {
                connection.liveViewFrames.collect { frame ->
                    if (!receivedFrame) {
                        log.i { "Wi-Fi live view first image ${frame.image.width}x${frame.image.height}" }
                        receivedFrame = true
                    }
                    staleWatchdog?.cancel()
                    staleWatchdog = launch {
                        delay(liveViewStaleTimeoutMs)
                        updateReady(id) {
                            if (it.preview == WifiPreviewStatus.Streaming) it.copy(liveViewPaused = true)
                            else it
                        }
                    }
                    _image.value = frame.image
                    _liveViewFrame.value = frame
                    updateReady(id) {
                        it.copy(
                            hasLiveView = true,
                            supportsLiveView = true,
                            liveViewPaused = false,
                            preview = WifiPreviewStatus.Streaming,
                        )
                    }
                }
                updateReady(id) { it.copy(hasLiveView = false, liveViewPaused = false, preview = WifiPreviewStatus.Failed) }
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                log.w { "Wi-Fi live view timed out: ${cancelled.message}" }
                updateReady(id) { it.copy(hasLiveView = false, liveViewPaused = false, preview = WifiPreviewStatus.Failed) }
            } catch (failure: Exception) {
                log.w { "Wi-Fi live view failed: ${failure.message}" }
                updateReady(id) { it.copy(hasLiveView = false, liveViewPaused = false, preview = WifiPreviewStatus.Failed) }
            } finally {
                staleWatchdog?.cancel()
                log.i { "Wi-Fi live view stopped; receivedFrame=$receivedFrame" }
                _image.value = null
                _liveViewFrame.value = null
            }
        }
    }

    private fun updateReady(id: String, update: (WifiRemoteState) -> WifiRemoteState) {
        val state = registry.get(id)?.wifiRemote ?: return
        if (state.phase == WifiRemotePhase.Ready) registry.updateWifiRemote(id, update(state))
    }
}
