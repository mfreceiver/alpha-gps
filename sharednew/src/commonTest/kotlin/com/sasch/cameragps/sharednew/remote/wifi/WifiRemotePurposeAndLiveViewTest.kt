package com.sasch.cameragps.sharednew.remote.wifi

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.sasch.cameragps.sharednew.bluetooth.session.CameraSessionRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WifiRemotePurposeAndLiveViewTest : WifiLoggingTest() {
    private class TestBitmap(override val width: Int = 640, override val height: Int = 480) : ImageBitmap {
        override val colorSpace = ColorSpaces.Srgb
        override val hasAlpha = false
        override val config = ImageBitmapConfig.Argb8888
        override fun prepareToDraw() = Unit
        override fun readPixels(
            buffer: IntArray,
            startX: Int,
            startY: Int,
            width: Int,
            height: Int,
            bufferOffset: Int,
            stride: Int,
        ) = Unit
    }

    private class TestConnection(
        val purpose: WifiSessionPurpose,
    ) : WifiRemoteConnection {
        override val cameraName = "ILCE-7M4"
        override val canCapture = purpose == WifiSessionPurpose.RemoteControl
        override val canTransferImages = true
        override val hasLiveViewStream = purpose == WifiSessionPurpose.RemoteControl
        val framesChannel = Channel<WifiLiveViewFrame>(Channel.UNLIMITED)
        var failNextPreview = false
        var previewOpenCount = 0
        var captureCount = 0
        var captureGate: CompletableDeferred<WifiCaptureStatus>? = null
        var closed = false

        override val images: Flow<ImageBitmap> = emptyFlow()
        override val liveViewFrames: Flow<WifiLiveViewFrame> = flow {
            previewOpenCount++
            if (failNextPreview) {
                failNextPreview = false
                error("live view stream error")
            }
            for (frame in framesChannel) {
                emit(frame)
            }
        }
        private val losses = Channel<Unit>(Channel.CONFLATED)
        override val lost: Flow<Unit> = losses.receiveAsFlow()

        override suspend fun capture(): WifiCaptureStatus {
            captureCount++
            return captureGate?.await() ?: WifiCaptureStatus.Captured
        }

        override suspend fun close() {
            closed = true
            framesChannel.close()
        }
    }

    @Test
    fun connectsWithRequestedPurposeAndExposesLiveViewFrameBytes() = runTest {
        val id = "AA:BB:CC:DD:EE:FF"
        val registry = CameraSessionRegistry()
        val openedPurposes = mutableListOf<WifiSessionPurpose>()
        var currentConnection: TestConnection? = null
        val automatic = object : WifiAutomaticConnector {
            override val phonePreconditions: WifiPreconditions? = null
            override suspend fun open(
                identifier: String,
                scope: CoroutineScope,
                onPhase: (WifiRemotePhase) -> Unit,
            ): WifiRemoteConnection = open(identifier, scope, WifiSessionPurpose.Browse, onPhase)

            override suspend fun open(
                identifier: String,
                scope: CoroutineScope,
                purpose: WifiSessionPurpose,
                onPhase: (WifiRemotePhase) -> Unit,
            ): WifiRemoteConnection {
                openedPurposes += purpose
                onPhase(WifiRemotePhase.OpeningSession)
                return TestConnection(purpose).also { currentConnection = it }
            }
        }
        val controller = WifiRemoteController(
            scope = backgroundScope,
            registry = registry,
            connector = WifiRemoteConnector { _, _ -> error("unused") },
            claimControls = {},
            releaseControls = {},
            automaticConnector = automatic,
            diagnosticsDispatcher = UnconfinedTestDispatcher(testScheduler),
            liveViewStaleTimeoutMs = 2_000L,
        )

        controller.connectAutomatically(id, WifiSessionPurpose.RemoteControl)
        runCurrent()

        assertEquals(listOf(WifiSessionPurpose.RemoteControl), openedPurposes)
        val readyState = registry.get(id)!!.wifiRemote
        assertEquals(WifiSessionPurpose.RemoteControl, readyState.purpose)
        assertTrue(readyState.isControlReady)
        assertTrue(readyState.supportsLiveView)
        assertTrue(readyState.canCaptureStill)

        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02)
        currentConnection!!.framesChannel.send(WifiLiveViewFrame(TestBitmap(), jpeg))
        runCurrent()

        val streamingState = registry.get(id)!!.wifiRemote
        assertTrue(streamingState.isLiveViewReady)
        assertFalse(streamingState.liveViewPaused)
        assertNotNull(controller.image.value)
        assertContentEquals(jpeg, controller.liveViewFrame.value?.jpegBytes)

        // Stale timeout freezes the last frame with liveViewPaused = true while keeping control Ready.
        advanceTimeBy(2_100L)
        val pausedState = registry.get(id)!!.wifiRemote
        assertTrue(pausedState.liveViewPaused)
        assertFalse(pausedState.isLiveViewReady)
        assertTrue(pausedState.isControlReady)
        assertNotNull(controller.image.value)

        // Capture still works while live view is paused.
        controller.capture(id)
        runCurrent()
        assertEquals(1, currentConnection.captureCount)

        // New frame clears liveViewPaused.
        currentConnection.framesChannel.send(WifiLiveViewFrame(TestBitmap(), jpeg))
        runCurrent()
        assertFalse(registry.get(id)!!.wifiRemote.liveViewPaused)
        assertTrue(registry.get(id)!!.wifiRemote.isLiveViewReady)
    }

    @Test
    fun switchPurposeReusesSamePurposeAndReplacesDifferentPurpose() = runTest {
        val id = "AA:BB:CC:DD:EE:FF"
        val registry = CameraSessionRegistry()
        val connections = mutableListOf<TestConnection>()
        val automatic = object : WifiAutomaticConnector {
            override val phonePreconditions: WifiPreconditions? = null
            override suspend fun open(
                identifier: String,
                scope: CoroutineScope,
                onPhase: (WifiRemotePhase) -> Unit,
            ): WifiRemoteConnection = open(identifier, scope, WifiSessionPurpose.Browse, onPhase)

            override suspend fun open(
                identifier: String,
                scope: CoroutineScope,
                purpose: WifiSessionPurpose,
                onPhase: (WifiRemotePhase) -> Unit,
            ): WifiRemoteConnection = TestConnection(purpose).also { connections += it }
        }
        val controller = WifiRemoteController(
            scope = backgroundScope,
            registry = registry,
            connector = WifiRemoteConnector { _, _ -> error("unused") },
            claimControls = {},
            releaseControls = {},
            automaticConnector = automatic,
            diagnosticsDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        assertTrue(controller.switchPurpose(id, WifiSessionPurpose.Browse))
        runCurrent()
        assertEquals(1, connections.size)
        assertEquals(WifiSessionPurpose.Browse, registry.get(id)!!.wifiRemote.purpose)

        // Same purpose reuses existing session.
        assertTrue(controller.switchPurpose(id, WifiSessionPurpose.Browse))
        runCurrent()
        assertEquals(1, connections.size)

        // Another camera cannot preempt.
        assertFalse(controller.switchPurpose("11:22:33:44:55:66", WifiSessionPurpose.RemoteControl))
        assertEquals(1, connections.size)

        // Switching to RemoteControl closes Browse first, then opens RemoteControl.
        assertTrue(controller.switchPurpose(id, WifiSessionPurpose.RemoteControl))
        runCurrent()
        assertTrue(connections[0].closed)
        assertEquals(2, connections.size)
        assertEquals(WifiSessionPurpose.RemoteControl, registry.get(id)!!.wifiRemote.purpose)
    }

    @Test
    fun retryLiveViewReopensOnlyStreamWhenControlRemainsReady() = runTest {
        val id = "AA:BB:CC:DD:EE:FF"
        val registry = CameraSessionRegistry()
        val conn = TestConnection(WifiSessionPurpose.RemoteControl).apply { failNextPreview = true }
        val controller = WifiRemoteController(
            scope = backgroundScope,
            registry = registry,
            connector = WifiRemoteConnector { _, _ -> conn },
            claimControls = {},
            releaseControls = {},
            diagnosticsDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        controller.connect(id, "192.168.122.1", WifiSessionPurpose.RemoteControl)
        runCurrent()

        val failedPreviewState = registry.get(id)!!.wifiRemote
        assertEquals(WifiPreviewStatus.Failed, failedPreviewState.preview)
        assertTrue(failedPreviewState.isControlReady)
        assertTrue(failedPreviewState.canCaptureStill)
        assertEquals(1, conn.previewOpenCount)

        controller.retryLiveView(id)
        runCurrent()
        conn.framesChannel.send(WifiLiveViewFrame(TestBitmap()))
        runCurrent()

        assertEquals(2, conn.previewOpenCount)
        assertEquals(WifiPreviewStatus.Streaming, registry.get(id)!!.wifiRemote.preview)
    }

    @Test
    fun backgroundVisibilityClosesRemoteControlWifiImmediatelyWithoutAffectingBle() = runTest {
        val id = "AA:BB:CC:DD:EE:FF"
        val registry = CameraSessionRegistry()
        val conn = TestConnection(WifiSessionPurpose.RemoteControl).apply {
            captureGate = CompletableDeferred()
        }
        var releasedControls = 0
        val controller = WifiRemoteController(
            scope = backgroundScope,
            registry = registry,
            connector = WifiRemoteConnector { _, _ -> conn },
            claimControls = {},
            releaseControls = { releasedControls++ },
            diagnosticsDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        controller.connect(id, "192.168.122.1", WifiSessionPurpose.RemoteControl)
        runCurrent()
        conn.framesChannel.send(WifiLiveViewFrame(TestBitmap()))
        runCurrent()

        // Even while a capture is in flight, live view + capture are NOT user downloads.
        controller.capture(id)
        runCurrent()
        controller.onAppVisibilityChanged(false)
        runCurrent()

        assertTrue(conn.closed)
        assertNull(controller.owner.value)
        assertEquals(1, releasedControls)
        assertEquals(WifiRemotePhase.Idle, registry.get(id)?.wifiRemote?.phase ?: WifiRemotePhase.Idle)
    }
}
