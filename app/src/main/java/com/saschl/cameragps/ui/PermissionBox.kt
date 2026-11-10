package com.saschl.cameragps.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.app_settings
import cameragps.sharednew.generated.resources.background_location_access_message
import cameragps.sharednew.generated.resources.background_location_access_title
import cameragps.sharednew.generated.resources.background_location_benefit_1
import cameragps.sharednew.generated.resources.background_location_benefit_2
import cameragps.sharednew.generated.resources.background_location_benefit_3
import cameragps.sharednew.generated.resources.background_location_instruction
import cameragps.sharednew.generated.resources.background_location_required_error
import cameragps.sharednew.generated.resources.bluetooth_connect_grant_button
import cameragps.sharednew.generated.resources.bluetooth_connect_permanently_denied
import cameragps.sharednew.generated.resources.bluetooth_connect_required_desc
import cameragps.sharednew.generated.resources.bluetooth_connect_required_title
import cameragps.sharednew.generated.resources.camera_gps_permissions
import cameragps.sharednew.generated.resources.cancel_button
import cameragps.sharednew.generated.resources.continue_anyway
import cameragps.sharednew.generated.resources.continue_button
import cameragps.sharednew.generated.resources.grant_permissions_button
import cameragps.sharednew.generated.resources.location_bluetooth_access_message
import cameragps.sharednew.generated.resources.location_bluetooth_access_title
import cameragps.sharednew.generated.resources.location_bluetooth_benefit_1
import cameragps.sharednew.generated.resources.location_bluetooth_benefit_2
import cameragps.sharednew.generated.resources.location_bluetooth_benefit_3
import cameragps.sharednew.generated.resources.permission_background_gps
import cameragps.sharednew.generated.resources.permission_bluetooth
import cameragps.sharednew.generated.resources.permission_gps
import cameragps.sharednew.generated.resources.permission_gps_coarse
import cameragps.sharednew.generated.resources.permission_notifications
import cameragps.sharednew.generated.resources.permissions_required_app_error
import cameragps.sharednew.generated.resources.step1_desc
import cameragps.sharednew.generated.resources.step1_grant
import cameragps.sharednew.generated.resources.step1_granted
import cameragps.sharednew.generated.resources.step1_missing
import cameragps.sharednew.generated.resources.step1_title
import cameragps.sharednew.generated.resources.step2_android10_warning
import cameragps.sharednew.generated.resources.step2_desc
import cameragps.sharednew.generated.resources.step2_grant
import cameragps.sharednew.generated.resources.step2_granted
import cameragps.sharednew.generated.resources.step2_title
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.theme.AlphaTextStyles
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.saschl.cameragps.utils.PreferencesManager
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource


/**
 * Enhanced PermissionBox that properly handles background location permission according to Android guidelines.
 * For Android 10+ (API 29+), background location must be requested separately from foreground location.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun EnhancedLocationPermissionBox(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    onAllPermissionsGranted: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    var ignorePermissions by remember {
        mutableStateOf(PreferencesManager.isPermissionsIgnored(context))
    }

    // Foreground location permissions. Android 12+ requires ACCESS_COARSE_LOCATION to be
    // requested together with ACCESS_FINE_LOCATION in the same runtime request.
    val foregroundLocationPermissions = foregroundRuntimePermissions(Build.VERSION.SDK_INT)

    var hasUserDeniedPermission by remember { mutableStateOf(false) }

    val foregroundPermissionState = rememberMultiplePermissionsState(
        permissions = foregroundLocationPermissions
    ) { results ->
        if (results.values.any { !it }) {
            hasUserDeniedPermission = true
        }
    }

    val backgroundLocationPermission =
        rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION) { granted ->
            if (!granted) {
                hasUserDeniedPermission = true
            }
        }

    val allForegroundGranted = foregroundPermissionState.allPermissionsGranted
    val backgroundGranted = backgroundLocationPermission.status.isGranted
    val allPermissionsGranted = allForegroundGranted && backgroundGranted
    val shouldAllowContent = allPermissionsGranted || ignorePermissions

    Box(
            modifier = Modifier
                .fillMaxSize()
                .then(modifier),
        contentAlignment = if (shouldAllowContent) {
                contentAlignment
            } else {
                Alignment.Center
            },
        ) {
        if (shouldAllowContent) {
                onAllPermissionsGranted()
            } else {
                EnhancedPermissionScreen(
                    foregroundPermissionState = foregroundPermissionState,
                    backgroundLocationPermission = backgroundLocationPermission,
                    allForegroundGranted = allForegroundGranted,
                    hasUserDeniedPermission = hasUserDeniedPermission,
                    onContinueAnyway = {
                        ignorePermissions = true
                        PreferencesManager.setPermissionsIgnored(context, true)
                    },
                )
            }
        }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun EnhancedPermissionScreen(
    foregroundPermissionState: MultiplePermissionsState,
    backgroundLocationPermission: PermissionState,
    allForegroundGranted: Boolean,
    hasUserDeniedPermission: Boolean,
    onContinueAnyway: () -> Unit,
) {
    val context = LocalContext.current
    var showForegroundRationale by remember { mutableStateOf(false) }
    var showBackgroundRationale by remember { mutableStateOf(false) }

    val rejectedPermissionDescriptions = foregroundPermissionState.revokedPermissions
        .mapNotNull { permission ->
            getPermissionDescription(permission.permission)?.let { stringResource(it) }
        }
    val errorText = when {
        rejectedPermissionDescriptions.isNotEmpty() -> stringResource(
            Res.string.permissions_required_app_error,
            rejectedPermissionDescriptions.joinToString()
        )

        allForegroundGranted && !backgroundLocationPermission.status.isGranted ->
            stringResource(Res.string.background_location_required_error)

        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.camera_gps_permissions),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )

        // Step 1: Foreground Location Permissions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (allForegroundGranted)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.step1_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.step1_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (!allForegroundGranted) {
                    val revokedPermissions =
                        foregroundPermissionState.revokedPermissions.map { it ->
                            getPermissionDescription(it.permission)?.let { res -> stringResource(res) }
                        }
                            .filterNotNull()
                            .joinToString("\n") { " - $it" }

                    Text(
                        text = stringResource(Res.string.step1_missing) + "\n" + revokedPermissions,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Button(
                        onClick = {
                            if (foregroundPermissionState.shouldShowRationale) {
                                showForegroundRationale = true
                            } else {
                                foregroundPermissionState.launchMultiplePermissionRequest()
                            }
                        },
                    ) {
                        Text(text = stringResource(Res.string.step1_grant))
                    }
                } else {
                    Text(
                        text = stringResource(Res.string.step1_granted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Step 2: Background location is a separate permission (Android 10+).
        if (allForegroundGranted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (backgroundLocationPermission.status.isGranted)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(Res.string.step2_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.step2_desc),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!backgroundLocationPermission.status.isGranted) {
                            Text(
                                text = stringResource(Res.string.step2_android10_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                        Button(
                            onClick = {
                                if (backgroundLocationPermission.status.shouldShowRationale) {
                                    showBackgroundRationale = true
                                } else {
                                    backgroundLocationPermission.launchPermissionRequest()
                                }
                            },
                        ) {
                            Text(text = stringResource(Res.string.step2_grant))
                        }
                    } else {
                        Text(
                            text = stringResource(Res.string.step2_granted),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (hasUserDeniedPermission && errorText.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(text = stringResource(Res.string.app_settings))
            }
            TextButton(onClick = onContinueAnyway) {
                Text(text = stringResource(Res.string.continue_anyway))
            }
        }
    }

    // Rationale dialogs
    if (showForegroundRationale) {
        CapsuleDialog(
            onDismissRequest = { showForegroundRationale = false },
            title = stringResource(Res.string.location_bluetooth_access_title),
            confirm = DialogAction(stringResource(Res.string.grant_permissions_button), onClick = {
                showForegroundRationale = false
                foregroundPermissionState.launchMultiplePermissionRequest()
            }),
            dismiss = DialogAction(stringResource(Res.string.cancel_button), onClick = {
                showForegroundRationale = false
            }),
        ) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RationaleText(stringResource(Res.string.location_bluetooth_access_message))
            RationaleText(stringResource(Res.string.location_bluetooth_benefit_1))
            RationaleText(stringResource(Res.string.location_bluetooth_benefit_2))
            RationaleText(stringResource(Res.string.location_bluetooth_benefit_3))
        } }
    }

    if (showBackgroundRationale) {
        CapsuleDialog(
            onDismissRequest = { showBackgroundRationale = false },
            title = stringResource(Res.string.background_location_access_title),
            confirm = DialogAction(stringResource(Res.string.continue_button), onClick = {
                showBackgroundRationale = false
                backgroundLocationPermission.launchPermissionRequest()
            }),
            dismiss = DialogAction(stringResource(Res.string.cancel_button), onClick = {
                showBackgroundRationale = false
            }),
        ) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RationaleText(stringResource(Res.string.background_location_access_message))
            RationaleText(stringResource(Res.string.background_location_benefit_1))
            RationaleText(stringResource(Res.string.background_location_benefit_2))
            RationaleText(stringResource(Res.string.background_location_benefit_3))
            RationaleText(stringResource(Res.string.background_location_instruction), bold = true)
        } }
    }

}

/** Body line of a permission rationale dialog. */
@Composable
private fun RationaleText(text: String, bold: Boolean = false) {
    Text(
        text = text,
        style = AlphaTextStyles.dialogBody,
        fontWeight = if (bold) FontWeight.Bold else null,
        color = AlphaTheme.colors.onSurfaceVariant,
    )
}

/**
 * Gates [content] behind the BLUETOOTH_CONNECT runtime permission.
 * Shows a permission-request UI when the permission is missing, with an optional
 * "continue anyway" escape hatch (consistent with [EnhancedLocationPermissionBox]).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BluetoothConnectPermissionGate(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current

    var ignorePermissions by remember {
        mutableStateOf(PreferencesManager.isPermissionsIgnored(context))
    }

    val bluetoothConnectPermission = rememberPermissionState(
        permission = Manifest.permission.BLUETOOTH_CONNECT
    )

    val isGranted = bluetoothConnectPermission.status.isGranted

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
        contentAlignment = if (isGranted || ignorePermissions) Alignment.TopStart else Alignment.Center,
    ) {
        if (isGranted || ignorePermissions) {
            content()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.bluetooth_connect_required_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.bluetooth_connect_required_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
                Button(
                    onClick = { bluetoothConnectPermission.launchPermissionRequest() },
                ) {
                    Text(text = stringResource(Res.string.bluetooth_connect_grant_button))
                }
                // Permanently denied – show explanation and offer opening app settings
                if (!bluetoothConnectPermission.status.shouldShowRationale) {
                    Text(
                        text = stringResource(Res.string.bluetooth_connect_permanently_denied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    TextButton(
                        onClick = {
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(text = stringResource(Res.string.app_settings))
                    }
                }

            }
        }
    }
}

/**
 * Gets a user-friendly description for what the permission is used for
 */
/**
 * Foreground runtime permissions requested together on the given [sdkInt] (Build.VERSION.SDK_INT).
 *
 * Android 12+ ignores a runtime request that asks for ACCESS_FINE_LOCATION without
 * ACCESS_COARSE_LOCATION, so both are always requested in one shot; access is still
 * gated on FINE being granted. POST_NOTIFICATIONS joins the same request on 33+.
 */
fun foregroundRuntimePermissions(sdkInt: Int): List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.BLUETOOTH_CONNECT)
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * Gets a user-friendly description for what the permission is used for
 */
fun getPermissionDescription(permission: String): StringResource? {
    return when (permission) {
        Manifest.permission.ACCESS_FINE_LOCATION -> Res.string.permission_gps
        Manifest.permission.ACCESS_COARSE_LOCATION -> Res.string.permission_gps_coarse
        Manifest.permission.ACCESS_BACKGROUND_LOCATION -> Res.string.permission_background_gps
        Manifest.permission.BLUETOOTH_CONNECT -> Res.string.permission_bluetooth
        Manifest.permission.POST_NOTIFICATIONS -> Res.string.permission_notifications
        else -> null
    }
}
