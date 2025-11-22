package com.example.vitruvianredux.util

/**
 * Known Vitruvian device models with their capabilities.
 */
enum class VitruvianModel(
    val modelNumber: String,
    val displayName: String,
    val capabilities: HardwareCapabilities
) {
    EUCLID(
        modelNumber = "VIT-200",
        displayName = "Vitruvian V-Form Trainer (Euclid)",
        capabilities = HardwareCapabilities(
            supportsEccentricMode = true,
            supportsEchoMode = true,
            maxResistanceKg = 200.0f,
            notes = "Original V-Form Trainer. Eccentric-only mode supported but may not work correctly - under investigation."
        )
    ),
    TRAINER_PLUS(
        modelNumber = "VIT-300",
        displayName = "Vitruvian Trainer+",
        capabilities = HardwareCapabilities(
            supportsEccentricMode = true,
            supportsEchoMode = true,
            maxResistanceKg = 220.0f,
            notes = "Second generation with improved motors for better eccentric mode performance."
        )
    ),
    UNKNOWN(
        modelNumber = "UNKNOWN",
        displayName = "Unknown Vitruvian Model",
        capabilities = HardwareCapabilities(
            supportsEccentricMode = true,
            supportsEchoMode = true,
            maxResistanceKg = 200.0f,
            notes = "Unknown device model. Capabilities assumed."
        )
    )
}
