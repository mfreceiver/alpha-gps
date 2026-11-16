package com.sasch.cameragps.sharednew.database.logging

data class LogEntry(
    val id: Long = 0,
    val timestamp: Long,
    val priority: Int,
    val tag: String?,
    val message: String,
    val exception: String?,
)

