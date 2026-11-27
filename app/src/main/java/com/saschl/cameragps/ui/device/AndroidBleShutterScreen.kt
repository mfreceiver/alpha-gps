package com.saschl.cameragps.ui.device

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sasch.cameragps.sharednew.bluetooth.coordinator.BleShutterState
import com.sasch.cameragps.sharednew.bluetooth.coordinator.GpsRecoveryBaseline
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionOrchestrator
import com.sasch.cameragps.sharednew.ui.help.GuideTopic
import com.sasch.cameragps.sharednew.ui.remote.BleShutterControls
import com.sasch.cameragps.sharednew.ui.remote.BleShutterScreen
import com.saschl.cameragps.AppServices
import com.saschl.cameragps.utils.PreferencesManager
import kotlinx.coroutines.flow.StateFlow

/** The orchestrator's R1 single-shot BLE shutter as the shared page sees it. */
private class OrchestratorBleShutterControls(private val orchestrator: CameraSessionOrchestrator) : BleShutterControls {
    override val states: StateFlow<Map<String, BleShutterState>> get() = orchestrator.bleShutterStates
    override fun acquire(identifier: String) = orchestrator.acquireBleRemoteControl(identifier)
    override fun trigger(identifier: String) = orchestrator.triggerBleShutter(identifier)
    override fun releaseAsync(identifier: String) = orchestrator.releaseBleRemoteControlAsync(identifier)
    override fun cancelActiveShutter(identifier: String) = orchestrator.cancelActiveBleShutter(identifier)
}

@Composable
fun AndroidBleShutterScreen(
    identifier: String,
    deviceName: String?,
    isCustomName: Boolean,
    onClose: () -> Unit,
    onLeft: (GpsRecoveryBaseline) -> Unit,
    onOpenGuide: (GuideTopic) -> Unit,
    onSwitchToWifi: () -> Unit,
    displayName: String?,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val orchestrator = AppServices.from(context).orchestrator
    val controls = remember(orchestrator) { OrchestratorBleShutterControls(orchestrator) }
    val live by remember(identifier) { orchestrator.liveStatus.observe(identifier.uppercase()) }
        .collectAsState(initial = null)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) {
            controls.cancelActiveShutter(identifier)
        }
    }
    BleShutterScreen(
        identifier = identifier,
        deviceName = deviceName,
        isCustomName = isCustomName,
        controls = controls,
        live = live,
        hapticsEnabled = PreferencesManager.isHapticsEnabled(context),
        onClose = onClose,
        onLeft = onLeft,
        onOpenGuide = onOpenGuide,
        onSwitchToWifi = onSwitchToWifi,
        displayName = displayName,
    )
}
