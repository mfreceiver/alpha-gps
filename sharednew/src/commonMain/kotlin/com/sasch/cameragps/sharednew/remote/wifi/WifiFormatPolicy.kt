package com.sasch.cameragps.sharednew.remote.wifi

import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy.JpegOnly
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy.Original

private const val MIME_JPEG = "image/jpeg"
private const val MIME_HEIF = "image/heif"
private const val MIME_RAW = "image/x-sony-arw"

/**
 * The user's "default download format" setting as one policy that the download planner
 * ([planDownloads]) and the settings copy derive from.
 *
 * - [Original]: a download fetches all original files of the selected captures.
 * - [JpegOnly]: a download fetches only the existing JPEG file; captures without one (RAW-only,
 *   HEIF-only) are skipped and counted, never converted. The grid badge is independent of it.
 */
internal enum class WifiFormatPolicy {
    Original,
    JpegOnly;

    companion object {
        fun of(jpegOnly: Boolean): WifiFormatPolicy = if (jpegOnly) JpegOnly else Original
    }
}

/**
 * Grid corner badge for one capture, decided purely by the MIME types of its files (never by the
 * download policy, and never claiming a format the card does not hold): JPEG -> "JPG", RAW -> "RAW",
 * HEIF -> "HEIF"; two or more formats collapse to their initials joined by "&" (JPEG+RAW "J&R",
 * HEIF+RAW "H&R"). Null when no file has a known format. Format names are not localized.
 */
internal fun WifiCameraCapture.formatBadge(): String? {
    val jpeg = files.any { it.mimeType == MIME_JPEG }
    val heif = files.any { it.mimeType == MIME_HEIF }
    val raw = files.any { it.mimeType == MIME_RAW }
    return when (listOf(jpeg, heif, raw).count { it }) {
        0 -> null
        1 -> when {
            jpeg -> "JPG"
            heif -> "HEIF"
            else -> "RAW"
        }
        else -> listOfNotNull("J".takeIf { jpeg }, "H".takeIf { heif }, "R".takeIf { raw }).joinToString("&")
    }
}

/** Why a selected capture or file contributes nothing to a download. */
internal enum class WifiSkipReason { NoJpeg, AlreadySaved, TooLarge }

/**
 * What one download action will fetch: the exact [files] (original bytes, never a transcode) and what
 * was skipped and why. The same plan drives the grid's batch action, the viewer's download button and
 * the details button, so all of them agree on files, sizes and skip counts.
 */
internal data class WifiDownloadPlan(
    val files: List<WifiCameraPhoto>,
    val skipped: Map<WifiSkipReason, Int>,
) {
    val downloads: List<WifiPhotoDownload> get() = files.map { WifiPhotoDownload(it.handle) }

    /** Sum of the planned file sizes. */
    val totalBytes: Long get() = files.sumOf { it.size }
    val skippedCount: Int get() = skipped.values.sum()

    /** The biggest reason something was skipped, i.e. why an empty plan is empty. */
    val mainSkipReason: WifiSkipReason? get() = skipped.entries.filter { it.value > 0 }.maxByOrNull { it.value }?.key
}

/**
 * The single download planner. Both policies download original bytes only — nothing is converted.
 *
 * - [Original]: every file of each capture (JPG+RAW downloads both). Files saved earlier in this
 *   catalog generation or over the download limit are skipped.
 * - [JpegOnly]: only the capture's JPEG file (MIME `image/jpeg`; a downloadable one preferred). A
 *   capture without a JPEG — RAW-only or HEIF-only alike — is skipped as [WifiSkipReason.NoJpeg]; there
 *   is no transcode and no fallback to another format.
 *
 * Files keep selection order and appear once even if the same capture is selected twice.
 */
internal fun planDownloads(
    captures: List<WifiCameraCapture>,
    policy: WifiFormatPolicy,
    saved: Set<WifiPhotoDownload>,
): WifiDownloadPlan {
    val files = LinkedHashMap<Long, WifiCameraPhoto>()
    val skipped = LinkedHashMap<WifiSkipReason, Int>()
    fun skip(reason: WifiSkipReason) {
        skipped[reason] = (skipped[reason] ?: 0) + 1
    }
    fun consider(file: WifiCameraPhoto) {
        when {
            !file.downloadable -> skip(WifiSkipReason.TooLarge)
            WifiPhotoDownload(file.handle) in saved -> skip(WifiSkipReason.AlreadySaved)
            else -> files.putIfAbsent(file.handle, file)
        }
    }
    for (capture in captures) {
        when (policy) {
            Original -> capture.files.forEach(::consider)
            JpegOnly -> {
                val jpegs = capture.files.filter { it.mimeType == MIME_JPEG }
                val jpeg = jpegs.firstOrNull { it.downloadable } ?: jpegs.firstOrNull()
                if (jpeg == null) skip(WifiSkipReason.NoJpeg) else consider(jpeg)
            }
        }
    }
    return WifiDownloadPlan(files.values.toList(), skipped)
}

/** Result line shown after a batch: how many files were downloaded, how many skipped. */
internal data class WifiBatchSummary(val downloaded: Int, val skipped: Map<WifiSkipReason, Int>) {
    val skippedCount: Int get() = skipped.values.sum()

    /**
     * The batch is settled: either it ran to the end — every submitted file is saved (a failure stops
     * it earlier) — or nothing was submitted at all (everything skipped), which is a local result that
     * never waits for a transfer.
     */
    fun isComplete(transfer: WifiImageTransferState): Boolean =
        if (downloaded == 0) true
        else transfer.status == WifiImageTransferStatus.Saved &&
                transfer.completedFiles == downloaded && transfer.totalFiles == downloaded
}

internal fun WifiDownloadPlan.toSummary() = WifiBatchSummary(files.size, skipped)

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
