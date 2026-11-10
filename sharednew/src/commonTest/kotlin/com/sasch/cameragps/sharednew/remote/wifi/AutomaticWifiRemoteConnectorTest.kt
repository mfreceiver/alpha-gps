package com.sasch.cameragps.sharednew.remote.wifi

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticWifiRemoteConnectorTest {
    private class Request : CameraNetworkRequest<String> {
        val channel = Channel<CameraNetworkEvent<String>>(Channel.UNLIMITED)
        override val events = channel.receiveAsFlow()
        var closes = 0
        override fun close() { closes++; channel.close() }
        fun ready() {
            channel.trySend(CameraNetworkEvent.Available("camera-network"))
            channel.trySend(CameraNetworkEvent.Ready("camera-network", "192.168.122.1"))
        }
    }

    private class Connection(private val request: Request) : WifiRemoteConnection {
        override val cameraName = "Camera"
        override val canCapture = true
        override val images = emptyFlow<ImageBitmap>()
        override val lost = emptyFlow<Unit>()
        var closed = false
        override suspend fun capture() = WifiCaptureStatus.Captured
        override suspend fun close() {
            assertEquals(0, request.closes, "Keep the network until camera controls are released")
            closed = true
        }
    }

    private fun credentials() = CameraWifiCredentials("DIRECT-camera", "secret-pass", null)

    @Test
    fun keepsRequestedNetworkThroughSessionCleanupAndReportsLoss() = runTest {
        val request = Request().apply { ready() }
        val connection = Connection(request)
        val phases = mutableListOf<WifiRemotePhase>()
        val connector = AutomaticWifiRemoteConnector({ credentials() }, { request }, { network, host, _ ->
            assertEquals("camera-network", network)
            assertEquals("192.168.122.1", host)
            connection
        })
        val opened = connector.open("id", backgroundScope, phases::add)
        assertEquals(listOf(WifiRemotePhase.PreparingCamera, WifiRemotePhase.AwaitingNetworkApproval,
            WifiRemotePhase.JoiningNetwork, WifiRemotePhase.OpeningSession), phases)
        assertEquals(0, request.closes)
        request.channel.send(CameraNetworkEvent.Lost("camera-network"))
        opened.lost.first()
        opened.close()
        assertTrue(connection.closed)
        assertEquals(1, request.closes)
    }

    @Test
    fun cancellationDuringApprovalReleasesRequestWithoutOpeningSockets() = runTest {
        val request = Request()
        val connector = AutomaticWifiRemoteConnector({ credentials() }, { request }, { _, _, _ -> error("No network yet") })
        val attempt = launch { connector.open("id", backgroundScope) {} }
        runCurrent()
        attempt.cancelAndJoin()
        assertEquals(1, request.closes)
    }

    @Test
    fun cancellationDuringSessionOpeningAlsoReleasesRequest() = runTest {
        val request = Request().apply { ready() }
        val connector = AutomaticWifiRemoteConnector({ credentials() }, { request }, { _, _, _ -> awaitCancellation() })
        val attempt = launch { connector.open("id", backgroundScope) {} }
        runCurrent()
        attempt.cancelAndJoin()
        assertEquals(1, request.closes)
    }

    @Test
    fun deniedOrFailedJoinDoesNotOpenOrRetry() = runTest {
        val request = Request().apply { channel.trySend(CameraNetworkEvent.Unavailable) }
        val connector = AutomaticWifiRemoteConnector({ credentials() }, { request }, { _, _, _ -> error("No network") })
        val error = assertFailsWith<WifiRemoteConnectException> { connector.open("id", backgroundScope) {} }
        assertEquals(WifiRemoteFailure.NetworkJoinFailed, error.failure)
        assertEquals(1, request.closes)
    }

    @Test
    fun missingGatewayTimesOutAndReleasesNetwork() = runTest {
        val request = Request().apply { channel.trySend(CameraNetworkEvent.Available("network")) }
        val connector = AutomaticWifiRemoteConnector({ credentials() }, { request }, { _, _, _ -> error("No address") }, joinTimeoutMs = 100)
        val error = assertFailsWith<WifiRemoteConnectException> { connector.open("id", backgroundScope) {} }
        assertEquals(WifiRemoteFailure.CameraAddressUnavailable, error.failure)
        assertEquals(1, request.closes)
    }

    @Test
    fun failedBootstrapNeverRequestsNetwork() = runTest {
        val connector = AutomaticWifiRemoteConnector<String>({ null }, { error("No credentials") }, { _, _, _ -> error("No network") })
        val error = assertFailsWith<WifiRemoteConnectException> { connector.open("id", backgroundScope) {} }
        assertEquals(WifiRemoteFailure.CameraSetupFailed, error.failure)
    }

    private class Radio(var wifi: Boolean = true, var location: Boolean = true) : WifiPreconditions {
        override suspend fun isWifiEnabled(): Boolean = wifi
        override suspend fun isLocationEnabled(): Boolean = location
    }

    @Test
    fun disabledPhoneWifiFailsBeforeWakingTheCamera() = runTest {
        var prepared = 0
        val connector = AutomaticWifiRemoteConnector<String>(
            { prepared++; credentials() },
            { error("No network request") }, { _, _, _ -> error("No camera") },
            preconditions = Radio(wifi = false),
        )
        val phases = mutableListOf<WifiRemotePhase>()
        val error = assertFailsWith<WifiRemoteConnectException> {
            connector.open("id", backgroundScope, phases::add)
        }
        assertEquals(WifiRemoteFailure.WifiDisabled, error.failure)
        assertEquals(0, prepared, "The camera must not be woken over BLE when phone Wi-Fi is off")
        assertTrue(phases.isEmpty(), "No phase — not even PreparingCamera — is published")
    }

    @Test
    fun disabledLocationServicesFailsBeforeWakingTheCamera() = runTest {
        var prepared = 0
        val connector = AutomaticWifiRemoteConnector<String>(
            { prepared++; credentials() },
            { error("No network request") }, { _, _, _ -> error("No camera") },
            preconditions = Radio(location = false),
        )
        val error = assertFailsWith<WifiRemoteConnectException> { connector.open("id", backgroundScope) {} }
        assertEquals(WifiRemoteFailure.LocationServicesRequired, error.failure)
        assertEquals(0, prepared, "The camera must not be woken over BLE when location is required and off")
    }

    @Test
    fun satisfiedPreconditionsLeaveTheFlowUnchanged() = runTest {
        val request = Request().apply { ready() }
        val connection = Connection(request)
        var prepared = 0
        val phases = mutableListOf<WifiRemotePhase>()
        val connector = AutomaticWifiRemoteConnector(
            { prepared++; credentials() }, { request }, { _, _, _ -> connection },
            preconditions = Radio(),
        )
        val opened = connector.open("id", backgroundScope, phases::add)
        assertEquals(listOf(WifiRemotePhase.PreparingCamera, WifiRemotePhase.AwaitingNetworkApproval,
            WifiRemotePhase.JoiningNetwork, WifiRemotePhase.OpeningSession), phases)
        assertEquals(1, prepared)
        opened.close()
    }
}
