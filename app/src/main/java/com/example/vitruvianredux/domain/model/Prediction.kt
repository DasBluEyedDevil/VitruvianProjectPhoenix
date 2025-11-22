package com.example.vitruvianredux.domain.model

data class Prediction(
    val metricType: TrendMetricType,
    val currentValue: Float,
    val predictedValue: Float,
    val predictedDate: Long,
    val confidence: Float,
    val method: PredictionMethod
)
