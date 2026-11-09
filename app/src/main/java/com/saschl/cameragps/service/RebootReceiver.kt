package com.saschl.cameragps.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.saschl.cameragps.utils.PreferencesManager
import timber.log.Timber

class RebootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, LocationSenderService::class.java)

        val appEnabled = PreferencesManager.isAppEnabled(context)
        Timber.i(
            "RebootReceiver received intent: ${intent.action} with preference ${
                PreferencesManager.getAutoStartAfterBootEnabled(context)
            } and appEnabled=$appEnabled"
        )
        if (!LocationSenderService.hasLocationPermission(context)) {
            Timber.e("Location permission missing, not starting LocationSenderService")
            return
        }

        if (shouldAutoStartAfter(intent.action, PreferencesManager.getAutoStartAfterBootEnabled(context), appEnabled)) {
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}

/**
 * The app master switch (isAppEnabled) gates every automatic entry: a user who
 * turned the app off must not be reconnected (nor get a foreground service)
 * after a reboot or an app update. The boot-only toggle additionally gates
 * BOOT_COMPLETED; MY_PACKAGE_REPLACED keeps honoring the master switch alone,
 * matching the activity/CDM entry points.
 */
internal fun shouldAutoStartAfter(
    action: String?,
    autoStartAfterBootEnabled: Boolean,
    appEnabled: Boolean,
): Boolean =
    appEnabled && (
        Intent.ACTION_MY_PACKAGE_REPLACED == action ||
            (Intent.ACTION_BOOT_COMPLETED == action && autoStartAfterBootEnabled)
        )
