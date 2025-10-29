package com.example.vitruvianredux.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.vitruvianredux.data.local.WorkoutDatabase
import com.example.vitruvianredux.data.local.WorkoutDao
import com.example.vitruvianredux.data.preferences.PreferencesManager
import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.data.repository.BleRepositoryImpl
import com.example.vitruvianredux.data.repository.WorkoutRepository
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
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
}
