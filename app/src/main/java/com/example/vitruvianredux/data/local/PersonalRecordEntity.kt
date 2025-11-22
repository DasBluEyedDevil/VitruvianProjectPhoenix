package com.example.vitruvianredux.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for personal records (PRs) per exercise
 * Tracks the best performance (weight and reps) for each exercise and workout mode combination
 */
@Entity(
    tableName = "personal_records",
    indices = [
        Index(value = ["exerciseId", "workoutMode"], unique = true)
    ]
)
data class PersonalRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exerciseId: String,
    val weightPerCableKg: Float,
    val reps: Int,
    val timestamp: Long,
    val workoutMode: String
)
