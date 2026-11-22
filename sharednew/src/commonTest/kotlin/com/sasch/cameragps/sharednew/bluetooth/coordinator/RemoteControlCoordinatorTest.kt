package com.sasch.cameragps.sharednew.bluetooth.coordinator

import com.diamondedge.logging.FixedLogLevel
import com.diamondedge.logging.KmLogging
import com.diamondedge.logging.PlatformLogger
import com.sasch.cameragps.sharednew.bluetooth.SonyBluetoothConstants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteControlCoordinatorTest {

    @BeforeTest
    fun disablePlatformLogging() {
        KmLogging.setLoggers()
    }

    @AfterTest
    fun restorePlatformLogging() {
        KmLogging.setLoggers(PlatformLogger(FixedLogLevel(true)))
    }

    // ---- Pure function tests ----

    @Test
    fun isRemoteFeatureActive_emptyArray_returnsFalse() {
        assertFalse(RemoteControlCoordinator.isRemoteFeatureActive(byteArrayOf()))
    }

    @Test
    fun isRemoteFeatureActive_inactiveValue_returnsFalse() {
        val inactive = byteArrayOf(0x02, 0xC3.toByte(), 0x00)
        assertFalse(RemoteControlCoordinator.isRemoteFeatureActive(inactive))
    }

    @Test
    fun isRemoteFeatureActive_activeValue_returnsTrue() {
        val active = byteArrayOf(0x02, 0xA0.toByte(), 0x00)
        assertTrue(RemoteControlCoordinator.isRemoteFeatureActive(active))
    }

    @Test
    fun shouldSendShutterUp_matchingValue_returnsTrue() {
        val value = byteArrayOf(0x02, 0xA0.toByte(), 0x00)
        assertTrue(RemoteControlCoordinator.shouldSendShutterUp(value))
    }

    @Test
    fun shouldSendShutterUp_nonMatchingValue_returnsFalse() {
        val value = byteArrayOf(0x02, 0xC3.toByte(), 0x00)
        assertFalse(RemoteControlCoordinator.shouldSendShutterUp(value))
    }

    @Test
    fun hasTimeZoneDstFlag_shortArray_returnsFalse() {
        assertFalse(RemoteControlCoordinator.hasTimeZoneDstFlag(byteArrayOf(0, 1, 2, 3)))
    }

    @Test
    fun hasTimeZoneDstFlag_flagSet_returnsTrue() {
        val value = byteArrayOf(0, 0, 0, 0, 0x02)
        assertTrue(RemoteControlCoordinator.hasTimeZoneDstFlag(value))
    }

    @Test
    fun hasTimeZoneDstFlag_flagNotSet_returnsFalse() {
        val value = byteArrayOf(0, 0, 0, 0, 0x01)
        assertFalse(RemoteControlCoordinator.hasTimeZoneDstFlag(value))
    }

    // ---- Coordinator integration tests with fake port ----

    @Test
    fun wifiOwnershipStopsBleProbesAndPressesUntilReleased() = runTest {
        val id = "AA:BB:CC:DD:EE:FF"
        val port = FakeBleGattPort()
        port.connectedDevices.add(id)
        port.devicesWithRemoteControl.add(id)
        port.remoteActiveDevices.add(id)
        val coordinator = RemoteControlCoordinator(port, backgroundScope)
        coordinator.startRemoteStatusMonitoring(id)
        coordinator.claimWifiControls(id)
        val writes = port.writtenCharacteristics.size
        assertFalse(coordinator.sendCommand(id, RemoteCommand.ShutterFullPress))
        assertFalse(coordinator.startShutterSequence(id))
        assertFalse(coordinator.onRemoteStatusChanged(id, byteArrayOf(2, 0xa0.toByte(), 0)))
        coordinator.startRemoteStatusMonitoring(id)
        advanceTimeBy(10_000)
        assertEquals(writes, port.writtenCharacteristics.size)
        coordinator.releaseWifiControls(id)
        coordinator.onRemoteStatusChanged(id, byteArrayOf(2, 0xa0.toByte(), 0))
        assertTrue(coordinator.sendCommand(id, RemoteCommand.ShutterFullPress))
    }

    @Test
    fun handleRemoteShutterRequest_notConnected_returnsFalse() = runTest {
        val port = FakeBleGattPort()
        val coordinator = RemoteControlCoordinator(port, backgroundScope)

        assertFalse(coordinator.handleRemoteShutterRequest("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun handleRemoteShutterRequest_connectedButRemoteInactive_returnsFalse() = runTest {
        val port = FakeBleGattPort()
        port.connectedDevices.add("AA:BB:CC:DD:EE:FF")
        port.devicesWithRemoteControl.add("AA:BB:CC:DD:EE:FF")
        val coordinator = RemoteControlCoordinator(port, backgroundScope)

        assertFalse(coordinator.handleRemoteShutterRequest("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun handleRemoteShutterRequest_connectedAndActive_returnsTrue() = runTest {
        val port = FakeBleGattPort()
        port.connectedDevices.add("AA:BB:CC:DD:EE:FF")
        port.devicesWithRemoteControl.add("AA:BB:CC:DD:EE:FF")
        port.remoteActiveDevices.add("AA:BB:CC:DD:EE:FF")
        val coordinator = RemoteControlCoordinator(port, backgroundScope)

        assertTrue(coordinator.handleRemoteShutterRequest("AA:BB:CC:DD:EE:FF"))
        assertEquals(1, port.writtenCharacteristics.size)
    }

    @Test
    fun onRemoteStatusChanged_activeValue_stopsProbeLoop() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        val port = FakeBleGattPort()
        port.connectedDevices.add("AA:BB:CC:DD:EE:FF")
        port.devicesWithRemoteControl.add("AA:BB:CC:DD:EE:FF")

        val coordinator = RemoteControlCoordinator(port, scope.backgroundScope)
        coordinator.startRemoteStatusMonitoring("AA:BB:CC:DD:EE:FF")

        // Advance past initial delay + one probe
        scope.advanceTimeBy(4_000)
        val probeCountBefore = port.writtenCharacteristics.size

        // Report feature as active → should stop probing
        val active = byteArrayOf(0x02, 0xA0.toByte(), 0x00)
        coordinator.onRemoteStatusChanged("AA:BB:CC:DD:EE:FF", active)

        // Advance more time — no new probes should appear
        scope.advanceTimeBy(10_000)
        assertEquals(probeCountBefore, port.writtenCharacteristics.size)
    }

    @Test
    fun identifiers_are_uppercased() = runTest {
        val port = FakeBleGattPort()
        port.connectedDevices.add("AA:BB:CC:DD:EE:FF")
        port.devicesWithRemoteControl.add("AA:BB:CC:DD:EE:FF")
        port.remoteActiveDevices.add("AA:BB:CC:DD:EE:FF")
        val coordinator = RemoteControlCoordinator(port, backgroundScope)

        // Call with lowercase — should still work
        assertTrue(coordinator.handleRemoteShutterRequest("aa:bb:cc:dd:ee:ff"))
    }

    // ---- R1 managed single-shot shutter tests ----

    @Test
    fun acquireBleRemoteControl_transitionsPreparingToReadyAndTimesOut() = runTest {
        val id = "AA:BB:CC:DD:EE:FF"
        val port = FakeBleGattPort()
        port.connectedDevices.add(id)
        port.devicesWithRemoteControl.add(id)
        var generation = 1L
        val coordinator = RemoteControlCoordinator(
            port = port,
            scope = backgroundScope,
            currentGeneration = { generation },
            prepareTimeoutMs = 5_000L,
        )

        assertTrue(coordinator.acquireBleRemoteControl(id))
        assertEquals(BleShutterPhase.Preparing, coordinator.bleShutterState(id).phase)

        // Camera reports remote active via FF02 -> Ready.
        coordinator.onRemoteStatusChanged(id, SonyBluetoothConstants.STATUS_READY)
        assertEquals(BleShutterPhase.Ready, coordinator.bleShutterState(id).phase)
        assertTrue(coordinator.bleShutterState(id).canTrigger)

        // If remote becomes inactive again and times out -> Failed(Timeout).
        coordinator.onRemoteStatusChanged(id, byteArrayOf(0x02, 0xC3.toByte(), 0x00))
        assertEquals(BleShutterPhase.Preparing, coordinator.bleShutterState(id).phase)
        advanceTimeBy(5_100L)
        assertEquals(BleShutterPhase.Failed, coordinator.bleShutterState(id).phase)
        assertEquals(BleShutterFailureReason.Timeout, coordinator.bleShutterState(id).failureReason)
    }

    @Test
    fun triggerBleShutter_rejectsDuplicatesDuringPressingAndTrailingReadyWait() = runTest {
        val id = "AA:BB:CC:DD:EE:FF"
        val port = FakeBleGattPort()
        port.connectedDevices.add(id)
        port.devicesWithRemoteControl.add(id)
        port.remoteActiveDevices.add(id)
        val coordinator = RemoteControlCoordinator(port, backgroundScope, currentGeneration = { 1L })

        assertTrue(coordinator.acquireBleRemoteControl(id))
        assertTrue(coordinator.triggerBleShutter(id))
        assertEquals(BleShutterPhase.Pressing, coordinator.bleShutterState(id).phase)
        // Duplicate click while Pressing must be rejected synchronously.
        assertFalse(coordinator.triggerBleShutter(id))

        runCurrent()
        // Focus acquired -> Full press sent.
        coordinator.onRemoteStatusChanged(id, SonyBluetoothConstants.STATUS_FOCUS_ACQUIRED)
        runCurrent()
        assertFalse(coordinator.triggerBleShutter(id))

        // Shutter active -> releases sent, enters trailing ready-wait in Releasing.
        coordinator.onRemoteStatusChanged(id, SonyBluetoothConstants.STATUS_SHUTTER_ACTIVE)
        runCurrent()
        assertEquals(BleShutterPhase.Releasing, coordinator.bleShutterState(id).phase)
        assertTrue(coordinator.bleShutterState(id).exposureStarted)
        // Duplicate click during trailing ready-wait must STILL be rejected in R1.
        assertFalse(coordinator.triggerBleShutter(id))

        // Camera ready -> returns to Ready.
        coordinator.onRemoteStatusChanged(id, SonyBluetoothConstants.STATUS_READY)
        runCurrent()
        assertEquals(BleShutterPhase.Ready, coordinator.bleShutterState(id).phase)
        val fullPressCount = port.writtenCharacteristics.count {
            it.third.contentEquals(RemoteCommand.ShutterFullPress.payload)
        }
        assertEquals(1, fullPressCount)
    }

    @Test
    fun triggerBleShutter_fullPressTimeoutMarksUncertainWithoutResending() = runTest {
        val id = "AA:BB:CC:DD:EE:FF"
        val port = FakeBleGattPort()
        port.connectedDevices.add(id)
        port.devicesWithRemoteControl.add(id)
        port.remoteActiveDevices.add(id)
        val coordinator = RemoteControlCoordinator(port, backgroundScope, currentGeneration = { 1L })

        assertTrue(coordinator.acquireBleRemoteControl(id))
        assertTrue(coordinator.triggerBleShutter(id))
        advanceTimeBy(RemoteControlCoordinator.FOCUS_CONFIRM_TIMEOUT_MS + RemoteControlCoordinator.SHUTTER_ACTIVE_TIMEOUT_MS + 100L)
        runCurrent()

        val state = coordinator.bleShutterState(id)
        assertEquals(BleShutterPhase.Uncertain, state.phase)
        assertFalse(state.releaseUncertain)
        // Full press was sent only once, never auto-retried; releases were sent.
        assertEquals(
            1,
            port.writtenCharacteristics.count { it.third.contentEquals(RemoteCommand.ShutterFullPress.payload) },
        )
        assertEquals(
            1,
            port.writtenCharacteristics.count { it.third.contentEquals(RemoteCommand.ShutterFullRelease.payload) },
        )
        assertEquals(
            1,
            port.writtenCharacteristics.count { it.third.contentEquals(RemoteCommand.ShutterHalfRelease.payload) },
        )
        // Cannot trigger again until user explicitly re-acquires.
        assertFalse(coordinator.triggerBleShutter(id))
    }

    @Test
    fun triggerBleShutter_rejectedFullPressReleasesHalfPressAndFails() = runTest {
        val id = "AA:BB:CC:DD:EE:FF"
        val port = FakeBleGattPort()
        port.connectedDevices.add(id)
        port.devicesWithRemoteControl.add(id)
        port.remoteActiveDevices.add(id)
        port.rejectPredicate = { it.contentEquals(RemoteCommand.ShutterFullPress.payload) }
        val coordinator = RemoteControlCoordinator(port, backgroundScope, currentGeneration = { 1L })

        assertTrue(coordinator.acquireBleRemoteControl(id))
        assertTrue(coordinator.triggerBleShutter(id))
        runCurrent()
        coordinator.onRemoteStatusChanged(id, SonyBluetoothConstants.STATUS_FOCUS_ACQUIRED)
        runCurrent()

        assertEquals(BleShutterPhase.Failed, coordinator.bleShutterState(id).phase)
        assertEquals(BleShutterFailureReason.CommandRejected, coordinator.bleShutterState(id).failureReason)
        assertEquals(
            1,
            port.writtenCharacteristics.count { it.third.contentEquals(RemoteCommand.ShutterHalfRelease.payload) },
        )
        assertEquals(
            0,
            port.writtenCharacteristics.count { it.third.contentEquals(RemoteCommand.ShutterFullRelease.payload) },
        )
    }

    @Test
    fun disconnectAndReconnectInvalidatesOldGenerationAndDoesNotLeakRelease() = runTest {
        val id = "AA:BB:CC:DD:EE:FF"
        val port = FakeBleGattPort()
        port.connectedDevices.add(id)
        port.devicesWithRemoteControl.add(id)
        port.remoteActiveDevices.add(id)
        var generation = 1L
        val coordinator = RemoteControlCoordinator(port, backgroundScope, currentGeneration = { generation })

        assertTrue(coordinator.acquireBleRemoteControl(id))
        assertTrue(coordinator.triggerBleShutter(id))
        runCurrent()
        coordinator.onRemoteStatusChanged(id, SonyBluetoothConstants.STATUS_FOCUS_ACQUIRED)
        runCurrent()

        // Disconnect mid-full-press, then reconnect on generation 2.
        port.connectedDevices.remove(id)
        coordinator.cancelProbe(id)
        runCurrent()
        assertEquals(BleShutterPhase.Uncertain, coordinator.bleShutterState(id).phase)
        assertTrue(coordinator.bleShutterState(id).releaseUncertain)

        val writesBeforeReconnect = port.writtenCharacteristics.size
        generation = 2L
        port.connectedDevices.add(id)
        port.remoteActiveDevices.add(id)
        assertTrue(coordinator.acquireBleRemoteControl(id))
        assertEquals(BleShutterPhase.Ready, coordinator.bleShutterState(id).phase)
        assertEquals(2L, coordinator.bleShutterState(id).connectionGeneration)
        // Old generation never wrote any release onto generation 2.
        assertEquals(
            0,
            port.writtenCharacteristics.drop(writesBeforeReconnect).count {
                it.third.contentEquals(RemoteCommand.ShutterFullRelease.payload) ||
                    it.third.contentEquals(RemoteCommand.ShutterHalfRelease.payload)
            },
        )
    }

    @Test
    fun releaseBleRemoteControl_cancelsHalfPressCleanlyAndReturnsIdle() = runTest {
        val id = "AA:BB:CC:DD:EE:FF"
        val port = FakeBleGattPort()
        port.connectedDevices.add(id)
        port.devicesWithRemoteControl.add(id)
        port.remoteActiveDevices.add(id)
        val coordinator = RemoteControlCoordinator(port, backgroundScope, currentGeneration = { 1L })

        assertTrue(coordinator.acquireBleRemoteControl(id))
        assertTrue(coordinator.triggerBleShutter(id))
        runCurrent()

        // Leave page while waiting for focus (half-press already sent).
        coordinator.releaseBleRemoteControl(id)
        runCurrent()

        assertEquals(BleShutterPhase.Idle, coordinator.bleShutterState(id).phase)
        assertEquals(
            1,
            port.writtenCharacteristics.count { it.third.contentEquals(RemoteCommand.ShutterHalfRelease.payload) },
        )
        assertEquals(
            0,
            port.writtenCharacteristics.count { it.third.contentEquals(RemoteCommand.ShutterFullPress.payload) },
        )
    }
}

/**
 * Minimal fake for testing the shared coordinator without real BLE.
 */
private class FakeBleGattPort : BleGattPort {
    override fun setAutoCorrectionState(
        identifier: String,
        setting: com.sasch.cameragps.sharednew.bluetooth.session.CameraAutoCorrectionSetting,
        state: com.sasch.cameragps.sharednew.bluetooth.session.CameraSettingState,
    ) = Unit
    val connectedDevices = mutableSetOf<String>()
    val devicesWithRemoteControl = mutableSetOf<String>()
    val remoteActiveDevices = mutableSetOf<String>()
    val shutterActiveDevices = mutableSetOf<String>()
    val writtenCharacteristics = mutableListOf<Triple<String, String, ByteArray>>()
    val subscriptions = mutableListOf<Pair<String, String>>()
    var rejectPredicate: (ByteArray) -> Boolean = { false }

    override fun writeCharacteristic(
        identifier: String,
        characteristicUuid: String,
        value: ByteArray,
    ): Boolean {
        if (rejectPredicate(value)) return false
        writtenCharacteristics.add(Triple(identifier, characteristicUuid, value))
        return true
    }

    override fun subscribeToNotifications(
        identifier: String,
        characteristicUuid: String,
    ): Boolean {
        subscriptions.add(identifier to characteristicUuid)
        return true
    }

    override fun isConnected(identifier: String): Boolean =
        identifier in connectedDevices

    override fun hasRemoteControlCharacteristic(identifier: String): Boolean =
        identifier in devicesWithRemoteControl

    override fun isRemoteFeatureActive(identifier: String): Boolean =
        identifier in remoteActiveDevices

    override fun setRemoteFeatureActive(identifier: String, active: Boolean) {
        if (active) remoteActiveDevices.add(identifier) else remoteActiveDevices.remove(identifier)
    }

    override fun setShutterSequenceActive(identifier: String, active: Boolean) {
        if (active) shutterActiveDevices.add(identifier) else shutterActiveDevices.remove(identifier)
    }

    override fun readCharacteristic(identifier: String, characteristicUuid: String): Boolean = true

    override fun setLocationDisabledByCamera(identifier: String, disabled: Boolean) = Unit
    override fun isLocationDisabledByCamera(identifier: String) = false

    override fun hasCharacteristic(identifier: String, characteristicUuid: String): Boolean =
        identifier in devicesWithRemoteControl
}
