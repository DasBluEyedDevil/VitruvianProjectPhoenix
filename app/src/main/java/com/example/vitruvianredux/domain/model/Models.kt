package com.example.vitruvianredux.domain.model

/**
 * Connection state sealed class representing BLE connection states
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Scanning : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val deviceName: String, val deviceAddress: String) : ConnectionState()
    data class Error(val message: String, val throwable: Throwable? = null) : ConnectionState()
}

/**
 * Workout state sealed class representing workout execution states
 */
sealed class WorkoutState {
    object Idle : WorkoutState()
    object Initializing : WorkoutState()
    data class Countdown(val secondsRemaining: Int) : WorkoutState()
    object Active : WorkoutState()
    object Paused : WorkoutState()
    object Completed : WorkoutState()
    data class Error(val message: String) : WorkoutState()
}

/**
 * Workout modes available on Vitruvian trainer
 */
sealed class WorkoutMode(val modeValue: Int, val displayName: String) {
    object OldSchool : WorkoutMode(0, "Old School")
    object Pump : WorkoutMode(2, "Pump")
    object TUT : WorkoutMode(3, "TUT")
    object TUTBeast : WorkoutMode(4, "TUT Beast")
    object EccentricOnly : WorkoutMode(6, "Eccentric Only")
    data class Echo(val level: EchoLevel) : WorkoutMode(10, "Echo ${level.name}")
}

/**
 * Echo mode difficulty levels
 */
enum class EchoLevel(val levelValue: Int) {
    LEVEL_1(0),
    LEVEL_2(1),
    LEVEL_3(2),
    LEVEL_4(3)
}

/**
 * Weight unit preference
 */
enum class WeightUnit {
    KG, LB
}

/**
 * Workout parameters
 */
data class WorkoutParameters(
    val mode: WorkoutMode,
    val reps: Int,
    val weightPerCableKg: Float,
    val progressionKg: Float = 0f,
    val isJustLift: Boolean = false,
    val stopAtTop: Boolean = false,
    val warmupReps: Int = 3
)

/**
 * Real-time workout metric data from the device
 */
data class WorkoutMetric(
    val timestamp: Long = System.currentTimeMillis(),
    val loadA: Float,
    val loadB: Float,
    val positionA: Int,
    val positionB: Int,
    val ticks: Int = 0
) {
    val totalLoad: Float get() = loadA + loadB
}

/**
 * Rep count tracking
 */
data class RepCount(
    val warmupReps: Int = 0,
    val workingReps: Int = 0,
    val totalReps: Int = warmupReps + workingReps,
    val isWarmupComplete: Boolean = false
)

/**
 * Rep event types
 */
enum class RepType {
    WARMUP_COMPLETED,
    WORKING_COMPLETED,
    WARMUP_COMPLETE,
    WORKOUT_COMPLETE
}

/**
 * Rep event data
 */
data class RepEvent(
    val type: RepType,
    val warmupCount: Int,
    val workingCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Haptic feedback event types
 */
enum class HapticEvent {
    REP_COMPLETED,
    WARMUP_COMPLETE,
    WORKOUT_COMPLETE,
    WORKOUT_START,
    WORKOUT_END
}

/**
 * Workout session data (simplified for database storage)
 */
data class WorkoutSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val mode: String = "OldSchool",
    val reps: Int = 10,
    val weightPerCableKg: Float = 10f,
    val progressionKg: Float = 0f,
    val duration: Long = 0,
    val totalReps: Int = 0,
    val warmupReps: Int = 0,
    val workingReps: Int = 0,
    val isJustLift: Boolean = false,
    val stopAtTop: Boolean = false
)

/**
 * Chart data point for visualization
 */
data class ChartDataPoint(
    val timestamp: Long,
    val totalLoad: Float,
    val loadA: Float,
    val loadB: Float,
    val positionA: Int,
    val positionB: Int
)

/**
 * Chart event markers
 */
sealed class ChartEvent(val timestamp: Long, val label: String) {
    class RepStart(timestamp: Long, repNumber: Int) : ChartEvent(timestamp, "Rep $repNumber")
    class RepComplete(timestamp: Long, repNumber: Int) : ChartEvent(timestamp, "Rep $repNumber Complete")
    class WarmupComplete(timestamp: Long) : ChartEvent(timestamp, "Warmup Complete")
}

