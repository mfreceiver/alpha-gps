package com.saschl.cameragps.ui.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.device_already_associated
import cameragps.sharednew.generated.resources.device_association_removed_retry
import cameragps.sharednew.generated.resources.internal_error_happened
import cameragps.sharednew.generated.resources.onboarding_pair_step1
import cameragps.sharednew.generated.resources.onboarding_pair_step1_path
import cameragps.sharednew.generated.resources.onboarding_pair_step2
import cameragps.sharednew.generated.resources.onboarding_pair_step2_screen
import cameragps.sharednew.generated.resources.onboarding_pair_step3
import cameragps.sharednew.generated.resources.onboarding_pair_title
import cameragps.sharednew.generated.resources.scan_internal_error_log_hint
import cameragps.sharednew.generated.resources.scan_internal_error_retry
import cameragps.sharednew.generated.resources.scan_timeout_action
import cameragps.sharednew.generated.resources.scan_timeout_creators_app_hint
import cameragps.sharednew.generated.resources.scan_timeout_title
import cameragps.sharednew.generated.resources.the_request_was_canceled
import cameragps.sharednew.generated.resources.the_user_explicitly_declined_the_request
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassWarningCard
import com.sasch.cameragps.sharednew.ui.devicelist.AddCameraButton
import com.sasch.cameragps.sharednew.ui.help.GuideStepRow
import com.sasch.cameragps.sharednew.ui.help.GuideTopic
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.saschl.cameragps.service.AssociatedDeviceCompat
import com.saschl.cameragps.ui.BluetoothWarningCard
import com.saschl.cameragps.ui.LocationWarningCard
import com.saschl.cameragps.ui.pairing.isDevicePaired
import com.saschl.cameragps.utils.DeviceAssociationUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import timber.log.Timber

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
@Composable
fun ScanForDevicesMenu(
    deviceManager: CompanionDeviceManager,
    isBluetoothEnabled: Boolean,
    isLocationEnabled: Boolean,
    associatedDevices: List<AssociatedDeviceCompat>,
    onSetPairingDevice: (AssociatedDeviceCompat) -> Unit,
    onDeviceAssociated: (AssociatedDeviceCompat) -> Unit,
    onTroubleshootingClick: (GuideTopic?) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var issue by remember { mutableStateOf<ScanIssue>(ScanIssue.None) }
    val requestCanceledText = stringResource(Res.string.the_request_was_canceled)
    val internalErrorText = stringResource(Res.string.internal_error_happened)
    val userDeclinedText = stringResource(Res.string.the_user_explicitly_declined_the_request)
    val duplicateAssociatedText = stringResource(Res.string.device_already_associated)
    val duplicateRemovedText = stringResource(Res.string.device_association_removed_retry)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        when (it.resultCode) {
            CompanionDeviceManager.RESULT_OK -> {
                it.data?.let { intent ->
                    val bluetoothManager = context.getSystemService<BluetoothManager>()
                    val adapter = bluetoothManager?.adapter
                    DeviceAssociationUtils.getAssociationResult(intent, adapter)?.let { device ->
                        // Device association successful, now check if pairing is needed
                        if (associatedDevices.any { existingDevice -> existingDevice.address == device.address }) {
                            Timber.i("Device ${device.name} already associated, skipping pairing")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                deviceManager.disassociate(device.id)
                                issue = ScanIssue.Notice(duplicateAssociatedText)
                            } else {
                                @Suppress("DEPRECATION")
                                deviceManager.disassociate(device.address)
                                issue = ScanIssue.Notice(duplicateRemovedText)
                            }
                            return@let
                        }
                        if (!isDevicePaired(adapter, device.address)) {
                            Timber.i("Device ${device.name} associated but not paired, initiating pairing")
                            onSetPairingDevice(device)
                        } else {
                            Timber.i("Device ${device.name} already paired, completing association")
                            onDeviceAssociated(device)
                        }
                        issue = ScanIssue.None
                    }
                }
            }

            // RESULT_CANCELED can also carry SendIntentException when launching the chooser failed.
            CompanionDeviceManager.RESULT_CANCELED -> {
                val sendException = it.data?.let { intent ->
                    IntentCompat.getSerializableExtra(
                        intent,
                        ActivityResultContracts.StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION,
                        IntentSender.SendIntentException::class.java,
                    )
                }
                if (sendException != null) {
                    Timber.e(sendException, "Failed to launch companion device association intent")
                    issue = ScanIssue.Internal(
                        sendException.message?.takeIf { it.isNotBlank() } ?: internalErrorText,
                    )
                } else {
                    issue = ScanIssue.Quiet(requestCanceledText)
                }
            }

            CompanionDeviceManager.RESULT_USER_REJECTED -> {
                issue = ScanIssue.Quiet(userDeclinedText)
            }

            CompanionDeviceManager.RESULT_DISCOVERY_TIMEOUT -> {
                issue = ScanIssue.Timeout
            }

            else -> {
                issue = ScanIssue.Internal(internalErrorText)
            }
        }
    }

    val startAssociation: () -> Unit = {
        scope.launch {
            try {
                val intentSender =
                    DeviceAssociationUtils.requestDeviceAssociation(deviceManager)
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to start device association")
                issue = ScanIssue.Internal(
                    e.message?.takeIf { it.isNotBlank() } ?: internalErrorText,
                )
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        if (!isBluetoothEnabled) {
            Box(Modifier.padding(horizontal = 16.dp)) { BluetoothWarningCard() }
        }

        if (!isLocationEnabled) {
            Box(Modifier.padding(horizontal = 16.dp)) { LocationWarningCard() }
        }

        AddCameraButton(onClick = startAssociation)
        when (val current = issue) {
            ScanIssue.None -> Unit

            // Cancelled / declined by the user: a quiet grey line, not an error.
            is ScanIssue.Quiet -> Text(
                text = current.text,
                style = AlphaType.Caption,
                color = AlphaTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // The camera was not found in time: warning card with a link to the re-pairing steps.
            ScanIssue.Timeout -> Box(Modifier.padding(horizontal = 16.dp)) {
                GlassWarningCard(
                    title = stringResource(Res.string.scan_timeout_title),
                    message = stringResource(Res.string.scan_timeout_creators_app_hint),
                    actionLabel = stringResource(Res.string.scan_timeout_action),
                    onAction = { onTroubleshootingClick(GuideTopic.Repair) },
                )
            }

            // Something broke on our side: say so, offer a retry and point to the activity log.
            is ScanIssue.Internal -> {
                Text(
                    text = current.message,
                    style = AlphaType.Body,
                    color = AlphaTheme.colors.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text(
                    text = stringResource(Res.string.scan_internal_error_log_hint),
                    style = AlphaType.Caption,
                    color = AlphaTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                CapsuleButton(
                    text = stringResource(Res.string.scan_internal_error_retry),
                    onClick = {
                        issue = ScanIssue.None
                        startAssociation()
                    },
                    role = CapsuleRole.Tonal,
                )
            }

            is ScanIssue.Notice -> Text(
                text = current.text,
                style = AlphaType.Body,
                color = AlphaTheme.colors.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (associatedDevices.isEmpty()) {
            Box(Modifier.padding(horizontal = 16.dp)) { FirstTimePairingGuideCard() }
        }
    }
}

/** Pre-pairing 3-step guide shown below "Add camera" only when no cameras are saved yet. */
@Composable
private fun FirstTimePairingGuideCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.onboarding_pair_title),
            style = AlphaType.Heading,
            color = AlphaTheme.colors.textPrimary,
        )
        GuideStepRow(
            number = 1,
            text = stringResource(Res.string.onboarding_pair_step1),
            menuPath = stringResource(Res.string.onboarding_pair_step1_path),
        )
        GuideStepRow(
            number = 2,
            text = stringResource(Res.string.onboarding_pair_step2),
            menuPath = stringResource(Res.string.onboarding_pair_step2_screen),
        )
        GuideStepRow(
            number = 3,
            text = stringResource(Res.string.onboarding_pair_step3),
        )
    }
}

/** Outcome of the last CompanionDeviceManager request, graded by how much the user must act. */
private sealed interface ScanIssue {
    data object None : ScanIssue

    /** The user cancelled or declined: informational only. */
    data class Quiet(val text: String) : ScanIssue

    /** Discovery timed out: the camera is probably not in pairing mode. */
    data object Timeout : ScanIssue

    /** The request could not be started or failed for an unexpected reason. */
    data class Internal(val message: String) : ScanIssue

    /** Duplicate-association notices. */
    data class Notice(val text: String) : ScanIssue
}
