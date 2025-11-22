package com.example.vitruvianredux.domain.model

data class PersonalRecord(
    val id: Long = 0L,
    val exerciseId: String,
    val weightPerCableKg: Float,
    val reps: Int,
    val timestamp: Long,
    val workoutMode: String
)
