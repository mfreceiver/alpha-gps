package com.sasch.cameragps.sharednew.ui.device

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sasch.cameragps.sharednew.ui.components.ScreenContent
import com.sasch.cameragps.sharednew.ui.theme.AlphaTextStyles
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme

/** Device list scaffold: transparent over the app's GlassBackground (design v3). */
@OptIn(ExperimentalMaterial3Api::class)
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
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = AlphaTextStyles.topBarTitle,
                        )
                    },
                    actions = {
                        topBarActions()

                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = AlphaTheme.colors.onSurface,
                    )
                )
            }
        }
    ) { innerPadding ->
        ScreenContent(modifier = Modifier.padding(innerPadding)) {
            content(innerPadding)
        }
    }
}
