package com.saschl.cameragps.service.transport

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the GATT connection registry. They exist because the app unit-test
 * source set has no Robolectric: the registry is extracted from AndroidBleTransport so
 * the retire/replace vs callback race can be covered without a real BluetoothGatt.
 */
class GattConnectionRegistryTest {

    private class FakeGatt(val id: Int)

    @Test
    fun `register refuses a duplicate address and reports it`() {
        val registry = GattConnectionRegistry<FakeGatt>()
        val first = FakeGatt(1)
        val second = FakeGatt(2)

        assertTrue(registry.register("AA", first))
        assertFalse(registry.register("AA", second))
        assertSame(first, registry.entry("AA")?.gatt)
        assertEquals(1, registry.size)
    }

    @Test
    fun `a late callback after a replacement cannot touch the new connection`() {
        val registry = GattConnectionRegistry<FakeGatt>()
        val old = FakeGatt(1)
        val new = FakeGatt(2)
        registry.register("AA", old)

        // Replace the entry (retire + register the same address).
        registry.retire("AA")
        registry.register("AA", new)
        registry.update(new, "AA") {
            it.isActive = true
            it.mtu = 123
        }

        val applied = AtomicBoolean(false)
        // Late callback from the old GATT: must be rejected and leave the new entry intact.
        val result = registry.update(old, "AA") {
            applied.set(true)
            it.isActive = false
            it.mtu = 23
        }

        assertNull(result)
        assertFalse(applied.get())
        val entry = registry.entry("AA")
        assertNotNull(entry)
        assertSame(new, entry?.gatt)
        assertTrue(entry?.isActive == true)
        assertEquals(23 + 100, entry?.mtu)
        assertEquals(1, registry.activeCount())
    }

    @Test
    fun `callback handling is serialized with retirement`() {
        val registry = GattConnectionRegistry<FakeGatt>()
        val gatt = FakeGatt(1)
        registry.register("AA", gatt)

        val insideCallback = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val retireFinished = AtomicBoolean(false)

        val callbackThread = thread {
            registry.update(gatt, "AA") {
                insideCallback.countDown()
                releaseCallback.await(5, TimeUnit.SECONDS)
                it.isActive = true
            }
        }

        assertTrue(insideCallback.await(5, TimeUnit.SECONDS))

        val retireThread = thread {
            registry.retire("AA")
            retireFinished.set(true)
        }

        // While the callback holds the monitor, retirement must not complete.
        Thread.sleep(100)
        assertFalse(retireFinished.get())

        releaseCallback.countDown()
        callbackThread.join(5000)
        retireThread.join(5000)
        assertTrue(retireFinished.get())
        assertNull(registry.entry("AA"))

        // After retirement the same callback target is rejected for good.
        assertNull(registry.update(gatt, "AA") { it.isActive = true })
    }

    @Test
    fun `size counts pending handles while activeCount does not`() {
        val registry = GattConnectionRegistry<FakeGatt>()
        registry.register("AA", FakeGatt(1))
        registry.register("BB", FakeGatt(2))

        assertEquals(2, registry.size)
        assertEquals(0, registry.activeCount())

        registry.update(registry.entry("AA")!!.gatt, "AA") { it.isActive = true }
        assertEquals(1, registry.activeCount())
        assertEquals(2, registry.size)
    }

    @Test
    fun `retireIfParked removes only inactive handles and rejects their late callbacks`() {
        val registry = GattConnectionRegistry<FakeGatt>()
        val parked = FakeGatt(1)
        val live = FakeGatt(2)
        registry.register("AA", parked)
        registry.register("BB", live)
        registry.update(live, "BB") { it.isActive = true }

        // A live link is never retired by the parked-handle path.
        assertNull(registry.retireIfParked("BB"))
        assertSame(live, registry.entry("BB")?.gatt)

        val retired = registry.retireIfParked("AA")
        assertSame(parked, retired?.gatt)
        assertEquals(1, registry.size)
        assertNull(registry.entry("AA"))
        // Unknown address: nothing to retire.
        assertNull(registry.retireIfParked("CC"))
        // Late callback of the parked handle must not reach the event channel.
        assertNull(registry.update(parked, "AA") { it.isActive = true })
    }

    @Test
    fun `retireAll retires every handle so late callbacks are all rejected`() {
        val registry = GattConnectionRegistry<FakeGatt>()
        val gatts = listOf(FakeGatt(1), FakeGatt(2))
        gatts.forEachIndexed { index, gatt -> registry.register("A$index", gatt) }

        val retired = registry.retireAll()
        assertEquals(2, retired.size)
        assertEquals(0, registry.size)

        gatts.forEachIndexed { index, gatt ->
            assertNull(registry.update(gatt, "A$index") { it.isActive = true })
        }
    }

    @Test
    fun `a pending handle owes a disconnected report until a callback or pause delivers one`() {
        val registry = GattConnectionRegistry<FakeGatt>()

        // Fresh registration: connect still pending, nothing reported — a retire owes a
        // synthetic Disconnected or the session above stays Connecting forever.
        val pending = FakeGatt(1)
        registry.register("AA", pending)
        assertFalse(registry.entry("AA")!!.disconnectedReported)

        // The real GATT Disconnected callback marks the handle as reported.
        registry.update(pending, "AA") {
            it.isActive = false
            it.disconnectedReported = true
        }
        assertTrue(registry.entry("AA")!!.disconnectedReported)

        // pause() emits a synthetic Disconnected itself, so it counts as reported too.
        val active = FakeGatt(2)
        registry.register("BB", active)
        registry.update(active, "BB") { it.isActive = true }
        assertTrue(registry.pause("BB"))
        assertTrue(registry.entry("BB")!!.disconnectedReported)
    }
}
