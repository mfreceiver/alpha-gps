package com.saschl.cameragps.service.transport

import java.util.Collections
import java.util.WeakHashMap

/**
 * Serialized registry of live GATT handles, keyed by uppercase device address.
 *
 * Every mutation — connect registration, retirement (manual disconnect, disconnect-all), pause,
 * and every GATT-callback state update — runs inside this registry's monitor, and each callback
 * update verifies, atomically and before touching any state, that its handle is neither retired
 * nor replaced by a newer handle owning the address. A late callback of an old handle therefore
 * returns null and can never corrupt the connection that owns the address now (no flipping the
 * new one inactive, no MTU reset to 23, no stale events attributed to it). Events emitted inside
 * an [update] block are atomic with that update; the synthetic events of
 * [AndroidBleTransport.disconnectDevice]/[AndroidBleTransport.pauseDevice]/
 * [AndroidBleTransport.retireParkedHandle] are sent only after the registry mutation returned
 * and the monitor was released, so their ordering relative to separate mutations and other
 * synthetic events is not guaranteed by this monitor alone: it relies on
 * [AndroidBleTransport] confining those emissions to the main thread.
 *
 * Pure JVM (no Android types) so the interleavings are unit-testable; [AndroidBleTransport]
 * instantiates it with [android.bluetooth.BluetoothGatt].
 */
internal class GattConnectionRegistry<G : Any> {

    /** One address's current handle plus the mutable per-link state callbacks own. */
    class Entry<T : Any>(val gatt: T) {
        var isActive = false
        var mtu = 23

        /**
         * `true` once this handle already produced a Disconnected — the real GATT callback or
         * the synthetic event of a pause. A handle retired while this is still false (a connect
         * that never got a single connection-state callback) owes its owner a synthetic
         * Disconnected, so the session above does not stay `Connecting` forever.
         */
        var disconnectedReported = false
    }

    private val lock = Any()

    /** Uppercased MAC → connection. Entries survive disconnects (autoConnect). */
    private val connections = LinkedHashMap<String, Entry<G>>()

    /**
     * Handles this transport closed itself (weak: they are closed, only their identity matters).
     * A late callback of such a handle must not reach the event channel: it would be read as
     * news about whatever new connection now owns the address.
     */
    private val retiredGatts: MutableSet<G> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<G, Boolean>()))

    /** Registers [gatt] for [address]; false (gatt untouched) when a handle already owns it. */
    fun register(address: String, gatt: G): Boolean = synchronized(lock) {
        if (connections.containsKey(address)) {
            false
        } else {
            connections[address] = Entry(gatt)
            true
        }
    }

    /** `true` if a handle exists for this device (connected or waiting for autoConnect). */
    fun has(address: String): Boolean = synchronized(lock) { connections.containsKey(address) }

    fun entry(address: String): Entry<G>? = synchronized(lock) { connections[address] }

    /** Number of handles: active links, pending connects and autoConnect-parked alike. */
    val size: Int
        get() = synchronized(lock) { connections.size }

    /** Number of devices with a live (resumed) connection. */
    fun activeCount(): Int = synchronized(lock) { connections.values.count { it.isActive } }

    /**
     * Atomically retires the handle of [address] (manual disconnect): removed from the registry
     * and marked retired, so its late callbacks are dropped. Null when no handle exists.
     */
    fun retire(address: String): Entry<G>? = synchronized(lock) {
        connections.remove(address)?.also { retiredGatts.add(it.gatt) }
    }

    /** Retires every handle (service teardown); the caller closes the returned GATTs. */
    fun retireAll(): List<Entry<G>> = synchronized(lock) {
        val all = connections.values.toList()
        connections.clear()
        all.forEach { retiredGatts.add(it.gatt) }
        all
    }

    /**
     * Marks the address's handle inactive (keep the GATT for autoConnect). True when it was
     * active, i.e. the caller owes a synthetic Disconnected event; that synthetic event also
     * counts as the handle's reported disconnect ([Entry.disconnectedReported]).
     */
    fun pause(address: String): Boolean = synchronized(lock) {
        val entry = connections[address] ?: return false
        if (!entry.isActive) return false
        entry.isActive = false
        entry.disconnectedReported = true
        true
    }

    /**
     * Atomically retires the address's handle, but only while it is parked (link inactive):
     * removed from the registry and marked retired. Null when no handle exists or the link is
     * still active — a handle that reconnected between the caller's check and this call keeps
     * its place.
     */
    fun retireIfParked(address: String): Entry<G>? = synchronized(lock) {
        val entry = connections[address] ?: return null
        if (entry.isActive) return null
        connections.remove(address)
        retiredGatts.add(entry.gatt)
        entry
    }

    /**
     * Identity-verified, serialized state update for a GATT callback: [block] runs only when
     * [gatt] is neither retired nor superseded by a newer handle for [address], and its whole
     * run is atomic w.r.t. retirement and replacement. Null = stale callback, drop it (and any
     * event it would have emitted).
     */
    fun <R> update(gatt: G, address: String, block: (Entry<G>) -> R): R? = synchronized(lock) {
        if (gatt in retiredGatts) return null
        val entry = connections[address] ?: return null
        if (entry.gatt !== gatt) return null
        block(entry)
    }
}
