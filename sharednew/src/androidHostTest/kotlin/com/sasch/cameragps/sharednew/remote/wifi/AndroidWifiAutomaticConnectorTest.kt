package com.sasch.cameragps.sharednew.remote.wifi

import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The production wiring ([AndroidWifiAutomaticConnector], used by `createAndroidWifiRemoteController`)
 * must hand the controller the real [WifiPreconditions]; a wrapper that drops them silently
 * re-introduces "PreparingCamera + BLE claim first, radio check later".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidWifiAutomaticConnectorTest : WifiLoggingTest() {
    private class NetworkRequest : CameraNetworkRequest<String> {
        val channel = Channel<CameraNetworkEvent<String>>(Channel.UNLIMITED)
        override val events = channel.receiveAsFlow()
        override fun close() {}
    }

    @Test
    fun theProductionWrapperForwardsThePhonePreconditionsOfItsDelegate() {
        val radio = object : WifiPreconditions {}
        val automatic = AutomaticWifiRemoteConnector<String>(
            { null }, { NetworkRequest() }, { _, _, _ -> error("unused") }, preconditions = radio,
        )
        assertSame(radio, AndroidWifiAutomaticConnector(automatic).phonePreconditions)
        assertNull(AndroidWifiAutomaticConnector(testAutomaticConnector { _, _, _ -> error("unused") }).phonePreconditions)
    }

    @Test
    fun aDisabledWifiBehindTheProductionWrapperNeverPublishesPreparingCameraNorClaimsBle() = runTest {
        val registry = CameraSessionRegistry()
        val phases = mutableListOf<WifiRemotePhase>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            registry.sessions.collect { sessions ->
                sessions.forEach { (_, session) -> phases += session.wifiRemote.phase }
            }
        }
        var claims = 0
        var releases = 0
        var prepared = 0
        val automatic = AutomaticWifiRemoteConnector<String>(
            { prepared++; CameraWifiCredentials("DIRECT-x", "y", null) },
            { error("no network request may be built") },
            { _, _, _ -> error("no camera session may open") },
            preconditions = object : WifiPreconditions {
                override suspend fun isWifiEnabled() = false
            },
        )
        val controller = WifiRemoteController(
            backgroundScope, registry, { _, _ -> error("manual connector unused") },
            { claims++ }, { releases++ },
            automaticConnector = AndroidWifiAutomaticConnector(automatic),
        )
        controller.connectAutomatically("camera")
        runCurrent()
        assertEquals(WifiRemotePhase.Failed, registry.get("camera")!!.wifiRemote.phase)
        assertEquals(WifiRemoteFailure.WifiDisabled, registry.get("camera")!!.wifiRemote.failure)
        assertFalse(WifiRemotePhase.PreparingCamera in phases, "phases seen: $phases")
        assertEquals(0, claims)
        assertEquals(0, releases)
        assertEquals(0, prepared, "the camera's Wi-Fi is never woken")
        assertNull(controller.owner.value)
    }

    @Test
    fun aMissingPermissionInsideTheDelegateMapsToNetworkPermissionDenied() = runTest {
        val wrapper = AndroidWifiAutomaticConnector(testAutomaticConnector { _, _, _ ->
            throw SecurityException("no NEARBY_WIFI_DEVICES")
        })
        val failure = assertFailsWith<WifiRemoteConnectException> {
            wrapper.open("camera", backgroundScope) {}
        }
        assertEquals(WifiRemoteFailure.NetworkPermissionDenied, failure.failure)
    }
}
