package com.example.vitruvianredux.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for workout history
 *
 * Version history:
 * - v22: Added aliases and defaultCableConfig to exercises, isTutorial to exercise_videos
 * - v21: Added exerciseName to WorkoutSessionEntity
 * - v20: Added isAMRAP to routine_exercises for AMRAP workout mode
 * - v19: Schema cleanup - forces fresh DB creation to fix SQL DEFAULT inconsistencies
 * - v18: Added perSetRestTime boolean flag to routine_exercises for toggleable per-set rest time feature
 * - v17: Added setRestSeconds (JSON array) to routine_exercises for per-set rest time configuration
 * - v16: Added routineSessionId and routineName to workout_sessions for grouping routine sets in history
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
    version = 22,  // Added aliases, defaultCableConfig to exercises, isTutorial to exercise_videos
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun connectionLogDao(): ConnectionLogDao
}
