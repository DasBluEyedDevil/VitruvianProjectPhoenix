package com.ninthlevel.phoenix.data.repository

import com.ninthlevel.phoenix.data.local.ExerciseDao
import com.ninthlevel.phoenix.data.local.ExerciseEntity
import com.ninthlevel.phoenix.data.local.WorkoutDao
import com.ninthlevel.phoenix.data.local.seed.DefaultExercises
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface ExerciseRepository {
    fun getAllExercises(): Flow<List<ExerciseEntity>>
    fun searchExercises(query: String): Flow<List<ExerciseEntity>>
    fun filterByMuscleGroup(muscleGroup: String): Flow<List<ExerciseEntity>>
    fun getFavorites(): Flow<List<ExerciseEntity>>
    suspend fun toggleFavorite(id: String)
    suspend fun getExerciseById(id: String): ExerciseEntity?
    suspend fun resolveExerciseName(id: String): String?
    suspend fun seedDefaultExercises()
}

@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao
) : ExerciseRepository {

    override fun getAllExercises(): Flow<List<ExerciseEntity>> {
        return exerciseDao.getAllExercises()
    }

    override fun searchExercises(query: String): Flow<List<ExerciseEntity>> {
        return if (query.isBlank()) {
            getAllExercises()
        } else {
            exerciseDao.searchExercises(query.trim())
        }
    }

    override fun filterByMuscleGroup(muscleGroup: String): Flow<List<ExerciseEntity>> {
        return if (muscleGroup.isBlank()) {
            getAllExercises()
        } else {
            exerciseDao.getExercisesByMuscleGroup(muscleGroup)
        }
    }

    override fun getFavorites(): Flow<List<ExerciseEntity>> {
        return exerciseDao.getFavorites()
    }

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

    override suspend fun getExerciseById(id: String): ExerciseEntity? {
        return exerciseDao.getExerciseById(id)
    }

    override suspend fun resolveExerciseName(id: String): String? {
        return exerciseDao.getExerciseById(id)?.name
            ?: workoutDao.getExerciseNameForExerciseId(id)
    }

    override suspend fun seedDefaultExercises() {
        try {
            exerciseDao.seed(DefaultExercises.all)
            Timber.d("Seeded ${DefaultExercises.all.size} default exercises")
        } catch (e: Exception) {
            Timber.e(e, "Failed to seed default exercises")
        }
    }
}
