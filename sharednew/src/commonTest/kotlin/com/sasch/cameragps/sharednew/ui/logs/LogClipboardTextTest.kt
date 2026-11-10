package com.sasch.cameragps.sharednew.ui.logs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogClipboardTextTest {
    @Test
    fun emptyLogsProduceEmptyPayload() {
        val payload = buildLogClipboardText(emptyList())
        assertEquals(0, payload.count)
        assertEquals("", payload.text)
        assertFalse(payload.truncated)
    }

    @Test
    fun keepsDisplayedOrderAndSeparatesEntriesWithBlankLine() {
        val payload = buildLogClipboardText(listOf("newest", "older", "oldest"))
        assertEquals("newest\n\nolder\n\noldest", payload.text)
        assertEquals(3, payload.count)
        assertFalse(payload.truncated)
    }

    @Test
    fun exactlyAtLimitIsNotTruncated() {
        val payload = buildLogClipboardText(List(LOG_COPY_MAX_ENTRIES) { "e$it" })
        assertEquals(LOG_COPY_MAX_ENTRIES, payload.count)
        assertFalse(payload.truncated)
    }

    @Test
    fun overLimitKeepsOnlyTheNewestEntries() {
        val logs = List(LOG_COPY_MAX_ENTRIES + 5) { "e$it" }
        val payload = buildLogClipboardText(logs)
        assertEquals(LOG_COPY_MAX_ENTRIES, payload.count)
        assertTrue(payload.truncated)
        assertTrue(payload.text.startsWith("e0\n\ne1"))
        assertFalse(payload.text.contains("e${LOG_COPY_MAX_ENTRIES}\n") || payload.text.endsWith("e${LOG_COPY_MAX_ENTRIES}"))
    }
}
