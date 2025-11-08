package com.example.vitruvianredux.data.repository

import com.example.vitruvianredux.data.local.WorkoutDao
import com.example.vitruvianredux.data.local.WorkoutMetricEntity
import com.example.vitruvianredux.data.local.WorkoutSessionEntity
import com.example.vitruvianredux.data.local.RoutineEntity
import com.example.vitruvianredux.data.local.RoutineExerciseEntity
import com.example.vitruvianredux.data.local.WeeklyProgramWithDays
import com.example.vitruvianredux.data.local.PersonalRecordDao
import com.example.vitruvianredux.data.local.PersonalRecordEntity
import com.example.vitruvianredux.domain.model.WorkoutMetric
import com.example.vitruvianredux.domain.model.WorkoutSession
import com.example.vitruvianredux.domain.model.Routine
import com.example.vitruvianredux.domain.model.RoutineExercise
import com.example.vitruvianredux.domain.model.Exercise
import com.example.vitruvianredux.domain.model.CableConfiguration
import com.example.vitruvianredux.domain.model.WorkoutMode
import com.example.vitruvianredux.domain.model.WorkoutType
import com.example.vitruvianredux.domain.model.ProgramMode
import com.example.vitruvianredux.domain.model.EchoLevel
import com.example.vitruvianredux.domain.model.EccentricLoad
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for workout history management
 */
@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val personalRecordDao: PersonalRecordDao
) {
    
    /**
     * Save a workout session
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
                exerciseId = session.exerciseId
            )
            workoutDao.insertSession(entity)
            Timber.d("Saved workout session: ${session.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save workout session")
            Result.failure(e)
        }
    }
    
    /**
     * Save workout metrics (batch insert for performance)
     */
    suspend fun saveMetrics(sessionId: String, metrics: List<WorkoutMetric>): Result<Unit> {
        return try {
            val entities = metrics.map { metric ->
                WorkoutMetricEntity(
                    sessionId = sessionId,
                    timestamp = metric.timestamp,
                    loadA = metric.loadA,
                    loadB = metric.loadB,
                    positionA = metric.positionA,
                    positionB = metric.positionB,
                    ticks = metric.ticks
                )
            }
            workoutDao.insertMetrics(entities)
            Timber.d("Saved ${entities.size} metrics for session $sessionId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save workout metrics")
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

// Extension functions for mapping between entities and domain models
private fun WorkoutSessionEntity.toWorkoutSession() = WorkoutSession(
    id = id,
    timestamp = timestamp,
    mode = mode,
    reps = reps,
    weightPerCableKg = weightPerCableKg,
    progressionKg = progressionKg,
    duration = duration,
    totalReps = totalReps,
    warmupReps = warmupReps,
    workingReps = workingReps,
    isJustLift = isJustLift,
    stopAtTop = stopAtTop,
    eccentricLoad = eccentricLoad,
    echoLevel = echoLevel,
    exerciseId = exerciseId
)

private fun WorkoutMetricEntity.toWorkoutMetric() = WorkoutMetric(
    timestamp = timestamp,
    loadA = loadA,
    loadB = loadB,
    positionA = positionA,
    positionB = positionB,
    ticks = ticks
)

// Routine mapping extensions
private fun Routine.toEntity() = RoutineEntity(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    lastUsed = lastUsed,
    useCount = useCount
)

private fun RoutineExercise.toEntity(routineId: String) = RoutineExerciseEntity(
    id = id,
    routineId = routineId,
    // Store Exercise data class fields
    exerciseName = exercise.name,
    exerciseMuscleGroup = exercise.muscleGroup,
    exerciseEquipment = exercise.equipment,
    exerciseDefaultCableConfig = exercise.defaultCableConfig.name, // Convert enum to String
    exerciseId = exercise.id, // Store exercise library ID
    // Routine-specific configuration
    cableConfig = cableConfig.name, // Convert enum to String
    orderIndex = orderIndex,
    setReps = setReps.joinToString(","), // Convert List<Int> to comma-separated String
    weightPerCableKg = weightPerCableKg,
    setWeights = setWeightsPerCableKg.joinToString(",") { it.toString() },
    mode = when (workoutType) {
        is WorkoutType.Program -> when (workoutType.mode) {
            is ProgramMode.OldSchool -> "OldSchool"
            is ProgramMode.Pump -> "Pump"
            is ProgramMode.TUT -> "TUT"
            is ProgramMode.TUTBeast -> "TUTBeast"
            is ProgramMode.EccentricOnly -> "EccentricOnly"
        }
        is WorkoutType.Echo -> "Echo"
    },
    eccentricLoad = when (workoutType) {
        is WorkoutType.Echo -> workoutType.eccentricLoad.percentage
        else -> eccentricLoad.percentage
    },
    echoLevel = when (workoutType) {
        is WorkoutType.Echo -> workoutType.level.levelValue
        else -> echoLevel.levelValue
    },
    progressionKg = progressionKg,
    restSeconds = restSeconds,
    notes = notes,
    duration = duration
)

private fun RoutineEntity.toRoutine(exerciseEntities: List<RoutineExerciseEntity>) = Routine(
    id = id,
    name = name,
    description = description,
    exercises = exerciseEntities.map { it.toRoutineExercise() },
    createdAt = createdAt,
    lastUsed = lastUsed,
    useCount = useCount
)

private fun RoutineExerciseEntity.toRoutineExercise() = RoutineExercise(
    id = id,
    // Reconstruct Exercise data class from stored fields
    exercise = Exercise(
        name = exerciseName,
        muscleGroup = exerciseMuscleGroup,
        equipment = exerciseEquipment,
        defaultCableConfig = CableConfiguration.valueOf(exerciseDefaultCableConfig),
        id = exerciseId  // Pass through exercise library ID
    ),
    cableConfig = CableConfiguration.valueOf(cableConfig), // Convert String to enum
    orderIndex = orderIndex,
    setReps = if (setReps.isEmpty()) emptyList() else setReps.split(",").mapNotNull { it.toIntOrNull() },
    weightPerCableKg = weightPerCableKg,
    setWeightsPerCableKg = if (setWeights.isEmpty()) emptyList() else setWeights.split(",").mapNotNull { it.toFloatOrNull() },
    workoutType = when (mode) {
        "Echo" -> WorkoutType.Echo(
            level = EchoLevel.values().getOrNull(echoLevel) ?: EchoLevel.HARDER,
            eccentricLoad = EccentricLoad.values().find { it.percentage == eccentricLoad } ?: EccentricLoad.LOAD_100
        )
        "OldSchool" -> WorkoutType.Program(ProgramMode.OldSchool)
        "Pump" -> WorkoutType.Program(ProgramMode.Pump)
        "TUT" -> WorkoutType.Program(ProgramMode.TUT)
        "TUTBeast" -> WorkoutType.Program(ProgramMode.TUTBeast)
        "EccentricOnly" -> WorkoutType.Program(ProgramMode.EccentricOnly)
        else -> WorkoutType.Program(ProgramMode.OldSchool)
    },
    eccentricLoad = EccentricLoad.values().find { it.percentage == eccentricLoad } ?: EccentricLoad.LOAD_100,
    echoLevel = EchoLevel.values().getOrNull(echoLevel) ?: EchoLevel.HARDER,
    progressionKg = progressionKg,
    restSeconds = restSeconds,
    notes = notes,
    duration = duration
)
