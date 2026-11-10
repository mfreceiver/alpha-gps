package com.sasch.cameragps.sharednew.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.x helpers used by the palette contrast tests (and available to later batches that need to
 * check a new colour pairing). Pure functions, no Compose runtime needed.
 */
object Contrast {
    /** WCAG AA for body text. */
    const val AA_TEXT = 4.5f
    /** WCAG AA for large text (≥ 18.66 sp bold / 24 sp) and non-text UI parts (icons, borders). */
    const val AA_LARGE = 3.0f

    /** Relative luminance of an opaque colour (alpha ignored). */
    fun luminance(color: Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    /** Contrast ratio (1…21) between two opaque colours. */
    fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** [top] (alpha honoured) composited over [bottom] (treated as opaque); 8-bit rounding like the design tool. */
    fun over(top: Color, bottom: Color): Color {
        val a = top.alpha
        fun mix(t: Float, b: Float): Float {
            val v = b + (t - b) * a
            return kotlin.math.round(v * 255f) / 255f
        }
        return Color(mix(top.red, bottom.red), mix(top.green, bottom.green), mix(top.blue, bottom.blue), 1f)
    }
}
