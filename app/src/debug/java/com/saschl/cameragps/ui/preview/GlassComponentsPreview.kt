package com.saschl.cameragps.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.saschl.cameragps.R
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleSize
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBottomSheet
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCheckbox
import com.sasch.cameragps.sharednew.ui.components.glass.GlassFloatingBar
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconTile
import com.sasch.cameragps.sharednew.ui.components.glass.GlassMenu
import com.sasch.cameragps.sharednew.ui.components.glass.GlassMenuItem
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressCircle
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressLinear
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressSize
import com.sasch.cameragps.sharednew.ui.components.glass.GlassRadio
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSegmented
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSnackbarDuration
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSnackbarHost
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSwitch
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTextField
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTileAction
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTopBar
import com.sasch.cameragps.sharednew.ui.components.glass.GlassWarningCard
import com.sasch.cameragps.sharednew.ui.components.glass.IconEmphasis
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTag
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTagStyle
import com.sasch.cameragps.sharednew.ui.components.glass.RevealValueRow
import com.sasch.cameragps.sharednew.ui.components.glass.StatusBadge
import com.sasch.cameragps.sharednew.ui.components.glass.StatusItem
import com.sasch.cameragps.sharednew.ui.components.glass.StatusLine
import com.sasch.cameragps.sharednew.ui.components.glass.StatusRow
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import com.sasch.cameragps.sharednew.ui.components.glass.glassAnchor
import com.sasch.cameragps.sharednew.ui.components.glass.rememberGlassAnchor
import com.sasch.cameragps.sharednew.ui.components.glass.rememberGlassSnackbarHostState
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import com.sasch.cameragps.sharednew.ui.theme.GlassRenderMode

/*
 * Debug-only showcase of the glass component library (design v3 + 0.3.0 B0: AlphaType tiers,
 * colour roles, glass material, N1–N20). Open in Android Studio's preview pane (interactive mode
 * for popovers / snackbars / sheets); nothing here ships in release builds. Every gallery is
 * previewed light + dark and at the three window classes (A portrait, B near-square, C landscape).
 */

@Composable
private fun TypeAndColourSection() {
    val c = AlphaTheme.colors
    GlassCard {
        Text("Title 22/28", style = AlphaType.Title, color = c.textPrimary)
        Text("Heading 18/24", style = AlphaType.Heading, color = c.textPrimary)
        Text("Body 15/22 — textSecondary", style = AlphaType.Body, color = c.textSecondary)
        Text("Label 15/20 — accent", style = AlphaType.Label, color = c.accent)
        Text("Caption 13/18 — textMuted", style = AlphaType.Caption, color = c.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tag success", style = AlphaType.Tag, color = c.success)
            Text("Tag warning", style = AlphaType.Tag, color = c.warningText)
            Text("Tag danger", style = AlphaType.Tag, color = c.danger)
        }
        Text("Mono 12 — 2026-11-11 01:02:03 I/Gps", style = AlphaType.Mono, color = c.textSecondary)
    }
}

@Composable
private fun ControlsSection() {
    var on by remember { mutableStateOf(true) }
    var radio by remember { mutableIntStateOf(0) }
    var check by remember { mutableStateOf(ToggleableState.Indeterminate) }
    var segment by remember { mutableIntStateOf(1) }
    var text by remember { mutableStateOf("ILCE-7M4") }
    var revealed by remember { mutableStateOf(false) }
    val c = AlphaTheme.colors
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto connect", style = AlphaType.Body, color = c.textPrimary, modifier = Modifier.weight(1f))
            GlassSwitch(on, onCheckedChange = { on = it })
            GlassSwitch(!on, onCheckedChange = { on = !it })
            GlassSwitch(false, onCheckedChange = null, enabled = false)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassRadio(radio == 0, onClick = { radio = 0 })
            Text("Every second", style = AlphaType.Body, color = c.textPrimary)
            GlassRadio(radio == 1, onClick = { radio = 1 })
            Text("On shutter", style = AlphaType.Body, color = c.textPrimary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassCheckbox(check, onStateChange = { check = it })
            GlassCheckbox(true, onCheckedChange = {})
            GlassCheckbox(false, onCheckedChange = {})
            GlassTileAction(painterResource(R.drawable.info_24px), "Preview", onClick = {})
        }
        GlassSegmented(listOf("All", "RAW", "JPEG"), segment, onSelect = { segment = it })
        GlassTextField(text, { text = it }, label = "Camera name", supportingText = "Shown in AlphaGPS only")
        GlassTextField("", {}, label = "Password", placeholder = "8+ characters", isError = true, supportingText = "Required")
        RevealValueRow("Bluetooth address", "AA:BB:CC:DD:EE:FF", revealed, { revealed = it })
    }
}

@Composable
private fun StatusSection() {
    GlassCard {
        StatusLine("GPS fixed", StatusTone.Success, supportingText = "31.2304° N, 121.4737° E")
        StatusRow(
            listOf(
                // N11: the icon carries the tone, the value stays in textPrimary.
                StatusItem("Signal", "Strong", painterResource(R.drawable.ic_gps_fixed), iconTone = StatusTone.Success),
                StatusItem("Written", "128", painterResource(R.drawable.ic_gps_fixed), iconTone = StatusTone.Active),
                StatusItem("GPS", "Weak", painterResource(R.drawable.warning_24px), tone = StatusTone.Warning),
            ),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusBadge("Connected", StatusTone.Success)
            StatusBadge("Location off", StatusTone.Warning)
            StatusBadge("Failed", StatusTone.Error)
            StatusBadge("Idle", StatusTone.Neutral)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            InfoTag("RAW")
            InfoTag("JPEG")
            InfoTag("Downloaded", contentColor = AlphaTheme.colors.success)
            GlassIconTile(painterResource(R.drawable.ic_gps_fixed), tone = StatusTone.Success)
            GlassIconTile(painterResource(R.drawable.warning_24px), tone = StatusTone.Warning)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassProgressCircle(size = GlassProgressSize.Small)
            GlassProgressCircle(progress = 0.4f)
            GlassProgressCircle(progress = 0.8f, size = GlassProgressSize.Large)
            GlassProgressLinear(Modifier.weight(1f), progress = 0.6f)
        }
    }
    GlassWarningCard(
        title = "Location permission missing",
        message = "AlphaGPS cannot send a position to the camera without it.",
        icon = painterResource(R.drawable.warning_24px),
        actionLabel = "Grant",
        onAction = {},
    )
    GlassWarningCard(title = "Bluetooth is off", message = null, tone = StatusTone.Error, icon = painterResource(R.drawable.warning_24px))
}

@Composable
private fun ButtonsSection() {
    val menuAnchor = rememberGlassAnchor()
    var menu by remember { mutableStateOf(false) }
    GlassCard {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CapsuleButton("Primary", onClick = {})
            CapsuleButton("Connect", onClick = {}, role = CapsuleRole.Tonal)
            CapsuleButton("Cancel", onClick = {}, role = CapsuleRole.Secondary)
            CapsuleButton("Remove", onClick = {}, role = CapsuleRole.Danger)
            CapsuleButton("Disconnect", onClick = {}, role = CapsuleRole.DangerOutlined, size = CapsuleSize.Small)
            CapsuleButton("Disabled", onClick = {}, enabled = false)
            CapsuleButton("Reading", onClick = {}, role = CapsuleRole.Tonal, loading = true)
        }
    }
    // N12 glass variants straight on the backdrop (top-level glass blurs the orbs).
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CapsuleButton("Glass primary", onClick = {}, glass = true)
        CapsuleButton("Glass tonal", onClick = {}, role = CapsuleRole.Tonal, glass = true)
        CapsuleButton("Glass secondary", onClick = {}, role = CapsuleRole.Secondary, glass = true)
        CapsuleButton("Glass danger", onClick = {}, role = CapsuleRole.Danger, glass = true)
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
        GlassIconButton(
            painterResource(R.drawable.baseline_view_list_24),
            "View options",
            onClick = { menu = true },
            modifier = Modifier.glassAnchor(menuAnchor),
        )
    }
    GlassMenu(
        expanded = menu,
        onDismissRequest = { menu = false },
        anchor = menuAnchor,
        items = listOf(
            GlassMenuItem("Grid", onClick = {}, selected = true),
            GlassMenuItem("List", onClick = {}, selected = false),
            GlassMenuItem("Delete all", onClick = {}, destructive = true),
        ),
    )
}

/** Viewer chrome over a (fake) photo: onPhoto text, Viewer glass, photo checkbox, on-photo tag. */
@Composable
private fun ViewerSection() {
    CameraGpsTheme(darkTheme = true) {
        val c = AlphaTheme.colors
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassFloatingBar(level = GlassLevel.Viewer) {
                    GlassIconButton(painterResource(R.drawable.arrow_back_24px), "Back", onClick = {}, container = null)
                    Column(Modifier.weight(1f)) {
                        Text("DSC01234.ARW", style = AlphaType.Label, color = c.onPhoto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("2026-11-11 · 24.2 MB", style = AlphaType.Caption, color = c.onPhotoSecondary)
                    }
                    GlassProgressCircle(onPhoto = true, size = GlassProgressSize.Small)
                    GlassCheckbox(false, onCheckedChange = {}, onPhoto = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    InfoTag("★ 4", style = InfoTagStyle.OnPhoto)
                    InfoTag("RAW", style = InfoTagStyle.OnPhoto)
                    CapsuleButton("Download", onClick = {}, role = CapsuleRole.GlassPrimary, glassLevel = GlassLevel.Viewer)
                }
            }
        }
    }
}

@Composable
private fun ComponentGallery(wide: Boolean = false) {
    val snackbar = rememberGlassSnackbarHostState()
    remember { snackbar.show("Photo saved to Pictures/AlphaGPS", actionLabel = "Open", duration = GlassSnackbarDuration.Indefinite) }
    val scroll = rememberScrollState()
    GlassBackground {
        Column(Modifier.fillMaxSize()) {
            GlassTopBar(
                title = "AlphaGPS",
                subtitle = "Component gallery",
                navigationIcon = { GlassIconButton(painterResource(R.drawable.arrow_back_24px), "Back", onClick = {}) },
                actions = { GlassIconButton(painterResource(R.drawable.settings_24px), "Settings", onClick = {}) },
                scrolled = scroll.value > 0,
            )
            Column(
                Modifier
                    .verticalScroll(scroll)
                    .padding(AlphaDimens.screenMargin)
                    .then(if (wide) Modifier.widthIn(max = 1200.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap),
            ) {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap)) {
                            TypeAndColourSection()
                            StatusSection()
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap)) {
                            ControlsSection()
                            ButtonsSection()
                            ViewerSection()
                        }
                    }
                } else {
                    TypeAndColourSection()
                    ControlsSection()
                    StatusSection()
                    ButtonsSection()
                    ViewerSection()
                }
            }
        }
        GlassSnackbarHost(snackbar)
    }
}

// ---- window class A (portrait) / B (near-square) / C (landscape), light + dark ----------------

@Preview(name = "Components A – light", widthDp = 380, heightDp = 1900)
@Composable
private fun ComponentsLightPreview() = CameraGpsTheme(darkTheme = false) { ComponentGallery() }

@Preview(name = "Components A – dark", widthDp = 380, heightDp = 1900)
@Composable
private fun ComponentsDarkPreview() = CameraGpsTheme(darkTheme = true) { ComponentGallery() }

@Preview(name = "Components B – light", widthDp = 720, heightDp = 1300)
@Composable
private fun ComponentsSquareLightPreview() = CameraGpsTheme(darkTheme = false) { ComponentGallery(wide = true) }

@Preview(name = "Components B – dark", widthDp = 720, heightDp = 1300)
@Composable
private fun ComponentsSquareDarkPreview() = CameraGpsTheme(darkTheme = true) { ComponentGallery(wide = true) }

@Preview(name = "Components C – light", widthDp = 900, heightDp = 420)
@Composable
private fun ComponentsLandscapeLightPreview() = CameraGpsTheme(darkTheme = false) { ComponentGallery(wide = true) }

@Preview(name = "Components C – dark", widthDp = 900, heightDp = 420)
@Composable
private fun ComponentsLandscapeDarkPreview() = CameraGpsTheme(darkTheme = true) { ComponentGallery(wide = true) }

@Preview(name = "Components – solid fallback", widthDp = 380, heightDp = 1900)
@Composable
private fun ComponentsSolidPreview() =
    CameraGpsTheme(darkTheme = false, glassRenderMode = GlassRenderMode.Solid) { ComponentGallery() }

// ---- overlays ---------------------------------------------------------------------------------

@Composable
private fun BackdropCards() {
    Column(Modifier.padding(AlphaDimens.screenMargin), verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap)) {
        repeat(3) {
            GlassCard { Text("Page content behind the overlay", style = AlphaType.Body, color = AlphaTheme.colors.textPrimary) }
        }
    }
}

@Composable
private fun DialogGallery(destructive: Boolean) {
    GlassBackground {
        BackdropCards()
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
        BackdropCards()
        CapsuleDialog(
            onDismissRequest = {},
            title = "Rename camera",
            dismiss = DialogAction("Cancel", {}),
            confirm = DialogAction("Save", {}, loading = true),
            onClose = {},
        ) {
            GlassTextField("ILCE-7M4", {}, label = "Name", supportingText = "Shown in AlphaGPS only.")
        }
    }
}

@Preview(name = "Info dialog – N18 header row", widthDp = 380, heightDp = 640)
@Composable
private fun InfoDialogPreview() = CameraGpsTheme(darkTheme = true) {
    GlassBackground {
        BackdropCards()
        CapsuleDialog(
            onDismissRequest = {},
            title = "Camera info for a camera with a very long name that must be shortened",
            confirm = null,
            dismiss = null,
            onClose = {},
        ) {
            StatusLine("Connected", StatusTone.Success)
        }
    }
}

@Preview(name = "Bottom sheet – light", widthDp = 380, heightDp = 760)
@Composable
private fun BottomSheetLightPreview() = CameraGpsTheme(darkTheme = false) { SheetGallery() }

@Preview(name = "Bottom sheet – dark, landscape", widthDp = 860, heightDp = 400)
@Composable
private fun BottomSheetDarkPreview() = CameraGpsTheme(darkTheme = true) { SheetGallery() }

@Composable
private fun SheetGallery() {
    GlassBackground {
        BackdropCards()
        GlassBottomSheet(visible = true, onDismissRequest = {}, title = "Sort photos") {
            Text("Sort photos", style = AlphaType.Heading, color = AlphaTheme.colors.textPrimary)
            listOf("Newest first", "Oldest first", "Name").forEachIndexed { i, label ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GlassRadio(i == 0, onClick = {})
                    Text(label, style = AlphaType.Body, color = AlphaTheme.colors.textPrimary)
                }
            }
            Box(Modifier.size(8.dp))
        }
    }
}
