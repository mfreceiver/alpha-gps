package com.saschl.cameragps.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsColumn
import com.sasch.cameragps.sharednew.ui.settings.SharedSettingsScreen

@Composable
internal fun SettingsSectionScreen(
    title: String,
    onBackClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    SharedSettingsScreen(
        title = title,
        onBackClick = onBackClick,
    ) { paddingValues ->
        SharedSettingsColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            content()
        }
    }
}
