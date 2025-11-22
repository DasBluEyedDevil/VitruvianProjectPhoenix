package com.example.vitruvianredux.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.vitruvianredux.data.local.entity.PhaseStatisticsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhaseStatisticsDao {
    @Insert
    suspend fun insert(stats: PhaseStatisticsEntity): Long

    @Query("SELECT * FROM phase_statistics WHERE sessionId = :sessionId")
    suspend fun getBySessionId(sessionId: String): PhaseStatisticsEntity?

    @Query("SELECT * FROM phase_statistics WHERE sessionId IN (:sessionIds)")
    suspend fun getBySessionIds(sessionIds: List<String>): List<PhaseStatisticsEntity>

    @Query("DELETE FROM phase_statistics WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("SELECT * FROM phase_statistics ORDER BY timestamp DESC")
    fun getAll(): Flow<List<PhaseStatisticsEntity>>
}
