package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.wifi_remote_batch_download_size
import cameragps.sharednew.generated.resources.wifi_remote_batch_original_hint
import cameragps.sharednew.generated.resources.wifi_remote_batch_skip_hint
import cameragps.sharednew.generated.resources.wifi_remote_photo_close
import cameragps.sharednew.generated.resources.wifi_remote_photo_download_hint
import cameragps.sharednew.generated.resources.wifi_remote_selection_count
import com.sasch.cameragps.sharednew.remote.wifi.WifiDownloadPlan
import com.sasch.cameragps.sharednew.remote.wifi.WifiPhotoDownload
import com.sasch.cameragps.sharednew.remote.wifi.WifiRemoteState
import com.sasch.cameragps.sharednew.remote.wifi.formatByteSize
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Confirmation of an Original-format batch: every original file of the selection is downloaded (there
 * is no format picker), so the sheet only states how many files and how many bytes, plus what is
 * skipped. The plan is the same one the top bar computed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoBatchDownloadSheet(
    selectionCount: Int,
    plan: WifiDownloadPlan,
    state: WifiRemoteState,
    onDismiss: () -> Unit,
    onDownload: (List<WifiPhotoDownload>) -> Unit,
) {
    val busy = state.photoBrowser.loading || state.imageTransfer.busy
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .nestedScroll(PhotoSheetScrollConnection)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(Res.string.wifi_remote_selection_count, selectionCount),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                stringResource(Res.string.wifi_remote_batch_original_hint),
                style = MaterialTheme.typography.bodySmall
            )
            if (plan.skippedCount > 0) Text(
                stringResource(Res.string.wifi_remote_batch_skip_hint, plan.skippedCount, skipReasonsText(plan.skipped)),
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { onDownload(plan.downloads) }, enabled = plan.files.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        Res.string.wifi_remote_batch_download_size, plan.files.size, formatByteSize(plan.totalBytes)
                    )
                )
            }
            Text(
                stringResource(Res.string.wifi_remote_photo_download_hint),
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = {
                scope.launch {
                    sheetState.hide()
                    if (!sheetState.isVisible) onDismiss()
                }
            }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(Res.string.wifi_remote_photo_close))
            }
        }
    }
}
