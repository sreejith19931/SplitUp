package com.finance.splitup.ui.theme

import androidx.compose.ui.graphics.Color

// App Logo Multi-Color Palette (Cobalt Blue, Emerald Teal, Gold, Cyan, Light Blue)
val LogoPrimaryBlue = Color(0xFF1D4ED8)      // "Split" Side Cobalt Blue
val LogoEmeraldTeal = Color(0xFF047857)      // "Up" Side Emerald Teal
val LogoGold = Color(0xFFFBBF24)             // Microchip Gold
val LogoCyan = Color(0xFF38BDF8)             // Perforated Seam Cyan
val LogoLightBlue = Color(0xFF93C5FD)        // Hologram Light Blue

// Professional Neutrals (Eliminates "too much blue everywhere" while maintaining supreme contrast)
val TextPrimary = Color(0xFF0F172A)          // Slate 900 for sharp, legible body text & headers
val TextSecondary = Color(0xFF475569)        // Slate 600 for clean subtitles & metadata

val LightGreyBackground = Color(0xFFF3F4F6)  // Clean Light Grey Page Background
val LightGreyButtonBg = Color(0xFFE5E7EB)    // Light Grey Button Container Background
val LightGreyCardBg = Color(0xFFFFFFFF)      // Crisp White Surface Card
val LightGreyBorder = Color(0xFFD1D5DB)      // Subtle Grey Border

// Dark Mode Neutrals
val DarkSlateBackground = Color(0xFF0F172A)  // Slate 900 Page Background
val DarkSlateSurface = Color(0xFF1E293B)     // Slate 800 Surface Card
val DarkSlateSurfaceVariant = Color(0xFF334155) // Slate 700 Surface Variant / Button
val DarkSlateTextPrimary = Color(0xFFF8FAFC) // Slate 50 Text
val DarkSlateTextSecondary = Color(0xFF94A3B8) // Slate 400 Text Secondary
val DarkSlateBorder = Color(0xFF475569)     // Slate 600 Border

// Financial Status
val EmeraldSuccess = Color(0xFF059669)
val CrimsonDanger = Color(0xFFDC2626)

// Compatibility aliases
val AppLogoBlue = LogoPrimaryBlue
val PrimaryBlue = LogoPrimaryBlue
val PrimaryBlueLight = LogoLightBlue
val PrimaryBlueDark = LogoPrimaryBlue

val DarkModeBackground = DarkSlateBackground
val DarkModeSurface = DarkSlateSurface
val DarkModeSurfaceVariant = DarkSlateSurfaceVariant
val DarkModeOnSurface = DarkSlateTextPrimary

val LightModeBackground = LightGreyBackground
val LightModeSurface = LightGreyCardBg
val LightModeSurfaceVariant = LightGreyButtonBg
val LightModeOnSurface = TextPrimary

val CorporateBlue = LogoPrimaryBlue
val CorporateBlueLight = LogoLightBlue
val ProfessionalTeal = LogoEmeraldTeal
val ProfessionalTealLight = LogoCyan
val CorporateDanger = CrimsonDanger
