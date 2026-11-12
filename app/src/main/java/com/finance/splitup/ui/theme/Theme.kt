package com.finance.splitup.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

private val StrictlyNeutralColorScheme = lightColorScheme(
    primary = LogoPrimaryBlue,               // App Logo Blue for primary action buttons & highlights
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFFFFF),    // Strictly White container (Zero tint!)
    onPrimaryContainer = TextPrimary,        // Deep Slate text
    secondary = LightGreyButtonBg,           // Light grey for secondary buttons
    onSecondary = TextPrimary,               // Deep Slate text on secondary buttons
    secondaryContainer = Color(0xFFFFFFFF),  // Strictly White container
    onSecondaryContainer = TextPrimary,
    tertiary = LogoCyan,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFFFFF),   // Strictly White container
    onTertiaryContainer = TextPrimary,
    background = LightGreyBackground,        // Clean Light Grey page background (0xFFF3F4F6)
    onBackground = TextPrimary,              // Deep Slate text (0xFF0F172A)
    surface = Color(0xFFFFFFFF),             // Crisp White cards
    onSurface = TextPrimary,                 // Deep Slate text on surfaces (0xFF0F172A)
    surfaceVariant = LightGreyButtonBg,      // Light grey surface variant for chips & items
    onSurfaceVariant = TextSecondary,        // Slate 600 for subtitles & dates (0xFF475569)
    surfaceTint = Color.Transparent,         // Prevents M3 elevation tonal tinting
    outline = LightGreyBorder,
    outlineVariant = LightGreyButtonBg,
    error = CrimsonDanger,
    onError = Color.White,
    errorContainer = Color(0xFFF1F5F9),      // Neutral Slate 100 (No pink/red tint!)
    onErrorContainer = CrimsonDanger
)

@Composable
fun SplitUpTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StrictlyNeutralColorScheme,
        typography = Typography,
        content = content
    )
}





