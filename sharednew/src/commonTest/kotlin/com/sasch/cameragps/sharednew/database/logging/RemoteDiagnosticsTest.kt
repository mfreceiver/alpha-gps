package com.sasch.cameragps.sharednew.database.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteDiagnosticsTest {
    @Test
    fun keepsOpeningEvidenceAndRejectsLateCallbacksAfterReplacementOrClear() {
        val first = RemoteDiagnostics("BLE", "A")
        first.record("opened")
        repeat(150) { first.record("state=$it") }
        assertTrue(RemoteDiagnostics.latest.value[1].endsWith("opened"))
        assertTrue(RemoteDiagnostics.latest.value.last().endsWith("state=149"))
        RemoteDiagnostics("Wi-Fi")
        first.record("late")
        assertEquals(1, RemoteDiagnostics.latest.value.size)
        RemoteDiagnostics.latest.value = emptyList()
        first.record("late")
        assertFalse(RemoteDiagnostics.latest.value.isNotEmpty())
    }
}
