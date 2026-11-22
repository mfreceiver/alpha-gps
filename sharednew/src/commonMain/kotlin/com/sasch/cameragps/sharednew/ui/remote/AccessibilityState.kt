package com.sasch.cameragps.sharednew.ui.remote

import androidx.compose.runtime.Composable

/** Whether an accessibility service (such as TalkBack) is currently active on this device. */
@Composable
internal expect fun rememberAccessibilityEnabled(): Boolean
