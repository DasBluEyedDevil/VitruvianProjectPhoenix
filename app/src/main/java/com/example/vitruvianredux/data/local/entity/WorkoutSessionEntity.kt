package com.example.vitruvianredux.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a completed workout session with all its statistics.
 */
@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey
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
