package com.example.vitruvianredux.domain.model

data class RepCount(
    val warmupReps: Int = 0,
    val workingReps: Int = 0,
    val totalReps: Int = workingReps,
    val isWarmupComplete: Boolean = false
)
