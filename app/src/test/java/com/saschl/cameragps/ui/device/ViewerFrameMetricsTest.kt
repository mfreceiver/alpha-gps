package com.saschl.cameragps.ui.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerFrameMetricsTest {
    private val ms = 1_000_000L
    private val period60 = 16_666_667L

    private fun frame(totalMs: Double, layoutMs: Long = 0) =
        ViewerFrameSample(totalNanos = (totalMs * ms).toLong(), layoutNanos = layoutMs * ms)

    @Test
    fun noFramesGiveAnEmptySummary() {
        val summary = summarizeViewerFrames(emptyList(), period60)
        assertEquals(ViewerFrameSummary(0, 0, 0.0, 0.0, null), summary)
        assertNull(summary.worst)
    }

    @Test
    fun jankIsAFrameLongerThanTheRefreshPeriod() {
        val frames = listOf(frame(8.0), frame(16.6), frame(16.7), frame(40.0))
        assertEquals(2, summarizeViewerFrames(frames, period60).janky)
        // 120 Hz: an 8.4 ms period, so the 16.6 ms frame is late too.
        assertEquals(3, summarizeViewerFrames(frames, 8_333_333L).janky)
        assertEquals("unknown period: no jank count", 0, summarizeViewerFrames(frames, 0).janky)
    }

    @Test
    fun worstAndNearestRankP90() {
        val frames = (1..10).map { frame(it.toDouble(), layoutMs = it.toLong()) }
        val summary = summarizeViewerFrames(frames, period60)
        assertEquals(10, summary.frames)
        assertEquals(10.0, summary.worstMs, 1e-9)
        assertEquals("ceil(0.9 × 10) = 9th smallest", 9.0, summary.p90Ms, 1e-9)
        assertSame(frames.last(), summary.worst)
        assertEquals(1.0, summarizeViewerFrames(listOf(frame(1.0)), period60).p90Ms, 1e-9)
        // Unsorted input: the order of arrival does not matter.
        assertEquals(9.0, summarizeViewerFrames(frames.shuffled(java.util.Random(7)), period60).p90Ms, 1e-9)
    }

    @Test
    fun theLogLineCarriesCountsAndTheWorstFramesStages() {
        val summary = summarizeViewerFrames(listOf(frame(5.0), frame(48.25, layoutMs = 30)), period60)
        val line = viewerFrameLine(summary, firstOpen = true, refreshPeriodNanos = period60, endedBy = "timer")
        assertTrue(line, line.startsWith("details open first=true end=timer frames=2 janky=1 (>16.7ms) worst=48.3ms"))
        assertTrue(line, "layout=30.0" in line && "draw=0.0" in line && "command=0.0" in line && "gpu=0.0" in line)
        val empty = viewerFrameLine(summarizeViewerFrames(emptyList(), period60), false, period60, "closed")
        assertEquals("details open first=false end=closed frames=0 janky=0 (>16.7ms) worst=0.0ms p90=0.0ms", empty)
    }

    @Test
    fun aSealedOpeningKeepsItsFramesAndRejectsLateOnes() {
        val buffer = ViewerFrameBuffer()
        assertTrue(buffer.add(frame(5.0)))
        assertTrue(buffer.add(frame(6.0)))
        assertEquals(listOf(frame(5.0), frame(6.0)), buffer.closeAndTake())
        assertFalse("late callback of a finished opening is dropped", buffer.add(frame(99.0)))
        assertEquals("sealing again still reports the same frames", listOf(frame(5.0), frame(6.0)), buffer.closeAndTake())
    }

    @Test
    fun aLateCallbackOfTheOldOpeningNeverPollutesTheNextOne() {
        // What the probe does: one buffer per start, each callback bound to its own buffer.
        val first = ViewerFrameBuffer()
        val firstCallback = { s: ViewerFrameSample -> first.add(s) }
        firstCallback(frame(4.0))
        val firstFrames = first.closeAndTake() // finish("closed")
        val second = ViewerFrameBuffer() // start() of the next opening
        val secondCallback = { s: ViewerFrameSample -> second.add(s) }
        // The old worker is still draining: its stale frame arrives after the new opening began.
        assertFalse(firstCallback(frame(77.0)))
        secondCallback(frame(8.0))
        assertFalse(firstCallback(frame(78.0)))
        secondCallback(frame(9.0))
        assertEquals(listOf(frame(4.0)), firstFrames)
        assertEquals(listOf(frame(8.0), frame(9.0)), second.closeAndTake())
    }

    @Test
    fun lateCallbacksRacingTheSealAreEitherKeptOrDroppedNeverLeaked() {
        repeat(200) {
            val old = ViewerFrameBuffer()
            val writer = Thread { repeat(50) { old.add(frame(1.0)) } }
            writer.start()
            val taken = old.closeAndTake()
            writer.join()
            val next = ViewerFrameBuffer()
            assertTrue(taken.size <= 50)
            assertEquals("whatever the old worker adds after sealing is rejected", taken.size, old.closeAndTake().size)
            assertEquals(emptyList<ViewerFrameSample>(), next.closeAndTake())
        }
    }
}
