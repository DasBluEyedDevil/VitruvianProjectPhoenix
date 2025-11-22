package com.example.vitruvianredux.domain.model

data class PeriodData(
    val startDate: Long,
    val endDate: Long,
    val totalValue: Float,
    val averageValue: Float,
    val dataPoints: List<TrendPoint>
)
