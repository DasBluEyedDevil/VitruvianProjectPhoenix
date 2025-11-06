package com.example.vitruvianredux.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for storing Bluetooth connection debug logs
 *
 * This helps diagnose connectivity issues by tracking all BLE events
 */
@Entity(
    tableName = "connection_logs",
    indices = [Index(value = ["timestamp"])]
)
data class ConnectionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Timestamp in milliseconds since epoch */
    val timestamp: Long,

    /** Event type (e.g., CONNECTION_STARTED, COMMAND_SENT, ERROR) */
    val eventType: String,

    /** Event severity level: DEBUG, INFO, WARNING, ERROR */
    val level: String,

    /** Device address (MAC address) if applicable */
    val deviceAddress: String?,

    /** Device name if applicable */
    val deviceName: String?,

    /** Short event message */
    val message: String,

    /** Detailed context or error information */
    val details: String?,

    /** Additional metadata as JSON string if needed */
    val metadata: String?
)
