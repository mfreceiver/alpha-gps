package com.sasch.cameragps.sharednew.ui.device

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sasch.cameragps.sharednew.ui.components.ScreenContent
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTopBar

/**
 * Device list scaffold: transparent over the app's GlassBackground (design v3), with a
 * [GlassTopBar] (left-aligned title, [topBarActions] as glass icon buttons on the right).
 */
@Composable
fun SharedDevicesScreen(
    title: String,
    topBarActions: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = snackbarHost,
        topBar = {
            ScreenContent {
                GlassTopBar(
                    title = title,
                    actions = { topBarActions() },
                )
            }
        },
    ) { innerPadding ->
        ScreenContent(modifier = Modifier.padding(innerPadding)) {
            content(innerPadding)
        }
    }
}
