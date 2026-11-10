package com.sasch.cameragps.sharednew.remote.wifi

import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.net.InetAddress

fun createAndroidWifiRemoteController(
    context: Context, scope: CoroutineScope,
    orchestrator: CameraSessionOrchestrator,
    browseOnly: () -> Boolean = { true },
): WifiRemoteController {
    val connector = AndroidWifiRemoteConnector(context.applicationContext, browseOnly)
    val automatic = AutomaticWifiRemoteConnector(
        orchestrator::prepareCameraWifi,
        { credentials -> AndroidCameraNetworkRequest(context.applicationContext, credentials) },
        connector::openOnNetwork,
        preconditions = AndroidWifiPreconditions(context),
    )
    return WifiRemoteController(scope, orchestrator.registry, connector,
        orchestrator::claimWifiControls, orchestrator::releaseWifiControls,
        WifiAutomaticConnector { id, sessionScope, phase ->
            try { automatic.open(id, sessionScope, phase) }
            catch (_: SecurityException) { throw WifiRemoteConnectException(WifiRemoteFailure.NetworkPermissionDenied) }
        },
        onBatteryRead = orchestrator.liveStatus::onBattery,
    )
}

/**
 * Phone-side radio state for the pre-BLE preflight. Mirrors the guards of
 * [AndroidCameraNetworkRequest] (which stay in place as the late fallback) but runs before the
 * camera is woken over BLE, so a disabled Wi-Fi fails in milliseconds instead of after the wake-up.
 */
private class AndroidWifiPreconditions(context: Context) : WifiPreconditions {
    private val appContext = context.applicationContext
    override suspend fun isWifiEnabled(): Boolean =
        appContext.getSystemService(WifiManager::class.java).isWifiEnabled
    override suspend fun isLocationEnabled(): Boolean =
        Build.VERSION.SDK_INT > 32 ||
            appContext.getSystemService(LocationManager::class.java).isLocationEnabled
}

private class AndroidWifiRemoteConnector(
    context: Context,
    private val browseOnly: () -> Boolean,
) : WifiRemoteConnector {
    override val supportsAddressDiscovery = true
    private val imageStore = AndroidCameraImageStore(context)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override suspend fun open(host: String, scope: CoroutineScope): WifiRemoteConnection {
        return try { openCamera(host, scope) }
        catch (_: SecurityException) { throw WifiRemoteConnectException(WifiRemoteFailure.NetworkPermissionDenied) }
    }

    private suspend fun openCamera(host: String, scope: CoroutineScope): WifiRemoteConnection {
        val candidates = connectivity.allNetworks.filter { network ->
            val caps = connectivity.getNetworkCapabilities(network)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
        if (candidates.size != 1) throw WifiRemoteConnectException(WifiRemoteFailure.JoinCameraWifi)
        val network = candidates.single()
        val cameraHost = resolveCameraHost(host) {
            val links = connectivity.getLinkProperties(network)
                ?: throw WifiRemoteConnectException(WifiRemoteFailure.NetworkLost)
            cameraGateway(links)
        }
        return openOnNetwork(network, cameraHost, scope)
    }

    suspend fun openOnNetwork(network: Network, host: String, scope: CoroutineScope): WifiRemoteConnection {
        val address = InetAddress.getByName(host) // Validated literal; never performs a hostname lookup.
        val links = connectivity.getLinkProperties(network)
            ?: throw WifiRemoteConnectException(WifiRemoteFailure.NetworkLost)
        if (links.linkAddresses.none { link ->
                ipPrefixContains(link.address, link.prefixLength, address)
            } || links.linkAddresses.any { it.address == address }) {
            throw WifiRemoteConnectException(WifiRemoteFailure.JoinCameraWifi)
        }
        val losses = Channel<Unit>(Channel.CONFLATED)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(lost: Network) { if (lost == network) losses.trySend(Unit) }
        }
        connectivity.registerNetworkCallback(NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback)
        if (connectivity.getLinkProperties(network) == null) {
            connectivity.unregisterNetworkCallback(callback)
            losses.close()
            throw WifiRemoteConnectException(WifiRemoteFailure.NetworkLost)
        }
        return openSonyWifiSession(
            AndroidPtpIpConnectionFactory(network, host), scope, host,
            AndroidCameraHttpTransport(network.socketFactory), imageStore,
            ::decodeAndroidCameraThumbnail, ::decodeAndroidCameraPreview, losses.receiveAsFlow(),
            releaseNetwork = { connectivity.unregisterNetworkCallback(callback); losses.close() },
            browseOnly = browseOnly(),
        )
    }
}
