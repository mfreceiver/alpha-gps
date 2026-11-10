package com.sasch.cameragps.sharednew.remote.wifi

import androidx.compose.ui.graphics.ImageBitmap
import com.diamondedge.logging.logging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/** The platform supplies network mechanics; Sony session behavior is shared. */
internal suspend fun openSonyWifiSession(
    connections: PtpIpConnectionFactory,
    scope: CoroutineScope,
    host: String,
    http: SonyLiveViewHttpTransport,
    imageStore: CameraImageStore,
    decodeThumbnail: (ByteArray) -> ImageBitmap?,
    decodePreview: (ByteArray) -> ImageBitmap?,
    networkLost: Flow<Unit> = emptyFlow(),
    releaseNetwork: () -> Unit = {},
    browseOnly: Boolean = false,
): WifiRemoteConnection {
    val connection = SonyWifiConnection(
        connections, scope, host, http, imageStore,
        decodeThumbnail, decodePreview, networkLost, releaseNetwork, browseOnly
    )
    try {
        connection.open()
        return connection
    } catch (failure: Throwable) {
        withContext(NonCancellable) { connection.close() }
        throw failure
    }
}

/** Main-confined. A mode change owns both PTP channels, but keeps the camera network lease. */
private class SonyWifiConnection(
    private val connections: PtpIpConnectionFactory,
    scope: CoroutineScope,
    private val host: String,
    private val http: SonyLiveViewHttpTransport,
    private val store: CameraImageStore,
    private val decodeThumbnail: (ByteArray) -> ImageBitmap?,
    private val decodePreview: (ByteArray) -> ImageBitmap?,
    networkLost: Flow<Unit>,
    private val releaseNetwork: () -> Unit,
    /** When set, the session starts (and stays) in ImageTransfer mode: no remote shooting, no live view. */
    private val browseOnly: Boolean = false,
) : WifiRemoteConnection {
    private val log = logging("SonyWifiSession")

    // Kept alive through caller cancellation so CloseSession can still use its command queue.
    private val protocolScope = CoroutineScope(scope.coroutineContext + SupervisorJob())
    private val clientGuid = Random.nextBytes(16)
    private val disconnected = MutableStateFlow(false)
    private var current: Session? = null
    private var lossMonitor: Job? = null
    private var separateTransfer = false
    private var closed = false
    private var name = ""

    private inner class Session(
        val ptp: PtpIpOpenedSession,
        val ready: SonyPtpInitializationResult.Ready,
        val scope: CoroutineScope,
    ) {
        val transfer = SonyImageTransfer(
            ptp.commands,
            ready,
            ptp.events,
            decodeThumbnail,
            decodePreview,
            store
        )
        val shutter = SonyPtpShutter(ptp.commands, ready, ptp.events)
        val preview = SonyLiveViewStream(ptp.commands, ready, ptp.events, http)
        val endpoint = SonyLiveViewEndpointResolver(ptp.commands, ready)

        suspend fun close() {
            try {
                ptp.close()
            } finally {
                scope.cancel()
            }
        }
    }

    suspend fun open() {
        // Browse-only skips the remote-shooting protocol entirely: open straight into
        // transfer mode so no live-view stream or shutter session is ever negotiated.
        replaceSession(if (browseOnly) SonySessionPurpose.ImageTransfer else SonySessionPurpose.RemoteShooting)
        val session = checkNotNull(current)
        name = session.ptp.camera.cameraName
        separateTransfer = session.ready.supportsSeparateTransferSession
    }

    override val cameraName: String get() = name
    override val canTransferImages: Boolean get() = separateTransfer || current?.transfer?.supported == true
    override val canConvertHeif: Boolean get() = store.canConvertHeif
    override val photoHandlesSurviveModeSwitch: Boolean get() = !separateTransfer
    override val canCapture: Boolean
        get() = current?.ready?.let { ready ->
            ready.purpose == SonySessionPurpose.RemoteShooting &&
                    ready.deviceInfo.supports(SonyPtpOperation.SDIO_CONTROL_DEVICE) &&
                    ready.extendedInfo.supportsControl(SonyPtpControlCode.HALF_PRESS) &&
                    ready.extendedInfo.supportsControl(SonyPtpControlCode.FULL_PRESS)
        } == true
    override val lost: Flow<Unit> = merge(networkLost, disconnected.filter { it }.map { })

    private suspend fun enterTransferMode() {
        if (separateTransfer && current?.ready?.purpose != SonySessionPurpose.ImageTransfer) {
            replaceSession(SonySessionPurpose.ImageTransfer)
        }
    }

    override suspend fun openPhotoBrowser(): CameraPhotoPage {
        enterTransferMode()
        return checkNotNull(current).transfer.openBrowser()
    }

    override suspend fun openCatalog(): List<DateGroup> {
        enterTransferMode()
        return checkNotNull(current).transfer.openCatalog()
    }

    override suspend fun dayPhotos(dateKey: String) =
        checkNotNull(current).transfer.dayPhotos(dateKey)

    override suspend fun photoPage(offset: Int) = checkNotNull(current).transfer.page(offset)
    override suspend fun photoThumbnail(handle: Long, stats: ThumbnailStatsSink?) =
        checkNotNull(current).transfer.thumbnail(handle, stats)

    override suspend fun photoPreview(handle: Long) = checkNotNull(current).transfer.preview(handle)
    override suspend fun photoMetadataHead(handle: Long, length: Int) =
        current?.transfer?.takeIf { it.supported }?.metadataHead(handle, length)
    override suspend fun downloadPhoto(
        handle: Long, format: WifiPhotoDownloadFormat,
        onProgress: (WifiImageTransferState) -> Unit
    ) =
        checkNotNull(current).transfer.download(handle, format, onProgress)

    override suspend fun closePhotoBrowser() {
        current?.transfer?.closeBrowser()
        // Browse-only never leaves ImageTransfer mode; only the full remote session
        // returns to remote shooting when the browser closes.
        if (!browseOnly && separateTransfer && current?.ready?.purpose != SonySessionPurpose.RemoteShooting) {
            replaceSession(SonySessionPurpose.RemoteShooting)
        }
    }

    override suspend fun readBatteryPercent(): Int? {
        val session = current ?: return null
        return SonyBatteryReader(session.ptp.commands, session.ready).read()
    }

    override suspend fun prepareToDisconnect() {
        current?.transfer?.closeBrowser()
    }

    override val hasLiveViewStream: Boolean get() = !browseOnly

    override val images = flow {
        // Browse-only sessions never start the live-view HTTP stream, so this flow
        // completes immediately. Callers should consult [hasLiveViewStream] and not
        // collect it in that case; the early return stays as a safe fallback for
        // any collector that does (a normal completion upstream must not be read
        // as a live-view failure).
        if (browseOnly) return@flow
        val session = checkNotNull(current)
        check(session.ready.purpose == SonySessionPurpose.RemoteShooting)
        log.i { "Wi-Fi live view resolving camera endpoint" }
        emitAll(session.preview.images(session.endpoint.resolve(host)))
    }

    override suspend fun capture(): WifiCaptureStatus {
        if (!canCapture) return WifiCaptureStatus.Rejected
        return when (checkNotNull(current).shutter.captureStill()) {
            SonyPtpCaptureResult.CaptureEventObserved -> WifiCaptureStatus.Captured
            is SonyPtpCaptureResult.Rejected, SonyPtpCaptureResult.Unsupported -> WifiCaptureStatus.Rejected
            else -> WifiCaptureStatus.Uncertain
        }
    }

    override suspend fun turnOffWifi(): WifiShutdownStatus = current?.let {
        SonyWifiShutdown(it.ptp.commands, it.ready).request()
    } ?: WifiShutdownStatus.Unavailable

    private suspend fun replaceSession(purpose: SonySessionPurpose) {
        check(!closed)
        log.i { "Wi-Fi PTP switching to $purpose" }
        // Detach before closing: an intentional EOF must not tear down the network owner.
        val previous = current
        current = null
        withContext(NonCancellable) {
            try {
                lossMonitor?.cancelAndJoin()
            } finally {
                lossMonitor = null
                previous?.close()
            }
        }
        currentCoroutineContext().ensureActive()
        val sessionScope =
            CoroutineScope(protocolScope.coroutineContext + SupervisorJob(protocolScope.coroutineContext[Job]))
        var opened: PtpIpOpenedSession? = null
        try {
            val session = withTimeout(45.seconds) {
                val ptp =
                    PtpIpSessionOpener(connections, sessionScope).open(clientGuid, "Alpha GPS")
                        ?: throw WifiRemoteConnectException(WifiRemoteFailure.CameraUnavailable)
                opened = ptp
                val ready = when (val init = ptp.initializeSony(purpose)) {
                    is SonyPtpInitializationResult.Ready -> init
                    is SonyPtpInitializationResult.Rejected -> {
                        log.w { "Wi-Fi Sony initialization rejected: $init" }
                        throw WifiRemoteConnectException(WifiRemoteFailure.CameraRefused)
                    }

                    else -> {
                        log.w { "Wi-Fi Sony initialization failed: $init" }
                        throw WifiRemoteConnectException(WifiRemoteFailure.ProtocolError)
                    }
                }
                log.i { "Wi-Fi Sony initialized: $ready" }
                Session(ptp, ready, sessionScope)
            }
            current = session
            lossMonitor = protocolScope.launch(start = CoroutineStart.UNDISPATCHED) {
                session.ptp.events.isClosed.first { it }
                if (current === session && !closed) disconnected.value = true
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                try {
                    opened?.close()
                } finally {
                    sessionScope.cancel()
                }
            }
            throw failure
        }
    }

    override suspend fun close() {
        if (closed) return
        closed = true
        val session = current
        current = null
        withContext(NonCancellable) {
            lossMonitor?.cancelAndJoin()
            try { session?.close() }
            finally { protocolScope.cancel(); releaseNetwork() }
        }
    }
}
