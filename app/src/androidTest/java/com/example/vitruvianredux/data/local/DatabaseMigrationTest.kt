package com.example.vitruvianredux.data.local

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vitruvianredux.di.AppModule
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Database migration tests for WorkoutDatabase
 *
 * These tests verify that database migrations preserve data correctly.
 * Following TDD: Write test → Verify it fails → Implement migration → Verify it passes
 *
 * Note: Since exportSchema = false, we test migrations by creating databases
 * and verifying the migration executes without errors.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val TEST_DB_NAME = "migration-test"
    private var database: WorkoutDatabase? = null

    @After
    fun closeDb() {
        database?.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB_NAME)
    }

    /**
     * Test migration from v16 to v17: Add per-set rest time support
     *
     * Migration should:
     * 1. Add new setRestSeconds column to routine_exercises table
     * 2. Populate setRestSeconds with JSON array based on existing restSeconds value
     * 3. Preserve all existing data
     *
     * This test verifies the migration can be applied successfully by:
     * - Creating a v16 database with test data
     * - Applying the MIGRATION_16_17
     * - Verifying the setRestSeconds column exists and contains correct data
     */
    @Test
    @Throws(IOException::class)
    fun migration_16_17_adds_setRestSeconds_to_RoutineExercise() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Step 1: Create a database with v16 schema using a callback
        val createV16Schema = object : androidx.room.RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // Create minimal v16 schema (routine_exercises table without setRestSeconds)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS routine_exercises (
                        id TEXT PRIMARY KEY NOT NULL,
                        routineId TEXT NOT NULL,
                        exerciseName TEXT NOT NULL,
                        exerciseMuscleGroup TEXT NOT NULL DEFAULT '',
                        exerciseEquipment TEXT NOT NULL DEFAULT '',
                        exerciseDefaultCableConfig TEXT NOT NULL,
                        exerciseId TEXT DEFAULT NULL,
                        cableConfig TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        setReps TEXT NOT NULL,
                        weightPerCableKg REAL NOT NULL,
                        setWeights TEXT NOT NULL DEFAULT '',
                        mode TEXT NOT NULL DEFAULT 'OldSchool',
                        eccentricLoad INTEGER NOT NULL DEFAULT 100,
                        echoLevel INTEGER NOT NULL DEFAULT 1,
                        progressionKg REAL NOT NULL DEFAULT 0.0,
                        restSeconds INTEGER NOT NULL DEFAULT 60,
                        notes TEXT NOT NULL DEFAULT '',
                        duration INTEGER DEFAULT NULL
                    )
                """.trimIndent())

                // Insert test data with v16 schema (no setRestSeconds column)
                db.execSQL("""
                    INSERT INTO routine_exercises
                    (id, routineId, exerciseName, exerciseMuscleGroup, exerciseEquipment,
                     exerciseDefaultCableConfig, exerciseId, cableConfig, orderIndex,
                     setReps, weightPerCableKg, setWeights, mode, eccentricLoad, echoLevel,
                     progressionKg, restSeconds, notes, duration)
                    VALUES
                    ('test-id-1', 'routine-1', 'Bench Press', 'Chest', 'Bench',
                     'DOUBLE', 'exercise-1', 'DOUBLE', 0,
                     '10,10,10', 20.0, '', 'OldSchool', 100, 1,
                     0.0, 60, '', NULL),
                    ('test-id-2', 'routine-1', 'Squat', 'Legs', 'None',
                     'DOUBLE', 'exercise-2', 'DOUBLE', 1,
                     '8,8,8,8', 30.0, '', 'OldSchool', 100, 1,
                     0.0, 90, '', NULL)
                """.trimIndent())
            }
        }

        // Create temporary v16 database
        val tempDb = Room.databaseBuilder(context, WorkoutDatabase::class.java, TEST_DB_NAME)
            .addCallback(createV16Schema)
            .fallbackToDestructiveMigration()
            .build()

        // Access database to trigger onCreate
        tempDb.workoutDao()
        tempDb.close()

        // Step 2: Reopen with MIGRATION_16_17 to test migration
        database = Room.databaseBuilder(context, WorkoutDatabase::class.java, TEST_DB_NAME)
            .addMigrations(AppModule.MIGRATION_16_17)
            .build()

        // Access database to trigger migration
        database!!.workoutDao()

        // Step 3: Verify migration results
        val db = database!!.openHelper.writableDatabase

        // Verify setRestSeconds column exists and contains correct data
        val cursor1 = db.query("SELECT setRestSeconds, setReps, restSeconds FROM routine_exercises WHERE id = 'test-id-1'")
        assertThat(cursor1.moveToFirst()).isTrue()
        val setRestSeconds1 = cursor1.getString(0)
        val setReps1 = cursor1.getString(1)
        val restSeconds1 = cursor1.getInt(2)
        cursor1.close()

        // Verify: 3 sets → [60,60,60]
        assertThat(setRestSeconds1).isEqualTo("[60,60,60]")
        assertThat(setReps1).isEqualTo("10,10,10")
        assertThat(restSeconds1).isEqualTo(60)

        // Verify second exercise
        val cursor2 = db.query("SELECT setRestSeconds, setReps, restSeconds FROM routine_exercises WHERE id = 'test-id-2'")
        assertThat(cursor2.moveToFirst()).isTrue()
        val setRestSeconds2 = cursor2.getString(0)
        val setReps2 = cursor2.getString(1)
        val restSeconds2 = cursor2.getInt(2)
        cursor2.close()

        // Verify: 4 sets → [90,90,90,90]
        assertThat(setRestSeconds2).isEqualTo("[90,90,90,90]")
        assertThat(setReps2).isEqualTo("8,8,8,8")
        assertThat(restSeconds2).isEqualTo(90)
    }
}
