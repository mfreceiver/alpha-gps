package com.saschl.cameragps.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sasch.cameragps.sharednew.ui.components.glass.isScrolledUnderTopBar
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsColumn
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsScreen

@Composable
internal fun SettingsSectionScreen(
    title: String,
    onBackClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    SharedSettingsScreen(
        title = title,
        onBackClick = onBackClick,
        scrolled = scrollState.isScrolledUnderTopBar,
    ) { paddingValues ->
        SharedSettingsColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = scrollState,
        ) {
            content()
        }
    }
}
