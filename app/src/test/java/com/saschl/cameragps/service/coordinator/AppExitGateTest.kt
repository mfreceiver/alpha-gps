package com.saschl.cameragps.service.coordinator

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppExitGateTest {

    private fun gate() = AppExitGate(CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `the session owner itself may schedule its teardown`() = runBlocking {
        val g = gate()
        val activity = Any()
        g.beginSession(activity)
        // Regression: beginSession used to leave an anonymous session token behind,
        // rejecting the owning activity's own onDestroy teardown.
        val done = CompletableDeferred<Unit>()
        g.scheduleExit(owner = activity, teardown = { done.complete(Unit) }, afterwards = {})
        assertTrue(done.isCompleted)
    }

    @Test
    fun `teardown from a superseded activity session is rejected`() = runBlocking {
        val g = gate()
        val oldActivity = Any()
        val newActivity = Any()
        g.beginSession(oldActivity)

        // Old activity finishes, new one is created…
        g.beginSession(newActivity)

        // …then the old activity's late onDestroy still asks for a teardown.
        var ran = false
        g.scheduleExit(owner = oldActivity, teardown = { ran = true }, afterwards = {})
        assertFalse("stale owner must not run a teardown", ran)
    }

    @Test
    fun `a new session cancels a teardown the old activity already started`() = runBlocking {
        withTimeout(10_000) {
            val g = gate()
            val oldActivity = Any()
            val newActivity = Any()
            g.beginSession(oldActivity)

            val inTeardown = CompletableDeferred<Unit>()
            var afterwardsRan = false
            g.scheduleExit(
                owner = oldActivity,
                teardown = { inTeardown.complete(Unit); hang() },
                afterwards = { afterwardsRan = true },
            )
            inTeardown.await()

            g.beginSession(newActivity)

            assertFalse("afterwards must not run after the new session began", afterwardsRan)
        }
    }

    @Test
    fun `a connect command during the exit window voids the pending exit`() = runBlocking {
        withTimeout(10_000) {
            val g = gate()
            val inTeardown = CompletableDeferred<Unit>()
            var afterwardsRan = false
            g.scheduleExit(
                owner = null,
                teardown = { inTeardown.complete(Unit); hang() },
                afterwards = { afterwardsRan = true },
            )
            inTeardown.await()

            // The valid connect command arrives while the teardown waits on Wi-Fi close.
            g.cancelPendingExit()

            assertFalse("afterwards (service stop) must not run after a connect cancelled the exit", afterwardsRan)
        }
    }

    @Test
    fun `an unowned task-removal exit after a connect still runs`() = runBlocking {
        withTimeout(10_000) {
            val g = gate()
            g.cancelPendingExit() // connect command

            val calls = mutableListOf<String>()
            g.scheduleExit(
                owner = null,
                teardown = { calls += "teardown" },
                afterwards = { calls += "afterwards" },
            )
            assertEquals(listOf("teardown", "afterwards"), calls)
        }
    }

    private suspend fun hang() = CompletableDeferred<Unit>().await()
}
