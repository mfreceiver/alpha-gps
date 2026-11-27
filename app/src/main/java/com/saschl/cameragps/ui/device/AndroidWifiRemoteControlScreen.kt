package com.saschl.cameragps.ui.device

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sasch.cameragps.sharednew.remote.wifi.WifiSessionPurpose
import com.sasch.cameragps.sharednew.ui.remote.WifiRemoteControlScreen
import com.saschl.cameragps.AppServices
import com.saschl.cameragps.utils.PreferencesManager

/** R2 host: the shared remote-shooting page plus the platform's network permission and Wi-Fi settings hooks. */
@Composable
fun AndroidWifiRemoteControlScreen(
    identifier: String,
    onClose: () -> Unit,
    onFallbackToBle: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = AppServices.from(context).wifiRemote
    val owner by controller.owner.collectAsState()
    KeepScreenOnWhileRemoteActive(owner.equals(identifier, ignoreCase = true))
    var pendingProceed by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val proceed = pendingProceed
        pendingProceed = null
        if (proceed != null) {
            if (grants.values.any { !it }) controller.permissionDenied(identifier)
            else if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) proceed()
        }
    }
    val ensurePermission: (String?, () -> Unit) -> Unit = { host, proceed ->
        val required = buildList {
            if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
            if (host == null) {
                if (Build.VERSION.SDK_INT >= 33) add("android.permission.NEARBY_WIFI_DEVICES")
                else {
                    add("android.permission.ACCESS_FINE_LOCATION")
                    add("android.permission.ACCESS_COARSE_LOCATION")
                }
            }
        }
        val missing = required.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            pendingProceed = proceed
            // Android 12 requires fine and coarse to be requested together.
            permission.launch(required.toTypedArray())
        } else proceed()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) pendingProceed = null
    }
    DisposableEffect(identifier) {
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                controller.onRemoteScreenDisposed(identifier, WifiSessionPurpose.RemoteControl)
            }
        }
    }
    WifiRemoteControlScreen(
        identifier = identifier,
        controller = controller,
        hapticsEnabled = PreferencesManager.isHapticsEnabled(context),
        ensureNetworkPermission = ensurePermission,
        onWifiSettings = {
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    context.startActivity(Intent(Settings.Panel.ACTION_WIFI))
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
            } else context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        },
        onClose = onClose,
        onFallbackToBle = onFallbackToBle,
        onAppSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
            )
        },
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
