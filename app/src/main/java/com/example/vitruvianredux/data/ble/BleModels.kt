package com.example.vitruvianredux.data.ble

/**
 * Connection status sealed class
 */
sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Ready : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

enum class HandleState {
    WaitingForRest,  // Initial state - waiting for handles to be at rest before arming grab detection
    Released,        // Handles at rest - armed for grab detection
    Grabbed,         // Handles grabbed - force > 3kg sustained
    Moving           // Handles in motion
}

/**
 * Rep notification data class
 * Parsed from device notifications on characteristic 8308f2a6-0875-4a94-a86f-5c5c5e1b068a
 *
 * Official App 24-byte Structure:
 * - topCounter (u32): Concentric/up phase completions
 * - completeCounter (u32): Eccentric/down phase completions
 * - rangeTop (float): Maximum ROM boundary
 * - rangeBottom (float): Minimum ROM boundary
 * - repsRomCount (u16): Warmup reps with proper ROM - USE FOR WARMUP DISPLAY
 * - repsRomTotal (u16): Total reps regardless of ROM
 * - repsSetCount (u16): Working set rep count - USE FOR WORKING REPS DISPLAY
 * - repsSetTotal (u16): Total reps in set
 */
data class RepNotification(
    val topCounter: Int,        // u32: Concentric completions (up counter)
    val completeCounter: Int,   // u32: Eccentric completions (down counter)
    val repsRomCount: Int = 0,  // u16: Warmup reps (proper ROM) - DISPLAY THIS FOR WARMUP
    val repsSetCount: Int = 0,  // u16: Working set reps - DISPLAY THIS FOR WORKING
    val rangeTop: Float = 0f,   // ROM max boundary
    val rangeBottom: Float = 0f, // ROM min boundary
    val rawData: ByteArray,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RepNotification

        if (topCounter != other.topCounter) return false
        if (completeCounter != other.completeCounter) return false
        if (repsRomCount != other.repsRomCount) return false
        if (repsSetCount != other.repsSetCount) return false
        if (rangeTop != other.rangeTop) return false
        if (rangeBottom != other.rangeBottom) return false
        if (!rawData.contentEquals(other.rawData)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = topCounter
        result = 31 * result + completeCounter
        result = 31 * result + repsRomCount
        result = 31 * result + repsSetCount
        result = 31 * result + rangeTop.hashCode()
        result = 31 * result + rangeBottom.hashCode()
        result = 31 * result + rawData.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * Request for auto-reconnection after BLE stack issues
 * Emitted when onServicesInvalidated() is called (Android 16 Pixel BLE bug)
 */
data class ReconnectionRequest(
    val deviceName: String?,
    val deviceAddress: String,
    val reason: String,
    val timestamp: Long
)
