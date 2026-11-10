package com.sasch.cameragps.sharednew.ui.components.page

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.arrow_back_24px
import cameragps.sharednew.generated.resources.back
import com.sasch.cameragps.sharednew.ui.components.ScreenContent
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.components.glass.GlassIconButton
import com.sasch.cameragps.sharednew.ui.theme.AlphaTextStyles
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Glass page of the secondary screens (settings, help, about, licenses, privacy, logs): a
 * [GlassBackground] root, a transparent top bar with a back [GlassIconButton], a start-aligned
 * title and optional [actions], and the content below. Both the bar and the content are limited to
 * the readable width on near-square / landscape windows ([ScreenContent]).
 *
 * [content] receives the padding that keeps it clear of the bar and the system bars.
 */
@Composable
fun GlassPageScaffold(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    GlassBackground(modifier = modifier) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { ScreenContent { GlassPageTopBar(title, onBackClick, actions) } },
        ) { padding ->
            ScreenContent { content(padding) }
        }
    }
}

@Composable
private fun GlassPageTopBar(
    title: String,
    onBackClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            icon = painterResource(Res.drawable.arrow_back_24px),
            contentDescription = stringResource(Res.string.back),
            onClick = onBackClick,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            style = AlphaTextStyles.topBarTitle,
            color = AlphaTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}
