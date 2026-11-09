package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.cancel
import cameragps.sharednew.generated.resources.chevron_left_24px
import cameragps.sharednew.generated.resources.chevron_right_24px
import cameragps.sharednew.generated.resources.first_page_24px
import cameragps.sharednew.generated.resources.wifi_remote_browser_first
import cameragps.sharednew.generated.resources.wifi_remote_browser_jump_go
import cameragps.sharednew.generated.resources.wifi_remote_browser_jump_label
import cameragps.sharednew.generated.resources.wifi_remote_browser_jump_title
import cameragps.sharednew.generated.resources.wifi_remote_browser_next
import cameragps.sharednew.generated.resources.wifi_remote_browser_page_of
import cameragps.sharednew.generated.resources.wifi_remote_browser_previous
import com.sasch.cameragps.sharednew.remote.wifi.SonyImageTransfer
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoBrowserState
import com.sasch.cameragps.sharednew.remote.wifi.parsePageInput
import com.sasch.cameragps.sharednew.remote.wifi.photoPageCount
import com.sasch.cameragps.sharednew.remote.wifi.photoPageNumber
import com.sasch.cameragps.sharednew.remote.wifi.photoPageOffset
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom page bar: first / previous / "page n / total" (tap to jump) / next, as narrow icon
 * buttons. Pinned below the grid so it stays in thumb reach; [enabled] is false while a catalog
 * page is loading or a transfer runs. [onPage] receives the target offset.
 */
@Composable
internal fun WifiPhotoPagerBar(
    browser: WifiPhotoBrowserState, enabled: Boolean, onPage: (offset: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageSize = SonyImageTransfer.PAGE_SIZE
    val pageCount = photoPageCount(browser.totalObjects, pageSize)
    val page = photoPageNumber(browser.offset, pageSize)
    var jumping by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { onPage(0) }, enabled = enabled && browser.offset > 0) {
            Icon(painterResource(Res.drawable.first_page_24px), stringResource(Res.string.wifi_remote_browser_first))
        }
        IconButton(
            onClick = { onPage(maxOf(0, browser.offset - pageSize)) },
            enabled = enabled && browser.offset > 0
        ) {
            Icon(painterResource(Res.drawable.chevron_left_24px), stringResource(Res.string.wifi_remote_browser_previous))
        }
        TextButton(onClick = { jumping = true }, enabled = enabled && pageCount > 1) {
            Text(stringResource(Res.string.wifi_remote_browser_page_of, page, maxOf(pageCount, 1)))
        }
        IconButton(
            onClick = { onPage(browser.offset + pageSize) },
            enabled = enabled && browser.hasMore
        ) {
            Icon(painterResource(Res.drawable.chevron_right_24px), stringResource(Res.string.wifi_remote_browser_next))
        }
    }
    if (jumping) JumpToPageDialog(
        pageCount = pageCount, currentPage = page,
        onDismiss = { jumping = false },
        onJump = { target ->
            jumping = false
            onPage(photoPageOffset(target, browser.totalObjects, pageSize))
        },
    )
}

@Composable
private fun JumpToPageDialog(
    pageCount: Int, currentPage: Int, onDismiss: () -> Unit, onJump: (Int) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val target = parsePageInput(input, pageCount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.wifi_remote_browser_jump_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(6) },
                singleLine = true,
                label = { Text(stringResource(Res.string.wifi_remote_browser_jump_label, pageCount)) },
                placeholder = { Text(currentPage.toString()) },
                isError = input.isNotEmpty() && target == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { target?.let(onJump) }, enabled = target != null) {
                Text(stringResource(Res.string.wifi_remote_browser_jump_go))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
