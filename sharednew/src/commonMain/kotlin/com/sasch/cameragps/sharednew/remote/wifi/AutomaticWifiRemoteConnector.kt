package com.sasch.cameragps.sharednew.remote.wifi

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal sealed interface CameraNetworkEvent<out N> {
    data class Available<N>(val network: N) : CameraNetworkEvent<N>
    data class Ready<N>(val network: N, val host: String) : CameraNetworkEvent<N>
    data class Lost<N>(val network: N) : CameraNetworkEvent<N>
    data object Unavailable : CameraNetworkEvent<Nothing>
}

internal interface CameraNetworkRequest<N> {
    val events: Flow<CameraNetworkEvent<N>>
    fun close()
}

/**
 * Platform radio state, injected so the common logic stays testable: checked at the very start of
 * an automatic attempt, before any BLE traffic wakes the camera's Wi-Fi. Defaults keep every
 * precondition satisfied, so a null or default implementation never blocks the flow.
 */
internal interface WifiPreconditions {
    suspend fun isWifiEnabled(): Boolean = true

    /** Android <= 32 refuses Wi-Fi joins while location services are off (see AndroidCameraNetworkRequest). */
    suspend fun isLocationEnabled(): Boolean = true
}

/** Owns the OS network request until the camera session has finished releasing its controls. */
internal class AutomaticWifiRemoteConnector<N>(
    private val prepare: suspend (String) -> CameraWifiCredentials?,
    private val request: (CameraWifiCredentials) -> CameraNetworkRequest<N>,
    private val openCamera: suspend (N, String, CoroutineScope) -> WifiRemoteConnection,
    private val joinTimeoutMs: Long = 60_000,
    private val preconditions: WifiPreconditions? = null,
) : WifiAutomaticConnector {
    /** The controller checks these before publishing a phase or claiming BLE controls. */
    override val phonePreconditions: WifiPreconditions? get() = preconditions

    override suspend fun open(identifier: String, scope: CoroutineScope,
                              onPhase: (WifiRemotePhase) -> Unit): WifiRemoteConnection {
        // Fail before any BLE wake-up or phase is published: a disabled phone Wi-Fi makes the
        // whole attempt pointless, so the camera must not be needlessly woken first.
        if (preconditions?.isWifiEnabled() == false) {
            throw WifiRemoteConnectException(WifiRemoteFailure.WifiDisabled)
        }
        if (preconditions?.isLocationEnabled() == false) {
            throw WifiRemoteConnectException(WifiRemoteFailure.LocationServicesRequired)
        }
        onPhase(WifiRemotePhase.PreparingCamera)
        val requested = request(prepare(identifier)
            ?: throw WifiRemoteConnectException(WifiRemoteFailure.CameraSetupFailed))
        var connection: WifiRemoteConnection? = null
        try {
            onPhase(WifiRemotePhase.AwaitingNetworkApproval)
            var available = false
            val ready = withTimeoutOrNull(joinTimeoutMs) {
                requested.events.first { event ->
                    when (event) {
                        is CameraNetworkEvent.Available -> {
                            available = true
                            onPhase(WifiRemotePhase.JoiningNetwork)
                            false
                        }
                        is CameraNetworkEvent.Ready -> true
                        is CameraNetworkEvent.Lost -> throw WifiRemoteConnectException(WifiRemoteFailure.NetworkLost)
                        CameraNetworkEvent.Unavailable -> throw WifiRemoteConnectException(WifiRemoteFailure.NetworkJoinFailed)
                    }
                } as CameraNetworkEvent.Ready<N>
            } ?: throw WifiRemoteConnectException(if (available) WifiRemoteFailure.CameraAddressUnavailable
                else WifiRemoteFailure.NetworkJoinFailed)
            onPhase(WifiRemotePhase.OpeningSession)
            val opened = openCamera(ready.network, ready.host, scope)
            connection = opened
            return object : WifiRemoteConnection by opened {
                override val lost = merge(opened.lost, requested.events.filter {
                    it == CameraNetworkEvent.Unavailable || it is CameraNetworkEvent.Lost && it.network == ready.network
                }.map { Unit })
                override suspend fun close() {
                    try { opened.close() } finally { requested.close() }
                }
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                try { connection?.close() } finally { requested.close() }
            }
            throw failure
        }
    }
}
