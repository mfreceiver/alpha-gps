package com.saschl.cameragps.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.content_copy_24px
import cameragps.sharednew.generated.resources.m9_link_copied
import cameragps.sharednew.generated.resources.m9_link_copy
import cameragps.sharednew.generated.resources.m9_link_open
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSurface
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Card section title (heading semantics for TalkBack). */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = AlphaTheme.colors.onSurface,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
internal fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = AlphaTheme.colors.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * A web address row: tapping the row opens [url] in the browser, the trailing icon copies it.
 * Both targets are ≥ 48 dp.
 */
@Composable
internal fun LinkRow(label: String, url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val copyLabel = stringResource(Res.string.m9_link_copy)
    val openLabel = stringResource(Res.string.m9_link_open)
    val copiedText = stringResource(Res.string.m9_link_copied)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassSurface(
            modifier = Modifier.weight(1f),
            shape = AlphaShapes.row,
            onClick = { runCatching { uriHandler.openUri(url) } },
            onClickLabel = openLabel,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(Modifier.heightIn(min = AlphaDimens.minTouchTarget - 16.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = AlphaTheme.colors.onSurface,
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = AlphaTheme.colors.primary,
                )
            }
        }
        GlassIconButton(
            icon = painterResource(Res.drawable.content_copy_24px),
            contentDescription = copyLabel,
            onClick = { copyToClipboard(context, label, url, copiedText) },
        )
    }
}

/** Copies [text] to the clipboard; Android 13+ shows its own confirmation, older versions get a toast. */
internal fun copyToClipboard(context: Context, label: String, text: String, toastText: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
    }
}
