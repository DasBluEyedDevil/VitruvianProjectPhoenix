package com.example.vitruvianredux.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for exercises from the exercise library
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val created: String,
    val muscleGroups: String, // comma-separated
    val muscles: String, // comma-separated
    val equipment: String, // comma-separated
    val movement: String?,
    val sidedness: String?,
    val grip: String?,
    val gripWidth: String?,
    val minRepRange: Float?,
    val popularity: Float,
    val archived: Boolean,
    val isFavorite: Boolean = false,
    val timesPerformed: Int = 0,
    val lastPerformed: Long? = null
)

/**
 * Room entity for exercise video demonstrations
 */
@Entity(
    tableName = "exercise_videos",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("exerciseId")]
)
data class ExerciseVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: String,
    val angle: String, // FRONT, SIDE, or ISOMETRIC
    val videoUrl: String,
    val thumbnailUrl: String
)
