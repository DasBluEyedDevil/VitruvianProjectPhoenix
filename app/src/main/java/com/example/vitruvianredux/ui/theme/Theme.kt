package com.example.vitruvianredux.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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

@Composable
fun VitruvianReduxTheme(
    content: @Composable () -> Unit
) {
    // Always use dark theme with custom colors (no dynamic color, no light mode)
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}