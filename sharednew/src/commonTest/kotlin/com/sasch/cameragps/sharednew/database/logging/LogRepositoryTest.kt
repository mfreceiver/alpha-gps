package com.sasch.cameragps.sharednew.database.logging

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogRepositoryTest {
    @Test
    fun trimsToNewest500OnlyAfterExceeding1000AndClears() = runTest {
        val repository = LogRepository()
        repeat(1000) { repository.insertLog(it.toLong(), 4, null, "$it", null) }
        assertEquals(1000, repository.getRecentLogs(2000).first().size)
        assertEquals((999 downTo 800).map(Int::toString), repository.getRecentLogs().first().map { it.message })

        repository.insertLog(1000, 4, null, "1000", null)
        val trimmed = repository.getRecentLogs(2000).first()
        assertEquals(500, trimmed.size)
        assertEquals((1000 downTo 501).map(Int::toString), trimmed.map { it.message })

        repository.clearAllLogs()
        assertTrue(repository.getRecentLogs().first().isEmpty())
        assertTrue(LogRepository().getRecentLogs().first().isEmpty())
    }
}
