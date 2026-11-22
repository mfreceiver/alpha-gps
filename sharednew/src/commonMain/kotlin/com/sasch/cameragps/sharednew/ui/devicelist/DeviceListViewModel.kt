package com.sasch.cameragps.sharednew.ui.devicelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import com.sasch.cameragps.sharednew.bluetooth.status.CameraLiveStatus
import com.sasch.cameragps.sharednew.ui.device.BatteryDisplay
import com.sasch.cameragps.sharednew.ui.device.batteryDisplay
import com.sasch.cameragps.sharednew.database.devices.CameraDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlinx.coroutines.flow.stateIn

/**
 * Platform inputs for the shared device-list state. Both implementations are
 * thin wrappers over the app-scoped graph (AppServices on Android).
 */
interface DeviceListDataSource {
    val sessions: StateFlow<Map<String, CameraSession>>
    val transmissionActive: StateFlow<Boolean>
    val deviceSettings: Flow<List<CameraDevice>>

    /**
     * Live status per upper-case address (last GPS write, cached battery). Defaults to none, so
     * hosts without a status hub render rows without those two hints.
     */
    val liveStatuses: Flow<Map<String, CameraLiveStatus>> get() = flowOf(emptyMap())
}

/**
 * Derived per-device state for a list row. Platforms iterate their own identity
 * lists (CDM associations / discovered peripherals) and look rows up by
 * uppercased identifier; a missing entry means "no state yet" and renders as
 * all-off defaults.
 */
data class DeviceListItem(
    val identifier: String,
    /**
     * A name a person chose, or null when the row should keep the identity name
     * its platform already has (a CDM association name, a peripheral name).
     */
    val customName: String?,
    val isAlwaysOnEnabled: Boolean,
    val isTransmissionActive: Boolean,
    val isRemoteFeatureActive: Boolean,
    val isShutterActive: Boolean,
    val locationDisabledByCamera: Boolean = false,
    /** Last camera-acknowledged GPS write (epoch ms); null = none seen. */
    val lastWriteAtMillis: Long? = null,
    /** Cached camera battery percent, only when the reading is fresh (at most 30 min old); never a placeholder. */
    val freshBatteryPercent: Int? = null,
    val batteryObtainedAtMillis: Long? = null,
)

/** The live-status values a list row shows, reduced so unrelated status churn (RSSI) is ignored. */
internal data class DeviceLiveSlice(
    val lastWriteAtMillis: Long?,
    val freshBatteryPercent: Int?,
    val batteryObtainedAtMillis: Long? = null,
) {
    /**
     * Rows show the write age in whole minutes, so a newer write within the same minute is not a
     * visible change and must not recompose the card (writes land about once a second).
     */
    fun sameDisplayAs(other: DeviceLiveSlice): Boolean =
        freshBatteryPercent == other.freshBatteryPercent &&
            batteryObtainedAtMillis == other.batteryObtainedAtMillis &&
            lastWriteAtMillis?.div(MINUTE_MILLIS) == other.lastWriteAtMillis?.div(MINUTE_MILLIS)

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}

internal fun liveSlicesOf(
    statuses: Map<String, CameraLiveStatus>,
    nowMillis: Long,
): Map<String, DeviceLiveSlice> = statuses.mapValues { (_, status) ->
    val freshBattery = (batteryDisplay(status.battery, nowMillis) as? BatteryDisplay.Value)
        ?.takeIf { it.ageMinutes == null }
    DeviceLiveSlice(
        lastWriteAtMillis = status.lastWriteAtMillis,
        freshBatteryPercent = freshBattery?.percent,
        batteryObtainedAtMillis = status.battery?.obtainedAtMillis?.takeIf { freshBattery != null },
    )
}

internal fun sameDisplay(a: Map<String, DeviceLiveSlice>, b: Map<String, DeviceLiveSlice>): Boolean =
    a.keys == b.keys && a.all { (key, slice) -> slice.sameDisplayAs(b.getValue(key)) }

/**
 * Shared device-list presentation state: the single derivation point of
 * per-device UI state from the session registry, the global transmission gate
 * and the persisted device settings.
 */
class DeviceListViewModel(
    dataSource: DeviceListDataSource,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private val liveSlices: Flow<Map<String, DeviceLiveSlice>> = dataSource.liveStatuses
        .map { liveSlicesOf(it, nowMillis()) }
        .distinctUntilChanged(::sameDisplay)


    val items: StateFlow<Map<String, DeviceListItem>> = combine(
        dataSource.sessions,
        dataSource.transmissionActive,
        dataSource.deviceSettings,
        liveSlices,
    ) { sessions, transmissionActive, settings, live ->
        val settingsByMac = settings.associateBy { it.mac.uppercase() }
        (sessions.keys + settingsByMac.keys).associateWith { identifier ->
            val session = sessions[identifier]
            val persisted = settingsByMac[identifier]
            DeviceListItem(
                identifier = identifier,
                customName = persisted?.takeIf { it.deviceNameIsCustom }?.deviceName
                    ?.takeUnless { it.isBlank() },
                isAlwaysOnEnabled = persisted?.alwaysOnEnabled == true,
                isTransmissionActive =
                    session?.isLocationReady == true && transmissionActive,
                isRemoteFeatureActive = session?.remoteFeatureActive == true,
                isShutterActive = session?.shutterSequenceActive == true,
                locationDisabledByCamera = session?.locationDisabledByCamera == true,
                lastWriteAtMillis = live[identifier]?.lastWriteAtMillis,
                freshBatteryPercent = live[identifier]?.freshBatteryPercent,
                batteryObtainedAtMillis = live[identifier]?.batteryObtainedAtMillis,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}
