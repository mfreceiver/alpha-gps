package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.c1_open_wifi_settings
import cameragps.sharednew.generated.resources.camera_24px
import cameragps.sharednew.generated.resources.cancel
import cameragps.sharednew.generated.resources.check_24px
import cameragps.sharednew.generated.resources.mw_connect_closing
import cameragps.sharednew.generated.resources.mw_connect_closing_hint
import cameragps.sharednew.generated.resources.mw_connect_dismiss
import cameragps.sharednew.generated.resources.mw_connect_failed
import cameragps.sharednew.generated.resources.mw_connect_grant
import cameragps.sharednew.generated.resources.mw_connect_ready
import cameragps.sharednew.generated.resources.mw_connect_ready_hint
import cameragps.sharednew.generated.resources.mw_connect_step_approve
import cameragps.sharednew.generated.resources.mw_connect_step_approve_hint
import cameragps.sharednew.generated.resources.mw_connect_step_catalog
import cameragps.sharednew.generated.resources.mw_connect_step_catalog_hint
import cameragps.sharednew.generated.resources.mw_connect_step_join
import cameragps.sharednew.generated.resources.mw_connect_step_join_hint
import cameragps.sharednew.generated.resources.mw_connect_step_prepare
import cameragps.sharednew.generated.resources.mw_connect_step_prepare_hint
import cameragps.sharednew.generated.resources.mw_connect_tap_to_return
import cameragps.sharednew.generated.resources.mw_connect_unsupported
import cameragps.sharednew.generated.resources.mw_error_24px
import cameragps.sharednew.generated.resources.mw_phonelink_24px
import cameragps.sharednew.generated.resources.mw_photo_library_24px
import cameragps.sharednew.generated.resources.mw_wifi_24px
import cameragps.sharednew.generated.resources.mw_wifi_off_24px
import cameragps.sharednew.generated.resources.remote_mode_wifi_title
import cameragps.sharednew.generated.resources.remote_wifi_live_unsupported
import cameragps.sharednew.generated.resources.remote_wifi_ready_hint
import cameragps.sharednew.generated.resources.wifi_connection_failed
import cameragps.sharednew.generated.resources.wifi_remote_address_failed
import cameragps.sharednew.generated.resources.wifi_remote_bluetooth_required
import cameragps.sharednew.generated.resources.wifi_remote_browse_unsupported
import cameragps.sharednew.generated.resources.wifi_remote_credentials_failed
import cameragps.sharednew.generated.resources.wifi_remote_failed
import cameragps.sharednew.generated.resources.wifi_remote_invalid_ip
import cameragps.sharednew.generated.resources.wifi_remote_join_failed
import cameragps.sharednew.generated.resources.wifi_remote_join_wifi
import cameragps.sharednew.generated.resources.wifi_remote_location_required
import cameragps.sharednew.generated.resources.wifi_remote_lost
import cameragps.sharednew.generated.resources.wifi_remote_setup_failed
import cameragps.sharednew.generated.resources.wifi_remote_wifi_disabled
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteFailure
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressCircle
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSurface
import com.sasch.cameragps.sharednew.ui.components.glass.glassProgressColors
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val CardMaxWidth = 360.dp
private val CardHeight = 320.dp
private val IconSlot = 144.dp
private val TextSlot = 84.dp
private val ActionSlot = 48.dp
private val RingRadius = 46.dp
private val RingStroke = 4.dp
private val StepIcon = 40.dp
private val FailureIcon = 76.dp

/** The message of a failed attempt (the existing per-failure texts). */
@Composable
internal fun wifiFailureMessage(failure: WifiRemoteFailure?, networkPermissionMessage: String): String =
    if (failure == WifiRemoteFailure.NetworkPermissionDenied) networkPermissionMessage
    else stringResource(wifiFailureText(failure))

internal fun wifiFailureText(failure: WifiRemoteFailure?): StringResource = when (failure) {
    WifiRemoteFailure.InvalidAddress -> Res.string.wifi_remote_invalid_ip
    WifiRemoteFailure.JoinCameraWifi -> Res.string.wifi_remote_join_wifi
    WifiRemoteFailure.NetworkConnectionFailed -> Res.string.wifi_connection_failed
    WifiRemoteFailure.NetworkLost -> Res.string.wifi_remote_lost
    WifiRemoteFailure.BluetoothRequired -> Res.string.wifi_remote_bluetooth_required
    WifiRemoteFailure.CameraSetupFailed -> Res.string.wifi_remote_setup_failed
    WifiRemoteFailure.CameraCredentialsUnavailable -> Res.string.wifi_remote_credentials_failed
    WifiRemoteFailure.NetworkJoinFailed -> Res.string.wifi_remote_join_failed
    WifiRemoteFailure.CameraAddressUnavailable -> Res.string.wifi_remote_address_failed
    WifiRemoteFailure.WifiDisabled -> Res.string.wifi_remote_wifi_disabled
    WifiRemoteFailure.LocationServicesRequired -> Res.string.wifi_remote_location_required
    else -> Res.string.wifi_remote_failed
}

/**
 * Full-screen connection overlay with reserved icon, text, and action slots. The remote path has
 * three steps. Photo transfer passes a fourth step for reading the catalog.
 *
 * [onCancel] ends the active attempt. [onDismiss] leaves a failed or unsupported attempt via the
 * background or the existing timeout. A missing permission or disabled Wi-Fi with a settings
 * action waits for input.
 */
@Composable
internal fun WifiConnectionOverlay(
    frame: WifiConnectionFrame,
    networkPermissionMessage: String,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onAppSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onWifiSettings: (() -> Unit)? = null,
    totalSteps: Int = WifiConnectStep.ReadCatalog.ordinal,
    fallbackActionLabel: String? = null,
    onFallbackAction: (() -> Unit)? = null,
) {
    val ended = frame is WifiConnectionFrame.Failed || frame is WifiConnectionFrame.Unsupported
    val permission = frame.needsPermissionAction() && onAppSettings != null
    val wifiSettingsAction = frame is WifiConnectionFrame.Failed &&
        frame.failure == WifiRemoteFailure.WifiDisabled && onWifiSettings != null
    val hasFallback = ended && fallbackActionLabel != null && onFallbackAction != null
    val waitsForSettings = permission || wifiSettingsAction || hasFallback
    val readsCatalog = totalSteps > WifiConnectStep.ReadCatalog.ordinal
    val presentedFrame = if (!readsCatalog && frame is WifiConnectionFrame.Progress && frame.step == WifiConnectStep.ReadCatalog) {
        WifiConnectionFrame.Progress(WifiConnectStep.JoinCamera)
    } else frame
    val (title, hint) = frameTexts(presentedFrame, networkPermissionMessage, readsCatalog)
    val accessibilityEnabled = rememberAccessibilityEnabled()
    val autoDismissMs = connectionFailureAutoDismissMs(hint.length, accessibilityEnabled)
    val dismissNow by rememberUpdatedState(onDismiss)
    if (ended && !waitsForSettings && autoDismissMs != null) {
        LaunchedEffect(frame, autoDismissMs) {
            delay(autoDismissMs)
            dismissNow()
        }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (ended) {
            Box(
                Modifier.matchParentSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(Res.string.mw_connect_dismiss),
                    onClick = onDismiss,
                ),
            )
        }
        Box(Modifier.padding(horizontal = AlphaDimens.screenMargin, vertical = 8.dp)) {
            GlassSurface(
                modifier = Modifier.widthIn(max = CardMaxWidth).fillMaxWidth(),
                level = GlassLevel.Modal,
                shape = AlphaShapes.dialog,
            ) {
                // The scroll viewport covers the card, including its padding, and owns hit-testing
                // there. The sibling behind it can only receive taps outside the card.
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .heightIn(min = CardHeight)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(IconSlot), contentAlignment = Alignment.Center) {
                        FrameIcon(presentedFrame, totalSteps)
                    }
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = TextSlot)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            title, style = AlphaType.Heading, textAlign = TextAlign.Center,
                            color = if (ended) AlphaTheme.colors.danger else AlphaTheme.colors.textPrimary,
                        )
                        Text(
                            hint, style = AlphaType.Body, textAlign = TextAlign.Center,
                            color = AlphaTheme.colors.textSecondary,
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = ActionSlot),
                        contentAlignment = Alignment.Center,
                    ) {
                        FrameAction(
                            frame = frame,
                            permission = permission,
                            onCancel = onCancel,
                            onDismiss = onDismiss,
                            onAppSettings = onAppSettings,
                            onWifiSettings = onWifiSettings,
                            fallbackActionLabel = fallbackActionLabel,
                            onFallbackAction = onFallbackAction,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun frameTexts(
    frame: WifiConnectionFrame,
    networkPermissionMessage: String,
    readsCatalog: Boolean,
): Pair<String, String> =
    when (frame) {
        is WifiConnectionFrame.Progress -> when (frame.step) {
            WifiConnectStep.PrepareCamera ->
                stringResource(Res.string.mw_connect_step_prepare) to stringResource(Res.string.mw_connect_step_prepare_hint)
            WifiConnectStep.ApproveNetwork ->
                stringResource(Res.string.mw_connect_step_approve) to stringResource(Res.string.mw_connect_step_approve_hint)
            WifiConnectStep.JoinCamera ->
                stringResource(Res.string.mw_connect_step_join) to stringResource(Res.string.mw_connect_step_join_hint)
            WifiConnectStep.ReadCatalog ->
                stringResource(Res.string.mw_connect_step_catalog) to stringResource(Res.string.mw_connect_step_catalog_hint)
        }
        WifiConnectionFrame.Ready ->
            stringResource(Res.string.mw_connect_ready) to stringResource(
                if (readsCatalog) Res.string.mw_connect_ready_hint
                else Res.string.remote_wifi_ready_hint,
            )
        WifiConnectionFrame.Closing ->
            stringResource(Res.string.mw_connect_closing) to stringResource(Res.string.mw_connect_closing_hint)
        WifiConnectionFrame.Unsupported -> if (readsCatalog) {
            stringResource(Res.string.mw_connect_unsupported) to stringResource(Res.string.wifi_remote_browse_unsupported)
        } else {
            stringResource(Res.string.remote_mode_wifi_title) to stringResource(Res.string.remote_wifi_live_unsupported)
        }
        is WifiConnectionFrame.Failed ->
            stringResource(Res.string.mw_connect_failed) to wifiFailureMessage(frame.failure, networkPermissionMessage)
    }

@Composable
private fun FrameIcon(frame: WifiConnectionFrame, totalSteps: Int) {
    val colors = AlphaTheme.colors
    // Remaining segments use the GlassProgress track so the ring reads as the same progress family.
    val todo = glassProgressColors(colors, onPhoto = false).first
    when (frame) {
        is WifiConnectionFrame.Failed, WifiConnectionFrame.Unsupported -> Icon(
            painterResource(Res.drawable.mw_error_24px), contentDescription = null,
            tint = colors.danger, modifier = Modifier.size(FailureIcon)
        )
        WifiConnectionFrame.Closing -> {
            // Neutral indeterminate ring at the step ring's size (N8 GlassProgressCircle).
            GlassProgressCircle(
                Modifier.size(RingRadius * 2 + RingStroke), color = colors.textMuted, strokeWidth = RingStroke,
            )
            Icon(
                painterResource(Res.drawable.mw_wifi_off_24px), null,
                tint = colors.textSecondary, modifier = Modifier.size(StepIcon)
            )
        }
        WifiConnectionFrame.Ready -> {
            StepRing(done = totalSteps, current = null, doneColor = colors.success, todo = todo, totalSteps = totalSteps)
            Icon(painterResource(Res.drawable.check_24px), null, tint = colors.success, modifier = Modifier.size(StepIcon))
        }
        is WifiConnectionFrame.Progress -> {
            StepRing(
                done = frame.step.ordinal, current = frame.step.ordinal, doneColor = colors.accent,
                todo = todo, totalSteps = totalSteps,
            )
            Icon(stepIcon(frame.step), null, tint = colors.accent, modifier = Modifier.size(StepIcon))
        }
    }
}

@Composable
private fun stepIcon(step: WifiConnectStep): Painter = painterResource(
    when (step) {
        WifiConnectStep.PrepareCamera -> Res.drawable.camera_24px
        WifiConnectStep.ApproveNetwork -> Res.drawable.mw_phonelink_24px
        WifiConnectStep.JoinCamera -> Res.drawable.mw_wifi_24px
        WifiConnectStep.ReadCatalog -> Res.drawable.mw_photo_library_24px
    }
)

/** Segmented ring: [done] segments solid, the [current] one pulsing, the rest light grey. */
@Composable
private fun StepRing(
    done: Int, current: Int?, doneColor: Color, todo: Color, totalSteps: Int = WifiConnectStep.entries.size,
) {
    val pulse by rememberInfiniteTransition(label = "step").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse"
    )
    Canvas(Modifier.size(RingRadius * 2 + RingStroke)) {
        val stroke = RingStroke.toPx()
        val diameter = RingRadius.toPx() * 2
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val gap = 10f
        val sweep = 360f / totalSteps
        for (i in 0 until totalSteps) {
            val color = when {
                i == current -> doneColor.copy(alpha = pulse)
                i < done -> doneColor
                else -> todo
            }
            drawArc(
                color, startAngle = -90f + i * sweep + gap / 2, sweepAngle = sweep - gap, useCenter = false,
                topLeft = topLeft, size = Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun FrameAction(
    frame: WifiConnectionFrame,
    permission: Boolean,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onAppSettings: (() -> Unit)?,
    onWifiSettings: (() -> Unit)?,
    fallbackActionLabel: String?,
    onFallbackAction: (() -> Unit)?,
) {
    when (frame) {
        is WifiConnectionFrame.Progress, WifiConnectionFrame.Ready -> {
            // Kept in place (only faded) when ready so the card does not change shape.
            val ready = frame == WifiConnectionFrame.Ready
            val visible by animateFloatAsState(if (ready) 0f else 1f, tween(150))
            val hiddenSemantics = if (ready) Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier
            Box(hiddenSemantics) {
                CapsuleButton(
                    text = stringResource(Res.string.cancel), onClick = onCancel,
                    role = CapsuleRole.Secondary, enabled = !ready,
                    modifier = Modifier.fillMaxWidth().alpha(visible),
                )
            }
        }
        is WifiConnectionFrame.Failed, WifiConnectionFrame.Unsupported -> {
            val hasFallback = fallbackActionLabel != null && onFallbackAction != null
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    permission && onAppSettings != null -> CapsuleButton(
                        text = stringResource(Res.string.mw_connect_grant), onClick = onAppSettings,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    frame is WifiConnectionFrame.Failed && frame.failure == WifiRemoteFailure.WifiDisabled &&
                        onWifiSettings != null -> CapsuleButton(
                        text = stringResource(Res.string.c1_open_wifi_settings), onClick = onWifiSettings,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    !hasFallback -> Text(
                        stringResource(Res.string.mw_connect_tap_to_return), style = AlphaType.Body,
                        textAlign = TextAlign.Center,
                        color = AlphaTheme.colors.textSecondary,
                    )
                }
                if (hasFallback) {
                    CapsuleButton(
                        text = fallbackActionLabel,
                        onClick = onFallbackAction,
                        role = CapsuleRole.Tonal,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CapsuleButton(
                        text = stringResource(Res.string.mw_connect_dismiss),
                        onClick = onDismiss,
                        role = CapsuleRole.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        WifiConnectionFrame.Closing -> Unit
    }
}
