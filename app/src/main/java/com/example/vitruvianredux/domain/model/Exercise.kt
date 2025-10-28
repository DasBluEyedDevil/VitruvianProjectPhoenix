package com.example.vitruvianredux.domain.model

/**
 * Cable configuration for Vitruvian exercises
 * - SINGLE: One cable only (unilateral - e.g., one-arm row)
 * - DOUBLE: Both cables required (bilateral - e.g., bench press)
 * - EITHER: User can choose single or double (e.g., bicep curls)
 */
enum class CableConfiguration {
    SINGLE,
    DOUBLE,
    EITHER
}

/**
 * Exercises supported by Vitruvian Trainer
 * Hardware does NOT provide exercise names - must be tracked in app
 *
 * NOTES:
 * - Vitruvian cables only pull UPWARD from floor platform
 * - Compatible: Rows, presses, curls, squats, deadlifts, raises
 * - NOT compatible: Pulldowns, pushdowns (require overhead anchor)
 * - Machine tracks each cable independently (loadA, loadB, posA, posB)
 * - Weight is always specified as "per cable" in the BLE protocol
 */
enum class Exercise(
    val displayName: String,
    val category: ExerciseCategory,
    val defaultCableConfig: CableConfiguration
) {
    // Chest (all bilateral - require both cables)
    BENCH_PRESS("Bench Press", ExerciseCategory.CHEST, CableConfiguration.DOUBLE),
    INCLINE_BENCH_PRESS("Incline Bench Press", ExerciseCategory.CHEST, CableConfiguration.DOUBLE),
    DECLINE_BENCH_PRESS("Decline Bench Press", ExerciseCategory.CHEST, CableConfiguration.DOUBLE),
    CHEST_FLY("Chest Fly", ExerciseCategory.CHEST, CableConfiguration.DOUBLE),

    // Back (can be done single or double arm)
    BENT_OVER_ROW("Bent Over Row", ExerciseCategory.BACK, CableConfiguration.EITHER),
    SEATED_ROW("Seated Row", ExerciseCategory.BACK, CableConfiguration.EITHER),
    UPRIGHT_ROW("Upright Row", ExerciseCategory.BACK, CableConfiguration.DOUBLE),
    FACE_PULL("Face Pull", ExerciseCategory.BACK, CableConfiguration.EITHER),
    SHRUG("Shrug", ExerciseCategory.BACK, CableConfiguration.EITHER),

    // Shoulders
    SHOULDER_PRESS("Shoulder Press", ExerciseCategory.SHOULDERS, CableConfiguration.EITHER),
    FRONT_RAISE("Front Raise", ExerciseCategory.SHOULDERS, CableConfiguration.EITHER),
    LATERAL_RAISE("Lateral Raise", ExerciseCategory.SHOULDERS, CableConfiguration.EITHER),
    REAR_DELT_FLY("Rear Delt Fly", ExerciseCategory.SHOULDERS, CableConfiguration.SINGLE),

    // Biceps (can be done single or double arm)
    BICEP_CURL("Bicep Curl", ExerciseCategory.BICEPS, CableConfiguration.EITHER),
    HAMMER_CURL("Hammer Curl", ExerciseCategory.BICEPS, CableConfiguration.EITHER),
    CONCENTRATION_CURL("Concentration Curl", ExerciseCategory.BICEPS, CableConfiguration.SINGLE),
    PREACHER_CURL("Preacher Curl", ExerciseCategory.BICEPS, CableConfiguration.EITHER),

    // Triceps (overhead extensions only - no pushdowns)
    OVERHEAD_TRICEP_EXTENSION("Overhead Tricep Extension", ExerciseCategory.TRICEPS, CableConfiguration.EITHER),
    CLOSE_GRIP_PRESS("Close Grip Press", ExerciseCategory.TRICEPS, CableConfiguration.DOUBLE),

    // Legs
    SQUAT("Squat", ExerciseCategory.LEGS, CableConfiguration.DOUBLE),
    FRONT_SQUAT("Front Squat", ExerciseCategory.LEGS, CableConfiguration.DOUBLE),
    DEADLIFT("Deadlift", ExerciseCategory.LEGS, CableConfiguration.DOUBLE),
    ROMANIAN_DEADLIFT("Romanian Deadlift", ExerciseCategory.LEGS, CableConfiguration.DOUBLE),
    LUNGE("Lunge", ExerciseCategory.LEGS, CableConfiguration.SINGLE),
    HAMSTRING_CURL("Hamstring Curl", ExerciseCategory.LEGS, CableConfiguration.SINGLE),
    CALF_RAISE("Calf Raise", ExerciseCategory.LEGS, CableConfiguration.EITHER),

    // Glutes
    HIP_THRUST("Hip Thrust", ExerciseCategory.GLUTES, CableConfiguration.DOUBLE),
    GLUTE_BRIDGE("Glute Bridge", ExerciseCategory.GLUTES, CableConfiguration.DOUBLE),
    PULL_THROUGH("Pull Through", ExerciseCategory.GLUTES, CableConfiguration.DOUBLE),

    // Core (typically single cable for rotation/anti-rotation)
    CRUNCH("Crunch", ExerciseCategory.CORE, CableConfiguration.DOUBLE),
    PALLOF_PRESS("Pallof Press", ExerciseCategory.CORE, CableConfiguration.SINGLE),
    WOOD_CHOP("Wood Chop", ExerciseCategory.CORE, CableConfiguration.SINGLE),

    // Full Body
    POWER_CLEAN("Power Clean", ExerciseCategory.FULL_BODY, CableConfiguration.DOUBLE);
}

enum class ExerciseCategory(val displayName: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    LEGS("Legs"),
    GLUTES("Glutes"),
    CORE("Core"),
    FULL_BODY("Full Body")
}
