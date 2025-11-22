package com.example.vitruvianredux.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an exercise in the workout library.
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey
    val id: String,

    val name: String,

    val description: String,

    val created: String,

    val muscleGroups: String,

    val muscles: String,

    val equipment: String,

    val movement: String? = null,

    val sidedness: String? = null,

    val grip: String? = null,

    val gripWidth: String? = null,

    val minRepRange: Float? = null,

    val popularity: Float,

    val archived: Boolean,

    val isFavorite: Boolean = false,

    val timesPerformed: Int = 0,

    val lastPerformed: Long? = null,

    val aliases: String = "",

    val defaultCableConfig: String = "DOUBLE"
)
