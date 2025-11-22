package com.example.vitruvianredux.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.vitruvianredux.data.local.entity.ConnectionLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for connection logs.
 * Provides methods for logging and querying Bluetooth connection events.
 */
@Dao
interface ConnectionLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ConnectionLogEntity)

    @Query("SELECT * FROM connection_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ConnectionLogEntity>>

    @Query("SELECT * FROM connection_logs WHERE deviceAddress = :deviceAddress ORDER BY timestamp DESC")
    fun getLogsForDevice(deviceAddress: String): Flow<List<ConnectionLogEntity>>

    @Query("SELECT * FROM connection_logs WHERE eventType = :eventType ORDER BY timestamp DESC")
    fun getLogsByEventType(eventType: String): Flow<List<ConnectionLogEntity>>

    @Query("SELECT * FROM connection_logs WHERE level = :level ORDER BY timestamp DESC")
    fun getLogsByLevel(level: String): Flow<List<ConnectionLogEntity>>

    @Query("SELECT * FROM connection_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<ConnectionLogEntity>>

    @Query("SELECT * FROM connection_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getLogsBetween(startTime: Long, endTime: Long): Flow<List<ConnectionLogEntity>>

    @Query("SELECT COUNT(*) FROM connection_logs WHERE level = :level")
    suspend fun getCountByLevel(level: String): Int

    @Query("DELETE FROM connection_logs WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    @Query("DELETE FROM connection_logs")
    suspend fun deleteAll()

    @Query("SELECT * FROM connection_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsForExport(): List<ConnectionLogEntity>
}
