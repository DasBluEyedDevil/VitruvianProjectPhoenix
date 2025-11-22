package com.example.vitruvianredux.domain.model

data class Plateau(
    val exerciseId: String,
    val metricType: TrendMetricType,
    val startDate: Long,
    val endDate: Long?,
    val averageValue: Float,
    val durationDays: Int,
    val recommendation: String
)
