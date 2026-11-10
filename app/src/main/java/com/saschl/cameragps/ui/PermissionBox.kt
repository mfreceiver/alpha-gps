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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleButton
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleDialog
import com.sasch.cameragps.sharednew.ui.components.glass.CapsuleRole
import com.sasch.cameragps.sharednew.ui.components.glass.DialogAction
import com.sasch.cameragps.sharednew.ui.components.glass.GlassBackground
import com.sasch.cameragps.sharednew.ui.components.glass.GlassCard
import com.sasch.cameragps.sharednew.ui.components.glass.StatusBadge
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
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
        contentAlignment = if (shouldAllowContent) contentAlignment else Alignment.Center,
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

/**
 * Root of a permission page: its own [GlassBackground] (backdrop + host of the rationale
 * dialogs), a scrollable, width-limited column centred in the window.
 */
@Composable
private fun PermissionPage(content: @Composable ColumnScope.() -> Unit) {
    GlassBackground(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = PermissionPageMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(AlphaDimens.screenMargin)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(AlphaDimens.cardGap),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

private val PermissionPageMaxWidth = 560.dp

@Composable
private fun PermissionPageTitle(text: String) {
    Text(
        text = text,
        style = AlphaType.Title,
        color = AlphaTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
    )
}

/**
 * One permission step: Heading title, Body description, then either what is still missing with
 * its grant action ([pending]) or a green "granted" badge.
 */
@Composable
private fun PermissionStepCard(
    title: String,
    description: String,
    granted: Boolean,
    grantedText: String,
    pending: @Composable ColumnScope.() -> Unit,
) {
    val colors = AlphaTheme.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = AlphaType.Heading, color = colors.textPrimary, textAlign = TextAlign.Center)
            Text(text = description, style = AlphaType.Body, color = colors.textSecondary, textAlign = TextAlign.Center)
            if (granted) {
                StatusBadge(text = grantedText, tone = StatusTone.Success, modifier = Modifier.padding(top = 4.dp))
            } else {
                pending()
            }
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
    val colors = AlphaTheme.colors

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

    PermissionPage {
        PermissionPageTitle(stringResource(Res.string.camera_gps_permissions))

        // Step 1: Foreground Location Permissions
        PermissionStepCard(
            title = stringResource(Res.string.step1_title),
            description = stringResource(Res.string.step1_desc),
            granted = allForegroundGranted,
            grantedText = stringResource(Res.string.step1_granted),
        ) {
            val revokedPermissions =
                foregroundPermissionState.revokedPermissions.map { it ->
                    getPermissionDescription(it.permission)?.let { res -> stringResource(res) }
                }
                    .filterNotNull()
                    .joinToString("\n") { " - $it" }

            Text(
                text = stringResource(Res.string.step1_missing) + "\n" + revokedPermissions,
                style = AlphaType.Caption,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            CapsuleButton(
                text = stringResource(Res.string.step1_grant),
                onClick = {
                    if (foregroundPermissionState.shouldShowRationale) {
                        showForegroundRationale = true
                    } else {
                        foregroundPermissionState.launchMultiplePermissionRequest()
                    }
                },
                role = CapsuleRole.Primary,
            )
        }

        // Step 2: Background location is a separate permission (Android 10+).
        if (allForegroundGranted) {
            PermissionStepCard(
                title = stringResource(Res.string.step2_title),
                description = stringResource(Res.string.step2_desc),
                granted = backgroundLocationPermission.status.isGranted,
                grantedText = stringResource(Res.string.step2_granted),
            ) {
                Text(
                    text = stringResource(Res.string.step2_android10_warning),
                    style = AlphaType.Caption,
                    color = colors.warningText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                CapsuleButton(
                    text = stringResource(Res.string.step2_grant),
                    onClick = {
                        if (backgroundLocationPermission.status.shouldShowRationale) {
                            showBackgroundRationale = true
                        } else {
                            backgroundLocationPermission.launchPermissionRequest()
                        }
                    },
                    role = CapsuleRole.Primary,
                )
            }
        }

        if (hasUserDeniedPermission && errorText.isNotBlank()) {
            Text(
                text = errorText,
                style = AlphaType.Caption,
                color = colors.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            CapsuleButton(
                text = stringResource(Res.string.app_settings),
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                role = CapsuleRole.Primary,
            )
            CapsuleButton(
                text = stringResource(Res.string.continue_anyway),
                onClick = onContinueAnyway,
                role = CapsuleRole.Secondary,
            )
        }

        // Rationale dialogs (in-tree overlays of this page's GlassBackground)
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
                RationaleText(stringResource(Res.string.background_location_instruction), emphasized = true)
            } }
        }
    }
}

/** Body line of a permission rationale dialog; the closing instruction is emphasised (Label). */
@Composable
private fun RationaleText(text: String, emphasized: Boolean = false) {
    Text(
        text = text,
        style = if (emphasized) AlphaType.Label else AlphaType.Body,
        color = if (emphasized) AlphaTheme.colors.textPrimary else AlphaTheme.colors.textSecondary,
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
            val colors = AlphaTheme.colors
            PermissionPage {
                PermissionPageTitle(stringResource(Res.string.bluetooth_connect_required_title))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.bluetooth_connect_required_desc),
                            style = AlphaType.Body,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        CapsuleButton(
                            text = stringResource(Res.string.bluetooth_connect_grant_button),
                            onClick = { bluetoothConnectPermission.launchPermissionRequest() },
                            role = CapsuleRole.Primary,
                        )
                    }
                }
                // Permanently denied – show explanation and offer opening app settings
                if (!bluetoothConnectPermission.status.shouldShowRationale) {
                    Text(
                        text = stringResource(Res.string.bluetooth_connect_permanently_denied),
                        style = AlphaType.Caption,
                        color = colors.danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    CapsuleButton(
                        text = stringResource(Res.string.app_settings),
                        onClick = {
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            context.startActivity(intent)
                        },
                        role = CapsuleRole.Secondary,
                    )
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
