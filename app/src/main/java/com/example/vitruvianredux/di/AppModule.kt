package com.example.vitruvianredux.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.vitruvianredux.data.local.WorkoutDatabase
import com.example.vitruvianredux.data.local.WorkoutDao
import com.example.vitruvianredux.data.local.ExerciseDao
import com.example.vitruvianredux.data.local.ExerciseImporter
import com.example.vitruvianredux.data.local.PersonalRecordDao
import com.example.vitruvianredux.data.local.ConnectionLogDao
import com.example.vitruvianredux.data.logger.ConnectionLogger
import com.example.vitruvianredux.data.preferences.PreferencesManager
import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.data.repository.BleRepositoryImpl
import com.example.vitruvianredux.data.repository.WorkoutRepository
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.data.repository.ExerciseRepositoryImpl
import com.example.vitruvianredux.data.repository.PersonalRecordRepository
import com.example.vitruvianredux.domain.usecase.RepCounterFromMachine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Migration from version 1 to 2: Add routine tables
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create routines table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS routines (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    createdAt INTEGER NOT NULL,
                    lastUsed INTEGER,
                    useCount INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            // Create routine_exercises table with foreign key
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS routine_exercises (
                    id TEXT PRIMARY KEY NOT NULL,
                    routineId TEXT NOT NULL,
                    exerciseName TEXT NOT NULL,
                    orderIndex INTEGER NOT NULL,
                    sets INTEGER NOT NULL,
                    reps INTEGER NOT NULL,
                    weightPerCableKg REAL NOT NULL,
                    progressionKg REAL NOT NULL DEFAULT 0.0,
                    restSeconds INTEGER NOT NULL DEFAULT 60,
                    notes TEXT NOT NULL DEFAULT '',
                    FOREIGN KEY(routineId) REFERENCES routines(id) ON DELETE CASCADE
                )
            """.trimIndent())

            // Create index on routineId for performance
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS index_routine_exercises_routineId
                ON routine_exercises(routineId)
            """.trimIndent())
        }
    }

    /**
     * Migration from version 2 to 3: Add cable configuration to exercises
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add cableConfig column with default value "DOUBLE" for existing rows
            database.execSQL("""
                ALTER TABLE routine_exercises
                ADD COLUMN cableConfig TEXT NOT NULL DEFAULT 'DOUBLE'
            """.trimIndent())
        }
    }

    /**
     * Migration from version 3 to 4: Replace sets/reps with setReps array
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add setReps column with default value "10,10,10"
            database.execSQL("""
                ALTER TABLE routine_exercises
                ADD COLUMN setReps TEXT NOT NULL DEFAULT '10,10,10'
            """.trimIndent())

            // Populate setReps from existing sets and reps columns
            // Creates a comma-separated string like "10,10,10" for 3 sets of 10 reps
            database.execSQL("""
                UPDATE routine_exercises
                SET setReps = (
                    SELECT GROUP_CONCAT(reps, ',')
                    FROM (
                        SELECT reps
                        FROM (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) numbers
                        JOIN routine_exercises re ON re.id = routine_exercises.id
                        WHERE numbers.n <= re.sets
                    )
                )
            """.trimIndent())

            // Note: We don't drop the old 'sets' and 'reps' columns to maintain backwards compatibility
            // Room will ignore them since they're not in the entity definition
        }
    }

    /**
     * Migration from version 4 to 5: Add equipment type
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add equipment column with default value 'LONG_BAR'
            database.execSQL("""
                ALTER TABLE routine_exercises
                ADD COLUMN equipment TEXT NOT NULL DEFAULT 'LONG_BAR'
            """.trimIndent())
        }
    }

    /**
     * Migration from version 5 to 6: Add exercise library tables
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create exercises table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS exercises (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    created TEXT NOT NULL,
                    muscleGroups TEXT NOT NULL,
                    muscles TEXT NOT NULL,
                    equipment TEXT NOT NULL,
                    movement TEXT,
                    sidedness TEXT,
                    grip TEXT,
                    gripWidth TEXT,
                    minRepRange REAL,
                    popularity REAL NOT NULL,
                    archived INTEGER NOT NULL,
                    isFavorite INTEGER NOT NULL DEFAULT 0,
                    timesPerformed INTEGER NOT NULL DEFAULT 0,
                    lastPerformed INTEGER
                )
            """.trimIndent())

            // Create exercise_videos table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS exercise_videos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    exerciseId TEXT NOT NULL,
                    angle TEXT NOT NULL,
                    videoUrl TEXT NOT NULL,
                    thumbnailUrl TEXT NOT NULL,
                    FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON DELETE CASCADE
                )
            """.trimIndent())

            // Create index on exerciseId for performance
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS index_exercise_videos_exerciseId
                ON exercise_videos(exerciseId)
            """.trimIndent())
        }
    }

    /**
     * Migration from version 6 to 7: Add exercise detail fields to routine_exercises
     * Supports Exercise data class (previously enum)
     */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add exercise detail columns with default values
            database.execSQL("""
                ALTER TABLE routine_exercises
                ADD COLUMN exerciseMuscleGroup TEXT NOT NULL DEFAULT 'Full Body'
            """.trimIndent())

            database.execSQL("""
                ALTER TABLE routine_exercises
                ADD COLUMN exerciseEquipment TEXT NOT NULL DEFAULT ''
            """.trimIndent())

            database.execSQL("""
                ALTER TABLE routine_exercises
                ADD COLUMN exerciseDefaultCableConfig TEXT NOT NULL DEFAULT 'DOUBLE'
            """.trimIndent())
        }
    }

    /**
     * Migration from version 8 to 9: Rename progressionKg to progressionRegressionKg in workout_sessions
     * and add personal_records table
     */
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. Fix workout_sessions table: rename progressionKg ? progressionRegressionKg
            // Create new table with correct schema
            database.execSQL("""
                CREATE TABLE `workout_sessions_new` (
                    `id` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `mode` TEXT NOT NULL,
                    `reps` INTEGER NOT NULL,
                    `weightPerCableKg` REAL NOT NULL,
                    `progressionRegressionKg` REAL NOT NULL,
                    `duration` INTEGER NOT NULL,
                    `totalReps` INTEGER NOT NULL,
                    `warmupReps` INTEGER NOT NULL,
                    `workingReps` INTEGER NOT NULL,
                    `isJustLift` INTEGER NOT NULL,
                    `stopAtTop` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())

            // 2. Copy data from old table (progressionKg ? progressionRegressionKg)
            database.execSQL("""
                INSERT INTO `workout_sessions_new` (
                    id, timestamp, mode, reps, weightPerCableKg, progressionRegressionKg,
                    duration, totalReps, warmupReps, workingReps, isJustLift, stopAtTop
                )
                SELECT
                    id, timestamp, mode, reps, weightPerCableKg, progressionKg,
                    duration, totalReps, warmupReps, workingReps, isJustLift, stopAtTop
                FROM `workout_sessions`
            """.trimIndent())

            // 3. Drop old table
            database.execSQL("DROP TABLE `workout_sessions`")

            // 4. Rename new table
            database.execSQL("ALTER TABLE `workout_sessions_new` RENAME TO `workout_sessions`")

            // 5. Create personal_records table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `personal_records` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `exerciseId` TEXT NOT NULL,
                    `weightPerCableKg` REAL NOT NULL,
                    `reps` INTEGER NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `workoutMode` TEXT NOT NULL
                )
            """.trimIndent())

            // 6. Create unique index on exerciseId and workoutMode
            database.execSQL("""
                CREATE UNIQUE INDEX `index_personal_records_exerciseId_workoutMode`
                ON `personal_records` (`exerciseId`, `workoutMode`)
            """.trimIndent())
        }
    }

    /**
     * Migration from version 9 to 10: Add exerciseId column to routine_exercises
     * Stores exercise library ID for loading videos/thumbnails
     */
    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add exerciseId column with NULL default (for existing rows)
            database.execSQL("""
                ALTER TABLE routine_exercises
                ADD COLUMN exerciseId TEXT DEFAULT NULL
            """.trimIndent())
        }
    }

    /**
     * Migration from version 10 to 11: Add weekly programs and program days tables
     * Supports weekly program scheduling with routines assigned to specific days
     */
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create weekly_programs table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS weekly_programs (
                    id TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    notes TEXT,
                    isActive INTEGER NOT NULL DEFAULT 0,
                    lastUsed INTEGER,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())

            // Create program_days table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS program_days (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    programId TEXT NOT NULL,
                    dayOfWeek INTEGER NOT NULL,
                    routineId TEXT NOT NULL,
                    FOREIGN KEY(programId) REFERENCES weekly_programs(id) ON DELETE CASCADE,
                    FOREIGN KEY(routineId) REFERENCES routines(id) ON DELETE CASCADE
                )
            """.trimIndent())

            // Create indices
            database.execSQL("CREATE INDEX IF NOT EXISTS index_program_days_programId ON program_days(programId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_program_days_routineId ON program_days(routineId)")
        }
    }

    /**
     * Migration from version 11 to 12: Add per-set weights, mode, eccentricLoad, echoLevel, duration
     * to routine_exercises
     */
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add setWeights column to store comma-separated per-set weights
            database.execSQL(
                """
                ALTER TABLE routine_exercises
                ADD COLUMN setWeights TEXT NOT NULL DEFAULT ''
                """.trimIndent()
            )

            // Add mode column for selected workout mode per exercise
            database.execSQL(
                """
                ALTER TABLE routine_exercises
                ADD COLUMN mode TEXT NOT NULL DEFAULT 'OldSchool'
                """.trimIndent()
            )

            // Add eccentricLoad (percentage) and echoLevel (difficulty level) columns
            database.execSQL(
                """
                ALTER TABLE routine_exercises
                ADD COLUMN eccentricLoad INTEGER NOT NULL DEFAULT 100
                """.trimIndent()
            )
            database.execSQL(
                """
                ALTER TABLE routine_exercises
                ADD COLUMN echoLevel INTEGER NOT NULL DEFAULT 2
                """.trimIndent()
            )

            // Add duration column for duration-based sets (in seconds)
            database.execSQL(
                """
                ALTER TABLE routine_exercises
                ADD COLUMN duration INTEGER DEFAULT NULL
                """.trimIndent()
            )
        }
    }

    /**
     * Migration from version 12 to 13: Add Echo mode fields to workout_sessions
     * Adds eccentricLoad and echoLevel to persist Echo mode configuration in workout history
     */
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add eccentricLoad column (percentage: 0, 50, 75, 100, 125, 150)
            database.execSQL(
                """
                ALTER TABLE workout_sessions
                ADD COLUMN eccentricLoad INTEGER NOT NULL DEFAULT 100
                """.trimIndent()
            )

            // Add echoLevel column (difficulty: 1=Hard, 2=Harder, 3=Hardest, 4=Epic)
            database.execSQL(
                """
                ALTER TABLE workout_sessions
                ADD COLUMN echoLevel INTEGER NOT NULL DEFAULT 2
                """.trimIndent()
            )
        }
    }

    /**
     * Migration from version 13 to 14: Add connection_logs table for Bluetooth debugging
     */
    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS connection_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    eventType TEXT NOT NULL,
                    level TEXT NOT NULL,
                    deviceAddress TEXT,
                    deviceName TEXT,
                    message TEXT NOT NULL,
                    details TEXT,
                    metadata TEXT
                )
            """.trimIndent())

            // Create index on timestamp for efficient queries
            database.execSQL("CREATE INDEX IF NOT EXISTS index_connection_logs_timestamp ON connection_logs(timestamp)")
        }
    }

    /**
     * Migration from version 14 to 15: Add exerciseId to workout_sessions for PR tracking
     * This enables tracking which exercise was performed in each workout session
     */
    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add exerciseId column to workout_sessions
            database.execSQL("""
                ALTER TABLE workout_sessions
                ADD COLUMN exerciseId TEXT DEFAULT NULL
            """.trimIndent())
        }
    }

    /**
     * Migration from version 7 to 8: Fix routine_exercises schema
     * Removes old columns (sets, reps, equipment) using create/copy/drop/rename strategy
     */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. Create new table with correct schema (13 columns)
            database.execSQL("""
                CREATE TABLE `routine_exercises_new` (
                    `id` TEXT NOT NULL,
                    `routineId` TEXT NOT NULL,
                    `exerciseName` TEXT NOT NULL,
                    `exerciseMuscleGroup` TEXT NOT NULL,
                    `exerciseEquipment` TEXT NOT NULL,
                    `exerciseDefaultCableConfig` TEXT NOT NULL,
                    `cableConfig` TEXT NOT NULL,
                    `orderIndex` INTEGER NOT NULL,
                    `setReps` TEXT NOT NULL,
                    `weightPerCableKg` REAL NOT NULL,
                    `progressionKg` REAL NOT NULL,
                    `restSeconds` INTEGER NOT NULL,
                    `notes` TEXT NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())

            // 2. Copy data (IFNULL handles NULL values from failed v7 migration)
            database.execSQL("""
                INSERT INTO `routine_exercises_new` (
                    id, routineId, exerciseName, exerciseMuscleGroup, exerciseEquipment, exerciseDefaultCableConfig,
                    cableConfig, orderIndex, setReps, weightPerCableKg, progressionKg, restSeconds, notes
                )
                SELECT
                    id,
                    routineId,
                    exerciseName,
                    IFNULL(exerciseMuscleGroup, ''),
                    IFNULL(exerciseEquipment, ''),
                    IFNULL(exerciseDefaultCableConfig, ''),
                    cableConfig,
                    orderIndex,
                    setReps,
                    weightPerCableKg,
                    progressionKg,
                    restSeconds,
                    notes
                FROM `routine_exercises`
            """.trimIndent())

            // 3. Drop old table
            database.execSQL("DROP TABLE `routine_exercises`")

            // 4. Rename new table
            database.execSQL("ALTER TABLE `routine_exercises_new` RENAME TO `routine_exercises`")

            // 5. Recreate index
            database.execSQL("CREATE INDEX `index_routine_exercises_routineId` ON `routine_exercises` (`routineId`)")
        }
    }

    @Provides
    @Singleton
    fun provideConnectionLogDao(database: WorkoutDatabase): ConnectionLogDao {
        return database.connectionLogDao()
    }

    @Provides
    @Singleton
    fun provideConnectionLogger(connectionLogDao: ConnectionLogDao): ConnectionLogger {
        return ConnectionLogger(connectionLogDao)
    }

    @Provides
    @Singleton
    fun provideBleRepository(
        @ApplicationContext context: Context,
        connectionLogger: ConnectionLogger
    ): BleRepository {
        return BleRepositoryImpl(context, connectionLogger)
    }

    @Provides
    @Singleton
    fun provideWorkoutDatabase(
        @ApplicationContext context: Context
    ): WorkoutDatabase {
        return Room.databaseBuilder(
            context,
            WorkoutDatabase::class.java,
            "vitruvian_workout_db"
        )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
        .build()
    }

    @Provides
    @Singleton
    fun provideWorkoutDao(database: WorkoutDatabase): WorkoutDao {
        return database.workoutDao()
    }

    @Provides
    @Singleton
    fun provideWorkoutRepository(
        workoutDao: WorkoutDao,
        personalRecordDao: PersonalRecordDao
    ): WorkoutRepository {
        return WorkoutRepository(workoutDao, personalRecordDao)
    }

    @Provides
    fun provideRepCounterFromMachine(): RepCounterFromMachine {
        return RepCounterFromMachine()
    }

    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager {
        return PreferencesManager(context)
    }
    
    @Provides
    @Singleton
    fun provideExerciseDao(database: WorkoutDatabase): ExerciseDao {
        return database.exerciseDao()
    }
    
    @Provides
    @Singleton
    fun provideExerciseImporter(
        @ApplicationContext context: Context,
        exerciseDao: ExerciseDao
    ): ExerciseImporter {
        return ExerciseImporter(context, exerciseDao)
    }
    
    @Provides
    @Singleton
    fun provideExerciseRepository(
        exerciseDao: ExerciseDao,
        exerciseImporter: ExerciseImporter
    ): ExerciseRepository {
        return ExerciseRepositoryImpl(exerciseDao, exerciseImporter)
    }

    @Provides
    @Singleton
    fun providePersonalRecordDao(database: WorkoutDatabase): PersonalRecordDao {
        return database.personalRecordDao()
    }

    @Provides
    @Singleton
    fun providePersonalRecordRepository(personalRecordDao: PersonalRecordDao): PersonalRecordRepository {
        return PersonalRecordRepository(personalRecordDao)
    }
}
