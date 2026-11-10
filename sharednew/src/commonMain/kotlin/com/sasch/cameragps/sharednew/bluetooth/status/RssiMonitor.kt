package com.sasch.cameragps.sharednew.bluetooth.status

import com.diamondedge.logging.logging
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import com.sasch.cameragps.sharednew.bluetooth.transport.BleOperationStatus
import com.sasch.cameragps.sharednew.bluetooth.transport.BlePeripheralTransport
import com.sasch.cameragps.sharednew.bluetooth.transport.BleTransportEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handle for a consumer's interest in a camera's signal strength (typically: the device page is
 * visible). Release it exactly when the interest ends; releasing twice is harmless.
 */
class SignalMonitoringLease internal constructor(private val onRelease: () -> Unit) {
    private var released = false

    fun release() {
        if (released) return
        released = true
        onRelease()
    }
}

/**
 * Low-frequency RSSI polling of the active GATT link, reference counted per camera so that only
 * visible screens cost anything: with no lease nothing is ever sent.
 *
 * Power/traffic: one `readRemoteRssi` every [intervalMs] (default 10 s) per leased camera, only
 * while the session is [com.sasch.cameragps.sharednew.bluetooth.session.CameraSession.isLocationReady]
 * (Transmitting). The request is an HCI command to the local controller (no air traffic of its own
 * beyond the connection event that already happens) and it deliberately does **not** go through the
 * ATT operation lane: a lost callback must never be able to block a GPS write for the lane's
 * 15 s timeout. Every failure (refusal, error status, timeout) is swallowed; after [maxFailures]
 * consecutive failures the reading is reported as unknown.
 */
internal class RssiMonitor(
    private val transport: BlePeripheralTransport,
    private val registry: CameraSessionRegistry,
    private val scope: CoroutineScope,
    private val report: (address: String, rssiDbm: Int?) -> Unit,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS,
    private val maxFailures: Int = DEFAULT_MAX_FAILURES,
) {
    private val log = logging()

    private class Entry {
        var leases = 0
        var job: Job? = null
        var pending: CompletableDeferred<Int?>? = null
    }

    private val entries = mutableMapOf<String, Entry>()

    init {
        require(intervalMs >= MIN_INTERVAL_MS) { "RSSI polling faster than ${MIN_INTERVAL_MS}ms is not allowed" }
    }

    fun acquire(address: String): SignalMonitoringLease {
        val id = address.uppercase()
        val entry = entries.getOrPut(id) { Entry() }
        entry.leases++
        if (entry.job == null) entry.job = scope.launch { poll(id, entry) }
        return SignalMonitoringLease { release(id, entry) }
    }

    /** Number of active leases (diagnostics/tests). */
    fun leaseCount(address: String): Int = entries[address.uppercase()]?.leases ?: 0

    fun onEvent(event: BleTransportEvent.RssiRead) {
        val pending = entries[event.identifier.uppercase()]?.pending ?: return
        pending.complete(if (event.status == BleOperationStatus.Success) event.rssi else null)
    }

    /** Stops every poll loop (app shutdown). */
    fun shutdown() {
        entries.values.forEach { it.job?.cancel(); it.job = null; it.leases = 0 }
        entries.clear()
    }

    private fun release(id: String, entry: Entry) {
        if (entry.leases > 0) entry.leases--
        if (entry.leases == 0) {
            entry.job?.cancel()
            entry.job = null
            entry.pending = null
            if (entries[id] === entry) entries.remove(id)
        }
    }

    private suspend fun poll(id: String, entry: Entry) {
        var failures = 0
        while (true) {
            // Wait for a ready link; the first read happens right when it becomes ready.
            registry.sessions.first { it[id]?.isLocationReady == true }
            val value = readOnce(id, entry)
            if (value != null) {
                failures = 0
                report(id, value)
            } else if (++failures >= maxFailures) {
                report(id, null)
            }
            delay(intervalMs)
        }
    }

    private suspend fun readOnce(id: String, entry: Entry): Int? {
        val deferred = CompletableDeferred<Int?>()
        entry.pending = deferred
        return try {
            if (!transport.initiateReadRssi(id)) null
            else withTimeoutOrNull(readTimeoutMs) { deferred.await() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.w { "RSSI read failed (${failure::class.simpleName})" }
            null
        } finally {
            if (entry.pending === deferred) entry.pending = null
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 10_000L
        const val DEFAULT_READ_TIMEOUT_MS = 5_000L
        const val DEFAULT_MAX_FAILURES = 3

        /** Floor protecting battery and the controller; shorter intervals are programming errors. */
        const val MIN_INTERVAL_MS = 1_000L
    }
}
