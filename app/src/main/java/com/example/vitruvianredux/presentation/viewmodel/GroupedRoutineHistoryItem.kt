package com.example.vitruvianredux.presentation.viewmodel

import com.example.vitruvianredux.domain.model.WorkoutSession

/**
 * A grouped routine history item containing multiple workout sessions.
 */
data class GroupedRoutineHistoryItem(
    val routineSessionId: String,
    val routineName: String,
    val sessions: List<WorkoutSession>,
    val totalDuration: Long,
    val totalReps: Int,
    val exerciseCount: Int,
    override val timestamp: Long
) : HistoryItem()
