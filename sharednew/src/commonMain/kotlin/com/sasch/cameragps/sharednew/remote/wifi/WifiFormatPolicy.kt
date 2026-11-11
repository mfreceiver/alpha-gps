package com.sasch.cameragps.sharednew.remote.wifi

import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy.JpegOnly
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy.Original
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy.RawOnly

private const val MIME_JPEG = "image/jpeg"
private const val MIME_HEIF = "image/heif"
private const val MIME_RAW = "image/x-sony-arw"

/**
 * The user's "Download format" setting (settings → Photo transfer), persisted as [storageValue].
 *
 * - [Ask]: every download asks — the viewer offers the formats this photo has, a batch offers JPG /
 *   RAW / JPG+RAW. The default for new installs and for unknown or missing stored values.
 * - [Jpg], [Raw], [JpgRaw]: downloads follow [WifiFormatPolicy.JpegOnly] / [WifiFormatPolicy.RawOnly] /
 *   [WifiFormatPolicy.Original] without asking.
 *
 * The stored strings of the two pre-0.3.1 values are kept, so old settings migrate unchanged:
 * "original" (all original files) is [JpgRaw] and "jpeg" is [Jpg].
 */
enum class WifiDownloadFormatSetting(val storageValue: String) {
    Ask("ask"),
    Jpg("jpeg"),
    Raw("raw"),
    JpgRaw("original");

    companion object {
        /** New installs ask on every download. */
        val Default: WifiDownloadFormatSetting = Ask

        /** The setting of a stored value: the old "original" / "jpeg" keep their meaning; anything else is [Default]. */
        fun fromStorage(value: String?): WifiDownloadFormatSetting =
            entries.firstOrNull { it.storageValue == value } ?: Default
    }
}

/** The fixed download policy of a setting; null for [WifiDownloadFormatSetting.Ask] (chosen per download). */
internal val WifiDownloadFormatSetting.policy: WifiFormatPolicy?
    get() = when (this) {
        WifiDownloadFormatSetting.Ask -> null
        WifiDownloadFormatSetting.Jpg -> JpegOnly
        WifiDownloadFormatSetting.Raw -> RawOnly
        WifiDownloadFormatSetting.JpgRaw -> Original
    }

/**
 * Which files one download fetches — the policy the download planner ([planDownloads]) and every size
 * or format label derive from. With the "ask each time" setting the user picks one per download.
 *
 * - [Original]: JPG+RAW — all original files of the selected captures.
 * - [JpegOnly]: only the existing JPEG file; captures without one (RAW-only, HEIF-only) are skipped and
 *   counted, never converted.
 * - [RawOnly]: only the existing RAW (ARW) file; captures without one (JPG-only, HEIF-only) are skipped
 *   and counted, never replaced by the JPG.
 *
 * The grid badge is independent of it.
 */
internal enum class WifiFormatPolicy {
    Original,
    JpegOnly,
    RawOnly,
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
internal enum class WifiSkipReason { NoJpeg, NoRaw, AlreadySaved, TooLarge }

/** The capture lacks the one format the policy takes (no JPEG under JPG only, no RAW under RAW only). */
internal val WifiSkipReason.isMissingFormat: Boolean
    get() = this == WifiSkipReason.NoJpeg || this == WifiSkipReason.NoRaw

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

    /**
     * Why an empty plan is empty, by a fixed priority that never depends on counts or file order:
     * [WifiSkipReason.AlreadySaved] only when *every* skipped candidate was already saved; otherwise
     * [WifiSkipReason.TooLarge] wins (something is over the limit and nothing can be fetched), then
     * [WifiSkipReason.NoJpeg], then [WifiSkipReason.NoRaw]. Null when nothing was skipped.
     */
    val mainSkipReason: WifiSkipReason? get() {
        val reasons = skipped.filterValues { it > 0 }.keys
        return when {
            reasons.isEmpty() -> null
            reasons == setOf(WifiSkipReason.AlreadySaved) -> WifiSkipReason.AlreadySaved
            WifiSkipReason.TooLarge in reasons -> WifiSkipReason.TooLarge
            WifiSkipReason.NoJpeg in reasons -> WifiSkipReason.NoJpeg
            WifiSkipReason.NoRaw in reasons -> WifiSkipReason.NoRaw
            else -> reasons.first()
        }
    }
}

/**
 * The single download planner. Every policy downloads original bytes only — nothing is converted.
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
            RawOnly -> {
                val raws = capture.files.filter { it.mimeType == MIME_RAW }
                val raw = raws.firstOrNull { it.downloadable } ?: raws.firstOrNull()
                if (raw == null) skip(WifiSkipReason.NoRaw) else consider(raw)
            }
        }
    }
    return WifiDownloadPlan(files.values.toList(), skipped)
}

/**
 * The size shown for one capture (grid size tag, viewer info strip): what a download of it under
 * [policy] fetches, planned exactly like the batch button's plan — including [saved], so a tag never
 * counts a file that is already on the phone. The byte counts of the tiles of a selection therefore
 * agree with the floating button's plan; the rounded numbers on screen are each rounded on their own
 * and need not add up to the rounded total.
 *
 * [CaptureDownloadSize.reason] says why nothing would be fetched (null = the bytes will download):
 * - [WifiSkipReason.NoJpeg] / [WifiSkipReason.NoRaw] — JPG-only policy over a RAW/HEIF-only capture,
 *   or RAW-only policy over a JPG/HEIF-only capture: the size of the original files is still shown with
 *   the reason, so the UI can mark it ("only RAW" / "only JPG") — the user-confirmed exception of
 *   showing a size that will not download.
 * - [WifiSkipReason.TooLarge] — every candidate file is over the download limit: nothing is shown.
 * - [WifiSkipReason.AlreadySaved] — every candidate file was saved earlier in this catalog
 *   generation: the tile shows its saved state instead of a size.
 */
internal data class CaptureDownloadSize(val bytes: Long, val reason: WifiSkipReason? = null)

internal fun captureDownloadSize(
    capture: WifiCameraCapture,
    policy: WifiFormatPolicy,
    saved: Set<WifiPhotoDownload> = emptySet(),
): CaptureDownloadSize {
    val plan = planDownloads(listOf(capture), policy, saved)
    if (plan.files.isNotEmpty()) return CaptureDownloadSize(plan.totalBytes)
    return when (plan.mainSkipReason) {
        WifiSkipReason.NoJpeg, WifiSkipReason.NoRaw ->
            // The missing-format exception shows the originals' size as a warning — unless the user
            // already saved every original file by hand (the viewer's "Download RAW"): then it is
            // saved, exactly like the viewer's button says.
            planDownloads(listOf(capture), WifiFormatPolicy.Original, saved).let { originals ->
                if (originals.files.isEmpty() && originals.mainSkipReason == WifiSkipReason.AlreadySaved)
                    CaptureDownloadSize(0, WifiSkipReason.AlreadySaved)
                else CaptureDownloadSize(capture.files.sumOf { it.size }, plan.mainSkipReason)
            }
        WifiSkipReason.TooLarge -> CaptureDownloadSize(0, WifiSkipReason.TooLarge)
        WifiSkipReason.AlreadySaved -> CaptureDownloadSize(0, WifiSkipReason.AlreadySaved)
        null -> CaptureDownloadSize(0)
    }
}

/** The choices of the "ask each time" chooser, in display order: JPG, RAW, JPG+RAW. */
internal val askFormatPolicies: List<WifiFormatPolicy> = listOf(JpegOnly, RawOnly, Original)

/**
 * One choice of the "ask each time" chooser: the [policy] the user may pick and the [plan] it would run
 * — planned by [planDownloads] exactly like the fixed settings, so files, sizes and skip counts agree.
 */
internal data class WifiFormatChoice(val policy: WifiFormatPolicy, val plan: WifiDownloadPlan) {
    val available: Boolean get() = plan.files.isNotEmpty()
}

/**
 * Choices for a batch under "ask each time": always JPG, RAW and JPG+RAW (in that order), each with
 * its own plan over the selection; a choice that would fetch nothing is shown but not available.
 */
internal fun batchFormatChoices(
    captures: List<WifiCameraCapture>,
    saved: Set<WifiPhotoDownload>,
): List<WifiFormatChoice> = askFormatPolicies.map { WifiFormatChoice(it, planDownloads(captures, it, saved)) }

/**
 * Choices for one capture under "ask each time": only the formats it actually has and that still need
 * fetching (files already saved or over the download limit are left out by the planner). Choices that
 * would fetch the same files collapse into the first one — a JPG-only capture offers just "JPG", a
 * JPG + RAW capture whose JPG is already saved just "RAW". One choice (or none) means no question.
 */
internal fun singleFormatChoices(
    capture: WifiCameraCapture,
    saved: Set<WifiPhotoDownload>,
): List<WifiFormatChoice> {
    val choices = mutableListOf<WifiFormatChoice>()
    for (policy in askFormatPolicies) {
        val plan = planDownloads(listOf(capture), policy, saved)
        if (plan.files.isEmpty()) continue
        val handles = plan.files.map { it.handle }.toSet()
        if (choices.any { choice -> choice.plan.files.map { it.handle }.toSet() == handles }) continue
        choices += WifiFormatChoice(policy, plan)
    }
    return choices
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

/**
 * One shared number formatting for every size label: binary units, rounded half up exactly once
 * from the raw byte count (never rounded twice). Below 10 units one decimal; a value that reaches
 * 10 units (including 9.95..9.99, which would round to "10.0") shows the integer, itself rounded
 * half up from the raw bytes. The unit is added by the caller ("MB" / "GB").
 */
private fun tenths(bytes: Long, unit: Long): String {
    val t = (bytes * 10 + unit / 2) / unit
    return if (t >= 100) "${(bytes + unit / 2) / unit}" else "${t / 10}.${t % 10}"
}

/** "512 KB", "4.8 MB", "48 MB", "1.3 GB" — binary units, no locale-specific formatting. */
internal fun formatByteSize(bytes: Long): String {
    val kb = 1024L
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes < mb -> "${maxOf(0L, (bytes + kb - 1) / kb)} KB"
        bytes < gb -> "${tenths(bytes, mb)} MB"
        else -> "${tenths(bytes, gb)} GB"
    }
}

/**
 * The unitless number of the grid's size tag and of any MB-scale label: one decimal (binary MB,
 * half up; no decimal from 10 MB up), "<0.1" below 0.1 MB, null for an unknown (zero) size. Always
 * agrees with [formatByteSize] on the same byte count (modulo the KB branch).
 */
internal fun formatSizeNumber(bytes: Long): String? {
    if (bytes <= 0) return null
    val mb = 1024L * 1024L
    if (bytes * 10 < mb) return "<0.1"
    return tenths(bytes, mb)
}
