package com.example.vitruvianredux.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.vitruvianredux.domain.model.EccentricLoad
import com.example.vitruvianredux.domain.model.EchoLevel
import com.example.vitruvianredux.domain.model.UserPreferences
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.WorkoutMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Saved settings for Just Lift mode
 * These are stored and recalled automatically when entering Just Lift mode
 */
@Serializable
data class JustLiftDefaults(
    val workoutMode: String = "OldSchool",  // Stored as enum name
    val weightPerCableKg: Float = 10f,
    val weightChangePerRep: Int = 0,
    val eccentricLoadPercentage: Int = 100,  // EccentricLoad.LOAD_100
    val echoLevelValue: Int = 1  // EchoLevel.HARDER
)

/**
 * Saved settings for a specific exercise in Single Exercise mode
 * Keyed by exerciseId + cableConfig (e.g., "bicep_curl_DOUBLE")
 */
@Serializable
data class SingleExerciseDefaults(
    val exerciseId: String,
    val cableConfig: String,  // "SINGLE" or "DOUBLE"
    val workoutMode: String = "OldSchool",
    val setReps: List<Int?> = listOf(10, 10, 10),
    val weightPerCableKg: Float = 20f,
    val setWeightsPerCableKg: List<Float> = emptyList(),
    val progressionKg: Float = 0f,
    val setRestSeconds: List<Int> = listOf(60, 60, 60),
    val perSetRestTime: Boolean = false,
    val eccentricLoadPercentage: Int = 100,
    val echoLevelValue: Int = 1,
    val duration: Int? = null,
    val isAMRAP: Boolean = false
)

/**
 * Manager for user preferences using DataStore
 */
@Singleton
class PreferencesManager @Inject constructor(
    private val context: Context
) {
    private val WEIGHT_UNIT_KEY = stringPreferencesKey("weight_unit")
    private val AUTOPLAY_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("autoplay_enabled")
    private val STOP_AT_TOP_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("stop_at_top")
    private val ENABLE_VIDEO_PLAYBACK_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("enable_video_playback")

    /**
     * Flow of user preferences
     */
    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .map { preferences ->
            val weightUnitString = preferences[WEIGHT_UNIT_KEY]
            val weightUnit = try {
                weightUnitString?.let { WeightUnit.valueOf(it) } ?: WeightUnit.KG
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Invalid weight unit in preferences: $weightUnitString, defaulting to KG")
                WeightUnit.KG
            }
            val autoplayEnabled = preferences[AUTOPLAY_ENABLED_KEY] ?: true
            val stopAtTop = preferences[STOP_AT_TOP_KEY] ?: false
            val enableVideoPlayback = preferences[ENABLE_VIDEO_PLAYBACK_KEY] ?: true

            UserPreferences(
                weightUnit = weightUnit,
                autoplayEnabled = autoplayEnabled,
                stopAtTop = stopAtTop,
                enableVideoPlayback = enableVideoPlayback
            )
        }

    /**
     * Set the weight unit preference
     */
    suspend fun setWeightUnit(unit: WeightUnit) {
        context.dataStore.edit { preferences ->
            preferences[WEIGHT_UNIT_KEY] = unit.name
        }
        Timber.d("Weight unit preference set to: ${unit.name}")
    }

    /**
     * Set the autoplay enabled preference
     */
    suspend fun setAutoplayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTOPLAY_ENABLED_KEY] = enabled
        }
        Timber.d("Autoplay enabled preference set to: $enabled")
    }

    /**
     * Set the stop at top preference
     */
    suspend fun setStopAtTop(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[STOP_AT_TOP_KEY] = enabled
        }
        Timber.d("Stop at top preference set to: $enabled")
    }

    /**
     * Set the enable video playback preference
     */
    suspend fun setEnableVideoPlayback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_VIDEO_PLAYBACK_KEY] = enabled
        }
        Timber.d("Enable video playback preference set to: $enabled")
    }

    // ========== Just Lift Defaults ==========

    private val JUST_LIFT_DEFAULTS_KEY = stringPreferencesKey("just_lift_defaults")

    /**
     * Save Just Lift mode defaults
     * Called when a Just Lift workout is completed
     */
    suspend fun saveJustLiftDefaults(defaults: JustLiftDefaults) {
        context.dataStore.edit { preferences ->
            preferences[JUST_LIFT_DEFAULTS_KEY] = Json.encodeToString(defaults)
        }
        Timber.d("Just Lift defaults saved: mode=${defaults.workoutMode}, weight=${defaults.weightPerCableKg}kg, progression=${defaults.weightChangePerRep}")
    }

    /**
     * Load Just Lift mode defaults
     * Returns null if no defaults have been saved yet
     */
    suspend fun getJustLiftDefaults(): JustLiftDefaults? {
        return try {
            context.dataStore.data.firstOrNull()?.let { preferences ->
                preferences[JUST_LIFT_DEFAULTS_KEY]?.let { json ->
                    Json.decodeFromString<JustLiftDefaults>(json)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load Just Lift defaults, returning null")
            null
        }
    }

    // ========== Single Exercise Defaults (per exercise+equipment) ==========

    private val SINGLE_EXERCISE_DEFAULTS_KEY = stringPreferencesKey("single_exercise_defaults_map")

    /**
     * Generate a unique key for exercise + cable config combination
     */
    private fun getExerciseKey(exerciseId: String, cableConfig: String): String {
        return "${exerciseId}_${cableConfig}"
    }

    /**
     * Save Single Exercise mode defaults for a specific exercise+equipment combination
     * Called when a Single Exercise workout is completed (not routine-based)
     */
    suspend fun saveSingleExerciseDefaults(defaults: SingleExerciseDefaults) {
        val key = getExerciseKey(defaults.exerciseId, defaults.cableConfig)
        context.dataStore.edit { preferences ->
            // Load existing map
            val existingJson = preferences[SINGLE_EXERCISE_DEFAULTS_KEY]
            val existingMap: MutableMap<String, SingleExerciseDefaults> = if (existingJson != null) {
                try {
                    Json.decodeFromString<Map<String, SingleExerciseDefaults>>(existingJson).toMutableMap()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse existing exercise defaults, starting fresh")
                    mutableMapOf()
                }
            } else {
                mutableMapOf()
            }

            // Update with new defaults
            existingMap[key] = defaults

            // Save back
            preferences[SINGLE_EXERCISE_DEFAULTS_KEY] = Json.encodeToString(existingMap)
        }
        Timber.d("Single Exercise defaults saved: exerciseId=${defaults.exerciseId}, cableConfig=${defaults.cableConfig}, mode=${defaults.workoutMode}")
    }

    /**
     * Load Single Exercise mode defaults for a specific exercise+equipment combination
     * Returns null if no defaults have been saved for this combination
     */
    suspend fun getSingleExerciseDefaults(exerciseId: String, cableConfig: String): SingleExerciseDefaults? {
        val key = getExerciseKey(exerciseId, cableConfig)
        return try {
            context.dataStore.data.firstOrNull()?.let { preferences ->
                preferences[SINGLE_EXERCISE_DEFAULTS_KEY]?.let { json ->
                    val map = Json.decodeFromString<Map<String, SingleExerciseDefaults>>(json)
                    map[key]
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load Single Exercise defaults for $key, returning null")
            null
        }
    }

    /**
     * Clear all saved Single Exercise defaults
     * Useful for testing or resetting to system defaults
     */
    suspend fun clearAllSingleExerciseDefaults() {
        context.dataStore.edit { preferences ->
            preferences.remove(SINGLE_EXERCISE_DEFAULTS_KEY)
        }
        Timber.d("All Single Exercise defaults cleared")
    }

    /**
     * Clear Just Lift defaults
     * Useful for testing or resetting to system defaults
     */
    suspend fun clearJustLiftDefaults() {
        context.dataStore.edit { preferences ->
            preferences.remove(JUST_LIFT_DEFAULTS_KEY)
        }
        Timber.d("Just Lift defaults cleared")
    }
}
