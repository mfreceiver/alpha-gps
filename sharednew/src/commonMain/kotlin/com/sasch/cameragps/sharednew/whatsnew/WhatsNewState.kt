package com.sasch.cameragps.sharednew.whatsnew

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class WhatsNewState(
    private val version: String,
    private val previousVersion: String?,
    firstLaunch: Boolean,
    private val saveVersion: (String) -> Unit,
) {
    val release = ReleaseNotesCatalog.forVersion(version)
    var pending by mutableStateOf(
        WhatsNewPolicy.shouldShow(version, previousVersion, firstLaunch, release != null)
    )
        private set

    /** Fresh installs and releases without notes establish a baseline silently. */
    fun initialize() {
        if (!pending) saveIfNewer()
    }

    fun dismiss() {
        saveIfNewer()
        pending = false
    }

    private fun saveIfNewer() {
        if (WhatsNewPolicy.isNewer(version, previousVersion)) saveVersion(version.removePrefix("v"))
    }
}

@Composable
fun rememberWhatsNewState(
    version: String,
    previousVersion: String?,
    firstLaunch: Boolean,
    saveVersion: (String) -> Unit,
): WhatsNewState {
    val state = remember(version) {
        WhatsNewState(version, previousVersion, firstLaunch, saveVersion)
    }
    LaunchedEffect(state) { state.initialize() }
    return state
}
