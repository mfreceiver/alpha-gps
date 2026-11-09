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
 * new one inactive, no MTU reset to 23, no stale events attributed to it). Mutations that emit
 * transport events do so inside their [update] block, so synthetic events (e.g. the Disconnected
 * of a manual disconnect) can never overtake or be overtaken by a racing callback of the same
 * handle.
 *
 * Pure JVM (no Android types) so the interleavings are unit-testable; [AndroidBleTransport]
 * instantiates it with [android.bluetooth.BluetoothGatt].
 */
internal class GattConnectionRegistry<G : Any> {

    /** One address's current handle plus the mutable per-link state callbacks own. */
    class Entry<T : Any>(val gatt: T) {
        var isActive = false
        var mtu = 23
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
     * active, i.e. the caller owes a synthetic Disconnected event.
     */
    fun pause(address: String): Boolean = synchronized(lock) {
        val entry = connections[address] ?: return false
        if (!entry.isActive) return false
        entry.isActive = false
        true
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
