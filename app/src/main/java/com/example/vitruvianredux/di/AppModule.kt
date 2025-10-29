package com.example.vitruvianredux.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.vitruvianredux.data.local.WorkoutDatabase
import com.example.vitruvianredux.data.local.WorkoutDao
import com.example.vitruvianredux.data.local.ExerciseDao
import com.example.vitruvianredux.data.local.ExerciseImporter
import com.example.vitruvianredux.data.preferences.PreferencesManager
import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.data.repository.BleRepositoryImpl
import com.example.vitruvianredux.data.repository.WorkoutRepository
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.data.repository.ExerciseRepositoryImpl
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
    fun provideBleRepository(
        @ApplicationContext context: Context
    ): BleRepository {
        return BleRepositoryImpl(context)
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
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
        .build()
    }

    @Provides
    @Singleton
    fun provideWorkoutDao(database: WorkoutDatabase): WorkoutDao {
        return database.workoutDao()
    }

    @Provides
    @Singleton
    fun provideWorkoutRepository(workoutDao: WorkoutDao): WorkoutRepository {
        return WorkoutRepository(workoutDao)
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
}
