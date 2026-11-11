package com.saschl.cameragps.ui.device

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.sasch.cameragps.sharednew.ui.remote.VIEWER_DETAILS_ENTER_MILLIS
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

/** Log tag of the photo viewer's details-drawer frame timing (shown in the Run log at INFO). */
internal const val VIEWER_FRAME_LOG_TAG = "AlphaViewer"

/** How long frames are timed after the drawer's enter animation has finished. */
internal const val VIEWER_FRAME_TAIL_MILLIS = 600L

/** One rendered frame, the [FrameMetrics] durations in nanoseconds. */
internal data class ViewerFrameSample(
    val totalNanos: Long,
    val inputNanos: Long = 0,
    val animationNanos: Long = 0,
    val layoutNanos: Long = 0,
    val drawNanos: Long = 0,
    val syncNanos: Long = 0,
    val commandNanos: Long = 0,
    val swapNanos: Long = 0,
    val gpuNanos: Long = 0,
)

/**
 * The frames of one drawer opening: [frames] rendered, [janky] of them longer than the display's
 * refresh period, the [worstMs] / nearest-rank [p90Ms] total durations and the stages of the worst.
 */
internal data class ViewerFrameSummary(
    val frames: Int,
    val janky: Int,
    val worstMs: Double,
    val p90Ms: Double,
    val worst: ViewerFrameSample?,
)

internal fun summarizeViewerFrames(samples: List<ViewerFrameSample>, refreshPeriodNanos: Long): ViewerFrameSummary {
    if (samples.isEmpty()) return ViewerFrameSummary(0, 0, 0.0, 0.0, null)
    val sorted = samples.map { it.totalNanos }.sorted()
    val p90 = sorted[(ceil(0.9 * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)]
    val worst = samples.maxBy { it.totalNanos }
    return ViewerFrameSummary(
        frames = samples.size,
        janky = if (refreshPeriodNanos > 0) samples.count { it.totalNanos > refreshPeriodNanos } else 0,
        worstMs = worst.totalNanos.toMs(),
        p90Ms = p90.toMs(),
        worst = worst,
    )
}

/** The one log line of an opening, e.g. "details open first=true frames=21 janky=3 (>16.7ms) worst=48.2ms p90=19.0ms worst[…]". */
internal fun viewerFrameLine(summary: ViewerFrameSummary, firstOpen: Boolean, refreshPeriodNanos: Long, endedBy: String): String {
    val head = "details open first=$firstOpen end=$endedBy frames=${summary.frames} " +
        "janky=${summary.janky} (>${refreshPeriodNanos.toMs().fmt()}ms) " +
        "worst=${summary.worstMs.fmt()}ms p90=${summary.p90Ms.fmt()}ms"
    val w = summary.worst ?: return head
    return head + " worst[input=${w.inputNanos.toMs().fmt()} anim=${w.animationNanos.toMs().fmt()} " +
        "layout=${w.layoutNanos.toMs().fmt()} draw=${w.drawNanos.toMs().fmt()} sync=${w.syncNanos.toMs().fmt()} " +
        "command=${w.commandNanos.toMs().fmt()} swap=${w.swapNanos.toMs().fmt()} gpu=${w.gpuNanos.toMs().fmt()}]"
}

private fun Long.toMs(): Double = this / 1_000_000.0
private fun Double.fmt(): String = "%.1f".format(java.util.Locale.ROOT, this)

/**
 * Times the window's frames while the viewer's details drawer opens: from the open tap until the
 * enter animation plus [VIEWER_FRAME_TAIL_MILLIS] has passed (or the drawer closes earlier), then
 * logs one [viewerFrameLine]. The FrameMetrics listener is registered only inside that window and
 * receives its callbacks on a background thread that exists only while timing.
 */
internal class ViewerFrameMetricsProbe(private val window: Window) {
    private val main = Handler(Looper.getMainLooper())
    private val samples = ArrayList<ViewerFrameSample>(64)
    private var thread: HandlerThread? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private var firstOpen = false
    private val timeout = Runnable { finish("timer") }

    /** Main thread. */
    fun onDetailsVisibleChanged(open: Boolean) {
        if (open) start() else finish("closed")
    }

    private fun start() {
        finish("reopened")
        firstOpen = processFirstOpen.compareAndSet(false, true)
        synchronized(samples) { samples.clear() }
        val worker = HandlerThread("AlphaViewerFrames").apply { start() }
        val callback = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val sample = metrics.toSample()
            synchronized(samples) { samples += sample }
        }
        try {
            window.addOnFrameMetricsAvailableListener(callback, Handler(worker.looper))
        } catch (e: RuntimeException) {
            worker.quit()
            Timber.tag(VIEWER_FRAME_LOG_TAG).d("frame metrics unavailable: ${e.message}")
            return
        }
        thread = worker
        listener = callback
        main.postDelayed(timeout, VIEWER_DETAILS_ENTER_MILLIS + VIEWER_FRAME_TAIL_MILLIS)
    }

    /** Main thread: stops timing (if running) and logs the opening. */
    fun finish(endedBy: String) {
        val callback = listener ?: return
        main.removeCallbacks(timeout)
        listener = null
        try {
            window.removeOnFrameMetricsAvailableListener(callback)
        } catch (_: RuntimeException) {
        }
        thread?.quitSafely()
        thread = null
        val taken = synchronized(samples) { samples.toList().also { samples.clear() } }
        val period = refreshPeriodNanos()
        Timber.tag(VIEWER_FRAME_LOG_TAG).i(viewerFrameLine(summarizeViewerFrames(taken, period), firstOpen, period, endedBy))
    }

    private fun refreshPeriodNanos(): Long {
        val rate = window.decorView.display?.refreshRate ?: 60f
        return if (rate > 0f) (1_000_000_000.0 / rate).toLong() else 16_666_667L
    }

    private companion object {
        /** Whether this process has timed an opening yet (the first one pays class loading / JIT). */
        val processFirstOpen = AtomicBoolean(false)
    }
}

private fun FrameMetrics.toSample() = ViewerFrameSample(
    totalNanos = getMetric(FrameMetrics.TOTAL_DURATION),
    inputNanos = getMetric(FrameMetrics.INPUT_HANDLING_DURATION),
    animationNanos = getMetric(FrameMetrics.ANIMATION_DURATION),
    layoutNanos = getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
    drawNanos = getMetric(FrameMetrics.DRAW_DURATION),
    syncNanos = getMetric(FrameMetrics.SYNC_DURATION),
    commandNanos = getMetric(FrameMetrics.COMMAND_ISSUE_DURATION),
    swapNanos = getMetric(FrameMetrics.SWAP_BUFFERS_DURATION),
    gpuNanos = getMetric(FrameMetrics.GPU_DURATION),
)

/** The probe for the current window, released (and its last opening logged) with the composition. */
@Composable
internal fun rememberViewerFrameMetricsProbe(window: Window?): (Boolean) -> Unit {
    val probe = remember(window) { window?.let { ViewerFrameMetricsProbe(it) } }
    DisposableEffect(probe) { onDispose { probe?.finish("left") } }
    return remember(probe) { { open: Boolean -> probe?.onDetailsVisibleChanged(open) } }
}
