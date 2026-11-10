package com.saschl.cameragps.service.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.sasch.cameragps.sharednew.bluetooth.SonyBluetoothConstants
import com.sasch.cameragps.sharednew.bluetooth.transport.BleOperationStatus
import com.sasch.cameragps.sharednew.bluetooth.transport.BlePeripheralTransport
import com.sasch.cameragps.sharednew.bluetooth.transport.BleTransportEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import java.util.Collections
import java.util.UUID

/**
 * Android implementation of [BlePeripheralTransport] over BluetoothGatt.
 *
 * Owns the GATT connection registry (bond check + `autoConnect = true`,
 * absorbed from the former `CameraConnectionManager`) and the GATT callback;
 * all sequencing/orchestration lives in the shared layer. GATT callbacks
 * feed the event channel only through [GattConnectionRegistry.update], whose
 * monitor serializes them with retirement/replacement and verifies the
 * callback's handle is still the address's current connection — a late
 * callback of a closed or superseded GATT is dropped instead of corrupting
 * the state (or events) of the connection that owns the address now.
 *
 * A disconnect keeps the GATT handle open so `autoConnect` can resume the
 * connection when the camera reappears; [disconnectAll] is the only close path.
 */
class AndroidBleTransport(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
) : BlePeripheralTransport {

    private companion object {
        // BluetoothStatusCodes.ERROR_DEVICE_NOT_CONNECTED is hidden from the public SDK.
        const val ERROR_DEVICE_NOT_CONNECTED = 4
    }

    private val eventChannel = Channel<BleTransportEvent>(Channel.UNLIMITED)
    override val events: Flow<BleTransportEvent> = eventChannel.receiveAsFlow()

    private val registry = GattConnectionRegistry<BluetoothGatt>()

    /** Last requested enable state per "MAC:UUID", to label SubscriptionChanged events. */
    private val pendingSubscribeEnable =
        Collections.synchronizedMap(mutableMapOf<String, Boolean>())

    fun connect(mac: String): Boolean {
        val address = mac.uppercase()
        if (registry.has(address)) {
            return true
        }

        try {
            val device: BluetoothDevice = bluetoothManager.adapter.getRemoteDevice(address)
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                //Timber.w("Device $address is not paired. Cannot connect.")
                return false
            }
            val gatt: BluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
                device.connectGatt(
                    BluetoothGattConnectionSettings.Builder()
                        .setAutoConnectEnabled(true)
                        .setTransport(BluetoothDevice.TRANSPORT_LE)
                        .setAutomaticMtuEnabled(true)
                        .build(),
                    context.mainExecutor,
                    gattCallback
                )
            } else {
                // Same serialization as the new API's main executor: a main-looper handler
                // keeps every legacy callback ordered with the registry's own monitor use.
                device.connectGatt(
                    context,
                    true,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE,
                    BluetoothDevice.PHY_LE_1M,
                    Handler(Looper.getMainLooper())
                )
            } ?: throw IllegalStateException("Failed to connect to device $address: GATT is null")
            if (!registry.register(address, gatt)) {
                // Lost a connect race for this address: the older handle keeps it.
                runCatching { gatt.close() }
            }
        } catch (e: SecurityException) {
            Timber.e("SecurityException while connecting to device $address: ${e.message}")
            return false
        }

        return true
    }

    /** `true` if a GATT handle exists for this device (connected or waiting for autoConnect). */
    fun hasConnection(mac: String): Boolean = registry.has(mac.uppercase())

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectAll() {
        registry.retireAll().forEach { connection ->
            runCatching {
                try {
                    connection.gatt.disconnect()
                } finally {
                    connection.gatt.close()
                }
            }.onFailure { Timber.w(it) }
        }
    }

    /**
     * The user disconnects exactly this camera: forget the handle, really close the GATT (no
     * autoConnect left behind) and tell the orchestrator the link is gone. Other cameras are
     * untouched, and late callbacks of the closed handle are dropped — the retire and every
     * still-running callback update serialize on the registry's monitor, so this synthetic
     * Disconnected can never end up ahead of an event the old handle had already begun emitting.
     *
     * @return true when a connection existed.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectDevice(mac: String): Boolean {
        val address = mac.uppercase()
        val connection = registry.retire(address) ?: return false
        synchronized(pendingSubscribeEnable) {
            pendingSubscribeEnable.keys.removeAll { it.startsWith("$address:") }
        }
        runCatching {
            try {
                connection.gatt.disconnect()
            } finally {
                connection.gatt.close()
            }
        }.onFailure { Timber.w(it) }
        eventChannel.trySend(BleTransportEvent.Disconnected(address, statusCode = null))
        return true
    }

    /**
     * Mark a device inactive without closing the GATT (autoConnect keeps
     * listening for the camera). Emits a synthetic Disconnected event so the
     * orchestrator clears the session.
     */
    fun pauseDevice(mac: String) {
        val address = mac.uppercase()
        if (registry.pause(address)) {
            eventChannel.trySend(BleTransportEvent.Disconnected(address, statusCode = null))
        }
    }

    /** Number of devices with a live (resumed) connection. */
    fun connectedCount(): Int = registry.activeCount()

    /** `true` when the device has a GATT handle whose link is inactive (parked on autoConnect). */
    fun isHandleParked(mac: String): Boolean =
        registry.entry(mac.uppercase())?.isActive == false

    /**
     * Closes the parked handle of a camera that is gone and will not come back on its own
     * (absent, "keep active" off): removed from the registry so it stops counting as connection
     * demand, autoConnect cancelled, GATT really closed. A no-op while the link is active or the
     * handle is already gone.
     *
     * A handle that never got past its initial connect still has an upper-layer session stuck in
     * `Connecting` — no Disconnected was ever delivered for it — so retiring it emits a synthetic
     * [BleTransportEvent.Disconnected] to end that session (the camera is gone; a later manual
     * Connect starts fresh). A handle that already reported its disconnect (parked after a real
     * callback or a pause) needs no second event.
     *
     * @return true when a parked handle was retired.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun retireParkedHandle(mac: String): Boolean {
        val address = mac.uppercase()
        val entry = registry.retireIfParked(address) ?: return false
        synchronized(pendingSubscribeEnable) {
            pendingSubscribeEnable.keys.removeAll { it.startsWith("$address:") }
        }
        runCatching {
            try {
                entry.gatt.disconnect()
            } finally {
                entry.gatt.close()
            }
        }.onFailure { Timber.w(it) }
        if (!entry.disconnectedReported) {
            eventChannel.trySend(BleTransportEvent.Disconnected(address, statusCode = null))
        }
        return true
    }

    /**
     * Number of registered GATT handles, whatever their state: active links, connects still
     * pending and handles parked on `autoConnect` waiting for the camera to reappear. This is
     * the number shutdown eligibility must look at — counting only active links would stop the
     * whole service (and kill every GATT) while another camera is still connecting.
     */
    fun connectionCount(): Int = registry.size

    /**
     * Drop back to the default connection parameters once the handshake is done —
     * the periodic location writes do not need the short interval, and holding it
     * costs battery on both the phone and the camera.
     */
    @SuppressLint("MissingPermission")
    fun relaxConnection(mac: String) {
        val connection = registry.entry(mac.uppercase()) ?: return
        if (!connection.isActive) return
        if (!connection.gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)) {
            Timber.w("Could not relax connection priority for %s", mac)
        }
    }

    // ---------------------------------------------------------------------------
    // BlePeripheralTransport
    // ---------------------------------------------------------------------------

    override fun isConnected(identifier: String): Boolean =
        registry.entry(identifier.uppercase())?.isActive == true

    override fun currentMtu(identifier: String): Int? = registry.entry(identifier.uppercase())?.mtu

    override fun wifiMtuRequest(identifier: String): Int? {
        val connection = registry.entry(identifier.uppercase()) ?: return null
        // SSID/password values include a three-byte header; avoid fragmented credential reads.
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN && connection.mtu < 67) 517 else null
    }

    @SuppressLint("MissingPermission")
    override fun initiateMtuRequest(identifier: String, mtu: Int): Boolean {
        val connection = registry.entry(identifier.uppercase()) ?: return false
        return connection.isActive && connection.gatt.requestMtu(mtu)
    }

    @SuppressLint("MissingPermission")
    override fun initiateReadRssi(identifier: String): Boolean {
        val connection = registry.entry(identifier.uppercase()) ?: return false
        return connection.isActive && connection.gatt.readRemoteRssi()
    }

    override fun hasCharacteristic(identifier: String, characteristicUuid: String): Boolean {
        val connection = registry.entry(identifier.uppercase()) ?: return false
        return findCharacteristic(connection.gatt, characteristicUuid) != null
    }

    override fun supportsWriteWithResponse(
        identifier: String,
        characteristicUuid: String
    ): Boolean {
        val connection = registry.entry(identifier.uppercase()) ?: return false
        val characteristic = findCharacteristic(connection.gatt, characteristicUuid) ?: return false
        return characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
    }

    override fun initiateWrite(
        identifier: String,
        characteristicUuid: String,
        value: ByteArray,
    ): Boolean {
        val connection = registry.entry(identifier.uppercase()) ?: return false
        // The GATT handle survives disconnects for autoConnect; it is not proof of a live link.
        if (!connection.isActive) return false
        val characteristic = findCharacteristic(connection.gatt, characteristicUuid) ?: return false
        return writeCharacteristicCompat(connection.gatt, characteristic, value)
    }

    @SuppressLint("MissingPermission")
    override fun initiateRead(identifier: String, characteristicUuid: String): Boolean {
        val connection = registry.entry(identifier.uppercase()) ?: return false
        if (!connection.isActive) return false
        val characteristic = findCharacteristic(connection.gatt, characteristicUuid) ?: return false
        // The read forces link re-encryption on a fresh reconnect: a long gap
        // until the read response is the stack encrypting, not the queue
        Timber.d("Issuing read of %s to the stack", characteristicUuid)
        return connection.gatt.readCharacteristic(characteristic)
    }

    @SuppressLint("MissingPermission")
    override fun initiateSubscribe(
        identifier: String,
        characteristicUuid: String,
        enable: Boolean,
    ): Boolean {
        val address = identifier.uppercase()
        val connection = registry.entry(address) ?: return false
        if (!connection.isActive) return false
        val characteristic = findCharacteristic(connection.gatt, characteristicUuid) ?: return false
        val descriptor =
            characteristic.getDescriptor(UUID.fromString(SonyBluetoothConstants.CCCD_UUID))
                ?: return false

        if (!connection.gatt.setCharacteristicNotification(characteristic, enable)) return false

        pendingSubscribeEnable["$address:${characteristicUuid.lowercase()}"] = enable
        val descriptorValue = if (enable) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }
        return writeDescriptorCompat(connection.gatt, descriptor, descriptorValue)
    }

    @SuppressLint("MissingPermission")
    override fun initiateDiscoverServices(identifier: String): Boolean {
        val connection = registry.entry(identifier.uppercase()) ?: return false
        if (!connection.isActive) return false
        return connection.gatt.discoverServices()
    }

    // ---------------------------------------------------------------------------
    // GATT callback — feeds the event channel only, from binder threads
    // ---------------------------------------------------------------------------

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address.uppercase()

            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                Timber.i("Connected to device with status %d", status)
                // The event is emitted inside the registry's monitor: it can never land after
                // the synthetic Disconnected of a retire/reconnect that races this callback.
                registry.update(gatt, address) {
                    it.isActive = true
                    eventChannel.trySend(BleTransportEvent.Connected(address))
                } ?: return
                // Discovery + handshake are dozens of sequential ATT round trips,
                // each costing one connection interval (~50ms at the default).
                // Request the short interval for setup; relaxConnection() drops
                // it again after the handshake to save battery.
                if (!gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)) {
                    Timber.w("Could not request high connection priority for %s", address)
                }
                return
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                registry.update(gatt, address) {
                    it.isActive = false
                    it.mtu = 23
                    it.disconnectedReported = true
                    if (status == 19 || status == 8 || status == 0) {
                        Timber.i("Device disconnected in callback with status: $status")
                    } else {
                        Timber.e("An error happened: $status")
                    }
                    eventChannel.trySend(BleTransportEvent.Disconnected(address, status))
                }
                return
            }

            Timber.d("Ignoring connection callback with status=$status and state=$newState")
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Timber.i("MTU changed for %s: mtu=%d status=%d", gatt.device.address, mtu, status)
            val address = gatt.device.address.uppercase()
            registry.update(gatt, address) {
                if (status == BluetoothGatt.GATT_SUCCESS) it.mtu = mtu
                eventChannel.trySend(BleTransportEvent.MtuChanged(address, mtu, statusOf(status)))
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            val address = gatt.device.address.uppercase()
            registry.update(gatt, address) {
                eventChannel.trySend(BleTransportEvent.RssiRead(address, rssi, statusOf(status)))
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Timber.i("Services discovered for ${gatt.device.address} with status $status")
            registry.update(gatt, gatt.device.address.uppercase()) {
                eventChannel.trySend(
                    BleTransportEvent.ServicesDiscovered(
                        gatt.device.address.uppercase(),
                        status == BluetoothGatt.GATT_SUCCESS,
                    )
                )
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic?,
            status: Int,
        ) {
            val uuid = characteristic?.uuid?.toString() ?: return
            registry.update(gatt, gatt.device.address.uppercase()) {
                eventChannel.trySend(
                    BleTransportEvent.CharacteristicWritten(
                        gatt.device.address.uppercase(),
                        uuid,
                        statusOf(status),
                    )
                )
            }
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                emitCharacteristicRead(
                    gatt,
                    characteristic,
                    characteristic.value ?: ByteArray(0),
                    status
                )
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            emitCharacteristicRead(gatt, characteristic, value, status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            Timber.d("Characteristic changed: %s, bytes=%d", characteristic.uuid, value.size)
            registry.update(gatt, gatt.device.address.uppercase()) {
                eventChannel.trySend(
                    BleTransportEvent.CharacteristicChanged(
                        gatt.device.address.uppercase(),
                        characteristic.uuid.toString(),
                        value,
                    )
                )
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            val address = gatt.device.address.uppercase()
            val characteristicUuid = descriptor.characteristic.uuid.toString()
            Timber.d("Descriptor write completed for ${gatt.device.address} with status $status")
            registry.update(gatt, address) {
                val enabled =
                    pendingSubscribeEnable.remove("$address:${characteristicUuid.lowercase()}")
                        ?: true
                eventChannel.trySend(
                    BleTransportEvent.SubscriptionChanged(
                        address,
                        characteristicUuid,
                        enabled,
                        statusOf(status),
                    )
                )
            }
        }

        private fun emitCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            Timber.d("Read response for %s with status %d", characteristic.uuid, status)
            registry.update(gatt, gatt.device.address.uppercase()) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Timber.w(
                        "BLE read failed: characteristic=%s status=%d mtu=%d", characteristic.uuid,
                        status, it.mtu
                    )
                }
                eventChannel.trySend(
                    BleTransportEvent.CharacteristicRead(
                        gatt.device.address.uppercase(),
                        characteristic.uuid.toString(),
                        value,
                        statusOf(status),
                    )
                )
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers (write compat absorbed from the former BluetoothGattUtils)
    // ---------------------------------------------------------------------------

    private fun statusOf(status: Int): BleOperationStatus = when (status) {
        BluetoothGatt.GATT_SUCCESS -> BleOperationStatus.Success

        SonyBluetoothConstants.ATT_ERROR_INSUFFICIENT_AUTHENTICATION,
        SonyBluetoothConstants.ATT_ERROR_INSUFFICIENT_ENCRYPTION,
            -> BleOperationStatus.AuthError

        else -> BleOperationStatus.Failure
    }

    private fun findCharacteristic(
        gatt: BluetoothGatt,
        uuid: String,
    ): BluetoothGattCharacteristic? {
        val target = UUID.fromString(uuid)
        return gatt.services?.flatMap { it.characteristics }?.find { it.uuid == target }
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristicCompat(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeCharacteristic(characteristic, value, writeType)
            // The link can drop after the active check but before Android accepts the write.
            if (result == ERROR_DEVICE_NOT_CONNECTED) {
                Timber.d("Characteristic write not initiated because device disconnected")
                return false
            }
            // 201 == device busy (spams the log), cause unknown yet
            if (result != 0 && result != 201) {
                Timber.e("Writing characteristic failed. Result: $result")
                false
            } else {
                Timber.d("Characteristic written successfully (API 33+)")
                true
            }
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            val result = gatt.writeCharacteristic(characteristic)
            if (!result) {
                Timber.e("Writing characteristic failed (legacy API)")
            }
            result
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeDescriptorCompat(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeDescriptor(descriptor, value)
            if (result == ERROR_DEVICE_NOT_CONNECTED) {
                Timber.d("Descriptor write not initiated because device disconnected")
                return false
            }
            if (result != 0 && result != 201) {
                Timber.e("Writing descriptor failed. Result: $result")
                false
            } else {
                Timber.d("Descriptor written successfully (API 33+)")
                true
            }
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            val result = gatt.writeDescriptor(descriptor)
            if (!result) {
                Timber.e("Writing descriptor failed (legacy API)")
            }
            result
        }
    }
}
