package com.ninthlevel.phoenix.data.local.seed

import com.ninthlevel.phoenix.data.local.WorkoutDatabase
import com.ninthlevel.phoenix.data.preferences.PreferencesManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the one-shot v26→v27 exercise-id remap to DataStore keys, then drops the helper table.
 */
@Singleton
class ExerciseIdRemapApplier @Inject constructor(
    private val database: WorkoutDatabase,
    private val preferencesManager: PreferencesManager
) {
    suspend fun applyPendingDataStoreRemap() {
        val sqlite = database.openHelper.writableDatabase
        val oldToNew = ExerciseCatalogMigration.readRemapTable(sqlite)
        if (oldToNew.isEmpty()) {
            sqlite.execSQL("DROP TABLE IF EXISTS ${ExerciseCatalogMigration.REMAP_TABLE}")
            return
        }
        try {
            preferencesManager.remapExerciseIds(oldToNew)
            sqlite.execSQL("DROP TABLE IF EXISTS ${ExerciseCatalogMigration.REMAP_TABLE}")
            Timber.d("Remapped ${oldToNew.size} catalogue exercise ids in saved defaults")
        } catch (e: Exception) {
            Timber.e(e, "Failed to remap single-exercise defaults")
        }
    }
}
