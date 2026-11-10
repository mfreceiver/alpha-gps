package com.sasch.cameragps.sharednew.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.keyboard_arrow_right_24px
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSurface
import com.sasch.cameragps.sharednew.ui.components.glass.GlassSwitch
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaShapes
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import org.jetbrains.compose.resources.painterResource

/**
 * A settings group: one G1 glass card with a title and its rows. Rows are separated by space, never
 * by dividers (design v3).
 */
@Composable
fun SharedSettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = AlphaType.Heading,
            color = AlphaTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/**
 * A labelled toggle row (title + optional description + [GlassSwitch]) for use inside a settings card.
 * Without [trailing] the whole row is one ≥ 48 dp switch target announced as "title, description".
 */
@Composable
fun SharedToggleRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val label = if (description.isNullOrBlank()) title else "$title. $description"
    val rowModifier = if (trailing == null) {
        Modifier
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics { contentDescription = label }
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AlphaDimens.minTouchTarget)
            .then(rowModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AlphaType.Label,
                color = AlphaTheme.colors.textPrimary,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = AlphaType.Caption,
                    color = AlphaTheme.colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (trailing != null) {
            trailing()
        } else {
            // The row handles the click; the switch only reflects state.
            GlassSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

/**
 * A tappable settings row that opens a sub-page or a picker: one glass row (radius 24, ≥ 72 dp)
 * with title, optional [description] / current [value] and a trailing chevron.
 */
@Composable
fun SharedSettingsNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
) {
    val secondary = value ?: description
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = AlphaShapes.row,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = AlphaDimens.cardPadding, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = AlphaDimens.rowMinHeight - 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AlphaType.Label,
                    color = AlphaTheme.colors.textPrimary,
                )
                if (!secondary.isNullOrBlank()) {
                    Text(
                        text = secondary,
                        style = AlphaType.Caption,
                        color = AlphaTheme.colors.textSecondary,
                    )
                }
            }
            Icon(
                painter = painterResource(Res.drawable.keyboard_arrow_right_24px),
                contentDescription = null,
                tint = AlphaTheme.colors.textSecondary,
            )
        }
    }
}
