package com.sasch.cameragps.sharednew.remote.wifi

import androidx.compose.ui.graphics.ImageBitmap
import com.diamondedge.logging.logging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal interface CameraImageDestination {
    suspend fun write(bytes: ByteArray)
    suspend fun prepare() {}
    suspend fun commit()

    /** Removes an incomplete file; harmless after commit. */
    suspend fun abort()
}

internal fun interface CameraImageStore {
    suspend fun create(info: SonyImageInfo): CameraImageDestination
    val canConvertHeif: Boolean get() = false
    suspend fun createJpeg(info: SonyImageInfo): CameraImageDestination = error("HEIF conversion unavailable")
}

internal data class SonyImageInfo(
    val size: Long,
    val filename: String,
    val mimeType: String,
    val capturedAt: String = "",
    val captureId: String? = null,
) {
    companion object {
        fun parse(data: ByteArray): SonyImageInfo {
            require(data.size >= 53) { "Truncated image information" }
            fun number(offset: Int, size: Int): Long = (0 until size).fold(0L) { value, i ->
                value or ((data[offset + i].toLong() and 255) shl (i * 8))
            }

            val format = number(4, 2).toInt()
            val size = number(8, 4)
            val mime = when (format) {
                0x3801 -> "image/jpeg"
                0xb101 -> "image/x-sony-arw"
                0xb110 -> "image/heif"
                else -> ""
            }
            var offset = 52
            fun string(): String {
                require(offset < data.size)
                val count = data[offset++].toInt() and 255
                require(count * 2 <= data.size - offset)
                if (count == 0) return ""
                val chars = CharArray(count) { number(offset + it * 2, 2).toInt().toChar() }
                offset += count * 2
                require(chars.last() == '\u0000') { "Unterminated image filename" }
                return chars.concatToString(0, count - 1)
            }

            val filename = string()
            val capturedAt = string()
            repeat(2) { string() }
            require(offset == data.size)
            require(
                filename.length in 1..200 && filename != "." && filename != ".." &&
                        filename.none { it == '/' || it == '\\' || it.code < 32 || it.code == 127 }) {
                "Invalid image filename"
            }
            val captureId = "object:${number(0, 4)}:${number(38, 4)}:" +
                "${filename.substringBeforeLast('.').uppercase()}:$capturedAt"
            return SonyImageInfo(size, filename, mime, capturedAt, captureId)
        }
    }
}

internal data class CameraPhotoPage(
    val photos: List<WifiCameraPhoto>,
    val offset: Int,
    val totalObjects: Int,
    val hasMore: Boolean = false
)

/** Card browsing is exclusive with live view and shutter operation. */
internal class SonyImageTransfer(
    private val commands: PtpIpCommandQueue,
    private val capabilities: SonyPtpInitializationResult.Ready,
    events: PtpIpEventMonitor? = null,
    private val decodeNonJpegThumbnail: (ByteArray) -> ImageBitmap? = { null },
    private val decodePreview: (ByteArray) -> ImageBitmap? = { null },
    private val store: CameraImageStore,
) {
    private val log = logging()
    private val catalog = if (events != null && SonyContentCatalog.supported(capabilities))
        SonyContentCatalog(commands, capabilities, events) else null
    val supported: Boolean = catalog != null || listOf(
        GET_STORAGE_IDS,
        GET_HANDLES,
        GET_PROPERTIES,
        SET_TRANSFER_MODE
    )
        .all(capabilities.deviceInfo::supports) &&
            (capabilities.deviceInfo.supports(GET_OBJECT_INFO) || capabilities.deviceInfo.supports(
                GET_OBJECT_PROP_LIST
            )) &&
            (capabilities.deviceInfo.supports(GET_PARTIAL_LARGE_OBJECT) || capabilities.deviceInfo.supports(
                GET_PARTIAL_OBJECT
            )) &&
            capabilities.extendedInfo.supportsProperty(TRANSFER_ENABLED)
    private var modeRequested = false
    private var snapshot = CatalogSnapshot.EMPTY
    private var pagePhotos = emptyList<WifiCameraPhoto>()

    /** Unfiltered browser: enumerates the card, then shows the first page of everything. */
    suspend fun openBrowser(): CameraPhotoPage {
        enumerate()
        return page(0)
    }

    /**
     * Date-grouped browser: enumerates the whole card (the only camera traffic needed to know the
     * days) and returns the days. No page is displayed, so no photo is addressable until [datePage].
     */
    suspend fun openCatalog(): List<DateGroup> {
        enumerate()
        pagePhotos = emptyList()
        return snapshot.dates
    }

    /**
     * One page of a single day, served from the local snapshot. The result is also recorded as the
     * displayed page: thumbnails and previews only answer for handles of that page.
     */
    fun datePage(dateKey: String, offset: Int): CameraPhotoPage =
        snapshot.page(dateKey, offset).also { pagePhotos = it.photos }

    private suspend fun enumerate() {
        check(supported)
        catalog?.let {
            snapshot = CatalogSnapshot.EMPTY
            pagePhotos = emptyList()
            snapshot = CatalogSnapshot.of(it.enumerate())
            return
        }
        if (!modeRequested) {
            modeRequested = true
            accepted(commands.executeNoData(SET_TRANSFER_MODE, listOf(2L, 1L, 0L)))
        }
        withTimeout(10.seconds) {
            val flags =
                if ((capabilities.vendorCodeVersion ?: 0) >= 310) listOf(1L) else emptyList()
            while (true) {
                val properties = SonyCameraProperties.parse(
                    data(GET_PROPERTIES, listOf(0L) + flags),
                    capabilities.extendedInfo.controlCodes, setOf(TRANSFER_ENABLED)
                )
                if (properties[TRANSFER_ENABLED]?.value?.and(0xffff) == 1L) break
                delay(250.milliseconds)
            }
        }
        val storages = parseIds(data(GET_STORAGE_IDS, limit = 1028), 256)
        val found = mutableListOf<Long>()
        for (storage in storages) {
            found += parseIds(data(GET_HANDLES, listOf(storage, 0L, 0L), 400_004), 100_000)
            require(found.size <= 100_000) { "Too many camera objects" }
        }
        val photos = mutableListOf<WifiCameraPhoto>()
        for (handle in found.distinct().asReversed()) {
            val info = objectInfo(handle)
            if (info.mimeType.isNotEmpty()) photos += WifiCameraPhoto(
                handle, info.filename, info.size, info.mimeType, info.capturedAt,
                info.size in 1..MAX_IMAGE_BYTES, info.captureId
            )
        }
        snapshot = CatalogSnapshot.of(groupCameraPhotos(photos.sortedByDescending { it.capturedAt }))
        pagePhotos = emptyList()
    }

    suspend fun page(offset: Int): CameraPhotoPage {
        catalog?.let { return it.page(offset).also { page -> pagePhotos = page.photos } }
        check(modeRequested)
        return cameraPhotoPage(snapshot.captures, offset).also { pagePhotos = it.photos }
    }

    suspend fun thumbnail(handle: Long, stats: ThumbnailStatsSink? = null): ImageBitmap? {
        if (!(catalog?.hasThumbnails
                ?: capabilities.deviceInfo.supports(GET_THUMB)) || pagePhotos.none { it.handle == handle }
        ) return null
        return try {
            val requestStarted = TimeSource.Monotonic.markNow()
            val bytes = catalog?.thumbnail(handle) ?: data(GET_THUMB, listOf(handle), 512 * 1024)
            stats?.onThumbnailBytes(handle, bytes.size, requestStarted.elapsedNow().inWholeMilliseconds)
            withContext(Dispatchers.Default) {
                if (bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()) {
                    val jpeg = SonyJpeg.inspect(bytes)
                    require(jpeg.width <= 640 && jpeg.height <= 640)
                    bytes.copyOf(jpeg.byteCount).decodeToImageBitmap()
                        .rotateByExifOrientation(jpeg.orientation)
                } else decodeNonJpegThumbnail(bytes)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    suspend fun preview(handle: Long): ImageBitmap? {
        val photo = pagePhotos.firstOrNull { it.handle == handle } ?: return null
        if (photo.mimeType == "image/x-sony-arw") {
            try {
                val embedded = SonyArwPreview.read(photo.size) { offset, length ->
                    read(
                        handle,
                        offset,
                        length
                    )
                }
                if (embedded != null) return withContext(Dispatchers.Default) { embedded.decode() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Other ARW layouts and rejected partial reads retain the camera preview fallback.
            }
        }
        val catalog = catalog ?: return null
        if (!catalog.hasThumbnails) return null
        return try {
            val bytes = catalog.preview(handle)
            withContext(Dispatchers.Default) {
                if (bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()) {
                    val jpeg = SonyJpeg.inspect(bytes)
                    require(
                        jpeg.width <= 4096 && jpeg.height <= 4096 &&
                                jpeg.width.toLong() * jpeg.height <= 8 * 1024 * 1024
                    )
                    bytes.copyOf(jpeg.byteCount).decodeToImageBitmap()
                        .rotateByExifOrientation(jpeg.orientation)
                } else decodePreview(bytes)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    suspend fun download(
        handle: Long,
        format: WifiPhotoDownloadFormat = WifiPhotoDownloadFormat.Original,
        onProgress: (WifiImageTransferState) -> Unit
    ): WifiImageTransferState {
        // Downloads may target any file from the opened catalog, not only the current page.
        val photo = (catalog?.file(handle)
            ?: snapshot.captures.asSequence().flatMap { it.files }.singleOrNull { it.handle == handle })
            ?.takeIf { it.downloadable }
            ?: return WifiImageTransferState(
                WifiImageTransferStatus.Failed,
                failureReason = WifiTransferFailureReason.CatalogChanged
            )
        val convert = format == WifiPhotoDownloadFormat.Jpeg
        if (convert && (photo.mimeType != "image/heif" || !store.canConvertHeif)) {
            return WifiImageTransferState(WifiImageTransferStatus.ConversionFailed, photo.filename)
        }
        val filename = if (convert) photo.filename.substringBeforeLast('.') + ".jpg" else photo.filename
        var converting = false
        var destination: CameraImageDestination? = null
        var committed = false
        // Set by the wrapped failure sites below; the generic catch keeps it as Unknown.
        var failureReason: WifiTransferFailureReason = WifiTransferFailureReason.Unknown
        try {
            return withTimeout(5.minutes) {
                // Re-read metadata before saving: the card may have changed since listing.
                val info = try {
                    catalog?.revalidate(handle) ?: objectInfo(handle)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (transport: PtpTransportException) {
                    // Communication with the camera failed, not the metadata check.
                    failureReason = transport.reason
                    throw transport
                } catch (changed: Exception) {
                    failureReason = WifiTransferFailureReason.CatalogChanged
                    throw changed
                }
                try {
                    check(
                        info.filename == photo.filename && info.size == photo.size && info.mimeType == photo.mimeType &&
                                info.capturedAt == photo.capturedAt
                    )
                } catch (changed: IllegalStateException) {
                    failureReason = WifiTransferFailureReason.CatalogChanged
                    throw changed
                }
                val output = try {
                    if (convert) store.createJpeg(info) else store.create(info)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (storage: Exception) {
                    failureReason = WifiTransferFailureReason.Storage
                    throw storage
                }
                destination = output
                var offset = 0L
                while (offset < info.size) {
                    val length = minOf(CHUNK_BYTES.toLong(), info.size - offset).toInt()
                    val bytes = try {
                        read(handle, offset, length)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (transport: PtpTransportException) {
                        failureReason = transport.reason
                        throw transport
                    } catch (protocol: Exception) {
                        failureReason = WifiTransferFailureReason.Protocol
                        throw protocol
                    }
                    try {
                        output.write(bytes)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (storage: Exception) {
                        failureReason = WifiTransferFailureReason.Storage
                        throw storage
                    }
                    offset += length
                    onProgress(
                        WifiImageTransferState(
                            WifiImageTransferStatus.Downloading,
                            filename,
                            offset,
                            info.size
                        )
                    )
                }
                converting = convert
                if (convert) onProgress(WifiImageTransferState(WifiImageTransferStatus.Converting, filename))
                try {
                    output.prepare()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (storage: Exception) {
                    failureReason = WifiTransferFailureReason.Storage
                    throw storage
                }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    try {
                        output.commit()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (storage: Exception) {
                        failureReason = WifiTransferFailureReason.Storage
                        throw storage
                    }
                    committed = true
                    destination = null
                }
                WifiImageTransferState(WifiImageTransferStatus.Saved, filename)
            }
        } catch (_: TimeoutCancellationException) {
            // A committed file is a full success; a converting failure keeps its own status.
            return WifiImageTransferState(
                if (committed) WifiImageTransferStatus.Saved else if (converting) WifiImageTransferStatus.ConversionFailed else WifiImageTransferStatus.Failed,
                filename,
                failureReason = if (committed || converting) null else WifiTransferFailureReason.Timeout
            )
        } catch (cancelled: CancellationException) {
            if (committed) return WifiImageTransferState(
                WifiImageTransferStatus.Saved,
                filename
            )
            throw cancelled
        } catch (failure: Exception) {
            // Diagnostics carry only the exception category and reason — no extra payload.
            log.w {
                "download failed: ${failure::class.simpleName} reason=$failureReason"
            }
            return WifiImageTransferState(
                if (converting) WifiImageTransferStatus.ConversionFailed else WifiImageTransferStatus.Failed,
                filename,
                failureReason = if (converting) null else failureReason
            )
        } finally {
            withContext(NonCancellable) { destination?.abort() }
        }
    }

    suspend fun closeBrowser() {
        catalog?.let { it.close(); pagePhotos = emptyList(); return }
        if (!modeRequested) return
        // A failed exit must not restart shutter/live view while the camera is still in transfer mode.
        accepted(commands.executeNoData(SET_TRANSFER_MODE, listOf(2L, 0L, 0L)))
        modeRequested = false
        snapshot = CatalogSnapshot.EMPTY
        pagePhotos = emptyList()
    }

    private suspend fun data(
        code: Int,
        parameters: List<Long> = emptyList(),
        limit: Int = 8 * 1024 * 1024
    ): ByteArray {
        val result = commands.executeDataIn(code, parameters, maxDataBytes = limit)
        accepted(result)
        return (result as PtpIpTransactionResult.Response).data ?: error("Missing camera data")
    }

    private suspend fun objectInfo(handle: Long): SonyImageInfo =
        if (capabilities.deviceInfo.supports(GET_OBJECT_INFO)) {
            SonyImageInfo.parse(data(GET_OBJECT_INFO, listOf(handle), 4096))
        } else {
            SonyMtpObjectInfo.parse(
                data(
                    GET_OBJECT_PROP_LIST,
                    listOf(handle, 0, 0xffffffffL, 0, 0),
                    64 * 1024
                ), handle
            )
        }

    private suspend fun read(handle: Long, offset: Long, length: Int): ByteArray {
        val bytes = if (catalog != null) catalog.read(handle, offset, length) else {
            val large = capabilities.deviceInfo.supports(GET_PARTIAL_LARGE_OBJECT)
            val parameters =
                if (large) listOf(handle, offset and 0xffffffffL, offset ushr 32, length.toLong())
                else listOf(handle, offset, length.toLong())
            val result = commands.executeDataIn(
                if (large) GET_PARTIAL_LARGE_OBJECT else GET_PARTIAL_OBJECT,
                parameters,
                maxDataBytes = length
            )
            accepted(result)
            result as PtpIpTransactionResult.Response
            if (!large) require(result.response.parameters.firstOrNull() == length.toLong())
            result.data ?: error("Missing image data")
        }
        require(bytes.size == length) { "Incomplete image chunk" }
        return bytes
    }

    private fun accepted(result: PtpIpTransactionResult) {
        // Timeouts and broken connections are transport failures, not catalog changes.
        check(result.ensureResponded().response.code == 0x2001) { "Camera refused image operation" }
    }

    internal companion object {
        const val PAGE_SIZE = 60
        const val MAX_IMAGE_BYTES = 512L * 1024 * 1024
        private const val GET_STORAGE_IDS = 0x1004
        private const val GET_HANDLES = 0x1007
        private const val GET_OBJECT_INFO = 0x1008
        private const val GET_OBJECT_PROP_LIST = 0x9805
        private const val GET_THUMB = 0x100a
        private const val GET_PARTIAL_OBJECT = 0x101b
        private const val GET_PARTIAL_LARGE_OBJECT = 0x9211
        private const val SET_TRANSFER_MODE = 0x9212
        private const val GET_PROPERTIES = 0x9209
        private const val TRANSFER_ENABLED = 0xd295
        private const val CHUNK_BYTES = 512 * 1024

        fun parseIds(data: ByteArray, limit: Int): List<Long> {
            require(data.size >= 4)
            fun number(offset: Int) =
                (0..3).fold(0L) { n, i -> n or ((data[offset + i].toLong() and 255) shl (8 * i)) }

            val count = number(0)
            require(count <= limit && data.size.toLong() == 4L + count * 4)
            return List(count.toInt()) { number(4 + it * 4).also { id -> require(id in 1..0xfffffffeL) } }
        }
    }
}
