package com.sasch.cameragps.sharednew.ui.remote

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberAccessibilityEnabled(): Boolean {
    val context = LocalContext.current
    val am = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    var enabled by remember(am) {
        mutableStateOf(am?.isEnabled == true || am?.isTouchExplorationEnabled == true)
    }
    DisposableEffect(am) {
        if (am == null) return@DisposableEffect onDispose {}
        val update = { enabled = am.isEnabled || am.isTouchExplorationEnabled }
        val stateListener = AccessibilityManager.AccessibilityStateChangeListener { update() }
        val touchListener = AccessibilityManager.TouchExplorationStateChangeListener { update() }
        am.addAccessibilityStateChangeListener(stateListener)
        am.addTouchExplorationStateChangeListener(touchListener)
        update()
        onDispose {
            am.removeAccessibilityStateChangeListener(stateListener)
            am.removeTouchExplorationStateChangeListener(touchListener)
        }
    }
    return enabled
}
