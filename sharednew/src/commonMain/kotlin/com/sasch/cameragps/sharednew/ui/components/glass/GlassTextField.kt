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
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.m0_text_field_invalid
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
 * What the editable node of a [GlassTextField] announces. The visible label / placeholder /
 * supporting text are separate Texts (hidden from accessibility), so the field itself carries:
 * [name] (content description) = label, or the placeholder when there is no label; [description]
 * (state description) = the placeholder as a hint while the field is empty, plus a non-error
 * supporting text; [error] = the supporting text (or a generic fallback) when isError.
 */
data class GlassTextFieldA11y(val name: String?, val description: String?, val error: String?)

/** Pure accessibility text of a [GlassTextField]; see [GlassTextFieldA11y]. */
fun glassTextFieldA11y(
    label: String?,
    placeholder: String?,
    supportingText: String?,
    isError: Boolean,
    valueEmpty: Boolean,
    errorFallback: String,
): GlassTextFieldA11y {
    val name = label ?: placeholder
    val hint = placeholder.takeIf { label != null && valueEmpty }
    val support = supportingText.takeIf { !isError }
    val description = listOfNotNull(hint, support).joinToString(", ").ifEmpty { null }
    val error = if (isError) supportingText ?: errorFallback else null
    return GlassTextFieldA11y(name, description, error)
}

/** Writes [a] into a plain `Modifier.semantics` block on the editable node (keeps its text actions). */
fun SemanticsPropertyReceiver.applyGlassTextFieldA11y(a: GlassTextFieldA11y) {
    a.name?.let { contentDescription = it }
    a.description?.let { stateDescription = it }
    a.error?.let { error(it) }
}

/**
 * Glass text field (N4): G2 inset glass, radius 18, Body text, Caption [label] above, Caption
 * [supportingText] below (danger colour when [isError]). Focus draws a 2 dp accent border, error a
 * danger one. Replaces `OutlinedTextField` / `TextField`. Accessibility: one editable node named by
 * [label] (see [glassTextFieldA11y]); the visible label / placeholder / supporting texts are not
 * read a second time; [trailing] keeps its own node.
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
    val a11y = glassTextFieldA11y(
        label, placeholder, supportingText, isError, value.isEmpty(), stringResource(Res.string.m0_text_field_invalid),
    )
    Column(modifier.alpha(if (enabled) 1f else 0.6f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(
                label, style = AlphaType.Caption, color = if (isError) colors.danger else colors.textSecondary,
                modifier = Modifier.clearAndSetSemantics { },
            )
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
                        Text(
                            placeholder, style = AlphaType.Body, color = colors.textMuted, maxLines = 1,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { applyGlassTextFieldA11y(a11y) },
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
            Text(
                supportingText, style = AlphaType.Caption, color = if (isError) colors.danger else colors.textSecondary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
