package com.sasch.cameragps.sharednew.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
/** Text styles of the v3 components that have no exact Material slot (design page 4 / 9). */
object AlphaTextStyles {
    /** Top bar title, start-aligned (Q14). */
    val topBarTitle = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val dialogTitle = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    val dialogBody = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    /** Capsule button label (48 dp); larger buttons use [buttonLarge]. */
    val button = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    /** Compact in-row capsule (32 dp chip, e.g. 断开 / 显示 inside a dialog row). */
    val buttonSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    val buttonLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val buttonHero = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    /** Status cell value / label. */
    val statusValue = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val statusLabel = TextStyle(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal)
    /** Information tag. */
    val tag = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
}
