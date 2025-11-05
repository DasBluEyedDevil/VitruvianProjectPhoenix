package com.example.vitruvianredux.ui.theme

import androidx.compose.ui.graphics.Color

// Background Colors
val BackgroundBlack = Color(0xFF000000)          // Pure black background
val BackgroundDarkGrey = Color(0xFF121212)       // Dark grey surface
val SurfaceDarkGrey = Color(0xFF1E1E1E)         // Elevated surfaces
val CardBackground = Color(0xFF252525)           // Card backgrounds

// Light Theme Colors
val ColorLightBackground = Color(0xFFF8F9FB)     // Soft light background
val ColorOnLightBackground = Color(0xFF0F172A)   // Slate-900 like text
val ColorLightSurface = Color(0xFFFFFFFF)        // White surface
val ColorOnLightSurface = Color(0xFF111827)      // Dark text on surface
val ColorLightSurfaceVariant = Color(0xFFF3F4F6) // Light gray surface variant
val ColorOnLightSurfaceVariant = Color(0xFF6B7280) // Gray-500 text

// Purple Accent Colors
val PrimaryPurple = Color(0xFFBB86FC)           // Primary purple (Material 3 style)
val SecondaryPurple = Color(0xFF9965F4)         // Deeper purple
val TertiaryPurple = Color(0xFFE0BBF7)          // Light purple for highlights
val PurpleAccent = Color(0xFF7E57C2)            // Accent purple for buttons

// TopAppBar Colors (darker for better contrast)
val TopAppBarDark = Color(0xFF1A0E26)           // Very dark purple for dark mode header
val TopAppBarLight = Color(0xFF4A2F8A)          // Darker purple for light mode header

// Text Colors
val TextPrimary = Color(0xFFFFFFFF)             // Pure white text
val TextSecondary = Color(0xFFE0E0E0)           // Light grey text
val TextTertiary = Color(0xFFB0B0B0)            // Medium grey text
val TextDisabled = Color(0xFF707070)            // Disabled text

// Status Colors
val SuccessGreen = Color(0xFF4CAF50)            // Success states
val ErrorRed = Color(0xFFF44336)                // Error states
val WarningOrange = Color(0xFFFF9800)           // Warning states
val InfoBlue = Color(0xFF2196F3)                // Info states

// Legacy colors (kept for compatibility)
val Purple80 = PrimaryPurple
val PurpleGrey80 = SecondaryPurple
val Pink80 = TertiaryPurple
val Purple40 = PurpleAccent
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)