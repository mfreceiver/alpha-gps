package com.saschl.cameragps.ui.device

import android.companion.CompanionDeviceManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sasch.cameragps.sharednew.bluetooth.location.PhoneLocationFix
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionOrchestrator
import com.sasch.cameragps.sharednew.bluetooth.status.CameraLiveStatus
import com.sasch.cameragps.sharednew.database.LogDatabase
import com.sasch.cameragps.sharednew.database.devices.CameraDevice
import com.sasch.cameragps.sharednew.database.devices.CameraDeviceDAO
import com.sasch.cameragps.sharednew.database.getDatabaseBuilder
import com.sasch.cameragps.sharednew.ui.device.DeviceDetailDataSource
import com.sasch.cameragps.sharednew.ui.device.DeviceDetailViewModel
import com.sasch.cameragps.sharednew.ui.device.DeviceLiveStatusSource
import com.saschl.cameragps.AppServices
import com.saschl.cameragps.service.AssociatedDeviceCompat
import com.saschl.cameragps.ui.pairing.startDevicePresenceObservation
import com.saschl.cameragps.ui.pairing.stopDevicePresenceObservation
import kotlinx.coroutines.flow.Flow

/** The orchestrator's live-status hub as the device page sees it (read-only + signal lease). */
private class OrchestratorLiveStatusSource(private val orchestrator: CameraSessionOrchestrator) :
    DeviceLiveStatusSource {
    override fun observe(address: String): Flow<CameraLiveStatus?> = orchestrator.liveStatus.observe(address)
    override fun acquireSignalMonitoring(address: String): () -> Unit {
        val lease = orchestrator.acquireSignalMonitoring(address)
        return { lease.release() }
    }

    override fun observePhoneLocation(): Flow<PhoneLocationFix?> = orchestrator.liveStatus.phoneLocation
}

private fun createAndroidDataSource(dao: CameraDeviceDAO): DeviceDetailDataSource {
    return object : DeviceDetailDataSource {
        override suspend fun ensureDeviceExists(deviceId: String, deviceName: String?) {
            // Companion/pairing flows own the row's settings, but a rename needs
            // a row to update: insert-if-absent never touches an existing one.
            dao.insertDevice(CameraDevice(mac = deviceId.uppercase()))
        }

        override suspend fun isDeviceEnabled(deviceId: String) = dao.isDeviceEnabled(deviceId)
        override suspend fun isAlwaysOnEnabled(deviceId: String) =
            dao.isDeviceAlwaysOnEnabled(deviceId)

        override suspend fun setDeviceEnabled(deviceId: String, enabled: Boolean) {
            dao.setDeviceEnabled(deviceId, enabled)
        }

        override suspend fun setAlwaysOnEnabled(deviceId: String, enabled: Boolean) {
            dao.setAlwaysOnEnabled(deviceId, enabled)
        }

        override suspend fun getDeviceName(deviceId: String) = dao.getDeviceName(deviceId)

        // Database only: the CompanionDeviceManager association keeps its own
        // name, which Android never shows to the user once a custom one exists.
        override suspend fun setDeviceName(deviceId: String, name: String) {
            dao.setDeviceName(deviceId, name, isCustom = true)
        }
    }
}

/**
 * The one [DeviceDetailViewModel] of a camera, keyed by its address in the current
 * ViewModelStoreOwner: the device page and the device list's ⓘ dialog get the same instance (same
 * pending connect/disconnect state, same persisted intents), never a copy of the wiring.
 */
@Composable
internal fun rememberDeviceDetailViewModel(
    device: AssociatedDeviceCompat,
    deviceManager: CompanionDeviceManager,
): DeviceDetailViewModel {
    val context = LocalContext.current
    return viewModel(key = device.address) {
        val dao = LogDatabase.getRoomDatabase(getDatabaseBuilder(context)).cameraDeviceDao()
        val orchestrator = AppServices.from(context).orchestrator
        DeviceDetailViewModel(
            dataSource = createAndroidDataSource(dao),
            serviceActions = AndroidDeviceDetailServiceActions(
                context.applicationContext,
                observePresence = { enabled ->
                    if (enabled) startDevicePresenceObservation(deviceManager, device)
                    else stopDevicePresenceObservation(deviceManager, device)
                },
            ),
            cameraSettings = orchestrator,
            liveStatusSource = OrchestratorLiveStatusSource(orchestrator),
        )
    }
}
