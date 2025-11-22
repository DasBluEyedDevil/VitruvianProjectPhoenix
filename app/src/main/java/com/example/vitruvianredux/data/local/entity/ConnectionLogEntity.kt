package com.example.vitruvianredux.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing connection log entries for Bluetooth device connections.
 */
@Entity(tableName = "connection_logs")
data class ConnectionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val timestamp: Long,

    val eventType: String,

    val level: String,

    val deviceAddress: String? = null,

    val deviceName: String? = null,

    val message: String,

    val details: String? = null,

    val metadata: String? = null
)
