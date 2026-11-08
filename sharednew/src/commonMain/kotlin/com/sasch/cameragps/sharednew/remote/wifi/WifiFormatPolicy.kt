package com.sasch.cameragps.sharednew.remote.wifi

import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy.JpegOnly
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy.Original

private const val MIME_JPEG = "image/jpeg"
private const val MIME_HEIF = "image/heif"
private const val MIME_RAW = "image/x-sony-arw"

/**
 * The user's "default download format" setting as one policy that the grid badge, the
 * details page, the batch bar and the settings copy all derive from.
 *
 * - [Original]: every original format is shown and the user chooses before downloading.
 * - [JpegOnly]: lists, details and batches only talk about the JPG; one tap downloads it.
 *   A capture without any JPG (typically RAW-only) is never downloaded silently and never
 *   hidden from the grid: it is flagged, skipped by batches, and downloadable by hand
 *   from its details page.
 *
 * Note: [WifiPhotoDownloadFormat.Jpeg] is the HEIF→JPEG *transcode*, not "the JPG file".
 * It is only used here as the JPG source of a HEIF-only capture when the platform can convert.
 */
internal enum class WifiFormatPolicy {
    Original,
    JpegOnly;

    companion object {
        fun of(jpegOnly: Boolean): WifiFormatPolicy = if (jpegOnly) JpegOnly else Original
    }
}

/** Where the JPG of a capture comes from and how it is requested. */
internal data class WifiJpegTarget(val file: WifiCameraPhoto, val download: WifiPhotoDownload) {
    /** True when the JPG is produced by transcoding a HEIF original (size is not known). */
    val converted: Boolean get() = download.format == WifiPhotoDownloadFormat.Jpeg

    /** Size of the JPG, or null when it is only produced by a conversion. */
    val knownSize: Long? get() = if (converted) null else file.size
}

/**
 * The JPG of a capture: its JPEG file (preferring a downloadable one), else — when the
 * platform can transcode — its HEIF as a JPEG copy. Null when the capture has no JPG
 * (e.g. RAW-only).
 */
internal fun WifiCameraCapture.jpegTarget(canConvertHeif: Boolean): WifiJpegTarget? {
    val jpegs = files.filter { it.mimeType == MIME_JPEG }
    (jpegs.firstOrNull { it.downloadable } ?: jpegs.firstOrNull())?.let {
        return WifiJpegTarget(it, WifiPhotoDownload(it.handle))
    }
    if (canConvertHeif) {
        val heifs = files.filter { it.mimeType == MIME_HEIF }
        (heifs.firstOrNull { it.downloadable } ?: heifs.firstOrNull())?.let {
            return WifiJpegTarget(it, WifiPhotoDownload(it.handle, WifiPhotoDownloadFormat.Jpeg))
        }
    }
    return null
}

/**
 * What the JpegOnly details page offers by hand when a capture has no JPG: its RAW if it
 * has one, otherwise its first remaining original.
 */
internal fun WifiCameraCapture.manualOriginal(): WifiCameraPhoto? =
    files.firstOrNull { it.mimeType == MIME_RAW } ?: files.firstOrNull { it.downloadable } ?: files.firstOrNull()

/** Grid corner badge for one capture. */
internal sealed interface WifiFormatBadge {
    data object None : WifiFormatBadge

    /** Format names that are not localized, e.g. "JPEG + RAW". */
    data class Label(val text: String) : WifiFormatBadge

    /** JpegOnly: the capture has nothing but RAW. Localized by the UI ("RAW only"). */
    data object RawOnly : WifiFormatBadge
}

internal fun WifiCameraCapture.formatBadge(policy: WifiFormatPolicy, canConvertHeif: Boolean): WifiFormatBadge =
    when (policy) {
        Original -> WifiFormatBadge.Label(files.joinToString(" + ") { it.formatLabel })
        JpegOnly -> when {
            jpegTarget(canConvertHeif) != null -> WifiFormatBadge.None
            files.all { it.mimeType == MIME_RAW } -> WifiFormatBadge.RawOnly
            // No JPG and not RAW-only (e.g. HEIF the platform cannot convert): name what
            // is there, but never mention a RAW companion in JPG-only mode.
            else -> WifiFormatBadge.Label(
                files.filter { it.mimeType != MIME_RAW }.joinToString(" + ") { it.formatLabel }
            )
        }
    }

/** How the details page of one capture behaves under a [WifiFormatPolicy]. */
internal sealed interface WifiDetailPlan {
    /** Original: the format list with radio selection stays. */
    data object ChooseFormat : WifiDetailPlan

    /** JpegOnly: a single "Download JPG (size)" button. */
    data class DownloadJpeg(val target: WifiJpegTarget) : WifiDetailPlan

    /** JpegOnly without a JPG: say so, offer [fallback] manually (null if nothing at all). */
    data class NoJpeg(val fallback: WifiCameraPhoto?) : WifiDetailPlan
}

internal fun WifiCameraCapture.detailPlan(policy: WifiFormatPolicy, canConvertHeif: Boolean): WifiDetailPlan =
    when (policy) {
        Original -> WifiDetailPlan.ChooseFormat
        JpegOnly -> jpegTarget(canConvertHeif)?.let { WifiDetailPlan.DownloadJpeg(it) }
            ?: WifiDetailPlan.NoJpeg(manualOriginal())
    }

/** Why a selected capture contributes nothing to a JpegOnly batch. */
internal enum class WifiSkipReason { NoJpeg, AlreadySaved, TooLarge }

internal data class WifiJpegBatchPlan(
    val downloads: List<WifiPhotoDownload>,
    /** Sum of the source file sizes (a transcoded HEIF counts with its HEIF size). */
    val totalBytes: Long,
    val skipped: Map<WifiSkipReason, Int>,
) {
    val skippedCount: Int get() = skipped.values.sum()
}

/**
 * JpegOnly batch: one JPG per capture in selection order. RAW-only captures are skipped
 * (never downloaded silently), as are JPGs saved earlier in this catalog generation and
 * JPGs over the download limit.
 */
internal fun jpegOnlyBatchPlan(
    captures: List<WifiCameraCapture>,
    saved: Set<WifiPhotoDownload>,
    canConvertHeif: Boolean,
): WifiJpegBatchPlan {
    val downloads = LinkedHashMap<WifiPhotoDownload, Long>()
    val skipped = LinkedHashMap<WifiSkipReason, Int>()
    fun skip(reason: WifiSkipReason) {
        skipped[reason] = (skipped[reason] ?: 0) + 1
    }
    for (capture in captures) {
        val target = capture.jpegTarget(canConvertHeif)
        when {
            target == null -> skip(WifiSkipReason.NoJpeg)
            !target.file.downloadable -> skip(WifiSkipReason.TooLarge)
            target.download in saved -> skip(WifiSkipReason.AlreadySaved)
            else -> downloads.putIfAbsent(target.download, target.file.size)
        }
    }
    return WifiJpegBatchPlan(downloads.keys.toList(), downloads.values.sum(), skipped)
}

/** Result line shown after a JpegOnly batch: how many were downloaded, how many skipped. */
internal data class WifiBatchSummary(val downloaded: Int, val skipped: Map<WifiSkipReason, Int>) {
    val skippedCount: Int get() = skipped.values.sum()

    /** The batch ran to the end: every submitted file is saved (a failure stops it earlier). */
    fun isComplete(transfer: WifiImageTransferState): Boolean =
        downloaded > 0 && transfer.status == WifiImageTransferStatus.Saved &&
                transfer.completedFiles == downloaded && transfer.totalFiles == downloaded
}

internal fun WifiJpegBatchPlan.toSummary() = WifiBatchSummary(downloads.size, skipped)

/**
 * Whether the settled batch summary line may replace the transfer status line on the
 * grid. A busy, failed or cancelled transfer always wins: the user must see what the
 * current download is doing instead of a stale "downloaded N, skipped M" result.
 */
internal fun shouldShowBatchSummary(summarySettled: Boolean, transfer: WifiImageTransferState): Boolean =
    summarySettled && (transfer.status == WifiImageTransferStatus.Saved ||
            transfer.status == WifiImageTransferStatus.Idle)

/** "512 KB", "4.8 MB", "48 MB", "1.2 GB" — binary units, no locale-specific formatting. */
internal fun formatByteSize(bytes: Long): String {
    val kb = 1024L
    val mb = kb * 1024
    val gb = mb * 1024
    fun tenths(unit: Long): String {
        val t = bytes * 10 / unit
        return if (t >= 100) "${t / 10}" else "${t / 10}.${t % 10}"
    }
    return when {
        bytes < mb -> "${maxOf(0L, (bytes + kb - 1) / kb)} KB"
        bytes < gb -> "${tenths(mb)} MB"
        else -> "${tenths(gb)} GB"
    }
}
