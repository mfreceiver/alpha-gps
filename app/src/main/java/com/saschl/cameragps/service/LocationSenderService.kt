package com.saschl.cameragps.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.companion.CompanionDeviceManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sasch.cameragps.sharednew.bluetooth.SonyBluetoothConstants.locationTransmissionNotificationId
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionOrchestrator
import com.sasch.cameragps.sharednew.bluetooth.session.OrchestratorEvent
import com.sasch.cameragps.sharednew.database.devices.CameraDevice
import com.sasch.cameragps.sharednew.notification.TransmissionNotificationCoordinator
import com.saschl.cameragps.AppServices
import com.saschl.cameragps.notification.AndroidTransmissionNotificationPublisher
import com.saschl.cameragps.notification.NotificationsHelper
import com.saschl.cameragps.service.coordinator.ServicePresenceCoordinator
import com.saschl.cameragps.service.coordinator.ServiceShutdownCoordinator
import com.saschl.cameragps.service.transport.AndroidBleTransport
import com.saschl.cameragps.utils.PreferencesManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Thin Android lifecycle shell. Owns the foreground service, notifications,
 * sounds, intent routing and stopSelf(). All BLE/location/session logic lives
 * in the shared [CameraSessionOrchestrator]; the raw GATT/location plumbing in
 * [AndroidBleTransport] and the flavor-provided
 * [com.sasch.cameragps.sharednew.bluetooth.location.LocationSource].
 */
class LocationSenderService : LifecycleService() {

    private var hasForegroundSession = false
    private lateinit var eventSoundPlayer: EventSoundPlayer
    private lateinit var bluetoothStateReceiver: BluetoothStateBroadcastReceiver
    private val commandMutex = Mutex()
    private val commandRouter = ServiceCommandRouter()
    private var transmissionNotificationJob: Job? = null
    private var lastHandledStartId = 0

    // The BLE/location graph is app-scoped (see [AppServices]); the service
    // borrows it for the duration of a foreground session.
    private val services get() = AppServices.from(this)
    private val orchestrator get() = services.orchestrator
    private val transport get() = services.transport
    private val bluetoothManager get() = services.bluetoothManager

    private val shutdownCoordinator by lazy {
        ServiceShutdownCoordinator(
            deviceDao = services.deviceDao,
            transport = services.transport,
            onShutdownRequested = { startId -> requestShutdown(startId) },
            isAppEnabled = { PreferencesManager.isAppEnabled(this) },
        )
    }

    private val presenceCoordinator by lazy {
        ServicePresenceCoordinator(
            hasConnectedDevices = { transport.connectedCount() > 0 },
            // Saved devices keep the service alive for automatic reconnection.
            hasAlwaysOnDevices = { services.deviceDao.getEnabledDeviceCount() > 0 },
            onShutdownRequested = {
                Timber.i("No connected or saved cameras remain after presence loss; stopping service")
                requestShutdown(lastHandledStartId)
            },
        )
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false

        /**
         * Check before every startForegroundService(): without a location grant,
         * startForeground() throws SecurityException, and stopping the service
         * then crashes with ForegroundServiceDidNotStartInTimeException.
         */
        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
    }

    // ==================== Lifecycle ====================

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    @SuppressLint("MissingPermission")
    private fun startForegroundSession(): Boolean {
        eventSoundPlayer = EventSoundPlayer(this)
        NotificationsHelper.createNotificationChannel(this)

        if (!startAsForegroundService()) return false
        hasForegroundSession = true

        lifecycleScope.launch {
            orchestrator.events.collect { event -> handleEvent(event) }
        }
        transmissionNotificationJob = TransmissionNotificationCoordinator(
            scope = lifecycleScope,
            sessions = orchestrator.sessions,
            transmitting = orchestrator.locationManager.isTransmitting,
            publisher = AndroidTransmissionNotificationPublisher(this),
            deviceNames = services.deviceDao.observeAllDevices().map { devices ->
                devices.mapNotNull { device ->
                    resolveCameraDisplayName(device)?.let { device.mac.uppercase() to it }
                }.toMap()
            },
        ).start()
        return true
    }

    /**
     * The user swiped the app away: that is "exit", so actively disconnect the camera
     * (Wi-Fi, then BLE/GATT) and stop instead of lingering as a background service.
     * Backgrounding (Home) never reaches here. Boot / notification / CDM-presence
     * starts are unaffected and simply start a new session.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.i("Task removed; disconnecting cameras and stopping service")
        // Snapshot: the teardown callback used to read the mutable field, which a
        // start command arriving during the window could have advanced meanwhile.
        val startId = lastHandledStartId
        services.disconnectAllOnExit {
            // Only stop for our own last start: a start command that arrived during
            // the teardown window (and cancelled it) must keep the service alive.
            if (startId > 0) stopSelf(startId) else stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        isRunning = false
        // Stop publishing before shutdown clears sessions; teardown must not repost standby.
        transmissionNotificationJob?.cancel()
        super.onDestroy()
        runCatching {
            if (::bluetoothStateReceiver.isInitialized) unregisterReceiver(bluetoothStateReceiver)
        }.onFailure { e ->
            Timber.e(e, "Failed to unregister Bluetooth state receiver")
        }
        if (hasForegroundSession) {
            com.saschl.cameragps.AppServices.from(this).wifiRemote.disconnect()
            orchestrator.shutdownAll()
            transport.disconnectAll()
        }
        if (::eventSoundPlayer.isInitialized) eventSoundPlayer.release()
        Timber.i("Destroyed service")
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val command = commandRouter.route(intent)
        // A valid connection request starts a new session: any exit teardown that is
        // still pending or running (e.g. within the 3s Wi-Fi close window of a task
        // removal) must not tear this connection down afterwards.
        if (command is ServiceCommand.Connect) {
            services.cancelPendingExit()
        }
        // A stop-related startService intent can create a new instance. There is no
        // foreground session to shut down in that case, so don't promote it.
        if ((command is ServiceCommand.Shutdown || command is ServiceCommand.DeviceDisappeared)
            && !hasForegroundSession
        ) {
            Timber.i("Ignoring stop-related request because no foreground session is running")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!hasForegroundSession && !startForegroundSession()) {
            return START_NOT_STICKY
        }

        if (!bluetoothManager.adapter.isEnabled) {
            Timber.w("Bluetooth is disabled, will shutdown service")
            startAsForegroundService()
            requestShutdown(startId)
            return START_NOT_STICKY
        }

        lifecycleScope.launch {
            commandMutex.withLock {
                lastHandledStartId = startId
                handleStartCommand(command, startId)
                Timber.i(
                    "processed start command $startId with intent action ${intent?.action} and address ${
                        intent?.getStringExtra(
                            "address"
                        )
                    }"
                )
            }
        }
        return START_REDELIVER_INTENT
    }

    @SuppressLint("MissingPermission")
    private fun handleEvent(event: OrchestratorEvent) {
        when (event) {
            is OrchestratorEvent.DeviceConnected -> {
                eventSoundPlayer.play(TransmissionSoundEvent.CAMERA_CONNECTED)
            }

            is OrchestratorEvent.DeviceDisconnected -> {
                eventSoundPlayer.play(TransmissionSoundEvent.CAMERA_DISCONNECTED)
                lifecycleScope.launch {
                    commandMutex.withLock {
                        presenceCoordinator.onDeviceDisconnected(event.identifier)
                    }
                }
            }

            is OrchestratorEvent.HandshakeCompleted -> {
                // Setup is done — the periodic location writes don't need the
                // short connection interval requested at connect
                transport.relaxConnection(event.identifier)
            }

            is OrchestratorEvent.PairingFailed -> {
                // Pairing-failure UI state is tracked in AppServices
                Timber.e("Pairing failed for ${event.identifier}")
            }

            is OrchestratorEvent.FirstLocationAcquired -> {
                eventSoundPlayer.play(TransmissionSoundEvent.LOCATION_ACQUIRED)
            }

            is OrchestratorEvent.LocationUnavailable -> {
                eventSoundPlayer.play(TransmissionSoundEvent.LOCATION_INVALID)
            }
        }
    }

    // ==================== Command handling ====================

    @SuppressLint("MissingPermission")
    private suspend fun handleStartCommand(command: ServiceCommand, startId: Int) {
        when (command) {
            is ServiceCommand.Ignore -> {
                Timber.w(command.reason)
            }

            is ServiceCommand.ReconnectAlwaysOn -> {
                shutdownCoordinator.handleNoAddress(startId)
            }

            is ServiceCommand.Shutdown -> {
                shutdownCoordinator.handleShutdownRequest(command.address, startId)
            }

            is ServiceCommand.DeviceDisappeared -> {
                Timber.i(
                    "Device presence lost for ${command.address}; GATT connected=${
                        transport.isConnected(
                            command.address
                        )
                    }"
                )
                presenceCoordinator.onDeviceDisappeared(command.address)
            }

            is ServiceCommand.SendRemoteCommand -> {
                val success = orchestrator.sendRemoteCommand(command.address, command.command)
                if (!success) {
                    Timber.w("Remote command ${command.command} failed for ${command.address.uppercase()}")
                }
            }

            is ServiceCommand.Connect -> {
                presenceCoordinator.onDeviceAppeared(command.address)
                ensureBluetoothStateReceiver()
                if (!transport.hasConnection(command.address)) {
                    Timber.i("Service initialized")
                    orchestrator.onConnectRequested(command.address)
                    runCatching {
                        transport.connect(command.address)
                    }.onFailure {
                        Timber.e("Failed to connect to device, bluetooth is likely turned off")
                        orchestrator.onConnectFailed(command.address)
                    }
                }
            }
        }
    }

    private fun ensureBluetoothStateReceiver() {
        if (!::bluetoothStateReceiver.isInitialized) {
            bluetoothStateReceiver = BluetoothStateBroadcastReceiver { enabled ->
                if (!enabled) {
                    Timber.w("Bluetooth turned off, will shutdown service")
                    requestShutdown()
                }
            }
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            ContextCompat.registerReceiver(
                this,
                bluetoothStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun startAsForegroundService(): Boolean {
        try {
            ServiceCompat.startForeground(
                this,
                locationTransmissionNotificationId,
                NotificationsHelper.buildWaitingNotification(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (e: SecurityException) {
            Timber.e("Failed to start foreground service due to missing permissions: ${e.message}")
            stopSelf()
            return false
        }
        return true
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    /**
     * Best display name for the single-camera notification. Priority: a custom Room
     * name, the companion-device association name, the Bluetooth stack's name, then a
     * derived Room name; null makes the notification fall back to a generic "Camera".
     */
    @SuppressLint("MissingPermission")
    private fun resolveCameraDisplayName(device: CameraDevice): String? {
        fun String?.usable() = takeIf { !it.isNullOrBlank() && it != "N/A" }

        device.deviceName.takeIf { device.deviceNameIsCustom }?.usable()?.let { return it }

        val upperMac = device.mac.uppercase()
        val associatedName = runCatching {
            val cdm = getSystemService(CompanionDeviceManager::class.java) ?: return@runCatching null
            cdm.getAssociatedDevices(bluetoothManager.adapter)
                .firstOrNull { it.address.equals(upperMac, ignoreCase = true) }
                ?.name?.usable()
        }.getOrNull()
        if (associatedName != null) return associatedName

        val bluetoothName = runCatching {
            // BLUETOOTH_CONNECT gates BluetoothDevice.name only from Android 12 on.
            val canConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
            if (canConnect) bluetoothManager.adapter.getRemoteDevice(upperMac).name.usable() else null
        }.getOrNull()
        if (bluetoothName != null) return bluetoothName

        // Derived (non-custom) Room name: captured from the hardware earlier; better
        // than the generic label when the Bluetooth stack is unavailable right now.
        return device.deviceName.usable()
    }

    private fun requestShutdown(startId: Int? = null) {
        if (startId != null) stopSelf(startId) else stopSelf()
    }
}
