package com.finance.splitup.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

private val LightColorScheme = lightColorScheme(
    primary = LogoPrimaryBlue,               // App Logo Blue for primary action buttons & highlights
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFF6FF),    // Soft Blue Tint (Blue 50)
    onPrimaryContainer = Color(0xFF1E40AF),  // Deep Blue Text (Blue 800)
    secondary = Color(0xFFE2E8F0),           // Slate 200 for high contrast secondary buttons in light mode
    onSecondary = TextPrimary,               // Deep Slate text on secondary buttons
    secondaryContainer = Color(0xFFE2E8F0),  // Soft Slate Tint
    onSecondaryContainer = TextPrimary,
    tertiary = LogoCyan,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0FDF4),   // Soft Emerald Tint (Emerald 50)
    onTertiaryContainer = Color(0xFF166534),  // Deep Emerald Text (Emerald 800)
    background = LightGreyBackground,        // Clean Light Grey page background (0xFFF3F4F6)
    onBackground = TextPrimary,              // Deep Slate text (0xFF0F172A)
    surface = Color(0xFFFFFFFF),             // Crisp White cards
    onSurface = TextPrimary,                 // Deep Slate text on surfaces (0xFF0F172A)
    surfaceVariant = Color(0xFFE2E8F0),      // Slate 200 for high-contrast chips & items in light mode
    onSurfaceVariant = Color(0xFF334155),    // Slate 700 for high-contrast subtitles & dates
    surfaceTint = Color.Transparent,         // Prevents M3 elevation tonal tinting
    outline = Color(0xFF94A3B8),             // Slate 400 for visible borders in light mode
    outlineVariant = Color(0xFFCBD5E1),      // Slate 300
    error = CrimsonDanger,
    onError = Color.White,
    errorContainer = Color(0xFFF1F5F9),      // Neutral Slate 100
    onErrorContainer = CrimsonDanger
)

private val DarkColorScheme = darkColorScheme(
    primary = LogoLightBlue,                 // Lighter blue accent for dark surfaces
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E3A8A),    // Rich dark blue container
    onPrimaryContainer = Color(0xFFE0F2FE),  // Light cyan/blue text for dark mode
    secondary = DarkSlateSurfaceVariant,     // Dark Slate button background (0xFF334155)
    onSecondary = DarkSlateTextPrimary,      // Crisp light text (0xFFF8FAFC)
    secondaryContainer = DarkSlateSurface,   // Dark Slate container (0xFF1E293B)
    onSecondaryContainer = DarkSlateTextPrimary,
    tertiary = LogoCyan,
    onTertiary = Color(0xFF0F172A),
    tertiaryContainer = Color(0xFF0369A1),
    onTertiaryContainer = Color(0xFFE0F2FE),
    background = DarkSlateBackground,        // Deep Slate background (0xFF0F172A)
    onBackground = DarkSlateTextPrimary,     // Crisp light text (0xFFF8FAFC)
    surface = DarkSlateSurface,              // Slate 800 cards (0xFF1E293B)
    onSurface = DarkSlateTextPrimary,        // Crisp light text on surfaces (0xFFF8FAFC)
    surfaceVariant = DarkSlateSurfaceVariant,// Slate 700 chips & secondary items (0xFF334155)
    onSurfaceVariant = DarkSlateTextSecondary, // Slate 400 for subtitles/metadata (0xFF94A3B8)
    surfaceTint = Color.Transparent,
    outline = DarkSlateBorder,               // Slate 600 borders (0xFF475569)
    outlineVariant = DarkSlateSurfaceVariant,
    error = Color(0xFFF87171),               // Red 400 for dark mode error
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

@Composable
fun SplitUpTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
