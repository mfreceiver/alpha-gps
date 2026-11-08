package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.wifi_connection_failed
import cameragps.sharednew.generated.resources.wifi_remote_address_failed
import cameragps.sharednew.generated.resources.wifi_remote_approval
import cameragps.sharednew.generated.resources.wifi_remote_auto_connect
import cameragps.sharednew.generated.resources.wifi_remote_auto_setup
import cameragps.sharednew.generated.resources.wifi_remote_bluetooth_required
import cameragps.sharednew.generated.resources.wifi_remote_browse
import cameragps.sharednew.generated.resources.wifi_remote_browse_unsupported
import cameragps.sharednew.generated.resources.wifi_remote_browser_back
import cameragps.sharednew.generated.resources.wifi_remote_close
import cameragps.sharednew.generated.resources.wifi_remote_closing
import cameragps.sharednew.generated.resources.wifi_remote_connect
import cameragps.sharednew.generated.resources.wifi_remote_connected
import cameragps.sharednew.generated.resources.wifi_remote_credentials_failed
import cameragps.sharednew.generated.resources.wifi_remote_disconnect
import cameragps.sharednew.generated.resources.wifi_remote_failed
import cameragps.sharednew.generated.resources.wifi_remote_invalid_ip
import cameragps.sharednew.generated.resources.wifi_remote_ip
import cameragps.sharednew.generated.resources.wifi_remote_join_failed
import cameragps.sharednew.generated.resources.wifi_remote_join_wifi
import cameragps.sharednew.generated.resources.wifi_remote_joining
import cameragps.sharednew.generated.resources.wifi_remote_location_required
import cameragps.sharednew.generated.resources.wifi_remote_lost
import cameragps.sharednew.generated.resources.wifi_remote_manual
import cameragps.sharednew.generated.resources.wifi_remote_opening
import cameragps.sharednew.generated.resources.wifi_remote_other_camera
import cameragps.sharednew.generated.resources.wifi_remote_permission
import cameragps.sharednew.generated.resources.wifi_remote_prepare_only
import cameragps.sharednew.generated.resources.wifi_remote_prepare_only_hint
import cameragps.sharednew.generated.resources.wifi_remote_preparing
import cameragps.sharednew.generated.resources.wifi_remote_selection_clear
import cameragps.sharednew.generated.resources.wifi_remote_settings
import cameragps.sharednew.generated.resources.wifi_remote_setup
import cameragps.sharednew.generated.resources.wifi_remote_setup_detect_address
import cameragps.sharednew.generated.resources.wifi_remote_setup_failed
import cameragps.sharednew.generated.resources.wifi_remote_shutdown_requested
import cameragps.sharednew.generated.resources.wifi_remote_shutdown_unavailable
import cameragps.sharednew.generated.resources.wifi_remote_shutdown_unconfirmed
import cameragps.sharednew.generated.resources.wifi_remote_title
import cameragps.sharednew.generated.resources.wifi_remote_wifi_disabled
import com.diamondedge.logging.logging
import com.sasch.cameragps.sharednew.remote.wifi.SonyImageTransfer
import com.sasch.cameragps.sharednew.remote.wifi.WifiBatchSummary
import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraCapture
import com.sasch.cameragps.sharednew.remote.wifi.WifiImageTransferState
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoDownload
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteController
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteFailure
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemotePhase
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.WifiShutdownStatus
import com.sasch.cameragps.sharednew.remote.wifi.groupCameraPhotos
import org.jetbrains.compose.resources.stringResource

class WifiRemoteViewModel(val identifier: String, val controller: WifiRemoteController) : ViewModel() {
    var host by mutableStateOf("")

    /** Cross-page selection snapshot: capture id -> metadata, in selection (insertion) order. */
    internal var selectedPhotos by mutableStateOf<Map<String, WifiCameraCapture>>(emptyMap())
        private set
    val selectedPhotoIds: Set<String> get() = selectedPhotos.keys

    /** Catalog generation the selection belongs to; mismatch means the catalog changed. */
    private var selectedGeneration: Long? = null

    var viewedPhotoIndex by mutableStateOf<Int?>(null)
        private set

    /**
     * Tap-to-view entry. Ignored while a selection exists: in selection mode a tap toggles
     * the tile, and previewing goes through [previewPhoto] instead.
     */
    fun openPhoto(captureId: String) {
        if (selectedPhotoIds.isNotEmpty()) return
        previewPhoto(captureId)
    }

    /**
     * Opens the full-screen viewer for a capture on the current page without touching the
     * selection (magnifier button in selection mode). Neither the selected set nor the
     * selection mode changes, and [closePhoto] leaves them alone too.
     */
    fun previewPhoto(captureId: String) {
        val state = controller.sessions.value[identifier.uppercase()]?.wifiRemote ?: return
        if (!state.photoBrowser.open) return
        // Opening the viewer while a catalog load or download is in flight would clear the
        // transfer result and, with it, the pending batch summary line. Refuse instead.
        if (state.photoBrowser.loading || state.imageTransfer.busy) return
        val index = groupCameraPhotos(state.photoBrowser.photos).indexOfFirst { it.id == captureId }
        if (index >= 0) {
            noteBatchCompletion()
            controller.clearPhotoTransferResult(identifier)
            viewedPhotoIndex = state.photoBrowser.offset + index
            viewPhotoAt(viewedPhotoIndex!!)
        }
    }

    fun closePhoto() {
        noteBatchCompletion()
        controller.clearPhotoTransferResult(identifier)
        viewedPhotoIndex = null
        controller.clearPhotoPreview(identifier)
    }

    fun viewPhotoAt(index: Int) {
        if (viewedPhotoIndex == null) return
        val state = controller.sessions.value[identifier.uppercase()]?.wifiRemote ?: return
        val browser = state.photoBrowser
        if (!browser.open) return
        noteBatchCompletion()
        val captures = groupCameraPhotos(browser.photos)
        val count = captures.size
        if (index in browser.offset until browser.offset + count) {
            if (viewedPhotoIndex != index) controller.clearPhotoTransferResult(identifier)
            viewedPhotoIndex = index
            controller.showPhotoPreview(identifier, captures[index - browser.offset].preview.handle)
        } else if (!browser.loading && !state.imageTransfer.busy) {
            val offset = when {
                index == browser.offset - 1 && browser.offset > 0 -> maxOf(
                    0,
                    browser.offset - SonyImageTransfer.PAGE_SIZE
                )

                index == browser.offset + count && browser.hasMore -> browser.offset + SonyImageTransfer.PAGE_SIZE
                else -> return
            }
            controller.clearPhotoTransferResult(identifier)
            viewedPhotoIndex = index
            controller.browsePhotos(identifier, offset, preferredPhotoIndex = index)
        }
    }

    fun togglePhotoSelection(captureId: String) {
        val state = controller.sessions.value[identifier.uppercase()]?.wifiRemote ?: return
        if (!state.photoBrowser.open || state.photoBrowser.loading || state.imageTransfer.busy) return
        // Only captures visible on the current page can be (de)selected, but the selection
        // itself survives paging and is only cleared explicitly or on catalog changes.
        val capture = groupCameraPhotos(state.photoBrowser.photos).firstOrNull { it.id == captureId }
            ?: return
        batchSummary = null
        selectedPhotos = if (captureId in selectedPhotos) selectedPhotos - captureId
        else selectedPhotos + (captureId to capture)
    }

    fun selectAllPhotos() {
        val state = controller.sessions.value[identifier.uppercase()]?.wifiRemote ?: return
        if (!state.photoBrowser.open || state.photoBrowser.loading || state.imageTransfer.busy) return
        // "Select all on this page" unions with selections kept from other pages.
        batchSummary = null
        selectedPhotos = selectedPhotos +
                groupCameraPhotos(state.photoBrowser.photos).associateBy { it.id }
    }

    fun clearPhotoSelection() {
        selectedPhotos = emptyMap()
        batchSummary = null
    }

    /**
     * Skip counts of the JPG-only batch the user just started; shown as "Downloaded N,
     * skipped M" once the transfer completes. Cleared whenever the selection changes.
     */
    internal var batchSummary by mutableStateOf<WifiBatchSummary?>(null)
        private set

    /**
     * The batch whose summary has already been observed complete. Lives in the ViewModel
     * (not composition) so a preview round-trip — which removes the browser composable and
     * clears the transfer result — cannot lose the finished batch line. Reset whenever a
     * new batch summary is recorded; a null batchSummary renders it moot.
     */
    private var settledBatchSummary: WifiBatchSummary? = null

    internal fun recordBatchSummary(summary: WifiBatchSummary?) {
        settledBatchSummary = null
        batchSummary = summary
    }

    /**
     * Latches the current batch summary as complete if the transfer finished it. Must be
     * called before anything clears the transfer result (preview/viewer round-trips);
     * does nothing when no batch is pending or it already settled.
     */
    internal fun noteBatchCompletion() {
        val summary = batchSummary ?: return
        if (settledBatchSummary != null) return
        if (summary.isComplete(currentImageTransfer())) settledBatchSummary = summary
    }

    /** True when the "Downloaded N, skipped M" line should show instead of the transfer status. */
    internal fun batchSummarySettled(): Boolean {
        val summary = batchSummary ?: return false
        return settledBatchSummary === summary || summary.isComplete(currentImageTransfer())
    }

    private fun currentImageTransfer(): WifiImageTransferState =
        controller.sessions.value[identifier.uppercase()]?.wifiRemote?.imageTransfer
            ?: WifiImageTransferState()

    fun reconcilePhotoSelection() {
        val state = controller.sessions.value[identifier.uppercase()]?.wifiRemote
        if (state?.phase != WifiRemotePhase.Ready || !state.photoBrowser.open) {
            clearPhotoSelection()
            selectedGeneration = null
            closePhoto()
        } else {
            val generation = state.photoBrowser.browserGeneration
            if (selectedGeneration != null && selectedGeneration != generation) {
                // The catalog was refreshed or reopened: previous selections are stale.
                clearPhotoSelection()
            }
            selectedGeneration = generation
        }
    }

    /**
     * Browse-only mode: this connection exists to download photos, not to remote-shoot.
     * Set by the screen; drives both the auto-enter effect and back navigation.
     */
    var browseOnly: Boolean = false

    /** Guards the browse-only auto-enter so it fires once per connection, not after every close. */
    private var autoEnteredBrowser = false

    /**
     * Auto-opens the photo browser once per connection while in browse-only mode. Leaving
     * the browser deliberately (back) must strand the user on a "no live view" Ready
     * screen — so the effect never re-opens it while Ready holds. The once-flag is bound
     * to the session lifecycle, not just to non-Ready phases: any observation outside a
     * live Ready session (Closing/Failed/Idle, or the registry entry fully removed after
     * cleanup) resets it, so the next connection auto-enters again even when no
     * intermediate transition state was ever observed.
     */
    fun maybeAutoEnterBrowser() {
        val state = controller.sessions.value[identifier.uppercase()]?.wifiRemote
        if (state == null || state.phase != WifiRemotePhase.Ready) {
            autoEnteredBrowser = false
            return
        }
        if (!browseOnly || autoEnteredBrowser || state.photoBrowser.open || !state.canTransferImages) return
        autoEnteredBrowser = true
        controller.browsePhotos(identifier)
    }

    fun navigateBack(onClose: () -> Unit) {
        when {
            viewedPhotoIndex != null -> closePhoto()
            selectedPhotoIds.isNotEmpty() -> clearPhotoSelection()
            controller.sessions.value[identifier.uppercase()]?.wifiRemote?.photoBrowser?.open == true -> {
                val state = controller.sessions.value[identifier.uppercase()]!!.wifiRemote
                if (browseOnly) {
                    // Browse-only has no remote-shooting screen to go "back" to: leaving
                    // the browser means ending the session. While a transfer or catalog
                    // load is in flight the controllers would ignore it anyway — wait.
                    if (!state.photoBrowser.loading && !state.imageTransfer.busy) {
                        controller.disconnect(identifier)
                    }
                } else {
                    controller.leavePhotoBrowser(identifier)
                }
            }
            else -> onClose()
        }
    }

    override fun onCleared() {
        logging("WifiRemoteViewModel").d { "Wi-Fi remote ViewModel cleared -> Wi-Fi disconnect requested" }
        controller.disconnect(identifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiRemoteScreen(viewModel: WifiRemoteViewModel, onConnect: (String) -> Unit,
                     onWifiSettings: () -> Unit,
                     onClose: () -> Unit,
                     isExperimentalNoticeAcknowledged: () -> Boolean,
                     onAcknowledgeExperimentalNotice: () -> Unit,
                     onConnectAutomatically: () -> Unit = {},
                     wifiSettingsLabel: String = stringResource(Res.string.wifi_remote_settings),
                     networkPermissionMessage: String = stringResource(Res.string.wifi_remote_permission),
                     openingSessionMessage: String = stringResource(Res.string.wifi_remote_opening),
                     onAppSettings: (() -> Unit)? = null,
                     defaultDownloadFormatJpeg: Boolean = false,
                     onDownloadPhotos: (List<WifiPhotoDownload>) -> Unit = { downloads ->
                         viewModel.controller.downloadPhotos(viewModel.identifier, downloads)
                     }
) {
    var noticeAcknowledged by remember { mutableStateOf(isExperimentalNoticeAcknowledged()) }
    if (!noticeAcknowledged) {
        WifiRemoteExperimentalNotice(
            onContinue = {
                onAcknowledgeExperimentalNotice()
                noticeAcknowledged = true
            },
            onCancel = onClose,
        )
    }
    val controller = viewModel.controller
    val sessions by controller.sessions.collectAsState()
    val owner by controller.owner.collectAsState()
    val id = viewModel.identifier.uppercase()
    val state = sessions[id]?.wifiRemote ?: WifiRemoteState()
    val ready = state.phase == WifiRemotePhase.Ready
    val busy = state.phase !in setOf(WifiRemotePhase.Idle, WifiRemotePhase.Failed)
    val otherCamera = owner != null && owner != id
    var manual by remember { mutableStateOf(!controller.supportsAutomaticConnection && !controller.supportsManualSetup) }
    val back = { viewModel.navigateBack(onClose) }
    val browserStateHolder = rememberSaveableStateHolder()
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = back,
    )
    // Browse-only: as soon as the session is ready, walk straight into the photo
    // browser — there is no live view or shutter to show first. The once-per-connection
    // guard lives in the ViewModel: leaving the browser (back) must not re-open it.
    viewModel.browseOnly = true
    LaunchedEffect(ready, state.photoBrowser.open, state.canTransferImages) {
        viewModel.maybeAutoEnterBrowser()
    }
    LaunchedEffect(
        ready,
        state.photoBrowser.open,
        state.photoBrowser.photos,
        state.photoBrowser.loading,
        state.photoBrowser.browserGeneration
    ) {
        viewModel.reconcilePhotoSelection()
    }
    if (viewModel.viewedPhotoIndex != null && ready && state.photoBrowser.open) {
        WifiPhotoViewer(state, viewModel, onDownloadPhotos, defaultDownloadFormatJpeg)
        return
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(Res.string.wifi_remote_title)) }, navigationIcon = {
            TextButton(onClick = back,
                enabled = viewModel.selectedPhotoIds.isNotEmpty() || !state.photoBrowser.open ||
                        (!state.photoBrowser.loading && !state.imageTransfer.busy)
            ) {
                Text(
                    stringResource(
                        if (viewModel.selectedPhotoIds.isNotEmpty()) Res.string.wifi_remote_selection_clear
                        else if (state.photoBrowser.open) Res.string.wifi_remote_browser_back
                    else Res.string.wifi_remote_close))
            }
        })
    }) { padding ->
        Column(
            // The photo grid gets a tighter frame so the photos use the screen.
            Modifier.fillMaxSize().padding(padding)
                .padding(if (state.photoBrowser.open) 8.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (state.photoBrowser.open) 8.dp else 16.dp)
        ) {
            if (state.photoBrowser.open) {
                browserStateHolder.SaveableStateProvider("photos") {
                    WifiPhotoBrowser(state, viewModel, onDownloadPhotos, defaultDownloadFormatJpeg)
                }
                return@Column
            }
            // Connect actions (formerly the live-view placeholder): still the primary
            // way to establish the session before the photo browser takes over.
            if (!busy && (controller.supportsAutomaticConnection || controller.supportsManualSetup)) {
                Button(onClick = onConnectAutomatically, enabled = !otherCamera) {
                    Text(
                        stringResource(
                            if (controller.supportsAutomaticConnection) Res.string.wifi_remote_auto_connect
                            else Res.string.wifi_remote_prepare_only
                        )
                    )
                }
            }
                        val controls: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!busy) {
                        if (controller.supportsAutomaticConnection || controller.supportsManualSetup) {
                            Text(
                                stringResource(
                                    if (controller.supportsAutomaticConnection) Res.string.wifi_remote_auto_setup
                                    else Res.string.wifi_remote_prepare_only_hint
                                )
                            )
                            TextButton(onClick = { manual = !manual }) {
                                Text(stringResource(Res.string.wifi_remote_manual))
                            }
                        }
                        if (manual) {
                            Text(
                                stringResource(
                                    if (controller.supportsAddressDiscovery) Res.string.wifi_remote_setup_detect_address
                                    else Res.string.wifi_remote_setup
                                )
                            )
                            OutlinedButton(onClick = onWifiSettings) { Text(wifiSettingsLabel) }
                            OutlinedTextField(value = viewModel.host, onValueChange = { viewModel.host = it.take(64) },
                                label = { Text(stringResource(Res.string.wifi_remote_ip)) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            Button(
                                onClick = { onConnect(viewModel.host) },
                                enabled = (controller.supportsAddressDiscovery || viewModel.host.isNotBlank()) && !otherCamera
                            ) {
                                Text(stringResource(Res.string.wifi_remote_connect))
                            }
                        }
                    }
                    if (otherCamera) Text(stringResource(Res.string.wifi_remote_other_camera))
                    if (state.phase == WifiRemotePhase.Idle) {
                        val shutdownMessage = when (state.wifiShutdown) {
                            WifiShutdownStatus.Requested -> Res.string.wifi_remote_shutdown_requested
                            WifiShutdownStatus.Unavailable -> Res.string.wifi_remote_shutdown_unavailable
                            WifiShutdownStatus.Unconfirmed -> Res.string.wifi_remote_shutdown_unconfirmed
                            WifiShutdownStatus.NotRequested -> null
                        }
                        shutdownMessage?.let { Text(stringResource(it)) }
                    }
                    if (state.phase == WifiRemotePhase.OpeningSession) Text(openingSessionMessage)
                    if (state.phase == WifiRemotePhase.PreparingCamera) Text(stringResource(Res.string.wifi_remote_preparing))
                    state.manualNetwork?.let { credentials ->
                        WifiManualJoin(
                            credentials, onWifiSettings,
                            onContinue = { controller.continueManualConnection(id) })
                    }
                    if (state.phase == WifiRemotePhase.AwaitingNetworkApproval) Text(stringResource(Res.string.wifi_remote_approval))
                    if (state.phase == WifiRemotePhase.JoiningNetwork) Text(stringResource(Res.string.wifi_remote_joining))
                    if (state.phase == WifiRemotePhase.Closing) Text(stringResource(Res.string.wifi_remote_closing))
                    state.failure?.let { failure ->
                        Text(if (failure == WifiRemoteFailure.NetworkPermissionDenied) networkPermissionMessage else stringResource(when (failure) {
                            WifiRemoteFailure.InvalidAddress -> Res.string.wifi_remote_invalid_ip
                            WifiRemoteFailure.JoinCameraWifi -> Res.string.wifi_remote_join_wifi
                            WifiRemoteFailure.NetworkPermissionDenied -> Res.string.wifi_remote_permission
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
                        }), color = MaterialTheme.colorScheme.error)
                    }
                    if (noticeAcknowledged && onAppSettings != null) {
                        WifiConnectionHelp(state.failure, onAppSettings)
                    }
                    if (ready) {
                        Text(stringResource(Res.string.wifi_remote_connected, state.cameraName))
                        if (state.canTransferImages) OutlinedButton(
                            onClick = { controller.browsePhotos(id) }
                        ) {
                            Text(stringResource(Res.string.wifi_remote_browse))
                        } else Text(stringResource(Res.string.wifi_remote_browse_unsupported))
                    }
                    if (busy) OutlinedButton(onClick = { controller.disconnect(id) }, enabled = state.phase != WifiRemotePhase.Closing) {
                        Text(stringResource(Res.string.wifi_remote_disconnect))
                    }
                }
            }
            controls(Modifier.fillMaxWidth().weight(1f))
        }
    }
}
