package com.sasch.cameragps.sharednew.remote.wifi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Loads the metadata of the single photo the viewer is showing.
 *
 * Basic metadata (format, size, capture time, resolution) comes from the catalog entry and costs
 * no camera traffic, so it is published at once. The extended part (EXIF shooting parameters) needs
 * one small partial read of the original's head; to keep the camera's single serial command queue
 * free for previews and downloads that read is
 *  - debounced ([debounceMs]): a swipe through several photos only reads the one that settles,
 *  - cancelled as soon as another photo is requested ([request]) or the viewer closes ([cancel]),
 *  - deferred until no catalog load, download or preview holds the camera ([awaitIdle]), and
 *  - bounded by [fetchTimeoutMs]; a failure only yields [PhotoMetadataState.Unavailable] with the
 *    basic data and never touches preview or download state.
 * Successful extended results are kept in an LRU cache of [capacity] photos, valid for one catalog
 * generation.
 *
 * Confined to the single thread of [scope] (the app's Main dispatcher), like the controller.
 */
internal class PhotoMetadataLoader(
    private val scope: CoroutineScope,
    /** Basic metadata of a handle in the current catalog, `null` when it is not (or no longer) known. */
    private val basicFor: (Long) -> PhotoMetadata?,
    /** Reads up to `length` bytes from the start of the original; `null` when the connection cannot. */
    private val fetchHead: suspend (handle: Long, length: Int) -> ByteArray?,
    /** Suspends until the camera is not busy with browsing/preview/download work. */
    private val awaitIdle: suspend () -> Unit = {},
    /** Marks the camera busy for the duration of the read (so thumbnail loading waits). */
    private val hold: () -> Unit = {},
    private val release: () -> Unit = {},
    private val debounceMs: Long = DEBOUNCE_MS,
    private val capacity: Int = CACHE_CAPACITY,
    private val fetchTimeoutMs: Long = FETCH_TIMEOUT_MS,
) {
    init {
        require(capacity > 0 && debounceMs >= 0 && fetchTimeoutMs > 0)
    }

    private val _state = MutableStateFlow<PhotoMetadataState>(PhotoMetadataState.Idle)
    val state: StateFlow<PhotoMetadataState> = _state.asStateFlow()

    private var job: Job? = null
    private var cacheGeneration = Long.MIN_VALUE

    /** Insertion-ordered; re-inserting on access makes the first key the least recently used. */
    private val cache = LinkedHashMap<Long, PhotoMetadata>()

    /** Number of cached extended results (for diagnostics and tests). */
    val cacheSize: Int get() = cache.size

    /**
     * Shows [handle]. [generation] identifies the catalog the handle belongs to; a different one than
     * the cached results drops the cache first. With [extended] false, or for files that carry no
     * EXIF we can read (RAW, HEIF), the basic metadata is final and nothing is read from the camera.
     */
    fun request(handle: Long, generation: Long, extended: Boolean) {
        job?.cancel()
        job = null
        if (generation != cacheGeneration) {
            cache.clear()
            cacheGeneration = generation
        }
        val basic = basicFor(handle)
        if (basic == null) {
            _state.value = PhotoMetadataState.Unavailable(handle, PhotoMetadataFailureReason.CatalogChanged, null)
            return
        }
        if (!extended || !basic.supportsExtended) {
            _state.value = PhotoMetadataState.Ready(basic)
            return
        }
        cache.remove(handle)?.let { cached ->
            cache[handle] = cached // most recently used
            _state.value = PhotoMetadataState.Ready(cached)
            return
        }
        _state.value = PhotoMetadataState.Loading(handle, basic)
        job = scope.launch { loadExtended(handle, generation, basic) }
    }

    /** Stops any pending/in-flight read and reports [PhotoMetadataState.Idle]; the cache is kept. */
    fun cancel() {
        job?.cancel()
        job = null
        _state.value = PhotoMetadataState.Idle
    }

    /**
     * Stops the read without forgetting what is shown: a pending [PhotoMetadataState.Loading] falls
     * back to the basic data ([PhotoMetadata.extendedRead] stays false), other states are kept. Used
     * when a download/browse operation needs the camera; the consumer may request again afterwards.
     */
    fun interrupt() {
        job?.cancel()
        job = null
        val current = _state.value
        if (current is PhotoMetadataState.Loading) {
            _state.value = current.basic?.let { PhotoMetadataState.Ready(it) } ?: PhotoMetadataState.Idle
        }
    }

    /** [cancel] plus dropping every cached result (session end, card change). */
    fun clear() {
        cancel()
        cache.clear()
        cacheGeneration = Long.MIN_VALUE
    }

    private suspend fun loadExtended(handle: Long, generation: Long, basic: PhotoMetadata) {
        delay(debounceMs)
        awaitIdle()
        hold()
        try {
            val head = withTimeout(fetchTimeoutMs) {
                fetchHead(handle, minOf(basic.sizeBytes, HEAD_BYTES.toLong()).toInt())
            }
            val exif = head?.let { ExifMetadataParser.parse(it) }
            _state.value = when {
                head == null -> PhotoMetadataState.Unavailable(handle, PhotoMetadataFailureReason.Unsupported, basic)
                exif == null -> PhotoMetadataState.Unavailable(handle, PhotoMetadataFailureReason.Protocol, basic)
                else -> {
                    val merged = basic.copy(
                        width = basic.width ?: exif.pixelWidth,
                        height = basic.height ?: exif.pixelHeight,
                        exif = exif,
                        extendedRead = true,
                    )
                    if (generation == cacheGeneration) remember(handle, merged)
                    PhotoMetadataState.Ready(merged)
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            _state.value = PhotoMetadataState.Unavailable(handle, PhotoMetadataFailureReason.Timeout, basic)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            _state.value = PhotoMetadataState.Unavailable(handle, photoMetadataFailureReason(failure), basic)
        } finally {
            release()
        }
    }

    private fun remember(handle: Long, metadata: PhotoMetadata) {
        cache.remove(handle)
        cache[handle] = metadata
        while (cache.size > capacity) cache.remove(cache.keys.first())
    }

    companion object {
        const val DEBOUNCE_MS = 250L
        const val CACHE_CAPACITY = 200
        const val FETCH_TIMEOUT_MS = 20_000L

        /** EXIF lives in the first APP1 segment (≤ 64 KB); one chunk of this size is enough. */
        const val HEAD_BYTES = 64 * 1024
    }
}
