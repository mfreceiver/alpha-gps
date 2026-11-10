package com.saschl.cameragps.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.saschl.cameragps.R
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleSize
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassFloatingBar
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.IconEmphasis
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTag
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTagStyle
import com.sasch.cameragps.sharednew.ui.components.glass.StatusItem
import com.sasch.cameragps.sharednew.ui.components.glass.StatusLine
import com.sasch.cameragps.sharednew.ui.components.glass.StatusRow
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTextStyles
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import com.sasch.cameragps.sharednew.ui.theme.GlassRenderMode

/*
 * Debug-only showcase of the M0 glass component library (design v3 pages 4–9).
 * Open in Android Studio's preview pane; nothing here ships in release builds.
 */

@Composable
private fun ComponentGallery() {
    GlassBackground {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(AlphaDimens.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap),
        ) {
            Text("AlphaGPS", style = AlphaTextStyles.topBarTitle, color = AlphaTheme.colors.onSurface)
            GlassCard {
                StatusLine("GPS fixed", StatusTone.Success, supportingText = "31.2304° N, 121.4737° E")
                StatusRow(
                    listOf(
                        StatusItem("Written", "128", tone = StatusTone.Active),
                        StatusItem("Failed", "0"),
                        StatusItem("Latest", "12:04"),
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    InfoTag("RAW")
                    InfoTag("JPEG")
                    InfoTag("Downloaded", contentColor = AlphaTheme.colors.success)
                }
            }
            GlassCard(onClick = {}) {
                Text("Clickable card", style = MaterialTheme.typography.titleMedium, color = AlphaTheme.colors.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CapsuleButton("Connect", onClick = {}, role = CapsuleRole.Tonal)
                    CapsuleButton("Remove camera", onClick = {}, role = CapsuleRole.DangerOutlined)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CapsuleButton("Disabled", onClick = {}, enabled = false)
                CapsuleButton("Reading", onClick = {}, role = CapsuleRole.Tonal, loading = true)
            }
            CapsuleButton(
                "Search cameras",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                size = CapsuleSize.Large,
                icon = painterResource(R.drawable.ic_gps_fixed),
            )
            GlassFloatingBar {
                GlassIconButton(painterResource(R.drawable.arrow_back_24px), "Back", onClick = {})
                Spacer(Modifier.weight(1f))
                GlassIconButton(painterResource(R.drawable.info_24px), "Info", onClick = {}, emphasis = IconEmphasis.Primary)
                GlassIconButton(painterResource(R.drawable.settings_24px), "Settings", onClick = {}, emphasis = IconEmphasis.Subtle)
            }
            InfoTag("★ 4", style = InfoTagStyle.OnPhoto)
        }
    }
}

@Composable
private fun DialogGallery(destructive: Boolean) {
    GlassBackground {
        ComponentGalleryBackdropText()
        CapsuleDialog(
            onDismissRequest = {},
            title = if (destructive) "Remove camera?" else "Turn on Bluetooth",
            message = if (destructive) {
                "The pairing with ILCE-7M4 will be deleted."
            } else {
                "AlphaGPS needs Bluetooth to send location to the camera."
            },
            dismiss = DialogAction("Cancel", {}),
            confirm = DialogAction(if (destructive) "Remove" else "Turn on", {}),
            destructive = destructive,
            icon = painterResource(if (destructive) R.drawable.warning_24px else R.drawable.info_24px),
        )
    }
}

@Composable
private fun ComponentGalleryBackdropText() {
    Column(Modifier.padding(AlphaDimens.screenMargin), verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap)) {
        repeat(3) {
            GlassCard { Text("Page content behind the dialog", color = AlphaTheme.colors.onSurface) }
        }
    }
}

@Preview(name = "Components – light", widthDp = 380, heightDp = 860)
@Composable
private fun ComponentsLightPreview() = CameraGpsTheme(darkTheme = false) { ComponentGallery() }

@Preview(name = "Components – dark", widthDp = 380, heightDp = 860)
@Composable
private fun ComponentsDarkPreview() = CameraGpsTheme(darkTheme = true) { ComponentGallery() }

@Preview(name = "Components – solid fallback", widthDp = 380, heightDp = 860)
@Composable
private fun ComponentsSolidPreview() =
    CameraGpsTheme(darkTheme = false, glassRenderMode = GlassRenderMode.Solid) { ComponentGallery() }

@Preview(name = "Dialog – light", widthDp = 380, heightDp = 640)
@Composable
private fun DialogLightPreview() = CameraGpsTheme(darkTheme = false) { DialogGallery(destructive = false) }

@Preview(name = "Dialog – dark, destructive", widthDp = 380, heightDp = 640)
@Composable
private fun DialogDarkPreview() = CameraGpsTheme(darkTheme = true) { DialogGallery(destructive = true) }

@Preview(name = "Form dialog – landscape", widthDp = 800, heightDp = 360)
@Composable
private fun FormDialogPreview() = CameraGpsTheme(darkTheme = false) {
    GlassBackground {
        ComponentGalleryBackdropText()
        CapsuleDialog(
            onDismissRequest = {},
            title = "Rename camera",
            dismiss = DialogAction("Cancel", {}),
            confirm = DialogAction("Save", {}, loading = true),
            onClose = {},
        ) {
            Text("Name shown in AlphaGPS only.", color = AlphaTheme.colors.onSurfaceVariant)
        }
    }
}
