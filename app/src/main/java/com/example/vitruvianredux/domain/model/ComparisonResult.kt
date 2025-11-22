package com.example.vitruvianredux.domain.model

data class ComparisonResult(
    val metricType: TrendMetricType,
    val currentPeriod: PeriodData,
    val previousPeriod: PeriodData,
    val changePercentage: Float,
    val changeDirection: ChangeDirection,
    val isSignificant: Boolean
)
