package com.sasch.cameragps.sharednew.remote.wifi

/**
 * Page arithmetic for the photo grid. The catalog is fetched whole and paged locally in
 * captures, so [totalObjects] (a capture count) gives an exact page count.
 */
internal fun photoPageCount(totalObjects: Int, pageSize: Int = SonyImageTransfer.PAGE_SIZE): Int =
    if (totalObjects <= 0 || pageSize <= 0) 0 else (totalObjects + pageSize - 1) / pageSize

/** 1-based number of the page that starts at [offset]. */
internal fun photoPageNumber(offset: Int, pageSize: Int = SonyImageTransfer.PAGE_SIZE): Int =
    offset.coerceAtLeast(0) / pageSize + 1

/** Offset of 1-based [page], clamped into the catalog. */
internal fun photoPageOffset(
    page: Int, totalObjects: Int, pageSize: Int = SonyImageTransfer.PAGE_SIZE
): Int {
    val count = photoPageCount(totalObjects, pageSize)
    if (count == 0) return 0
    return (page.coerceIn(1, count) - 1) * pageSize
}

/** The page number typed into the jump box, or null unless it is a whole number in 1..pageCount. */
internal fun parsePageInput(text: String, pageCount: Int): Int? =
    text.trim().toIntOrNull()?.takeIf { it in 1..pageCount }
