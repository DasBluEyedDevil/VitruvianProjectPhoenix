package com.example.vitruvianredux.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPurple,
    onPrimary = BackgroundBlack,
    primaryContainer = PurpleAccent,
    onPrimaryContainer = TextPrimary,

    secondary = SecondaryPurple,
    onSecondary = BackgroundBlack,
    secondaryContainer = SecondaryPurple,
    onSecondaryContainer = TextPrimary,

    tertiary = TertiaryPurple,
    onTertiary = BackgroundBlack,
    tertiaryContainer = TertiaryPurple,
    onTertiaryContainer = TextPrimary,

    background = BackgroundBlack,
    onBackground = TextPrimary,

    surface = SurfaceDarkGrey,
    onSurface = TextPrimary,
    surfaceVariant = CardBackground,
    onSurfaceVariant = TextSecondary,

    error = ErrorRed,
    onError = TextPrimary,

    outline = TextTertiary,
    outlineVariant = TextDisabled
)

private val LightColorScheme = lightColorScheme(
    primary = TertiaryPurple,               // Light purple for buttons
    onPrimary = ColorOnLightBackground,     // Dark text on light buttons
    primaryContainer = TertiaryPurple,      // Light purple container
    onPrimaryContainer = ColorOnLightBackground,

    secondary = TertiaryPurple,             // Light purple for secondary elements
    onSecondary = ColorOnLightBackground,   // Dark text
    secondaryContainer = TertiaryPurple,
    onSecondaryContainer = ColorOnLightBackground,

    tertiary = InfoBlue.copy(alpha = 0.3f), // Light blue
    onTertiary = ColorOnLightBackground,
    tertiaryContainer = InfoBlue.copy(alpha = 0.15f),
    onTertiaryContainer = ColorOnLightBackground,

    background = ColorLightBackground,
    onBackground = ColorOnLightBackground,

    surface = ColorLightSurface,
    onSurface = ColorOnLightSurface,
    surfaceVariant = ColorLightSurfaceVariant,
    onSurfaceVariant = ColorOnLightSurfaceVariant,

    error = ErrorRed,
    onError = ColorLightSurface,            // White text on red error

    outline = ColorOnLightSurfaceVariant.copy(alpha = 0.6f),
    outlineVariant = ColorOnLightSurfaceVariant.copy(alpha = 0.4f)
)

@Composable
fun VitruvianProjectPhoenixTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkColors = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkColors) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}