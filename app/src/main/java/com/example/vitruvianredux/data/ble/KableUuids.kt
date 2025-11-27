package com.example.vitruvianredux.data.ble

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * BLE Service and Characteristic UUIDs for Vitruvian Trainer.
 * Compatible with Kable's Uuid format (using kotlin.uuid.Uuid).
 */
@OptIn(ExperimentalUuidApi::class)
object KableUuids {
    // Nordic UART Service (NUS)
    val NUS_SERVICE: Uuid = Uuid.parse("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    // Characteristics
    val NUS_RX_CHAR: Uuid = Uuid.parse("6e400002-b5a3-f393-e0a9-e50e24dcca9e")       // Write
    val MONITOR_CHAR: Uuid = Uuid.parse("90e991a6-c548-44ed-969b-eb541014eae3")      // Notify
    val DIAGNOSTIC_CHAR: Uuid = Uuid.parse("5fa538ec-d041-42f6-bbd6-c30d475387b7")   // Read
    val REPS_CHAR: Uuid = Uuid.parse("8308f2a6-0875-4a94-a86f-5c5c5e1b068a")         // Notify
    val HEURISTIC_CHAR: Uuid = Uuid.parse("c7b73007-b245-4503-a1ed-9e4e97eb9802")    // Read
    val VERSION_CHAR: Uuid = Uuid.parse("74e994ac-0e80-4c02-9cd0-76cb31d3959b")      // Notify

    // Client Characteristic Configuration Descriptor (for notifications)
    val CCCD: Uuid = Uuid.parse("00002902-0000-1000-8000-00805f9b34fb")

    // String versions for compatibility
    const val NUS_SERVICE_STR = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    const val NUS_RX_CHAR_STR = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
    const val MONITOR_CHAR_STR = "90e991a6-c548-44ed-969b-eb541014eae3"
    const val DIAGNOSTIC_CHAR_STR = "5fa538ec-d041-42f6-bbd6-c30d475387b7"
    const val REPS_CHAR_STR = "8308f2a6-0875-4a94-a86f-5c5c5e1b068a"
    const val HEURISTIC_CHAR_STR = "c7b73007-b245-4503-a1ed-9e4e97eb9802"
}
