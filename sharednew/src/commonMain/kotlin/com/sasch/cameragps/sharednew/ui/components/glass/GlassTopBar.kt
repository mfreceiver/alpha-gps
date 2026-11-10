package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel

/** Pure: whether content has scrolled under the top bar (any offset from the very top). */
fun topBarScrolled(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int): Boolean =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

/** [topBarScrolled] for a lazy list. */
val LazyListState.isScrolledUnderTopBar: Boolean
    get() = topBarScrolled(firstVisibleItemIndex, firstVisibleItemScrollOffset)

/** [topBarScrolled] for a plain scroll container. */
val ScrollState.isScrolledUnderTopBar: Boolean get() = value > 0

private val TopBarShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)

/**
 * Top bar (N5): left-aligned Title (+ optional Caption [subtitle]), [navigationIcon] / [actions]
 * slots for [GlassIconButton]s, ≥ 64 dp. Transparent over the backdrop at rest; when [scrolled]
 * (content passes under it — use [isScrolledUnderTopBar]) a G2 floating glass layer fades in
 * behind it. Pads the status bar itself ([windowInsets]); the title is a heading for TalkBack.
 * Replaces Material `TopAppBar` / `CenterAlignedTopAppBar`.
 */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrolled: Boolean = false,
    windowInsets: WindowInsets = WindowInsets.statusBars,
) {
    val colors = AlphaTheme.colors
    val glassAlpha by animateFloatAsState(if (scrolled) 1f else 0f, tween(180), label = "topBarGlass")
    Box(modifier.fillMaxWidth()) {
        if (glassAlpha > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = glassAlpha }
                    .glass(GlassLevel.Float, TopBarShape),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .heightIn(min = 64.dp)
                .padding(start = if (navigationIcon != null) 4.dp else 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(Modifier.width(4.dp))
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    title,
                    style = AlphaType.Title,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Text(subtitle, style = AlphaType.Caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
    }
}
