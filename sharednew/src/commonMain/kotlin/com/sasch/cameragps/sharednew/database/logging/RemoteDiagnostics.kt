package com.sasch.cameragps.sharednew.database.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random
import kotlin.time.TimeSource

/** Latest remote operation, independent of the ordinary log ring. Only protocol metadata belongs here. */
internal class RemoteDiagnostics(transport: String, private val owner: String? = null) {
    private val start = TimeSource.Monotonic.markNow()
    private val id = Random.nextInt().toUInt().toString(16)
    private val header = "Remote session $id ($transport)"
    init {
        current = this
        latest.value = listOf(header)
    }

    fun record(message: String) {
        val line = "+${start.elapsedNow().inWholeMilliseconds}ms $message"
        latest.update { old ->
            if (old.firstOrNull() != header) old
            else {
                val pinned = old.take(2)
                val tail = (old.drop(2) + line).takeLast(96).toMutableList()
                var chars = tail.sumOf { it.length }
                while (chars > 180_000 && tail.size > 1) chars -= tail.removeAt(0).length
                pinned + tail
            }
        }
    }

    companion object {
        private var current: RemoteDiagnostics? = null
        fun forDevice(id: String) = current?.takeIf { it.owner == id.uppercase() }
        val latest = MutableStateFlow(emptyList<String>())
    }
}
