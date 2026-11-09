package com.saschl.cameragps.service.coordinator

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AppExitCoordinatorTest {
    private val calls = mutableListOf<String>()

    private fun coordinator(
        closeWifi: suspend () -> Unit = { calls += "wifi" },
        shutdownSessions: () -> Unit = { calls += "sessions" },
        disconnectBle: () -> Unit = { calls += "ble" },
        timeoutMs: Long = 1_000,
    ) = AppExitCoordinator(closeWifi, shutdownSessions, disconnectBle, timeoutMs)

    @Test
    fun `exit closes wifi first then sessions then every GATT connection`() = runBlocking {
        coordinator().disconnectEverything()
        assertEquals(listOf("wifi", "sessions", "ble"), calls)
    }

    @Test
    fun `failing wifi teardown still closes BLE`() = runBlocking {
        coordinator(closeWifi = { calls += "wifi"; error("boom") }).disconnectEverything()
        assertEquals(listOf("wifi", "sessions", "ble"), calls)
    }

    @Test
    fun `hanging wifi teardown is bounded and BLE is still closed`() = runBlocking {
        coordinator(closeWifi = { calls += "wifi"; delay(60_000) }, timeoutMs = 50).disconnectEverything()
        assertEquals(listOf("wifi", "sessions", "ble"), calls)
    }

    @Test
    fun `failing session shutdown does not skip the GATT close`() = runBlocking {
        coordinator(shutdownSessions = { calls += "sessions"; error("boom") }).disconnectEverything()
        assertEquals(listOf("wifi", "sessions", "ble"), calls)
    }

    @Test
    fun `cancelled exit (quick relaunch) does not close BLE`() = runBlocking {
        val inWifi = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator(closeWifi = { inWifi.complete(Unit); delay(60_000) }).disconnectEverything()
        }
        inWifi.await()
        job.cancel()
        job.join()
        assertEquals(emptyList<String>(), calls)
    }
}
