package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.arrow_back_24px
import cameragps.sharednew.generated.resources.back
import cameragps.sharednew.generated.resources.cancel_button
import cameragps.sharednew.generated.resources.continue_button
import cameragps.sharednew.generated.resources.q_alert_open_guide
import cameragps.sharednew.generated.resources.remote_ble_dialog_title
import cameragps.sharednew.generated.resources.remote_ble_status_idle
import cameragps.sharednew.generated.resources.remote_ble_status_pressing
import cameragps.sharednew.generated.resources.remote_ble_status_preparing
import cameragps.sharednew.generated.resources.remote_ble_status_ready
import cameragps.sharednew.generated.resources.remote_ble_status_releasing
import cameragps.sharednew.generated.resources.remote_ble_title
import cameragps.sharednew.generated.resources.remote_check_camera
import cameragps.sharednew.generated.resources.remote_request_sent
import cameragps.sharednew.generated.resources.remote_retry
import cameragps.sharednew.generated.resources.remote_shutter_cd
import com.sasch.cameragps.sharednew.bluetooth.coordinator.BleShutterPhase
import com.sasch.cameragps.sharednew.bluetooth.coordinator.BleShutterState
import com.sasch.cameragps.sharednew.bluetooth.coordinator.GpsRecoveryBaseline
import com.sasch.cameragps.sharednew.bluetooth.coordinator.assessBleLocationCompatibility
import com.sasch.cameragps.sharednew.bluetooth.coordinator.captureGpsRecoveryBaseline
import com.sasch.cameragps.sharednew.bluetooth.status.CameraLiveStatus
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleSize
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassShutterButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTopBar
import com.sasch.cameragps.sharednew.ui.help.GuideTopic
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Plain flag holder: leaving must be idempotent without writing Compose state while disposing. */
private class LeaveGuard {
    var left = false
    var hadPriorWrite = false
}

/**
 * R1 · Bluetooth minimal shutter. Entering asks (in-page [CapsuleDialog]) whether location linking
 * may be paused — the text depends on [assessBleLocationCompatibility] — and only the confirmation
 * calls [BleShutterControls.acquire]. The page is one [GlassShutterButton] plus one status line;
 * a failure adds Retry and a link to the Remote troubleshooting topic.
 *
 * Leaving (Back or disposal) releases the remote and hands [onLeft] a [GpsRecoveryBaseline] so the
 * device page can watch location linking recover. If the dialog was cancelled nothing was acquired
 * and nothing is released.
 */
@Composable
fun BleShutterScreen(
    identifier: String,
    deviceName: String?,
    isCustomName: Boolean,
    controls: BleShutterControls,
    live: CameraLiveStatus?,
    hapticsEnabled: Boolean,
    onClose: () -> Unit,
    onLeft: (GpsRecoveryBaseline) -> Unit,
    onOpenGuide: ((GuideTopic) -> Unit)? = null,
) {
    val id = identifier.uppercase()
    val compatibility = remember(deviceName, isCustomName) { assessBleLocationCompatibility(deviceName, isCustomName) }
    val states by controls.states.collectAsState()
    val state = states[id] ?: BleShutterState()
    var confirmed by remember { mutableStateOf(false) }
    val guard = remember { LeaveGuard() }
    val liveNow by rememberUpdatedState(live)
    val controlsNow by rememberUpdatedState(controls)
    val onLeftNow by rememberUpdatedState(onLeft)
    val haptics = LocalHapticFeedback.current

    // "Request sent" is the settled result of a press: Ready again right after Pressing/Releasing.
    var requestSent by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf(state.phase) }
    LaunchedEffect(state.phase) {
        val was = previous
        previous = state.phase
        requestSent = when (state.phase) {
            BleShutterPhase.Ready -> requestSent || was == BleShutterPhase.Pressing || was == BleShutterPhase.Releasing
            else -> false
        }
    }

    val leave = {
        if (confirmed && !guard.left) {
            guard.left = true
            controlsNow.releaseAsync(id)
            onLeftNow(captureGpsRecoveryBaseline(guard.hadPriorWrite, liveNow))
        }
    }
    val leaveNow by rememberUpdatedState(leave)
    DisposableEffect(Unit) { onDispose { leaveNow() } }
    val back = {
        leave()
        onClose()
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = back,
    )

    GlassBackground(contentIsBackdropSource = false) {
        if (!confirmed) {
            CapsuleDialog(
                onDismissRequest = onClose,
                title = stringResource(Res.string.remote_ble_dialog_title),
                message = stringResource(bleCompatibilityMessage(compatibility)),
                dismiss = DialogAction(stringResource(Res.string.cancel_button), onClose),
                confirm = DialogAction(stringResource(Res.string.continue_button), onClick = {
                    guard.hadPriorWrite = (liveNow?.gps?.writtenCount ?: 0) > 0 || liveNow?.lastWriteAtMillis != null
                    guard.left = false
                    confirmed = true
                    controls.acquire(id)
                }),
            )
        }
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopBar(
                    title = stringResource(Res.string.remote_ble_title),
                    navigationIcon = {
                        GlassIconButton(
                            icon = painterResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.back),
                            onClick = back,
                        )
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.padding(horizontal = AlphaDimens.screenMargin),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    GlassShutterButton(
                        onClick = {
                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            controls.trigger(id)
                        },
                        contentDescription = stringResource(Res.string.remote_shutter_cd),
                        enabled = confirmed && state.canTrigger,
                        pressing = bleShutterPressing(state),
                    )
                    BleStatusText(state, requestSent)
                    if (confirmed && state.phase == BleShutterPhase.Failed) {
                        CapsuleButton(
                            text = stringResource(Res.string.remote_retry),
                            onClick = { controls.acquire(id) },
                            role = CapsuleRole.Tonal,
                        )
                        if (onOpenGuide != null) {
                            CapsuleButton(
                                text = stringResource(Res.string.q_alert_open_guide),
                                onClick = { onOpenGuide(GuideTopic.Remote) },
                                role = CapsuleRole.Secondary,
                                size = CapsuleSize.Small,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BleStatusText(state: BleShutterState, requestSent: Boolean) {
    val colors = AlphaTheme.colors
    val line = bleShutterStatusLine(state, requestSent)
    val text = when (line) {
        BleShutterStatusLine.Idle -> stringResource(Res.string.remote_ble_status_idle)
        BleShutterStatusLine.Preparing -> stringResource(Res.string.remote_ble_status_preparing)
        BleShutterStatusLine.Ready -> stringResource(Res.string.remote_ble_status_ready)
        BleShutterStatusLine.RequestSent -> stringResource(Res.string.remote_request_sent)
        BleShutterStatusLine.Pressing -> stringResource(Res.string.remote_ble_status_pressing)
        BleShutterStatusLine.Releasing -> stringResource(Res.string.remote_ble_status_releasing)
        BleShutterStatusLine.Uncertain -> stringResource(Res.string.remote_check_camera)
        BleShutterStatusLine.Failed -> stringResource(bleShutterFailureText(state.failureReason))
    }
    val color = when (line) {
        BleShutterStatusLine.Failed -> colors.danger
        BleShutterStatusLine.Uncertain -> colors.warningText
        BleShutterStatusLine.RequestSent -> colors.success
        else -> colors.textSecondary
    }
    Text(
        text,
        style = AlphaType.Body,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
