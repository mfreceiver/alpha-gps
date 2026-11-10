package com.sasch.cameragps.sharednew.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.sasch.cameragps.sharednew.ui.components.page.GlassPageScaffold

/** Settings page frame: glass page with back button; system Back also calls [onBackClick]. */
@Composable
fun SharedSettingsScreen(
    title: String,
    onBackClick: () -> Unit,
    content: @Composable (innerPadding: PaddingValues) -> Unit,
) {
    GlassPageScaffold(title = title, onBackClick = onBackClick) { paddingValues ->
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = true,
            onBackCompleted = { onBackClick() },
        )
        content(paddingValues)
    }
}
