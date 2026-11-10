package com.saschl.cameragps.ui.settings

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.cancel_button
import cameragps.sharednew.generated.resources.log_level
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.settings.SharedRadioOption
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsNavRow
import com.saschl.cameragps.service.FileTree
import com.saschl.cameragps.utils.PreferencesManager
import org.jetbrains.compose.resources.stringResource
import timber.log.Timber

/** Log level row (current level as value) opening a [CapsuleDialog] radio list. */
@Composable
internal fun LogLevelSettingsCard() {
    val context = LocalContext.current
    val currentLogLevel = remember {
        mutableIntStateOf(PreferencesManager.logLevel(context))
    }
    var showLogLevelDialog by remember { mutableStateOf(false) }

    SharedSettingsNavRow(
        title = stringResource(Res.string.log_level),
        value = getLogLevelName(currentLogLevel.intValue),
        onClick = { showLogLevelDialog = true },
    )

    if (showLogLevelDialog) {
        LogLevelSelectionDialog(
            currentLevel = currentLogLevel.intValue,
            onLevelSelected = { level ->
                currentLogLevel.intValue = level
                PreferencesManager.setLogLevel(context, level)
                showLogLevelDialog = false
                Timber.uprootAll()
                Timber.plant(FileTree(context, level))
            },
            onDismiss = { showLogLevelDialog = false }
        )
    }
}

private fun getLogLevelName(level: Int): String {
    return when (level) {
        android.util.Log.VERBOSE -> "VERBOSE"
        android.util.Log.DEBUG -> "DEBUG"
        android.util.Log.INFO -> "INFO"
        android.util.Log.WARN -> "WARN"
        android.util.Log.ERROR -> "ERROR"
        else -> "DEBUG"
    }
}

@Composable
private fun LogLevelSelectionDialog(
    currentLevel: Int,
    onLevelSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val logLevels = listOf(
        android.util.Log.VERBOSE to "VERBOSE",
        android.util.Log.DEBUG to "DEBUG",
        android.util.Log.INFO to "INFO",
        android.util.Log.WARN to "WARN",
        android.util.Log.ERROR to "ERROR"
    )

    CapsuleDialog(
        onDismissRequest = onDismiss,
        confirm = null,
        dismiss = DialogAction(
            label = stringResource(Res.string.cancel_button),
            onClick = onDismiss,
        ),
        title = stringResource(Res.string.log_level),
    ) {
        Column(Modifier.selectableGroup()) {
            logLevels.forEach { (level, name) ->
                SharedRadioOption(
                    label = name,
                    selected = level == currentLevel,
                    onClick = { onLevelSelected(level) },
                )
            }
        }
    }
}
