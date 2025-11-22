package com.example.vitruvianredux.presentation.viewmodel

/**
 * UI state for the auto-stop feature during workouts.
 * Tracks whether auto-stop is active, progress, and remaining time.
 */
data class AutoStopUiState(
    val isActive: Boolean = false,
    val progress: Float = 0f,
    val secondsRemaining: Int = 3
)
