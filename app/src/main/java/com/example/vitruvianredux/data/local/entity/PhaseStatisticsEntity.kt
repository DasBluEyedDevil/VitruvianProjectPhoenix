package com.example.vitruvianredux.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "phase_statistics",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class PhaseStatisticsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String, // Links to WorkoutSessionEntity.id

    // Concentric
    val concentricKgAvg: Float,
    val concentricKgMax: Float,
    val concentricVelAvg: Float,
    val concentricVelMax: Float,
    val concentricWattAvg: Float,
    val concentricWattMax: Float,

    // Eccentric
    val eccentricKgAvg: Float,
    val eccentricKgMax: Float,
    val eccentricVelAvg: Float,
    val eccentricVelMax: Float,
    val eccentricWattAvg: Float,
    val eccentricWattMax: Float,

    val timestamp: Long = System.currentTimeMillis()
)
