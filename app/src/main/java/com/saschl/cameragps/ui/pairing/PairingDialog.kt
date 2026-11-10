package com.saschl.cameragps.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources._1_turn_on_your_camera
import cameragps.sharednew.generated.resources._2_go_to_camera_settings_and_enable_bluetooth_pairing_mode
import cameragps.sharednew.generated.resources._3_make_sure_the_camera_is_discoverable
import cameragps.sharednew.generated.resources.camera_pairing_required_title
import cameragps.sharednew.generated.resources.cancel
import cameragps.sharednew.generated.resources.continue_label
import cameragps.sharednew.generated.resources.done
import cameragps.sharednew.generated.resources.failed_to_pair_with_device
import cameragps.sharednew.generated.resources.once_your_camera_is_ready_tap_continue_to_start_pairing
import cameragps.sharednew.generated.resources.pairing_camera_title
import cameragps.sharednew.generated.resources.pairing_complete_title
import cameragps.sharednew.generated.resources.pairing_failed_hint_camera_pairing
import cameragps.sharednew.generated.resources.pairing_failed_hint_intro
import cameragps.sharednew.generated.resources.pairing_failed_hint_pairing_mode
import cameragps.sharednew.generated.resources.pairing_failed_hint_phone_pairing
import cameragps.sharednew.generated.resources.pairing_failed_title
import cameragps.sharednew.generated.resources.pairing_with_device_please_wait
import cameragps.sharednew.generated.resources.successfully_paired_with_device
import cameragps.sharednew.generated.resources.to_pair_with_your_camera_please
import cameragps.sharednew.generated.resources.try_again
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.theme.AlphaTextStyles
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun PairingConfirmationDialogWithLoading(
    deviceName: String,
    isPairing: Boolean,
    pairingResult: PairingResult?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val title = when {
        isPairing -> stringResource(Res.string.pairing_camera_title)
        pairingResult == PairingResult.SUCCESS -> stringResource(Res.string.pairing_complete_title)
        pairingResult == PairingResult.FAILED -> stringResource(Res.string.pairing_failed_title)
        else -> stringResource(Res.string.camera_pairing_required_title)
    }
    val confirm = when {
        isPairing -> null // No buttons during pairing
        pairingResult == PairingResult.SUCCESS -> DialogAction(stringResource(Res.string.done), onDismiss)
        pairingResult == PairingResult.FAILED -> DialogAction(stringResource(Res.string.try_again), onRetry)
        else -> DialogAction(stringResource(Res.string.continue_label), onConfirm)
    }
    val dismiss = if (!isPairing && pairingResult != PairingResult.SUCCESS) {
        DialogAction(stringResource(Res.string.cancel), onDismiss)
    } else {
        null
    }
    CapsuleDialog(
        onDismissRequest = if (isPairing) { {} } else onDismiss, // Prevent dismissal during pairing
        title = title,
        confirm = confirm,
        dismiss = dismiss,
        dismissOnBack = !isPairing,
        dismissOnScrimClick = !isPairing,
    ) {
        val colors = AlphaTheme.colors
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { when {
            isPairing -> {
                // Show loading state
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 16.dp).size(32.dp),
                        color = colors.primary,
                        strokeWidth = 3.dp
                    )
                    PairingText(
                        stringResource(Res.string.pairing_with_device_please_wait, deviceName),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }

            pairingResult == PairingResult.SUCCESS -> {
                // Show success state
                PairingText(
                    stringResource(Res.string.successfully_paired_with_device, deviceName),
                    color = colors.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            pairingResult == PairingResult.FAILED -> {
                // Show failure state with camera-side troubleshooting steps (the dialog scrolls)
                PairingText(
                    stringResource(Res.string.failed_to_pair_with_device, deviceName),
                    color = colors.error,
                    modifier = Modifier.fillMaxWidth(),
                )
                PairingText(stringResource(Res.string.pairing_failed_hint_intro), secondary = true)
                PairingText(stringResource(Res.string.pairing_failed_hint_pairing_mode), secondary = true)
                PairingText(stringResource(Res.string.pairing_failed_hint_camera_pairing), secondary = true)
                PairingText(stringResource(Res.string.pairing_failed_hint_phone_pairing), secondary = true)
            }

            else -> {
                // Show initial instructions
                PairingText(stringResource(Res.string.to_pair_with_your_camera_please, deviceName))
                PairingText(stringResource(Res.string._1_turn_on_your_camera))
                PairingText(stringResource(Res.string._2_go_to_camera_settings_and_enable_bluetooth_pairing_mode))
                PairingText(stringResource(Res.string._3_make_sure_the_camera_is_discoverable))
                PairingText(
                    stringResource(Res.string.once_your_camera_is_ready_tap_continue_to_start_pairing),
                    color = colors.primary,
                )
            }
        } }
    }
}

@Composable
private fun PairingText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    secondary: Boolean = false,
    textAlign: TextAlign? = null,
) {
    val colors = AlphaTheme.colors
    Text(
        text = text,
        modifier = modifier,
        style = AlphaTextStyles.dialogBody,
        color = when {
            color != Color.Unspecified -> color
            secondary -> colors.onSurfaceVariant
            else -> colors.onSurface
        },
        textAlign = textAlign,
    )
}
