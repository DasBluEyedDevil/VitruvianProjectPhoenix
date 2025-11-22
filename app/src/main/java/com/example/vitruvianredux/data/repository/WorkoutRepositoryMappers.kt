package com.example.vitruvianredux.data.repository

import com.example.vitruvianredux.data.local.entity.RoutineEntity
import com.example.vitruvianredux.data.local.entity.RoutineExerciseEntity
import com.example.vitruvianredux.data.local.entity.WorkoutMetricEntity
import com.example.vitruvianredux.data.local.entity.WorkoutSessionEntity
import com.example.vitruvianredux.data.local.entity.PhaseStatisticsEntity
import com.example.vitruvianredux.domain.model.CableConfiguration
import com.example.vitruvianredux.domain.model.EccentricLoad
import com.example.vitruvianredux.domain.model.EchoLevel
import com.example.vitruvianredux.domain.model.Exercise
import com.example.vitruvianredux.domain.model.HeuristicStatistics
import com.example.vitruvianredux.domain.model.ProgramMode
import com.example.vitruvianredux.domain.model.Routine
import com.example.vitruvianredux.domain.model.RoutineExercise
import com.example.vitruvianredux.domain.model.WorkoutMetric
import com.example.vitruvianredux.domain.model.WorkoutSession
import com.example.vitruvianredux.domain.model.WorkoutType
import timber.log.Timber

// ===================== WorkoutSession Mappers =====================

/**
 * Convert WorkoutSessionEntity to WorkoutSession domain model.
 */
internal fun WorkoutSessionEntity.toWorkoutSession(): WorkoutSession {
    return WorkoutSession(
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
        routineName = routineName
    )
}

/**
 * Convert WorkoutSession to WorkoutSessionEntity.
 */
internal fun WorkoutSession.toEntity(): WorkoutSessionEntity {
    return WorkoutSessionEntity(
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
        routineName = routineName
    )
}

// ===================== WorkoutMetric Mappers =====================

/**
 * Convert WorkoutMetricEntity to WorkoutMetric domain model.
 */
internal fun WorkoutMetricEntity.toWorkoutMetric(): WorkoutMetric {
    return WorkoutMetric(
        timestamp = timestamp,
        loadA = loadA,
        loadB = loadB,
        positionA = positionA,
        positionB = positionB,
        ticks = ticks
    )
}

/**
 * Convert WorkoutMetric to WorkoutMetricEntity.
 */
internal fun WorkoutMetric.toEntity(sessionId: String, index: Int): WorkoutMetricEntity {
    return WorkoutMetricEntity(
        sessionId = sessionId,
        timestamp = timestamp,
        loadA = loadA,
        loadB = loadB,
        positionA = positionA,
        positionB = positionB,
        ticks = ticks
    )
}

// ===================== Routine Mappers =====================

/**
 * Convert Routine to RoutineEntity.
 */
internal fun Routine.toEntity(): RoutineEntity {
    return RoutineEntity(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        lastUsed = lastUsed,
        useCount = useCount
    )
}

/**
 * Convert RoutineEntity to Routine domain model.
 */
internal fun RoutineEntity.toRoutine(exerciseEntities: List<RoutineExerciseEntity>): Routine {
    return Routine(
        id = id,
        name = name,
        description = description,
        exercises = exerciseEntities.map { it.toRoutineExercise() },
        createdAt = createdAt,
        lastUsed = lastUsed,
        useCount = useCount
    )
}

// ===================== RoutineExercise Mappers =====================

/**
 * Convert RoutineExercise to RoutineExerciseEntity.
 */
internal fun RoutineExercise.toEntity(routineId: String): RoutineExerciseEntity {
    val setRepsString = setReps.joinToString(",") { it?.toString() ?: "null" }
    val setWeightsString = setWeightsPerCableKg.joinToString(",") { it.toString() }
    val setRestString = setRestSeconds.toJsonArray()

    Timber.d("toEntity: '${exercise.name}' setReps=$setReps -> '$setRepsString', " +
            "setWeights=$setWeightsPerCableKg -> '$setWeightsString', " +
            "setRest=$setRestSeconds -> '$setRestString'")

    val modeString = when (val type = workoutType) {
        is WorkoutType.Program -> when (type.mode) {
            is ProgramMode.OldSchool -> "OldSchool"
            is ProgramMode.Pump -> "Pump"
            is ProgramMode.TUT -> "TUT"
            is ProgramMode.TUTBeast -> "TUTBeast"
            is ProgramMode.EccentricOnly -> "EccentricOnly"
        }
        is WorkoutType.Echo -> "Echo"
    }

    val eccentricPercentage = when (val type = workoutType) {
        is WorkoutType.Echo -> type.eccentricLoad.percentage
        else -> eccentricLoad.percentage
    }

    val echoLevelValue = when (val type = workoutType) {
        is WorkoutType.Echo -> type.level.levelValue
        else -> echoLevel.levelValue
    }

    val entity = RoutineExerciseEntity(
        id = id,
        routineId = routineId,
        exerciseName = exercise.name,
        exerciseMuscleGroup = exercise.muscleGroup,
        exerciseEquipment = exercise.equipment,
        exerciseDefaultCableConfig = exercise.defaultCableConfig.name,
        exerciseId = exercise.id,
        cableConfig = cableConfig.name,
        orderIndex = orderIndex,
        setReps = setRepsString,
        weightPerCableKg = weightPerCableKg,
        setWeights = setWeightsString,
        mode = modeString,
        eccentricLoad = eccentricPercentage,
        echoLevel = echoLevelValue,
        progressionKg = progressionKg,
        restSeconds = setRestSeconds.firstOrNull() ?: 60,
        duration = duration,
        setRestSeconds = setRestString,
        perSetRestTime = perSetRestTime,
        isAMRAP = isAMRAP
    )

    Timber.d("Domain->DB: ${exercise.name}, isAMRAP=$isAMRAP, setReps=$setReps -> DB string='${entity.setReps}'")
    return entity
}

/**
 * Convert RoutineExerciseEntity to RoutineExercise domain model.
 */
internal fun RoutineExerciseEntity.toRoutineExercise(): RoutineExercise {
    if (mode == "Echo") {
        Timber.d("DATABASE -> DOMAIN MAPPING (Issue #109)")
        Timber.d("Exercise: $exerciseName")
        Timber.d("DB Values: mode='$mode', echoLevel (raw): $echoLevel, eccentricLoad (raw): $eccentricLoad")
    }

    val exercise = Exercise(
        name = exerciseName,
        muscleGroup = exerciseMuscleGroup,
        equipment = exerciseEquipment,
        defaultCableConfig = CableConfiguration.valueOf(exerciseDefaultCableConfig),
        id = exerciseId
    )

    val parsedSetReps: List<Int?> = if (setReps.isEmpty()) {
        listOf(null)
    } else {
        setReps.split(",").map { str ->
            if (str.isEmpty() || str == "null") null else str.toIntOrNull()
        }
    }

    val parsedSetWeights: List<Float> = if (setWeights.isEmpty()) {
        emptyList()
    } else {
        setWeights.split(",").mapNotNull { it.toFloatOrNull() }
    }

    val parsedWorkoutType: WorkoutType = when (mode) {
        "OldSchool" -> WorkoutType.Program(ProgramMode.OldSchool)
        "Pump" -> WorkoutType.Program(ProgramMode.Pump)
        "TUT" -> WorkoutType.Program(ProgramMode.TUT)
        "TUTBeast" -> WorkoutType.Program(ProgramMode.TUTBeast)
        "EccentricOnly" -> WorkoutType.Program(ProgramMode.EccentricOnly)
        "Echo" -> {
            val level = EchoLevel.values().find { it.levelValue == echoLevel } ?: EchoLevel.HARDER
            val load = EccentricLoad.values().find { it.percentage == this.eccentricLoad } ?: EccentricLoad.LOAD_100

            if (this.mode == "Echo") {
                Timber.d("Mapped Values: echoLevel: ${this.echoLevel} -> ${level.displayName} (levelValue=${level.levelValue})")
                Timber.d("Mapped Values: eccentricLoad: ${this.eccentricLoad} -> ${load.displayName} (${load.percentage}%)")
            }

            WorkoutType.Echo(level, load)
        }
        else -> WorkoutType.Program(ProgramMode.OldSchool)
    }

    val parsedEccentricLoad = EccentricLoad.values()
        .find { it.percentage == this.eccentricLoad } ?: EccentricLoad.LOAD_100

    val parsedEchoLevel = EchoLevel.values()
        .find { it.levelValue == this.echoLevel } ?: EchoLevel.HARDER

    val routineExercise = RoutineExercise(
        id = id,
        exercise = exercise,
        cableConfig = CableConfiguration.valueOf(cableConfig),
        orderIndex = orderIndex,
        setReps = parsedSetReps,
        weightPerCableKg = weightPerCableKg,
        setWeightsPerCableKg = parsedSetWeights,
        workoutType = parsedWorkoutType,
        eccentricLoad = parsedEccentricLoad,
        echoLevel = parsedEchoLevel,
        progressionKg = progressionKg,
        setRestSeconds = parseIntListFromJson(setRestSeconds),
        duration = duration,
        isAMRAP = isAMRAP,
        perSetRestTime = perSetRestTime
    )

    Timber.d("DB->Domain: $exerciseName, DB string='$setReps' -> setReps=${routineExercise.setReps}, isAMRAP=$isAMRAP")
    return routineExercise.withNormalizedRestTimes()
}

// ===================== Phase Statistics Mappers =====================

/**
 * Convert HeuristicStatistics to PhaseStatisticsEntity.
 */
internal fun HeuristicStatistics.toPhaseStatisticsEntity(sessionId: String): PhaseStatisticsEntity {
    return PhaseStatisticsEntity(
        sessionId = sessionId,
        concentricKgAvg = concentric.kgAvg,
        concentricKgMax = concentric.kgMax,
        concentricVelAvg = concentric.velAvg,
        concentricVelMax = concentric.velMax,
        concentricWattAvg = concentric.wattAvg,
        concentricWattMax = concentric.wattMax,
        eccentricKgAvg = eccentric.kgAvg,
        eccentricKgMax = eccentric.kgMax,
        eccentricVelAvg = eccentric.velAvg,
        eccentricVelMax = eccentric.velMax,
        eccentricWattAvg = eccentric.wattAvg,
        eccentricWattMax = eccentric.wattMax,
        timestamp = timestamp
    )
}

// ===================== Helper Functions =====================

/**
 * Convert a list of integers to JSON array string.
 */
private fun List<Int>.toJsonArray(): String {
    return "[${joinToString(",")}]"
}

/**
 * Parse a JSON array string to list of integers.
 */
private fun parseIntListFromJson(json: String): List<Int> {
    if (json.isEmpty() || json == "[]") {
        return emptyList()
    }

    val cleaned = json.removePrefix("[").removeSuffix("]").trim()
    if (cleaned.isEmpty()) {
        return emptyList()
    }

    return cleaned.split(",").mapNotNull { it.trim().toIntOrNull() }
}
