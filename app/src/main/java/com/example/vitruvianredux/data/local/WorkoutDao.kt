package com.example.vitruvianredux.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    // --- Sessions ---
    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity)

    @Insert
    suspend fun insertMetrics(metrics: List<WorkoutMetricEntity>)

    @Query("SELECT * FROM workout_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSessionsSync(limit: Int): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_metrics WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMetricsForSession(sessionId: String): Flow<List<WorkoutMetricEntity>>

    @Query("SELECT * FROM workout_metrics WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMetricsForSessionSync(sessionId: String): List<WorkoutMetricEntity>

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId")
    suspend fun deleteWorkout(sessionId: String)

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllWorkouts()

    @Query("DELETE FROM workout_metrics WHERE sessionId = :sessionId")
    suspend fun deleteMetrics(sessionId: String)

    // --- Routines ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineExercises(exercises: List<RoutineExerciseEntity>)

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Transaction
    suspend fun insertRoutineWithExercises(routine: RoutineEntity, exercises: List<RoutineExerciseEntity>) {
        insertRoutine(routine)
        // Clear old exercises for this routine before inserting new ones
        deleteRoutineExercises(routine.id)
        insertRoutineExercises(exercises)
    }

    @Transaction
    suspend fun updateRoutineWithExercises(routine: RoutineEntity, exercises: List<RoutineExerciseEntity>) {
        updateRoutine(routine)
        // Clear old exercises for this routine before inserting new ones
        deleteRoutineExercises(routine.id)
        insertRoutineExercises(exercises)
    }

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun deleteRoutineExercises(routineId: String)

    @Query("SELECT * FROM routines ORDER BY lastUsed DESC, createdAt DESC")
    fun getAllRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :routineId")
    fun observeRoutineById(routineId: String): Flow<RoutineEntity?>

    @Query("SELECT * FROM routines WHERE id = :routineId")
    suspend fun getRoutineById(routineId: String): RoutineEntity?

    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY orderIndex ASC")
    suspend fun getExercisesForRoutineSync(routineId: String): List<RoutineExerciseEntity>

    @Query("DELETE FROM routines WHERE id = :routineId")
    suspend fun deleteRoutine(routineId: String)

    @Transaction
    suspend fun deleteRoutineComplete(routineId: String) {
        deleteRoutineExercises(routineId)
        deleteRoutine(routineId)
    }

    @Query("UPDATE routines SET lastUsed = :timestamp, useCount = useCount + 1 WHERE id = :routineId")
    suspend fun updateRoutineUsage(routineId: String, timestamp: Long)

    @Transaction
    suspend fun markRoutineUsed(routineId: String) {
        updateRoutineUsage(routineId, System.currentTimeMillis())
    }

    // --- Weekly Programs ---
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

    @Transaction
    @Query("SELECT * FROM weekly_programs ORDER BY createdAt DESC")
    fun getAllProgramsWithDays(): Flow<List<WeeklyProgramWithDays>>

    @Transaction
    @Query("SELECT * FROM weekly_programs WHERE isActive = 1 LIMIT 1")
    fun getActiveProgramWithDays(): Flow<WeeklyProgramWithDays?>

    @Transaction
    @Query("SELECT * FROM weekly_programs WHERE id = :programId")
    fun getProgramWithDaysById(programId: String): Flow<WeeklyProgramWithDays?>

    @Query("UPDATE weekly_programs SET isActive = CASE WHEN id = :programId THEN 1 ELSE 0 END")
    suspend fun activateProgram(programId: String)

    @Query("DELETE FROM weekly_programs WHERE id = :programId")
    suspend fun deleteProgram(programId: String)
}
