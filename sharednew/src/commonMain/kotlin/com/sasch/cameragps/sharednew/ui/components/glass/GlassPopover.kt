package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Anchors and placement (pure)
// ---------------------------------------------------------------------------------------------

/** Where an anchored overlay prefers to sit; it flips vertically when there is no room. */
enum class PopoverPlacement { BelowEnd, BelowStart, AboveEnd, AboveStart }

/**
 * Pure placement of a [popup] of the given size next to [anchor] inside a [container] (all in the
 * same coordinate space, px). Horizontal: aligned to the anchor's end / start edge, then clamped
 * into the container minus [margin]. Vertical: [gap] below (or above) the anchor; flips to the other
 * side if the preferred side does not fit but the other does; finally clamped into the container.
 */
fun popoverPosition(
    anchor: IntRect,
    popup: IntSize,
    container: IntSize,
    placement: PopoverPlacement,
    gap: Int,
    margin: Int,
): IntOffset {
    val endAligned = placement == PopoverPlacement.BelowEnd || placement == PopoverPlacement.AboveEnd
    val rawX = if (endAligned) anchor.right - popup.width else anchor.left
    val maxX = container.width - margin - popup.width
    val x = if (maxX < margin) margin else rawX.coerceIn(margin, maxX)

    val below = anchor.bottom + gap
    val above = anchor.top - gap - popup.height
    val fitsBelow = below + popup.height <= container.height - margin
    val fitsAbove = above >= margin
    val preferBelow = placement == PopoverPlacement.BelowEnd || placement == PopoverPlacement.BelowStart
    val rawY = when {
        preferBelow && (fitsBelow || !fitsAbove) -> below
        preferBelow -> above
        fitsAbove || !fitsBelow -> above
        else -> below
    }
    val maxY = container.height - margin - popup.height
    val y = if (maxY < margin) margin else rawY.coerceIn(margin, maxY)
    return IntOffset(x, y)
}

/**
 * Bounds (in root coordinates) of the element a popover / tooltip is attached to, and its focus
 * requester — keyboard focus returns there when the popover closes.
 */
@Stable
class GlassAnchorState {
    var bounds: Rect? by mutableStateOf(null)
        internal set

    internal val focusHandle = OverlayFocusHandle()
}

@Composable
fun rememberGlassAnchor(): GlassAnchorState = remember { GlassAnchorState() }

/**
 * Records this element's bounds into [state] and makes it (or the first focusable element inside)
 * the focus return target — put it on the button that opens the popover.
 */
fun Modifier.glassAnchor(state: GlassAnchorState): Modifier =
    onGloballyPositioned { state.bounds = it.boundsInRoot() }.overlayFocusTarget(state.focusHandle)

/**
 * Full-size overlay box that places its single child next to [anchor] via [popoverPosition]. It
 * measures its own position in the root so anchor bounds (root coordinates) map correctly even
 * when the overlay layer does not start at the window origin.
 */
@Composable
internal fun AnchoredBox(
    anchor: Rect?,
    placement: PopoverPlacement,
    gap: Dp,
    margin: Dp,
    content: @Composable () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Layout(
        content = content,
        modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeable = measurables.firstOrNull()?.measure(loose)
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (placeable == null) return@layout
            val a = anchor ?: Rect(Offset(constraints.maxWidth.toFloat(), 0f), Offset(constraints.maxWidth.toFloat(), 0f))
            val local = IntRect(
                (a.left - origin.x).roundToInt(),
                (a.top - origin.y).roundToInt(),
                (a.right - origin.x).roundToInt(),
                (a.bottom - origin.y).roundToInt(),
            )
            val pos = popoverPosition(
                anchor = local,
                popup = IntSize(placeable.width, placeable.height),
                container = IntSize(constraints.maxWidth, constraints.maxHeight),
                placement = placement,
                gap = gap.roundToPx(),
                margin = margin.roundToPx(),
            )
            placeable.place(pos)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// N6 GlassPopover / GlassMenu
// ---------------------------------------------------------------------------------------------

/**
 * Anchored G3 glass popover (N6) — drawn **in-page** through the [GlassBackground] overlay host
 * (never a platform `Popup`). Tap outside or system back calls [onDismissRequest]. Opens with a
 * short fade + offset. Without a [GlassBackground] above it, it renders nothing.
 *
 * Focus (like the platform `Popup(focusable = true)` it replaces): on open keyboard focus moves onto
 * the panel and the layers below (page, lower overlays) cannot take it back; on close it returns to
 * [anchor] if the layer below had focus.
 * [paneTitle] (optional) names the pane for accessibility services.
 *
 * ```
 * val anchor = rememberGlassAnchor()
 * GlassIconButton(icon, "More", { open = true }, Modifier.glassAnchor(anchor))
 * GlassPopover(open, { open = false }, anchor) { … }
 * ```
 */
@Composable
fun GlassPopover(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchor: GlassAnchorState,
    modifier: Modifier = Modifier,
    placement: PopoverPlacement = PopoverPlacement.BelowEnd,
    paneTitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalGlassOverlayHost.current ?: return
    if (!expanded) return
    GlassOverlay(host, returnFocus = anchor.focusHandle) {
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = true,
            onBackCompleted = onDismissRequest,
        )
        Box(
            Modifier.fillMaxSize().pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } },
        ) {
            AnchoredBox(anchor.bounds, placement, gap = 4.dp, margin = 8.dp) {
                val enter = remember { Animatable(0f) }
                LaunchedEffect(Unit) { enter.animateTo(1f, tween(160)) }
                val up = placement == PopoverPlacement.AboveEnd || placement == PopoverPlacement.AboveStart
                val panelFocus = rememberModalPanelFocus()
                GlassSurface(
                    modifier = modifier
                        .widthIn(min = 180.dp, max = 320.dp)
                        .graphicsLayer {
                            alpha = enter.value
                            translationY = (1f - enter.value) * (if (up) 8.dp else (-8).dp).toPx()
                        }
                        .pointerInput(Unit) { detectTapGestures { } }
                        .semantics {
                            isTraversalGroup = true
                            if (paneTitle != null) this.paneTitle = paneTitle
                        }
                        .modalPanelFocus(panelFocus),
                    level = GlassLevel.Modal,
                    shape = AlphaShapes.popover,
                ) {
                    Column(
                        Modifier.heightIn(max = 420.dp).overlayFocusLevel().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

/**
 * One entry of a [GlassMenu]. [selected] non-null makes it a single-choice row (✓ when true);
 * [destructive] draws it in the danger colour.
 */
@Immutable
data class GlassMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val icon: Painter? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val selected: Boolean? = null,
)

/** Menu (N6) — a [GlassPopover] with ≥ 48 dp rows; picking an item also dismisses it. */
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchor: GlassAnchorState,
    items: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
    placement: PopoverPlacement = PopoverPlacement.BelowEnd,
    paneTitle: String? = null,
) {
    GlassPopover(expanded, onDismissRequest, anchor, modifier, placement, paneTitle) {
        items.forEach { item -> GlassMenuRow(item, onDismissRequest) }
    }
}

@Composable
private fun GlassMenuRow(item: GlassMenuItem, dismiss: () -> Unit) {
    val colors = AlphaTheme.colors
    val content = when {
        !item.enabled -> colors.textMuted
        item.destructive -> colors.danger
        else -> colors.textPrimary
    }
    val onClick = {
        item.onClick()
        dismiss()
    }
    val click = if (item.selected != null) {
        Modifier.selectable(
            selected = item.selected,
            enabled = item.enabled,
            role = Role.RadioButton,
            interactionSource = null,
            indication = ripple(),
            onClick = onClick,
        )
    } else {
        Modifier.clickable(
            enabled = item.enabled,
            role = Role.Button,
            interactionSource = null,
            indication = ripple(),
            onClick = onClick,
        )
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).then(click).padding(PaddingValues(horizontal = 16.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (item.icon != null) TintedIcon(item.icon, content, 20.dp)
        Text(item.label, style = AlphaType.Body, color = content, modifier = Modifier.weight(1f))
        if (item.selected == true) {
            CheckGlyph(colors.accent, Modifier.size(18.dp))
        } else if (item.selected == false) {
            Spacer(Modifier.width(18.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// In-page tooltip (used by GlassIconButton's long press)
// ---------------------------------------------------------------------------------------------

/**
 * Non-modal in-page tooltip above [anchor] showing [text] (Caption on Modal glass). Shown while it
 * is in the composition; no platform window. Renders nothing without a [GlassBackground].
 */
@Composable
internal fun GlassTooltip(text: String, anchor: GlassAnchorState) {
    val host = LocalGlassOverlayHost.current ?: return
    GlassOverlay(host, modal = false) {
        AnchoredBox(anchor.bounds, PopoverPlacement.AboveEnd, gap = 6.dp, margin = 8.dp) {
            GlassSurface(
                modifier = Modifier.widthIn(max = 260.dp),
                level = GlassLevel.Modal,
                shape = AlphaShapes.capsule,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                // The button's contentDescription already speaks this text; keep TalkBack quiet.
                Text(text, style = AlphaType.Caption, color = AlphaTheme.colors.textPrimary, modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}
