package com.sasch.cameragps.sharednew.remote.wifi

import androidx.compose.ui.graphics.ImageBitmap

/** Explicit purpose for a Wi-Fi session; prevents browse-only mode from locking out live view. */
enum class WifiSessionPurpose {
    Browse,
    RemoteControl,
}

/** Latest decoded live-view frame paired with its raw JPEG bytes when available. */
class WifiLiveViewFrame(
    val image: ImageBitmap,
    val jpegBytes: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WifiLiveViewFrame) return false
        if (image != other.image) return false
        return when {
            jpegBytes == null && other.jpegBytes == null -> true
            jpegBytes != null && other.jpegBytes != null -> jpegBytes.contentEquals(other.jpegBytes)
            else -> false
        }
    }

    override fun hashCode(): Int {
        var result = image.hashCode()
        result = 31 * result + (jpegBytes?.contentHashCode() ?: 0)
        return result
    }
}

/** Connection and shooting state, independent of BLE location and BLE shutter availability. */
enum class WifiRemotePhase {
    Idle,
    PreparingCamera,
    AwaitingNetworkApproval,
    AwaitingManualNetwork,
    JoiningNetwork,
    OpeningSession,
    Ready,
    Closing,
    Failed,
}

enum class WifiRemoteFailure {
    CameraUnavailable,
    CameraRefused,
    NetworkPermissionDenied,
    NetworkConnectionFailed,
    NetworkLost,
    AnotherController,
    ProtocolError,
    InvalidAddress,
    JoinCameraWifi,
    BluetoothRequired,
    CameraSetupFailed,
    CameraCredentialsUnavailable,
    NetworkJoinFailed,
    CameraAddressUnavailable,
    WifiDisabled,
    LocationServicesRequired,
}

enum class WifiCaptureStatus { Idle, Shooting, Captured, Uncertain, Rejected }
enum class WifiPreviewStatus { Waiting, Streaming, Failed, Unavailable }
enum class WifiShutdownStatus { NotRequested, Requested, Unavailable, Unconfirmed }

enum class WifiImageTransferStatus { Idle, Downloading, Converting, Saved, Cancelled, Failed, ConversionFailed, PermissionDenied }

/** Actionable cause of a [WifiImageTransferStatus.Failed] transfer. */
enum class WifiTransferFailureReason { CatalogChanged, Timeout, Storage, Protocol, Unknown }

enum class WifiPhotoDownloadFormat { Original, Jpeg }

data class WifiPhotoDownload(val handle: Long, val format: WifiPhotoDownloadFormat = WifiPhotoDownloadFormat.Original)

data class WifiImageTransferState(
    val status: WifiImageTransferStatus = WifiImageTransferStatus.Idle,
    val filename: String = "",
    val bytesReceived: Long = 0,
    val totalBytes: Long = 0,
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    /** Set only for Failed results; null keeps the generic failure text. */
    val failureReason: WifiTransferFailureReason? = null,
) {
    val busy: Boolean get() = status == WifiImageTransferStatus.Downloading || status == WifiImageTransferStatus.Converting
}

data class WifiCameraPhoto(
    val handle: Long,
    val filename: String,
    val size: Long,
    val mimeType: String,
    val capturedAt: String,
    val downloadable: Boolean = true,
    val captureId: String? = null,
    /** Pixel size from the camera's object info; `null` when the catalog source does not carry it. */
    val width: Int? = null,
    val height: Int? = null,
)

data class WifiPhotoBrowserState(
    val open: Boolean = false,
    /** Monotonic catalog generation: bumps on every (re)open or refresh, resets when closed. */
    val browserGeneration: Long = 0,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val photos: List<WifiCameraPhoto> = emptyList(),
    val totalObjects: Int = 0,
    val savedDownloads: Set<WifiPhotoDownload> = emptySet(),
    /** Days of the enumerated card, newest first with the unknown-date group last. */
    val dates: List<DateGroup> = emptyList(),
    /** Day whose captures [photos] holds; null while the day list is what is shown. */
    val dateKey: String? = null,
    /**
     * Bumps with every published day or day list. With [browserGeneration] and [dateKey] it
     * identifies a view, so a late result of an earlier navigation cannot be mistaken for the current one.
     */
    val pageRevision: Long = 0,
)

enum class PropertyProbeStatus { Idle, Reading, Recorded, Unsupported, Failed }
enum class RemoteActionStatus { Idle, Running, Done, Rejected, Uncertain }

data class WifiRemoteState(
    val phase: WifiRemotePhase = WifiRemotePhase.Idle,
    val failure: WifiRemoteFailure? = null,
    val purpose: WifiSessionPurpose = WifiSessionPurpose.Browse,
    val canCaptureStill: Boolean = false,
    val canFocus: Boolean = false,
    val canRecordMovie: Boolean = false,
    val hasLiveView: Boolean = false,
    val supportsLiveView: Boolean = false,
    val liveViewPaused: Boolean = false,
    val cameraName: String = "",
    val capture: WifiCaptureStatus = WifiCaptureStatus.Idle,
    val focusStatus: RemoteActionStatus = RemoteActionStatus.Idle,
    val movieActionStatus: RemoteActionStatus = RemoteActionStatus.Idle,
    val preview: WifiPreviewStatus = WifiPreviewStatus.Waiting,
    val canTransferImages: Boolean = false,
    val canConvertHeif: Boolean = false,
    val photoBrowser: WifiPhotoBrowserState = WifiPhotoBrowserState(),
    val imageTransfer: WifiImageTransferState = WifiImageTransferState(),
    val wifiShutdown: WifiShutdownStatus = WifiShutdownStatus.NotRequested,
    val manualNetwork: CameraWifiCredentials? = null,
    val propertyProbe: PropertyProbeStatus = PropertyProbeStatus.Idle,
    val probeAttempt: Int = 0,
    val cameraProperties: CameraPropertiesState = CameraPropertiesState(),
) {
    /** R2 control channel readiness, independent of live-view stream state. */
    val isControlReady: Boolean get() = phase == WifiRemotePhase.Ready

    /** R2 live-view stream readiness, independent of control channel & R3 parameter panel. */
    val isLiveViewReady: Boolean
        get() = phase == WifiRemotePhase.Ready &&
            supportsLiveView &&
            preview == WifiPreviewStatus.Streaming &&
            !liveViewPaused

    init {
        require((phase == WifiRemotePhase.Failed) == (failure != null))
        require(phase == WifiRemotePhase.Ready || (!canCaptureStill && !canFocus && !canRecordMovie && !hasLiveView && !supportsLiveView))
        require(phase == WifiRemotePhase.Ready || !liveViewPaused)
    }
}
