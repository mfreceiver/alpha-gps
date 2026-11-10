package com.sasch.cameragps.sharednew.ui.components.page

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.arrow_back_24px
import cameragps.sharednew.generated.resources.back
import com.sasch.cameragps.sharednew.ui.components.ScreenContent
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.components.glass.GlassTopBar
import com.sasch.cameragps.sharednew.ui.components.glass.isScrolledUnderTopBar
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Glass page of the secondary screens (settings, help, about, licenses, privacy, logs): a
 * [GlassBackground] root, a [GlassTopBar] with a back [GlassIconButton], the title and optional
 * [actions], and the content below. Both the bar and the content are limited to the readable width
 * on near-square / landscape windows ([ScreenContent]).
 *
 * [scrolled]: pass the content's [isScrolledUnderTopBar] so the bar's G2 glass fades in once the
 * content has moved; false (default) keeps the bar transparent.
 *
 * [content] receives the padding that keeps it clear of the bar and the system bars. It is drawn
 * inside the [GlassBackground], so page-level overlays (`GlassSnackbarHost`, `CapsuleDialog` …)
 * can be placed anywhere in it.
 */
@Composable
fun GlassPageScaffold(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    scrolled: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    GlassBackground(modifier = modifier) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                ScreenContent {
                    GlassTopBar(
                        title = title,
                        navigationIcon = {
                            GlassIconButton(
                                icon = painterResource(Res.drawable.arrow_back_24px),
                                contentDescription = stringResource(Res.string.back),
                                onClick = onBackClick,
                            )
                        },
                        actions = actions,
                        scrolled = scrolled,
                        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                    )
                }
            },
        ) { padding ->
            ScreenContent { content(padding) }
        }
    }
}
