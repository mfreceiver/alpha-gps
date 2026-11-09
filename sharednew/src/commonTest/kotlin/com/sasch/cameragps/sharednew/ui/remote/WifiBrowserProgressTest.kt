package com.sasch.cameragps.sharednew.ui.remote

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A transfer is the one active progress indicator: while it runs, neither the
 * browser's linear progress bar nor held tiles' spinners show progress.
 */
class WifiBrowserProgressTest {
    @Test
    fun `catalog and thumbnail loading show the progress bar`() {
        assertTrue(browserProgressVisible(browserLoading = true, thumbnailsLoading = false, transferBusy = false))
        assertTrue(browserProgressVisible(browserLoading = false, thumbnailsLoading = true, transferBusy = false))
    }

    @Test
    fun `an active transfer hides the thumbnail progress bar`() {
        assertFalse(browserProgressVisible(browserLoading = false, thumbnailsLoading = true, transferBusy = true))
        assertTrue(browserProgressVisible(browserLoading = true, thumbnailsLoading = true, transferBusy = false))
    }

    @Test
    fun `nothing loading hides the progress bar`() {
        assertFalse(browserProgressVisible(browserLoading = false, thumbnailsLoading = false, transferBusy = false))
    }

    @Test
    fun `tiles stay silent while a transfer holds the thumbnail loader`() {
        assertFalse(tileSpinnerVisible(browserLoading = false, thumbnailsLoading = true, transferBusy = true))
        assertTrue(tileSpinnerVisible(browserLoading = false, thumbnailsLoading = true, transferBusy = false))
    }

    @Test
    fun `catalog loading still spins the tiles`() {
        assertTrue(tileSpinnerVisible(browserLoading = true, thumbnailsLoading = false, transferBusy = true))
    }
}
