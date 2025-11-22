package com.example.vitruvianredux.util

/**
 * Hardware capabilities for different Vitruvian device models.
 */
data class HardwareCapabilities(
    val supportsEccentricMode: Boolean,
    val supportsEchoMode: Boolean,
    val maxResistanceKg: Float,
    val notes: String = ""
)
