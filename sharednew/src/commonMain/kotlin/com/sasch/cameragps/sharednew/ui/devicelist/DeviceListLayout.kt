package com.sasch.cameragps.sharednew.ui.devicelist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.add_camera
import cameragps.sharednew.generated.resources.device_list_need_help
import cameragps.sharednew.generated.resources.m2_add_24px
import com.sasch.cameragps.sharednew.ui.components.AdaptiveAction
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Device list layout (design v3): header (notices, add camera), the list, and the connection
 * troubleshooting link at the bottom — no divider and no opaque footer, the page's glass
 * background shows through.
 */
@Composable
fun DeviceListLayout(
    onNeedHelp: () -> Unit,
    header: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        header()
        // Measure the footer first so the list never hides it, even with larger text.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AlphaDimens.screenMargin, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(onClick = onNeedHelp, modifier = Modifier.heightIn(min = AlphaDimens.minTouchTarget)) {
                Text(
                    stringResource(Res.string.device_list_need_help),
                    style = AlphaType.Label,
                    color = AlphaTheme.colors.primary,
                )
            }
        }
    }
}

@Composable
fun AddCameraButton(onClick: () -> Unit) {
    AdaptiveAction(
        modifier = Modifier
            .padding(horizontal = AlphaDimens.screenMargin, vertical = 8.dp),
    ) { actionModifier ->
        CapsuleButton(
            text = stringResource(Res.string.add_camera),
            onClick = onClick,
            role = CapsuleRole.Primary,
            icon = painterResource(Res.drawable.m2_add_24px),
            modifier = actionModifier,
        )
    }
}
