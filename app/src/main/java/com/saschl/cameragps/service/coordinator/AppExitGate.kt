package com.saschl.cameragps.service.coordinator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owner/epoch guard around app-exit teardowns.
 *
 * Exit teardowns are asynchronous (Wi-Fi close alone waits up to 3s), and two
 * interleavings used to break them:
 *  1. Old activity finishes, a new one is created, then the old activity's
 *     late onDestroy still scheduled a fresh teardown that killed the new
 *     session's connection.
 *  2. A valid connect command (CDM/notification with an address) arrived
 *     during the teardown window and was subsequently torn down too.
 *
 * Every teardown is therefore stamped with the app session it belongs to:
 * [beginSession] re-stamps on a new activity, [cancelPendingExit] cancels a
 * pending/running exit without deposing the current owner, and [scheduleExit]
 * from a stale owner is rejected outright.
 */
class AppExitGate(private val scope: CoroutineScope) {

    private var session: Any = Any()
    private var exitJob: Job? = null

    /** A new foreground session starts; any pending exit from an older one is void. */
    fun beginSession(owner: Any) {
        exitJob?.cancel()
        exitJob = null
        session = owner
    }

    /**
     * Voids any pending or running exit teardown. The current session owner
     * keeps its authority: an incoming connection must not depose a still
     * alive activity, whose own later exit must still be honoured (and still
     * disconnect). Only [beginSession] — a new foreground activity — supersedes
     * an owner.
     */
    fun cancelPendingExit() {
        exitJob?.cancel()
        exitJob = null
    }

    /**
     * Runs [teardown] and then [afterwards] unless a newer app session or an
     * incoming connection superseded [owner]. A null owner (task removed)
     * always belongs to the current session.
     */
    fun scheduleExit(
        owner: Any? = null,
        teardown: suspend () -> Unit,
        afterwards: () -> Unit,
    ) {
        if (owner != null && owner !== session) return
        exitJob?.cancel()
        exitJob = scope.launch {
            teardown()
            afterwards()
        }
    }
}
