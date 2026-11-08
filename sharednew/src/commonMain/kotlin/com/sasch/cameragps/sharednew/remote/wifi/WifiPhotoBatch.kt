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
 * Initial checked-format set for the Original-mode batch sheet: every available original
 * format is checked (JPEG *and* RAW). [WifiPhotoBatchFormat.JpegCopy] stays unchecked — it
 * is a HEIF→JPEG conversion, not an "original". JPG-only mode bypasses the sheet entirely
 * (see [jpegOnlyBatchPlan]).
 */
internal fun defaultBatchFormats(available: Set<WifiPhotoBatchFormat>): Set<WifiPhotoBatchFormat> =
    available - WifiPhotoBatchFormat.JpegCopy

/**
 * Initial selected file handle for the Original-mode single-photo details: the first
 * downloadable file, so a JPG+RAW pair yields a concrete selection (and an enabled download
 * button) instead of nothing. Falls back to a lone file even when it is not downloadable
 * so the details can explain why.
 */
internal fun defaultSelectedPhotoHandle(files: List<WifiCameraPhoto>): Long? =
    files.firstOrNull { it.downloadable }?.handle ?: files.singleOrNull()?.handle
