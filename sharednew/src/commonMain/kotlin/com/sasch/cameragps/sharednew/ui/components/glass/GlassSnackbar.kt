package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import kotlinx.coroutines.delay

/** How long a snackbar stays up. */
enum class GlassSnackbarDuration { Short, Long, Indefinite }

/**
 * Pure auto-dismiss timeout (ms), null = stays until dismissed. Short is 3 s (N9); a Short message
 * with an action gets 5 s so the action can be reached; Long is 6 s.
 */
fun snackbarTimeoutMillis(duration: GlassSnackbarDuration, hasAction: Boolean): Long? = when (duration) {
    GlassSnackbarDuration.Short -> if (hasAction) 5_000L else 3_000L
    GlassSnackbarDuration.Long -> 6_000L
    GlassSnackbarDuration.Indefinite -> null
}

/** One queued snackbar message. [id] is unique per host so a stale timer cannot dismiss a newer one. */
@Immutable
data class GlassSnackbarData(
    val id: Long,
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val duration: GlassSnackbarDuration = GlassSnackbarDuration.Short,
)

/**
 * Pure FIFO queue behind [GlassSnackbarHostState]: one message is [current], the rest wait.
 * [dismiss] only advances when it names the current item — a late timer of an already replaced
 * message is ignored. Duplicates of the message currently shown or already waiting are dropped.
 */
class SnackbarQueue<T>(private val sameMessage: (T, T) -> Boolean = { a, b -> a == b }) {
    private val waiting = ArrayDeque<T>()
    var current: T? = null
        private set

    val size: Int get() = waiting.size + if (current != null) 1 else 0

    /** Adds [item]; returns false when an equal message is already shown or queued. */
    fun enqueue(item: T): Boolean {
        val cur = current
        if ((cur != null && sameMessage(cur, item)) || waiting.any { sameMessage(it, item) }) return false
        if (cur == null) current = item else waiting.addLast(item)
        return true
    }

    /** Dismisses [item] if it is current and promotes the next one; returns whether it did. */
    fun dismiss(item: T): Boolean {
        if (current != item) return false
        current = waiting.removeFirstOrNull()
        return true
    }

    fun clear() {
        waiting.clear()
        current = null
    }
}

/**
 * State of a [GlassSnackbarHost]: call [show] from event handlers (non-suspending). Messages are
 * shown one at a time in order.
 */
@Stable
class GlassSnackbarHostState {
    private var nextId = 0L
    private val queue = SnackbarQueue<GlassSnackbarData> { a, b -> a.message == b.message && a.actionLabel == b.actionLabel }

    /** The message on screen, or null. */
    var current: GlassSnackbarData? by mutableStateOf(null)
        private set

    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        duration: GlassSnackbarDuration = GlassSnackbarDuration.Short,
    ) {
        queue.enqueue(GlassSnackbarData(nextId++, message, actionLabel, onAction, duration))
        current = queue.current
    }

    /** Dismisses [data] if it is the current message (swipe, action, timeout). */
    fun dismiss(data: GlassSnackbarData) {
        queue.dismiss(data)
        current = queue.current
    }

    fun clear() {
        queue.clear()
        current = null
    }
}

@Composable
fun rememberGlassSnackbarHostState(): GlassSnackbarHostState = remember { GlassSnackbarHostState() }

/**
 * Hosts snackbars of [state] (N9): a G3 glass bar at the bottom (above the navigation bar), Body
 * message + optional accent action, auto-dismissed per [snackbarTimeoutMillis]. Drawn **in-page**
 * as a non-modal overlay of the nearest [GlassBackground] (the page stays interactive and visible
 * to TalkBack); the message is a polite live region so it is announced. Place it once per screen,
 * anywhere inside the [GlassBackground]. Renders nothing without one.
 */
@Composable
fun GlassSnackbarHost(state: GlassSnackbarHostState, modifier: Modifier = Modifier) {
    val data = state.current ?: return
    val host = LocalGlassOverlayHost.current ?: return
    LaunchedEffect(data.id) {
        val timeout = snackbarTimeoutMillis(data.duration, data.actionLabel != null) ?: return@LaunchedEffect
        delay(timeout)
        state.dismiss(data)
    }
    GlassOverlay(host, modal = false) {
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            key(data.id) { GlassSnackbar(data, onDismiss = { state.dismiss(data) }, modifier = modifier) }
        }
    }
}

/** One snackbar bar (usually shown through [GlassSnackbarHost]). */
@Composable
fun GlassSnackbar(data: GlassSnackbarData, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AlphaTheme.colors
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(200)) }
    GlassSurface(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 16.dp.toPx()
            },
        level = GlassLevel.Modal,
        shape = AlphaShapes.row,
        contentPadding = PaddingValues(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                data.message,
                style = AlphaType.Body,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (data.actionLabel != null) {
                CapsuleButton(
                    text = data.actionLabel,
                    onClick = {
                        data.onAction?.invoke()
                        onDismiss()
                    },
                    role = CapsuleRole.Secondary,
                    size = CapsuleSize.Small,
                )
            }
        }
    }
}
