package com.sasch.cameragps.sharednew.remote.wifi

import kotlin.math.abs
import kotlin.math.roundToInt

enum class PhotoFormat { Jpeg, Raw, Heif, Unknown }

internal fun photoFormatOf(mimeType: String): PhotoFormat = when (mimeType) {
    "image/jpeg" -> PhotoFormat.Jpeg
    "image/x-sony-arw" -> PhotoFormat.Raw
    "image/heif" -> PhotoFormat.Heif
    else -> PhotoFormat.Unknown
}

/**
 * Shooting parameters read from the JPEG's EXIF. Every field is optional: a camera or file may omit
 * any of them, and an unreadable tag never invalidates the others. Numbers are raw values; use the
 * `*Text` helpers for the conventional display form (language neutral).
 */
data class PhotoExif(
    val apertureF: Double? = null,
    val exposureTimeSeconds: Double? = null,
    val iso: Int? = null,
    val focalLengthMm: Double? = null,
    val focalLength35mm: Int? = null,
    val exposureBiasEv: Double? = null,
    val lensModel: String? = null,
    val cameraModel: String? = null,
    val pixelWidth: Int? = null,
    val pixelHeight: Int? = null,
) {
    /** `f/2.8` */
    val apertureText: String? get() = apertureF?.let { "f/" + decimal(it, 1) }

    /** `1/250` for fast shutters, `2.5s` / `30s` for slow ones. */
    val exposureTimeText: String?
        get() = exposureTimeSeconds?.takeIf { it > 0 }?.let { seconds ->
            if (seconds < 0.4) "1/" + (1.0 / seconds).roundToInt() else decimal(seconds, 1) + "s"
        }

    /** `ISO 400` */
    val isoText: String? get() = iso?.let { "ISO $it" }

    /** `50mm`; the actual focal length, not the 35 mm equivalent. */
    val focalLengthText: String? get() = focalLengthMm?.let { decimal(it, 0) + "mm" }

    /** `+0.7 EV` / `-1.3 EV` / `0 EV` */
    val exposureBiasText: String?
        get() = exposureBiasEv?.let { ev ->
            val rounded = (ev * 10).roundToInt() / 10.0
            when {
                rounded == 0.0 -> "0 EV"
                rounded > 0 -> "+" + decimal(rounded, 1) + " EV"
                else -> decimal(rounded, 1) + " EV"
            }
        }

    val isEmpty: Boolean
        get() = this == EMPTY

    companion object {
        val EMPTY = PhotoExif()

        /** Rounds half away from zero to [digits] decimals and drops a trailing `.0`. */
        internal fun decimal(value: Double, digits: Int): String {
            var scale = 1L
            repeat(digits) { scale *= 10 }
            val scaled = (abs(value) * scale + 0.5).toLong()
            val whole = scaled / scale
            val fraction = scaled % scale
            val sign = if (value < 0 && scaled != 0L) "-" else ""
            if (digits == 0 || fraction == 0L) return sign + whole
            return sign + whole + "." + fraction.toString().padStart(digits, '0')
        }
    }
}

/**
 * Metadata of one photo. The basic part (format, size, capture time, resolution when the camera's
 * object info carries it) costs no camera traffic; [exif] is only filled when the extended read was
 * requested and succeeded.
 */
data class PhotoMetadata(
    val handle: Long,
    val filename: String,
    val format: PhotoFormat,
    val sizeBytes: Long,
    /** Camera-local capture time exactly as the camera reports it (`YYYYMMDDThhmmss` style); may be empty. */
    val capturedAt: String,
    /** Pixel size from the object info; falls back to the EXIF value after an extended read. */
    val width: Int? = null,
    val height: Int? = null,
    val exif: PhotoExif? = null,
    /** True once the extended (EXIF) read was attempted successfully, even if the file had no EXIF. */
    val extendedRead: Boolean = false,
) {
    /** `6000×4000` or `null` when unknown. */
    val resolutionText: String? get() = if (width != null && height != null) "$width×$height" else null

    /** Whether an extended read can say anything for this file (JPEG only; RAW/HEIF stay basic). */
    val supportsExtended: Boolean get() = format == PhotoFormat.Jpeg && sizeBytes > 0
}

internal fun WifiCameraPhoto.toBasicMetadata() = PhotoMetadata(
    handle = handle,
    filename = filename,
    format = photoFormatOf(mimeType),
    sizeBytes = size,
    capturedAt = capturedAt,
    width = width,
    height = height,
)

/** Why metadata could not be read; mirrors [WifiTransferFailureReason] plus protocol-level causes. */
enum class PhotoMetadataFailureReason {
    /** The photo is no longer in the displayed catalog (card changed, handle retired). */
    CatalogChanged,

    /** The camera did not answer in time. */
    Timeout,

    /** The camera is busy (e.g. shooting or writing); retry later. */
    CameraBusy,

    /** The camera refuses partial reads for this object. */
    Unsupported,

    /** Broken connection or inconsistent data. */
    Protocol,
}

sealed interface PhotoMetadataState {
    /** Nothing requested (initial, after [WifiRemoteController.cancelMetadata], or after the session ended). */
    data object Idle : PhotoMetadataState

    /** [basic] is available immediately for the UI to show while the extended read is pending. */
    data class Loading(val handle: Long, val basic: PhotoMetadata?) : PhotoMetadataState

    data class Ready(val metadata: PhotoMetadata) : PhotoMetadataState {
        val handle: Long get() = metadata.handle
    }

    /** The extended part failed; [basic] is still valid when known. Preview and download are unaffected. */
    data class Unavailable(
        val handle: Long,
        val reason: PhotoMetadataFailureReason,
        val basic: PhotoMetadata?,
    ) : PhotoMetadataState
}

/** Maps a failed metadata read to the user-facing reason (never leaks messages or coordinates). */
internal fun photoMetadataFailureReason(failure: Throwable): PhotoMetadataFailureReason = when (failure) {
    is PtpTransportException -> when (failure.reason) {
        WifiTransferFailureReason.Timeout -> PhotoMetadataFailureReason.Timeout
        WifiTransferFailureReason.CatalogChanged -> PhotoMetadataFailureReason.CatalogChanged
        else -> PhotoMetadataFailureReason.Protocol
    }
    is PtpOperationRejected -> when (failure.code) {
        PtpResponse.DEVICE_BUSY -> PhotoMetadataFailureReason.CameraBusy
        PtpResponse.INVALID_OBJECT_HANDLE, PtpResponse.INVALID_PARAMETER ->
            PhotoMetadataFailureReason.CatalogChanged
        PtpResponse.OPERATION_NOT_SUPPORTED, PtpResponse.INVALID_OBJECT_FORMAT ->
            PhotoMetadataFailureReason.Unsupported
        else -> PhotoMetadataFailureReason.Protocol
    }
    is kotlinx.coroutines.TimeoutCancellationException -> PhotoMetadataFailureReason.Timeout
    else -> PhotoMetadataFailureReason.Protocol
}

/** Standard PTP response codes relevant to read-only image operations. */
internal object PtpResponse {
    const val OK = 0x2001
    const val OPERATION_NOT_SUPPORTED = 0x2005
    const val INVALID_PARAMETER = 0x201d
    const val INVALID_OBJECT_HANDLE = 0x2009
    const val INVALID_OBJECT_FORMAT = 0x200b
    const val DEVICE_BUSY = 0x2019
}

/**
 * The camera answered an image operation with a response code other than OK. Still an
 * [IllegalStateException] with the historical message so existing handlers keep working; the code
 * lets callers tell "busy" from "not supported".
 */
internal class PtpOperationRejected(val code: Int) : IllegalStateException("Camera refused image operation")
