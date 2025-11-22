package com.example.vitruvianredux.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.vitruvianredux.data.local.entity.DiagnosticsEntity

@Dao
interface DiagnosticsDao {
    @Insert
    suspend fun insert(diagnostics: DiagnosticsEntity): Long

    @Query("SELECT * FROM diagnostics_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<DiagnosticsEntity>

    @Query("SELECT * FROM diagnostics_history WHERE containsFaults = 1 ORDER BY timestamp DESC")
    suspend fun getFaultsOnly(): List<DiagnosticsEntity>

    @Query("DELETE FROM diagnostics_history WHERE timestamp < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long)

    @Query("SELECT COUNT(*) FROM diagnostics_history WHERE containsFaults = 1")
    suspend fun getFaultCount(): Int
}
