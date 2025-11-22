package com.example.vitruvianredux.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing an exercise within a routine with all configuration settings.
 */
@Entity(
    tableName = "routine_exercises",
    indices = [Index(value = ["routineId"])]
)
data class RoutineExerciseEntity(
    @PrimaryKey
    val id: String,

    val routineId: String,

    val exerciseName: String,

    val exerciseMuscleGroup: String,

    val exerciseEquipment: String = "",

    val exerciseDefaultCableConfig: String,

    val exerciseId: String? = null,

    val cableConfig: String,

    val orderIndex: Int,

    val setReps: String,

    val weightPerCableKg: Float,

    val setWeights: String = "",

    val mode: String = "OldSchool",

    val eccentricLoad: Int = 100,

    val echoLevel: Int = 1,

    val progressionKg: Float = 0f,

    val restSeconds: Int = 60,

    val duration: Int? = null,

    val setRestSeconds: String = "",

    val perSetRestTime: Boolean = false,

    val isAMRAP: Boolean = false
)
