package com.sasch.cameragps.sharednew.remote.wifi

/** Only an accepted user download (not thumbnails, previews or a result summary) delays closing. */
object BackgroundWifiPolicy {
    enum class Action { Keep, Close, FinishDownload }

    fun decide(
        isForeground: Boolean,
        userDownloadInProgress: Boolean,
        explicitExit: Boolean = false,
    ): Action = when {
        explicitExit -> Action.Close
        isForeground -> Action.Keep
        userDownloadInProgress -> Action.FinishDownload
        else -> Action.Close
    }
}
