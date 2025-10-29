package com.example.vitruvianredux.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for exercise library
 */
@Dao
interface ExerciseDao {
    
    // ========== Exercise Queries ==========
    
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAllExercises(): Flow<List<ExerciseEntity>>
    
    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExerciseById(id: String): ExerciseEntity?
    
    @Query("SELECT * FROM exercises WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavorites(): Flow<List<ExerciseEntity>>
    
    @Query("""
        SELECT * FROM exercises 
        WHERE name LIKE '%' || :query || '%' 
           OR description LIKE '%' || :query || '%'
           OR muscles LIKE '%' || :query || '%'
        ORDER BY popularity DESC, name ASC
    """)
    fun searchExercises(query: String): Flow<List<ExerciseEntity>>
    
    @Query("""
        SELECT * FROM exercises 
        WHERE muscleGroups LIKE '%' || :muscleGroup || '%'
        ORDER BY popularity DESC, name ASC
    """)
    fun getExercisesByMuscleGroup(muscleGroup: String): Flow<List<ExerciseEntity>>
    
    @Query("""
        SELECT * FROM exercises 
        WHERE equipment LIKE '%' || :equipment || '%'
        ORDER BY popularity DESC, name ASC
    """)
    fun getExercisesByEquipment(equipment: String): Flow<List<ExerciseEntity>>
    
    @Query("UPDATE exercises SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)
    
    @Query("UPDATE exercises SET timesPerformed = timesPerformed + 1, lastPerformed = :timestamp WHERE id = :id")
    suspend fun incrementPerformed(id: String, timestamp: Long = System.currentTimeMillis())
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: ExerciseEntity)
    
    @Query("DELETE FROM exercises")
    suspend fun deleteAll()
    
    // ========== Video Queries ==========
    
    @Query("SELECT * FROM exercise_videos WHERE exerciseId = :exerciseId ORDER BY angle ASC")
    suspend fun getVideos(exerciseId: String): List<ExerciseVideoEntity>
    
    @Query("SELECT * FROM exercise_videos WHERE exerciseId = :exerciseId ORDER BY angle ASC")
    fun getVideosFlow(exerciseId: String): Flow<List<ExerciseVideoEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<ExerciseVideoEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: ExerciseVideoEntity)
    
    @Query("DELETE FROM exercise_videos")
    suspend fun deleteAllVideos()
    
    // ========== Transaction Operations ==========
    
    @Transaction
    suspend fun insertExerciseWithVideos(
        exercise: ExerciseEntity,
        videos: List<ExerciseVideoEntity>
    ) {
        insert(exercise)
        insertVideos(videos)
    }
}
