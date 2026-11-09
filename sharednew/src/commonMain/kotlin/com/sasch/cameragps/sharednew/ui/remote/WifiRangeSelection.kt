package com.sasch.cameragps.sharednew.ui.remote

import com.sasch.cameragps.sharednew.remote.wifi.WifiCameraCapture

/**
 * Identity of one view of the grid. A drag gesture is only valid for the exact page it started on:
 * a catalog refresh (generation), another day, another page or any republish of the page (revision)
 * makes every later update of that gesture a no-op.
 */
internal data class PageIdentity(
    val generation: Long, val dateKey: String?, val offset: Int, val revision: Long
)

/**
 * A running long-press-and-drag selection. [select] is decided once, by the anchor tile's state when
 * the press began (selected anchor -> the whole range is deselected, otherwise selected), and
 * [initial] is the selection before the gesture: every update recomputes from it, so shrinking the
 * range back restores the tiles it had passed instead of leaving them toggled.
 *
 * [token] is the gesture's lifecycle identity from the view model: only the newest gesture — and
 * only until another begins, a manual selection change happens or the gesture ends — may write
 * into the selection. A stale session (e.g. an update of gesture A arriving after gesture B
 * began) must be dropped, or it would rebuild the selection from its outdated [initial].
 */
internal class RangeSelectionSession(
    val page: PageIdentity,
    val pageIds: List<String>,
    val anchorIndex: Int,
    val select: Boolean,
    val initial: Map<String, WifiCameraCapture>,
    val token: Long,
)

/**
 * The selection after dragging from [anchor] to [end] (both capture indices of [pageCaptures], either
 * order): the closed range is set (or unset) against [initial]; tiles outside it keep their initial
 * state and tiles the finger passed over are never toggled one by one. Selection order stays that of
 * [initial], newly selected captures follow in page order.
 */
internal fun rangeSelectionResult(
    initial: Map<String, WifiCameraCapture>,
    pageCaptures: List<WifiCameraCapture>,
    anchor: Int,
    end: Int,
    select: Boolean,
): Map<String, WifiCameraCapture> {
    if (pageCaptures.isEmpty()) return initial
    val low = minOf(anchor, end).coerceIn(0, pageCaptures.lastIndex)
    val high = maxOf(anchor, end).coerceIn(0, pageCaptures.lastIndex)
    val range = pageCaptures.subList(low, high + 1)
    return if (select) {
        LinkedHashMap(initial).also { result -> range.forEach { result.putIfAbsent(it.id, it) } }
    } else {
        LinkedHashMap(initial).also { result -> range.forEach { result.remove(it.id) } }
    }
}

/** Where one capture tile sits in the grid viewport, in pixels. */
internal data class TileBounds(
    val captureIndex: Int, val left: Float, val top: Float, val width: Float, val height: Float
)

/** Which part of a tile a point lands on: the checkbox and magnifier corners are separate actions. */
internal enum class TileZone { Body, Checkbox, Preview }

internal data class TileHit(val captureIndex: Int, val zone: TileZone)

/**
 * The tile under ([x], [y]), or null in a gap between tiles / outside every tile. [actionSize] is the
 * side of the checkbox (bottom-end) and magnifier (top-start) touch targets.
 */
internal fun hitTestTile(tiles: List<TileBounds>, x: Float, y: Float, actionSize: Float): TileHit? {
    val tile = tiles.firstOrNull {
        x >= it.left && x < it.left + it.width && y >= it.top && y < it.top + it.height
    } ?: return null
    val zone = when {
        x - tile.left < actionSize && y - tile.top < actionSize -> TileZone.Preview
        tile.left + tile.width - x <= actionSize && tile.top + tile.height - y <= actionSize -> TileZone.Checkbox
        else -> TileZone.Body
    }
    return TileHit(tile.captureIndex, zone)
}

/**
 * Slow auto-scroll while a finger rests near the top or bottom edge of the viewport: 0 in the middle,
 * ramping up to [maxStep] pixels per tick at the very edge (negative = up). It only scrolls the grid
 * of the current page; turning pages is never automatic.
 */
internal fun autoScrollStep(y: Float, viewportHeight: Float, edge: Float, maxStep: Float): Float {
    if (edge <= 0f || viewportHeight <= 0f) return 0f
    return when {
        y < edge -> -maxStep * ((edge - y) / edge).coerceIn(0f, 1f)
        y > viewportHeight - edge -> maxStep * ((y - (viewportHeight - edge)) / edge).coerceIn(0f, 1f)
        else -> 0f
    }
}
