package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.m0_button_loading
import com.sasch.cameragps.sharednew.ui.theme.AlphaColors
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassHighlight
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel
import com.sasch.cameragps.sharednew.ui.theme.GlassTintRole
import com.sasch.cameragps.sharednew.ui.theme.GlassTints
import org.jetbrains.compose.resources.stringResource

/**
 * Role of an operation capsule (design page 7, "按钮规则表"):
 * - [Primary]: the one main action of a screen (下载 / 照片传输 / 添加相机 / 搜索相机), icon + text,
 *   at the bottom; the confirm button of a dialog.
 * - [Secondary]: pale glass capsule — the cancel / left button of a dialog.
 * - [Tonal]: secondary operations in a card (连接 / 配对 / 重新读取 / 去授权).
 * - [Danger]: filled red — confirm of a destructive dialog (移除 / 断开 / 删除).
 * - [DangerOutlined]: red outline — the destructive entry point on a page (移除相机).
 * - [GlassPrimary]: the primary action floating over photos or the photo grid (下载 N 张 / 下载).
 *   Kept for existing call sites; it is exactly `Primary` with `glass = true`.
 *
 * Every role also has a **glass variant** (`CapsuleButton(glass = true)`, N12): a frosted capsule
 * ([Modifier.glass], level [GlassLevel.Float] or [GlassLevel.Viewer]) tinted with the role's
 * [GlassTint] — accent for Primary, danger for Danger / DangerOutlined, a faint accent cast for
 * Tonal, untinted for Secondary. Glass labels use the tint's high-contrast content colour.
 */
enum class CapsuleRole { Primary, Secondary, Tonal, Danger, DangerOutlined, GlassPrimary }

/**
 * Height / padding of a capsule — **never** the text size (the label is always [AlphaType.Label]):
 * 32 dp chip inside a dense row ([Small] — the touch target is still padded to 48 dp), 48 dp
 * (dialogs, inline), 52 dp (bottom action of the device page), 56 dp (screen primary), 68 dp (hero).
 * [raised] sizes give an opaque primary capsule its light gradient and a soft accent glow.
 */
enum class CapsuleSize(val height: Dp, val iconSize: Dp, val horizontalPadding: Dp, val raised: Boolean) {
    Small(32.dp, 16.dp, 14.dp, raised = false),
    Regular(48.dp, 20.dp, 20.dp, raised = false),
    Medium(52.dp, 22.dp, 22.dp, raised = true),
    Large(56.dp, 22.dp, 24.dp, raised = true),
    Hero(68.dp, 24.dp, 28.dp, raised = true),
}

/**
 * Resolved colours of a capsule. [containerTop]/[containerBottom] form a vertical gradient (equal for
 * flat roles; for [glass] both are the translucent tint laid over the frosted base); [rimAlpha] is
 * the white 1 dp rim of opaque capsules (glass draws its own edge light); [glow] is the soft accent
 * shadow of a raised opaque primary (null = none, never black); [border] is the outline of
 * [CapsuleRole.DangerOutlined].
 */
@Immutable
data class CapsuleColors(
    val containerTop: Color,
    val containerBottom: Color,
    val content: Color,
    val rimAlpha: Float,
    val glow: Color?,
    val border: Color?,
    val glass: Boolean = false,
)

/** The glass tint role of [role]'s glass variant. */
fun capsuleGlassTintRole(role: CapsuleRole): GlassTintRole = when (role) {
    CapsuleRole.Primary, CapsuleRole.GlassPrimary -> GlassTintRole.Accent
    CapsuleRole.Danger, CapsuleRole.DangerOutlined -> GlassTintRole.Danger
    CapsuleRole.Tonal -> GlassTintRole.AccentSoft
    CapsuleRole.Secondary -> GlassTintRole.Neutral
}

/**
 * Pure colour resolution for [role] (design `lib3.primary_btn` / `pill_btn` / `tonal_btn`).
 * [raised] adds the primary gradient + accent glow (operation buttons ≥ 52 dp, not dialog
 * capsules); [glass] resolves the glass variant ([CapsuleRole.GlassPrimary] is always glass).
 */
fun capsuleColors(
    role: CapsuleRole,
    enabled: Boolean,
    colors: AlphaColors,
    raised: Boolean = false,
    glass: Boolean = false,
): CapsuleColors {
    val isGlass = glass || role == CapsuleRole.GlassPrimary
    if (!enabled) {
        val fill = colors.onSurface.copy(alpha = 0.12f)
        return CapsuleColors(
            containerTop = fill,
            containerBottom = fill,
            content = colors.onSurface.copy(alpha = 0.45f),
            rimAlpha = 0f,
            glow = null,
            border = null,
            glass = isGlass,
        )
    }
    if (isGlass) {
        val t = GlassTints.of(capsuleGlassTintRole(role), colors)
        return CapsuleColors(t.tint, t.tint, t.content, 0f, null, null, glass = true)
    }
    return when (role) {
        CapsuleRole.Primary, CapsuleRole.GlassPrimary -> CapsuleColors(
            // Raised: vertical gradient from a lightened primary; flat capsules (dialogs) stay solid.
            containerTop = if (raised) lerp(colors.primary, GlassHighlight, if (colors.isDark) 0.25f else 0.16f) else colors.primary,
            containerBottom = colors.primary,
            content = colors.onPrimary,
            rimAlpha = if (raised) (if (colors.isDark) 0.5f else 0.35f) else 0.3f,
            // Soft accent glow only — the old dark (black 50 %) ring of Large / Hero is gone.
            glow = if (raised) colors.accent.copy(alpha = if (colors.isDark) 0.18f else 0.24f) else null,
            border = null,
        )
        CapsuleRole.Secondary -> {
            val fill = colors.onSurface.copy(alpha = if (colors.isDark) 0.13f else 0.07f)
            CapsuleColors(fill, fill, colors.onSurface, if (colors.isDark) 0.16f else 0.7f, null, null)
        }
        CapsuleRole.Tonal -> CapsuleColors(
            colors.primaryContainer, colors.primaryContainer, colors.onPrimaryContainer, 0f, null, null,
        )
        CapsuleRole.Danger -> CapsuleColors(colors.error, colors.error, colors.onError, 0.3f, null, null)
        CapsuleRole.DangerOutlined -> CapsuleColors(
            Color.Transparent, Color.Transparent, colors.error, 0f, null, colors.error,
        )
    }
}

/**
 * Operation capsule button (≥ 48 dp touch). Text is required — icon-only actions use
 * [GlassIconButton]. [loading] keeps the label, swaps the icon for a spinner and ignores clicks;
 * TalkBack hears the loading state.
 */
@Composable
fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: CapsuleRole = CapsuleRole.Primary,
    size: CapsuleSize = CapsuleSize.Regular,
    icon: Painter? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    glass: Boolean = false,
    glassLevel: GlassLevel = GlassLevel.Float,
) {
    val alpha = AlphaTheme.colors
    val raised = (role == CapsuleRole.Primary || role == CapsuleRole.GlassPrimary) && size.raised
    val c0 = capsuleColors(role, enabled, alpha, raised, glass)
    val onGlass = c0.glass
    // Viewer glass: label and icon in the photo-overlay colour.
    val c = if (onGlass && enabled && glassLevel == GlassLevel.Viewer) c0.copy(content = alpha.onPhoto) else c0
    val shape = AlphaShapes.capsule
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "capsulePress")
    val loadingLabel = stringResource(Res.string.m0_button_loading)
    val glowModifier = c.glow?.let {
        Modifier.dropShadow(shape, Shadow(radius = 14.dp, color = it, offset = DpOffset(0.dp, 4.dp)))
    } ?: Modifier
    val borderModifier = when {
        c.border != null -> Modifier.border(1.dp, c.border, shape)
        c.rimAlpha > 0f -> Modifier.border(1.dp, GlassHighlight.copy(alpha = c.rimAlpha), shape)
        else -> Modifier
    }
    Row(
        modifier = modifier
            // A chip keeps a 48 dp touch target around its 32 dp capsule.
            .then(if (size == CapsuleSize.Small) Modifier.minimumInteractiveComponentSize() else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .defaultMinSize(minWidth = size.height, minHeight = size.height)
            .then(glowModifier)
            // Glass variant: frosted base (blur when a backdrop is reachable, else the near-solid
            // fallback) with the role tint folded into the fill; opaque roles paint their gradient.
            .then(
                if (onGlass) {
                    Modifier.glass(glassLevel, shape, tint = c.containerBottom)
                } else {
                    Modifier.clip(shape).background(Brush.verticalGradient(listOf(c.containerTop, c.containerBottom)))
                },
            )
            .then(borderModifier)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = c.content),
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (loading) Modifier.semantics { stateDescription = loadingLabel } else Modifier)
            .padding(PaddingValues(horizontal = size.horizontalPadding)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            GlassProgressCircle(
                modifier = Modifier.size(size.iconSize - 2.dp),
                color = c.content,
            )
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            // Decorative: the label already names the action.
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(size.iconSize),
                colorFilter = ColorFilter.tint(c.content),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = AlphaType.Label,
            color = c.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
