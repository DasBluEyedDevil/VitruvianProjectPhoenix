package com.example.vitruvianredux.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing video tutorials/demonstrations for exercises.
 */
@Entity(
    tableName = "exercise_videos",
    indices = [Index(value = ["exerciseId"])]
)
data class ExerciseVideoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val exerciseId: String,

    val angle: String,

    val videoUrl: String,

    val thumbnailUrl: String,

    val isTutorial: Boolean = false
)
