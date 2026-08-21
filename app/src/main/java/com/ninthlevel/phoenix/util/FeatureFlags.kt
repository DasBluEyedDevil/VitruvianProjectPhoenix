package com.ninthlevel.phoenix.util

/**
 * Feature flags for controlling app behavior.
 * Can be backed by SharedPreferences, Firebase Remote Config, etc.
 */
object FeatureFlags {

    /**
     * Use Kable BLE library instead of Nordic BLE.
     * Set to true to enable Kable implementation.
     */
    var useKableBle: Boolean = false
        private set

    /**
     * Enable Kable BLE implementation.
     * Call this at app startup to switch to Kable.
     */
    fun enableKableBle() {
        useKableBle = true
    }

    /**
     * Disable Kable BLE (use Nordic).
     * This is the default.
     */
    fun disableKableBle() {
        useKableBle = false
    }
}
