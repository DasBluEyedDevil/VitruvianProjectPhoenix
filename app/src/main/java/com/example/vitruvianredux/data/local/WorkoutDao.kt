package com.example.vitruvianredux.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for workout data
 */
@Dao
interface WorkoutDao {
    
    // Sessions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSessionEntity)
    
    @Query("SELECT * FROM workout_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<WorkoutSessionEntity>>
    
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): WorkoutSessionEntity?
    
    @Query("SELECT * FROM workout_sessions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSessions(limit: Int = 10): Flow<List<WorkoutSessionEntity>>
    
    @Query("DELETE FROM workout_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)
    
    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllSessions()
    
    // Metrics
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetric(metric: WorkoutMetricEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetrics(metrics: List<WorkoutMetricEntity>)
    
    @Query("SELECT * FROM workout_metrics WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMetricsForSession(sessionId: String): Flow<List<WorkoutMetricEntity>>
    
    @Query("DELETE FROM workout_metrics WHERE sessionId = :sessionId")
    suspend fun deleteMetricsForSession(sessionId: String)
    
    @Query("DELETE FROM workout_metrics")
    suspend fun deleteAllMetrics()
    
    // Combined operations
    @Transaction
    suspend fun deleteWorkout(sessionId: String) {
        deleteSession(sessionId)
        deleteMetricsForSession(sessionId)
    }
    
    @Transaction
    suspend fun deleteAllWorkouts() {
        deleteAllSessions()
        deleteAllMetrics()
    }
}
