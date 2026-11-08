package com.sasch.cameragps.sharednew.remote.wifi

internal enum class WifiPhotoBatchFormat(
    val mimeType: String,
    val format: WifiPhotoDownloadFormat = WifiPhotoDownloadFormat.Original
) {
    Jpeg("image/jpeg"),
    Raw("image/x-sony-arw"),
    Heif("image/heif"),
    JpegCopy("image/heif", WifiPhotoDownloadFormat.Jpeg),
}

internal fun photoBatchDownloads(
    captures: List<WifiCameraCapture>,
    formats: Set<WifiPhotoBatchFormat>,
    saved: Set<WifiPhotoDownload>,
    canConvertHeif: Boolean,
): List<WifiPhotoDownload> = captures.flatMap { capture ->
    capture.files.filter { it.downloadable }.flatMap { file ->
        formats.filter { it.mimeType == file.mimeType && (it != WifiPhotoBatchFormat.JpegCopy || canConvertHeif) }
            .map { WifiPhotoDownload(file.handle, it.format) }
    }
}.distinct().filterNot { it in saved }

/**
 * Initial checked-format set for the batch download sheet, derived from the user's
 * default download format:
 * - Original (the default): every available original format is checked (JPEG *and* RAW).
 *   [WifiPhotoBatchFormat.JpegCopy] stays unchecked — it is a HEIF→JPEG conversion, not
 *   an "original".
 * - JPEG-only: JPEG is checked when available. If no JPEG is downloadable (already saved
 *   or never shot), the set is deliberately **empty** — never silently falling back to
 *   RAW — so the user explicitly picks what to download.
 */
internal fun defaultBatchFormats(
    available: Set<WifiPhotoBatchFormat>,
    defaultJpegOnly: Boolean,
): Set<WifiPhotoBatchFormat> = when {
    defaultJpegOnly ->
        if (WifiPhotoBatchFormat.Jpeg in available) setOf(WifiPhotoBatchFormat.Jpeg) else emptySet()
    else -> available - WifiPhotoBatchFormat.JpegCopy
}

/**
 * Initial selected file handle for the single-photo sheet. JPEG-only prefers the JPEG as
 * before; with the Original default the first downloadable file is preselected so a
 * JPG+RAW pair yields a concrete selection (and an enabled download button) instead of
 * nothing — the sheet is single-select, so "all formats" there means the user confirms
 * per file, and the first file (camera list order) is the least surprising default.
 */
internal fun defaultSelectedPhotoHandle(
    files: List<WifiCameraPhoto>,
    defaultJpegOnly: Boolean,
): Long? {
    if (defaultJpegOnly) {
        return files.firstOrNull { it.mimeType == "image/jpeg" && it.downloadable }?.handle
            ?: files.singleOrNull()?.handle
    }
    return files.firstOrNull { it.downloadable }?.handle ?: files.singleOrNull()?.handle
}
