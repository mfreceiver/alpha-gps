package com.saschl.cameragps.service.coordinator

/**
 * Lifecycle policy for CDM presence loss. Calls must be serialized by the service's command mutex.
 *
 * A parked GATT handle (kept alive for `autoConnect` after a disconnect) is only a meaningful
 * connection demand while its camera may still come back on its own:
 *
 * - a camera whose "keep active" switch is on (or that never disappeared) keeps the service —
 *   and its handle, pending or parked alike — alive, so reconnecting is never mistaken for
 *   "no one connected";
 * - an absent, non-keep-active camera's parked handle is retired instead: without this, the
 *   handle would count as demand forever and a default (keep-active off) service could never
 *   stop itself after the camera left.
 */
class ServicePresenceCoordinator(
    private val hasConnectedDevices: () -> Boolean,
    private val hasAlwaysOnDevices: suspend () -> Boolean,
    /** `true` when the device has a GATT handle whose link is inactive (parked on autoConnect). */
    private val isHandleParked: (String) -> Boolean,
    /** `true` when the device's own "keep active" switch is on. */
    private val isDeviceKeepActive: suspend (String) -> Boolean,
    /**
     * Closes (retires) the device's parked handle; a no-op when the handle is active or gone.
     * A handle whose link never got a single connection-state callback still owes the session
     * above a synthetic Disconnected — retiring it ends that `Connecting` session too.
     */
    private val retireParkedHandle: (String) -> Unit,
    private val onShutdownRequested: () -> Unit,
) {
    private val disappearedDevices = mutableSetOf<String>()

    fun onDeviceAppeared(address: String) {
        disappearedDevices.remove(address.uppercase())
    }

    suspend fun onDeviceDisappeared(address: String) {
        val id = address.uppercase()
        disappearedDevices.add(id)
        stopIfDisconnectedAndAbsent(id)
    }

    suspend fun onDeviceDisconnected(address: String) {
        stopIfDisconnectedAndAbsent(address.uppercase())
    }

    private suspend fun stopIfDisconnectedAndAbsent(address: String) {
        // Intentionally retain the service for reconnection if CDM never reports disappearance.
        // TODO: Consider tracking Android 16+ BLE_DISAPPEARED and BT_DISCONNECTED separately,
        // retaining legacy handling on older Android and GATT state as the transmission authority.
        if (address !in disappearedDevices) return
        // Any keep-active camera keeps the service (and every handle) alive while it is away.
        if (hasAlwaysOnDevices()) return
        // Absent, non-keep-active cameras no longer demand their parked handles: retire them so
        // they stop counting as connection demand. Retirement re-checks inactivity atomically,
        // so a handle that reconnected while the database read suspended is never closed.
        disappearedDevices.toList().forEach { id ->
            if (isHandleParked(id) && !isDeviceKeepActive(id)) {
                retireParkedHandle(id)
            }
        }
        if (hasConnectedDevices()) return
        // GATT callbacks can update connection state while the database read suspends.
        if (!hasConnectedDevices()) {
            onShutdownRequested()
        }
    }
}
