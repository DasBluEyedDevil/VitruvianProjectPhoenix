package com.example.vitruvianredux.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import com.example.vitruvianredux.ui.theme.ThemeMode

/**
 * Compact icon-only theme toggle.
 * Cycles through Light -> Dark -> System modes for full theme control.
 * Uses 48dp minimum touch target for accessibility compliance.
 */
@Composable
fun ThemeToggle(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine next mode for accessibility announcement
    val nextMode = when (mode) {
        ThemeMode.LIGHT -> ThemeMode.DARK
        ThemeMode.DARK -> ThemeMode.SYSTEM
        ThemeMode.SYSTEM -> ThemeMode.LIGHT
    }

    val currentModeLabel = when (mode) {
        ThemeMode.LIGHT -> "light mode"
        ThemeMode.DARK -> "dark mode"
        ThemeMode.SYSTEM -> "system mode"
    }

    val nextModeLabel = when (nextMode) {
        ThemeMode.LIGHT -> "light mode"
        ThemeMode.DARK -> "dark mode"
        ThemeMode.SYSTEM -> "system mode"
    }

    IconButton(
        onClick = { onModeChange(nextMode) },
        modifier = modifier
            .size(48.dp) // Material Design minimum touch target
            .semantics {
                stateDescription = "Currently $currentModeLabel"
            }
    ) {
        Icon(
            imageVector = when (mode) {
                ThemeMode.LIGHT -> Icons.Default.LightMode
                ThemeMode.DARK -> Icons.Default.DarkMode
                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
            },
            contentDescription = "Switch to $nextModeLabel",
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
