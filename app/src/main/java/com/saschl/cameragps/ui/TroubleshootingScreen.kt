package com.saschl.cameragps.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.guide_title
import com.sasch.cameragps.sharednew.ui.components.glass.isScrolledUnderTopBar
import com.sasch.cameragps.sharednew.ui.components.page.GlassPageScaffold
import com.sasch.cameragps.sharednew.ui.help.TroubleshootingGuideContent
import org.jetbrains.compose.resources.stringResource

/** Connection troubleshooting guide page: glass page with the shared step-by-step guide. */
@Composable
fun TroubleshootingScreen(
    onBackClick: () -> Unit
) {
    val listState = rememberLazyListState()
    GlassPageScaffold(
        title = stringResource(Res.string.guide_title),
        onBackClick = onBackClick,
        scrolled = listState.isScrolledUnderTopBar,
    ) { innerPadding ->
        TroubleshootingGuideContent(innerPadding, listState)
    }
}
