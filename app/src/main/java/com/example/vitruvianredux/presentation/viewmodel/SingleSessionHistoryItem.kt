package com.example.vitruvianredux.presentation.viewmodel

import com.example.vitruvianredux.domain.model.WorkoutSession

/**
 * A single workout session history item.
 */
data class SingleSessionHistoryItem(
    val session: WorkoutSession
) : HistoryItem() {
    override val timestamp: Long = session.timestamp
}
