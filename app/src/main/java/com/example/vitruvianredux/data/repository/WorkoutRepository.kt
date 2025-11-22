package com.example.vitruvianredux.data.repository

import com.example.vitruvianredux.data.local.dao.DiagnosticsDao
import com.example.vitruvianredux.data.local.dao.PhaseStatisticsDao
import com.example.vitruvianredux.data.local.entity.PhaseStatisticsEntity
import com.example.vitruvianredux.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing workout data
 */
@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val personalRecordDao: PersonalRecordDao,
    private val phaseStatisticsDao: PhaseStatisticsDao,
    private val diagnosticsDao: DiagnosticsDao
) {
    
    /**
     * Save a completed workout session
     */
    suspend fun saveSession(session: WorkoutSession): Result<Unit> {
        return try {
            val entity = WorkoutSessionEntity(
                id = session.id,
                timestamp = session.timestamp,
                mode = session.mode,
                reps = session.reps,
                weightPerCableKg = session.weightPerCableKg,
                progressionKg = session.progressionKg,
                duration = session.duration,
                totalReps = session.totalReps,
                warmupReps = session.warmupReps,
                workingReps = session.workingReps,
                isJustLift = session.isJustLift,
                stopAtTop = session.stopAtTop,
                eccentricLoad = session.eccentricLoad,
                echoLevel = session.echoLevel,
                exerciseId = session.exerciseId,
                exerciseName = session.exerciseName,
                routineSessionId = session.routineSessionId,
                routineName = session.routineName
            )
            workoutDao.insertSession(entity)
            Timber.d("Saved workout session: ${session.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save workout session")
            Result.failure(e)
        }
    }
    
    suspend fun saveMetrics(sessionId: String, metrics: List<WorkoutMetric>): Result<Unit> {
        return try {
            workoutDao.insertMetrics(metrics.map { it.toEntity(sessionId) })
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save workout metrics")
            Result.failure(e)
        }
    }

    /**
     * Save phase statistics
     */
    suspend fun savePhaseStatistics(sessionId: String, stats: HeuristicStatistics): Result<Unit> {
        return try {
            val entity = PhaseStatisticsEntity(
                sessionId = sessionId,
                concentricKgAvg = stats.concentric.kgAvg,
                concentricKgMax = stats.concentric.kgMax,
                concentricVelAvg = stats.concentric.velAvg,
                concentricVelMax = stats.concentric.velMax,
                concentricWattAvg = stats.concentric.wattAvg,
                concentricWattMax = stats.concentric.wattMax,
                eccentricKgAvg = stats.eccentric.kgAvg,
                eccentricKgMax = stats.eccentric.kgMax,
                eccentricVelAvg = stats.eccentric.velAvg,
                eccentricVelMax = stats.eccentric.velMax,
                eccentricWattAvg = stats.eccentric.wattAvg,
                eccentricWattMax = stats.eccentric.wattMax,
                timestamp = stats.timestamp
            )
            phaseStatisticsDao.insert(entity)
            Timber.d("Saved phase statistics for session $sessionId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save phase statistics")
            Result.failure(e)
        }
    }
    
    /**
     * Get all workout sessions
     */
    fun getAllSessions(): Flow<List<WorkoutSession>> {
        return workoutDao.getAllSessions().map { entities ->
            entities.map { it.toWorkoutSession() }
        }
    }
    
    /**
     * Get recent workout sessions
     */
    fun getRecentSessions(limit: Int = 10): Flow<List<WorkoutSession>> {
        return workoutDao.getRecentSessions(limit).map { entities ->
            entities.map { it.toWorkoutSession() }
        }
    }
    
    /**
     * Get a specific workout session
     */
    suspend fun getSession(sessionId: String): WorkoutSession? {
        return workoutDao.getSession(sessionId)?.toWorkoutSession()
    }
    
    /**
     * Get metrics for a workout session
     */
    fun getMetricsForSession(sessionId: String): Flow<List<WorkoutMetric>> {
        return workoutDao.getMetricsForSession(sessionId).map { entities ->
            entities.map { it.toWorkoutMetric() }
        }
    }
    
    /**
     * Get metrics for a workout session synchronously (for export)
     */
    suspend fun getMetricsForSessionSync(sessionId: String): List<WorkoutMetric> {
        return workoutDao.getMetricsForSessionSync(sessionId).map { it.toWorkoutMetric() }
    }
    
    /**
     * Get recent workout sessions synchronously (for export)
     */
    suspend fun getRecentSessionsSync(limit: Int = 10): List<WorkoutSession> {
        return workoutDao.getRecentSessionsSync(limit).map { it.toWorkoutSession() }
    }

    /**
     * Get all phase statistics
     */
    fun getAllPhaseStatistics(): Flow<List<PhaseStatisticsEntity>> {
        return phaseStatisticsDao.getAll()
    }
    
    /**
     * Delete a workout
     */
    suspend fun deleteWorkout(sessionId: String): Result<Unit> {
        return try {
            workoutDao.deleteWorkout(sessionId)
            Timber.d("Deleted workout: $sessionId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete workout")
            Result.failure(e)
        }
    }
    
    /**
     * Delete all workouts
     */
    suspend fun deleteAllWorkouts(): Result<Unit> {
        return try {
            workoutDao.deleteAllWorkouts()
            Timber.d("Deleted all workouts")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete all workouts")
            Result.failure(e)
        }
    }
    
    // ========== Routine Operations ==========
    
    /**
     * Save a routine with exercises
     */
    suspend fun saveRoutine(routine: Routine): Result<Unit> {
        return try {
            val entity = routine.toEntity()
            val exerciseEntities = routine.exercises.map { it.toEntity(routine.id) }
            workoutDao.insertRoutineWithExercises(entity, exerciseEntities)
            Timber.d("Saved routine: ${routine.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save routine")
            Result.failure(e)
        }
    }
    
    /**
     * Update a routine
     */
    suspend fun updateRoutine(routine: Routine): Result<Unit> {
        return try {
            val entity = routine.toEntity()
            val exerciseEntities = routine.exercises.map { it.toEntity(routine.id) }
            workoutDao.updateRoutineWithExercises(entity, exerciseEntities)
            Timber.d("Updated routine: ${routine.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update routine")
            Result.failure(e)
        }
    }
    
    /**
     * Get all routines
     */
    fun getAllRoutines(): Flow<List<Routine>> {
        return workoutDao.getAllRoutines().map { entities ->
            entities.map { entity ->
                val exercises = workoutDao.getExercisesForRoutineSync(entity.id)
                entity.toRoutine(exercises)
            }
        }
    }
    
    /**
     * Get a specific routine
     */
    suspend fun getRoutine(routineId: String): Routine? {
        return try {
            val entity = workoutDao.getRoutineById(routineId) ?: return null
            val exercises = workoutDao.getExercisesForRoutineSync(routineId)
            entity.toRoutine(exercises)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get routine")
            null
        }
    }
    
    /**
     * Delete a routine
     */
    suspend fun deleteRoutine(routineId: String): Result<Unit> {
        return try {
            workoutDao.deleteRoutineComplete(routineId)
            Timber.d("Deleted routine: $routineId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete routine")
            Result.failure(e)
        }
    }
    
    /**
     * Mark routine as used (updates lastUsed and increments useCount)
     */
    suspend fun markRoutineUsed(routineId: String): Result<Unit> {
        return try {
            workoutDao.markRoutineUsed(routineId)
            Timber.d("Marked routine used: $routineId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark routine used")
            Result.failure(e)
        }
    }

    /**
     * Get routine by ID as a Flow
     */
    fun getRoutineById(routineId: String): Flow<Routine?> {
        return workoutDao.observeRoutineById(routineId).map { entity ->
            entity?.let {
                val exercises = workoutDao.getExercisesForRoutineSync(routineId)
                it.toRoutine(exercises)
            }
        }
    }

    // ========== Weekly Programs ==========

    /**
     * Get all weekly programs with their assigned days
     */
    fun getAllPrograms(): Flow<List<WeeklyProgramWithDays>> =
        workoutDao.getAllProgramsWithDays()

    /**
     * Get the currently active program with its days
     */
    fun getActiveProgram(): Flow<WeeklyProgramWithDays?> =
        workoutDao.getActiveProgramWithDays()

    /**
     * Get a specific program by ID with its days
     */
    fun getProgramById(programId: String): Flow<WeeklyProgramWithDays?> =
        workoutDao.getProgramWithDaysById(programId)

    /**
     * Save a new weekly program or update existing one
     */
    suspend fun saveProgram(programWithDays: WeeklyProgramWithDays): Result<Unit> {
        return try {
            workoutDao.insertProgramWithDays(
                program = programWithDays.program,
                days = programWithDays.days
            )
            Timber.d("Saved weekly program: ${programWithDays.program.title}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save weekly program")
            Result.failure(e)
        }
    }

    /**
     * Delete a weekly program
     */
    suspend fun deleteProgram(programId: String): Result<Unit> {
        return try {
            workoutDao.deleteProgram(programId)
            Timber.d("Deleted weekly program: $programId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete weekly program")
            Result.failure(e)
        }
    }

    /**
     * Activate a weekly program (deactivates all others)
     */
    suspend fun activateProgram(programId: String): Result<Unit> {
        return try {
            workoutDao.activateProgram(programId)
            Timber.d("Activated weekly program: $programId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to activate weekly program")
            Result.failure(e)
        }
    }

    // ========== Personal Records ==========

    /**
     * Get all personal records
     */
    fun getAllPersonalRecords(): Flow<List<PersonalRecordEntity>> =
        personalRecordDao.getAllPRs()

    /**
     * Update personal record if the new performance is better
     */
    suspend fun updatePersonalRecordIfNeeded(
        exerciseId: String,
        weightPerCableKg: Float,
        reps: Int,
        workoutMode: String
    ): Boolean {
        return try {
            val isNewPR = personalRecordDao.updatePRIfBetter(
                exerciseId = exerciseId,
                weightPerCableKg = weightPerCableKg,
                reps = reps,
                workoutMode = workoutMode,
                timestamp = System.currentTimeMillis()
            )
            if (isNewPR) {
                Timber.d("New PR set for exercise $exerciseId: ${weightPerCableKg}kg x $reps reps ($workoutMode)")
            }
            isNewPR
        } catch (e: Exception) {
            Timber.e(e, "Failed to update personal record")
            false
        }
    }
}

