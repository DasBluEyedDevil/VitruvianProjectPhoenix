package com.ninthlevel.phoenix.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the built-in exercise list.
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val muscleGroups: String,
    val equipment: String,
    val defaultCableConfig: String = "DOUBLE",
    val aliases: String = "",
    val isFavorite: Boolean = false,
    val timesPerformed: Int = 0,
    val lastPerformed: Long? = null
)
