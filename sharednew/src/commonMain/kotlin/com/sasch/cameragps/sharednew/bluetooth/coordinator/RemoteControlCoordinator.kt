package com.sasch.cameragps.sharednew.bluetooth.coordinator

import com.diamondedge.logging.logging
import com.sasch.cameragps.sharednew.bluetooth.SonyBluetoothConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shared remote control coordinator. Owns:
 * - Remote feature status detection (pure byte-array parsing)
 * - Periodic probe loop (probe while feature inactive, stop when active)
 * - Shutter command orchestration
 *
 * Platform-specific BLE I/O is delegated to [BleGattPort].
 * Timing uses coroutines so it works identically on Android and iOS.
 */
class RemoteControlCoordinator(
    private val port: BleGattPort,
    private val scope: CoroutineScope,
    private val currentGeneration: (String) -> Long = { 0L },
    private val onBleShutterStateChanged: (String, BleShutterState) -> Unit = { _, _ -> },
    private val prepareTimeoutMs: Long = DEFAULT_PREPARE_TIMEOUT_MS,
) {
    private val log = logging()

    private val activeProbeJobs = mutableMapOf<String, Job>()
    private val monitoredDevices = mutableSetOf<String>()

    private val activeSequenceJobs = mutableMapOf<String, Job>()

    /** Devices with a running command sequence — suppresses the automatic shutter-up. */
    private val sequenceOwners = mutableSetOf<String>()

    /** Devices whose sequence is in the trailing ready-wait (buttons already released). */
    private val readyWaitDevices = mutableSetOf<String>()
    private val wifiOwners = mutableSetOf<String>()

    /** R1 single-shot BLE shutter state & single-operation slot (confined to [scope]). */
    private val _bleShutterStates = MutableStateFlow<Map<String, BleShutterState>>(emptyMap())
    val bleShutterStates: StateFlow<Map<String, BleShutterState>> = _bleShutterStates.asStateFlow()

    private val acquiredBleRemoteDevices = mutableSetOf<String>()
    private val acquiredGenerations = mutableMapOf<String, Long>()
    private val prepareTimeoutJobs = mutableMapOf<String, Job>()
    private val activeOperationSlots = mutableSetOf<String>()
    private var nextOperationId = 0L

    fun bleShutterState(identifier: String): BleShutterState =
        _bleShutterStates.value[identifier.uppercase()] ?: BleShutterState()

    private fun publishBleShutterState(id: String, state: BleShutterState) {
        _bleShutterStates.value = _bleShutterStates.value + (id to state)
        onBleShutterStateChanged(id, state)
    }

    /** Every remote-status notification (identifier to raw value), for sequence waits. */
    private val statusUpdates = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 16)

    // ---- Public API called by platform GATT callbacks / service ----

    /**
     * Begin remote status probing for [identifier]. Subscribes to remote status
     * notifications and starts the probe loop (which polls while the feature is inactive).
     *
     * Call this after the BLE handshake is complete (GPS enabled, time synced, transmitting).
     */
    fun startRemoteStatusMonitoring(identifier: String) {
        val normalized = identifier.uppercase()
        monitoredDevices.add(normalized)
        if (normalized in wifiOwners) return

        // Subscribe to remote status characteristic notifications
        port.subscribeToNotifications(normalized, SonyBluetoothConstants.REMOTE_STATUS_UUID)

        if (!port.hasRemoteControlCharacteristic(normalized)) return

        // CC09 describes Sony app remote availability, not the Bluetooth Rmt Ctrl
        // setting (a6700 reports true even when Bluetooth remote control is off).
        log.d { "Monitoring Bluetooth remote availability for $normalized via FF01 probes and FF02 feedback" }
        startProbeLoop(normalized)
    }

    /**
     * Called when the remote status characteristic value changes (notification from camera).
     * Returns `true` if the platform should send a shutter-up command (specific response value).
     */
    fun onRemoteStatusChanged(identifier: String, value: ByteArray): Boolean {
        val normalized = identifier.uppercase()
        statusUpdates.tryEmit(normalized to value)
        if (normalized in wifiOwners) return false

        val active = isRemoteFeatureActive(value)
        port.setRemoteFeatureActive(normalized, active)

        if (active) {
            stopProbeLoop(normalized)
        } else if (normalized in monitoredDevices) {
            startProbeLoop(normalized)
        } else {
            stopProbeLoop(normalized)
        }

        syncAcquiredStateWithRemoteFeature(normalized, active)

        // A running sequence owns the button state and sends its own releases
        if (normalized in sequenceOwners) return false

        return shouldSendShutterUp(value)
    }

    /**
     * Called when a write to the remote control characteristic completes.
     * Updates the remote-feature state based on success.
     */
    fun onRemoteControlWriteResponse(identifier: String, success: Boolean) {
        val normalized = identifier.uppercase()
        if (normalized in wifiOwners) return
        if (success) {
            port.setRemoteFeatureActive(normalized, true)
            stopProbeLoop(normalized)
        } else {
            port.setRemoteFeatureActive(normalized, false)
            if (normalized in monitoredDevices) startProbeLoop(normalized)
        }
        syncAcquiredStateWithRemoteFeature(normalized, success)
    }

    private fun syncAcquiredStateWithRemoteFeature(id: String, active: Boolean) {
        if (id !in acquiredBleRemoteDevices) return
        val gen = currentGeneration(id)
        if (acquiredGenerations[id] != gen) return
        if (id in activeOperationSlots) return
        val current = bleShutterState(id)
        if (active && current.phase == BleShutterPhase.Preparing) {
            prepareTimeoutJobs.remove(id)?.cancel()
            publishBleShutterState(id, BleShutterState(phase = BleShutterPhase.Ready, connectionGeneration = gen))
        } else if (!active && current.phase == BleShutterPhase.Ready) {
            publishBleShutterState(id, BleShutterState(phase = BleShutterPhase.Preparing, connectionGeneration = gen))
            schedulePrepareTimeout(id, gen)
        }
    }

    /**
     * Send a [RemoteCommand] to [identifier]. Returns `true` if the command was
     * enqueued. Fails when the device is not connected or the remote feature is
     * not active on the camera.
     */
    fun sendCommand(identifier: String, command: RemoteCommand): Boolean {
        val normalized = identifier.uppercase()
        if (normalized in wifiOwners && command !in setOf(RemoteCommand.ShutterFullRelease,
                RemoteCommand.ShutterHalfRelease, RemoteCommand.AfOnRelease)) return false

        if (!port.isConnected(normalized)) return false
        if (!port.isRemoteFeatureActive(normalized)) return false

        return port.writeCharacteristic(
            normalized,
            SonyBluetoothConstants.REMOTE_CHARACTERISTIC_UUID,
            command.payload,
        )
    }

    /**
     * Trigger a remote shutter press for [identifier].
     * Returns `true` if the shutter-down command was sent.
     * Shutter-up is sent later when the camera reports success via characteristic change callback.
     */
    fun handleRemoteShutterRequest(identifier: String): Boolean {
        return sendCommand(identifier, RemoteCommand.ShutterFullPress)
    }

    /**
     * Run one full shutter cycle for [identifier], driven by the camera's status
     * notifications: half-press → wait for focus acquired → full-press → wait for
     * the exposure to start → full-release → half-release → wait for camera ready.
     * Timeouts are only fallbacks so a camera that skips a status (e.g. manual
     * focus) cannot stall the cycle. While a sequence owns a device the automatic
     * ack-triggered shutter-up is suppressed — the sequence sends its own releases.
     *
     * Returns `true` if the sequence was started; `false` when the device is not
     * connected, the remote feature is inactive, or a sequence is still pressing
     * buttons. A press during a sequence's trailing ready-wait (buttons already
     * released) cancels that wait and starts a fresh cycle instead of being
     * dropped — otherwise a camera that never reports ready would lock the
     * shutter out for [SHUTTER_READY_TIMEOUT_MS].
     */
    fun startShutterSequence(identifier: String): Boolean {
        val normalized = identifier.uppercase()
        if (normalized in wifiOwners) return false
        if (!port.isConnected(normalized)) return false
        if (!port.isRemoteFeatureActive(normalized)) return false

        val existing = activeSequenceJobs[normalized]
        if (existing != null) {
            if (normalized !in readyWaitDevices) return false
            existing.cancel()
        }

        // Sequences rely on remote-status notifications. Subscribing is idempotent,
        // and being queued it naturally completes before the sequence's writes.
        port.subscribeToNotifications(normalized, SonyBluetoothConstants.REMOTE_STATUS_UUID)

        sequenceOwners.add(normalized)
        val job = scope.launch(start = CoroutineStart.LAZY) { runShutterCycle(normalized) }
        job.invokeOnCompletion {
            // Identity check: a replacement sequence may already own this device
            if (activeSequenceJobs[normalized] === job) {
                activeSequenceJobs.remove(normalized)
                sequenceOwners.remove(normalized)
            }
            // Report the map's state, not "false": on a takeover the replacement
            // sequence is still running when the cancelled job completes
            port.setShutterSequenceActive(normalized, activeSequenceJobs.containsKey(normalized))
        }
        activeSequenceJobs[normalized] = job
        port.setShutterSequenceActive(normalized, true)
        job.start()
        return true
    }

    /**
     * R1: Acquire BLE remote control for [identifier] on the current connection generation.
     * Starts FF02 subscription and FF01 probing if needed, transitioning [bleShutterStates]
     * from `Preparing` to `Ready` (or `Failed` on precondition failure / timeout).
     * Never alters user's persistent manual-disconnect intent.
     */
    fun acquireBleRemoteControl(identifier: String): Boolean {
        val id = identifier.uppercase()
        val gen = currentGeneration(id)
        prepareTimeoutJobs.remove(id)?.cancel()

        if (id in wifiOwners) {
            acquiredBleRemoteDevices.remove(id)
            acquiredGenerations.remove(id)
            publishBleShutterState(
                id,
                BleShutterState(
                    phase = BleShutterPhase.Failed,
                    connectionGeneration = gen,
                    failureReason = BleShutterFailureReason.WifiOccupied,
                ),
            )
            return false
        }
        if (!port.isConnected(id)) {
            acquiredBleRemoteDevices.remove(id)
            acquiredGenerations.remove(id)
            publishBleShutterState(
                id,
                BleShutterState(
                    phase = BleShutterPhase.Failed,
                    connectionGeneration = gen,
                    failureReason = BleShutterFailureReason.NotConnected,
                ),
            )
            return false
        }
        if (!port.hasRemoteControlCharacteristic(id)) {
            acquiredBleRemoteDevices.remove(id)
            acquiredGenerations.remove(id)
            publishBleShutterState(
                id,
                BleShutterState(
                    phase = BleShutterPhase.Failed,
                    connectionGeneration = gen,
                    failureReason = BleShutterFailureReason.RemoteControlUnavailable,
                ),
            )
            return false
        }

        acquiredBleRemoteDevices.add(id)
        acquiredGenerations[id] = gen

        // Keep an already-running operation slot intact on the same generation.
        if (id in activeOperationSlots && bleShutterState(id).connectionGeneration == gen) {
            return true
        }

        monitoredDevices.add(id)
        port.subscribeToNotifications(id, SonyBluetoothConstants.REMOTE_STATUS_UUID)

        return if (port.isRemoteFeatureActive(id)) {
            publishBleShutterState(
                id,
                BleShutterState(phase = BleShutterPhase.Ready, connectionGeneration = gen),
            )
            true
        } else {
            publishBleShutterState(
                id,
                BleShutterState(phase = BleShutterPhase.Preparing, connectionGeneration = gen),
            )
            startRemoteStatusMonitoring(id)
            schedulePrepareTimeout(id, gen)
            true
        }
    }

    private fun schedulePrepareTimeout(id: String, gen: Long) {
        prepareTimeoutJobs.remove(id)?.cancel()
        prepareTimeoutJobs[id] = scope.launch {
            delay(prepareTimeoutMs.milliseconds)
            val current = bleShutterState(id)
            if (id in acquiredBleRemoteDevices &&
                acquiredGenerations[id] == gen &&
                currentGeneration(id) == gen &&
                current.phase == BleShutterPhase.Preparing &&
                id !in activeOperationSlots
            ) {
                stopProbeLoop(id)
                publishBleShutterState(
                    id,
                    BleShutterState(
                        phase = BleShutterPhase.Failed,
                        connectionGeneration = gen,
                        failureReason = BleShutterFailureReason.Timeout,
                    ),
                )
            }
        }
    }

    /**
     * R1: Synchronously claim the single-operation slot on the confined thread and execute
     * one managed single-shot shutter cycle. Rejects duplicate submissions during `Pressing`,
     * `Releasing` (including trailing ready-wait), `Uncertain`, `Preparing`, or `Failed`.
     * Never auto-resends after `ShutterFullPress` may have reached the camera.
     */
    fun triggerBleShutter(identifier: String): Boolean {
        val id = identifier.uppercase()
        val gen = currentGeneration(id)
        val current = bleShutterState(id)

        if (id in wifiOwners) return false
        if (!port.isConnected(id)) return false
        if (!port.isRemoteFeatureActive(id)) return false
        if (id in activeOperationSlots || id in activeSequenceJobs || id in readyWaitDevices) return false
        if (current.phase != BleShutterPhase.Ready || current.connectionGeneration != gen) return false

        val opId = ++nextOperationId
        activeOperationSlots.add(id)
        sequenceOwners.add(id)
        port.subscribeToNotifications(id, SonyBluetoothConstants.REMOTE_STATUS_UUID)

        publishBleShutterState(
            id,
            BleShutterState(
                phase = BleShutterPhase.Pressing,
                connectionGeneration = gen,
                operationId = opId,
            ),
        )

        val job = scope.launch(start = CoroutineStart.LAZY) {
            runManagedShutterCycle(id, gen, opId)
        }
        job.invokeOnCompletion {
            if (activeSequenceJobs[id] === job) {
                activeSequenceJobs.remove(id)
                sequenceOwners.remove(id)
                activeOperationSlots.remove(id)
            }
            port.setShutterSequenceActive(id, activeSequenceJobs.containsKey(id))
        }
        activeSequenceJobs[id] = job
        port.setShutterSequenceActive(id, true)
        job.start()
        return true
    }

    /**
     * R1: Best-effort release on leaving the BLE remote screen. Stops probing, cancels any
     * in-flight shutter operation (which runs bounded button release if still connected on the
     * same generation), and returns state to `Idle` without modifying `deviceEnabled`.
     */
    suspend fun releaseBleRemoteControl(
        identifier: String,
        cleanupTimeoutMs: Long = RELEASE_CLEANUP_TIMEOUT_MS,
    ) {
        val id = identifier.uppercase()
        val gen = currentGeneration(id)
        acquiredBleRemoteDevices.remove(id)
        acquiredGenerations.remove(id)
        prepareTimeoutJobs.remove(id)?.cancel()
        monitoredDevices.remove(id)
        activeProbeJobs.remove(id)?.cancel()

        val sequence = activeSequenceJobs[id]
        if (sequence != null) {
            sequence.cancel()
            withTimeoutOrNull(cleanupTimeoutMs.milliseconds) {
                sequence.join()
            }
        }
        if (currentGeneration(id) == gen && id !in activeOperationSlots) {
            publishBleShutterState(id, BleShutterState(phase = BleShutterPhase.Idle, connectionGeneration = gen))
        }
    }

    private fun sendReleaseCommand(id: String, command: RemoteCommand): Boolean {
        if (!port.isConnected(id)) return false
        return port.writeCharacteristic(
            id,
            SonyBluetoothConstants.REMOTE_CHARACTERISTIC_UUID,
            command.payload,
        )
    }

    private suspend fun runManagedShutterCycle(id: String, gen: Long, opId: Long) = coroutineScope {
        fun isSameGeneration(): Boolean = currentGeneration(id) == gen
        fun canSendOnSameGeneration(): Boolean = isSameGeneration() && port.isConnected(id)

        var halfMayBePressed = false
        var fullMayBePressed = false
        var fullPressAttempted = false
        var exposureStarted = false
        var halfReleased = true
        var fullReleased = true
        var abortedBeforeFullPress = false
        var failureReasonBeforeFullPress: BleShutterFailureReason? = null
        var completedNormally = false
        var readyWaiter: Deferred<ByteArray?>? = null

        try {
            if (!canSendOnSameGeneration()) {
                abortedBeforeFullPress = true
                failureReasonBeforeFullPress = BleShutterFailureReason.Disconnected
                return@coroutineScope
            }

            // Step 1: Half-press (cleanup scope is already established above).
            val halfWaiter = async(start = CoroutineStart.UNDISPATCHED) {
                awaitStatus(id, FOCUS_CONFIRM_TIMEOUT_MS) { isFocusAcquired(it) }
            }
            if (!sendCommand(id, RemoteCommand.ShutterHalfPress)) {
                halfWaiter.cancel()
                abortedBeforeFullPress = true
                failureReasonBeforeFullPress = BleShutterFailureReason.CommandRejected
                return@coroutineScope
            }
            halfMayBePressed = true
            halfWaiter.await()

            currentCoroutineContext().ensureActive()
            if (!canSendOnSameGeneration()) {
                abortedBeforeFullPress = true
                failureReasonBeforeFullPress = BleShutterFailureReason.Disconnected
                return@coroutineScope
            }

            // Step 2: Full-press — once enqueued, never auto-retry.
            val fullWaiter = async(start = CoroutineStart.UNDISPATCHED) {
                awaitStatus(id, SHUTTER_ACTIVE_TIMEOUT_MS) { isShutterActive(it) }
            }
            if (!sendCommand(id, RemoteCommand.ShutterFullPress)) {
                fullWaiter.cancel()
                abortedBeforeFullPress = true
                failureReasonBeforeFullPress = BleShutterFailureReason.CommandRejected
                return@coroutineScope
            }
            fullMayBePressed = true
            fullPressAttempted = true
            val fullStatus = fullWaiter.await()
            if (fullStatus != null) {
                exposureStarted = true
                // Subscribe to ready status before releasing so fast exposures are not missed.
                readyWaiter = async(start = CoroutineStart.UNDISPATCHED) {
                    awaitStatus(id, SHUTTER_READY_TIMEOUT_MS) { isCameraReady(it) }
                }
            } else {
                log.w { "Camera $id did not report shutter active after full press" }
            }
            completedNormally = true
        } finally {
            withContext(NonCancellable) {
                if (isSameGeneration() && (fullMayBePressed || halfMayBePressed)) {
                    publishBleShutterState(
                        id,
                        BleShutterState(
                            phase = BleShutterPhase.Releasing,
                            connectionGeneration = gen,
                            operationId = opId,
                            exposureStarted = exposureStarted,
                        ),
                    )
                }

                if (fullMayBePressed) {
                    fullReleased = canSendOnSameGeneration() &&
                        sendReleaseCommand(id, RemoteCommand.ShutterFullRelease)
                }
                if (halfMayBePressed) {
                    halfReleased = canSendOnSameGeneration() &&
                        sendReleaseCommand(id, RemoteCommand.ShutterHalfRelease)
                }

                val releaseUncertain = !fullReleased || !halfReleased

                if (!isSameGeneration()) {
                    readyWaiter?.cancel()
                } else if (abortedBeforeFullPress) {
                    readyWaiter?.cancel()
                    if (releaseUncertain) {
                        publishBleShutterState(
                            id,
                            BleShutterState(
                                phase = BleShutterPhase.Uncertain,
                                connectionGeneration = gen,
                                operationId = opId,
                                exposureStarted = false,
                                releaseUncertain = true,
                            ),
                        )
                    } else {
                        publishBleShutterState(
                            id,
                            BleShutterState(
                                phase = BleShutterPhase.Failed,
                                connectionGeneration = gen,
                                operationId = opId,
                                failureReason = failureReasonBeforeFullPress ?: BleShutterFailureReason.CommandRejected,
                            ),
                        )
                    }
                } else if (!completedNormally || !exposureStarted || releaseUncertain) {
                    readyWaiter?.cancel()
                    if (fullPressAttempted || releaseUncertain) {
                        publishBleShutterState(
                            id,
                            BleShutterState(
                                phase = BleShutterPhase.Uncertain,
                                connectionGeneration = gen,
                                operationId = opId,
                                exposureStarted = exposureStarted,
                                releaseUncertain = releaseUncertain,
                            ),
                        )
                    } else if (id in acquiredBleRemoteDevices && port.isConnected(id) && port.isRemoteFeatureActive(id)) {
                        publishBleShutterState(
                            id,
                            BleShutterState(phase = BleShutterPhase.Ready, connectionGeneration = gen),
                        )
                    } else {
                        publishBleShutterState(
                            id,
                            BleShutterState(
                                phase = if (port.isConnected(id)) BleShutterPhase.Idle else BleShutterPhase.Failed,
                                connectionGeneration = gen,
                                failureReason = if (port.isConnected(id)) null else BleShutterFailureReason.Disconnected,
                            ),
                        )
                    }
                } else if (readyWaiter != null) {
                    // Trailing ready-wait: R1 keeps phase == Releasing so duplicate clicks are rejected.
                    readyWaitDevices.add(id)
                    try {
                        if (readyWaiter.await() == null) {
                            log.w { "Camera $id did not report ready after managed shutter cycle" }
                        }
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        // Disconnected or cancelled while waiting for camera ready; release status is published below.
                    } finally {
                        readyWaitDevices.remove(id)
                    }
                    if (isSameGeneration()) {
                        val nextPhase = when {
                            !port.isConnected(id) -> BleShutterPhase.Failed
                            id in acquiredBleRemoteDevices && port.isRemoteFeatureActive(id) -> BleShutterPhase.Ready
                            id in acquiredBleRemoteDevices -> BleShutterPhase.Preparing
                            else -> BleShutterPhase.Idle
                        }
                        publishBleShutterState(
                            id,
                            BleShutterState(
                                phase = nextPhase,
                                connectionGeneration = gen,
                                failureReason = if (nextPhase == BleShutterPhase.Failed) {
                                    BleShutterFailureReason.Disconnected
                                } else null,
                            ),
                        )
                    }
                }
            }
        }
    }

    private enum class StepResult { NotSent, TimedOut, Confirmed }

    private suspend fun runShutterCycle(identifier: String) = coroutineScope {
        // Half press — wait for the camera to confirm focus before the full press
        when (
            sendAndAwaitStatus(
                identifier,
                RemoteCommand.ShutterHalfPress,
                FOCUS_CONFIRM_TIMEOUT_MS
            ) {
                isFocusAcquired(it)
            }
        ) {
            StepResult.NotSent -> return@coroutineScope
            StepResult.TimedOut ->
                log.w { "Camera $identifier did not confirm focus, proceeding with full press" }

            StepResult.Confirmed -> Unit
        }

        var readyWaiter: Deferred<ByteArray?>? = null
        try {
            // Full press — wait until the camera reports the exposure started
            val fullPress =
                sendAndAwaitStatus(
                    identifier,
                    RemoteCommand.ShutterFullPress,
                    SHUTTER_ACTIVE_TIMEOUT_MS
                ) {
                    isShutterActive(it)
                }
            if (fullPress == StepResult.Confirmed) {
                // Subscribe to the ready status before releasing, so a fast exposure
                // finishing right after the release is not missed
                readyWaiter = async(start = CoroutineStart.UNDISPATCHED) {
                    awaitStatus(identifier, SHUTTER_READY_TIMEOUT_MS) { isCameraReady(it) }
                }
            } else if (fullPress == StepResult.TimedOut) {
                // No shutter-active status means the exposure never started (e.g.
                // release priority refused without focus lock). The camera never
                // left the ready state and only notifies on changes, so waiting
                // for ready would always run out the full timeout — skip it.
                log.w { "Camera $identifier did not report the shutter active, skipping the ready wait" }
            }
            if (fullPress != StepResult.NotSent) {
                sendCommand(identifier, RemoteCommand.ShutterFullRelease)
            }
        } finally {
            // Always release the half press, also on cancellation (disconnect)
            sendCommand(identifier, RemoteCommand.ShutterHalfRelease)
        }

        // Wait until the exposure finished and the camera is ready again. A new
        // shutter press may cancel this wait (see startShutterSequence).
        if (readyWaiter != null) {
            readyWaitDevices.add(identifier)
            try {
                if (readyWaiter.await() == null) {
                    log.w { "Camera $identifier did not report ready after the shutter cycle" }
                }
            } finally {
                readyWaitDevices.remove(identifier)
            }
        }
    }

    /**
     * Start waiting for a matching status notification, THEN send [command], so a
     * response arriving immediately after the write cannot be missed.
     */
    private suspend fun sendAndAwaitStatus(
        identifier: String,
        command: RemoteCommand,
        timeoutMs: Long,
        predicate: (ByteArray) -> Boolean,
    ): StepResult = coroutineScope {
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            awaitStatus(identifier, timeoutMs, predicate)
        }
        if (!sendCommand(identifier, command)) {
            waiter.cancel()
            return@coroutineScope StepResult.NotSent
        }
        if (waiter.await() != null) StepResult.Confirmed else StepResult.TimedOut
    }

    /**
     * Suspend until a remote-status notification from [identifier] matches
     * [predicate], or return `null` after [timeoutMs].
     */
    private suspend fun awaitStatus(
        identifier: String,
        timeoutMs: Long,
        predicate: (ByteArray) -> Boolean,
    ): ByteArray? = withTimeoutOrNull(timeoutMs.milliseconds) {
        statusUpdates
            .filter { it.first == identifier }
            .map { it.second }
            .first(predicate)
    }

    /**
     * Send the shutter-up command for [identifier].
     */
    fun sendShutterUp(identifier: String): Boolean {
        val normalized = identifier.uppercase()
        return port.writeCharacteristic(
            normalized,
            SonyBluetoothConstants.REMOTE_CHARACTERISTIC_UUID,
            SonyBluetoothConstants.FULL_SHUTTER_UP_COMMAND,
        )
    }

    /**
     * Trigger a remote shutter action (down command only) for [identifier].
     *
     * The shared BLE callback flow sends shutter-up only after camera acknowledgement.
     */
    fun triggerRemoteShutter(identifier: String): Boolean {
        return handleRemoteShutterRequest(identifier)
    }

    /** Finish BLE button releases before Wi-Fi takes ownership of camera controls. */
    suspend fun claimWifiControls(identifier: String) {
        val id = identifier.uppercase()
        val gen = currentGeneration(id)
        wifiOwners.add(id)
        prepareTimeoutJobs.remove(id)?.cancel()
        val probe = activeProbeJobs.remove(id)
        val sequence = activeSequenceJobs[id]
        probe?.cancel()
        sequence?.cancel()
        probe?.join()
        sequence?.join()
        if (acquiredBleRemoteDevices.remove(id)) {
            acquiredGenerations.remove(id)
            if (currentGeneration(id) == gen && bleShutterState(id).phase != BleShutterPhase.Uncertain) {
                publishBleShutterState(
                    id,
                    BleShutterState(
                        phase = BleShutterPhase.Failed,
                        connectionGeneration = gen,
                        failureReason = BleShutterFailureReason.WifiOccupied,
                    ),
                )
            }
        }
        port.setRemoteFeatureActive(id, false)
    }

    fun releaseWifiControls(identifier: String) {
        val id = identifier.uppercase()
        wifiOwners.remove(id)
        if (id in monitoredDevices && port.isConnected(id)) startRemoteStatusMonitoring(id)
    }

    /** Cancel BLE probes and sequences on disconnect, retaining any Wi-Fi ownership. */
    fun cancelProbe(identifier: String) {
        val normalized = identifier.uppercase()
        val gen = currentGeneration(normalized)
        val wasAcquired = acquiredBleRemoteDevices.remove(normalized)
        acquiredGenerations.remove(normalized)
        prepareTimeoutJobs.remove(normalized)?.cancel()
        monitoredDevices.remove(normalized)
        activeProbeJobs.remove(normalized)?.cancel()
        activeSequenceJobs.remove(normalized)?.cancel()
        sequenceOwners.remove(normalized)
        readyWaitDevices.remove(normalized)
        activeOperationSlots.remove(normalized)
        if (wasAcquired && currentGeneration(normalized) == gen) {
            val currentPhase = bleShutterState(normalized).phase
            if (currentPhase == BleShutterPhase.Preparing ||
                currentPhase == BleShutterPhase.Ready ||
                currentPhase == BleShutterPhase.Pressing ||
                currentPhase == BleShutterPhase.Releasing
            ) {
                publishBleShutterState(
                    normalized,
                    BleShutterState(
                        phase = BleShutterPhase.Failed,
                        connectionGeneration = gen,
                        failureReason = BleShutterFailureReason.Disconnected,
                    ),
                )
            }
        }
    }

    /** Cancels any in-flight R1 shutter sequence (e.g. when app goes to background) without tearing down BLE/GPS. */
    fun cancelActiveBleShutter(identifier: String) {
        val id = identifier.uppercase()
        activeSequenceJobs.remove(id)?.cancel()
    }

    /**
     * Cancel all active probe loops and command sequences (e.g. on service destroy).
     */
    fun cancelAllProbes() {
        acquiredBleRemoteDevices.clear()
        acquiredGenerations.clear()
        prepareTimeoutJobs.values.forEach { it.cancel() }
        prepareTimeoutJobs.clear()
        monitoredDevices.clear()
        activeProbeJobs.values.forEach { it.cancel() }
        activeProbeJobs.clear()
        // Snapshot: completion handlers may remove their own entry synchronously
        activeSequenceJobs.values.toList().forEach { it.cancel() }
        activeSequenceJobs.clear()
        sequenceOwners.clear()
        readyWaitDevices.clear()
        activeOperationSlots.clear()
    }

    // ---- Internal probe loop ----

    private fun startProbeLoop(identifier: String) {
        if (identifier in wifiOwners) return
        if (activeProbeJobs.containsKey(identifier)) return
        if (!port.hasRemoteControlCharacteristic(identifier)) return

        val job = scope.launch {
            delay(REMOTE_STATUS_PROBE_INITIAL_DELAY_MS.milliseconds)
            while (isActive) {
                if (!port.isConnected(identifier)) {
                    break
                }
                if (!port.hasRemoteControlCharacteristic(identifier)) {
                    break
                }

                port.writeCharacteristic(
                    identifier,
                    SonyBluetoothConstants.REMOTE_CHARACTERISTIC_UUID,
                    SonyBluetoothConstants.PROBE_COMMAND,
                )

                delay(REMOTE_STATUS_PROBE_INTERVAL_MS.milliseconds)
            }
            // Clean up our own entry when the loop exits naturally
            val currentJob = currentCoroutineContext()[Job]
            if (activeProbeJobs[identifier] === currentJob) {
                activeProbeJobs.remove(identifier)
            }
        }
        activeProbeJobs[identifier] = job
    }

    private fun stopProbeLoop(identifier: String) {
        activeProbeJobs.remove(identifier)?.cancel()
    }

    companion object {
        const val REMOTE_STATUS_PROBE_INTERVAL_MS = 3_000L
        const val REMOTE_STATUS_PROBE_INITIAL_DELAY_MS = 500L
        const val DEFAULT_PREPARE_TIMEOUT_MS = 12_000L
        const val RELEASE_CLEANUP_TIMEOUT_MS = 2_000L

        // Fallback timeouts for the status-driven shutter cycle — the cycle normally
        // advances on camera status notifications, never on these expiring.
        const val FOCUS_CONFIRM_TIMEOUT_MS = 2_000L
        const val SHUTTER_ACTIVE_TIMEOUT_MS = 2_000L
        const val SHUTTER_READY_TIMEOUT_MS = 20_000L

        /**
         * Determine if the remote feature is active based on the characteristic value.
         * Shared between Android and iOS — the byte protocol is identical.
         */
        fun isRemoteFeatureActive(value: ByteArray): Boolean {
            if (value.isEmpty()) return false
            return !value.contentEquals(byteArrayOf(0x02, 0xC3.toByte(), 0x00))
        }

        /**
         * Check if this characteristic change value indicates the platform
         * should send a shutter-up command.
         */
        fun shouldSendShutterUp(value: ByteArray): Boolean {
            return value.contentEquals(SonyBluetoothConstants.STATUS_READY)
        }

        /**
         * `true` when the camera reports the ready status (exposure finished).
         */
        fun isCameraReady(value: ByteArray): Boolean =
            value.startsWith(SonyBluetoothConstants.STATUS_READY)

        /** `true` when the camera reports the shutter active (exposure running). */
        fun isShutterActive(value: ByteArray): Boolean =
            value.startsWith(SonyBluetoothConstants.STATUS_SHUTTER_ACTIVE)

        /** `true` when the camera reports focus acquired after a half press. */
        fun isFocusAcquired(value: ByteArray): Boolean =
            value.startsWith(SonyBluetoothConstants.STATUS_FOCUS_ACQUIRED)

        /** Prefix match, so longer status payloads still qualify. */
        private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
            if (size < prefix.size) return false
            return prefix.indices.all { this[it] == prefix[it] }
        }

        /**
         * Parse the config-read characteristic value for the timezone/DST flag.
         * Shared between Android and iOS.
         */
        fun hasTimeZoneDstFlag(value: ByteArray): Boolean {
            return value.size >= 5 && (value[4].toInt() and 0x02) != 0
        }
    }
}
