package com.example.vitruvianredux.util

import android.os.Build

/**
 * Device information utility for logging and debugging
 */
object DeviceInfo {

    /**
     * Get device manufacturer (e.g., "samsung", "Google")
     */
    val manufacturer: String = Build.MANUFACTURER

    /**
     * Get device model (e.g., "SM-G998U" for S21 Ultra)
     */
    val model: String = Build.MODEL

    /**
     * Get device name (e.g., "Galaxy S21 Ultra 5G")
     */
    val device: String = Build.DEVICE

    /**
     * Get Android version string (e.g., "12", "13")
     */
    val androidVersion: String = Build.VERSION.RELEASE

    /**
     * Get Android SDK level (e.g., 31 for Android 12)
     */
    val sdkInt: Int = Build.VERSION.SDK_INT

    /**
     * Get full Android version string with SDK level
     */
    val androidVersionFull: String = "Android $androidVersion (SDK $sdkInt)"

    /**
     * Get device fingerprint (unique build ID)
     */
    val fingerprint: String = Build.FINGERPRINT

    /**
     * Get a formatted device info string for logging
     */
    fun getFormattedInfo(): String {
        return buildString {
            appendLine("Device: $manufacturer $model")
            appendLine("Model Name: $device")
            appendLine("OS: $androidVersionFull")
            appendLine("Build: ${Build.DISPLAY}")
        }
    }

    /**
     * Get a compact one-line device description
     */
    fun getCompactInfo(): String {
        return "$manufacturer $model (Android $androidVersion, SDK $sdkInt)"
    }

    /**
     * Get device info as structured JSON string for metadata storage
     */
    fun toJson(): String {
        return """{"manufacturer":"$manufacturer","model":"$model","device":"$device","androidVersion":"$androidVersion","sdkInt":$sdkInt,"fingerprint":"$fingerprint"}"""
    }

    /**
     * Check if running on Android 12 or higher (new BLE permissions)
     */
    fun isAndroid12OrHigher(): Boolean = sdkInt >= Build.VERSION_CODES.S

    /**
     * Check if running on Samsung device
     */
    fun isSamsung(): Boolean = manufacturer.equals("samsung", ignoreCase = true)

    /**
     * Check if running on Google Pixel
     */
    fun isPixel(): Boolean = manufacturer.equals("Google", ignoreCase = true)
}
