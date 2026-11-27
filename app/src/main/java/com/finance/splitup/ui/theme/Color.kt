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

// Category palette (semantic colors shared across budget rows, charts,
// insights breakdowns and receipt allocation).
val CategoryFood = Color(0xFF2563EB)          // Blue (Food & Dining)
val CategoryGroceries = Color(0xFF0D9488)     // Teal (Groceries)
val CategoryTransport = Color(0xFFD97706)     // Amber (Transport)
val CategoryShopping = Color(0xFF7C3AED)      // Violet (Shopping)
val CategoryBills = Color(0xFF0284C7)         // Sky Blue (Bills & Utilities)
val CategoryEntertainment = Color(0xFFE11D48) // Rose (Entertainment)
val CategoryHealth = Color(0xFFDC2626)        // Crimson Red (Health & Wellness)
val CategoryTravel = Color(0xFF4F46E5)        // Indigo (Travel)
val CategoryEducation = Color(0xFFCA8A04)     // Gold (Education)
val CategorySettlement = Color(0xFF0891B2)    // Cyan (Settlement)
val CategoryIncome = Color(0xFF059669)        // Emerald Green (Income)
val CategoryOther = Color(0xFF475569)         // Slate (Other)

// Logo micro-details
val LogoChipTextAmber = Color(0xFF92400E)     // Amber-800 text on the gold chip

// Third-party brand colors (must stay fixed regardless of theme)
val PayPalBrandBlue = Color(0xFF003087)

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
