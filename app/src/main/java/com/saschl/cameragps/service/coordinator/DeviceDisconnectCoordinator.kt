package com.saschl.cameragps.service.coordinator

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * A user's manual "disconnect" of one camera: the Wi-Fi session that belongs to it is closed first
 * (it needs the live BLE link to switch the camera's Wi-Fi off), bounded by [wifiTimeoutMs], then
 * only that camera's GATT link is really closed. A hanging or failing Wi-Fi teardown never leaves
 * the link open, and other cameras are never touched.
 */
class DeviceDisconnectCoordinator(
    private val closeWifi: suspend (String) -> Unit,
    private val closeLink: (String) -> Boolean,
    private val wifiTimeoutMs: Long = AppExitCoordinator.DEFAULT_WIFI_TIMEOUT_MS,
) {
    /** @return true when a link existed and was closed. */
    suspend fun disconnect(address: String): Boolean {
        try {
            if (withTimeoutOrNull(wifiTimeoutMs) { closeWifi(address) } == null) {
                Timber.w("Wi-Fi teardown timed out for $address; closing BLE anyway")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Wi-Fi teardown failed for $address; closing BLE anyway")
        }
        return runCatching { closeLink(address) }
            .onFailure { Timber.w(it, "GATT close failed for $address") }
            .getOrDefault(false)
    }
}

/**
 * Whether an automatic or explicit Connect may proceed, from the stored "enabled" flag. A camera
 * the user manually disconnected is disabled and stays so until the user connects it again; an
 * unknown camera (no row yet) is not blocked.
 */
fun isConnectAllowed(enabledFlag: Boolean?): Boolean = enabledFlag != false
