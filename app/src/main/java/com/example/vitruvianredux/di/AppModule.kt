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
        .addMigrations(MIGRATION_1_2)
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
