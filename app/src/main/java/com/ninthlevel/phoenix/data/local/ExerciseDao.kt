package com.ninthlevel.phoenix.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExerciseById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavorites(): Flow<List<ExerciseEntity>>

    @Query(
        """
        SELECT * FROM exercises
        WHERE name LIKE '%' || :query || '%'
           OR aliases LIKE '%' || :query || '%'
           OR muscleGroups LIKE '%' || :query || '%'
        ORDER BY name ASC
        """
    )
    fun searchExercises(query: String): Flow<List<ExerciseEntity>>

    @Query(
        """
        SELECT * FROM exercises
        WHERE muscleGroups LIKE '%' || :muscleGroup || '%'
        ORDER BY name ASC
        """
    )
    fun getExercisesByMuscleGroup(muscleGroup: String): Flow<List<ExerciseEntity>>

    @Query("UPDATE exercises SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE exercises SET timesPerformed = timesPerformed + 1, lastPerformed = :timestamp WHERE id = :id")
    suspend fun incrementPerformed(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(exercises: List<ExerciseEntity>)

    @Query(
        """
        UPDATE exercises SET
            name = :name,
            muscleGroups = :muscleGroups,
            equipment = :equipment,
            defaultCableConfig = :defaultCableConfig,
            aliases = :aliases
        WHERE id = :id
        """
    )
    suspend fun updateSeedMetadata(
        id: String,
        name: String,
        muscleGroups: String,
        equipment: String,
        defaultCableConfig: String,
        aliases: String
    )

    @Transaction
    suspend fun seed(list: List<ExerciseEntity>) {
        insertAllIgnore(list)
        list.forEach { exercise ->
            updateSeedMetadata(
                id = exercise.id,
                name = exercise.name,
                muscleGroups = exercise.muscleGroups,
                equipment = exercise.equipment,
                defaultCableConfig = exercise.defaultCableConfig,
                aliases = exercise.aliases
            )
        }
    }
}
