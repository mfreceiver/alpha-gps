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
 * Exit-flow epochs: one task removal reaches the app through *two* entry
 * points — the owned Activity onDestroy and the unowned onTaskRemoved — and
 * either may arrive first or lag behind the other. Each accepted
 * [scheduleExit] opens a new exit flow and records its entry kind; a connect
 * that cancels (or finds a just-completed) flow retires that flow's epoch.
 * Late callbacks of a retired flow are then rejected *in both directions*:
 *  - an owned callback is always a late duplicate (the owner already entered
 *    the retired flow), and
 *  - an unowned callback after an *owned*-entered flow is the same task
 *    removal's late onTaskRemoved (the removal destroyed the activity first).
 * An unowned callback after an *unowned*-entered retired flow, however, is a
 * genuinely new task removal: onTaskRemoved fires once per removal, so a
 * second delivery cannot belong to the flow that already used it. That keeps
 * the "a real later swipe still disconnects" semantics intact. Only
 * [beginSession] — a new foreground activity — clears a retired epoch, so a
 * relaunch always exits freely again.
 */
class AppExitGate(private val scope: CoroutineScope) {

    private var session: Any = Any()
    private var exitJob: Job? = null

    /** Entry kind of the current exit flow: owned (activity), unowned (task removed), or none yet. */
    private var exitFlowEnteredOwned: Boolean? = null

    /** True while a connect retired the current exit flow (pending, running or just completed). */
    private var exitFlowRetired = false

    /** A new foreground session starts; any pending exit from an older one is void. */
    fun beginSession(owner: Any) {
        exitJob?.cancel()
        exitJob = null
        exitFlowEnteredOwned = null
        exitFlowRetired = false
        session = owner
    }

    /**
     * Voids any pending or running exit teardown. The current session owner
     * keeps its authority: an incoming connection must not depose a still
     * alive activity, whose own later exit must still be honoured (and still
     * disconnect). Only [beginSession] — a new foreground activity — supersedes
     * an owner.
     *
     * If an exit flow of the current epoch existed — still pending, running,
     * or even just completed — it is retired: late duplicate callbacks of
     * that same flow (the other entry point of the same task removal) are
     * rejected, so they cannot rebuild a teardown that kills the connection
     * the connect just made. A flow that completed *before* the connect is
     * retired too: a late second callback of it would equally tear down the
     * brand-new connection.
     */
    fun cancelPendingExit() {
        if (exitFlowEnteredOwned != null) exitFlowRetired = true
        exitJob?.cancel()
        exitJob = null
    }

    /**
     * Runs [teardown] and then [afterwards] unless a newer app session or an
     * incoming connection superseded [owner]. A null owner (task removed)
     * always belongs to the current session.
     *
     * Late callbacks of a retired exit flow are rejected in both directions:
     * an owned callback, because its owner already entered the retired flow,
     * and an unowned callback after an owned-entered flow, because it is the
     * same task removal's late onTaskRemoved. An unowned callback after an
     * unowned-entered retired flow is a genuinely new task removal and stays
     * honoured (onTaskRemoved fires once per removal, so it is never a late
     * duplicate of a flow that already entered through it).
     */
    fun scheduleExit(
        owner: Any? = null,
        teardown: suspend () -> Unit,
        afterwards: () -> Unit,
    ) {
        if (owner != null && owner !== session) return
        if (owner != null && exitFlowRetired) return
        if (owner == null && exitFlowRetired && exitFlowEnteredOwned == true) return
        exitFlowEnteredOwned = owner != null
        exitFlowRetired = false
        exitJob?.cancel()
        exitJob = scope.launch {
            teardown()
            afterwards()
        }
    }
}
