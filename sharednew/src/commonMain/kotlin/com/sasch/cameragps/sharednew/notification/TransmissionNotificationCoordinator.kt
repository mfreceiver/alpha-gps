package com.sasch.cameragps.sharednew.notification

import com.sasch.cameragps.sharednew.bluetooth.session.CameraSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One status notification, driven by session state rather than individual GPS updates. */
class TransmissionNotificationCoordinator(
    private val scope: CoroutineScope,
    sessions: StateFlow<Map<String, CameraSession>>,
    transmitting: StateFlow<Boolean>,
    // Android's foreground status is mandatory; iOS supplies its opt-in/permission gates.
    enabled: Flow<Boolean> = flowOf(true),
    authorized: Flow<Boolean> = flowOf(true),
    private val publisher: Publisher,
    // Resolved display names by uppercase MAC. Merged into the published status so a
    // rename of the single ready camera refreshes the notification without a count change.
    deviceNames: Flow<Map<String, String>> = flowOf(emptyMap()),
) {
    interface Publisher {
        /** Replace the existing status notification; return after the native operation completes. */
        suspend fun show(cameraCount: Int)

        /**
         * Exactly one ready camera: render its display name when known. A null name
         * falls back to the platform's generic "camera" label.
         */
        suspend fun showSingleCamera(cameraCount: Int, cameraName: String?) = show(cameraCount)

        /** Android keeps a waiting foreground notification; iOS removes its status. */
        fun showIdle()
    }

    /** What the status notification currently renders. Equality gates re-publishing. */
    data class Status(val cameraCount: Int, val singleCameraName: String?)

    val transmittingCameraCount: StateFlow<Int> =
        combine(sessions, transmitting) { current, active ->
            if (active) current.values.count { it.isLocationReady } else 0
        }.stateIn(scope, SharingStarted.Eagerly, 0)

    private val visibleStatus: StateFlow<Status> = combine(
        transmittingCameraCount, enabled, authorized, sessions, deviceNames,
    ) { count, isEnabled, isAuthorized, current, names ->
        val visible = if (isEnabled && isAuthorized) count else 0
        val singleName = if (visible == 1) {
            current.values.filter { it.isLocationReady }.singleOrNull()?.identifier
                ?.let { names[it] }
        } else null
        Status(visible, singleName)
    }.stateIn(scope, SharingStarted.Eagerly, Status(0, null))

    val visibleCameraCount: StateFlow<Int> = visibleStatus
        .map { it.cameraCount }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private var publishingJob: Job? = null

    /** Cancel the returned job before platform teardown to prevent further notification updates. */
    fun start(): Job {
        publishingJob?.let { return it }
        return scope.launch {
            // Finish an in-flight native post before showing idle on disconnect/disable,
            // otherwise that late post can overwrite the waiting state or reappear on iOS.
            visibleStatus.collect { status ->
                when {
                    status.cameraCount == 0 -> publisher.showIdle()
                    status.cameraCount == 1 -> publisher.showSingleCamera(1, status.singleCameraName)
                    else -> publisher.show(status.cameraCount)
                }
            }
        }.also { publishingJob = it }
    }
}
