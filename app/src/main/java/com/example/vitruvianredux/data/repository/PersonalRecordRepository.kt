package com.example.vitruvianredux.data.repository

import com.example.vitruvianredux.data.local.PersonalRecordDao
import com.example.vitruvianredux.data.local.PersonalRecordEntity
import com.example.vitruvianredux.domain.model.PersonalRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing personal records (PRs)
 */
@Singleton
class PersonalRecordRepository @Inject constructor(
    private val personalRecordDao: PersonalRecordDao
) {

    /**
     * Get the latest PR for an exercise in a specific workout mode
     */
    suspend fun getLatestPR(exerciseId: String, workoutMode: String): PersonalRecord? {
        return try {
            personalRecordDao.getLatestPR(exerciseId, workoutMode)?.toPersonalRecord()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get PR for exercise $exerciseId")
            null
        }
    }

    /**
     * Get all PRs for an exercise across all workout modes
     */
    fun getPRsForExercise(exerciseId: String): Flow<List<PersonalRecord>> {
        return personalRecordDao.getPRsForExercise(exerciseId).map { entities ->
            entities.map { it.toPersonalRecord() }
        }
    }

    /**
     * Get the best PR for an exercise across all modes
     */
    suspend fun getBestPR(exerciseId: String): PersonalRecord? {
        return try {
            personalRecordDao.getBestPR(exerciseId)?.toPersonalRecord()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get best PR for exercise $exerciseId")
            null
        }
    }

    /**
     * Get all personal records
     */
    fun getAllPRs(): Flow<List<PersonalRecord>> {
        return personalRecordDao.getAllPRs().map { entities ->
            entities.map { it.toPersonalRecord() }
        }
    }

    /**
     * Get all personal records grouped by exercise (for analytics)
     */
    fun getAllPRsGrouped(): Flow<List<PersonalRecord>> {
        return personalRecordDao.getAllPRsGrouped().map { entities ->
            entities.map { it.toPersonalRecord() }
        }
    }

    /**
     * Update PR if the new performance is better
     * Returns Result.success(true) if a new PR was set, Result.success(false) otherwise
     */
    suspend fun updatePRIfBetter(
        exerciseId: String,
        weightPerCableKg: Float,
        reps: Int,
        workoutMode: String,
        timestamp: Long
    ): Result<Boolean> {
        return try {
            val isNewPR = personalRecordDao.updatePRIfBetter(
                exerciseId = exerciseId,
                weightPerCableKg = weightPerCableKg,
                reps = reps,
                workoutMode = workoutMode,
                timestamp = timestamp
            )
            Result.success(isNewPR)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update PR for exercise $exerciseId")
            Result.failure(e)
        }
    }
}

// Extension functions for mapping between entities and domain models
private fun PersonalRecordEntity.toPersonalRecord() = PersonalRecord(
    id = id,
    exerciseId = exerciseId,
    weightPerCableKg = weightPerCableKg,
    reps = reps,
    timestamp = timestamp,
    workoutMode = workoutMode
)
