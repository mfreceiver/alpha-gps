package com.sasch.cameragps.sharednew.database.logging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A process-local, bounded log buffer. Updates and clears are atomic across producers. */
class LogRepository {
    private val logs = MutableStateFlow<List<LogEntry>>(emptyList())

    fun insertLog(
        timestamp: Long,
        priority: Int,
        tag: String?,
        message: String,
        exception: String?
    ) {
        val entry = LogEntry(timestamp = timestamp, priority = priority, tag = tag, message = message, exception = exception)
        logs.update { current ->
            val updated = (current + entry).sortedByDescending { it.timestamp }
            if (updated.size > 1000) updated.take(500) else updated
        }
    }

    fun getRecentLogs(limit: Int = 200): Flow<List<LogEntry>> =
        logs.map { it.take(limit.coerceAtLeast(0)) }.distinctUntilChanged()

    suspend fun clearAllLogs() {
        logs.value = emptyList()
    }
}
