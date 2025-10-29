package com.example.vitruvianredux.data.repository

import com.example.vitruvianredux.data.local.ExerciseDao
import com.example.vitruvianredux.data.local.ExerciseEntity
import com.example.vitruvianredux.data.local.ExerciseImporter
import com.example.vitruvianredux.data.local.ExerciseVideoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for exercise library management
 */
interface ExerciseRepository {
    fun getAllExercises(): Flow<List<ExerciseEntity>>
    fun searchExercises(query: String): Flow<List<ExerciseEntity>>
    fun filterByMuscleGroup(muscleGroup: String): Flow<List<ExerciseEntity>>
    fun filterByEquipment(equipment: String): Flow<List<ExerciseEntity>>
    fun getFavorites(): Flow<List<ExerciseEntity>>
    suspend fun toggleFavorite(id: String)
    suspend fun getExerciseById(id: String): ExerciseEntity?
    suspend fun getVideos(exerciseId: String): List<ExerciseVideoEntity>
    suspend fun importExercises(): Result<Unit>
    suspend fun isExerciseLibraryEmpty(): Boolean
}

/**
 * Implementation of ExerciseRepository
 */
@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val exerciseImporter: ExerciseImporter
) : ExerciseRepository {
    
    /**
     * Get all exercises sorted by name
     */
    override fun getAllExercises(): Flow<List<ExerciseEntity>> {
        return exerciseDao.getAllExercises()
    }
    
    /**
     * Search exercises by name, description, or muscles
     */
    override fun searchExercises(query: String): Flow<List<ExerciseEntity>> {
        return if (query.isBlank()) {
            getAllExercises()
        } else {
            exerciseDao.searchExercises(query.trim())
        }
    }
    
    /**
     * Filter exercises by muscle group
     */
    override fun filterByMuscleGroup(muscleGroup: String): Flow<List<ExerciseEntity>> {
        return if (muscleGroup.isBlank()) {
            getAllExercises()
        } else {
            exerciseDao.getExercisesByMuscleGroup(muscleGroup)
        }
    }
    
    /**
     * Filter exercises by equipment
     */
    override fun filterByEquipment(equipment: String): Flow<List<ExerciseEntity>> {
        return if (equipment.isBlank()) {
            getAllExercises()
        } else {
            exerciseDao.getExercisesByEquipment(equipment)
        }
    }
    
    /**
     * Get favorite exercises
     */
    override fun getFavorites(): Flow<List<ExerciseEntity>> {
        return exerciseDao.getFavorites()
    }
    
    /**
     * Toggle favorite status for an exercise
     */
    override suspend fun toggleFavorite(id: String) {
        try {
            val exercise = exerciseDao.getExerciseById(id)
            exercise?.let {
                exerciseDao.updateFavorite(id, !it.isFavorite)
                Timber.d("Toggled favorite for exercise: $id")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle favorite")
        }
    }
    
    /**
     * Get exercise by ID
     */
    override suspend fun getExerciseById(id: String): ExerciseEntity? {
        return exerciseDao.getExerciseById(id)
    }
    
    /**
     * Get videos for an exercise
     */
    override suspend fun getVideos(exerciseId: String): List<ExerciseVideoEntity> {
        return exerciseDao.getVideos(exerciseId)
    }
    
    /**
     * Import exercises from assets (if not already imported)
     */
    override suspend fun importExercises(): Result<Unit> {
        return try {
            // Check if exercises are already imported
            val count = getAllExercises().firstOrNull()?.size ?: 0
            if (count == 0) {
                Timber.d("Importing exercises from assets...")
                val result = exerciseImporter.importExercises()
                if (result.isSuccess) {
                    Result.success(Unit)
                } else {
                    result.exceptionOrNull()?.let { Result.failure(it) } ?: Result.failure(Exception("Import failed"))
                }
            } else {
                Timber.d("Exercises already imported (count: $count)")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to import exercises")
            Result.failure(e)
        }
    }
    
    /**
     * Check if exercise library is empty
     */
    override suspend fun isExerciseLibraryEmpty(): Boolean {
        return getAllExercises().firstOrNull()?.isEmpty() ?: true
    }
}