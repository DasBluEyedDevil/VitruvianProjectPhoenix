package com.example.vitruvianredux.util

/**
 * Vitruvian Hardware Model Detection
 *
 * Identifies which generation of Vitruvian Trainer is connected based on device name
 * and determines hardware capabilities.
 *
 * Known Models:
 * - Euclid (VIT-200): Original V-Form Trainer with older motors
 *   - Device names start with "Vee"
 *   - Limited eccentric mode support due to hardware constraints
 *
 * - Trainer+: Second generation with improved motors
 *   - Better eccentric mode performance
 *   - Smoother operation overall
 *
 * Note: Currently we can only detect Euclid devices via "Vee" prefix.
 * Trainer+ detection would require additional device name patterns to be identified.
 */
object HardwareDetection {

    /**
     * Detect Vitruvian hardware model from device name
     */
    fun detectModel(deviceName: String): VitruvianModel {
        return when {
            // Euclid/V-Form devices use "Vee" prefix
            deviceName.startsWith("Vee", ignoreCase = true) -> VitruvianModel.EUCLID

            // Trainer+ detection pattern (to be confirmed with actual device names)
            deviceName.startsWith("Vitruvian", ignoreCase = true) -> VitruvianModel.TRAINER_PLUS

            // Default to unknown if pattern doesn't match
            else -> VitruvianModel.UNKNOWN
        }
    }

    /**
     * Get hardware capabilities for a device name
     */
    fun getCapabilities(deviceName: String): HardwareCapabilities {
        val model = detectModel(deviceName)
        return model.capabilities
    }

    /**
     * Check if eccentric mode is supported on this device
     */
    fun supportsEccentricMode(deviceName: String): Boolean {
        return getCapabilities(deviceName).supportsEccentricMode
    }

    /**
     * Get user-friendly model name for display
     */
    fun getDisplayName(deviceName: String): String {
        val model = detectModel(deviceName)
        return "${model.displayName} ($deviceName)"
    }
}

/**
 * Vitruvian hardware models
 */
enum class VitruvianModel(
    val modelNumber: String,
    val displayName: String,
    val capabilities: HardwareCapabilities
) {
    /**
     * Euclid (VIT-200) - Original V-Form Trainer
     * First generation with eccentric mode support (confirmed in 2021 reviews)
     * However, users report eccentric-only mode not working properly on this hardware
     */
    EUCLID(
        modelNumber = "VIT-200",
        displayName = "Vitruvian V-Form Trainer (Euclid)",
        capabilities = HardwareCapabilities(
            supportsEccentricMode = true,  // Feature exists but has known issues
            supportsEchoMode = true,
            maxResistanceKg = 200f,
            notes = "Original V-Form Trainer. Eccentric-only mode supported but may not work correctly - under investigation."
        )
    ),

    /**
     * Trainer+ - Second generation with improved motors
     */
    TRAINER_PLUS(
        modelNumber = "VIT-300",  // Assumed model number
        displayName = "Vitruvian Trainer+",
        capabilities = HardwareCapabilities(
            supportsEccentricMode = true,
            supportsEchoMode = true,
            maxResistanceKg = 220f,
            notes = "Second generation with improved motors for better eccentric mode performance."
        )
    ),

    /**
     * Unknown model - treat conservatively
     */
    UNKNOWN(
        modelNumber = "UNKNOWN",
        displayName = "Unknown Vitruvian Model",
        capabilities = HardwareCapabilities(
            supportsEccentricMode = true,  // Assume support for unknown devices
            supportsEchoMode = true,
            maxResistanceKg = 200f,
            notes = "Unknown device model. Capabilities assumed."
        )
    )
}

/**
 * Hardware capabilities for a specific Vitruvian model
 */
data class HardwareCapabilities(
    val supportsEccentricMode: Boolean,
    val supportsEchoMode: Boolean,
    val maxResistanceKg: Float,
    val notes: String = ""
)
