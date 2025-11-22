package com.example.vitruvianredux.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.vitruvianredux.data.local.entity.PersonalRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for personal records.
 * Provides methods for tracking and querying user's best performances.
 */
@Dao
interface PersonalRecordDao {

    @Query("""
        SELECT * FROM personal_records
        WHERE exerciseId = :exerciseId AND workoutMode = :workoutMode
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getLatestPR(exerciseId: String, workoutMode: String): PersonalRecordEntity?

    @Query("SELECT * FROM personal_records WHERE exerciseId = :exerciseId ORDER BY weightPerCableKg DESC")
    fun getPRsForExercise(exerciseId: String): Flow<List<PersonalRecordEntity>>

    @Query("""
        SELECT * FROM personal_records
        WHERE exerciseId = :exerciseId
        ORDER BY weightPerCableKg DESC
        LIMIT 1
    """)
    suspend fun getBestPR(exerciseId: String): PersonalRecordEntity?

    @Query("SELECT * FROM personal_records ORDER BY timestamp DESC")
    fun getAllPRs(): Flow<List<PersonalRecordEntity>>

    @Query("""
        SELECT * FROM personal_records
        GROUP BY exerciseId, workoutMode
        HAVING MAX(weightPerCableKg)
        ORDER BY exerciseId ASC
    """)
    fun getAllPRsGrouped(): Flow<List<PersonalRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPR(pr: PersonalRecordEntity): Long

    /**
     * Updates the personal record if the new weight is better than the existing one.
     * @return true if a new PR was set, false otherwise
     */
    @Transaction
    suspend fun updatePRIfBetter(
        exerciseId: String,
        weightPerCableKg: Float,
        reps: Int,
        workoutMode: String,
        timestamp: Long
    ): Boolean {
        val existingPR = getLatestPR(exerciseId, workoutMode)

        return if (existingPR == null || weightPerCableKg > existingPR.weightPerCableKg) {
            val newPR = PersonalRecordEntity(
                id = existingPR?.id ?: 0L,
                exerciseId = exerciseId,
                weightPerCableKg = weightPerCableKg,
                reps = reps,
                workoutMode = workoutMode,
                timestamp = timestamp
            )
            upsertPR(newPR)
            true
        } else {
            false
        }
    }
}
