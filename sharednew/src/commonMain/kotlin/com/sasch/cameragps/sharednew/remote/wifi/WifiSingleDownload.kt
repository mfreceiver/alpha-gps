package com.sasch.cameragps.sharednew.remote.wifi

/** One thing the viewer's Download button can fetch for a capture. */
internal data class WifiDownloadOption(
    val file: WifiCameraPhoto,
    val format: WifiPhotoDownloadFormat,
    val saved: Boolean,
) {
    val download: WifiPhotoDownload get() = WifiPhotoDownload(file.handle, format)

    /** HEIF transcoded to a JPEG copy on the phone. */
    val converted: Boolean get() = format == WifiPhotoDownloadFormat.Jpeg

    /** Not already saved this catalog generation and within the download limit. */
    val enabled: Boolean get() = file.downloadable && !saved
}

/**
 * Every original file of the capture, plus a JPEG copy for each HEIF when the platform
 * can transcode. Used by the Original-format policy; JPG-only has its own plan.
 */
internal fun WifiCameraCapture.downloadOptions(
    canConvertHeif: Boolean,
    saved: Set<WifiPhotoDownload>,
): List<WifiDownloadOption> = files.flatMap { file ->
    val formats = if (canConvertHeif && file.mimeType == "image/heif") {
        WifiPhotoDownloadFormat.entries
    } else {
        listOf(WifiPhotoDownloadFormat.Original)
    }
    formats.map { format ->
        WifiDownloadOption(file, format, WifiPhotoDownload(file.handle, format) in saved)
    }
}

/** What tapping the viewer's Download button does for a capture. */
internal sealed interface WifiSingleDownloadAction {
    /** Nothing left to download (all saved / over the limit). */
    data object Unavailable : WifiSingleDownloadAction

    /** Exactly one downloadable choice: download it right away, no extra step. */
    data class Direct(val option: WifiDownloadOption) : WifiSingleDownloadAction

    /** Several choices (e.g. JPG + RAW): let the user pick which to download. */
    data class Choose(val options: List<WifiDownloadOption>) : WifiSingleDownloadAction
}

internal fun singleDownloadAction(options: List<WifiDownloadOption>): WifiSingleDownloadAction {
    val enabled = options.filter { it.enabled }
    return when (enabled.size) {
        0 -> WifiSingleDownloadAction.Unavailable
        1 -> WifiSingleDownloadAction.Direct(enabled.single())
        else -> WifiSingleDownloadAction.Choose(options)
    }
}
