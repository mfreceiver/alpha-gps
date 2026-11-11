package com.sasch.cameragps.sharednew.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.cancel_button
import cameragps.sharednew.generated.resources.content_copy_24px
import cameragps.sharednew.generated.resources.delete_24px
import cameragps.sharednew.generated.resources.keyboard_arrow_right_24px
import cameragps.sharednew.generated.resources.log_level
import cameragps.sharednew.generated.resources.logs_clear_all
import cameragps.sharednew.generated.resources.logs_empty
import cameragps.sharednew.generated.resources.logs_title
import cameragps.sharednew.generated.resources.m9_logs_clear_confirm
import cameragps.sharednew.generated.resources.m9_logs_copied
import cameragps.sharednew.generated.resources.m9_logs_copied_latest
import cameragps.sharednew.generated.resources.m9_logs_copy
import cameragps.sharednew.generated.resources.m9_logs_copy_empty
import com.sasch.cameragps.sharednew.database.logging.LogEntry
import com.sasch.cameragps.sharednew.database.logging.LogRepository
import com.sasch.cameragps.sharednew.logging.LogFormatter
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassMenu
import com.sasch.cameragps.sharednew.ui.components.glass.GlassMenuItem
import com.sasch.cameragps.sharednew.ui.components.glass.PopoverPlacement
import com.sasch.cameragps.sharednew.ui.components.glass.glassAnchor
import com.sasch.cameragps.sharednew.ui.components.glass.rememberGlassAnchor
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSnackbarHost
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSurface
import com.sasch.cameragps.sharednew.ui.components.glass.isScrolledUnderTopBar
import com.sasch.cameragps.sharednew.ui.components.glass.rememberGlassSnackbarHostState
import com.sasch.cameragps.sharednew.ui.components.page.GlassPageScaffold
import com.sasch.cameragps.sharednew.ui.components.verticalScrollbar
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@OptIn(FormatStringsInDatetimeFormats::class)
private val LOG_TIMESTAMP_FORMAT = LocalDateTime.Format {
    byUnicodePattern("dd-MM-yyyy HH:mm:ss.SSS")
}

/** One selectable log level: the Android log [priority] (2 = VERBOSE … 6 = ERROR) and its name. */
data class LogLevelOption(val priority: Int, val name: String)

/** The levels offered by the log page's selector, most to least verbose (android.util.Log priorities). */
val logLevelOptions: List<LogLevelOption> = listOf(
    LogLevelOption(2, "VERBOSE"),
    LogLevelOption(3, "DEBUG"),
    LogLevelOption(4, "INFO"),
    LogLevelOption(5, "WARN"),
    LogLevelOption(6, "ERROR"),
)

/** Name shown for a stored level; an unexpected stored value is shown as its number. */
internal fun logLevelName(priority: Int): String =
    logLevelOptions.firstOrNull { it.priority == priority }?.name ?: priority.toString()

/**
 * The activity log. With a [logLevel] the page shows a compact level selector between the top bar and
 * the log (an in-page [GlassMenu], never a system window); picking a level calls [onLogLevelChange],
 * which applies and stores it immediately.
 */
@Composable
fun SharedLogViewerScreen(
    logFormatter: LogFormatter,
    logRepository: LogRepository,
    onBackClick: () -> Unit,
    logLevel: Int? = null,
    onLogLevelChange: (Int) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val logs by remember(logFormatter) { logFormatter.format() }.collectAsState(emptyList())
    var showClearConfirm by remember { mutableStateOf(false) }
    // Copy feedback (formerly an inline notice line) is an in-page glass snackbar.
    val snackbar = rememberGlassSnackbarHostState()

    val copyLabel = stringResource(Res.string.m9_logs_copy)
    val clearLabel = stringResource(Res.string.logs_clear_all)
    val nothingToCopy = stringResource(Res.string.m9_logs_copy_empty)
    val copiedAll = stringResource(Res.string.m9_logs_copied, logs.size.coerceAtMost(LOG_COPY_MAX_ENTRIES))
    val copiedLatest = stringResource(Res.string.m9_logs_copied_latest, LOG_COPY_MAX_ENTRIES)

    GlassPageScaffold(
        title = stringResource(Res.string.logs_title),
        onBackClick = onBackClick,
        scrolled = scrollState.isScrolledUnderTopBar,
        actions = {
            GlassIconButton(
                icon = painterResource(Res.drawable.content_copy_24px),
                contentDescription = copyLabel,
                enabled = logs.isNotEmpty(),
                onClick = {
                    val payload = buildLogClipboardText(logs)
                    if (payload.count == 0) {
                        snackbar.show(nothingToCopy)
                    } else {
                        @Suppress("DEPRECATION")
                        clipboard.setText(AnnotatedString(payload.text))
                        snackbar.show(if (payload.truncated) copiedLatest else copiedAll)
                    }
                },
            )
            GlassIconButton(
                icon = painterResource(Res.drawable.delete_24px),
                contentDescription = clearLabel,
                enabled = logs.isNotEmpty(),
                onClick = { showClearConfirm = true },
            )
        },
    ) { paddingValues ->
        GlassSnackbarHost(snackbar)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = AlphaDimens.screenMargin)
                .padding(bottom = AlphaDimens.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap),
        ) {
            if (logLevel != null) LogLevelSelector(logLevel, onLogLevelChange)
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(0.dp),
            ) {
                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScrollbar(scrollState.scrollIndicatorState)
                            .verticalScroll(scrollState)
                    ) {
                        if (logs.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.logs_empty),
                                style = AlphaType.Body,
                                color = AlphaTheme.colors.textSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            Text(
                                text = logs.joinToString("\n\n"),
                                style = AlphaType.Mono,
                                color = AlphaTheme.colors.textSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        CapsuleDialog(
            onDismissRequest = { showClearConfirm = false },
            title = clearLabel,
            message = stringResource(Res.string.m9_logs_clear_confirm),
            confirm = DialogAction(
                label = clearLabel,
                onClick = {
                    showClearConfirm = false
                    scope.launch { logRepository.clearAllLogs() }
                },
            ),
            dismiss = DialogAction(
                label = stringResource(Res.string.cancel_button),
                onClick = { showClearConfirm = false },
            ),
            destructive = true,
        )
    }
}

/** Compact "Log level  INFO ⌄" row opening an in-page menu of [logLevelOptions]. */
@Composable
private fun LogLevelSelector(level: Int, onLevelChange: (Int) -> Unit) {
    val anchor = rememberGlassAnchor()
    var expanded by remember { mutableStateOf(false) }
    val title = stringResource(Res.string.log_level)
    val value = logLevelName(level)
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .glassAnchor(anchor)
            .semantics(mergeDescendants = true) {
                role = Role.DropdownList
                stateDescription = value
            },
        shape = AlphaShapes.row,
        onClick = { expanded = true },
        contentPadding = PaddingValues(horizontal = AlphaDimens.cardPadding, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = AlphaType.Label,
                color = AlphaTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = AlphaType.Label,
                color = AlphaTheme.colors.accent,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Icon(
                painter = painterResource(Res.drawable.keyboard_arrow_right_24px),
                contentDescription = null,
                tint = AlphaTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 4.dp).size(20.dp).rotate(90f),
            )
        }
    }
    GlassMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        anchor = anchor,
        items = logLevelOptions.map { option ->
            GlassMenuItem(
                label = option.name,
                onClick = { if (option.priority != level) onLevelChange(option.priority) },
                selected = option.priority == level,
            )
        },
        placement = PopoverPlacement.BelowEnd,
        paneTitle = title,
    )
}

private fun LogEntry.toDisplayString(): String {
    val exceptionText = if (exception.isNullOrBlank()) "" else "\n$exception"
    return "[${timestamp.toDisplayTimestamp()}] [${priority.toDisplayPriority()}] ${tag ?: "App"}: $message$exceptionText"
}

private fun Long.toDisplayTimestamp(): String {
    val localDateTime =
        Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
    return LOG_TIMESTAMP_FORMAT.format(localDateTime)
}

private fun Int.toDisplayPriority(): String = when (this) {
    1, 2 -> "V"
    3 -> "D"
    4 -> "I"
    5 -> "W"
    6 -> "E"
    7 -> "A"
    else -> toString()
}




