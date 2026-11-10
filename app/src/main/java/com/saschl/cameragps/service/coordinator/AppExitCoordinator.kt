package com.saschl.cameragps.service.coordinator

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Explicit exit only ends Wi-Fi/browsing, even during a user download. BLE/GPS and the
 * foreground service keep running. Deliberately has no BLE or service-shutdown capability:
 * neither a failed nor a timed-out Wi-Fi close may turn UI exit into a Bluetooth disconnect.
 */
class AppExitCoordinator(
    private val closeWifi: suspend () -> Unit,
    private val wifiTimeoutMs: Long = DEFAULT_WIFI_TIMEOUT_MS,
) {
    suspend fun disconnectEverything() {
        val finished = try {
            withTimeoutOrNull(wifiTimeoutMs) { closeWifi() } != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Wi-Fi teardown failed on exit; BLE/GPS remain active")
            true
        }
        if (!finished) Timber.w("Wi-Fi teardown timed out on exit; BLE/GPS remain active")
    }

    companion object {
        const val DEFAULT_WIFI_TIMEOUT_MS = 3_000L
    }
}
