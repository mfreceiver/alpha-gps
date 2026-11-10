package com.sasch.cameragps.sharednew.ui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key

/**
 * Renders only the first request in [queue]. The content decides when to call
 * its dismiss callback (for example, after an asynchronous action finishes).
 * Hiding or removing the host leaves requests queued for its next appearance.
 *
 * The content should render the app's single dialog container,
 * [com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog] (glass panel + capsule buttons,
 * cancel left / confirm right), passing `dismiss` as both `onDismissRequest` and the cancel action:
 * ```
 * DialogHost(queue) { request, dismiss ->
 *     CapsuleDialog(
 *         onDismissRequest = dismiss,
 *         title = request.title,
 *         message = request.message,
 *         dismiss = DialogAction(cancelLabel, dismiss),
 *         confirm = DialogAction(okLabel, { request.onConfirm(); dismiss() }),
 *     )
 * }
 * ```
 */
@Composable
fun <T : Any> DialogHost(
    queue: DialogQueue<T>,
    canShow: Boolean = true,
    content: @Composable (dialog: T, dismiss: () -> Unit) -> Unit,
) {
    if (!canShow) return
    val dialog = queue.activeDialog ?: return
    key(queue, dialog) {
        content(dialog) { queue.dismiss(dialog) }
    }
}
