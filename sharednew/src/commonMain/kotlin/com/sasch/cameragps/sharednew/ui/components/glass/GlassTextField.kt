package com.sasch.cameragps.sharednew.ui.components.glass

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.theme.AlphaColors
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import com.sasch.cameragps.sharednew.ui.theme.GlassLevel

/** Pure focus / error border of a [GlassTextField]: (colour, width); transparent = glass rim only. */
fun textFieldBorder(focused: Boolean, isError: Boolean, enabled: Boolean, colors: AlphaColors): Pair<Color, Dp> = when {
    !enabled -> Color.Transparent to 0.dp
    isError -> colors.danger to 2.dp
    focused -> colors.accent to 2.dp
    else -> Color.Transparent to 0.dp
}

/**
 * Glass text field (N4): G2 inset glass, radius 18, Body text, Caption [label] above, Caption
 * [supportingText] below (danger colour when [isError]). Focus draws a 2 dp accent border, error a
 * danger one. Replaces `OutlinedTextField` / `TextField`.
 *
 * @param trailing optional trailing slot (e.g. a [GlassIconButton] to clear / reveal).
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AlphaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val (borderColor, borderWidth) = textFieldBorder(focused, isError, enabled, colors)
    Column(modifier.alpha(if (enabled) 1f else 0.6f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(label, style = AlphaType.Caption, color = if (isError) colors.danger else colors.textSecondary)
        }
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, AlphaShapes.inset) else Modifier),
            level = GlassLevel.Float,
            shape = AlphaShapes.inset,
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = AlphaType.Body, color = colors.textMuted, maxLines = 1)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isError && supportingText != null) Modifier.semantics { error(supportingText) } else Modifier),
                        enabled = enabled,
                        readOnly = readOnly,
                        singleLine = singleLine,
                        textStyle = AlphaType.Body.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        visualTransformation = visualTransformation,
                        interactionSource = interaction,
                    )
                }
                if (trailing != null) trailing()
            }
        }
        if (supportingText != null) {
            Text(supportingText, style = AlphaType.Caption, color = if (isError) colors.danger else colors.textSecondary)
        }
    }
}
