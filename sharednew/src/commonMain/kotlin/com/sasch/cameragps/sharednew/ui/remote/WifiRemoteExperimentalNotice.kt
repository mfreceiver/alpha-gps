package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.cancel_button
import cameragps.sharednew.generated.resources.continue_button
import cameragps.sharednew.generated.resources.help_report_issue
import cameragps.sharednew.generated.resources.wifi_remote_experimental_message
import cameragps.sharednew.generated.resources.wifi_remote_experimental_title
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.help.AlphaGpsLinks
import org.jetbrains.compose.resources.stringResource

/** One-time notice before the first Wi-Fi connection (design v3: capsule dialog, cancel left). */
@Composable
internal fun WifiRemoteExperimentalNotice(onContinue: () -> Unit, onCancel: () -> Unit) {
    CapsuleDialog(
        onDismissRequest = onCancel,
        confirm = DialogAction(stringResource(Res.string.continue_button), onContinue),
        dismiss = DialogAction(stringResource(Res.string.cancel_button), onCancel),
        title = stringResource(Res.string.wifi_remote_experimental_title),
    ) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(Res.string.wifi_remote_experimental_message))
            val linkStyle = TextLinkStyles(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
            )
            val reportIssue = stringResource(Res.string.help_report_issue)
            Text(buildAnnotatedString {
                withLink(LinkAnnotation.Url(AlphaGpsLinks.ISSUES, linkStyle)) {
                    append(reportIssue)
                }
            })
        }
    }
}
