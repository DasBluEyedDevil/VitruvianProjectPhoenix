package com.example.vitruvianredux.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * Data Access Object for Personal Records
 * Supports two types of PRs: MAX_WEIGHT (heaviest lift) and MAX_VOLUME (weight × reps)
 */
@Dao
interface PersonalRecordDao {

    /**
     * Get the PR for an exercise in a specific workout mode and PR type
     */
    @Query("""
        SELECT * FROM personal_records
        WHERE exerciseId = :exerciseId AND workoutMode = :workoutMode AND prType = :prType
        LIMIT 1
    """)
    suspend fun getPR(exerciseId: String, workoutMode: String, prType: String): PersonalRecordEntity?

    /**
     * Get both PRs (max weight and max volume) for an exercise in a specific workout mode
     */
    @Query("""
        SELECT * FROM personal_records
        WHERE exerciseId = :exerciseId AND workoutMode = :workoutMode
        ORDER BY prType
    """)
    suspend fun getPRsForExerciseAndMode(exerciseId: String, workoutMode: String): List<PersonalRecordEntity>

    /**
     * Get all personal records for an exercise across all workout modes
     */
    @Query("SELECT * FROM personal_records WHERE exerciseId = :exerciseId ORDER BY timestamp DESC")
    fun getPRsForExercise(exerciseId: String): Flow<List<PersonalRecordEntity>>

    /**
     * Get the best weight PR for an exercise across all modes
     */
    @Query("""
        SELECT * FROM personal_records
        WHERE exerciseId = :exerciseId AND prType = 'MAX_WEIGHT'
        ORDER BY weightPerCableKg DESC
        LIMIT 1
    """)
    suspend fun getBestWeightPR(exerciseId: String): PersonalRecordEntity?

    /**
     * Get the best volume PR for an exercise across all modes
     */
    @Query("""
        SELECT * FROM personal_records
        WHERE exerciseId = :exerciseId AND prType = 'MAX_VOLUME'
        ORDER BY volume DESC
        LIMIT 1
    """)
    suspend fun getBestVolumePR(exerciseId: String): PersonalRecordEntity?

    /**
     * Legacy: Get the best PR for an exercise (prefers weight, for backwards compatibility)
     */
    @Query("""
        SELECT * FROM personal_records
        WHERE exerciseId = :exerciseId
        ORDER BY weightPerCableKg DESC, volume DESC
        LIMIT 1
    """)
    suspend fun getBestPR(exerciseId: String): PersonalRecordEntity?

    /**
     * Legacy: Get the latest PR for an exercise in a specific workout mode
     * Returns the MAX_WEIGHT PR for backwards compatibility
     */
    @Query("""
        SELECT * FROM personal_records
        WHERE exerciseId = :exerciseId AND workoutMode = :workoutMode AND prType = 'MAX_WEIGHT'
        LIMIT 1
    """)
    suspend fun getLatestPR(exerciseId: String, workoutMode: String): PersonalRecordEntity?

    /**
     * Get all personal records
     */
    @Query("SELECT * FROM personal_records ORDER BY timestamp DESC")
    fun getAllPRs(): Flow<List<PersonalRecordEntity>>

    /**
     * Get all personal records grouped by exercise (for analytics)
     */
    @Query("SELECT * FROM personal_records ORDER BY exerciseId, workoutMode, prType, timestamp DESC")
    fun getAllPRsGrouped(): Flow<List<PersonalRecordEntity>>

    /**
     * Get all personal records synchronously for backup
     */
    @Query("SELECT * FROM personal_records")
    suspend fun getAllPRsSync(): List<PersonalRecordEntity>

    /**
     * Get all PR IDs for duplicate checking during import
     */
    @Query("SELECT id FROM personal_records")
    suspend fun getAllPRIds(): List<Long>

    /**
     * Insert a PR, ignoring if it already exists
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPRIgnore(pr: PersonalRecordEntity): Long

    /**
     * Insert or update a personal record
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPR(pr: PersonalRecordEntity): Long

    /**
     * Update PRs if new performance is better
     * Checks BOTH max weight and max volume PRs
     * Returns a list of PR types that were broken (can be empty, one, or both)
     */
    @Transaction
    suspend fun updatePRsIfBetter(
        exerciseId: String,
        weightPerCableKg: Float,
        reps: Int,
        workoutMode: String,
        timestamp: Long
    ): List<PRType> {
        val brokenPRs = mutableListOf<PRType>()
        val newVolume = weightPerCableKg * reps

        Timber.d("PR_CHECK: Checking PRs for $exerciseId/$workoutMode")
        Timber.d("  New performance: ${weightPerCableKg}kg x $reps reps (volume: $newVolume)")

        // Check MAX_WEIGHT PR
        val existingWeightPR = getPR(exerciseId, workoutMode, PRType.MAX_WEIGHT.name)
        if (existingWeightPR == null) {
            Timber.d("  MAX_WEIGHT: No existing PR - creating first: ${weightPerCableKg}kg")
            upsertPR(
                PersonalRecordEntity(
                    exerciseId = exerciseId,
                    weightPerCableKg = weightPerCableKg,
                    reps = reps,
                    workoutMode = workoutMode,
                    timestamp = timestamp,
                    prType = PRType.MAX_WEIGHT.name,
                    volume = newVolume
                )
            )
            brokenPRs.add(PRType.MAX_WEIGHT)
        } else if (weightPerCableKg > existingWeightPR.weightPerCableKg) {
            Timber.d("  MAX_WEIGHT: NEW PR! ${existingWeightPR.weightPerCableKg}kg -> ${weightPerCableKg}kg")
            upsertPR(
                PersonalRecordEntity(
                    id = existingWeightPR.id,  // Preserve ID for update
                    exerciseId = exerciseId,
                    weightPerCableKg = weightPerCableKg,
                    reps = reps,
                    workoutMode = workoutMode,
                    timestamp = timestamp,
                    prType = PRType.MAX_WEIGHT.name,
                    volume = newVolume
                )
            )
            brokenPRs.add(PRType.MAX_WEIGHT)
        } else {
            Timber.d("  MAX_WEIGHT: Not a PR (existing: ${existingWeightPR.weightPerCableKg}kg)")
        }

        // Check MAX_VOLUME PR
        val existingVolumePR = getPR(exerciseId, workoutMode, PRType.MAX_VOLUME.name)
        if (existingVolumePR == null) {
            Timber.d("  MAX_VOLUME: No existing PR - creating first: $newVolume")
            upsertPR(
                PersonalRecordEntity(
                    exerciseId = exerciseId,
                    weightPerCableKg = weightPerCableKg,
                    reps = reps,
                    workoutMode = workoutMode,
                    timestamp = timestamp,
                    prType = PRType.MAX_VOLUME.name,
                    volume = newVolume
                )
            )
            brokenPRs.add(PRType.MAX_VOLUME)
        } else if (newVolume > existingVolumePR.volume) {
            Timber.d("  MAX_VOLUME: NEW PR! ${existingVolumePR.volume} -> $newVolume (${existingVolumePR.weightPerCableKg}kg x ${existingVolumePR.reps} -> ${weightPerCableKg}kg x $reps)")
            upsertPR(
                PersonalRecordEntity(
                    id = existingVolumePR.id,  // Preserve ID for update
                    exerciseId = exerciseId,
                    weightPerCableKg = weightPerCableKg,
                    reps = reps,
                    workoutMode = workoutMode,
                    timestamp = timestamp,
                    prType = PRType.MAX_VOLUME.name,
                    volume = newVolume
                )
            )
            brokenPRs.add(PRType.MAX_VOLUME)
        } else {
            Timber.d("  MAX_VOLUME: Not a PR (existing: ${existingVolumePR.volume})")
        }

        if (brokenPRs.isNotEmpty()) {
            Timber.d("PR_CHECK: Broken PRs: $brokenPRs")
        }

        return brokenPRs
    }

    /**
     * Legacy: Update PR if the new performance is better (single PR tracking)
     * Kept for backwards compatibility - now checks weight only
     */
    @Transaction
    suspend fun updatePRIfBetter(
        exerciseId: String,
        weightPerCableKg: Float,
        reps: Int,
        workoutMode: String,
        timestamp: Long
    ): Boolean {
        val brokenPRs = updatePRsIfBetter(exerciseId, weightPerCableKg, reps, workoutMode, timestamp)
        return brokenPRs.isNotEmpty()
    }
}
