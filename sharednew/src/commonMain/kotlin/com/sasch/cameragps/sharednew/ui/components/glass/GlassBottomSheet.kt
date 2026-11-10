package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.m0_dialog_close
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Velocity (px/s) of a downward fling that dismisses a [GlassBottomSheet] regardless of distance. */
const val SHEET_DISMISS_VELOCITY = 1_200f

/** Fraction of the sheet height a drag must pass to dismiss on release. */
const val SHEET_DISMISS_FRACTION = 0.35f

/** Pure: whether releasing a drag of [dragOffset] px (down = positive) at [velocity] px/s dismisses. */
fun sheetShouldDismiss(dragOffset: Float, sheetHeight: Float, velocity: Float): Boolean =
    velocity >= SHEET_DISMISS_VELOCITY || (sheetHeight > 0f && dragOffset >= sheetHeight * SHEET_DISMISS_FRACTION)

/**
 * In-page bottom sheet (N20): G3 glass panel rising from the bottom over a scrim, drawn through the
 * [GlassBackground] overlay host (never `ModalBottomSheet` / a platform window). Dismissed by a tap
 * on the scrim, system back, or dragging the handle down past [SHEET_DISMISS_FRACTION] / flinging.
 * Content scrolls when taller than the window. Without a [GlassBackground] it renders nothing.
 */
@Composable
fun GlassBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalGlassOverlayHost.current ?: return
    if (!visible) return
    GlassOverlay(host) {
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = true,
            onBackCompleted = onDismissRequest,
        )
        val colors = AlphaTheme.colors
        val scope = rememberCoroutineScope()
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(240)) }
        val drag = remember { Animatable(0f) }
        var sheetHeight by remember { mutableIntStateOf(0) }
        var dismissing by remember { mutableStateOf(false) }
        val closeLabel = stringResource(Res.string.m0_dialog_close)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = enter.value }
                .background(colors.scrim)
                .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } },
            contentAlignment = Alignment.BottomCenter,
        ) {
            GlassSurface(
                modifier = modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .onSizeChanged { sheetHeight = it.height }
                    .graphicsLayer {
                        translationY = drag.value + (1f - enter.value) * sheetHeight
                    }
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics {
                        isTraversalGroup = true
                        if (title != null) paneTitle = title
                        dismiss(closeLabel) {
                            onDismissRequest()
                            true
                        }
                    },
                level = GlassLevel.Modal,
                shape = AlphaShapes.sheet,
            ) {
                Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                    // Drag handle: a 48 dp tall strip; the visible grip is 36 × 4 dp.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .draggable(
                                state = rememberDraggableState { delta ->
                                    scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
                                },
                                orientation = Orientation.Vertical,
                                onDragStopped = { velocity ->
                                    if (!dismissing && sheetShouldDismiss(drag.value, sheetHeight.toFloat(), velocity)) {
                                        dismissing = true
                                        onDismissRequest()
                                    } else {
                                        drag.animateTo(0f, tween(160))
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .background(colors.textMuted, AlphaShapes.capsule),
                        )
                    }
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                        content = content,
                    )
                }
            }
        }
    }
}
