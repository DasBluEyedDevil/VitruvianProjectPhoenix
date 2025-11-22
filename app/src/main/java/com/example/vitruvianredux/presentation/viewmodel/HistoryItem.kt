package com.example.vitruvianredux.presentation.viewmodel

/**
 * Sealed class representing a workout history item.
 * Can be either a single session or a grouped routine session.
 */
sealed class HistoryItem {
    abstract val timestamp: Long
}
