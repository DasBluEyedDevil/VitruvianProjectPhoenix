package com.example.vitruvianredux.util

import java.util.UUID

/**
 * BLE Constants - UUIDs and configuration values for Vitruvian device communication
 * Ported from device.js in the reference web application
 */
object BleConstants {
    // Service UUIDs
    val GATT_SERVICE_UUID: UUID = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb")
    val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    // Characteristic UUIDs
    val NUS_RX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val MONITOR_CHAR_UUID: UUID = UUID.fromString("90e991a6-c548-44ed-969b-eb541014eae3")
    val PROPERTY_CHAR_UUID: UUID = UUID.fromString("5fa538ec-d041-42f6-bbd6-c30d475387b7")
    val REP_NOTIFY_CHAR_UUID: UUID = UUID.fromString("8308f2a6-0875-4a94-a86f-5c5c5e1b068a")

    val NOTIFY_CHAR_UUIDS = listOf(
        UUID.fromString("383f7276-49af-4335-9072-f01b0f8acad6"),
        UUID.fromString("74e994ac-0e80-4c02-9cd0-76cb31d3959b"),
        UUID.fromString("67d0dae0-5bfc-4ea2-acc9-ac784dee7f29"),
        REP_NOTIFY_CHAR_UUID,
        UUID.fromString("c7b73007-b245-4503-a1ed-9e4e97eb9802"),
        UUID.fromString("36e6c2ee-21c7-404e-aa9b-f74ca4728ad4"),
        UUID.fromString("ef0e485a-8749-4314-b1be-01e57cd1712e")
    )

    // Official app workout command characteristics (discovered from HCI logs)
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

    // Device name prefix for filtering - matches "Vee" devices
    const val DEVICE_NAME_PREFIX = "Vee"

    // Connection timeouts
    const val CONNECTION_TIMEOUT_MS = 15000L
    const val GATT_OPERATION_TIMEOUT_MS = 5000L
    const val SCAN_TIMEOUT_MS = 30000L // Increased from 10s to 30s for better device discovery
}

/**
 * Workout-related constants
 */
object WorkoutConstants {
    const val LB_PER_KG = 2.2046226218488
    const val KG_PER_LB = 1 / LB_PER_KG

    const val MIN_WEIGHT_KG = 0f
    const val MAX_WEIGHT_KG = 100f
    const val MAX_PROGRESSION_KG = 3f

    const val DEFAULT_WARMUP_REPS = 3
    const val MAX_HISTORY_POINTS = 72000 // 2 hours at 100ms

    // Position ranges
    const val MAX_POSITION = 3000
    const val MIN_POSITION = 0
}

/**
 * Protocol constants from protocol.js
 */
object ProtocolConstants {
    // Command types
    const val CMD_INIT = 0x0A.toByte()
    const val CMD_INIT_PRESET = 0x11.toByte()
    const val CMD_PROGRAM_PARAMS = 0x04.toByte()
    const val CMD_ECHO_CONTROL = 0x13.toByte()
    const val CMD_COLOR_SCHEME = 0x1D.toByte()

    // Frame sizes
    const val INIT_CMD_SIZE = 4
    const val INIT_PRESET_SIZE = 34
    const val PROGRAM_PARAMS_SIZE = 96
    const val ECHO_CONTROL_SIZE = 40
    const val COLOR_SCHEME_SIZE = 44

    // Mode values
    const val MODE_OLD_SCHOOL = 0
    const val MODE_PUMP = 2
    const val MODE_TUT = 3
    const val MODE_TUT_BEAST = 4
    const val MODE_ECCENTRIC_ONLY = 6
    const val MODE_ECHO = 10
}

