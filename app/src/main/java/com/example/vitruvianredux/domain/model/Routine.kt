package com.example.vitruvianredux.domain.model

/**
 * Domain model for a workout routine
 */
data class Routine(
    val id: String,
    val name: String,
    val description: String = "",
    val exercises: List<RoutineExercise> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long? = null,
    val useCount: Int = 0
)

/**
 * Domain model for an exercise within a routine
 */
data class RoutineExercise(
    val id: String,
    val exercise: Exercise,
    val orderIndex: Int,
    val sets: Int,
    val reps: Int,
    val weightPerCableKg: Float,
    val progressionKg: Float = 0f,
    val restSeconds: Int = 60,
    val notes: String = ""
)
