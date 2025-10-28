package com.example.vitruvianredux.domain.model

/**
 * Common exercises supported by Vitruvian Trainer
 * Hardware does NOT provide exercise names - must be tracked in app
 */
enum class Exercise(val displayName: String, val category: ExerciseCategory) {
    // Upper Body - Push
    BENCH_PRESS("Bench Press", ExerciseCategory.UPPER_PUSH),
    INCLINE_BENCH_PRESS("Incline Bench Press", ExerciseCategory.UPPER_PUSH),
    DECLINE_BENCH_PRESS("Decline Bench Press", ExerciseCategory.UPPER_PUSH),
    SHOULDER_PRESS("Shoulder Press", ExerciseCategory.UPPER_PUSH),
    CHEST_FLY("Chest Fly", ExerciseCategory.UPPER_PUSH),
    
    // Upper Body - Pull
    BENT_OVER_ROW("Bent Over Row", ExerciseCategory.UPPER_PULL),
    SEATED_ROW("Seated Row", ExerciseCategory.UPPER_PULL),
    LAT_PULLDOWN("Lat Pulldown", ExerciseCategory.UPPER_PULL),
    BICEP_CURL("Bicep Curl", ExerciseCategory.UPPER_PULL),
    SHRUG("Shrug", ExerciseCategory.UPPER_PULL),
    
    // Lower Body
    SQUAT("Squat", ExerciseCategory.LOWER_BODY),
    DEADLIFT("Deadlift", ExerciseCategory.LOWER_BODY),
    ROMANIAN_DEADLIFT("Romanian Deadlift", ExerciseCategory.LOWER_BODY),
    LUNGE("Lunge", ExerciseCategory.LOWER_BODY),
    CALF_RAISE("Calf Raise", ExerciseCategory.LOWER_BODY),
    
    // Core
    CRUNCH("Crunch", ExerciseCategory.CORE),
    AB_PULLDOWN("Ab Pulldown", ExerciseCategory.CORE),
    
    // Arms
    TRICEP_EXTENSION("Tricep Extension", ExerciseCategory.ARMS),
    HAMMER_CURL("Hammer Curl", ExerciseCategory.ARMS),
    
    // Full Body
    POWER_CLEAN("Power Clean", ExerciseCategory.FULL_BODY);
}

enum class ExerciseCategory {
    UPPER_PUSH,
    UPPER_PULL,
    LOWER_BODY,
    CORE,
    ARMS,
    FULL_BODY
}
