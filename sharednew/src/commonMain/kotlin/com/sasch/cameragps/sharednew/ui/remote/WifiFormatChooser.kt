package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.cancel_button
import cameragps.sharednew.generated.resources.r_ask_batch_option_desc
import cameragps.sharednew.generated.resources.r_ask_title
import cameragps.sharednew.generated.resources.wifi_remote_batch_download_none
import cameragps.sharednew.generated.resources.wifi_remote_batch_skip_hint
import cameragps.sharednew.generated.resources.wifi_remote_download
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatChoice
import com.sasch.cameragps.sharednew.remote.wifi.WifiFormatPolicy
import com.sasch.cameragps.sharednew.remote.wifi.formatByteSize
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.settings.SharedRadioOption
import org.jetbrains.compose.resources.stringResource

/** One row of the format chooser: [label] ("JPG", "RAW", "JPG + RAW"), what it fetches, and whether it can be picked. */
internal data class FormatChooserOption(
    val policy: WifiFormatPolicy,
    val label: String,
    val description: String,
    val enabled: Boolean = true,
)

/** Fixed format name of a policy for the batch chooser (format names are not localized). */
internal fun WifiFormatPolicy.choiceLabel(): String = when (this) {
    WifiFormatPolicy.JpegOnly -> "JPG"
    WifiFormatPolicy.RawOnly -> "RAW"
    WifiFormatPolicy.Original -> "JPG + RAW"
}

/**
 * The row picked by default: the remembered [picked] one while it is still selectable, otherwise the
 * first selectable row; null when none is (the confirm button is then disabled).
 */
internal fun effectiveChooserSelection(
    options: List<FormatChooserOption>,
    picked: WifiFormatPolicy?,
): WifiFormatPolicy? =
    options.firstOrNull { it.enabled && it.policy == picked }?.policy ?: options.firstOrNull { it.enabled }?.policy

/**
 * "Ask each time": an in-page [CapsuleDialog] (never a system window) with one radio row per option
 * and Cancel / Download. The choice is used for this download only — nothing is remembered.
 */
@Composable
internal fun WifiFormatChooserDialog(
    options: List<FormatChooserOption>,
    onConfirm: (WifiFormatPolicy) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf<WifiFormatPolicy?>(null) }
    val selection = effectiveChooserSelection(options, picked)
    CapsuleDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.r_ask_title),
        confirm = DialogAction(
            label = stringResource(Res.string.wifi_remote_download),
            onClick = { selection?.let(onConfirm) },
            enabled = selection != null,
        ),
        dismiss = DialogAction(stringResource(Res.string.cancel_button), onClick = onDismiss),
    ) {
        Column(Modifier.selectableGroup()) {
            options.forEach { option ->
                SharedRadioOption(
                    label = option.label,
                    description = option.description,
                    selected = option.policy == selection,
                    enabled = option.enabled,
                    onClick = { picked = option.policy },
                )
            }
        }
    }
}

/** Single-photo rows: the formats this capture has (see [singleFormatChoices]) with what each fetches. */
@Composable
internal fun singleChooserOptions(choices: List<WifiFormatChoice>): List<FormatChooserOption> =
    choices.map { choice ->
        FormatChooserOption(
            policy = choice.policy,
            label = choice.formatText() ?: choice.policy.choiceLabel(),
            description = formatByteSize(choice.plan.totalBytes),
        )
    }

/**
 * Batch rows: JPG, RAW and JPG+RAW, each "N files · size" plus how many selected photos it would skip
 * and why (e.g. "2 will be skipped (2 without RAW)"); a choice that fetches nothing is disabled.
 */
@Composable
internal fun batchChooserOptions(choices: List<WifiFormatChoice>): List<FormatChooserOption> =
    choices.map { choice ->
        val plan = choice.plan
        val fetches = if (choice.available) {
            stringResource(Res.string.r_ask_batch_option_desc, plan.files.size, formatByteSize(plan.totalBytes))
        } else {
            stringResource(Res.string.wifi_remote_batch_download_none)
        }
        val skips = if (plan.skippedCount > 0) {
            stringResource(Res.string.wifi_remote_batch_skip_hint, plan.skippedCount, skipReasonsText(plan.skipped))
        } else null
        FormatChooserOption(
            policy = choice.policy,
            label = choice.policy.choiceLabel(),
            description = listOfNotNull(fetches, skips).joinToString(" · "),
            enabled = choice.available,
        )
    }
