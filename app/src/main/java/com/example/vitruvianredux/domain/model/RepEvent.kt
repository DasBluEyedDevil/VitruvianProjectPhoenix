package com.example.vitruvianredux.domain.model

data class RepEvent(
    val type: RepType,
    val warmupCount: Int,
    val workingCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)
