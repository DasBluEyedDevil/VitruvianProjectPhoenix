package com.example.vitruvianredux.data.ble

/**
 * Represents the connection status of a BLE device.
 */
sealed class ConnectionStatus {
    /**
     * The device is disconnected.
     */
    data object Disconnected : ConnectionStatus()

    /**
     * The device is connected and ready for communication.
     */
    data object Ready : ConnectionStatus()

    /**
     * An error occurred with the connection.
     */
    data class Error(val message: String) : ConnectionStatus()
}