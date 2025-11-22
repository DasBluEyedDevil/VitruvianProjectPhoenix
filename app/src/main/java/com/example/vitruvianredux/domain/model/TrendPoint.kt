package com.example.vitruvianredux.domain.model

data class TrendPoint(
    val timestamp: Long,
    val value: Float,
    val label: String? = null
)
