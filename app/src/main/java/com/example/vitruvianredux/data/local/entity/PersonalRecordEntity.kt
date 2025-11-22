package com.example.vitruvianredux.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing personal records for exercises.
 */
@Entity(
    tableName = "personal_records",
    indices = [Index(value = ["exerciseId"])]
)
data class PersonalRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val exerciseId: String,

    val weightPerCableKg: Float,

    val reps: Int,

    val timestamp: Long,

    val workoutMode: String
)
