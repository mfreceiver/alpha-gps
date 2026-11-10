package com.sasch.cameragps.sharednew.ui.logs

/** Most recent log entries put on the clipboard; older ones are left out. */
const val LOG_COPY_MAX_ENTRIES = 2000

/** Plain text for the clipboard plus how many entries it holds and whether older ones were cut. */
data class LogClipboardText(val text: String, val count: Int, val truncated: Boolean)

/**
 * Builds the clipboard text from the displayed logs ([displayedLogs] is newest first, exactly as
 * shown on screen). The entries are already redacted by the logging pipeline; nothing is added.
 */
fun buildLogClipboardText(
    displayedLogs: List<String>,
    maxEntries: Int = LOG_COPY_MAX_ENTRIES,
): LogClipboardText {
    val taken = displayedLogs.take(maxEntries)
    return LogClipboardText(
        text = taken.joinToString("\n\n"),
        count = taken.size,
        truncated = displayedLogs.size > maxEntries,
    )
}
