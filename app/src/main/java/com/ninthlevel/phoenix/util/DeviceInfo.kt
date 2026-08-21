package com.ninthlevel.phoenix.util

import android.os.Build
import com.ninthlevel.phoenix.BuildConfig

/**
 * Device and app information utility for logging and debugging
 */
object DeviceInfo {

    // ==================== App Build Info ====================

    /**
     * App version name (e.g., "0.5.1-beta" or "0.5.1-beta-DEBUG")
     */
    val appVersionName: String = BuildConfig.VERSION_NAME

    /**
     * App version code (incrementing integer)
     */
    val appVersionCode: Int = BuildConfig.VERSION_CODE

    /**
     * Build type (debug or release)
     */
    val buildType: String = BuildConfig.BUILD_TYPE

    /**
     * Whether this is a debug build
     */
    val isDebugBuild: Boolean = BuildConfig.DEBUG

    /**
     * Application ID (may differ between debug/release)
     */
    val applicationId: String = BuildConfig.APPLICATION_ID

    // ==================== Android Device Info ====================

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

    // ==================== Formatted Output ====================

    /**
     * Get a formatted device and app info string for logging
     */
    fun getFormattedInfo(): String {
        return buildString {
            appendLine("App: ProjectPhoenix v$appVersionName (build $appVersionCode)")
            appendLine("Build Type: $buildType")
            appendLine()
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
     * Get a compact one-line app version description
     */
    fun getAppVersionInfo(): String {
        return "v$appVersionName ($buildType)"
    }

    /**
     * Get device info as structured JSON string for metadata storage
     */
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"appVersion\":\"$appVersionName\",")
            append("\"appVersionCode\":$appVersionCode,")
            append("\"buildType\":\"$buildType\",")
            append("\"applicationId\":\"$applicationId\",")
            append("\"manufacturer\":\"$manufacturer\",")
            append("\"model\":\"$model\",")
            append("\"device\":\"$device\",")
            append("\"androidVersion\":\"$androidVersion\",")
            append("\"sdkInt\":$sdkInt,")
            append("\"fingerprint\":\"$fingerprint\"")
            append("}")
        }
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
