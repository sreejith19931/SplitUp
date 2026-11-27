package com.finance.splitup.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.finance.splitup.R

val ManropeFontFamily = FontFamily(
    Font(R.font.manrope)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * SplitUp type tokens — the app's de-facto text scale, extracted from the
 * ~350 inline `fontSize`/`fontWeight` pairs so screens reference named
 * styles instead of raw values. Sizes and weights mirror the previous
 * inline values exactly (no color, no lineHeight) so visuals are unchanged.
 * Bespoke one-off sizes (hero numbers, emoji) intentionally stay inline.
 */
object SplitUpType {
    val SectionTitle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)          // card section headers
    val CardTitle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)             // card titles
    val RowTitle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)              // list row titles
    val ItemTitle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)             // compact item titles
    val ItemTitleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val ScreenSubtitle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
    val ScreenSubtitleStrong = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)
    val BodyRegular = TextStyle(fontSize = 16.sp)
    val BodySmallAlt = TextStyle(fontSize = 13.sp)
    val RowTitleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val BadgeLabel = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp)
    val ChipLabel = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp)
    val ChipLabelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    val Caption = TextStyle(fontSize = 12.sp)
    val MicroLabel = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp)
}
