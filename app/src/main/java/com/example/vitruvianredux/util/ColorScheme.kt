package com.example.vitruvianredux.util

/**
 * Color scheme for Vitruvian LED display.
 */
data class ColorScheme(
    val name: String,
    val brightness: Float,
    val colors: List<RGBColor>
)
