package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.cancel_button
import cameragps.sharednew.generated.resources.continue_button
import cameragps.sharednew.generated.resources.help_report_issue
import cameragps.sharednew.generated.resources.open_in_new_24px
import cameragps.sharednew.generated.resources.wifi_remote_experimental_message
import cameragps.sharednew.generated.resources.wifi_remote_experimental_title
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.help.AlphaGpsLinks
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** One-time notice before the first Wi-Fi connection. */
@Composable
internal fun WifiRemoteExperimentalNotice(onContinue: () -> Unit, onCancel: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    CapsuleDialog(
        onDismissRequest = onCancel,
        confirm = DialogAction(stringResource(Res.string.continue_button), onContinue),
        dismiss = DialogAction(stringResource(Res.string.cancel_button), onCancel),
        title = stringResource(Res.string.wifi_remote_experimental_title),
    ) {
        Text(
            stringResource(Res.string.wifi_remote_experimental_message),
            style = AlphaType.Body,
            color = AlphaTheme.colors.textSecondary,
        )
        CapsuleButton(
            text = stringResource(Res.string.help_report_issue),
            icon = painterResource(Res.drawable.open_in_new_24px),
            onClick = { uriHandler.openUri(AlphaGpsLinks.ISSUES) },
            role = CapsuleRole.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
