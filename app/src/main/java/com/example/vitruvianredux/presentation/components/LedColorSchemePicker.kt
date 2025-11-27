package com.example.vitruvianredux.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vitruvianredux.util.ColorScheme
import com.example.vitruvianredux.util.ColorSchemes

/**
 * Enhanced LED Color Scheme Picker with better UX.
 *
 * Features:
 * - Grid layout of color options with clear selection state
 * - Checkmark indicator on selected scheme
 * - Animated selection transitions
 *
 * @param selectedSchemeIndex Currently selected color scheme index
 * @param onSchemeSelected Callback when a scheme is selected
 * @param modifier Modifier for the component
 */
@Composable
fun LedColorSchemePicker(
    selectedSchemeIndex: Int,
    onSchemeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorSchemes = ColorSchemes.ALL

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Color Scheme Grid (2 rows)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // First row: Blue, Green, Teal, Yellow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                colorSchemes.take(4).forEachIndexed { index, scheme ->
                    ColorSchemeOption(
                        scheme = scheme,
                        isSelected = selectedSchemeIndex == index,
                        onClick = { onSchemeSelected(index) }
                    )
                }
            }

            // Second row: Pink, Red, Purple
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Add spacer for centering 3 items
                Spacer(modifier = Modifier.width(40.dp))
                colorSchemes.drop(4).forEachIndexed { index, scheme ->
                    ColorSchemeOption(
                        scheme = scheme,
                        isSelected = selectedSchemeIndex == index + 4,
                        onClick = { onSchemeSelected(index + 4) }
                    )
                }
                Spacer(modifier = Modifier.width(40.dp))
            }
        }

        // Current selection label
        Text(
            text = "Selected: ${colorSchemes.getOrNull(selectedSchemeIndex)?.name ?: "Blue"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

/**
 * Individual color scheme option in the grid.
 * Shows a gradient preview with the scheme colors and a checkmark when selected.
 */
@Composable
private fun ColorSchemeOption(
    scheme: ColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = scheme.colors.map { Color(it.r, it.g, it.b) }
    val gradientColors = if (colors.size >= 2) colors else listOf(colors.firstOrNull() ?: Color.Gray, Color.DarkGray)

    // Animate selection state
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "border"
    )

    val elevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 2.dp,
        label = "elevation"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .shadow(elevation, CircleShape)
            .clip(CircleShape)
            .background(Brush.linearGradient(gradientColors))
            .then(
                if (isSelected) {
                    Modifier.border(borderWidth, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Checkmark for selected state
        if (isSelected) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(4.dp)
                        .fillMaxSize()
                )
            }
        }
    }
}

/**
 * Full LED Color Scheme Card for Settings screen.
 * Includes the header, picker, and optional brightness control.
 */
@Composable
fun LedColorSchemeCard(
    selectedSchemeIndex: Int,
    onSchemeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated gradient icon background
                val infiniteTransition = rememberInfiniteTransition(label = "icon_glow")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "glow"
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF3B82F6).copy(alpha = glowAlpha),
                                    Color(0xFF8B5CF6).copy(alpha = glowAlpha)
                                )
                            ),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // LED dots icon
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "LED Color Scheme",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Customize your machine's LED strip",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Color Scheme Picker
            LedColorSchemePicker(
                selectedSchemeIndex = selectedSchemeIndex,
                onSchemeSelected = onSchemeSelected
            )
        }
    }
}
