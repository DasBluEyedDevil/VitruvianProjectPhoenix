package com.example.vitruvianredux.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for connection log operations
 */
@Dao
interface ConnectionLogDao {

    /**
     * Insert a new connection log entry
     */
    @Insert
    suspend fun insert(log: ConnectionLogEntity)

    /**
     * Get all logs ordered by timestamp (most recent first)
     */
    @Query("SELECT * FROM connection_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ConnectionLogEntity>>

    /**
     * Get logs for a specific device
     */
    @Query("SELECT * FROM connection_logs WHERE deviceAddress = :deviceAddress ORDER BY timestamp DESC")
    fun getLogsForDevice(deviceAddress: String): Flow<List<ConnectionLogEntity>>

    /**
     * Get logs by event type
     */
    @Query("SELECT * FROM connection_logs WHERE eventType = :eventType ORDER BY timestamp DESC")
    fun getLogsByEventType(eventType: String): Flow<List<ConnectionLogEntity>>

    /**
     * Get logs by severity level
     */
    @Query("SELECT * FROM connection_logs WHERE level = :level ORDER BY timestamp DESC")
    fun getLogsByLevel(level: String): Flow<List<ConnectionLogEntity>>

    /**
     * Get recent logs (last N entries)
     */
    @Query("SELECT * FROM connection_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<ConnectionLogEntity>>

    /**
     * Get logs within a time range
     */
    @Query("SELECT * FROM connection_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getLogsBetween(startTime: Long, endTime: Long): Flow<List<ConnectionLogEntity>>

    /**
     * Get count of logs by level
     */
    @Query("SELECT COUNT(*) FROM connection_logs WHERE level = :level")
    suspend fun getCountByLevel(level: String): Int

    /**
     * Delete logs older than specified timestamp
     */
    @Query("DELETE FROM connection_logs WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    /**
     * Delete all logs
     */
    @Query("DELETE FROM connection_logs")
    suspend fun deleteAll()

    /**
     * Get logs for export (all data, oldest first)
     */
    @Query("SELECT * FROM connection_logs ORDER BY timestamp ASC")
    suspend fun getAllLogsForExport(): List<ConnectionLogEntity>
}
