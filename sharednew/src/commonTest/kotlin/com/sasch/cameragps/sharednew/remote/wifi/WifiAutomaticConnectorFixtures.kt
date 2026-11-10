package com.sasch.cameragps.sharednew.remote.wifi

import kotlinx.coroutines.CoroutineScope

/**
 * Test double for [WifiAutomaticConnector] (the production interface has no SAM form on purpose,
 * so every implementation has to state its [WifiAutomaticConnector.phonePreconditions]).
 */
internal fun testAutomaticConnector(
    preconditions: WifiPreconditions? = null,
    block: suspend (String, CoroutineScope, (WifiRemotePhase) -> Unit) -> WifiRemoteConnection,
): WifiAutomaticConnector = object : WifiAutomaticConnector {
    override val phonePreconditions: WifiPreconditions? = preconditions
    override suspend fun open(
        identifier: String, scope: CoroutineScope, onPhase: (WifiRemotePhase) -> Unit,
    ): WifiRemoteConnection = block(identifier, scope, onPhase)
}
