package com.sasch.cameragps.sharednew.ui.device

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import cameragps.sharednew.generated.resources.Res
import cameragps.sharednew.generated.resources.a1_bluetooth_connected_24px
import cameragps.sharednew.generated.resources.m2_bluetooth_24px
import cameragps.sharednew.generated.resources.m2_signal_excellent
import cameragps.sharednew.generated.resources.m2_signal_fair
import cameragps.sharednew.generated.resources.m2_signal_good
import cameragps.sharednew.generated.resources.m2_signal_poor
import cameragps.sharednew.generated.resources.q_battery_a11y
import cameragps.sharednew.generated.resources.q_battery_age_hours
import cameragps.sharednew.generated.resources.q_battery_age_minutes
import cameragps.sharednew.generated.resources.q_bt_signal_a11y
import cameragps.sharednew.generated.resources.q_bt_signal_unknown
import com.sasch.cameragps.sharednew.bluetooth.status.SignalLevel
import com.sasch.cameragps.sharednew.ui.components.glass.StatusTone
import com.sasch.cameragps.sharednew.ui.components.glass.statusIconColor
import com.sasch.cameragps.sharednew.ui.theme.AlphaDimens
import com.sasch.cameragps.sharednew.ui.theme.AlphaTheme
import com.sasch.cameragps.sharednew.ui.theme.AlphaType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/*
 * Trailing visuals of the device page's status card: the Bluetooth glyph inside its signal ring and
 * the camera battery. Purely presentational; the rules live in DeviceIndicators / DevicePagePresentation.
 */

/** Battery at or below this level is drawn in the danger colour. */
internal const val BATTERY_LOW_PERCENT = 20

/**
 * Bluetooth glyph (brand blue while connected, grey otherwise) inside a ring whose arc shows the
 * link's signal level ([signalArcFraction], coloured by [signalTone]) over a faint track. Not
 * connected: grey glyph, no ring. A level change eases the arc (short tween), nothing else moves.
 */
@Composable
internal fun BluetoothSignalRing(connected: Boolean, level: SignalLevel, modifier: Modifier = Modifier) {
    val colors = AlphaTheme.colors
    val target = if (connected) signalArcFraction(level) else 0f
    val sweep by animateFloatAsState(target, animationSpec = tween(durationMillis = 250), label = "signalArc")
    val arcColor = statusIconColor(signalTone(level), colors)
    val trackColor = colors.textMuted.copy(alpha = 0.28f)
    val description = if (connected) {
        stringResource(Res.string.q_bt_signal_a11y, signalLevelText(level) ?: stringResource(Res.string.q_bt_signal_unknown))
    } else {
        null
    }
    Box(
        modifier
            .size(AlphaDimens.minTouchTarget)
            .clearAndSetSemantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (connected) {
            Canvas(Modifier.size(42.dp)) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                if (sweep > 0f) {
                    drawArc(
                        arcColor,
                        startAngle = -90f,
                        sweepAngle = 360f * sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Image(
            painterResource(if (connected) Res.drawable.a1_bluetooth_connected_24px else Res.drawable.m2_bluetooth_24px),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (connected) colors.bluetoothBlue else colors.textMuted),
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Horizontal battery glyph filled to [BatteryDisplay.Value.percent] with the number below; a reading
 * older than 30 minutes is drawn muted with its age underneath. Callers hide it for
 * [BatteryDisplay.Unknown].
 */
@Composable
internal fun BatteryBlock(display: BatteryDisplay.Value, modifier: Modifier = Modifier) {
    val colors = AlphaTheme.colors
    val percentText = "${display.percent.coerceIn(0, 100)}%"
    val minutes = display.ageMinutes
    val ageText = when {
        minutes == null -> null
        minutes < 60 -> stringResource(Res.string.q_battery_age_minutes, minutes.toInt())
        else -> stringResource(Res.string.q_battery_age_hours, (minutes / 60).toInt())
    }
    val stale = ageText != null
    val outline = if (stale) colors.textMuted else colors.textSecondary
    val fill = when {
        display.percent <= BATTERY_LOW_PERCENT -> statusIconColor(StatusTone.Error, colors)
        stale -> colors.textMuted
        else -> colors.textSecondary
    }
    val description = buildString {
        append(stringResource(Res.string.q_battery_a11y, percentText))
        if (ageText != null) append(", ").append(ageText)
    }
    Column(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val fraction = display.percent.coerceIn(0, 100) / 100f
        Canvas(Modifier.size(width = 26.dp, height = 13.dp)) {
            val stroke = 1.5.dp.toPx()
            val nubWidth = 2.dp.toPx()
            val bodyWidth = size.width - nubWidth
            val radius = CornerRadius(3.dp.toPx())
            drawRoundRect(
                outline,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(bodyWidth - stroke, size.height - stroke),
                cornerRadius = radius,
                style = Stroke(stroke),
            )
            drawRoundRect(
                outline,
                topLeft = Offset(bodyWidth, size.height * 0.3f),
                size = Size(nubWidth, size.height * 0.4f),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
            val pad = stroke + 1.dp.toPx()
            val innerWidth = (bodyWidth - 2 * pad) * fraction
            if (innerWidth > 0f) {
                drawRoundRect(
                    fill,
                    topLeft = Offset(pad, pad),
                    size = Size(innerWidth, size.height - 2 * pad),
                    cornerRadius = CornerRadius(1.5.dp.toPx()),
                )
            }
        }
        Text(percentText, style = AlphaType.Caption, color = if (stale) colors.textMuted else colors.textSecondary, maxLines = 1)
        if (ageText != null) {
            Text(ageText, style = AlphaType.Tag, color = colors.textMuted, maxLines = 1)
        }
    }
}

@Composable
internal fun signalLevelText(level: SignalLevel): String? = when (level) {
    SignalLevel.Excellent -> stringResource(Res.string.m2_signal_excellent)
    SignalLevel.Good -> stringResource(Res.string.m2_signal_good)
    SignalLevel.Fair -> stringResource(Res.string.m2_signal_fair)
    SignalLevel.Poor -> stringResource(Res.string.m2_signal_poor)
    SignalLevel.Unknown -> null
}
