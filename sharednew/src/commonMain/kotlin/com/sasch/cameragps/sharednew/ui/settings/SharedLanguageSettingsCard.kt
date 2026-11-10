package com.sasch.cameragps.sharednew.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.cancel_button
import cameragps.sharednew.generated.resources.language_selection
import cameragps.sharednew.generated.resources.language_system
import com.sasch.cameragps.sharednew.language.AppLanguage
import com.sasch.cameragps.sharednew.language.LanguagePreference
import com.sasch.cameragps.sharednew.language.SupportedLanguages
import com.sasch.cameragps.sharednew.language.appLanguagePreference
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import org.jetbrains.compose.resources.stringResource

/**
 * Language picker for the settings screens of both platforms. Persisting and applying
 * the choice is [LanguagePreference]'s job; null means follow the system language.
 */
@Composable
fun SharedLanguageSettingsCard(preference: LanguagePreference = appLanguagePreference) {
    val currentLanguage by preference.selected.collectAsState()
    // The same setting also lives in the system's per-app language screen, and a change
    // there only recreates the activity (Android) / reactivates the app (iOS) rather
    // than telling us, so re-read the platform store whenever we come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { preference.refresh() }
    LanguageSettingsCardContent(currentLanguage, preference::select)
}

/** Split out from [SharedLanguageSettingsCard] so it can be rendered with arbitrary state. */
@Composable
internal fun LanguageSettingsCardContent(
    currentLanguage: AppLanguage?,
    onLanguageSelected: (AppLanguage?) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val systemLabel = stringResource(Res.string.language_system)

    SharedSettingsNavRow(
        title = stringResource(Res.string.language_selection),
        value = currentLanguage?.displayName ?: systemLabel,
        onClick = { showDialog = true },
    )

    if (showDialog) {
        fun select(language: AppLanguage?) {
            showDialog = false
            onLanguageSelected(language)
        }
        CapsuleDialog(
            onDismissRequest = { showDialog = false },
            title = stringResource(Res.string.language_selection),
            confirm = null,
            dismiss = DialogAction(stringResource(Res.string.cancel_button), onClick = { showDialog = false }),
        ) {
            Column(modifier = Modifier.selectableGroup()) {
                SharedRadioOption(systemLabel, currentLanguage == null, { select(null) })
                SupportedLanguages.entries.forEach { language ->
                    SharedRadioOption(
                        label = language.displayName,
                        selected = currentLanguage?.tag == language.tag,
                        onClick = { select(language) },
                    )
                }
            }
        }
    }
}
