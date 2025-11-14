package com.example.vitruvianredux.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Helper Functions
 * Provides consistent expressive styling for components
 */

/**
 * Material 3 Expressive Card Colors
 * Uses surfaceContainerHighest for better contrast
 */
@Composable
fun expressiveCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
)

/**
 * Material 3 Expressive Card Shape
 * More rounded corners (20dp vs standard 16dp)
 */
val expressiveCardShape = RoundedCornerShape(20.dp)

/**
 * Material 3 Expressive Card Elevation
 * Higher elevation (8dp vs standard 4dp)
 */
@Composable
fun expressiveCardElevation(pressed: Boolean = false) = CardDefaults.cardElevation(
    defaultElevation = if (pressed) 4.dp else 8.dp
)

/**
 * Material 3 Expressive Card Border
 * Thicker border (2dp vs standard 1dp)
 */
@Composable
fun expressiveCardBorder() = BorderStroke(
    2.dp,
    MaterialTheme.colorScheme.outlineVariant
)

/**
 * Material 3 Expressive Button Shape
 * More rounded corners
 */
val expressiveButtonShape = RoundedCornerShape(16.dp)

/**
 * Material 3 Expressive Button Elevation
 * Higher elevation
 */
@Composable
fun expressiveButtonElevation() = androidx.compose.material3.ButtonDefaults.buttonElevation(
    defaultElevation = 4.dp,
    pressedElevation = 2.dp
)

