package com.example.vitruvianredux.data.repository

import com.example.vitruvianredux.data.local.ExerciseDao
import com.example.vitruvianredux.data.local.entity.ExerciseEntity
import com.example.vitruvianredux.data.local.ExerciseImporter
import com.example.vitruvianredux.data.local.entity.ExerciseVideoEntity
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
    suspend fun updateFromGitHub(): Result<Int>
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

    /**
     * Update exercise library from GitHub
     * Fetches the latest exercise_dump.json from the repository and updates the database
     * @return Result with count of exercises updated, or error
     */
    override suspend fun updateFromGitHub(): Result<Int> {
        return try {
            Timber.d("Updating exercise library from GitHub...")

            // Fetch JSON from GitHub (raw content URL)
            val url = java.net.URL("https://raw.githubusercontent.com/exerciselibrary/exerciselibrary.github.io/main/exercise_dump.json")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000 // 30 seconds
            connection.readTimeout = 30000

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                return Result.failure(Exception("HTTP $responseCode: Failed to fetch exercise library"))
            }

            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            // Import from fetched JSON
            val result = exerciseImporter.importFromJsonString(jsonString, clearExisting = true)

            if (result.isSuccess) {
                Timber.d("Exercise library updated successfully from GitHub")
                result
            } else {
                Timber.e("Failed to import exercises from GitHub JSON")
                result
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to update exercise library from GitHub")
            Result.failure(e)
        }
    }
}