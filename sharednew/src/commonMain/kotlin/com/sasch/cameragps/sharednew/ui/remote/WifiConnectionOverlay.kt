package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.camera_24px
import cameragps.sharednew.generated.resources.cancel
import cameragps.sharednew.generated.resources.check_24px
import cameragps.sharednew.generated.resources.mw_connect_closing
import cameragps.sharednew.generated.resources.mw_connect_closing_hint
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
import com.sasch.cameragps.sharednew.ui.components.glass.glass
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val CardWidth = 310.dp
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
 * Full-screen connection overlay (design v3, connection frames): one fixed-size glass card with
 * three fixed slots — icon + step ring, title + two-line hint, one action — so nothing jumps between
 * frames. No dividers, no help or retry: a failure is left by tapping anywhere, Back or after
 * [CONNECTION_FAILURE_AUTO_DISMISS_MS]; only a missing permission offers "grant" (and waits).
 *
 * [onCancel] ends the attempt (cancel button while connecting); [onDismiss] leaves a finished
 * (failed / unsupported) attempt.
 */
@Composable
internal fun WifiConnectionOverlay(
    frame: WifiConnectionFrame,
    networkPermissionMessage: String,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onAppSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val ended = frame is WifiConnectionFrame.Failed || frame is WifiConnectionFrame.Unsupported
    val permission = frame.needsPermissionAction() && onAppSettings != null
    if (ended && !permission) {
        LaunchedEffect(frame) {
            delay(CONNECTION_FAILURE_AUTO_DISMISS_MS)
            onDismiss()
        }
    }
    val dismissModifier = if (ended) Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss
    ) else Modifier
    Box(modifier.fillMaxSize().then(dismissModifier), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(CardWidth).height(CardHeight)
                .glass(GlassLevel.Modal, AlphaShapes.dialog)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(IconSlot), contentAlignment = Alignment.Center) {
                FrameIcon(frame)
            }
            val (title, hint) = frameTexts(frame, networkPermissionMessage)
            Column(
                Modifier.fillMaxWidth().height(TextSlot)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    title, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    color = if (ended) AlphaTheme.colors.error else AlphaTheme.colors.onSurface,
                )
                Text(
                    hint, style = MaterialTheme.typography.bodyMedium, minLines = 2, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    color = AlphaTheme.colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(ActionSlot), contentAlignment = Alignment.Center) {
                FrameAction(frame, permission, onCancel, onAppSettings)
            }
        }
    }
}

@Composable
private fun frameTexts(frame: WifiConnectionFrame, networkPermissionMessage: String): Pair<String, String> =
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
            stringResource(Res.string.mw_connect_ready) to stringResource(Res.string.mw_connect_ready_hint)
        WifiConnectionFrame.Closing ->
            stringResource(Res.string.mw_connect_closing) to stringResource(Res.string.mw_connect_closing_hint)
        WifiConnectionFrame.Unsupported ->
            stringResource(Res.string.mw_connect_unsupported) to stringResource(Res.string.wifi_remote_browse_unsupported)
        is WifiConnectionFrame.Failed ->
            stringResource(Res.string.mw_connect_failed) to wifiFailureMessage(frame.failure, networkPermissionMessage)
    }

@Composable
private fun FrameIcon(frame: WifiConnectionFrame) {
    val colors = AlphaTheme.colors
    when (frame) {
        is WifiConnectionFrame.Failed, WifiConnectionFrame.Unsupported -> Icon(
            painterResource(Res.drawable.mw_error_24px), contentDescription = null,
            tint = colors.error, modifier = Modifier.size(FailureIcon)
        )
        WifiConnectionFrame.Closing -> {
            SpinningRing(colors.outline)
            Icon(
                painterResource(Res.drawable.mw_wifi_off_24px), null,
                tint = colors.onSurfaceVariant, modifier = Modifier.size(StepIcon)
            )
        }
        WifiConnectionFrame.Ready -> {
            StepRing(done = WifiConnectStep.entries.size, current = null, doneColor = colors.success, todo = colors.outlineVariant)
            Icon(painterResource(Res.drawable.check_24px), null, tint = colors.success, modifier = Modifier.size(StepIcon))
        }
        is WifiConnectionFrame.Progress -> {
            StepRing(done = frame.step.ordinal, current = frame.step.ordinal, doneColor = colors.primary, todo = colors.outlineVariant)
            Icon(stepIcon(frame.step), null, tint = colors.primary, modifier = Modifier.size(StepIcon))
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
private fun StepRing(done: Int, current: Int?, doneColor: Color, todo: Color) {
    val pulse by rememberInfiniteTransition(label = "step").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse"
    )
    val segments = WifiConnectStep.entries.size
    Canvas(Modifier.size(RingRadius * 2 + RingStroke)) {
        val stroke = RingStroke.toPx()
        val diameter = RingRadius.toPx() * 2
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val gap = 10f
        val sweep = 360f / segments
        for (i in 0 until segments) {
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

/** Neutral reverse-spinning ring of the closing frame. */
@Composable
private fun SpinningRing(color: Color) {
    val angle by rememberInfiniteTransition(label = "closing").animateFloat(
        360f, 0f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "angle"
    )
    Canvas(Modifier.size(RingRadius * 2 + RingStroke)) {
        val diameter = RingRadius.toPx() * 2
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        drawArc(
            color, startAngle = angle, sweepAngle = 270f, useCenter = false, topLeft = topLeft,
            size = Size(diameter, diameter), style = Stroke(RingStroke.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun FrameAction(
    frame: WifiConnectionFrame, permission: Boolean, onCancel: () -> Unit, onAppSettings: (() -> Unit)?,
) {
    when (frame) {
        is WifiConnectionFrame.Progress, WifiConnectionFrame.Ready -> {
            // Kept in place (only faded) when ready so the card does not change shape.
            val visible by animateFloatAsState(if (frame is WifiConnectionFrame.Ready) 0f else 1f, tween(150))
            CapsuleButton(
                text = stringResource(Res.string.cancel), onClick = onCancel,
                role = CapsuleRole.Secondary, enabled = frame !is WifiConnectionFrame.Ready,
                modifier = Modifier.fillMaxWidth().alpha(visible),
            )
        }
        is WifiConnectionFrame.Failed, WifiConnectionFrame.Unsupported -> if (permission && onAppSettings != null) {
            CapsuleButton(
                text = stringResource(Res.string.mw_connect_grant), onClick = onAppSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        } else Text(
            stringResource(Res.string.mw_connect_tap_to_return), style = MaterialTheme.typography.bodyMedium,
            color = AlphaTheme.colors.onSurfaceVariant,
        )
        WifiConnectionFrame.Closing -> Unit
    }
}
