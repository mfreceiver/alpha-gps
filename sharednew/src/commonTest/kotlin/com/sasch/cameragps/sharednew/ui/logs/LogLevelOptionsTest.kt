package com.sasch.cameragps.sharednew.ui.logs

import kotlin.test.Test
import kotlin.test.assertEquals

class LogLevelOptionsTest {
    @Test
    fun theSelectorOffersTheAndroidPrioritiesMostVerboseFirst() {
        // android.util.Log: VERBOSE 2, DEBUG 3, INFO 4, WARN 5, ERROR 6 — the values stored under "log_level".
        assertEquals(
            listOf(2 to "VERBOSE", 3 to "DEBUG", 4 to "INFO", 5 to "WARN", 6 to "ERROR"),
            logLevelOptions.map { it.priority to it.name },
        )
    }

    @Test
    fun aStoredLevelIsShownByNameAndAnUnexpectedOneAsItsNumber() {
        assertEquals("INFO", logLevelName(4))
        assertEquals("VERBOSE", logLevelName(2))
        assertEquals("7", logLevelName(7))
    }
}
