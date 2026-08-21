package com.ninthlevel.phoenix.data.local.seed

import androidx.sqlite.db.SupportSQLiteDatabase
import com.ninthlevel.phoenix.data.local.seed.ExerciseIdRemapper.LegacyExercise
import com.ninthlevel.phoenix.data.local.seed.ExerciseIdRemapper.PersonalRecordSnapshot


/**
 * v26 → v27: drop catalogue/media tables after remapping persisted exercise ids onto the slim seed.
 */
object ExerciseCatalogMigration {

    const val REMAP_TABLE = "exercise_id_remap"

    fun migrate26to27(db: SupportSQLiteDatabase) {
        val legacy = readLegacyExercises(db)
        val oldToNew = ExerciseIdRemapper.buildOldToNewIdMap(legacy)
        val metadata = ExerciseIdRemapper.mergeLegacyMetadata(legacy, oldToNew)

        remapPersonalRecords(db, oldToNew)
        remapIdColumn(db, "workout_sessions", oldToNew)
        remapIdColumn(db, "routine_exercises", oldToNew)

        db.execSQL("DROP TABLE IF EXISTS exercise_videos")
        db.execSQL("DROP TABLE IF EXISTS exercises")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `muscleGroups` TEXT NOT NULL,
              `equipment` TEXT NOT NULL, `defaultCableConfig` TEXT NOT NULL, `aliases` TEXT NOT NULL,
              `isFavorite` INTEGER NOT NULL, `timesPerformed` INTEGER NOT NULL, `lastPerformed` INTEGER, PRIMARY KEY(`id`))
            """.trimIndent()
        )

        insertSeedExercises(db, metadata)
        writeRemapTable(db, oldToNew)
    }

    private fun readLegacyExercises(db: SupportSQLiteDatabase): List<LegacyExercise> {
        val rows = mutableListOf<LegacyExercise>()
        db.query("SELECT id, name, aliases, isFavorite, timesPerformed, lastPerformed FROM exercises").use { cursor ->
            val idIdx = cursor.getColumnIndex("id")
            val nameIdx = cursor.getColumnIndex("name")
            val aliasesIdx = cursor.getColumnIndex("aliases")
            val favoriteIdx = cursor.getColumnIndex("isFavorite")
            val timesIdx = cursor.getColumnIndex("timesPerformed")
            val lastIdx = cursor.getColumnIndex("lastPerformed")
            if (idIdx < 0 || nameIdx < 0) return emptyList()
            while (cursor.moveToNext()) {
                rows.add(
                    LegacyExercise(
                        id = cursor.getString(idIdx),
                        name = cursor.getString(nameIdx).orEmpty(),
                        aliases = if (aliasesIdx >= 0 && !cursor.isNull(aliasesIdx)) {
                            cursor.getString(aliasesIdx).orEmpty()
                        } else {
                            ""
                        },
                        isFavorite = favoriteIdx >= 0 && cursor.getInt(favoriteIdx) != 0,
                        timesPerformed = if (timesIdx >= 0) cursor.getInt(timesIdx) else 0,
                        lastPerformed = if (lastIdx >= 0 && !cursor.isNull(lastIdx)) cursor.getLong(lastIdx) else null
                    )
                )
            }
        }
        return rows
    }

    private fun remapIdColumn(db: SupportSQLiteDatabase, table: String, oldToNew: Map<String, String>) {
        oldToNew.forEach { (oldId, newId) ->
            db.execSQL(
                "UPDATE $table SET exerciseId = ? WHERE exerciseId = ?",
                arrayOf(newId, oldId)
            )
        }
    }

    private fun remapPersonalRecords(db: SupportSQLiteDatabase, oldToNew: Map<String, String>) {
        if (oldToNew.isEmpty()) return
        val rows = mutableListOf<PersonalRecordSnapshot>()
        db.query(
            "SELECT id, exerciseId, weightPerCableKg, reps, timestamp, workoutMode, prType, volume FROM personal_records"
        ).use { cursor ->
            val idIdx = cursor.getColumnIndex("id")
            val exerciseIdx = cursor.getColumnIndex("exerciseId")
            if (idIdx < 0 || exerciseIdx < 0) return
            val weightIdx = cursor.getColumnIndex("weightPerCableKg")
            val repsIdx = cursor.getColumnIndex("reps")
            val timestampIdx = cursor.getColumnIndex("timestamp")
            val modeIdx = cursor.getColumnIndex("workoutMode")
            val typeIdx = cursor.getColumnIndex("prType")
            val volumeIdx = cursor.getColumnIndex("volume")
            while (cursor.moveToNext()) {
                rows.add(
                    PersonalRecordSnapshot(
                        id = cursor.getLong(idIdx),
                        exerciseId = cursor.getString(exerciseIdx),
                        weightPerCableKg = if (weightIdx >= 0) cursor.getFloat(weightIdx) else 0f,
                        reps = if (repsIdx >= 0) cursor.getInt(repsIdx) else 0,
                        timestamp = if (timestampIdx >= 0) cursor.getLong(timestampIdx) else 0L,
                        workoutMode = if (modeIdx >= 0) cursor.getString(modeIdx).orEmpty() else "",
                        prType = if (typeIdx >= 0) cursor.getString(typeIdx).orEmpty() else "",
                        volume = if (volumeIdx >= 0) cursor.getFloat(volumeIdx) else 0f
                    )
                )
            }
        }

        val merged = ExerciseIdRemapper.mergePersonalRecords(rows, oldToNew)
        db.execSQL("DELETE FROM personal_records")
        val insert = db.compileStatement(
            """
            INSERT INTO personal_records
            (id, exerciseId, weightPerCableKg, reps, timestamp, workoutMode, prType, volume)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        )
        merged.forEach { row ->
            insert.clearBindings()
            insert.bindLong(1, row.id)
            insert.bindString(2, row.exerciseId)
            insert.bindDouble(3, row.weightPerCableKg.toDouble())
            insert.bindLong(4, row.reps.toLong())
            insert.bindLong(5, row.timestamp)
            insert.bindString(6, row.workoutMode)
            insert.bindString(7, row.prType)
            insert.bindDouble(8, row.volume.toDouble())
            insert.executeInsert()
        }
        insert.close()
    }

    private fun insertSeedExercises(db: SupportSQLiteDatabase, metadata: Map<String, LegacyExercise>) {
        val insert = db.compileStatement(
            """
            INSERT INTO exercises
            (id, name, muscleGroups, equipment, defaultCableConfig, aliases, isFavorite, timesPerformed, lastPerformed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        )
        DefaultExercises.all.forEach { exercise ->
            val meta = metadata[exercise.id]
            insert.clearBindings()
            insert.bindString(1, exercise.id)
            insert.bindString(2, exercise.name)
            insert.bindString(3, exercise.muscleGroups)
            insert.bindString(4, exercise.equipment)
            insert.bindString(5, exercise.defaultCableConfig)
            insert.bindString(6, exercise.aliases)
            insert.bindLong(7, if (meta?.isFavorite == true) 1L else 0L)
            insert.bindLong(8, (meta?.timesPerformed ?: 0).toLong())
            val lastPerformed = meta?.lastPerformed
            if (lastPerformed == null) {
                insert.bindNull(9)
            } else {
                insert.bindLong(9, lastPerformed)
            }
            insert.executeInsert()
        }
        insert.close()
    }

    private fun writeRemapTable(db: SupportSQLiteDatabase, oldToNew: Map<String, String>) {
        db.execSQL("DROP TABLE IF EXISTS $REMAP_TABLE")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $REMAP_TABLE (oldId TEXT PRIMARY KEY NOT NULL, newId TEXT NOT NULL)"
        )
        if (oldToNew.isEmpty()) return
        val insert = db.compileStatement(
            "INSERT INTO $REMAP_TABLE (oldId, newId) VALUES (?, ?)"
        )
        oldToNew.forEach { (oldId, newId) ->
            insert.clearBindings()
            insert.bindString(1, oldId)
            insert.bindString(2, newId)
            insert.executeInsert()
        }
        insert.close()
    }

    fun readRemapTable(db: SupportSQLiteDatabase): Map<String, String> {
        val exists = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(REMAP_TABLE)
        ).use { cursor -> cursor.moveToFirst() }
        if (!exists) return emptyMap()

        val map = linkedMapOf<String, String>()
        db.query("SELECT oldId, newId FROM $REMAP_TABLE").use { cursor ->
            val oldIdx = cursor.getColumnIndex("oldId")
            val newIdx = cursor.getColumnIndex("newId")
            if (oldIdx < 0 || newIdx < 0) return emptyMap()
            while (cursor.moveToNext()) {
                map[cursor.getString(oldIdx)] = cursor.getString(newIdx)
            }
        }
        return map
    }
}
