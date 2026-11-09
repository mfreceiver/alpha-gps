package com.saschl.cameragps.service.coordinator

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDisconnectCoordinatorTest {
    private val events = mutableListOf<String>()

    private fun coordinator(
        wifi: suspend (String) -> Unit = { events += "wifi $it" },
        link: (String) -> Boolean = { events += "link $it"; true },
    ) = DeviceDisconnectCoordinator(wifi, link, wifiTimeoutMs = 50)

    @Test
    fun `wifi is closed before only the target link`() = runBlocking {
        assertTrue(coordinator().disconnect("AA:01"))
        assertEquals(listOf("wifi AA:01", "link AA:01"), events)
    }

    @Test
    fun `a hanging wifi teardown is bounded and the link still closes`() = runBlocking {
        val closed = coordinator(wifi = { awaitCancellation() }).disconnect("AA:01")
        assertTrue(closed)
        assertEquals(listOf("link AA:01"), events)
    }

    @Test
    fun `a failing wifi teardown does not leave the link open`() = runBlocking {
        val closed = coordinator(wifi = { error("boom") }).disconnect("AA:01")
        assertTrue(closed)
        assertEquals(listOf("link AA:01"), events)
    }

    @Test
    fun `a failing or missing link is reported as not closed without throwing`() = runBlocking {
        assertFalse(coordinator(link = { error("gatt") }).disconnect("AA:01"))
        assertFalse(coordinator(link = { false }).disconnect("AA:01"))
    }

    @Test
    fun `only an explicitly disabled camera is blocked from connecting`() {
        assertFalse(isConnectAllowed(false))
        assertTrue(isConnectAllowed(true))
        assertTrue("unknown cameras are not blocked", isConnectAllowed(null))
    }
}
