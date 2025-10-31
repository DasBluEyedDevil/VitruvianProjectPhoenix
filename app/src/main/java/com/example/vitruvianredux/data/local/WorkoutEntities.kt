package com.example.vitruvianredux.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for workout sessions
 */
@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val mode: String,
    val reps: Int,
    val weightPerCableKg: Float,
    val progressionKg: Float,
    val duration: Long,
    val totalReps: Int,
    val warmupReps: Int,
    val workingReps: Int,
    val isJustLift: Boolean,
    val stopAtTop: Boolean
)

/**
 * Room entity for workout metrics (time series data)
 */
@Entity(tableName = "workout_metrics")
data class WorkoutMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestamp: Long,
    val loadA: Float,
    val loadB: Float,
    val positionA: Int,
    val positionB: Int,
    val ticks: Int
)

/**
 * Room entity for workout routines
 */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long? = null,
    val useCount: Int = 0
)

/**
 * Room entity for exercises within a routine
 *
 * MIGRATION NOTE: Added exercise detail fields (muscleGroup, equipment, defaultCableConfig)
 * to support Exercise data class instead of enum
 */
@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId")]
)
data class RoutineExerciseEntity(
    @PrimaryKey
    val id: String,
    val routineId: String,
    // Exercise data
    val exerciseName: String,
    val exerciseMuscleGroup: String,
    val exerciseEquipment: String = "",
    val exerciseDefaultCableConfig: String, // "SINGLE", "DOUBLE", or "EITHER"
    val exerciseId: String? = null, // Exercise library ID for loading videos/thumbnails
    // Routine-specific configuration
    val cableConfig: String, // "SINGLE" or "DOUBLE" (never "EITHER" in storage)
    val orderIndex: Int,
    val setReps: String, // Comma-separated rep counts (e.g., "10,10,10" or "10,8,6,4")
    val weightPerCableKg: Float,
    val progressionKg: Float = 0f,
    val restSeconds: Int = 60,
    val notes: String = ""
)
