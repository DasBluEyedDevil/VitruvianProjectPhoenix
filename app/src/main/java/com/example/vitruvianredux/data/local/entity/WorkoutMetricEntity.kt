package com.example.vitruvianredux.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing real-time workout metrics captured during a session.
 */
@Entity(
    tableName = "workout_metrics",
    indices = [Index(value = ["sessionId"])]
)
data class WorkoutMetricEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val sessionId: String,

    val timestamp: Long,

    val loadA: Float,

    val loadB: Float,

    val positionA: Int,

    val positionB: Int,

    val ticks: Int
)
