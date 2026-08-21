package com.ninthlevel.phoenix.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Types of personal records tracked
 */
enum class PRType {
    MAX_WEIGHT,  // Highest weight in a single rep (strength PR)
    MAX_VOLUME   // Highest weight × reps in a single set (volume PR)
}

/**
 * Database entity for personal records (PRs) per exercise
 * Tracks two types of PRs for each exercise and workout mode combination:
 * - MAX_WEIGHT: The heaviest weight lifted (regardless of reps)
 * - MAX_VOLUME: The highest total volume (weight × reps)
 */
@Entity(
    tableName = "personal_records",
    indices = [
        Index(value = ["exerciseId", "workoutMode", "prType"], unique = true)
    ]
)
data class PersonalRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exerciseId: String,
    val weightPerCableKg: Float,
    val reps: Int,
    val timestamp: Long,
    val workoutMode: String,
    val prType: String = PRType.MAX_WEIGHT.name,  // Default for migration compatibility
    val volume: Float = 0f  // weight × reps, stored for easy querying
)
