package com.example.vitruvianredux.util

/**
 * Workout-related constants
 */
object WorkoutConstants {
    const val LB_PER_KG = 2.2046226218488
    const val KG_PER_LB = 1 / LB_PER_KG

    const val MIN_WEIGHT_KG = 0f
    const val MAX_WEIGHT_KG = 100f
    const val MAX_PROGRESSION_KG = 3f

    const val DEFAULT_WARMUP_REPS = 3
    const val MAX_HISTORY_POINTS = 72000 // 2 hours at 100ms

    // Position ranges
    const val MAX_POSITION = 3000
    const val MIN_POSITION = 0
}

/**
 * Protocol constants from protocol.js
 */
object ProtocolConstants {
    // Command types
    const val CMD_INIT = 0x0A.toByte()
    const val CMD_INIT_PRESET = 0x11.toByte()
    const val CMD_PROGRAM_PARAMS = 0x04.toByte()
    const val CMD_ECHO_CONTROL = 0x13.toByte()
    const val CMD_COLOR_SCHEME = 0x1D.toByte()

    // Frame sizes
    const val INIT_CMD_SIZE = 4
    const val INIT_PRESET_SIZE = 34
    const val PROGRAM_PARAMS_SIZE = 96
    const val ECHO_CONTROL_SIZE = 40
    const val COLOR_SCHEME_SIZE = 44

    // Mode values
    const val MODE_OLD_SCHOOL = 0
    const val MODE_PUMP = 2
    const val MODE_TUT = 3
    const val MODE_TUT_BEAST = 4
    const val MODE_ECCENTRIC_ONLY = 6
    const val MODE_ECHO = 10
}