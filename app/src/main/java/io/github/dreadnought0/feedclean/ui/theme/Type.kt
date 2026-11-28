package io.github.dreadnought0.feedclean.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = TextStyle(fontFamily = FontFamily.Default, color = androidx.compose.ui.graphics.Color.Unspecified)

/** Type scale tuned for the glass look: medium-weight titles, compact 12.5–15 sp body text. */
val Typography = Typography(
    displaySmall = Base.copy(fontWeight = FontWeight.Light, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1.5).sp),
    headlineSmall = Base.copy(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleLarge = Base.copy(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),
    titleMedium = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
    titleSmall = Base.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.15).sp),
    bodyLarge = Base.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = Base.copy(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = Base.copy(fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = Base.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp),
)

/** Monospace style for the Debug screen's live snapshot dump. */
val MonospaceBody = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 17.sp,
)
