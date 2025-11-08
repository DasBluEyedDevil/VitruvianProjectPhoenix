package com.example.vitruvianredux.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for workout history
 *
 * Version history:
 * - v15: Added exerciseId to workout_sessions for PR tracking
 * - v14: Added ConnectionLogEntity for Bluetooth connection debugging
 * - v13: Added eccentricLoad and echoLevel to workout_sessions for Echo mode persistence
 * - v12: Added setWeights, mode, eccentricLoad, echoLevel to routine_exercises
 * - v11: Added WeeklyProgramEntity and ProgramDayEntity for weekly program scheduling
 * - v10: Added exerciseId to routine_exercises for exercise library integration
 * - v9: Renamed progressionKg to progressionRegressionKg in workout_sessions, added personal_records
 * - v8: Schema cleanup for routine_exercises
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
        PersonalRecordEntity::class,
        WeeklyProgramEntity::class,
        ProgramDayEntity::class,
        ConnectionLogEntity::class
    ],
    version = 15,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun connectionLogDao(): ConnectionLogDao
}
