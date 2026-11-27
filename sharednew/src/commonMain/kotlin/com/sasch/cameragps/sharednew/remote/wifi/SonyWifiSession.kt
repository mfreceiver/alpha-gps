package com.sasch.cameragps.sharednew.remote.wifi

import com.sasch.cameragps.sharednew.database.logging.RemoteDiagnostics
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
import kotlinx.coroutines.flow.onEach
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
        connection.openFailed(failure)
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
    private val report = if (browseOnly) null else RemoteDiagnostics("Wi-Fi")
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
        val shutter = SonyPtpShutter(ptp.commands, ready, ptp.events, observe = { report?.record(it) })
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

    fun openFailed(error: Throwable) { report?.record("open failed: ${error::class.simpleName}") }

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
    override val canFocus: Boolean
        get() = current?.ready?.let { ready ->
            ready.purpose == SonySessionPurpose.RemoteShooting &&
                    ready.deviceInfo.supports(SonyPtpOperation.SDIO_CONTROL_DEVICE) &&
                    ready.extendedInfo.supportsControl(SonyPtpControlCode.HALF_PRESS)
        } == true
    override val canRecordMovie: Boolean
        get() = current?.ready?.let { ready ->
            ready.purpose == SonySessionPurpose.RemoteShooting &&
                    ready.deviceInfo.supports(SonyPtpOperation.SDIO_CONTROL_DEVICE) &&
                    ready.extendedInfo.supportsControl(SonyPtpControlCode.MOVIE_RECORD)
        } == true
    override val canReadProperties: Boolean
        get() = current?.ready?.let { ready ->
            ready.purpose == SonySessionPurpose.RemoteShooting &&
                    ready.deviceInfo.supports(SonyPtpOperation.SDIO_GET_ALL_EXT_DEVICE_PROP_INFO)
        } == true
    override val canWriteProperties: Boolean
        get() = canReadProperties &&
                current?.ready?.deviceInfo?.supports(SonyPtpOperation.SDIO_SET_EXT_DEVICE_PROP_VALUE) == true
    override val lost: Flow<Unit> = merge(networkLost.onEach { report?.record("network lost") }, disconnected.filter { it }.map { })
    override val propertyChangedEvents: Flow<Unit> = flow {
        val session = current ?: return@flow
        session.ptp.events.events.collect { signal ->
            if (signal is PtpIpEventSignal.Received && signal.event.isPropertyChanged) {
                emit(Unit)
            }
        }
    }

    private suspend fun enterTransferMode() {
        if (separateTransfer && current?.ready?.purpose != SonySessionPurpose.ImageTransfer) {
            replaceSession(SonySessionPurpose.ImageTransfer)
        }
    }

    override suspend fun openCatalog(): List<DateGroup> {
        enterTransferMode()
        return checkNotNull(current).transfer.openCatalog()
    }

    override suspend fun dayPhotos(dateKey: String) =
        checkNotNull(current).transfer.dayPhotos(dateKey)

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
        return SonyBatteryReader(session.ptp.commands, session.ready).read().also { report?.record("battery=${it ?: "unknown"}") }
    }

    override suspend fun readShootingProperties(): CameraPropertiesSnapshot? {
        val session = current ?: return null
        if (session.ready.purpose != SonySessionPurpose.RemoteShooting) return null
        val op = SonyPtpOperation.SDIO_GET_ALL_EXT_DEVICE_PROP_INFO
        if (!session.ready.deviceInfo.supports(op)) return null
        return try {
            val params = if ((session.ready.vendorCodeVersion ?: 0) >= 310) listOf(0L, 1L) else listOf(0L)
            val result = session.ptp.commands.executeDataIn(op, params, maxDataBytes = 1024 * 1024)
            if (result !is PtpIpTransactionResult.Response || result.response.code != 0x2001) return null
            val data = result.data ?: return null
            val parsed = SonyCameraProperties.parse(
                data,
                session.ready.extendedInfo.controlCodes,
                CameraShootingPropertyKey.readCodes,
            )
            SonyRemotePropertyFormatter.toSnapshot(
                parsed,
                supportsWriteOp = session.ready.deviceInfo.supports(SonyPtpOperation.SDIO_SET_EXT_DEVICE_PROP_VALUE),
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.w { "Shooting properties read failed: ${error::class.simpleName}" }
            null
        }
    }

    override suspend fun setShootingProperty(
        key: CameraShootingPropertyKey,
        dataType: Int,
        rawValue: Long,
    ): PropertyWriteStatus {
        val session = current ?: return PropertyWriteStatus.Failed
        if (session.ready.purpose != SonySessionPurpose.RemoteShooting) return PropertyWriteStatus.Unsupported
        val op = SonyPtpOperation.SDIO_SET_EXT_DEVICE_PROP_VALUE
        if (!session.ready.deviceInfo.supports(op)) return PropertyWriteStatus.Unsupported
        val payload = SonyCameraProperties.encodeScalar(dataType, rawValue) ?: return PropertyWriteStatus.Unsupported
        val vendor310 = (session.ready.vendorCodeVersion ?: 0) >= 310
        val primaryParams = if (vendor310) listOf(key.code.toLong(), 1L) else listOf(key.code.toLong())
        report?.record("property write 0x${key.code.toString(16)} type=$dataType raw=$rawValue params=$primaryParams")
        return try {
            var result = session.ptp.commands.executeDataOut(op, primaryParams, payload)
            report?.record("property write 0x${key.code.toString(16)} ${result.diagnosticSummary()}")
            if (vendor310 && result is PtpIpTransactionResult.Response && result.response.code == PtpResponse.INVALID_PARAMETER) {
                val fallbackParams = listOf(key.code.toLong())
                report?.record("property write 0x${key.code.toString(16)} fallback params=$fallbackParams")
                result = session.ptp.commands.executeDataOut(op, fallbackParams, payload)
                report?.record("property write 0x${key.code.toString(16)} fallback ${result.diagnosticSummary()}")
            }
            when (result) {
                is PtpIpTransactionResult.Response ->
                    if (result.response.code == PtpResponse.OK) PropertyWriteStatus.Applied
                    else PropertyWriteStatus.Rejected
                PtpIpTransactionResult.Uncertain,
                is PtpIpTransactionResult.Failure,
                PtpIpTransactionResult.Closed -> PropertyWriteStatus.Failed
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            report?.record("property write failed: ${error::class.simpleName}")
            PropertyWriteStatus.Failed
        }
    }

    override suspend fun focus(): RemoteActionStatus {
        if (!canFocus) return RemoteActionStatus.Rejected
        return when (checkNotNull(current).shutter.focus().also { report?.record("focus=$it") }) {
            SonyPtpCaptureResult.Submitted, SonyPtpCaptureResult.CaptureEventObserved -> RemoteActionStatus.Done
            is SonyPtpCaptureResult.Rejected, SonyPtpCaptureResult.Unsupported -> RemoteActionStatus.Rejected
            else -> RemoteActionStatus.Uncertain
        }
    }

    override suspend fun toggleMovieRecord(): RemoteActionStatus {
        if (!canRecordMovie) return RemoteActionStatus.Rejected
        return when (checkNotNull(current).shutter.toggleMovieRecord().also { report?.record("movie=$it") }) {
            SonyPtpCaptureResult.Submitted, SonyPtpCaptureResult.CaptureEventObserved -> RemoteActionStatus.Done
            is SonyPtpCaptureResult.Rejected, SonyPtpCaptureResult.Unsupported -> RemoteActionStatus.Rejected
            else -> RemoteActionStatus.Uncertain
        }
    }

    override suspend fun probeProperties(): PropertyProbeStatus {
        val session = current ?: return PropertyProbeStatus.Failed
        val diagnostics = report ?: return PropertyProbeStatus.Failed
        if (session.ready.purpose != SonySessionPurpose.RemoteShooting) return PropertyProbeStatus.Unsupported
        val op = SonyPtpOperation.SDIO_GET_ALL_EXT_DEVICE_PROP_INFO
        if (!session.ready.deviceInfo.supports(op)) {
            diagnostics.record("property read unsupported")
            return PropertyProbeStatus.Unsupported
        }
        diagnostics.record("property read started")
        val start = kotlin.time.TimeSource.Monotonic.markNow()
        return try {
            val params = if ((session.ready.vendorCodeVersion ?: 0) >= 310) listOf(0L, 1L) else listOf(0L)
            val result = session.ptp.commands.executeDataIn(op, params, maxDataBytes = 1024 * 1024)
            diagnostics.record("property read ${start.elapsedNow()} ${result.diagnosticSummary()}")
            if (result !is PtpIpTransactionResult.Response || result.response.code != 0x2001) return PropertyProbeStatus.Failed
            val data = result.data ?: return PropertyProbeStatus.Failed
            // Only known shooting properties may include values. Vendor strings can contain identifiers.
            val shooting = setOf(0x5001, 0x5003, 0x5004, 0x5005, 0x5007, 0x5008, 0x500a,
                0x500b, 0x500c, 0x500d, 0x500e, 0x500f, 0x5010, 0x5013, 0xd20d, 0xd21e, 0xd218)
            val snapshot = buildString {
                append("Property descriptors (raw integers, writes untested)")
                SonyCameraProperties.parse(data, session.ready.extendedInfo.controlCodes, shooting, describe = {
                    if (length < 48000) append("\n$it")
                    else if (!endsWith("[truncated]")) append("\n[truncated]")
                })
            }
            diagnostics.record(snapshot)
            PropertyProbeStatus.Recorded
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            diagnostics.record("property probe failed: ${error::class.simpleName}")
            PropertyProbeStatus.Failed
        }
    }

    override suspend fun prepareToDisconnect() {
        current?.transfer?.closeBrowser()
    }

    override val hasLiveViewStream: Boolean get() = !browseOnly

    override val liveViewFrames: Flow<WifiLiveViewFrame> = flow {
        if (browseOnly) return@flow
        val session = checkNotNull(current)
        check(session.ready.purpose == SonySessionPurpose.RemoteShooting)
        log.i { "Wi-Fi live view resolving camera endpoint" }
        report?.record("waiting for first frame")
        var first = true
        emitAll(session.preview.decodedFrames(session.endpoint.resolve(host)).onEach {
            if (first) {
                first = false
                report?.record("first frame")
            }
        })
    }

    override val images: Flow<ImageBitmap> = liveViewFrames.map { it.image }

    override suspend fun capture(): WifiCaptureStatus {
        if (!canCapture) return WifiCaptureStatus.Rejected
        return when (checkNotNull(current).shutter.captureStill().also { report?.record("capture=$it") }) {
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
                report?.record(buildString {
                    append("$ready model=${ready.deviceInfo.model ?: "unknown"} firmware=${ready.deviceInfo.firmware ?: "unknown"}")
                    fun codes(label: String, values: Set<Int>) {
                        append("\n$label=")
                        append(values.sorted().joinToString(",") { it.toString(16) })
                    }
                    codes("operations", ready.deviceInfo.supportedOperations)
                    codes("standard properties", ready.deviceInfo.supportedProperties)
                    codes("Sony properties", ready.extendedInfo.propertyCodes)
                    codes("controls", ready.extendedInfo.controlCodes)
                })
                Session(ptp, ready, sessionScope)
            }
            current = session
            lossMonitor = protocolScope.launch(start = CoroutineStart.UNDISPATCHED) {
                session.ptp.events.isClosed.first { it }
                if (current === session && !closed) {
                    report?.record("event channel closed")
                    disconnected.value = true
                }
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
        report?.record("session closed")
        val session = current
        current = null
        withContext(NonCancellable) {
            lossMonitor?.cancelAndJoin()
            try { session?.close() }
            finally { protocolScope.cancel(); releaseNetwork() }
        }
    }
}
