package com.saschl.cameragps.service.coordinator

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionDemandGateTest {
    @Test
    fun `no handles and no keep-active cameras allow the service to stop`() = runBlocking {
        val gate = Fixture().gate

        assertTrue(gate.mayStopService())
    }

    @Test
    fun `a pending or autoConnect-reconnecting camera of another device blocks shutdown`() =
        runBlocking {
            // Camera A was just disconnected by hand; camera B's GATT handle exists but has
            // not resumed yet (isActive=false, e.g. still connecting or parked on autoConnect).
            val fixture = Fixture()
            fixture.handles += 1

            assertFalse(fixture.gate.mayStopService())
        }

    @Test
    fun `a keep-active camera blocks shutdown`() = runBlocking {
        val fixture = Fixture()
        fixture.keepActive = true

        assertFalse(fixture.gate.mayStopService())
    }

    @Test
    fun `connection demand appearing during the keep-active lookup prevents shutdown`() =
        runBlocking {
            // Camera B's pending handle registers while the keep-active DAO query suspends.
            val fixture = Fixture()
            val resumeLookup = CompletableDeferred<Unit>()
            fixture.beforeKeepActiveCheck = { resumeLookup.await() }
            var decision: Boolean? = null
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                decision = fixture.gate.mayStopService()
            }

            fixture.handles += 1
            resumeLookup.complete(Unit)
            job.join()

            // The re-check after the suspend read must see B's handle and keep the service.
            assertFalse(decision!!)
        }

    private class Fixture {
        val handles = mutableSetOf<Int>()
        var keepActive = false
        var beforeKeepActiveCheck: (suspend () -> Unit)? = null
        val gate = ConnectionDemandGate(
            hasConnectionDemand = { handles.isNotEmpty() },
            hasKeepActiveDevices = {
                beforeKeepActiveCheck?.invoke()
                keepActive
            },
        )
    }
}
