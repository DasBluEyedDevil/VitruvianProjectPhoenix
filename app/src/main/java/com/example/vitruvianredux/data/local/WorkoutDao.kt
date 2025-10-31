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
    
    // ========== Routine Operations ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity)
    
    @Update
    suspend fun updateRoutine(routine: RoutineEntity)
    
    @Delete
    suspend fun deleteRoutine(routine: RoutineEntity)
    
    @Query("DELETE FROM routines WHERE id = :routineId")
    suspend fun deleteRoutineById(routineId: String)
    
    @Query("SELECT * FROM routines ORDER BY lastUsed DESC, createdAt DESC")
    fun getAllRoutines(): Flow<List<RoutineEntity>>
    
    @Query("SELECT * FROM routines WHERE id = :routineId")
    suspend fun getRoutineById(routineId: String): RoutineEntity?
    
    @Query("SELECT * FROM routines WHERE id = :routineId")
    fun observeRoutineById(routineId: String): Flow<RoutineEntity?>
    
    @Query("UPDATE routines SET lastUsed = :timestamp, useCount = useCount + 1 WHERE id = :routineId")
    suspend fun markRoutineUsed(routineId: String, timestamp: Long = System.currentTimeMillis())
    
    // ========== Routine Exercise Operations ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: RoutineExerciseEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<RoutineExerciseEntity>)
    
    @Update
    suspend fun updateExercise(exercise: RoutineExerciseEntity)
    
    @Delete
    suspend fun deleteExercise(exercise: RoutineExerciseEntity)
    
    @Query("DELETE FROM routine_exercises WHERE id = :exerciseId")
    suspend fun deleteExerciseById(exerciseId: String)
    
    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun deleteExercisesForRoutine(routineId: String)
    
    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY orderIndex ASC")
    fun getExercisesForRoutine(routineId: String): Flow<List<RoutineExerciseEntity>>
    
    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY orderIndex ASC")
    suspend fun getExercisesForRoutineSync(routineId: String): List<RoutineExerciseEntity>
    
    @Query("SELECT * FROM routine_exercises WHERE id = :exerciseId")
    suspend fun getExerciseById(exerciseId: String): RoutineExerciseEntity?
    
    // ========== Transaction Operations ==========
    
    @Transaction
    suspend fun insertRoutineWithExercises(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>
    ) {
        insertRoutine(routine)
        insertExercises(exercises)
    }
    
    @Transaction
    suspend fun updateRoutineWithExercises(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>
    ) {
        updateRoutine(routine)
        deleteExercisesForRoutine(routine.id)
        insertExercises(exercises)
    }
    
    @Transaction
    suspend fun deleteRoutineComplete(routineId: String) {
        deleteExercisesForRoutine(routineId)
        deleteRoutineById(routineId)
    }

    // ========== Weekly Programs ==========

    @Query("SELECT * FROM weekly_programs ORDER BY lastUsed DESC")
    fun getAllPrograms(): Flow<List<WeeklyProgramEntity>>

    @Transaction
    @Query("SELECT * FROM weekly_programs ORDER BY lastUsed DESC")
    fun getAllProgramsWithDays(): Flow<List<WeeklyProgramWithDays>>

    @Query("SELECT * FROM weekly_programs WHERE isActive = 1 LIMIT 1")
    fun getActiveProgram(): Flow<WeeklyProgramEntity?>

    @Transaction
    @Query("SELECT * FROM weekly_programs WHERE isActive = 1 LIMIT 1")
    fun getActiveProgramWithDays(): Flow<WeeklyProgramWithDays?>

    @Query("SELECT * FROM weekly_programs WHERE id = :programId")
    fun getProgramById(programId: String): Flow<WeeklyProgramEntity?>

    @Transaction
    @Query("SELECT * FROM weekly_programs WHERE id = :programId")
    fun getProgramWithDaysById(programId: String): Flow<WeeklyProgramWithDays?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgram(program: WeeklyProgramEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgramDays(days: List<ProgramDayEntity>)

    @Query("DELETE FROM program_days WHERE programId = :programId")
    suspend fun deleteProgramDays(programId: String)

    @Transaction
    suspend fun insertProgramWithDays(program: WeeklyProgramEntity, days: List<ProgramDayEntity>) {
        insertProgram(program)
        deleteProgramDays(program.id)
        insertProgramDays(days)
    }

    @Query("DELETE FROM weekly_programs WHERE id = :programId")
    suspend fun deleteProgram(programId: String)

    @Query("UPDATE weekly_programs SET isActive = 0")
    suspend fun setAllProgramsInactive()

    @Query("UPDATE weekly_programs SET isActive = 1, lastUsed = :timestamp WHERE id = :programId")
    suspend fun setProgramActive(programId: String, timestamp: Long = System.currentTimeMillis())

    @Transaction
    suspend fun activateProgram(programId: String) {
        setAllProgramsInactive()
        setProgramActive(programId)
    }
}
