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
 *
 * A cancelled exit also *retires* its exit generation. An exit teardown and
 * the late callbacks of the same exit flow (the unowned onTaskRemoved entry
 * and the owned Activity onDestroy entry are two entry points of one flow)
 * share a generation; once a connect command cancelled that generation's
 * teardown, every further callback of it is a late duplicate that must not
 * rebuild the teardown and kill the connection the connect just made. Only
 * [beginSession] — a new foreground activity — opens the next generation.
 */
class AppExitGate(private val scope: CoroutineScope) {

    private var session: Any = Any()
    private var exitJob: Job? = null

    /** True while the current session's exit generation was cancelled by a connect. */
    private var exitGenerationRetired = false

    /** A new foreground session starts; any pending exit from an older one is void. */
    fun beginSession(owner: Any) {
        exitJob?.cancel()
        exitJob = null
        exitGenerationRetired = false
        session = owner
    }

    /**
     * Voids any pending or running exit teardown. The current session owner
     * keeps its authority: an incoming connection must not depose a still
     * alive activity, whose own later exit must still be honoured (and still
     * disconnect). Only [beginSession] — a new foreground activity — supersedes
     * an owner.
     *
     * If a teardown was actually cancelled, its exit generation is retired:
     * late duplicate callbacks of that same exit flow (the flow that already
     * entered exit and was cancelled) are rejected, while the still-alive
     * owner's *first real* exit afterwards stays honoured — it is a new exit,
     * not a callback of the cancelled one.
     */
    fun cancelPendingExit() {
        if (exitJob != null) exitGenerationRetired = true
        exitJob?.cancel()
        exitJob = null
    }

    /**
     * Runs [teardown] and then [afterwards] unless a newer app session or an
     * incoming connection superseded [owner]. A null owner (task removed)
     * always belongs to the current session.
     *
     * An owned exit is rejected while the session's exit generation is
     * retired: the owner already entered the cancelled exit flow, so its
     * further callbacks are late duplicates of it. An unowned exit is never
     * blocked — onTaskRemoved fires once per real task removal, so it is
     * always a fresh, genuine exit signal (blocking it would leak a
     * connection the cancelled exit never tore down).
     */
    fun scheduleExit(
        owner: Any? = null,
        teardown: suspend () -> Unit,
        afterwards: () -> Unit,
    ) {
        if (owner != null && owner !== session) return
        if (owner != null && exitGenerationRetired) return
        exitJob?.cancel()
        exitJob = scope.launch {
            teardown()
            afterwards()
        }
    }
}
