package com.sasch.cameragps.sharednew.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.whats_new_close
import cameragps.sharednew.generated.resources.whats_new_title
import cameragps.sharednew.generated.resources.whats_new_version
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsNavRow
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun WhatsNewDialog(release: ReleaseNotes, onDismiss: () -> Unit) {
    CapsuleDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.whats_new_title),
        confirm = DialogAction(stringResource(Res.string.whats_new_close), onDismiss),
        dismiss = null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(Res.string.whats_new_version, release.version),
                style = MaterialTheme.typography.titleSmall,
                color = AlphaTheme.colors.primary,
            )
            release.highlights.forEach { highlight ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("•", color = AlphaTheme.colors.onSurface)
                    Text(
                        stringResource(highlight),
                        color = AlphaTheme.colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * "What's new" row of the settings' help & diagnostics group: the current version, opening the
 * release notes in an in-page dialog. Hidden when there are no notes for this version.
 */
@Composable
fun WhatsNewSettingsRow(state: WhatsNewState) {
    val release = state.release ?: return
    var showDialog by rememberSaveable { mutableStateOf(false) }
    SharedSettingsNavRow(
        title = stringResource(Res.string.whats_new_title),
        description = stringResource(Res.string.whats_new_version, release.version),
        onClick = { showDialog = true },
    )
    if (showDialog) {
        WhatsNewDialog(release) {
            state.dismiss()
            showDialog = false
        }
    }
}
