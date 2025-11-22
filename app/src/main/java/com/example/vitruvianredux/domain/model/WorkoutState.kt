package com.example.vitruvianredux.domain.model

sealed class WorkoutState {

    data object Idle : WorkoutState()

    data object Initializing : WorkoutState()

    data class Countdown(val secondsRemaining: Int) : WorkoutState()

    data object Active : WorkoutState()

    data class SetSummary(
        val metrics: List<WorkoutMetric>,
        val peakPower: Float,
        val averagePower: Float,
        val repCount: Int,
        val heuristicStatistics: HeuristicStatistics? = null,
        val safetyEventSummary: SafetyEventSummary? = null
    ) : WorkoutState()

    data object Paused : WorkoutState()

    data object Completed : WorkoutState()

    data class Error(val message: String) : WorkoutState()

    data class Resting(
        val restSecondsRemaining: Int,
        val nextExerciseName: String,
        val isLastExercise: Boolean,
        val currentSet: Int,
        val totalSets: Int
    ) : WorkoutState()
}
