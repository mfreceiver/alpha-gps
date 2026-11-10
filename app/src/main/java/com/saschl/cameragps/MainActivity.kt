package com.saschl.cameragps

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.sasch.cameragps.sharednew.database.LogDatabase
import com.sasch.cameragps.sharednew.database.getDatabaseBuilder
import com.sasch.cameragps.sharednew.database.logging.LogRepository
import com.sasch.cameragps.sharednew.ui.logs.SharedLogViewerScreen
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import com.sasch.cameragps.sharednew.whatsnew.ReleasePlatform
import com.sasch.cameragps.sharednew.whatsnew.rememberWhatsNewState
import com.saschl.cameragps.service.LocationSenderService
import com.saschl.cameragps.ui.EnhancedLocationPermissionBox
import com.saschl.cameragps.ui.HelpScreen
import com.saschl.cameragps.ui.TroubleshootingScreen
import com.saschl.cameragps.ui.WelcomeScreen
import com.saschl.cameragps.ui.device.CameraDeviceManager
import com.saschl.cameragps.ui.settings.SettingsScreen
import com.saschl.cameragps.utils.PreferencesManager
import com.saschl.cameragps.utils.logging.AndroidLogFormatter
import kotlinx.serialization.Serializable
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A relaunch right after exit must not be undone by the previous exit's late
        // teardown — including one the old activity instance schedules after this point.
        AppServices.from(this).beginAppSession(this)

        setContent {
            CameraGpsTheme {
                AppContent()
            }
        }
    }

    /**
     * Leaving the app for good (back at the root, or the task being swiped away) disconnects
     * the camera. A configuration change or Home press does not: GPS keeps flowing while the
     * app sits in the background.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !isChangingConfigurations) {
            Timber.i("MainActivity finishing; disconnecting cameras")
            val appContext = applicationContext
            // The stopService afterwards is guarded by the exit gate: it only runs
            // if this teardown itself ran to completion (not cancelled by a newer
            // session, not a late duplicate of an already-cancelled exit flow —
            // the gate retires the exit generation a connect cancelled).
            AppServices.from(appContext).disconnectAllOnExit(owner = this) {
                appContext.stopService(Intent(appContext, LocationSenderService::class.java))
            }
        }
    }

    @Composable
    private fun AppContent() {
        val context = LocalContext.current
        val cameraDeviceDAO =
            LogDatabase.getRoomDatabase(getDatabaseBuilder(context)).cameraDeviceDao()
        val logRepository = remember(context) { LogRepository(getDatabaseBuilder(context)) }
        val lifecycleState by ProcessLifecycleOwner.get().lifecycle.currentStateFlow.collectAsState()

        val startDestination = remember {
            if (PreferencesManager.isFirstLaunch(context)) {
                AppDestination.Welcome
            } else {
                AppDestination.Devices
            }
        }
        val backStack = rememberNavBackStack(startDestination)
        val appVersion = remember(context) {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }
        val whatsNew = rememberWhatsNewState(
            version = appVersion,
            platform = ReleasePlatform.Android,
            previousVersion = PreferencesManager.getLastSeenReleaseVersion(context),
            firstLaunch = PreferencesManager.isFirstLaunch(context),
            saveVersion = { PreferencesManager.setLastSeenReleaseVersion(context, it) },
        )


        val view = LocalView.current
        val darkTheme = isSystemInDarkTheme()
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                !darkTheme
        }

        fun popBackStackIfPossible() {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            } else {
                // Android 12+ would only move the task to the background here, leaving the
                // camera connected. Back at the root means exit: finish so onDestroy disconnects.
                // finishAndRemoveTask also removes the Recents card, so Back and a real swipe
                // share one semantics: the task removal is delivered at once, the card is gone,
                // and no later genuine "swipe the still-listed card" can race the exit flow.
                // If the removal's unowned delivery arrives while the owned teardown is still
                // active, the exit gate's replace path folds it in; if it arrives after a
                // background connect retired the flow, the gate's retirement rules apply.
                finishAndRemoveTask()
            }
        }

        fun resetBackStackTo(destination: AppDestination) {
            if (backStack.isEmpty()) {
                backStack.add(destination)
                return
            }

            backStack[0] = destination
            while (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        }

        LaunchedEffect(lifecycleState) {
            Timber.d("Lifecycle state changed: $lifecycleState")
            when (lifecycleState) {
                Lifecycle.State.RESUMED -> {
                    if (PreferencesManager.isFirstLaunch(context)) {
                        resetBackStackTo(AppDestination.Welcome)
                    }
                }
                else -> { /* No action needed */ }
            }
        }

        LaunchedEffect(lifecycleState) {
            when (lifecycleState) {
                Lifecycle.State.RESUMED -> {
                    Timber.d("App started, will resume transmission for configured devices")
                    if (!LocationSenderService.hasLocationPermission(context)) {
                        Timber.w("Location permission missing, not resuming transmission")
                        return@LaunchedEffect
                    }
                    cameraDeviceDAO.getAllCameraDevices().forEach {
                        // Exiting the app disconnects the camera, so opening it again must
                        // reconnect (a nearby camera raises no new presence event).
                        val shouldTransmissionStart =
                            it.deviceEnabled
                                    && PreferencesManager.isAppEnabled(context.applicationContext)
                        if (shouldTransmissionStart) {
                            Timber.d("Resuming location transmission for device ${it.mac}")
                            val intent = Intent(context, LocationSenderService::class.java)
                            intent.putExtra("address", it.mac.uppercase())
                            context.startForegroundService(intent)
                        }
                    }
                }

                else -> { /* No action needed */ }
            }
        }

        NavDisplay(
            backStack = backStack,
            onBack = {
                popBackStackIfPossible()
            }
        ) { destination ->
            when (destination) {
                AppDestination.Welcome -> {
                    NavEntry(AppDestination.Welcome) {
                        WelcomeScreen(
                            onGetStarted = {
                                PreferencesManager.setFirstLaunchCompleted(context)
                                resetBackStackTo(AppDestination.Devices)
                                Timber.i("Welcome screen completed, navigating to main app")
                            }
                        )
                    }
                }

                AppDestination.Settings -> {
                    NavEntry(AppDestination.Settings) {
                        SettingsScreen(
                            whatsNew = whatsNew,
                            onBackClick = {
                                popBackStackIfPossible()
                            }
                        )
                    }
                }

                AppDestination.Help -> {
                    NavEntry(AppDestination.Help) {
                        HelpScreen(
                            onBackClick = {
                                popBackStackIfPossible()
                            },
                            onTroubleshootingClick = {
                                backStack.add(AppDestination.Troubleshooting)
                            }
                        )
                    }
                }

                AppDestination.Troubleshooting -> {
                    NavEntry(AppDestination.Troubleshooting) {
                        TroubleshootingScreen(
                            onBackClick = {
                                popBackStackIfPossible()
                            }
                        )
                    }
                }

                AppDestination.Devices -> {
                    NavEntry(AppDestination.Devices) {
                        EnhancedLocationPermissionBox {
                            CameraDeviceManager(
                                whatsNew = whatsNew,
                                onSettingsClick = {
                                    backStack.add(AppDestination.Settings)
                                },
                                onHelpClick = {
                                    backStack.add(AppDestination.Help)
                                },
                                onTroubleshootingClick = {
                                    backStack.add(AppDestination.Troubleshooting)
                                },
                                onLogsClick = {
                                    backStack.add(AppDestination.Logs)
                                }
                            )
                        }                    }
                }

                AppDestination.Logs -> {
                    NavEntry(AppDestination.Logs) {
                        val logFormatter =
                            remember(logRepository) { AndroidLogFormatter(logRepository) }
                        SharedLogViewerScreen(
                            logFormatter,
                            logRepository = logRepository,
                            onBackClick = {
                                popBackStackIfPossible()
                            }
                        )
                    }
                }

                else -> {
                    NavEntry(destination) {
                        // Unknown destination fallback
                    }
                }
            }
        }
    }
}

private sealed interface AppDestination : NavKey {
    @Serializable
    data object Welcome : AppDestination
    @Serializable
    data object Devices : AppDestination
    @Serializable
    data object Settings : AppDestination

    @Serializable
    data object Help : AppDestination

    @Serializable
    data object Troubleshooting : AppDestination

    @Serializable
    data object Logs : AppDestination
}
