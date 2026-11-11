package com.sasch.cameragps.sharednew.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sasch.cameragps.sharednew.ui.components.glass.GlassRadio
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType

/**
 * One single-choice row (≥ 48 dp, the whole row selects) for settings cards and dialogs. A disabled
 * row is shown muted and cannot be selected.
 */
@Composable
fun SharedRadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AlphaDimens.minTouchTarget)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassRadio(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                text = label,
                style = AlphaType.Body,
                color = if (enabled) AlphaTheme.colors.textPrimary else AlphaTheme.colors.textMuted,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = AlphaType.Caption,
                    color = if (enabled) AlphaTheme.colors.textSecondary else AlphaTheme.colors.textMuted,
                )
            }
        }
    }
}
