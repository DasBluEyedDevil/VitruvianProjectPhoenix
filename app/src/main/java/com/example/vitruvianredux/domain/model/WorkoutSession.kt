package com.example.vitruvianredux.domain.model

import java.util.UUID

data class WorkoutSession(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val mode: String = "",
    val reps: Int = 0,
    val weightPerCableKg: Float = 0f,
    val progressionKg: Float = 0f,
    val duration: Long = 0L,
    val totalReps: Int = 0,
    val warmupReps: Int = 0,
    val workingReps: Int = 0,
    val isJustLift: Boolean = false,
    val stopAtTop: Boolean = false,
    val eccentricLoad: Int = 0,
    val echoLevel: Int = 0,
    val exerciseId: String? = null,
    val exerciseName: String? = null,
    val routineSessionId: String? = null,
    val routineName: String? = null,
    val safetyFlags: Int = 0,
    val deloadWarningCount: Int = 0,
    val romViolationCount: Int = 0,
    val spotterActivations: Int = 0
)
