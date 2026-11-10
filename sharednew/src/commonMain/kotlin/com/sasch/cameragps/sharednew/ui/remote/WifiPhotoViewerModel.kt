package com.sasch.cameragps.sharednew.ui.remote

import com.sasch.cameragps.sharednew.remote.wifi.PhotoFormat
import com.sasch.cameragps.sharednew.remote.wifi.PhotoMetadata
import com.sasch.cameragps.sharednew.remote.wifi.PhotoMetadataState
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraCapture
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraPhoto
import com.sasch.cameragps.sharednew.remote.wifi.WifiDownloadPlan
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferState
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoDownload
import com.sasch.cameragps.sharednew.remote.wifi.WifiSkipReason
import com.sasch.cameragps.sharednew.remote.wifi.planDownloads

/**
 * The viewer's optional info-strip blocks (settings → photos → viewer info; all off by default). They
 * only add blocks to the strip; the basic line (format · resolution · size) is always shown. Read by
 * the app host and handed in, the shared viewer never touches the app's preferences.
 */
data class ViewerInfoOptions(
    val showHistogram: Boolean = false,
    val showExifParams: Boolean = false,
    val showLens: Boolean = false,
)

/**
 * Whether the viewer asks for the extended metadata (a 64 KB read of the original). Only shooting
 * parameters and the lens come from it; the histogram is computed from the decoded preview, so with
 * only the histogram on (or everything off) the viewer stays on the free catalog data.
 */
internal fun viewerNeedsExtendedMetadata(options: ViewerInfoOptions): Boolean =
    options.showExifParams || options.showLens

/** The handle the viewer reads metadata for: the capture's preview file (its JPEG when it has one). */
internal val WifiCameraCapture.metadataHandle: Long get() = preview.handle

private const val MIME_JPEG = "image/jpeg"
private const val MIME_HEIF = "image/heif"
private const val MIME_RAW = "image/x-sony-arw"

private fun WifiCameraPhoto.viewerFormatName(): String = when (mimeType) {
    MIME_JPEG -> "JPG"
    MIME_HEIF -> "HEIF"
    MIME_RAW -> "RAW"
    else -> filename.substringAfterLast('.', "").uppercase()
}

/**
 * Format line of a capture, spelled out: "JPG", "RAW", "JPG + RAW", "HEIF + RAW" (JPG, HEIF, RAW
 * order; unknown types by extension). Null when nothing is known. Format names are not localized.
 */
internal fun viewerFormatText(capture: WifiCameraCapture): String? {
    val order = listOf("JPG", "HEIF", "RAW")
    val names = capture.files.map { it.viewerFormatName() }.filter { it.isNotBlank() }.distinct()
        .sortedBy { order.indexOf(it).let { index -> if (index < 0) order.size else index } }
    return names.takeIf { it.isNotEmpty() }?.joinToString(" + ")
}

/** Metadata of [handle] in [state], or null when the state belongs to another photo (stale swipe). */
internal fun PhotoMetadataState.metadataFor(handle: Long): PhotoMetadata? = when (this) {
    PhotoMetadataState.Idle -> null
    is PhotoMetadataState.Loading -> basic?.takeIf { this.handle == handle }
    is PhotoMetadataState.Ready -> metadata.takeIf { it.handle == handle }
    is PhotoMetadataState.Unavailable -> basic?.takeIf { this.handle == handle }
}

/** What the extended blocks of the strip show. */
internal enum class ViewerExtrasState {
    /** Nothing to show: options off, read failed, or the format has no readable EXIF. */
    None,
    /** The extended read is pending: placeholder bars. */
    Loading,
    /** [ViewerInfoStrip.exifParams] / [ViewerInfoStrip.lens] carry the values. */
    Ready,
}

/**
 * Everything the info strip (and the side panel of the wide layouts) shows for one capture.
 *
 * @property formatText "JPG + RAW" etc.; with [noJpeg] the strip shows it as "Only RAW".
 * @property resolutionText "6000×4000" from the JPEG/HEIF object info or the metadata; null for RAW only.
 * @property sizeBytes the one file size of the viewer: what the default download format would fetch
 *   (JPG only → the JPEG; Original → every file). A capture without a JPEG under JPG only shows the
 *   size of its originals (what the manual "Download RAW" fetches).
 * @property noJpeg the default format is JPG only and the capture has no JPEG (RAW-only / HEIF-only).
 */
internal data class ViewerInfoStrip(
    val formatText: String?,
    val resolutionText: String?,
    val sizeBytes: Long,
    val noJpeg: Boolean,
    val extras: ViewerExtrasState,
    val exifParams: String?,
    val lens: String?,
    val showHistogram: Boolean,
)

/** "f/2.8  1/250s  ISO 400  35mm  +0.3 EV": numbers only, missing values left out; null when empty. */
internal fun viewerExifParamsText(metadata: PhotoMetadata?): String? {
    val exif = metadata?.exif ?: return null
    val shutter = exif.exposureTimeText?.let { if (it.endsWith("s")) it else it + "s" }
    return listOfNotNull(
        exif.apertureText, shutter, exif.isoText, exif.focalLengthText, exif.exposureBiasText
    ).takeIf { it.isNotEmpty() }?.joinToString("  ")
}

internal fun viewerInfoStrip(
    capture: WifiCameraCapture,
    policy: WifiFormatPolicy,
    metadataState: PhotoMetadataState,
    options: ViewerInfoOptions,
): ViewerInfoStrip {
    val metadata = metadataState.metadataFor(capture.metadataHandle)
    val hasJpeg = capture.files.any { it.mimeType == MIME_JPEG }
    val noJpeg = policy == WifiFormatPolicy.JpegOnly && !hasJpeg
    // The developed image's size: JPEG first, then HEIF, then the metadata of the preview file. RAW
    // object info is not trusted for pixel size, so a RAW-only capture shows just "RAW".
    val developed = capture.files.filter { it.mimeType == MIME_JPEG || it.mimeType == MIME_HEIF }
        .sortedBy { if (it.mimeType == MIME_JPEG) 0 else 1 }
    val resolution = developed.firstNotNullOfOrNull { photo ->
        val w = photo.width
        val h = photo.height
        if (w != null && h != null && w > 0 && h > 0) "$w×$h" else null
    } ?: metadata?.takeIf { it.format == PhotoFormat.Jpeg || it.format == PhotoFormat.Heif }?.resolutionText
    val intended = planDownloads(listOf(capture), policy, emptySet()).totalBytes
    val size = if (intended > 0) intended else capture.files.sumOf { it.size }
    val wantsExtended = viewerNeedsExtendedMetadata(options)
    val params = if (options.showExifParams) viewerExifParamsText(metadata) else null
    val lens = if (options.showLens) metadata?.exif?.lensModel?.takeIf { it.isNotBlank() } else null
    val extras = when {
        !wantsExtended -> ViewerExtrasState.None
        metadataState is PhotoMetadataState.Loading && metadataState.handle == capture.metadataHandle ->
            ViewerExtrasState.Loading
        params != null || lens != null -> ViewerExtrasState.Ready
        else -> ViewerExtrasState.None
    }
    return ViewerInfoStrip(
        formatText = viewerFormatText(capture),
        resolutionText = if (hasJpeg || capture.files.any { it.mimeType == MIME_HEIF }) resolution else null,
        sizeBytes = size,
        noJpeg = noJpeg,
        extras = extras,
        exifParams = params.takeIf { extras == ViewerExtrasState.Ready },
        lens = lens.takeIf { extras == ViewerExtrasState.Ready },
        showHistogram = options.showHistogram,
    )
}

/** States of the viewer's single download button. */
internal enum class ViewerDownloadPhase {
    /** "Download": fetches [ViewerDownloadState.downloads] (the default-format plan). */
    Ready,
    /** This capture is being downloaded: glass progress capsule with Cancel, swiping locked. */
    Downloading,
    /** Another transfer (a batch) holds the camera: button disabled with a wait hint. */
    OtherTransfer,
    /** The catalog is (re)loading: button disabled. */
    Waiting,
    /** Everything the button would fetch is saved: greyed "Saved", not clickable. */
    Saved,
    /** JPG only and no JPEG: "This photo has no JPG" plus a manual "Download RAW" (never silent). */
    NoJpeg,
    /** Over the download limit: disabled with the reason. */
    TooLarge,
}

internal data class ViewerDownloadState(
    val phase: ViewerDownloadPhase,
    /** What a tap fetches (Ready: default format; NoJpeg: the originals). Empty otherwise. */
    val downloads: List<WifiPhotoDownload> = emptyList(),
    /** NoJpeg: the originals' format ("RAW", "HEIF") for "Download RAW". */
    val manualFormat: String? = null,
) {
    val enabled: Boolean
        get() = downloads.isNotEmpty() &&
                (phase == ViewerDownloadPhase.Ready || phase == ViewerDownloadPhase.NoJpeg)
}

/**
 * The download button of the viewer, from the shared planner (the same one as the grid batch), the
 * saved-file set of this catalog generation and the transfer state. A transfer whose current file
 * belongs to [capture] (or an unnamed one, the viewer being the only single-download source) is this
 * page's download; any other running transfer only disables the button.
 */
internal fun viewerDownloadState(
    capture: WifiCameraCapture,
    policy: WifiFormatPolicy,
    saved: Set<WifiPhotoDownload>,
    transfer: WifiImageTransferState,
    browserLoading: Boolean,
): ViewerDownloadState {
    if (transfer.busy) {
        val ours = transfer.filename.isBlank() || capture.files.any { it.filename == transfer.filename }
        return ViewerDownloadState(if (ours) ViewerDownloadPhase.Downloading else ViewerDownloadPhase.OtherTransfer)
    }
    if (browserLoading) return ViewerDownloadState(ViewerDownloadPhase.Waiting)
    val plan = planDownloads(listOf(capture), policy, saved)
    if (plan.files.isNotEmpty()) return ViewerDownloadState(ViewerDownloadPhase.Ready, plan.downloads)
    return when (plan.mainSkipReason) {
        WifiSkipReason.NoJpeg -> {
            val originals = planDownloads(listOf(capture), WifiFormatPolicy.Original, saved)
            noJpegState(capture, originals)
        }
        WifiSkipReason.AlreadySaved -> ViewerDownloadState(ViewerDownloadPhase.Saved)
        WifiSkipReason.TooLarge -> ViewerDownloadState(ViewerDownloadPhase.TooLarge)
        null -> ViewerDownloadState(ViewerDownloadPhase.Waiting)
    }
}

private fun noJpegState(capture: WifiCameraCapture, originals: WifiDownloadPlan): ViewerDownloadState = when {
    originals.files.isNotEmpty() ->
        ViewerDownloadState(ViewerDownloadPhase.NoJpeg, originals.downloads, viewerFormatText(capture))
    originals.mainSkipReason == WifiSkipReason.AlreadySaved -> ViewerDownloadState(ViewerDownloadPhase.Saved)
    else -> ViewerDownloadState(ViewerDownloadPhase.TooLarge)
}

/** Fraction of the current file received, or null while it is unknown (converting / no size yet). */
internal fun viewerTransferFraction(transfer: WifiImageTransferState): Float? =
    if (transfer.totalBytes > 0) (transfer.bytesReceived.toFloat() / transfer.totalBytes).coerceIn(0f, 1f) else null

/**
 * "k / N" of the top bar. A day of the grouped browser is complete, so N is its capture count; the
 * legacy flat browser keeps its catalog total.
 */
internal fun viewerPositionTotal(grouped: Boolean, captureCount: Int, totalObjects: Int): Int =
    if (grouped) captureCount else maxOf(totalObjects, captureCount)

/**
 * Captures the filmstrip asks thumbnails for: the shown one first, then the visible strip, and a
 * margin of [margin] items on each side of the visible range — never the whole day.
 */
internal fun filmstripThumbnailDemand(
    current: Int,
    visible: IntRange,
    count: Int,
    margin: Int = FILMSTRIP_MARGIN,
): Pair<List<Int>, List<Int>> {
    if (count <= 0) return emptyList<Int>() to emptyList()
    val shown = buildList {
        if (current in 0 until count) add(current)
        for (i in visible) if (i in 0 until count && i != current) add(i)
    }
    val first = if (visible.isEmpty()) current else visible.first
    val last = if (visible.isEmpty()) current else visible.last
    val around = ((first - margin) until first) + ((last + 1)..(last + margin))
    val extra = around.filter { it in 0 until count && it !in shown }
    return shown to extra
}

internal const val FILMSTRIP_MARGIN = 4

private val legacyCaptureTime = Regex("""(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2}).*""")
private val isoCaptureTime = Regex("""(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}(?::\d{2})?).*""")

/**
 * Second line of the viewer's top bar: the camera's local capture time as "YYYY-MM-DD hh:mm:ss"
 * (legacy PTP `YYYYMMDDThhmmss` and ISO forms; fraction and zone suffix dropped, no conversion).
 * Unknown forms are shown as recorded; null when absent.
 */
internal fun viewerCaptureTimeText(capturedAt: String): String? {
    val value = capturedAt.trim()
    if (value.isEmpty()) return null
    legacyCaptureTime.matchEntire(value)?.let { m ->
        val (y, mo, d, h, mi, s) = m.destructured
        return "$y-$mo-$d $h:$mi:$s"
    }
    isoCaptureTime.matchEntire(value)?.let { m -> return m.groupValues[1] + " " + m.groupValues[2] }
    return value
}
