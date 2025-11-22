package com.example.vitruvianredux.domain.model

data class ChartDataPoint(
    val timestamp: Long,
    val totalLoad: Float,
    val loadA: Float,
    val loadB: Float,
    val positionA: Int,
    val positionB: Int
)
