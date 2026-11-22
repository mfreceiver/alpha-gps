package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.arrow_back_24px
import cameragps.sharednew.generated.resources.back
import cameragps.sharednew.generated.resources.cancel_button
import cameragps.sharednew.generated.resources.refresh_24px
import cameragps.sharednew.generated.resources.remote_check_camera
import cameragps.sharednew.generated.resources.remote_request_sent
import cameragps.sharednew.generated.resources.remote_shutter_cd
import cameragps.sharednew.generated.resources.remote_wifi_busy
import cameragps.sharednew.generated.resources.remote_wifi_capture_rejected
import cameragps.sharednew.generated.resources.remote_wifi_capture_shooting
import cameragps.sharednew.generated.resources.remote_wifi_download_busy
import cameragps.sharednew.generated.resources.remote_wifi_live_cd
import cameragps.sharednew.generated.resources.remote_wifi_live_paused
import cameragps.sharednew.generated.resources.remote_wifi_live_retry
import cameragps.sharednew.generated.resources.remote_wifi_live_unsupported
import cameragps.sharednew.generated.resources.remote_wifi_live_waiting
import cameragps.sharednew.generated.resources.remote_wifi_switch_confirm
import cameragps.sharednew.generated.resources.remote_wifi_switch_message
import cameragps.sharednew.generated.resources.remote_wifi_switch_title
import cameragps.sharednew.generated.resources.wifi_remote_approval
import cameragps.sharednew.generated.resources.wifi_remote_auto_connect
import cameragps.sharednew.generated.resources.wifi_remote_closing
import cameragps.sharednew.generated.resources.wifi_remote_connect
import cameragps.sharednew.generated.resources.wifi_remote_disconnect
import cameragps.sharednew.generated.resources.wifi_remote_ip
import cameragps.sharednew.generated.resources.wifi_remote_joining
import cameragps.sharednew.generated.resources.wifi_remote_manual
import cameragps.sharednew.generated.resources.wifi_remote_opening
import cameragps.sharednew.generated.resources.wifi_remote_other_camera
import cameragps.sharednew.generated.resources.wifi_remote_permission
import cameragps.sharednew.generated.resources.wifi_remote_preparing
import cameragps.sharednew.generated.resources.wifi_remote_settings
import cameragps.sharednew.generated.resources.wifi_remote_title
import com.sasch.cameragps.sharednew.remote.wifi.WifiCaptureStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiPreviewStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteController
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.WifiSessionPurpose
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassShutterButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTextField
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTopBar
import com.sasch.cameragps.sharednew.ui.components.glass.glass
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import com.sasch.cameragps.sharednew.ui.theme.WindowClass
import com.sasch.cameragps.sharednew.ui.theme.windowClassOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal enum class WifiRemoteNotice { OtherCamera, DownloadBusy, Busy }

/** Whether the live-view overlay should show the neutral "paused" label with its retry action. */
internal fun wifiLiveViewNeedsRetry(state: WifiRemoteState): Boolean =
    state.isControlReady && state.supportsLiveView && (state.liveViewPaused || state.preview == WifiPreviewStatus.Failed)

/** The shutter is usable whenever the control channel is ready; live-view state does not matter. */
internal fun wifiShutterEnabled(state: WifiRemoteState): Boolean =
    state.isControlReady && state.canCaptureStill && state.capture != WifiCaptureStatus.Shooting

/**
 * R2 · Wi-Fi live view + single shot. Builds a [WifiSessionPurpose.RemoteControl] session through
 * [WifiRemoteController.switchPurpose] (a running Browse session is switched only after an in-page
 * confirmation, never over an accepted download), shows the connection overlay while connecting
 * (with a manual-IP fallback page) and then only the live image, the shutter and the single-shot
 * status. No R3 parameter controls live here.
 *
 * [ensureNetworkPermission] runs [proceed] once the platform's network permissions are in place.
 */
@Composable
fun WifiRemoteControlScreen(
    identifier: String,
    controller: WifiRemoteController,
    hapticsEnabled: Boolean,
    ensureNetworkPermission: (host: String?, proceed: () -> Unit) -> Unit,
    onWifiSettings: () -> Unit,
    onClose: () -> Unit,
    onAppSettings: (() -> Unit)? = null,
    networkPermissionMessage: String = stringResource(Res.string.wifi_remote_permission),
) {
    val id = identifier.uppercase()
    val sessions by controller.sessions.collectAsState()
    val owner by controller.owner.collectAsState()
    val liveFrame by controller.liveViewFrame.collectAsState()
    val state = sessions[id]?.wifiRemote ?: WifiRemoteState()
    val scope = rememberCoroutineScope()
    var handled by rememberSaveable(id) { mutableStateOf(false) }
    var ownsRemoteSession by rememberSaveable(id) { mutableStateOf(false) }
    var notice by remember { mutableStateOf<WifiRemoteNotice?>(null) }
    var confirmSwitch by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    var failureDismissed by remember(state.phase) { mutableStateOf(false) }

    fun start(hostArg: String?) {
        notice = null
        ensureNetworkPermission(hostArg) {
            ownsRemoteSession = true
            scope.launch {
                if (!controller.switchPurpose(id, WifiSessionPurpose.RemoteControl, hostArg)) {
                    ownsRemoteSession = false
                    notice = WifiRemoteNotice.Busy
                }
            }
        }
    }

    LaunchedEffect(state.phase, state.purpose, state.imageTransfer.busy, owner) {
        if (handled) return@LaunchedEffect
        when (wifiRemoteEntryAction(state, owner, id)) {
            WifiRemoteEntryAction.Wait -> Unit
            WifiRemoteEntryAction.Start -> {
                handled = true
                if (controller.supportsAutomaticConnection || controller.supportsManualSetup) start(null) else manual = true
            }
            WifiRemoteEntryAction.ConfirmSwitch -> {
                handled = true
                confirmSwitch = true
            }
            WifiRemoteEntryAction.Reuse -> {
                handled = true
                ownsRemoteSession = state.purpose == WifiSessionPurpose.RemoteControl
            }
            WifiRemoteEntryAction.BlockedOtherCamera -> {
                handled = true
                notice = WifiRemoteNotice.OtherCamera
            }
            WifiRemoteEntryAction.BlockedDownload -> {
                handled = true
                notice = WifiRemoteNotice.DownloadBusy
            }
        }
    }

    val close = {
        if (ownsRemoteSession && state.purpose == WifiSessionPurpose.RemoteControl) {
            controller.disconnect(id)
        }
        onClose()
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = if (ownsRemoteSession && !confirmSwitch && state.purpose == WifiSessionPurpose.RemoteControl && state.phase == WifiRemotePhase.Ready) close else onClose,
    )

    val ready = state.phase == WifiRemotePhase.Ready
    val frame = if (ready || !controller.supportsAutomaticConnection) null else wifiConnectionFrame(
        state,
        awaitingAutoConnect = !handled && notice == null,
        catalogShown = true,
    )?.takeUnless { failureDismissed && state.phase == WifiRemotePhase.Failed }

    GlassBackground(contentIsBackdropSource = false) {
        if (confirmSwitch) {
            CapsuleDialog(
                onDismissRequest = onClose,
                title = stringResource(Res.string.remote_wifi_switch_title),
                message = stringResource(Res.string.remote_wifi_switch_message),
                dismiss = DialogAction(stringResource(Res.string.cancel_button), onClose),
                confirm = DialogAction(stringResource(Res.string.remote_wifi_switch_confirm), onClick = {
                    confirmSwitch = false
                    start(null)
                }),
            )
        }
        when {
            confirmSwitch -> Unit
            frame != null -> WifiConnectionOverlay(
                frame = frame,
                networkPermissionMessage = networkPermissionMessage,
                onCancel = close,
                onDismiss = {
                    if (state.phase == WifiRemotePhase.Failed) failureDismissed = true else onClose()
                },
                onAppSettings = onAppSettings,
                onWifiSettings = onWifiSettings,
                modifier = Modifier.systemBarsPadding(),
            )
            ready -> RemoteLiveView(
                state = state,
                frameImage = liveFrame?.image,
                hapticsEnabled = hapticsEnabled,
                onBack = close,
                onShutter = { controller.capture(id) },
                onRetryLiveView = { controller.retryLiveView(id) },
            )
            else -> RemoteConnectControls(
                state = state,
                controller = controller,
                notice = notice,
                host = host,
                onHostChange = { host = it.take(64) },
                manual = manual,
                onToggleManual = { manual = !manual },
                onAutoConnect = { start(null) },
                onManualConnect = { start(host) },
                onWifiSettings = onWifiSettings,
                onContinueManualJoin = { controller.continueManualConnection(id) },
                onDisconnect = { controller.disconnect(id) },
                onBack = if (state.phase == WifiRemotePhase.Idle || state.phase == WifiRemotePhase.Failed) onClose else close,
                networkPermissionMessage = networkPermissionMessage,
            )
        }
    }
}

@Composable
private fun RemoteLiveView(
    state: WifiRemoteState,
    frameImage: androidx.compose.ui.graphics.ImageBitmap?,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onShutter: () -> Unit,
    onRetryLiveView: () -> Unit,
) {
    val colors = AlphaTheme.colors
    val haptics = LocalHapticFeedback.current
    val liveCd = stringResource(Res.string.remote_wifi_live_cd)
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.viewerBackground)) {
        val landscape = windowClassOf(maxWidth.value, maxHeight.value) == WindowClass.Landscape
        if (frameImage != null) {
            Image(
                bitmap = frameImage,
                contentDescription = liveCd,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Top: back + camera name, and the neutral paused label with its one action.
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp).align(Alignment.TopStart),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassIconButton(
                    icon = painterResource(Res.drawable.arrow_back_24px),
                    contentDescription = stringResource(Res.string.back),
                    onClick = onBack,
                    container = GlassLevel.Viewer,
                )
                if (state.cameraName.isNotEmpty()) {
                    Text(state.cameraName, style = AlphaType.Heading, color = colors.onPhoto, maxLines = 1)
                }
            }
            if (wifiLiveViewNeedsRetry(state)) {
                Column(
                    Modifier.align(Alignment.CenterHorizontally),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OnPhotoPill(stringResource(Res.string.remote_wifi_live_paused))
                    CapsuleButton(
                        text = stringResource(Res.string.remote_wifi_live_retry),
                        onClick = onRetryLiveView,
                        role = CapsuleRole.GlassPrimary,
                        glass = true,
                        glassLevel = GlassLevel.Viewer,
                        icon = painterResource(Res.drawable.refresh_24px),
                    )
                }
            }
        }
        // Centre: nothing to show yet / not offered (control and shutter stay independent).
        if (frameImage == null && !wifiLiveViewNeedsRetry(state)) {
            val message = when {
                !state.supportsLiveView -> stringResource(Res.string.remote_wifi_live_unsupported)
                else -> stringResource(Res.string.remote_wifi_live_waiting)
            }
            Box(Modifier.align(Alignment.Center)) { OnPhotoPill(message) }
        }
        // Bottom (or right in landscape): single-shot status above the shutter.
        Column(
            Modifier
                .align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaptureStatusText(state.capture)
            GlassShutterButton(
                onClick = {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onShutter()
                },
                contentDescription = stringResource(Res.string.remote_shutter_cd),
                enabled = wifiShutterEnabled(state) || state.capture == WifiCaptureStatus.Shooting,
                pressing = state.capture == WifiCaptureStatus.Shooting,
                onPhoto = true,
            )
        }
    }
}

@Composable
private fun OnPhotoPill(text: String) {
    Box(Modifier.glass(GlassLevel.Viewer, AlphaShapes.capsule).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(text, style = AlphaType.Caption, color = AlphaTheme.colors.onPhoto, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CaptureStatusText(status: WifiCaptureStatus) {
    val colors = AlphaTheme.colors
    val (text, color) = when (status) {
        WifiCaptureStatus.Idle -> return
        WifiCaptureStatus.Shooting -> stringResource(Res.string.remote_wifi_capture_shooting) to colors.onPhotoSecondary
        WifiCaptureStatus.Captured -> stringResource(Res.string.remote_request_sent) to colors.onPhoto
        WifiCaptureStatus.Uncertain -> stringResource(Res.string.remote_check_camera) to colors.onPhotoWarning
        WifiCaptureStatus.Rejected -> stringResource(Res.string.remote_wifi_capture_rejected) to colors.onPhotoDanger
    }
    Text(
        text,
        style = AlphaType.Label,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * Connection page outside the overlay: another camera / a download blocks the switch, the automatic
 * path failed and was dismissed (retry), or the manual-IP fallback (also the manual network join).
 */
@Composable
private fun RemoteConnectControls(
    state: WifiRemoteState,
    controller: WifiRemoteController,
    notice: WifiRemoteNotice?,
    host: String,
    onHostChange: (String) -> Unit,
    manual: Boolean,
    onToggleManual: () -> Unit,
    onAutoConnect: () -> Unit,
    onManualConnect: () -> Unit,
    onWifiSettings: () -> Unit,
    onContinueManualJoin: () -> Unit,
    onDisconnect: () -> Unit,
    onBack: () -> Unit,
    networkPermissionMessage: String,
) {
    val colors = AlphaTheme.colors
    val body = AlphaType.Body
    val busy = state.phase != WifiRemotePhase.Idle && state.phase != WifiRemotePhase.Failed
    val blocked = notice == WifiRemoteNotice.OtherCamera || notice == WifiRemoteNotice.DownloadBusy
    val scroll = rememberScrollState()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = stringResource(Res.string.wifi_remote_title),
                navigationIcon = {
                    GlassIconButton(
                        painterResource(Res.drawable.arrow_back_24px),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (notice) {
                WifiRemoteNotice.OtherCamera -> Text(stringResource(Res.string.wifi_remote_other_camera), style = body, color = colors.warningText)
                WifiRemoteNotice.DownloadBusy -> Text(stringResource(Res.string.remote_wifi_download_busy), style = body, color = colors.warningText)
                WifiRemoteNotice.Busy -> Text(stringResource(Res.string.remote_wifi_busy), style = body, color = colors.warningText)
                null -> Unit
            }
            state.failure?.let { failure ->
                Text(wifiFailureMessage(failure, networkPermissionMessage), style = body, color = colors.danger)
            }
            if (!busy && !blocked) {
                if (controller.supportsAutomaticConnection || controller.supportsManualSetup) {
                    CapsuleButton(
                        text = stringResource(Res.string.wifi_remote_auto_connect),
                        onClick = onAutoConnect,
                    )
                    CapsuleButton(
                        text = stringResource(Res.string.wifi_remote_manual),
                        onClick = onToggleManual,
                        role = CapsuleRole.Secondary,
                    )
                }
                if (manual || (!controller.supportsAutomaticConnection && !controller.supportsManualSetup)) {
                    CapsuleButton(
                        text = stringResource(Res.string.wifi_remote_settings),
                        onClick = onWifiSettings,
                        role = CapsuleRole.Secondary,
                    )
                    GlassTextField(
                        value = host,
                        onValueChange = onHostChange,
                        label = stringResource(Res.string.wifi_remote_ip),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CapsuleButton(
                        text = stringResource(Res.string.wifi_remote_connect),
                        onClick = onManualConnect,
                        enabled = controller.supportsAddressDiscovery || host.isNotBlank(),
                    )
                }
            }
            val progress = when (state.phase) {
                WifiRemotePhase.PreparingCamera -> Res.string.wifi_remote_preparing
                WifiRemotePhase.OpeningSession -> Res.string.wifi_remote_opening
                WifiRemotePhase.AwaitingNetworkApproval -> Res.string.wifi_remote_approval
                WifiRemotePhase.JoiningNetwork -> Res.string.wifi_remote_joining
                WifiRemotePhase.Closing -> Res.string.wifi_remote_closing
                else -> null
            }
            if (progress != null) Text(stringResource(progress), style = body, color = colors.textPrimary)
            state.manualNetwork?.let { credentials ->
                WifiManualJoin(credentials, onWifiSettings, onContinue = onContinueManualJoin)
            }
            if (busy) {
                CapsuleButton(
                    text = stringResource(Res.string.wifi_remote_disconnect),
                    onClick = onDisconnect,
                    role = CapsuleRole.Secondary,
                    enabled = state.phase != WifiRemotePhase.Closing,
                )
            }
        }
    }
}
