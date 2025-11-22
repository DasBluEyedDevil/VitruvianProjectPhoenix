package com.example.vitruvianredux.util

import java.util.UUID

/**
 * BLE UUIDs and constants for Vitruvian device communication.
 */
object BleConstants {
    // Service UUIDs
    val GATT_SERVICE_UUID: UUID = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb")
    val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    // Characteristic UUIDs
    val NUS_RX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val MONITOR_CHAR_UUID: UUID = UUID.fromString("90e991a6-c548-44ed-969b-eb541014eae3")
    val DIAGNOSTIC_CHAR_UUID: UUID = UUID.fromString("5fa538ec-d041-42f6-bbd6-c30d475387b7")
    val PROPERTY_CHAR_UUID: UUID = DIAGNOSTIC_CHAR_UUID
    val REP_NOTIFY_CHAR_UUID: UUID = UUID.fromString("8308f2a6-0875-4a94-a86f-5c5c5e1b068a")
    val HEURISTIC_CHAR_UUID: UUID = UUID.fromString("c7b73007-b245-4503-a1ed-9e4e97eb9802")
    val VERSION_CHAR_UUID: UUID = UUID.fromString("74e994ac-0e80-4c02-9cd0-76cb31d3959b")

    // Notification characteristic UUIDs
    val NOTIFY_CHAR_UUIDS: List<UUID> = listOf(
        UUID.fromString("383f7276-49af-4335-9072-f01b0f8acad6"),
        UUID.fromString("74e994ac-0e80-4c02-9cd0-76cb31d3959b"),
        UUID.fromString("67d0dae0-5bfc-4ea2-acc9-ac784dee7f29"),
        REP_NOTIFY_CHAR_UUID,
        UUID.fromString("c7b73007-b245-4503-a1ed-9e4e97eb9802"),
        UUID.fromString("36e6c2ee-21c7-404e-aa9b-f74ca4728ad4"),
        UUID.fromString("ef0e485a-8749-4314-b1be-01e57cd1712e")
    )

    // Workout command characteristic UUIDs
    val WORKOUT_CMD_CHAR_UUIDS: List<UUID> = listOf(
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6a5"),
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6a6"),
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6a7"),
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6a8"),
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6a9"),
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6aa"),
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6ab"),
        UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6ac")
    )

    // Device identification
    const val DEVICE_NAME_PREFIX = "Vee"

    // Timeouts
    const val CONNECTION_TIMEOUT_MS = 15_000L
    const val GATT_OPERATION_TIMEOUT_MS = 5_000L
    const val SCAN_TIMEOUT_MS = 30_000L
    const val BLE_QUEUE_DRAIN_DELAY_MS = 250L

    // Init sequence configuration
    const val INIT_RESPONSE_TIMEOUT_MS = 8_000L
    const val INIT_MAX_RETRIES = 2
    const val INIT_RETRY_DELAY_MS = 1_000L
}
