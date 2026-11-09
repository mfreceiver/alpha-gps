package com.saschl.cameragps.service.coordinator

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * "Leaving the app means disconnecting the camera": closes the Wi-Fi photo session
 * first (it needs the live BLE session to ask the camera to switch its Wi-Fi off),
 * then tears down the BLE sessions and closes every GATT connection.
 *
 * Every step is isolated: a Wi-Fi teardown that fails or hangs (bounded by
 * [wifiTimeoutMs]) must never leave the GATT connection open.
 */
class AppExitCoordinator(
    private val closeWifi: suspend () -> Unit,
    private val shutdownSessions: () -> Unit,
    private val disconnectBle: () -> Unit,
    private val wifiTimeoutMs: Long = DEFAULT_WIFI_TIMEOUT_MS,
) {
    suspend fun disconnectEverything() {
        val finished = try {
            withTimeoutOrNull(wifiTimeoutMs) { closeWifi() } != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Wi-Fi teardown failed on exit; closing BLE anyway")
            true
        }
        if (!finished) Timber.w("Wi-Fi teardown timed out on exit; closing BLE anyway")
        runCatching { shutdownSessions() }.onFailure { Timber.w(it, "Session shutdown failed on exit") }
        runCatching { disconnectBle() }.onFailure { Timber.w(it, "GATT disconnect failed on exit") }
    }

    companion object {
        const val DEFAULT_WIFI_TIMEOUT_MS = 3_000L
    }
}
