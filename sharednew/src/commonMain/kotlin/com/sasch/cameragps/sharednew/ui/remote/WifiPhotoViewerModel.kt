package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.ui.graphics.Color
import com.sasch.cameragps.sharednew.remote.wifi.PhotoExif
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
import com.sasch.cameragps.sharednew.remote.wifi.captureDownloadSize
import com.sasch.cameragps.sharednew.remote.wifi.planDownloads
import com.sasch.cameragps.sharednew.ui.theme.AlphaColors

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

private val viewerFormatOrder = listOf("JPG", "HEIF", "RAW")

private fun viewerFormatRank(name: String): Int =
    viewerFormatOrder.indexOf(name).let { index -> if (index < 0) viewerFormatOrder.size else index }

/**
 * Format line of a capture, spelled out: "JPG", "RAW", "JPG + RAW", "HEIF + RAW" (JPG, HEIF, RAW
 * order; unknown types by extension). Null when nothing is known. Format names are not localized.
 */
internal fun viewerFormatText(capture: WifiCameraCapture): String? = viewerFormatText(capture.files)

/** Metadata of [handle] in [state], or null when the state belongs to another photo (stale swipe). */
internal fun PhotoMetadataState.metadataFor(handle: Long): PhotoMetadata? = when (this) {
    PhotoMetadataState.Idle -> null
    is PhotoMetadataState.Loading -> basic?.takeIf { this.handle == handle }
    is PhotoMetadataState.Ready -> metadata.takeIf { it.handle == handle }
    is PhotoMetadataState.Unavailable -> basic?.takeIf { this.handle == handle }
}

/**
 * The single-layer info strip under the photo: "JPG · 8.1 MB" under JPG only, "JPG + RAW · 54.6 MB"
 * under Original (see [stripFormatLabel]).
 *
 * @property formatText the formats a download fetches ([stripFormatLabel]); with [noJpeg] the strip
 *   shows the capture's own formats as "Only RAW".
 * @property sizeBytes what the default download format would fetch ([captureDownloadSize], the same
 *   number as the grid's size tag and the batch button): JPG only → the JPEG; Original → every file.
 *   A capture without a JPEG under JPG only shows the size of its originals (what the manual
 *   "Download RAW" fetches) and is marked [noJpeg] — it is never downloaded silently.
 * @property noJpeg the default format is JPG only and the capture has no JPEG (RAW-only / HEIF-only).
 */
internal data class ViewerInfoStrip(
    val formatText: String?,
    val sizeBytes: Long,
    val noJpeg: Boolean,
)

/**
 * Whether the portrait details drawer is up and the viewer below must be hidden from
 * accessibility services (the drawer is modal; the wide layouts' side panel is not — this is
 * false for them because `portrait` is false).
 */
internal fun viewerHidesBackgroundSemantics(portrait: Boolean, drawerOpen: Boolean, hasDetails: Boolean): Boolean =
    portrait && drawerOpen && hasDetails

internal fun viewerInfoStrip(
    capture: WifiCameraCapture,
    policy: WifiFormatPolicy,
    saved: Set<WifiPhotoDownload> = emptySet(),
): ViewerInfoStrip {
    val size = captureDownloadSize(capture, policy, saved)
    // Only the NoJpeg exception carries a size that will not download (see CaptureDownloadSize).
    return ViewerInfoStrip(
        stripFormatLabel(capture, policy), size.bytes,
        noJpeg = size.reason == WifiSkipReason.NoJpeg,
    )
}

/**
 * The files a download of [capture] under [policy] aims at — chosen by the download planner itself
 * ([planDownloads]), before the per-file exclusions (already saved, over the download limit) that
 * only decide whether the bytes still have to be fetched. Empty when the policy takes nothing from
 * this capture (JPG only over a RAW / HEIF-only capture). A new policy only changes the planner.
 */
internal fun viewerDownloadTargets(capture: WifiCameraCapture, policy: WifiFormatPolicy): List<WifiCameraPhoto> {
    val candidates = capture.copy(files = capture.files.map { it.copy(downloadable = true) })
    val planned = planDownloads(listOf(candidates), policy, emptySet()).files.map { it.handle }.toSet()
    return capture.files.filter { it.handle in planned }
}

/**
 * Format part of the info strip, derived from what the default download format fetches
 * ([viewerDownloadTargets]) so it never contradicts the size next to it: JPG only over a JPG + RAW
 * capture → "JPG"; Original → every format ("JPG + RAW"). When the policy takes nothing from the
 * capture (no JPEG under JPG only) it is the capture's own formats, which the strip marks as
 * "Only RAW" ([ViewerInfoStrip.noJpeg]). Null when no format is known.
 */
internal fun stripFormatLabel(capture: WifiCameraCapture, policy: WifiFormatPolicy): String? {
    val targets = viewerDownloadTargets(capture, policy)
    return viewerFormatText(if (targets.isEmpty()) capture.files else targets)
}

private fun viewerFormatText(files: List<WifiCameraPhoto>): String? {
    val names = files.map { it.viewerFormatName() }.filter { it.isNotBlank() }.distinct()
        .sortedBy(::viewerFormatRank)
    return names.takeIf { it.isNotEmpty() }?.joinToString(" + ")
}

/** One file of a capture in the details: its format ("JPG" / "RAW" / "HEIF") and own size. */
internal data class ViewerFileEntry(val format: String, val bytes: Long)

/**
 * A shooting parameter of the details; the label is localized by the UI, values are not. Declared in
 * display order: the one-per-line rows first, then the compact two-column grid ([inGrid]).
 */
internal enum class ViewerExifField(val inGrid: Boolean = false) {
    Camera, Lens, FocalLength,
    /** 35 mm-equivalent focal length; only shown when it differs from the actual one. */
    FocalLength35mm,
    Aperture(inGrid = true), Shutter(inGrid = true), Iso(inGrid = true), ExposureBias(inGrid = true),
}

/**
 * Everything the details drawer (portrait) / side panel (wide layouts) shows for one capture, in
 * display order: capture time, resolution, camera, lens, focal length(s), the exposure grid, then the
 * file sizes next to the histogram. The file name (top bar) and the format line (info strip) are not
 * repeated. Every value is optional: what cannot be read is left out, nothing is shown as an empty
 * row.
 *
 * @property files each file's format with its own size (the strip shows only the download total).
 * @property exif shooting parameters in [ViewerExifField] order, only those that were read.
 * @property loading the extended read of this photo is pending: the EXIF rows it may fill show
 *   placeholders of the same height (see [viewerPendingExifFields]).
 */
internal data class ViewerDetails(
    val capturedAt: String?,
    val resolutionText: String?,
    val exif: List<Pair<ViewerExifField, String>>,
    val files: List<ViewerFileEntry>,
    val loading: Boolean,
)

/** The shooting parameter rows of [metadata]'s EXIF, missing / blank values left out. */
internal fun viewerExifRows(metadata: PhotoMetadata?): List<Pair<ViewerExifField, String>> {
    val exif = metadata?.exif ?: return emptyList()
    val shutter = exif.exposureTimeText?.let { if (it.endsWith("s")) it else it + "s" }
    val focal = exif.focalLengthText
    // The equivalent is written like focalLengthText ("50mm") and left out when it says the same.
    val focal35 = exif.focalLength35mm?.let { PhotoExif.decimal(it.toDouble(), 0) + "mm" }?.takeIf { it != focal }
    val values = mapOf(
        ViewerExifField.Camera to exif.cameraModel?.trim(),
        ViewerExifField.Lens to exif.lensModel?.trim(),
        ViewerExifField.FocalLength to focal,
        ViewerExifField.FocalLength35mm to focal35,
        ViewerExifField.Aperture to exif.apertureText,
        ViewerExifField.Shutter to shutter,
        ViewerExifField.Iso to exif.isoText,
        ViewerExifField.ExposureBias to exif.exposureBiasText,
    )
    return ViewerExifField.entries.mapNotNull { field ->
        values[field]?.takeIf { it.isNotBlank() }?.let { field to it }
    }
}

/**
 * The EXIF fields that show a placeholder while [ViewerDetails.loading]: those the read may still
 * fill. Fields that already have a value keep it; the 35 mm equivalent gets no placeholder (it is
 * often equal to the focal length and then left out), so the rows below do not jump when it is.
 */
internal fun viewerPendingExifFields(details: ViewerDetails): List<ViewerExifField> {
    if (!details.loading) return emptyList()
    val known = details.exif.map { it.first }.toSet()
    return ViewerExifField.entries.filter { it != ViewerExifField.FocalLength35mm && it !in known }
}

internal fun viewerDetails(capture: WifiCameraCapture, metadataState: PhotoMetadataState): ViewerDetails {
    val metadata = metadataState.metadataFor(capture.metadataHandle)
    val hasDeveloped = capture.files.any { it.mimeType == MIME_JPEG || it.mimeType == MIME_HEIF }
    // The developed image's size: JPEG first, then HEIF, then the metadata of the preview file. RAW
    // object info is not trusted for pixel size, so a RAW-only capture shows no resolution.
    val developed = capture.files.filter { it.mimeType == MIME_JPEG || it.mimeType == MIME_HEIF }
        .sortedBy { if (it.mimeType == MIME_JPEG) 0 else 1 }
    val resolution = developed.firstNotNullOfOrNull { photo ->
        val w = photo.width
        val h = photo.height
        if (w != null && h != null && w > 0 && h > 0) "$w×$h" else null
    } ?: metadata?.takeIf { it.format == PhotoFormat.Jpeg || it.format == PhotoFormat.Heif }?.let {
        it.resolutionText ?: it.exif?.let { e ->
            if (e.pixelWidth != null && e.pixelHeight != null) "${e.pixelWidth}×${e.pixelHeight}" else null
        }
    }
    val files = capture.files.sortedBy { viewerFormatRank(it.viewerFormatName()) }
        .map { ViewerFileEntry(it.viewerFormatName(), it.size) }
    return ViewerDetails(
        capturedAt = viewerCaptureTimeText(capture.preview.capturedAt),
        resolutionText = if (hasDeveloped) resolution else null,
        exif = viewerExifRows(metadata),
        files = files,
        loading = metadataState is PhotoMetadataState.Loading && metadataState.handle == capture.metadataHandle,
    )
}

/** States of the viewer's single download button. */
/**
 * Colour of the info strip's format text: "Only RAW" (no JPEG) is a warning. The Viewer is dark in
 * both themes, so it uses the photo role [AlphaColors.onPhotoWarning], never the theme's warningText.
 */
internal fun viewerFormatColor(noJpeg: Boolean, colors: AlphaColors): Color =
    if (noJpeg) colors.onPhotoWarning else colors.onPhoto

/**
 * Colour of the hint above the download button: in [ViewerDownloadPhase.Ready] the hint is the last
 * transfer's failure / cancel notice (error, [AlphaColors.onPhotoDanger]); otherwise an explanation.
 */
internal fun viewerDownloadHintColor(phase: ViewerDownloadPhase, colors: AlphaColors): Color =
    if (phase == ViewerDownloadPhase.Ready) colors.onPhotoDanger else colors.onPhotoSecondary

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

/**
 * Lower part of the portrait photo area (fraction of its height) in which an upward swipe opens the
 * details drawer. The info strip lies inside it. Touches above it belong to the pager alone.
 */
internal const val DETAILS_SWIPE_ZONE_FRACTION = 0.35f

/** Distance (dp) a finger must travel vertically before the drawer opens / closes. */
internal const val DETAILS_SWIPE_DISTANCE_DP = 40

/** A swipe counts as vertical only when it is this many times steeper than it is wide. */
internal const val DETAILS_SWIPE_STEEPNESS = 1.5f

/** Whether a touch going down at [y] of a photo area [height] tall may open the details drawer. */
internal fun inDetailsSwipeZone(y: Float, height: Float): Boolean =
    height > 0f && y in height * (1f - DETAILS_SWIPE_ZONE_FRACTION)..height

/**
 * Whether a finger that went down in the zone and has moved by ([dx], [dy]) opens the drawer: upward,
 * at least [distance] far and clearly vertical. A horizontal page swipe never qualifies (the pager
 * consumes it first, and its slope fails the steepness test).
 */
internal fun detailsSwipeOpens(dx: Float, dy: Float, distance: Float): Boolean =
    dy <= -distance && -dy >= kotlin.math.abs(dx) * DETAILS_SWIPE_STEEPNESS

/** Whether a drag on the drawer's handle by ([dx], [dy]) closes it: downward, far and vertical. */
internal fun detailsSwipeCloses(dx: Float, dy: Float, distance: Float): Boolean =
    dy >= distance && dy >= kotlin.math.abs(dx) * DETAILS_SWIPE_STEEPNESS

/**
 * Whether the viewer asks for the extended (EXIF) read of the shown photo. It no longer depends on
 * the details being visible: as soon as a photo is shown and the pager is at rest the viewer asks,
 * and the loader's debounce (250 ms) turns that into "read once the user stayed on the photo" — so
 * the drawer usually opens with the parameters already there, and opening / closing it never starts
 * or cancels a read. While the pager moves ([swiping]) a pending read is stopped (the photo is being
 * left; the settled page asks again), and while a download or other transfer runs ([transferBusy])
 * nothing is asked (the download interrupts the read; the viewer asks again once it is idle).
 * Neighbouring photos and the rest of the day are never read ahead.
 */
internal fun viewerWantsExtendedMetadata(transferBusy: Boolean, swiping: Boolean): Boolean =
    !transferBusy && !swiping
