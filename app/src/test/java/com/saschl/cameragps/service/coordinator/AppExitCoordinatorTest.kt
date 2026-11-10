package com.saschl.cameragps.service.coordinator

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppExitCoordinatorTest {
    private val calls = mutableListOf<String>()

    private fun coordinator(
        closeWifi: suspend () -> Unit = { calls += "wifi" },
        timeoutMs: Long = 1_000,
    ) = AppExitCoordinator(closeWifi, timeoutMs)

    // The old sessions/GATT assertions intentionally change: exit no longer has either
    // capability. Manual disconnect still has its own DeviceDisconnectCoordinator tests.
    @Test
    fun `exit closes only wifi without a BLE or service shutdown capability`() = runBlocking {
        coordinator().disconnectEverything()
        assertEquals(listOf("wifi"), calls)
    }

    @Test
    fun `failing wifi teardown does not trigger any other shutdown`() = runBlocking {
        coordinator(closeWifi = { calls += "wifi"; error("boom") }).disconnectEverything()
        assertEquals(listOf("wifi"), calls)
    }

    @Test
    fun `hanging wifi teardown is cancelled within the bound without a BLE fallback`() = runBlocking {
        coordinator(closeWifi = {
            calls += "wifi"
            try { delay(60_000) } finally { calls += "wifi-cancel" }
        }, timeoutMs = 50).disconnectEverything()
        assertEquals(listOf("wifi", "wifi-cancel"), calls)
    }

    @Test
    fun `quick relaunch retires the wifi exit without affecting a new owner`() = runBlocking {
        val gate = AppExitGate(this)
        val oldOwner = Any()
        val newOwner = Any()
        val inWifi = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        gate.beginSession(oldOwner)
        gate.scheduleExit(oldOwner, {
            coordinator(closeWifi = {
                inWifi.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }).disconnectEverything()
        }, { calls += "old-exit-finished" })
        inWifi.await()
        gate.beginSession(newOwner)
        cancelled.await()
        gate.scheduleExit(oldOwner, { calls += "new-wifi-closed" }, {})
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `cancelled exit propagates cancellation rather than finishing teardown`() = runBlocking {
        val inWifi = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator(closeWifi = { inWifi.complete(Unit); awaitCancellation() }).disconnectEverything()
            calls += "exit-finished"
        }
        inWifi.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(emptyList<String>(), calls)
    }
}
