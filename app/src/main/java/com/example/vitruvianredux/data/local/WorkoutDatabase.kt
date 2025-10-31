package com.example.vitruvianredux.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for workout history
 *
 * Version history:
 * - v7: Added exercise detail fields to RoutineExerciseEntity (muscleGroup, equipment, defaultCableConfig)
 *       to support Exercise data class instead of enum
 * - v6: Added ExerciseEntity and ExerciseVideoEntity for exercise library
 */
@Database(
    entities = [
        WorkoutSessionEntity::class,
        WorkoutMetricEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        ExerciseEntity::class,
        ExerciseVideoEntity::class,
        PersonalRecordEntity::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun personalRecordDao(): PersonalRecordDao
}
