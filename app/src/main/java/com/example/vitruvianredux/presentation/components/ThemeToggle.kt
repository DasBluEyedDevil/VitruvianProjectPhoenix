package com.example.vitruvianredux.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import com.example.vitruvianredux.ui.theme.ThemeMode

/**
 * Compact icon-only theme toggle.
 * Cycles between Light -> Dark -> System modes.
 */
@Composable
fun ThemeToggle(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = {
            // Cycle through: Light -> Dark -> System -> Light
            val nextMode = when (mode) {
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
            }
            onModeChange(nextMode)
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = when (mode) {
                ThemeMode.LIGHT -> Icons.Default.LightMode
                ThemeMode.DARK -> Icons.Default.DarkMode
                ThemeMode.SYSTEM -> Icons.Default.LightMode // Use light icon for system
            },
            contentDescription = "Toggle theme (current: ${mode.name.lowercase()})",
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
