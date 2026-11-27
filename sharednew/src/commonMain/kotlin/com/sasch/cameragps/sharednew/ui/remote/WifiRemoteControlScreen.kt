package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.arrow_back_24px
import cameragps.sharednew.generated.resources.back
import cameragps.sharednew.generated.resources.cancel_button
import cameragps.sharednew.generated.resources.refresh_24px
import cameragps.sharednew.generated.resources.remote_mode_wifi_desc
import cameragps.sharednew.generated.resources.remote_mode_wifi_title
import cameragps.sharednew.generated.resources.remote_shutter_cd
import cameragps.sharednew.generated.resources.remote_wifi_busy
import cameragps.sharednew.generated.resources.remote_probe
import cameragps.sharednew.generated.resources.remote_probe_reading
import cameragps.sharednew.generated.resources.remote_probe_recorded
import cameragps.sharednew.generated.resources.remote_probe_unsupported
import cameragps.sharednew.generated.resources.remote_probe_failed
import cameragps.sharednew.generated.resources.baseline_view_list_24
import cameragps.sharednew.generated.resources.remote_wifi_capture_confirmed
import cameragps.sharednew.generated.resources.remote_wifi_capture_rejected
import cameragps.sharednew.generated.resources.remote_wifi_capture_uncertain
import cameragps.sharednew.generated.resources.remote_wifi_capture_unsupported
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
import cameragps.sharednew.generated.resources.wifi_remote_blocked_heading
import cameragps.sharednew.generated.resources.wifi_remote_auto_connect
import cameragps.sharednew.generated.resources.wifi_remote_closing
import cameragps.sharednew.generated.resources.wifi_remote_connect
import cameragps.sharednew.generated.resources.wifi_remote_disconnect
import cameragps.sharednew.generated.resources.wifi_remote_ip
import cameragps.sharednew.generated.resources.wifi_remote_joining
import cameragps.sharednew.generated.resources.wifi_remote_lost
import cameragps.sharednew.generated.resources.wifi_remote_manual
import cameragps.sharednew.generated.resources.wifi_remote_opening
import cameragps.sharednew.generated.resources.wifi_remote_other_camera
import cameragps.sharednew.generated.resources.wifi_remote_permission
import cameragps.sharednew.generated.resources.wifi_remote_preparing
import cameragps.sharednew.generated.resources.wifi_remote_settings
import com.sasch.cameragps.sharednew.remote.wifi.PropertyProbeStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiCaptureStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiLiveViewFrame
import com.sasch.cameragps.sharednew.remote.wifi.WifiPreviewStatus
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteController
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.WifiSessionPurpose
import com.sasch.cameragps.sharednew.ui.components.ScreenContent
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressCircle
import com.sasch.cameragps.sharednew.ui.components.glass.GlassProgressSize
import com.sasch.cameragps.sharednew.ui.components.glass.GlassShutterButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTopBar
import com.sasch.cameragps.sharednew.ui.components.glass.GlassWarningCard
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTag
import com.sasch.cameragps.sharednew.ui.components.glass.InfoTagStyle
import com.sasch.cameragps.sharednew.ui.components.glass.overlayFocusLevel
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import com.sasch.cameragps.sharednew.ui.theme.LocalGlassRenderMode
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
    val showLiveView = ready && state.purpose == WifiSessionPurpose.RemoteControl && !confirmSwitch
    val frame = if (ready || confirmSwitch || !controller.supportsAutomaticConnection) null else wifiConnectionFrame(
        state,
        awaitingAutoConnect = !handled && notice == null,
        catalogShown = true,
    )?.takeUnless { failureDismissed && state.phase == WifiRemotePhase.Failed }

    GlassBackground(contentIsBackdropSource = !showLiveView) {
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
            frame != null -> WifiConnectionOverlay(
                frame = frame,
                networkPermissionMessage = networkPermissionMessage,
                onCancel = close,
                onDismiss = {
                    if (state.phase == WifiRemotePhase.Failed) failureDismissed = true else onClose()
                },
                onAppSettings = onAppSettings,
                onWifiSettings = onWifiSettings,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
            showLiveView -> RemoteLiveView(
                state = state,
                frame = controller.liveViewFrame.collectAsState(),
                hapticsEnabled = hapticsEnabled,
                onBack = close,
                onShutter = { controller.capture(id) },
                onRetryLiveView = { controller.retryLiveView(id) },
                onProbe = { controller.probeProperties(id) },
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
                onAppSettings = onAppSettings,
            )
        }
    }
}

@Composable
internal fun RemoteLiveView(
    state: WifiRemoteState,
    frame: State<WifiLiveViewFrame?>,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onShutter: () -> Unit,
    onRetryLiveView: () -> Unit,
    onProbe: (() -> Unit)? = null,
) {
    CameraGpsTheme(darkTheme = true, glassRenderMode = LocalGlassRenderMode.current) {
        ViewerStage(backdrop = null) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val landscape = windowClassOf(maxWidth.value, maxHeight.value) == WindowClass.Landscape
                val landscapeControlsWidth = minOf(232.dp, maxWidth * 0.4f)
                RemoteLiveImage(frame)
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RemoteLiveTopBar(state, onBack, onProbe, Modifier.align(Alignment.Start))
                    if (landscape) {
                        Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RemoteLiveStatus(state, frame, onRetryLiveView, Modifier.weight(1f))
                            RemoteCaptureControls(
                                state, hapticsEnabled, onShutter,
                                Modifier.width(landscapeControlsWidth),
                            )
                        }
                    } else {
                        RemoteLiveStatus(state, frame, onRetryLiveView, Modifier.weight(1f).fillMaxWidth())
                        RemoteCaptureControls(
                            state, hapticsEnabled, onShutter,
                            Modifier.widthIn(max = 320.dp).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteLiveImage(frame: State<WifiLiveViewFrame?>) {
    frame.value?.let {
        Image(
            bitmap = it.image,
            contentDescription = stringResource(Res.string.remote_wifi_live_cd),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun RemoteLiveTopBar(state: WifiRemoteState, onBack: () -> Unit, onProbe: (() -> Unit)?, modifier: Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            icon = painterResource(Res.drawable.arrow_back_24px),
            contentDescription = stringResource(Res.string.back),
            onClick = onBack,
            container = GlassLevel.Viewer,
        )
        if (onProbe != null) GlassIconButton(
            icon = painterResource(Res.drawable.baseline_view_list_24),
            contentDescription = stringResource(Res.string.remote_probe),
            enabled = state.isControlReady && !state.photoBrowser.open && state.capture != WifiCaptureStatus.Shooting &&
                state.propertyProbe != PropertyProbeStatus.Reading,
            onClick = onProbe,
            container = GlassLevel.Viewer,
        )
        val label = when (state.propertyProbe) {
            PropertyProbeStatus.Idle -> state.cameraName
            PropertyProbeStatus.Reading -> stringResource(Res.string.remote_probe_reading)
            PropertyProbeStatus.Recorded -> stringResource(Res.string.remote_probe_recorded, state.probeAttempt)
            PropertyProbeStatus.Unsupported -> stringResource(Res.string.remote_probe_unsupported)
            PropertyProbeStatus.Failed -> stringResource(Res.string.remote_probe_failed)
        }
        if (label.isNotEmpty()) {
            Text(
                label,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.widthIn(max = 320.dp).viewerChromeScrim().padding(horizontal = 12.dp, vertical = 8.dp),
                style = AlphaType.Heading,
                color = AlphaTheme.colors.onPhoto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RemoteLiveStatus(
    state: WifiRemoteState,
    frame: State<WifiLiveViewFrame?>,
    onRetryLiveView: () -> Unit,
    modifier: Modifier,
) {
    val hasFrame by remember(frame) { derivedStateOf { frame.value != null } }
    val paused = wifiLiveViewNeedsRetry(state)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (paused || !hasFrame) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.supportsLiveView && !paused) {
                    GlassProgressCircle(onPhoto = true, size = GlassProgressSize.Small)
                }
                val status = when {
                    paused -> Res.string.remote_wifi_live_paused
                    !state.supportsLiveView -> Res.string.remote_wifi_live_unsupported
                    else -> Res.string.remote_wifi_live_waiting
                }
                InfoTag(stringResource(status), style = InfoTagStyle.OnPhoto, maxLines = 3)
                if (paused) {
                    CapsuleButton(
                        text = stringResource(Res.string.remote_wifi_live_retry),
                        onClick = onRetryLiveView,
                        role = CapsuleRole.GlassPrimary,
                        glassLevel = GlassLevel.Viewer,
                        icon = painterResource(Res.drawable.refresh_24px),
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteCaptureControls(
    state: WifiRemoteState,
    hapticsEnabled: Boolean,
    onShutter: () -> Unit,
    modifier: Modifier,
) {
    val colors = AlphaTheme.colors
    val haptics = LocalHapticFeedback.current
    val shooting = state.capture == WifiCaptureStatus.Shooting
    val canShoot = wifiShutterEnabled(state)
    val text = when {
        shooting -> stringResource(Res.string.remote_wifi_capture_shooting)
        !state.isControlReady -> stringResource(Res.string.wifi_remote_lost)
        !state.canCaptureStill -> stringResource(Res.string.remote_wifi_capture_unsupported)
        state.capture == WifiCaptureStatus.Captured -> stringResource(Res.string.remote_wifi_capture_confirmed)
        state.capture == WifiCaptureStatus.Uncertain -> stringResource(Res.string.remote_wifi_capture_uncertain)
        state.capture == WifiCaptureStatus.Rejected -> stringResource(Res.string.remote_wifi_capture_rejected)
        else -> ""
    }
    val color = when {
        !canShoot -> colors.onPhotoMuted
        state.capture == WifiCaptureStatus.Uncertain -> colors.onPhotoWarning
        state.capture == WifiCaptureStatus.Rejected -> colors.onPhotoDanger
        else -> colors.onPhoto
    }
    Column(
        modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Always measure three Caption lines at the current font scale, even while idle.
        Text(
            text,
            style = AlphaType.Caption,
            color = color,
            textAlign = TextAlign.Center,
            minLines = 3,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .then(if (text.isNotEmpty()) Modifier.viewerChromeScrim() else Modifier)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        Box(Modifier.viewerChromeScrim(CircleShape).padding(6.dp)) {
            GlassShutterButton(
                onClick = {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onShutter()
                },
                contentDescription = stringResource(Res.string.remote_shutter_cd),
                enabled = canShoot || shooting,
                pressing = shooting,
                onPhoto = true,
            )
        }
    }
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
    onAppSettings: (() -> Unit)?,
) {
    val busy = state.phase != WifiRemotePhase.Idle && state.phase != WifiRemotePhase.Failed
    val blocked = notice == WifiRemoteNotice.OtherCamera || notice == WifiRemoteNotice.DownloadBusy
    val existingBrowseSession = busy && state.purpose == WifiSessionPurpose.Browse
    val noticeMessage = when (notice) {
        WifiRemoteNotice.OtherCamera -> stringResource(Res.string.wifi_remote_other_camera)
        WifiRemoteNotice.DownloadBusy -> stringResource(Res.string.remote_wifi_download_busy)
        WifiRemoteNotice.Busy -> stringResource(Res.string.remote_wifi_busy)
        null -> null
    }
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 0 } }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = stringResource(Res.string.remote_mode_wifi_title),
                scrolled = scrolled,
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
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
        if (existingBrowseSession) {
            ScreenContent(
                modifier = Modifier.fillMaxSize().padding(padding).overlayFocusLevel()
                    .verticalScroll(scroll)
                    .padding(horizontal = AlphaDimens.screenMargin, vertical = 12.dp),
                maxWidth = 560.dp,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap)) {
                    noticeMessage?.let {
                        GlassWarningCard(
                            title = stringResource(Res.string.wifi_remote_blocked_heading),
                            message = it,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (notice == null) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(Res.string.remote_mode_wifi_desc),
                                style = AlphaType.Body,
                                color = AlphaTheme.colors.textSecondary,
                            )
                        }
                    }
                }
            }
        } else {
            WifiConnectFallbackContent(
                state = state,
                supportsAutomaticConnection = controller.supportsAutomaticConnection,
                supportsManualSetup = controller.supportsManualSetup,
                supportsAddressDiscovery = controller.supportsAddressDiscovery,
                host = host,
                onHostChange = onHostChange,
                manual = manual,
                onToggleManual = onToggleManual,
                onAutoConnect = onAutoConnect,
                onManualConnect = onManualConnect,
                onWifiSettings = onWifiSettings,
                onContinueManualJoin = onContinueManualJoin,
                onDisconnect = onDisconnect,
                networkPermissionMessage = networkPermissionMessage,
                modifier = Modifier.fillMaxSize().padding(padding),
                scrollState = scroll,
                notice = noticeMessage,
                connectionBlocked = blocked,
                onAppSettings = onAppSettings,
            )
        }
    }
}
