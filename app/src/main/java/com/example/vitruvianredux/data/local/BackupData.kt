package com.example.vitruvianredux.data.local

import kotlinx.serialization.Serializable

/**
 * Serializable backup data classes for export/import functionality.
 * These mirror the Room entity structure but use kotlinx.serialization for JSON conversion.
 *
 * Design rationale:
 * - Separate from Room entities to avoid Room annotations in serialization
 * - Uses primitive types (String, Int, Long, Float, Boolean) for JSON compatibility
 * - Includes extension functions for bidirectional Entity ↔ Backup conversion
 */

/**
 * Backup representation of WorkoutSessionEntity
 */
@Serializable
data class WorkoutSessionBackup(
    val id: String,
    val timestamp: Long,
    val mode: String,
    val reps: Int,
    val weightPerCableKg: Float,
    val progressionKg: Float,
    val duration: Long,
    val totalReps: Int,
    val warmupReps: Int,
    val workingReps: Int,
    val isJustLift: Boolean,
    val stopAtTop: Boolean,
    val eccentricLoad: Int = 100,
    val echoLevel: Int = 1,
    val exerciseId: String? = null,
    val exerciseName: String? = null,
    val routineSessionId: String? = null,
    val routineName: String? = null,
    val safetyFlags: Int = 0,
    val deloadWarningCount: Int = 0,
    val romViolationCount: Int = 0,
    val spotterActivations: Int = 0
)

/**
 * Backup representation of WorkoutMetricEntity
 */
@Serializable
data class WorkoutMetricBackup(
    val id: Long = 0,
    val sessionId: String,
    val timestamp: Long,
    val loadA: Float,
    val loadB: Float,
    val positionA: Float,
    val positionB: Float,
    val ticks: Int,
    val status: Int = 0
)

/**
 * Backup representation of RoutineEntity
 */
@Serializable
data class RoutineBackup(
    val id: String,
    val name: String,
    val description: String = "",
    val createdAt: Long,
    val lastUsed: Long? = null,
    val useCount: Int = 0
)

/**
 * Backup representation of RoutineExerciseEntity
 */
@Serializable
data class RoutineExerciseBackup(
    val id: String,
    val routineId: String,
    val exerciseName: String,
    val exerciseMuscleGroup: String,
    val exerciseEquipment: String = "",
    val exerciseDefaultCableConfig: String,
    val exerciseId: String? = null,
    val cableConfig: String,
    val orderIndex: Int,
    val setReps: String,
    val weightPerCableKg: Float,
    val setWeights: String = "",
    val mode: String = "OldSchool",
    val eccentricLoad: Int = 100,
    val echoLevel: Int = 1,
    val progressionKg: Float = 0f,
    val restSeconds: Int = 60,
    val duration: Int? = null,
    val setRestSeconds: String = "[]",
    val perSetRestTime: Boolean = false,
    val isAMRAP: Boolean = false
)

/**
 * Backup representation of WeeklyProgramEntity
 */
@Serializable
data class WeeklyProgramBackup(
    val id: String,
    val title: String,
    val notes: String? = null,
    val isActive: Boolean = false,
    val lastUsed: Long? = null,
    val createdAt: Long
)

/**
 * Backup representation of ProgramDayEntity
 */
@Serializable
data class ProgramDayBackup(
    val id: Int = 0,
    val programId: String,
    val dayOfWeek: Int,
    val routineId: String
)

/**
 * Backup representation of PersonalRecordEntity
 */
@Serializable
data class PersonalRecordBackup(
    val id: Long = 0,
    val exerciseId: String,
    val weightPerCableKg: Float,
    val reps: Int,
    val timestamp: Long,
    val workoutMode: String,
    val prType: String = "MAX_WEIGHT",
    val volume: Float = 0f
)

/**
 * Root backup data structure containing all exportable data
 */
@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: String,
    val appVersion: String,
    val data: BackupContent
)

/**
 * Container for all backup data entities
 */
@Serializable
data class BackupContent(
    val workoutSessions: List<WorkoutSessionBackup> = emptyList(),
    val workoutMetrics: List<WorkoutMetricBackup> = emptyList(),
    val routines: List<RoutineBackup> = emptyList(),
    val routineExercises: List<RoutineExerciseBackup> = emptyList(),
    val weeklyPrograms: List<WeeklyProgramBackup> = emptyList(),
    val programDays: List<ProgramDayBackup> = emptyList(),
    val personalRecords: List<PersonalRecordBackup> = emptyList()
)

// ============================================================================
// Extension functions: Entity → Backup conversion
// ============================================================================

/**
 * Convert WorkoutSessionEntity to WorkoutSessionBackup
 */
fun WorkoutSessionEntity.toBackup() = WorkoutSessionBackup(
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
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    routineSessionId = routineSessionId,
    routineName = routineName,
    safetyFlags = safetyFlags,
    deloadWarningCount = deloadWarningCount,
    romViolationCount = romViolationCount,
    spotterActivations = spotterActivations
)

/**
 * Convert WorkoutMetricEntity to WorkoutMetricBackup
 */
fun WorkoutMetricEntity.toBackup() = WorkoutMetricBackup(
    id = id,
    sessionId = sessionId,
    timestamp = timestamp,
    loadA = loadA,
    loadB = loadB,
    positionA = positionA,
    positionB = positionB,
    ticks = ticks,
    status = status
)

/**
 * Convert RoutineEntity to RoutineBackup
 */
fun RoutineEntity.toBackup() = RoutineBackup(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    lastUsed = lastUsed,
    useCount = useCount
)

/**
 * Convert RoutineExerciseEntity to RoutineExerciseBackup
 */
fun RoutineExerciseEntity.toBackup() = RoutineExerciseBackup(
    id = id,
    routineId = routineId,
    exerciseName = exerciseName,
    exerciseMuscleGroup = exerciseMuscleGroup,
    exerciseEquipment = exerciseEquipment,
    exerciseDefaultCableConfig = exerciseDefaultCableConfig,
    exerciseId = exerciseId,
    cableConfig = cableConfig,
    orderIndex = orderIndex,
    setReps = setReps,
    weightPerCableKg = weightPerCableKg,
    setWeights = setWeights,
    mode = mode,
    eccentricLoad = eccentricLoad,
    echoLevel = echoLevel,
    progressionKg = progressionKg,
    restSeconds = restSeconds,
    duration = duration,
    setRestSeconds = setRestSeconds,
    perSetRestTime = perSetRestTime,
    isAMRAP = isAMRAP
)

/**
 * Convert WeeklyProgramEntity to WeeklyProgramBackup
 */
fun WeeklyProgramEntity.toBackup() = WeeklyProgramBackup(
    id = id,
    title = title,
    notes = notes,
    isActive = isActive,
    lastUsed = lastUsed,
    createdAt = createdAt
)

/**
 * Convert ProgramDayEntity to ProgramDayBackup
 */
fun ProgramDayEntity.toBackup() = ProgramDayBackup(
    id = id,
    programId = programId,
    dayOfWeek = dayOfWeek,
    routineId = routineId
)

/**
 * Convert PersonalRecordEntity to PersonalRecordBackup
 */
fun PersonalRecordEntity.toBackup() = PersonalRecordBackup(
    id = id,
    exerciseId = exerciseId,
    weightPerCableKg = weightPerCableKg,
    reps = reps,
    timestamp = timestamp,
    workoutMode = workoutMode,
    prType = prType,
    volume = volume
)

// ============================================================================
// Extension functions: Backup → Entity conversion
// ============================================================================

/**
 * Convert WorkoutSessionBackup to WorkoutSessionEntity
 */
fun WorkoutSessionBackup.toEntity() = WorkoutSessionEntity(
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
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    routineSessionId = routineSessionId,
    routineName = routineName,
    safetyFlags = safetyFlags,
    deloadWarningCount = deloadWarningCount,
    romViolationCount = romViolationCount,
    spotterActivations = spotterActivations
)

/**
 * Convert WorkoutMetricBackup to WorkoutMetricEntity
 */
fun WorkoutMetricBackup.toEntity() = WorkoutMetricEntity(
    id = id,
    sessionId = sessionId,
    timestamp = timestamp,
    loadA = loadA,
    loadB = loadB,
    positionA = positionA,
    positionB = positionB,
    ticks = ticks,
    status = status
)

/**
 * Convert RoutineBackup to RoutineEntity
 */
fun RoutineBackup.toEntity() = RoutineEntity(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    lastUsed = lastUsed,
    useCount = useCount
)

/**
 * Convert RoutineExerciseBackup to RoutineExerciseEntity
 */
fun RoutineExerciseBackup.toEntity() = RoutineExerciseEntity(
    id = id,
    routineId = routineId,
    exerciseName = exerciseName,
    exerciseMuscleGroup = exerciseMuscleGroup,
    exerciseEquipment = exerciseEquipment,
    exerciseDefaultCableConfig = exerciseDefaultCableConfig,
    exerciseId = exerciseId,
    cableConfig = cableConfig,
    orderIndex = orderIndex,
    setReps = setReps,
    weightPerCableKg = weightPerCableKg,
    setWeights = setWeights,
    mode = mode,
    eccentricLoad = eccentricLoad,
    echoLevel = echoLevel,
    progressionKg = progressionKg,
    restSeconds = restSeconds,
    duration = duration,
    setRestSeconds = setRestSeconds,
    perSetRestTime = perSetRestTime,
    isAMRAP = isAMRAP
)

/**
 * Convert WeeklyProgramBackup to WeeklyProgramEntity
 */
fun WeeklyProgramBackup.toEntity() = WeeklyProgramEntity(
    id = id,
    title = title,
    notes = notes,
    isActive = isActive,
    lastUsed = lastUsed,
    createdAt = createdAt
)

/**
 * Convert ProgramDayBackup to ProgramDayEntity
 */
fun ProgramDayBackup.toEntity() = ProgramDayEntity(
    id = id,
    programId = programId,
    dayOfWeek = dayOfWeek,
    routineId = routineId
)

/**
 * Convert PersonalRecordBackup to PersonalRecordEntity
 */
fun PersonalRecordBackup.toEntity() = PersonalRecordEntity(
    id = id,
    exerciseId = exerciseId,
    weightPerCableKg = weightPerCableKg,
    reps = reps,
    timestamp = timestamp,
    workoutMode = workoutMode,
    prType = prType,
    volume = volume
)
