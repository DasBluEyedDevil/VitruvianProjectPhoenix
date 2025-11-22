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
    val DIAGNOSTIC_CHAR_UUID: UUID = UUID.fromString("5fa538ec-d041-42f6-bbd6-c30d475387b7") // Alias for property
    val REP_NOTIFY_CHAR_UUID: UUID = UUID.fromString("8308f2a6-0875-4a94-a86f-5c5c5e1b068a")
    val HEURISTIC_CHAR_UUID: UUID = UUID.fromString("c7b73007-b245-4503-a1ed-9e4e97eb9802")
    val VERSION_CHAR_UUID: UUID = UUID.fromString("36e6c2ee-21c7-404e-aa9b-f74ca4728ad4")

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

    // BLE operation delays
    const val BLE_QUEUE_DRAIN_DELAY_MS = 250L // Delay to allow BLE queue to drain before sending next command
}
