package com.example.vitruvianredux.util

/**
 * Workout-related constants
 */
@Suppress("unused")  // Protocol reference constants
object WorkoutConstants {
    const val LB_PER_KG = 2.2046226218488
    const val KG_PER_LB = 1 / LB_PER_KG

    const val MIN_WEIGHT_KG = 0f
    const val MAX_WEIGHT_KG = 100f
    const val MAX_PROGRESSION_KG = 3f

    const val DEFAULT_WARMUP_REPS = 3
    const val MAX_HISTORY_POINTS = 72000 // 2 hours at 100ms

    // Position ranges (mm) - from official app documentation
    // Cable position is measured in millimeters, valid range is -1000 to +1000 mm
    const val MAX_POSITION = 1000   // Maximum valid position (mm)
    const val MIN_POSITION = -1000  // Minimum valid position (mm)

    // Spike detection threshold (web app uses 50000 to filter BLE transmission errors)
    const val POSITION_SPIKE_THRESHOLD = 50000
}

/**
 * Protocol constants - aligned with Phoenix Backend (official app)
 * NOTE: Legacy web app used different sizes and commands
 */
@Suppress("unused")  // Protocol reference constants
object ProtocolConstants {
    // Command types (single-byte commands per Phoenix Backend)
    const val CMD_STOP: Byte = 0x50           // Stop/halt (2 bytes total)
    const val CMD_REGULAR: Byte = 0x4F        // Regular mode packet (25 bytes)
    const val CMD_ECHO: Byte = 0x4E           // Echo mode packet (29 bytes)
    const val CMD_ACTIVATION: Byte = 0x04     // Activation packet (97 bytes)
    const val CMD_COLOR_SCHEME: Byte = 0x11   // Color scheme (34 bytes)

    // Legacy command IDs (deprecated - web app used these)
    @Deprecated("Web app legacy - use CMD_REGULAR instead")
    const val CMD_INIT: Byte = 0x0A
    @Deprecated("Web app legacy - use CMD_COLOR_SCHEME instead")
    const val CMD_INIT_PRESET: Byte = 0x11

    // Frame sizes (Phoenix Backend aligned)
    const val STOP_PACKET_SIZE = 2
    const val REGULAR_PACKET_SIZE = 25        // Was 96 in web app
    const val ECHO_PACKET_SIZE = 29           // Was 40 in web app
    const val ACTIVATION_PACKET_SIZE = 97
    const val COLOR_SCHEME_SIZE = 34

    // Legacy frame sizes (deprecated - for reference only)
    @Deprecated("Web app legacy - actual size is 25 bytes")
    const val PROGRAM_PARAMS_SIZE = 96
    @Deprecated("Web app legacy - actual size is 29 bytes")
    const val ECHO_CONTROL_SIZE = 40

    // Mode values (used in ActivationPacket)
    const val MODE_OLD_SCHOOL = 0
    const val MODE_PUMP = 2
    const val MODE_TUT = 3
    const val MODE_TUT_BEAST = 4
    const val MODE_ECCENTRIC_ONLY = 6
    const val MODE_ECHO = 10
}