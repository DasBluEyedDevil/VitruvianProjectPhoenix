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
