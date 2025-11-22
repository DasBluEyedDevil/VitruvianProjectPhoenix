package com.example.vitruvianredux.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Personal Records
 */
@Dao
interface PersonalRecordDao {

    /**
     * Get the latest personal record for an exercise in a specific workout mode
     */
    @Query("""
        SELECT * FROM personal_records
        WHERE exerciseId = :exerciseId AND workoutMode = :workoutMode
        LIMIT 1
    """)
    suspend fun getLatestPR(exerciseId: String, workoutMode: String): PersonalRecordEntity?

    /**
     * Get all personal records for an exercise across all workout modes
     */
    @Query("SELECT * FROM personal_records WHERE exerciseId = :exerciseId ORDER BY timestamp DESC")
    fun getPRsForExercise(exerciseId: String): Flow<List<PersonalRecordEntity>>

    /**
     * Get the best PR for an exercise across all modes (highest weight, then highest reps)
     */
    @Query("""
        SELECT * FROM personal_records
        WHERE exerciseId = :exerciseId
        ORDER BY weightPerCableKg DESC, reps DESC
        LIMIT 1
    """)
    suspend fun getBestPR(exerciseId: String): PersonalRecordEntity?

    /**
     * Get all personal records
     */
    @Query("SELECT * FROM personal_records ORDER BY timestamp DESC")
    fun getAllPRs(): Flow<List<PersonalRecordEntity>>

    /**
     * Get all personal records grouped by exercise (for analytics)
     */
    @Query("SELECT * FROM personal_records ORDER BY exerciseId, workoutMode, timestamp DESC")
    fun getAllPRsGrouped(): Flow<List<PersonalRecordEntity>>

    /**
     * Insert or update a personal record
     * Uses REPLACE strategy to update existing PR for the exercise+mode combination
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPR(pr: PersonalRecordEntity): Long

    /**
     * Update PR only if new performance is better
     * Returns true if a new PR was set
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

        // If no existing PR, this is automatically a new PR
        if (existingPR == null) {
            upsertPR(
                PersonalRecordEntity(
                    exerciseId = exerciseId,
                    weightPerCableKg = weightPerCableKg,
                    reps = reps,
                    workoutMode = workoutMode,
                    timestamp = timestamp
                )
            )
            return true
        }

        // Compare performance: new PR if weight is higher OR (weight is same AND reps are higher)
        val isBetter = weightPerCableKg > existingPR.weightPerCableKg ||
                (weightPerCableKg == existingPR.weightPerCableKg && reps > existingPR.reps)

        if (isBetter) {
            upsertPR(
                PersonalRecordEntity(
                    exerciseId = exerciseId,
                    weightPerCableKg = weightPerCableKg,
                    reps = reps,
                    workoutMode = workoutMode,
                    timestamp = timestamp
                )
            )
            return true
        }

        return false
    }
}
