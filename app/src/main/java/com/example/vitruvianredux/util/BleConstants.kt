package com.example.vitruvianredux.util

import java.util.UUID

/**
 * BLE Constants - UUIDs and configuration values for Vitruvian device communication
 * Based on device protocol ([removed]ed trainer)
 */
@Suppress("unused")  // Protocol reference constants - many are kept for documentation
object BleConstants {
    // Service UUIDs
    val GATT_SERVICE_UUID: UUID = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb")
    val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    // Primary Characteristic UUIDs (from device protocol)
    val NUS_RX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val SAMPLE_CHAR_UUID: UUID = UUID.fromString("90e991a6-c548-44ed-969b-eb541014eae3") // 28 bytes
    val MONITOR_CHAR_UUID: UUID = SAMPLE_CHAR_UUID // Alias for backward compat
    val CABLE_LEFT_CHAR_UUID: UUID = UUID.fromString("bc4344e9-8d63-4c89-8263-951e2d74f744") // 6 bytes
    val CABLE_RIGHT_CHAR_UUID: UUID = UUID.fromString("92ef83d6-8916-4921-8172-a9919bc82566") // 6 bytes
    val REPS_CHAR_UUID: UUID = UUID.fromString("8308f2a6-0875-4a94-a86f-5c5c5e1b068a") // 24 bytes notifiable
    val REP_NOTIFY_CHAR_UUID: UUID = REPS_CHAR_UUID // Alias
    val MODE_CHAR_UUID: UUID = UUID.fromString("67d0dae0-5bfc-4ea2-acc9-ac784dee7f29") // 4 bytes notifiable
    val VERSION_CHAR_UUID: UUID = UUID.fromString("74e994ac-0e80-4c02-9cd0-76cb31d3959b") // Variable
    val WIFI_STATE_CHAR_UUID: UUID = UUID.fromString("a7d06ce0-2e84-485f-9c25-3d4ba6fe7319") // 74 bytes
    val UPDATE_STATE_CHAR_UUID: UUID = UUID.fromString("383f7276-49af-4335-9072-f01b0f8acad6") // Variable
    val BLE_UPDATE_REQUEST_CHAR_UUID: UUID = UUID.fromString("ef0e485a-8749-4314-b1be-01e57cd1712e") // 5 bytes notifiable
    val HEURISTIC_CHAR_UUID: UUID = UUID.fromString("c7b73007-b245-4503-a1ed-9e4e97eb9802") // Variable
    val DIAGNOSTIC_CHAR_UUID: UUID = UUID.fromString("5fa538ec-d041-42f6-bbd6-c30d475387b7") // Variable
    val PROPERTY_CHAR_UUID: UUID = DIAGNOSTIC_CHAR_UUID // Alias

    // Unknown/Auth characteristic - present in web apps notification list
    // Purpose unclear but may be needed for proper device communication
    val UNKNOWN_AUTH_CHAR_UUID: UUID = UUID.fromString("36e6c2ee-21c7-404e-aa9b-f74ca4728ad4")

    val NOTIFY_CHAR_UUIDS = listOf(
        UPDATE_STATE_CHAR_UUID,
        VERSION_CHAR_UUID,
        MODE_CHAR_UUID,
        REPS_CHAR_UUID,
        HEURISTIC_CHAR_UUID,
        BLE_UPDATE_REQUEST_CHAR_UUID,
        UNKNOWN_AUTH_CHAR_UUID,  // Web apps subscribe to this
        SAMPLE_CHAR_UUID  // Monitor data - use notifications instead of read polling (fixes Android 16 Pixel disconnect)
    )

    // Official app workout command characteristics (discovered )
    // These are writable characteristics (props: 4 = WRITE_NO_RESPONSE)
    val WORKOUT_CMD_CHAR_UUIDS = listOf(
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6a5"),
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6a6"),
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6a7"),
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6a8"),
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6a9"),
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6aa"),
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6ab"),
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6ac")
    )

    // Device name pattern for filtering - matches "Vitruvian*" devices
    const val DEVICE_NAME_PREFIX = "Vee"
    val DEVICE_NAME_PATTERN = Regex("^Vitruvian.*$")

    // Command IDs (Official Protocol from device protocol)
    object Commands {
        const val STOP_COMMAND: Byte = 0x50        // Stop/halt (trainer)
        const val RESET_COMMAND: Byte = 0x0A       // Reset/init (web app stop) - recovery fallback
        const val REGULAR_COMMAND: Byte = 0x4F    // 25-byte packet (79 decimal)
        const val ECHO_COMMAND: Byte = 0x4E       // 29-byte packet (78 decimal)
        const val ACTIVATION_COMMAND: Byte = 0x04 // 97-byte packet
        const val DEFAULT_ROM_REP_COUNT: Byte = 3
    }

    // Legacy aliases for backward compatibility
    @Suppress("unused") const val CMD_REGULAR = 0x4F
    @Suppress("unused") const val CMD_ECHO = 0x4E
    @Suppress("unused") const val CMD_STOP = 0x50

    // Data Protocol Constants (from device protocol)
    @Suppress("unused")  // Protocol reference documentation
    object DataProtocol {
        // Scaling factors for cable data
        const val POSITION_SCALE = 10.0  // divide raw by 10 for mm
        const val VELOCITY_SCALE = 10.0  // divide raw by 10 for mm/s
        const val FORCE_SCALE = 100.0    // divide raw by 100 for percentage

        // Valid ranges
        const val POSITION_MIN = -1000.0
        const val POSITION_MAX = 1000.0
        const val VELOCITY_MIN = -1000.0
        const val VELOCITY_MAX = 1000.0
        const val FORCE_MIN = 0.0
        const val FORCE_MAX = 100.0

        // Data sizes
        const val CABLE_DATA_SIZE = 6     // 3 x Int16
        const val SAMPLE_DATA_SIZE = 28   // 2 cables + timestamp + status
        const val REPS_DATA_SIZE = 24
    }

    // Connection timeouts
    const val CONNECTION_TIMEOUT_MS = 15000L
    const val GATT_OPERATION_TIMEOUT_MS = 5000L
    const val SCAN_TIMEOUT_MS = 30000L

    // BLE operation delays
    const val BLE_QUEUE_DRAIN_DELAY_MS = 250L
}
