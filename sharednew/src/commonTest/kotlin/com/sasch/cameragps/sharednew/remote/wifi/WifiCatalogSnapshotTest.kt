package com.sasch.cameragps.sharednew.remote.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WifiCatalogSnapshotTest {
    private var next = 1L

    private fun photo(name: String, mime: String, date: String, captureId: String? = null) =
        WifiCameraPhoto(next++, name, 1_000, mime, date, captureId = captureId)

    private fun single(date: String, name: String = "DSC${next}.JPG") =
        WifiCameraCapture("c$next", listOf(photo(name, "image/jpeg", date)))

    @Test
    fun parsesBothCameraDateFormatsStrictlyWithoutTimeZoneConversion() {
        assertEquals("2024-05-01", cameraDateKey("20240501T123045"))
        assertEquals("2024-05-01", cameraDateKey("20240501T235959.0+0800"))
        assertEquals("2024-05-01", cameraDateKey("2024-05-01T12:30:45"))
        assertEquals("2024-05-01", cameraDateKey("2024-05-01T12:30"))
        assertEquals("2024-05-01", cameraDateKey("2024-05-01T12:30:45.123Z"))
        assertEquals("2024-05-01", cameraDateKey("2024-05-01T12:30:45+08:00"))
        assertEquals("2024-05-01", cameraDateKey("2024-05-01T12:30:45+0800"))
        assertEquals("2024-05-01", cameraDateKey("2024-05-01T12:30-0530"))
        assertEquals("2024-05-01", cameraDateKey("20240501T123045.5-0500"))
        assertEquals("2024-12-31", cameraDateKey("2024-12-31T23:59:59"))
        assertEquals("2024-02-29", cameraDateKey("20240229T000000"))
        assertEquals("1970-01-01", cameraDateKey("1970-01-01T00:00:01"))
    }

    @Test
    fun rejectsMissingMalformedOrImpossibleDates() {
        for (bad in listOf(
            "", "unknown", "20240501", "2024-05-01", "20241301T000000", "20240001T000000",
            "20240230T000000", "20230229T000000", "20240431T000000", "20240532T000000",
            "00000101T000000", "18991231T000000", "2024/05/01T12:00:00", " 20240501T120000"
        )) assertNull(cameraDateKey(bad), "'$bad' must not form a date")
    }

    @Test
    fun rejectsPlausibleDatesWithImpossibleOrTruncatedTimes() {
        for (bad in listOf(
            // Legacy PTP form: everything after the date must be a full hhmmss clock.
            "20240501Tgarbage", "20240501T123045x", "20240501T1230", "20240501T240000",
            "20240501T126045", "20240501T123060", "20240501T123045.99:30",
            // ISO form: seconds optional but clock values still strict.
            "2024-05-01T99:99:99", "2024-05-01T24:00:00", "2024-05-01T12:60",
            "2024-05-01T12:30:60", "2024-05-01 12:00:00", "2024-05-01T12:30:",
            // Zone markers must be numerically plausible when present.
            "2024-05-01T12:30:45+99:99", "20240501T123045+2400"
        )) assertNull(cameraDateKey(bad), "'$bad' must not form a date")
    }

    @Test
    fun groupsByDayNewestFirstWithUnknownLastAndCountsCaptures() {
        val snapshot = CatalogSnapshot.of(
            listOf(
                single("20240502T090000"), single("2024-05-01T10:00:00"), single("20240502T080000"),
                single(""), single("20240230T000000"), single("20240615T000000")
            )
        )
        assertEquals(
            listOf(
                DateGroup("2024-06-15", 1), DateGroup("2024-05-02", 2), DateGroup("2024-05-01", 1),
                DateGroup(UNKNOWN_DATE_KEY, 2)
            ), snapshot.dates
        )
        assertEquals(6, snapshot.captures.size)
    }

    @Test
    fun aJpgPlusRawPairCountsOnceAndIsNeverSplitAcrossDays() {
        val pair = WifiCameraCapture(
            "pair", listOf(
                photo("A.ARW", "image/x-sony-arw", "20240501T235959"),
                photo("A.JPG", "image/jpeg", "20240502T000001")
            )
        )
        val snapshot = CatalogSnapshot.of(listOf(pair))
        // The preview (JPEG) record decides; the RAW stamp is ignored.
        assertEquals(listOf(DateGroup("2024-05-02", 1)), snapshot.dates)
        assertEquals(pair, snapshot.capturesOn("2024-05-02")?.single())
        assertNull(snapshot.capturesOn("2024-05-01"))
    }

    @Test
    fun anUnusablePreviewDateFallsBackToAnotherFileOfTheSameCapture() {
        val pair = WifiCameraCapture(
            "pair", listOf(
                photo("A.ARW", "image/x-sony-arw", "20240501T120000"),
                photo("A.JPG", "image/jpeg", "")
            )
        )
        assertEquals("2024-05-01", pair.dateKey)
        val none = WifiCameraCapture("x", listOf(photo("B.JPG", "image/jpeg", "bad")))
        assertEquals(UNKNOWN_DATE_KEY, none.dateKey)
    }

    @Test
    fun aDayIsTheWholeDayInOneListWithoutPages() {
        // More than the old 60-capture page: a day is never sliced any more.
        val day = (1..SonyImageTransfer.PAGE_SIZE + 3).map { single("20240501T0000" + (it % 60).toString().padStart(2, '0')) }
        val other = (1..5).map { single("20240430T120000") }
        val snapshot = CatalogSnapshot.of(day + other)
        val first = snapshot.day("2024-05-01")
        assertEquals(SonyImageTransfer.PAGE_SIZE + 3, first.photos.size)
        assertEquals(SonyImageTransfer.PAGE_SIZE + 3, first.totalObjects)
        assertEquals(0, first.offset)
        assertEquals(false, first.hasMore)
        assertEquals(day.flatMap { it.files }, first.photos)
        assertEquals(5, snapshot.day("2024-04-30").totalObjects)
        assertFailsWith<IllegalArgumentException> { snapshot.day("2024-04-29") }
    }

    @Test
    fun anEmptyCatalogHasNoDays() {
        assertEquals(emptyList(), CatalogSnapshot.of(emptyList()).dates)
        assertEquals(emptyList(), CatalogSnapshot.EMPTY.dates)
    }
}
