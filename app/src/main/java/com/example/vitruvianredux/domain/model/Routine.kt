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
 *                    Should be set based on exercise's defaultCableConfig
 *                    If exercise allows EITHER, defaults to DOUBLE
 * @param weightPerCableKg Weight in kg per cable (machine tracks each cable independently)
 *                         For SINGLE: weight on the one active cable
 *                         For DOUBLE: weight per cable (total load = 2x this value)
 */
data class RoutineExercise(
    val id: String,
    val exercise: Exercise,
    val cableConfig: CableConfiguration,
    val orderIndex: Int,
    val setReps: List<Int> = listOf(10, 10, 10),
    val weightPerCableKg: Float,
    // Optional per-set weights in kg per cable; when empty, fall back to weightPerCableKg
    val setWeightsPerCableKg: List<Float> = emptyList(),
    // Selected workout type for this exercise in routines
    val workoutType: WorkoutType = WorkoutType.Program(ProgramMode.OldSchool),
    // Echo-specific configuration
    val eccentricLoad: EccentricLoad = EccentricLoad.LOAD_100,
    val echoLevel: EchoLevel = EchoLevel.HARDER,
    val progressionKg: Float = 0f,
    val restSeconds: Int = 60,
    val notes: String = "",
    // Optional duration in seconds for duration-based sets
    val duration: Int? = null
) {
    // Computed property for backwards compatibility
    val sets: Int get() = setReps.size
    val reps: Int get() = setReps.firstOrNull() ?: 10
}

/**
 * Helper function to determine the appropriate cable configuration for an exercise
 * If exercise allows EITHER, defaults to DOUBLE
 */
fun Exercise.resolveDefaultCableConfig(): CableConfiguration {
    return when (defaultCableConfig) {
        CableConfiguration.EITHER -> CableConfiguration.DOUBLE
        else -> defaultCableConfig
    }
}
