package com.example.vitruvianredux.domain.model

data class WorkoutParameters(
    val workoutType: WorkoutType,
    val reps: Int,
    val weightPerCableKg: Float = 0f,
    val progressionRegressionKg: Float = 0f,
    val isJustLift: Boolean = false,
    val useAutoStart: Boolean = false,
    val stopAtTop: Boolean = false,
    val warmupReps: Int = 3,
    val selectedExerciseId: String? = null,
    val isAMRAP: Boolean = false
)
