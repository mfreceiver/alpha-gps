package com.sasch.cameragps.sharednew.bluetooth.status

import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.location.PhoneLocationFix
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

/**
 * Optional storage for the one value that is worth keeping across process restarts: the time of
 * the last camera-acknowledged GPS write. Everything else in [CameraLiveStatus] is session-scoped
 * and deliberately lives in memory only. Implementations must be cheap and non-blocking (the hub
 * throttles saves to [CameraLiveStatusHub.PERSIST_INTERVAL_MS]).
 */
interface CameraStatusPersistence {
    fun loadLastWriteAt(address: String): Long?
    fun saveLastWriteAt(address: String, epochMillis: Long)

    companion object {
        val None: CameraStatusPersistence = object : CameraStatusPersistence {
            override fun loadLastWriteAt(address: String): Long? = null
            override fun saveLastWriteAt(address: String, epochMillis: Long) = Unit
        }
    }
}

/**
 * Aggregates the per-camera live status (time correction, GPS write statistics, signal, battery)
 * and joins it with the session registry (phase, MTU) into [CameraLiveStatus].
 *
 * Consumption from a ViewModel:
 * ```
 * hub.observe(address).collect { status -> ... }   // null until the camera was ever seen
 * hub.statuses                                      // StateFlow<Map<UPPERCASE address, CameraLiveStatus>>
 * ```
 * Entries are kept after a disconnect (phase [BleSessionPhase.Disconnected]) so the page can still
 * show the last counters and write time; they are only dropped by [forget].
 *
 * Recorders are called by the orchestrator on its confined dispatcher; the hub itself holds no
 * locks. No coordinates pass through here — only counts, timestamps and the phone's fix accuracy
 * radius ([phoneLocation]).
 */
class CameraLiveStatusHub(
    registry: CameraSessionRegistry,
    scope: CoroutineScope,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val persistence: CameraStatusPersistence = CameraStatusPersistence.None,
) {
    private data class Telemetry(
        val timeSync: TimeSyncStatus = TimeSyncStatus(),
        val gps: GpsWriteStats = GpsWriteStats(),
        val rssiDbm: Int? = null,
        val rssiAtMillis: Long? = null,
        val battery: CameraBattery? = null,
    )

    private val telemetry = MutableStateFlow<Map<String, Telemetry>>(emptyMap())
    private val lastPersisted = mutableMapOf<String, Long>()

    val statuses: StateFlow<Map<String, CameraLiveStatus>> =
        combine(registry.sessions, telemetry) { sessions, telemetry -> join(sessions, telemetry) }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Status of one camera; emits only when it changes. `null` while the camera is unknown. */
    fun observe(address: String): Flow<CameraLiveStatus?> {
        val id = address.uppercase()
        return statuses.map { it[id] }.distinctUntilChanged()
    }

    fun statusOf(address: String): CameraLiveStatus? = statuses.value[address.uppercase()]

    private val _phoneLocation = MutableStateFlow<PhoneLocationFix?>(null)

    /**
     * Phone-level (not per camera): accuracy radius and time of the phone's latest location fix as
     * the location manager last saw it; null before the first fix and after a shutdown. No
     * coordinates. Staleness is the reader's call (it compares the fix time with its own clock).
     */
    val phoneLocation: StateFlow<PhoneLocationFix?> = _phoneLocation.asStateFlow()

    /** Fed by the location manager's display side channel; never affects transmission. */
    internal fun onPhoneLocationFix(fix: PhoneLocationFix?) {
        _phoneLocation.value = fix
    }

    // ---- Recorders (internal: fed by the orchestrator and Wi-Fi controller) ----

    /** A new BLE link: counters, signal and time-sync result restart; timestamps and battery stay. */
    internal fun onSessionStarted(address: String) = edit(address) {
        it.copy(
            timeSync = TimeSyncStatus(),
            gps = it.gps.copy(queuedCount = 0, writtenCount = 0, failedCount = 0),
            rssiDbm = null,
            rssiAtMillis = null,
        )
    }

    /** The BLE link ended: signal is no longer meaningful; make sure the last write time is saved. */
    internal fun onSessionEnded(address: String) {
        val id = address.uppercase()
        if (id !in telemetry.value) return
        edit(id) {
            it.copy(
                rssiDbm = null,
                rssiAtMillis = null,
                // A correction cut off by the disconnect may or may not have reached the camera.
                timeSync = if (it.timeSync.outcome == TimeSyncOutcome.InProgress)
                    it.timeSync.copy(outcome = TimeSyncOutcome.Unconfirmed) else it.timeSync,
            )
        }
        telemetry.value[id]?.gps?.lastWriteAtMillis?.let { persist(id, it, force = true) }
    }

    /** A location packet was accepted into the BLE operation queue (not yet written). */
    internal fun onGpsQueued(address: String) = edit(address) {
        it.copy(gps = it.gps.copy(queuedCount = it.gps.queuedCount + 1))
    }

    /** The GATT write of a location packet finished: [success] means the camera acknowledged it. */
    internal fun onGpsWriteResult(address: String, success: Boolean) {
        val now = clock()
        edit(address) {
            if (success) it.copy(gps = it.gps.copy(writtenCount = it.gps.writtenCount + 1, lastWriteAtMillis = now))
            else it.copy(gps = it.gps.copy(failedCount = it.gps.failedCount + 1, lastFailureAtMillis = now))
        }
        if (success) persist(address.uppercase(), now, force = false)
    }

    internal fun onTimeSync(address: String, outcome: TimeSyncOutcome, rejectReason: Long? = null) = edit(address) {
        it.copy(timeSync = TimeSyncStatus(outcome, clock(), rejectReason))
    }

    /** [rssiDbm] `null` records "no reading" (e.g. after repeated failures). */
    internal fun onRssi(address: String, rssiDbm: Int?) = edit(address) {
        it.copy(rssiDbm = rssiDbm, rssiAtMillis = if (rssiDbm == null) null else clock())
    }

    internal fun onBattery(address: String, percent: Int) = edit(address) {
        it.copy(battery = CameraBattery(percent.coerceIn(0, 100), clock(), address.uppercase()))
    }

    /** Drops everything remembered about [address] (device removed from the app). */
    fun forget(address: String) {
        val id = address.uppercase()
        telemetry.update { it - id }
        lastPersisted.remove(id)
    }

    // ---- internals ----

    private fun edit(address: String, change: (Telemetry) -> Telemetry) {
        val id = address.uppercase()
        telemetry.update { all ->
            val current = all[id] ?: Telemetry(
                gps = GpsWriteStats(lastWriteAtMillis = persistence.loadLastWriteAt(id)),
            ).also { seed -> seed.gps.lastWriteAtMillis?.let { lastPersisted[id] = it } }
            all + (id to change(current))
        }
    }

    private fun persist(id: String, epochMillis: Long, force: Boolean) {
        val previous = lastPersisted[id]
        if (!force && previous != null && epochMillis - previous < PERSIST_INTERVAL_MS) return
        if (previous == epochMillis) return
        lastPersisted[id] = epochMillis
        persistence.saveLastWriteAt(id, epochMillis)
    }

    private fun join(
        sessions: Map<String, CameraSession>,
        telemetry: Map<String, Telemetry>,
    ): Map<String, CameraLiveStatus> {
        val result = LinkedHashMap<String, CameraLiveStatus>()
        for (id in telemetry.keys + sessions.keys) {
            val session = sessions[id]
            val t = telemetry[id] ?: Telemetry()
            result[id] = CameraLiveStatus(
                address = id,
                phase = session?.phase ?: BleSessionPhase.Disconnected,
                timeSync = t.timeSync,
                gps = t.gps,
                rssiDbm = t.rssiDbm,
                rssiAtMillis = t.rssiAtMillis,
                mtu = session?.mtu,
                battery = t.battery,
            )
        }
        return result
    }

    companion object {
        /** The persisted "last write" lags real time by at most this much (plus a flush on disconnect). */
        const val PERSIST_INTERVAL_MS = 60_000L
    }
}
