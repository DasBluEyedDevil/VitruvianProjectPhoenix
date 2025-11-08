package com.example.vitruvianredux.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

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
    val stopAtTop: Boolean,
    // Echo mode configuration (added in v13)
    val eccentricLoad: Int = 100,  // Percentage (0, 50, 75, 100, 125, 150)
    val echoLevel: Int = 2,  // 1=Hard, 2=Harder, 3=Hardest, 4=Epic
    // Exercise tracking (added in v15)
    val exerciseId: String? = null  // Exercise library ID for PR tracking
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
    // Optional per-set weights as comma-separated floats (kg per cable)
    val setWeights: String = "",
    // Selected workout mode stored as String (e.g., "OldSchool", "Pump", "EccentricOnly", "Echo")
    val mode: String = "OldSchool",
    // Echo-specific configuration
    val eccentricLoad: Int = 100,
    val echoLevel: Int = 2,
    val progressionKg: Float = 0f,
    val restSeconds: Int = 60,
    val notes: String = "",
    // Optional duration in seconds for duration-based sets
    val duration: Int? = null
)

/**
 * Room entity for weekly programs
 */
@Entity(tableName = "weekly_programs")
data class WeeklyProgramEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String? = null,
    val isActive: Boolean = false,
    val lastUsed: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Room entity for program days - links programs to routines by day of week
 */
@Entity(
    tableName = "program_days",
    foreignKeys = [
        ForeignKey(
            entity = WeeklyProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("programId"), Index("routineId")]
)
data class ProgramDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val programId: String,
    val dayOfWeek: Int, // 1=Monday, 7=Sunday (Calendar.MONDAY format)
    val routineId: String
)

/**
 * Embedded relation for weekly program with its days
 */
data class WeeklyProgramWithDays(
    @Embedded val program: WeeklyProgramEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "programId"
    )
    val days: List<ProgramDayEntity>
)
