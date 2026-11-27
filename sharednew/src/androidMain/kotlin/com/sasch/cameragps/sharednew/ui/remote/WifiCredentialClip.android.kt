package com.sasch.cameragps.sharednew.ui.remote

import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipEntry

internal actual fun wifiCredentialClip(value: String, sensitive: Boolean): ClipEntry {
    val data = ClipData.newPlainText(null, value)
    if (sensitive) {
        data.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    return ClipEntry(data)
}
