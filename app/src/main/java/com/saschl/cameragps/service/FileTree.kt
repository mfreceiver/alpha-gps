package com.saschl.cameragps.service

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.sasch.cameragps.sharednew.database.logging.LogRepository
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileTree(private val minPriority: Int) : Timber.Tree() {
    companion object {
        val logRepository = LogRepository()

        fun getLogs(): LiveData<List<String>> {
            Timber.d("Getting logs from FileTree")
            return logRepository.let { repo ->
                    repo.getRecentLogs().map { logEntry ->
                        logEntry.map {
                            val date = formatTimestamp(it.timestamp)
                            "[$date] [${priorityToString(it.priority)}] ${it.tag ?: "App"}: ${it.message}" +
                                    (it.exception?.let { "\n$it" } ?: "")
                        }

                    }
            }.asLiveData()
        }

        suspend fun clearLogs() {
            logRepository.clearAllLogs()
        }

        private fun priorityToString(priority: Int): String = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> priority.toString()
        }

        private fun formatTimestamp(timestamp: Long): String {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
        }
    }

    /**
     * Write a log message to its destination. Called for all level-specific methods by default.
     *
     * @param priority Log level. See [Log] for constants.
     * @param tag Explicit or inferred tag. May be `null`.
     * @param message Formatted log message. May be `null`, but then `t` will not be.
     * @param t Accompanying exceptions. May be `null`, but then `message` will not be.
     */
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val timestamp = System.currentTimeMillis()
        val exception = t?.stackTraceToString()

        if (priority >= minPriority) {
            logRepository.insertLog(timestamp, priority, tag, message, exception)
        }
    }
}