package com.example.vitruvianredux.domain.model

data class WorkoutMetric(
    val timestamp: Long = System.currentTimeMillis(),
    val loadA: Float,
    val loadB: Float,
    val positionA: Int,
    val positionB: Int,
    val ticks: Int = 0,
    val velocityA: Double = 0.0,
    val velocityB: Double = 0.0,
    val statusFlags: Set<SampleStatus> = emptySet()
) {
    val totalLoad: Float
        get() = loadA + loadB
}
