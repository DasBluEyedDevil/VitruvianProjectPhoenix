package com.ninthlevel.phoenix.data.local.seed

import com.ninthlevel.phoenix.data.local.WorkoutDatabase
import com.ninthlevel.phoenix.data.preferences.PreferencesManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the v26→v27 exercise-id remap to DataStore keys.
 * Keeps [ExerciseCatalogMigration.REMAP_TABLE] so later backup imports can remap PR-only rows.
 */
@Singleton
class ExerciseIdRemapApplier @Inject constructor(
    private val database: WorkoutDatabase,
    private val preferencesManager: PreferencesManager
) {
    fun deviceRemap(): Map<String, String> {
        return ExerciseCatalogMigration.readRemapTable(database.openHelper.readableDatabase)
    }

    suspend fun applyPendingDataStoreRemap() {
        val oldToNew = deviceRemap()
        if (oldToNew.isEmpty()) return
        try {
            preferencesManager.remapExerciseIds(oldToNew)
            Timber.d("Remapped ${oldToNew.size} catalogue exercise ids in saved defaults")
        } catch (e: Exception) {
            Timber.e(e, "Failed to remap single-exercise defaults")
        }
    }
}
