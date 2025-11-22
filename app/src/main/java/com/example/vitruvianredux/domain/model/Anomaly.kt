package com.example.vitruvianredux.domain.model

data class Anomaly(
    val timestamp: Long,
    val metricType: TrendMetricType,
    val actualValue: Float,
    val expectedValue: Float,
    val deviation: Float,
    val severity: AnomalySeverity,
    val description: String
)
