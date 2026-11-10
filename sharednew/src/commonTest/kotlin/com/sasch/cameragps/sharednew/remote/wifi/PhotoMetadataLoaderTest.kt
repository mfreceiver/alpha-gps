package com.sasch.cameragps.sharednew.remote.wifi

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoMetadataLoaderTest : WifiLoggingTest() {
    private fun basic(handle: Long, mime: String = "image/jpeg", size: Long = 5_000_000, width: Int? = null) =
        WifiCameraPhoto(handle, "DSC$handle.JPG", size, mime, "20260101T120000", width = width, height = width?.let { 4000 })
            .toBasicMetadata()

    private class Fixture(scope: CoroutineScope, capacity: Int = 200, timeoutMs: Long = 20_000) {
        val photos = mutableMapOf<Long, PhotoMetadata>()
        val fetches = mutableListOf<Pair<Long, Int>>()
        var holds = 0
        var releases = 0
        var idle: CompletableDeferred<Unit>? = null
        var gate: CompletableDeferred<Unit>? = null
        var failWith: Exception? = null
        var hang = false
        var reply: ((Long) -> ByteArray?)? = null
        var replyNull = false
        val loader = PhotoMetadataLoader(
            scope,
            basicFor = { photos[it] },
            fetchHead = { handle, length ->
                fetches += handle to length
                if (hang) awaitCancellation()
                gate?.await()
                failWith?.let { throw it }
                if (replyNull) null
                else reply?.invoke(handle) ?: ExifTestData.jpeg(ExifTestData.sampleExif())
            },
            awaitIdle = { idle?.await() },
            hold = { holds++ },
            release = { releases++ },
            capacity = capacity,
            fetchTimeoutMs = timeoutMs,
        )
    }

    private fun Fixture.photo(handle: Long, mime: String = "image/jpeg", size: Long = 5_000_000) {
        photos[handle] = basic(handle, mime, size)
    }

    @Test
    fun basicOnlyIsFinalAtOnceAndNeverTouchesTheCamera() = runTest {
        val f = Fixture(backgroundScope).apply { photo(1) }
        f.loader.request(1, 1, extended = false)
        val state = assertIs<PhotoMetadataState.Ready>(f.loader.state.value)
        assertEquals(PhotoFormat.Jpeg, state.metadata.format)
        assertEquals(5_000_000, state.metadata.sizeBytes)
        assertEquals("20260101T120000", state.metadata.capturedAt)
        assertNull(state.metadata.exif)
        assertTrue(!state.metadata.extendedRead)
        advanceTimeBy(5_000)
        assertTrue(f.fetches.isEmpty())
        assertEquals(0, f.holds)
    }

    @Test
    fun rawAndHeifStayBasicEvenWhenExtendedIsRequested() = runTest {
        val f = Fixture(backgroundScope).apply { photo(1, "image/x-sony-arw"); photo(2, "image/heif") }
        f.loader.request(1, 1, extended = true)
        assertEquals(PhotoFormat.Raw, assertIs<PhotoMetadataState.Ready>(f.loader.state.value).metadata.format)
        f.loader.request(2, 1, extended = true)
        assertEquals(PhotoFormat.Heif, assertIs<PhotoMetadataState.Ready>(f.loader.state.value).metadata.format)
        advanceTimeBy(5_000)
        assertTrue(f.fetches.isEmpty())
    }

    @Test
    fun extendedReadIsDebouncedThenPublishesExifMergedWithTheBasicData() = runTest {
        val f = Fixture(backgroundScope).apply { photo(1) }
        f.loader.request(1, 1, extended = true)
        val loading = assertIs<PhotoMetadataState.Loading>(f.loader.state.value)
        assertEquals(1, loading.handle)
        assertNotNull(loading.basic)
        advanceTimeBy(249)
        runCurrent()
        assertTrue(f.fetches.isEmpty(), "still inside the debounce window")
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(1L to 65_536), f.fetches)
        val ready = assertIs<PhotoMetadataState.Ready>(f.loader.state.value).metadata
        assertTrue(ready.extendedRead)
        assertEquals("1/250", ready.exif!!.exposureTimeText)
        assertEquals("6000×4000", ready.resolutionText)
        assertEquals(1, f.holds)
        assertEquals(1, f.releases)
    }

    @Test
    fun readLengthNeverExceedsTheFileSize() = runTest {
        val f = Fixture(backgroundScope).apply { photo(1, size = 20_000) }
        f.loader.request(1, 1, extended = true)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf(1L to 20_000), f.fetches)
    }

    @Test
    fun swipingCancelsTheUnfinishedReadAndOnlyTheSettledPhotoIsFetched() = runTest {
        val f = Fixture(backgroundScope).apply { photo(1); photo(2); photo(3) }
        f.loader.request(1, 1, true)
        advanceTimeBy(100)
        f.loader.request(2, 1, true)
        advanceTimeBy(100)
        f.loader.request(3, 1, true)
        advanceTimeBy(251)
        runCurrent()
        assertEquals(listOf(3L), f.fetches.map { it.first })
        assertEquals(3, assertIs<PhotoMetadataState.Ready>(f.loader.state.value).handle)
    }

    @Test
    fun anInFlightReadIsCancelledByTheNextRequestAndReleasesTheCamera() = runTest {
        val f = Fixture(backgroundScope).apply { photo(1); photo(2); hang = true }
        f.loader.request(1, 1, true)
        advanceTimeBy(251)
        runCurrent()
        assertEquals(1, f.fetches.size)
        assertEquals(1, f.holds)
        assertEquals(0, f.releases)
        f.hang = false
        f.loader.request(2, 1, true)
        runCurrent()
        assertEquals(1, f.releases, "cancelling releases the hold")
        assertEquals(2, assertIs<PhotoMetadataState.Loading>(f.loader.state.value).handle)
        advanceTimeBy(251)
        runCurrent()
        assertEquals(2, assertIs<PhotoMetadataState.Ready>(f.loader.state.value).handle)
        assertEquals(f.holds, f.releases)
    }

    @Test
    fun cancelResetsToIdleAndStopsPendingWork() = runTest {
        val f = Fixture(backgroundScope).apply { photo(1) }
        f.loader.request(1, 1, true)
        f.loader.cancel()
        assertEquals(PhotoMetadataState.Idle, f.loader.state.value)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(f.fetches.isEmpty())
        assertEquals(PhotoMetadataState.Idle, f.loader.state.value)
    }

    @Test
    fun interruptFallsBackToBasicWhileLoadingAndKeepsFinishedStates() = runTest {
        val f = Fixture(backgroundScope).apply { photo(1); photo(2) }
        f.loader.request(1, 1, true)
        f.loader.interrupt()
        val basicOnly = assertIs<PhotoMetadataState.Ready>(f.loader.state.value).metadata
        assertTrue(!basicOnly.extendedRead)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(f.fetches.isEmpty())
        f.loader.request(2, 1, true)
        advanceTimeBy(300)
        runCurrent()
        val done = f.loader.state.value
        f.loader.interrupt()
        assertEquals(done, f.loader.state.value)
    }

    @Test
    fun readWaitsUntilTheCameraIsIdle() = runTest {
        val f = Fixture(backgroundScope).apply { photo(1); idle = CompletableDeferred() }
        f.loader.request(1, 1, true)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(f.fetches.isEmpty())
        assertEquals(0, f.holds)
        f.idle!!.complete(Unit)
        runCurrent()
        assertEquals(1, f.fetches.size)
        assertIs<PhotoMetadataState.Ready>(f.loader.state.value)
    }

    @Test
    fun successfulResultsAreCachedAndServedWithoutNewTraffic() = runTest {
        val f = Fixture(backgroundScope).apply { photo(1); photo(2) }
        f.loader.request(1, 1, true)
        advanceTimeBy(300); runCurrent()
        f.loader.request(2, 1, true)
        advanceTimeBy(300); runCurrent()
        assertEquals(2, f.fetches.size)
        f.loader.request(1, 1, true)
        val again = assertIs<PhotoMetadataState.Ready>(f.loader.state.value)
        assertTrue(again.metadata.extendedRead)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(2, f.fetches.size)
        assertEquals(2, f.loader.cacheSize)
    }

    @Test
    fun cacheEvictsTheLeastRecentlyUsedPhoto() = runTest {
        val f = Fixture(backgroundScope, capacity = 2).apply { photo(1); photo(2); photo(3) }
        suspend fun load(handle: Long) {
            f.loader.request(handle, 1, true)
            advanceTimeBy(300); runCurrent()
        }
        load(1); load(2)
        f.loader.request(1, 1, true) // touch 1 → 2 is now the oldest
        load(3)
        assertEquals(2, f.loader.cacheSize)
        val before = f.fetches.size
        f.loader.request(1, 1, true)
        assertIs<PhotoMetadataState.Ready>(f.loader.state.value)
        assertEquals(before, f.fetches.size, "1 survived")
        f.loader.request(2, 1, true)
        assertIs<PhotoMetadataState.Loading>(f.loader.state.value)
    }

    @Test
    fun aNewCatalogGenerationInvalidatesTheCache() = runTest {
        val f = Fixture(backgroundScope).apply { photo(1) }
        f.loader.request(1, 1, true)
        advanceTimeBy(300); runCurrent()
        f.loader.request(1, 2, true)
        assertIs<PhotoMetadataState.Loading>(f.loader.state.value)
        advanceTimeBy(300); runCurrent()
        assertEquals(2, f.fetches.size)
        assertEquals(1, f.loader.cacheSize)
    }

    @Test
    fun clearDropsTheCacheAndShowsNothing() = runTest {
        val f = Fixture(backgroundScope).apply { photo(1) }
        f.loader.request(1, 1, true)
        advanceTimeBy(300); runCurrent()
        f.loader.clear()
        assertEquals(0, f.loader.cacheSize)
        assertEquals(PhotoMetadataState.Idle, f.loader.state.value)
    }

    @Test
    fun failuresMapToReasonsKeepTheBasicDataAndAreNotCached() = runTest {
        val f = Fixture(backgroundScope).apply { photo(1) }
        val cases = listOf(
            PtpOperationRejected(PtpResponse.DEVICE_BUSY) to PhotoMetadataFailureReason.CameraBusy,
            PtpOperationRejected(PtpResponse.OPERATION_NOT_SUPPORTED) to PhotoMetadataFailureReason.Unsupported,
            PtpOperationRejected(PtpResponse.INVALID_OBJECT_HANDLE) to PhotoMetadataFailureReason.CatalogChanged,
            PtpOperationRejected(0x2002) to PhotoMetadataFailureReason.Protocol,
            PtpTransportException(WifiTransferFailureReason.Timeout) to PhotoMetadataFailureReason.Timeout,
            PtpTransportException(WifiTransferFailureReason.Protocol) to PhotoMetadataFailureReason.Protocol,
            PtpTransportException(WifiTransferFailureReason.CatalogChanged) to PhotoMetadataFailureReason.CatalogChanged,
            IllegalStateException("boom") to PhotoMetadataFailureReason.Protocol,
        )
        for ((failure, reason) in cases) {
            f.failWith = failure
            f.loader.request(1, 1, true)
            advanceTimeBy(300); runCurrent()
            val state = assertIs<PhotoMetadataState.Unavailable>(f.loader.state.value, "$failure")
            assertEquals(reason, state.reason, "$failure")
            assertEquals(1, state.basic?.handle)
            assertEquals(0, f.loader.cacheSize)
        }
        assertEquals(f.holds, f.releases)
        // The next attempt works again.
        f.failWith = null
        f.loader.request(1, 1, true)
        advanceTimeBy(300); runCurrent()
        assertIs<PhotoMetadataState.Ready>(f.loader.state.value)
    }

    @Test
    fun aCameraThatNeverAnswersTimesOutWithoutBlockingLaterRequests() = runTest {
        val f = Fixture(backgroundScope, timeoutMs = 2_000).apply { photo(1); photo(2); hang = true }
        f.loader.request(1, 1, true)
        advanceTimeBy(300); runCurrent()
        advanceTimeBy(2_001); runCurrent()
        assertEquals(PhotoMetadataFailureReason.Timeout, assertIs<PhotoMetadataState.Unavailable>(f.loader.state.value).reason)
        assertEquals(f.holds, f.releases)
        f.hang = false
        f.loader.request(2, 1, true)
        advanceTimeBy(300); runCurrent()
        assertIs<PhotoMetadataState.Ready>(f.loader.state.value)
    }

    @Test
    fun unreadableAnswersAndUnsupportedConnectionsAreUnavailableNotCrashes() = runTest {
        val f = Fixture(backgroundScope).apply { photo(1) }
        f.replyNull = true
        f.loader.request(1, 1, true)
        advanceTimeBy(300); runCurrent()
        assertEquals(PhotoMetadataFailureReason.Unsupported, assertIs<PhotoMetadataState.Unavailable>(f.loader.state.value).reason)
        f.replyNull = false
        f.reply = { byteArrayOf(1, 2, 3, 4, 5, 6) }
        f.loader.request(1, 1, true)
        advanceTimeBy(300); runCurrent()
        assertEquals(PhotoMetadataFailureReason.Protocol, assertIs<PhotoMetadataState.Unavailable>(f.loader.state.value).reason)
    }

    @Test
    fun unknownHandleIsReportedAsCatalogChanged() = runTest {
        val f = Fixture(backgroundScope)
        f.loader.request(99, 1, true)
        val state = assertIs<PhotoMetadataState.Unavailable>(f.loader.state.value)
        assertEquals(PhotoMetadataFailureReason.CatalogChanged, state.reason)
        assertNull(state.basic)
    }

    @Test
    fun exifResolutionFillsInWhenTheObjectInfoHadNone() = runTest {
        val f = Fixture(backgroundScope).apply { photo(1); photos[2] = basic(2, width = 7000) }
        f.reply = { ExifTestData.jpeg(ExifTestData.sampleExif()) }
        f.loader.request(2, 1, true)
        advanceTimeBy(300); runCurrent()
        assertEquals(7000, assertIs<PhotoMetadataState.Ready>(f.loader.state.value).metadata.width)
    }
}
