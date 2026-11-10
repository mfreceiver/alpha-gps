package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.close_24px
import cameragps.sharednew.generated.resources.m0_dialog_close
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** A dialog button: [loading] shows a spinner and ignores clicks (async confirm). */
@Immutable
data class DialogAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val loading: Boolean = false,
)

/**
 * Pure button layout of a dialog, start → end (design `lib3.dlg_btns`): cancel/dismiss on the left as
 * [CapsuleRole.Secondary], confirm on the right as [CapsuleRole.Primary] ([CapsuleRole.Danger] when
 * [destructive]); a single button spans the full width with its own role.
 */
fun dialogButtonRoles(hasDismiss: Boolean, hasConfirm: Boolean, destructive: Boolean): List<CapsuleRole> {
    val confirmRole = if (destructive) CapsuleRole.Danger else CapsuleRole.Primary
    return buildList {
        if (hasDismiss) add(CapsuleRole.Secondary)
        if (hasConfirm) add(confirmRole)
    }
}

/** The bottom capsule row of every dialog: [dismiss] left (secondary), [confirm] right (primary / red). */
@Composable
fun DialogButtonRow(
    confirm: DialogAction?,
    dismiss: DialogAction?,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val roles = dialogButtonRoles(dismiss != null, confirm != null, destructive)
    val actions = listOfNotNull(dismiss, confirm)
    if (actions.isEmpty()) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AlphaDimens.dialogButtonGap)) {
        actions.forEachIndexed { index, action ->
            CapsuleButton(
                text = action.label,
                onClick = action.onClick,
                modifier = Modifier.weight(1f),
                role = roles[index],
                enabled = action.enabled,
                loading = action.loading,
            )
        }
    }
}

/**
 * Confirmation dialog (design page 9): optional icon, centred title and message, capsule row.
 * Example — remove a camera:
 * ```
 * CapsuleDialog(
 *     onDismissRequest = onCancel,
 *     title = stringResource(Res.string.device_remove_confirm_title),
 *     message = stringResource(Res.string.device_remove_confirm_message, name),
 *     dismiss = DialogAction(stringResource(Res.string.cancel_button), onCancel),
 *     confirm = DialogAction(stringResource(Res.string.remove), onRemove),
 *     destructive = true,
 * )
 * ```
 */
@Composable
fun CapsuleDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String?,
    confirm: DialogAction?,
    dismiss: DialogAction?,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    icon: Painter? = null,
    iconTint: Color = Color.Unspecified,
) {
    CapsuleDialog(
        onDismissRequest = onDismissRequest,
        confirm = confirm,
        dismiss = dismiss,
        modifier = modifier,
        title = title,
        icon = icon,
        iconTint = iconTint,
        destructive = destructive,
        centered = true,
    ) {
        if (message != null) {
            Text(
                message,
                style = AlphaType.Body,
                color = AlphaTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The single dialog container of the app: G3 glass panel (radius 28, ≤ 318 dp wide) over a scrim,
 * content slot, capsule row at the bottom. Form / info dialogs (rename, Ⓘ) keep [centered] false.
 *
 * - Scrim tap and system back call [onDismissRequest] — wire it to the same thing as the left
 *   (cancel) button. Disable with [dismissOnScrimClick] / [dismissOnBack] while busy.
 * - [onClose] adds a ✕ at the top end, in a header row shared with [title] (never overlapping the
 *   content; info dialogs: ✕ instead of an OK button).
 * - Inside a [GlassBackground] the dialog is drawn in-tree (it blurs the page) and takes keyboard
 *   focus like a modal window (GlassOverlayFocus.kt); elsewhere it falls back to a platform dialog
 *   window with the solid glass fill and the window's own focus handling.
 * - The content scrolls when the window is short (landscape, IME); the buttons stay visible.
 */
@Composable
fun CapsuleDialog(
    onDismissRequest: () -> Unit,
    confirm: DialogAction?,
    dismiss: DialogAction?,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: Painter? = null,
    iconTint: Color = Color.Unspecified,
    destructive: Boolean = false,
    centered: Boolean = false,
    onClose: (() -> Unit)? = null,
    dismissOnBack: Boolean = true,
    dismissOnScrimClick: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val panel: @Composable () -> Unit = {
        CapsuleDialogPanel(
            confirm = confirm,
            dismiss = dismiss,
            modifier = modifier,
            title = title,
            icon = icon,
            iconTint = iconTint,
            destructive = destructive,
            centered = centered,
            onClose = onClose,
            content = content,
        )
    }
    val host = LocalGlassOverlayHost.current
    if (host != null) {
        GlassOverlay(host) {
            NavigationBackHandler(
                state = rememberNavigationEventState(NavigationEventInfo.None),
                isBackEnabled = dismissOnBack,
                onBackCompleted = onDismissRequest,
            )
            val scrim = AlphaTheme.colors.scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrim)
                    .pointerInput(dismissOnScrimClick, onDismissRequest) {
                        detectTapGestures { if (dismissOnScrimClick) onDismissRequest() }
                    }
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                panel()
            }
        }
    } else {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                dismissOnBackPress = dismissOnBack,
                dismissOnClickOutside = dismissOnScrimClick,
                usePlatformDefaultWidth = false,
            ),
        ) {
            // A separate window cannot sample the page: solid glass. No overlay entry either, so the
            // in-page focus handling stays off: window focus as in 0.2.1 (panel not focusable).
            CompositionLocalProvider(LocalGlassBackdrop provides null, LocalGlassOverlayEntry provides null) {
                Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) { panel() }
            }
        }
    }
}

@Composable
private fun CapsuleDialogPanel(
    confirm: DialogAction?,
    dismiss: DialogAction?,
    modifier: Modifier,
    title: String?,
    icon: Painter?,
    iconTint: Color,
    destructive: Boolean,
    centered: Boolean,
    onClose: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AlphaTheme.colors
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(220)) }
    val horizontal = if (centered) Alignment.CenterHorizontally else Alignment.Start
    // Hosted dialog: keyboard focus enters it (and returns to the layer below when it closes).
    // Platform-Dialog fallback: null, no focus handling added (see rememberModalPanelFocus).
    val panelFocus = rememberModalPanelFocus()
    GlassSurface(
        modifier = modifier
            .widthIn(max = AlphaDimens.dialogWidth)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 12.dp.toPx()
            }
            // Swallow taps so they do not reach the scrim.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics {
                isTraversalGroup = true
                if (title != null) paneTitle = title
            }
            .modalPanelFocus(panelFocus),
        level = GlassLevel.Modal,
        shape = AlphaShapes.dialog,
    ) {
        Column(Modifier.padding(PaddingValues(AlphaDimens.dialogPadding)), horizontalAlignment = horizontal) {
            if (onClose != null) {
                // ✕ sits in its own header row (title | ✕), so it can never overlap the content.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (title != null) {
                        DialogTitle(title, centered = false, modifier = Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    GlassIconButton(
                        icon = painterResource(Res.drawable.close_24px),
                        contentDescription = stringResource(Res.string.m0_dialog_close),
                        onClick = onClose,
                        // Optically align the glyph (not the 48 dp touch box) with the content edge.
                        modifier = Modifier.offset(x = 12.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Column(
                // Own focus level: a focus return reaches the element inside the scrolling content.
                modifier = Modifier.weight(1f, fill = false).overlayFocusLevel().verticalScroll(rememberScrollState()),
                horizontalAlignment = horizontal,
            ) {
                if (icon != null) {
                    Image(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        colorFilter = ColorFilter.tint(
                            when {
                                iconTint.isSpecified -> iconTint
                                destructive -> colors.error
                                else -> colors.primary
                            },
                        ),
                    )
                    Spacer(Modifier.height(16.dp))
                }
                if (title != null && onClose == null) {
                    DialogTitle(title, centered = centered, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                }
                content()
            }
            if (confirm != null || dismiss != null) {
                Spacer(Modifier.height(24.dp))
                DialogButtonRow(confirm = confirm, dismiss = dismiss, destructive = destructive)
            }
        }
    }
}

@Composable
private fun DialogTitle(title: String, centered: Boolean, modifier: Modifier) {
    Text(
        title,
        style = AlphaType.Heading,
        color = AlphaTheme.colors.textPrimary,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = modifier.semantics { heading() },
    )
}
