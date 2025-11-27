package com.example.vitruvianredux.data.ble

import com.benasher44.uuid.uuidFrom

/**
 * BLE Service and Characteristic UUIDs for Vitruvian Trainer.
 * Compatible with Kable's UUID format.
 */
object KableUuids {
    // Nordic UART Service (NUS)
    val NUS_SERVICE = uuidFrom("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    // Characteristics
    val NUS_RX_CHAR = uuidFrom("6e400002-b5a3-f393-e0a9-e50e24dcca9e")       // Write
    val MONITOR_CHAR = uuidFrom("90e991a6-c548-44ed-969b-eb541014eae3")      // Notify
    val DIAGNOSTIC_CHAR = uuidFrom("5fa538ec-d041-42f6-bbd6-c30d475387b7")   // Read
    val REPS_CHAR = uuidFrom("8308f2a6-0875-4a94-a86f-5c5c5e1b068a")         // Notify
    val HEURISTIC_CHAR = uuidFrom("c7b73007-b245-4503-a1ed-9e4e97eb9802")    // Read
    val VERSION_CHAR = uuidFrom("74e994ac-0e80-4c02-9cd0-76cb31d3959b")      // Notify

    // Client Characteristic Configuration Descriptor (for notifications)
    val CCCD = uuidFrom("00002902-0000-1000-8000-00805f9b34fb")
}
