package com.sasch.cameragps.sharednew.ui.devicelist

import androidx.lifecycle.ViewModelStore
import com.sasch.cameragps.sharednew.bluetooth.BleSessionPhase
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.status.CameraBattery
import com.sasch.cameragps.sharednew.bluetooth.status.CameraLiveStatus
import com.sasch.cameragps.sharednew.bluetooth.status.GpsWriteStats
import com.sasch.cameragps.sharednew.database.devices.CameraDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListViewModelTest {
    @Test
    fun cameraWarningUpdatesIndependentlyOfTransmissionIndicator() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val source = object : DeviceListDataSource {
                override val sessions = MutableStateFlow(
                    mapOf(
                        "A" to CameraSession("A", BleSessionPhase.Transmitting),
                    )
                )
                override val transmissionActive = MutableStateFlow(true)
                override val deviceSettings = flowOf(listOf(CameraDevice(mac = "A")))
            }
            val model = DeviceListViewModel(source)
            store.put("devices", model)
            backgroundScope.launch { model.items.collect() }
            runCurrent()
            for (disabled in listOf(true, false, true)) {
                source.sessions.value = mapOf(
                    "A" to CameraSession(
                        "A",
                        BleSessionPhase.Transmitting,
                        locationDisabledByCamera = disabled
                    ),
                )
                runCurrent()
                val item = model.items.value.getValue("A")
                assertEquals(disabled, item.locationDisabledByCamera)
                assertTrue(item.isTransmissionActive)
            }
        } finally {
            store.clear()
            backgroundScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private fun status(
        address: String,
        lastWriteAt: Long? = null,
        battery: CameraBattery? = null,
        rssi: Int? = null,
    ) = CameraLiveStatus(
        address = address,
        gps = GpsWriteStats(lastWriteAtMillis = lastWriteAt),
        battery = battery,
        rssiDbm = rssi,
    )

    @Test
    fun rowCarriesLastWriteAndOnlyAFreshBattery() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val now = 100 * 3_600_000L
        try {
            val live = MutableStateFlow(
                mapOf(
                    "A" to status("A", lastWriteAt = now - 5_000, battery = CameraBattery(80, now - 5 * 60_000L, "A")),
                    // Older than 30 min (stale) or 24 h: no battery tag on the list card.
                    "B" to status("B", battery = CameraBattery(50, now - 35 * 60_000L, "B")),
                    "C" to status("C"),
                )
            )
            val source = object : DeviceListDataSource {
                override val sessions = MutableStateFlow(emptyMap<String, CameraSession>())
                override val transmissionActive = MutableStateFlow(true)
                override val deviceSettings = flowOf(listOf(CameraDevice(mac = "A"), CameraDevice(mac = "B"), CameraDevice(mac = "C")))
                override val liveStatuses = live
            }
            val model = DeviceListViewModel(source, nowMillis = { now })
            store.put("devices", model)
            backgroundScope.launch { model.items.collect() }
            runCurrent()
            val items = model.items.value
            assertEquals(now - 5_000, items.getValue("A").lastWriteAtMillis)
            assertEquals(80, items.getValue("A").freshBatteryPercent)
            assertEquals(null, items.getValue("B").freshBatteryPercent)
            assertEquals(null, items.getValue("C").lastWriteAtMillis)
            assertEquals(null, items.getValue("C").freshBatteryPercent)
        } finally {
            store.clear()
            backgroundScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun signalOnlyChurnAndSameMinuteWritesAreNotVisibleChanges() {
        val now = 10 * 60_000L
        val base = liveSlicesOf(mapOf("A" to status("A", lastWriteAt = now, rssi = -50)), now)
        val rssiOnly = liveSlicesOf(mapOf("A" to status("A", lastWriteAt = now, rssi = -80)), now)
        val sameMinuteWrite = liveSlicesOf(mapOf("A" to status("A", lastWriteAt = now + 20_000, rssi = -80)), now + 20_000)
        val nextMinuteWrite = liveSlicesOf(mapOf("A" to status("A", lastWriteAt = now + 61_000)), now + 61_000)
        assertTrue(sameDisplay(base, rssiOnly))
        assertTrue(sameDisplay(base, sameMinuteWrite))
        assertTrue(!sameDisplay(base, nextMinuteWrite))
        assertTrue(!sameDisplay(base, emptyMap()))
    }
}
