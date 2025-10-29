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
 *
 * @param cableConfig User's cable configuration choice (SINGLE or DOUBLE)
 *                    Defaults to exercise's defaultCableConfig, but can be overridden
 *                    if exercise allows EITHER configuration
 * @param weightPerCableKg Weight in kg per cable (machine tracks each cable independently)
 *                         For SINGLE: weight on the one active cable
 *                         For DOUBLE: weight per cable (total load = 2x this value)
 */
data class RoutineExercise(
    val id: String,
    val exercise: Exercise,
    val cableConfig: CableConfiguration = exercise.defaultCableConfig.let {
        if (it == CableConfiguration.EITHER) CableConfiguration.DOUBLE else it
    },
    val orderIndex: Int,
    val setReps: List<Int> = listOf(10, 10, 10),
    val weightPerCableKg: Float,
    val progressionKg: Float = 0f,
    val restSeconds: Int = 60,
    val notes: String = ""
) {
    // Computed property for backwards compatibility
    val sets: Int get() = setReps.size
    val reps: Int get() = setReps.firstOrNull() ?: 10
}
