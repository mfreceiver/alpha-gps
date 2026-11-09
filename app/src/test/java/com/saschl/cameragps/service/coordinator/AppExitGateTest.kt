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
    fun `the current owner may exit after a connect cancelled a pending exit`() = runBlocking {
        // Regression: a plain Connect command used to call cancelPendingExit(),
        // which overwrote the session token; the same activity's later real exit
        // (back at the root page) was then silently rejected and never disconnected.
        val g = gate()
        val activity = Any()
        g.beginSession(activity)
        g.cancelPendingExit() // connect command while the activity is alive

        val done = CompletableDeferred<Unit>()
        g.scheduleExit(owner = activity, teardown = { done.complete(Unit) }, afterwards = {})
        assertTrue("the still-alive owner must keep exit authority after a connect", done.isCompleted)
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

    @Test
    fun `a late owned onDestroy after its exit was cancelled by a connect is rejected`() =
        runBlocking {
            // Counterexample this pins down: beginSession(A) → task swiped away,
            // unowned exit hangs in the Wi-Fi close → a connect command cancels it
            // (session still A) → the SAME exit flow's late A.onDestroy passes the
            // owner===session check and must not rebuild the teardown / run
            // afterwards, which would kill the connection the connect just made.
            withTimeout(10_000) {
                val g = gate()
                val activity = Any()
                g.beginSession(activity)

                val inTeardown = CompletableDeferred<Unit>()
                g.scheduleExit(
                    owner = null,
                    teardown = { inTeardown.complete(Unit); hang() },
                    afterwards = {},
                )
                inTeardown.await()

                g.cancelPendingExit() // the connect command

                var ran = false
                g.scheduleExit(
                    owner = activity,
                    teardown = { ran = true },
                    afterwards = { ran = true },
                )
                assertFalse("a late callback of the cancelled exit flow must not run", ran)
            }
        }

    @Test
    fun `a retired generation does not block the next session's exits`() = runBlocking {
        // The retired exit generation must not leak into a new activity session.
        withTimeout(10_000) {
            val g = gate()
            val oldActivity = Any()
            val newActivity = Any()
            g.beginSession(oldActivity)
            val inTeardown = CompletableDeferred<Unit>()
            g.scheduleExit(
                owner = null,
                teardown = { inTeardown.complete(Unit); hang() },
                afterwards = {},
            )
            inTeardown.await()
            g.cancelPendingExit()

            g.beginSession(newActivity)

            val done = CompletableDeferred<Unit>()
            g.scheduleExit(owner = newActivity, teardown = { done.complete(Unit) }, afterwards = {})
            assertTrue("a new session's owner exits freely", done.isCompleted)
        }
    }

    @Test
    fun `an unowned task removal after a cancelled exit still runs`() = runBlocking {
        // A genuine new task removal after a connect cancelled an earlier exit
        // must still disconnect (onTaskRemoved fires once per real removal, so
        // it is never a late duplicate of the cancelled flow).
        withTimeout(10_000) {
            val g = gate()
            val inTeardown = CompletableDeferred<Unit>()
            g.scheduleExit(
                owner = null,
                teardown = { inTeardown.complete(Unit); hang() },
                afterwards = {},
            )
            inTeardown.await()
            g.cancelPendingExit()

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
