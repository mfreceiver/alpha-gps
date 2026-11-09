package com.saschl.cameragps.service.coordinator

/**
 * Unified "may the whole service stop" gate for single-device exits (manual disconnect, a
 * refused legacy connect, presence loss). The service may only end when no device still needs
 * it: no GATT handle at all — not even a pending or `autoConnect`-reconnecting one, which
 * [hasConnectionDemand] must cover — and no camera flagged keep-active. An enabled camera that
 * is present keeps its handle and is therefore covered; a handle-less, absent camera has no
 * connection demand left.
 *
 * [hasKeepActiveDevices] is a suspend database read: a connection can resume (or a new one
 * register) while it runs, so the fast transport fact is re-checked afterwards, exactly like
 * [ServicePresenceCoordinator.stopIfDisconnectedAndAbsent] does.
 */
internal class ConnectionDemandGate(
    private val hasConnectionDemand: () -> Boolean,
    private val hasKeepActiveDevices: suspend () -> Boolean,
) {
    /** True only when nothing keeps the service alive anymore. */
    suspend fun mayStopService(): Boolean {
        if (hasConnectionDemand() || hasKeepActiveDevices()) return false
        // GATT callbacks can flip a pending connection active while the database read suspends.
        return !hasConnectionDemand()
    }
}
