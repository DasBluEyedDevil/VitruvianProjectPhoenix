package com.example.vitruvianredux.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for workout history
 */
@Database(
    entities = [
        WorkoutSessionEntity::class,
        WorkoutMetricEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}
