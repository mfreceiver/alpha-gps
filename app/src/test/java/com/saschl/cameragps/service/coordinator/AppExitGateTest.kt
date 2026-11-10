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

            assertFalse("afterwards must not run after a connect cancelled the exit", afterwardsRan)
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
    fun `a late unowned onTaskRemoved after its owned exit was cancelled by a connect is rejected`() =
        runBlocking {
            // Counterexample this pins down: beginSession(A) → A.onDestroy (owned exit)
            // hangs in the Wi-Fi close → a connect command cancels it (retired, session
            // still A) → the SAME task removal's late onTaskRemoved must not rebuild
            // the teardown / run afterwards, which would kill the connection the
            // connect just made.
            withTimeout(10_000) {
                val g = gate()
                val activity = Any()
                g.beginSession(activity)

                val inTeardown = CompletableDeferred<Unit>()
                g.scheduleExit(
                    owner = activity,
                    teardown = { inTeardown.complete(Unit); hang() },
                    afterwards = {},
                )
                inTeardown.await()

                g.cancelPendingExit() // the connect command

                val calls = mutableListOf<String>()
                g.scheduleExit(
                    owner = null,
                    teardown = { calls += "teardown" },
                    afterwards = { calls += "afterwards" },
                )
                assertTrue(
                    "the same task removal's late onTaskRemoved must not rebuild the teardown",
                    calls.isEmpty(),
                )
            }
        }

    @Test
    fun `a late owned callback after a completed exit was retired by a connect is rejected`() =
        runBlocking {
            // beginSession(A) → A.onDestroy (owned exit) runs to completion →
            // a background connect retires the completed flow (session still
            // A). A late owned callback must not run: its owner already
            // entered the retired flow and is destroyed.
            withTimeout(10_000) {
                val g = gate()
                val activity = Any()
                g.beginSession(activity)

                val calls = mutableListOf<String>()
                g.scheduleExit(
                    owner = activity,
                    teardown = { calls += "teardown" },
                    afterwards = { calls += "afterwards" },
                )
                assertEquals(listOf("teardown", "afterwards"), calls)

                g.cancelPendingExit() // connect retires even a completed flow

                var ran = false
                g.scheduleExit(owner = activity, teardown = { ran = true }, afterwards = { ran = true })
                assertFalse("late owned callbacks of a retired completed flow must not run", ran)
            }
        }

    @Test
    fun `a real unowned task removal after a completed owned exit was retired by a connect still runs`() =
        runBlocking {
            // Counterexample this pins down: A.Back at the root page →
            // finish() → owned onDestroy exit runs teardown+afterwards to
            // completion → the Recents card is NOT auto-removed, the process
            // lives on, and a CDM-started service Connect calls
            // cancelPendingExit → the completed owned flow is retired. The
            // user then genuinely swipes the card away: onTaskRemoved is the
            // FIRST real removal delivery for that swipe, and the flow had
            // already used up its own removal's entries — so the teardown
            // must run and stop the just-reconnected session's FGS.
            withTimeout(10_000) {
                val g = gate()
                val activity = Any()
                g.beginSession(activity)

                val firstCalls = mutableListOf<String>()
                g.scheduleExit(
                    owner = activity,
                    teardown = { firstCalls += "teardown" },
                    afterwards = { firstCalls += "afterwards" },
                )
                assertEquals(listOf("teardown", "afterwards"), firstCalls)

                g.cancelPendingExit() // background connect, no new activity
                g.cancelPendingExit() // repeat connect command: retire must be idempotent

                val secondCalls = mutableListOf<String>()
                g.scheduleExit(
                    owner = null,
                    teardown = { secondCalls += "teardown" },
                    afterwards = { secondCalls += "afterwards" },
                )
                assertEquals(
                    "the first real task removal after a completed flow was retired must still disconnect",
                    listOf("teardown", "afterwards"),
                    secondCalls,
                )
            }
        }

    @Test
    fun `retiring a completed flow twice keeps the in-flight snapshot for a pending flow`() =
        runBlocking {
            // Companion of the idempotence fix: a flow still in flight when retired
            // must keep exitFlowCompletedAtRetire == false across a second
            // cancelPendingExit (whose exitJob is null), so its own removal's
            // late unowned delivery stays rejected.
            withTimeout(10_000) {
                val g = gate()
                val activity = Any()
                g.beginSession(activity)

                val inTeardown = CompletableDeferred<Unit>()
                g.scheduleExit(
                    owner = activity,
                    teardown = { inTeardown.complete(Unit); hang() },
                    afterwards = {},
                )
                inTeardown.await()

                g.cancelPendingExit() // connect retires the in-flight flow
                g.cancelPendingExit() // repeat connect must not "upgrade" the snapshot

                val calls = mutableListOf<String>()
                g.scheduleExit(
                    owner = null,
                    teardown = { calls += "teardown" },
                    afterwards = { calls += "afterwards" },
                )
                assertTrue(
                    "the same removal's late onTaskRemoved must stay rejected after a repeat connect",
                    calls.isEmpty(),
                )
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

    @Test
    fun `a second entry replacing a running first entry reruns the teardown`() =
        runBlocking {
            // Non-blocking observation (sol), pinned as current behaviour: for
            // an UNretired flow the two entry points of one task removal each
            // open their own flow — the second cancels and replaces the first,
            // so teardown is not exactly-once across the double entry. Only
            // retirement (connect) suppresses the late second entry; making
            // teardown exactly-once across unretired double entries is left
            // as-is deliberately (the real teardowns are idempotent).
            //
            // The first teardown blocks on a *releasable* gate (not an eternal
            // hang): after the second entry replaced it, releasing the gate
            // proves the first flow was genuinely cancelled — neither its
            // teardown tail nor its afterwards ever runs.
            withTimeout(10_000) {
                val g = gate()
                val activity = Any()
                g.beginSession(activity)

                val inFirstTeardown = CompletableDeferred<Unit>()
                val firstGate = CompletableDeferred<Unit>()
                var firstTeardownTailRan = false
                var firstAfterwardsRan = false
                g.scheduleExit(
                    owner = activity,
                    teardown = {
                        inFirstTeardown.complete(Unit)
                        firstGate.await()
                        firstTeardownTailRan = true
                    },
                    afterwards = { firstAfterwardsRan = true },
                )
                inFirstTeardown.await()

                val secondCalls = mutableListOf<String>()
                g.scheduleExit(
                    owner = null,
                    teardown = { secondCalls += "teardown" },
                    afterwards = { secondCalls += "afterwards" },
                )
                assertEquals(listOf("teardown", "afterwards"), secondCalls)

                // Releasing the gate can only matter if the first job was really
                // cancelled: an uncancelled Unconfined job would run its tail and
                // afterwards synchronously on release.
                firstGate.complete(Unit)
                kotlinx.coroutines.delay(100)
                assertFalse("cancelled flow must not run its teardown tail", firstTeardownTailRan)
                assertFalse("cancelled flow must not run afterwards", firstAfterwardsRan)
            }
        }

    private suspend fun hang() = CompletableDeferred<Unit>().await()
}
