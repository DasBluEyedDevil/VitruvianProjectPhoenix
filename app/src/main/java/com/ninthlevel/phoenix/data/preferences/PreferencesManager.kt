package com.ninthlevel.phoenix.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ninthlevel.phoenix.domain.model.CableConfiguration
import com.ninthlevel.phoenix.domain.model.EccentricLoad
import com.ninthlevel.phoenix.domain.model.EchoLevel
import com.ninthlevel.phoenix.domain.model.ProgramMode
import com.ninthlevel.phoenix.domain.model.UserPreferences
import com.ninthlevel.phoenix.domain.model.WeightUnit
import com.ninthlevel.phoenix.domain.model.WorkoutType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Workout mode identifiers for serialization.
 * Using stable string identifiers instead of enum names or display names for forward compatibility.
 */
object WorkoutModeId {
    const val OLD_SCHOOL = "old_school"
    const val PUMP = "pump"
    const val TUT = "tut"
    const val TUT_BEAST = "tut_beast"
    const val ECCENTRIC_ONLY = "eccentric_only"
    const val ECHO = "echo"

    fun fromWorkoutType(workoutType: WorkoutType): String = when (workoutType) {
        is WorkoutType.Program -> when (workoutType.mode) {
            ProgramMode.OldSchool -> OLD_SCHOOL
            ProgramMode.Pump -> PUMP
            ProgramMode.TUT -> TUT
            ProgramMode.TUTBeast -> TUT_BEAST
            ProgramMode.EccentricOnly -> ECCENTRIC_ONLY
        }
        is WorkoutType.Echo -> ECHO
    }

    fun toWorkoutType(
        modeId: String,
        eccentricLoad: EccentricLoad = EccentricLoad.LOAD_100,
        echoLevel: EchoLevel = EchoLevel.HARDER
    ): WorkoutType = when (modeId) {
        OLD_SCHOOL -> WorkoutType.Program(ProgramMode.OldSchool)
        PUMP -> WorkoutType.Program(ProgramMode.Pump)
        TUT -> WorkoutType.Program(ProgramMode.TUT)
        TUT_BEAST -> WorkoutType.Program(ProgramMode.TUTBeast)
        ECCENTRIC_ONLY -> WorkoutType.Program(ProgramMode.EccentricOnly)
        ECHO -> WorkoutType.Echo(echoLevel, eccentricLoad)
        else -> {
            Timber.w("Unknown workout mode ID: $modeId, defaulting to OldSchool")
            WorkoutType.Program(ProgramMode.OldSchool)
        }
    }
}

/**
 * Saved settings for Just Lift mode.
 * These are stored and recalled automatically when entering Just Lift mode.
 *
 * @param version Schema version for future migration support
 * @param workoutModeId Stable identifier for workout mode (see WorkoutModeId)
 * @param weightPerCableKg Weight per cable in kilograms (always stored in KG)
 * @param weightChangePerRep Progression/regression value in KG
 * @param eccentricLoadPercentage Eccentric load percentage (0, 50, 75, 100, 125, 150)
 * @param echoLevelValue Echo level ordinal (0=Hard, 1=Harder, 2=Hardest, 3=Epic)
 */
@Serializable
data class JustLiftDefaults(
    val version: Int = 1,
    val workoutModeId: String = WorkoutModeId.OLD_SCHOOL,
    val weightPerCableKg: Float = 10f,
    val weightChangePerRep: Int = 0,
    val eccentricLoadPercentage: Int = 100,
    val echoLevelValue: Int = 1
) {
    init {
        require(weightPerCableKg > 0) { "Weight must be positive" }
        require(eccentricLoadPercentage in listOf(0, 50, 75, 100, 125, 150)) {
            "Invalid eccentric load percentage: $eccentricLoadPercentage"
        }
        require(echoLevelValue in 0..3) { "Invalid echo level value: $echoLevelValue" }
    }

    fun toWorkoutType(): WorkoutType {
        val eccentricLoad = EccentricLoad.entries.find { it.percentage == eccentricLoadPercentage }
            ?: EccentricLoad.LOAD_100
        val echoLevel = EchoLevel.entries.find { it.levelValue == echoLevelValue }
            ?: EchoLevel.HARDER
        return WorkoutModeId.toWorkoutType(workoutModeId, eccentricLoad, echoLevel)
    }

    fun getEccentricLoad(): EccentricLoad =
        EccentricLoad.entries.find { it.percentage == eccentricLoadPercentage }
            ?: EccentricLoad.LOAD_100

    fun getEchoLevel(): EchoLevel =
        EchoLevel.entries.find { it.levelValue == echoLevelValue }
            ?: EchoLevel.HARDER
}

/**
 * Saved settings for a specific exercise in Single Exercise mode.
 * Keyed by exerciseId + cableConfig (e.g., "exercise_123_DOUBLE")
 *
 * @param version Schema version for future migration support
 * @param exerciseId Unique identifier of the exercise
 * @param cableConfig Cable configuration name ("SINGLE", "DOUBLE", or "EITHER")
 * @param workoutModeId Stable identifier for workout mode (see WorkoutModeId)
 */
@Serializable
data class SingleExerciseDefaults(
    val version: Int = 1,
    val exerciseId: String,
    val cableConfig: String,
    val workoutModeId: String = WorkoutModeId.OLD_SCHOOL,
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
) {
    init {
        require(exerciseId.isNotBlank()) { "Exercise ID must not be blank" }
        require(weightPerCableKg >= 0) { "Weight must be non-negative" }
        require(setReps.isNotEmpty()) { "Must have at least one set" }
        require(progressionKg in -50f..50f) { "Progression must be between -50kg and 50kg" }
        require(duration == null || duration > 0) { "Duration must be positive if set" }
        require(setWeightsPerCableKg.isEmpty() || setWeightsPerCableKg.size == setReps.size) {
            "setWeightsPerCableKg must be empty or match setReps size"
        }
        require(setRestSeconds.isEmpty() || setRestSeconds.size == setReps.size) {
            "setRestSeconds must be empty or match setReps size"
        }
    }

    fun toWorkoutType(): WorkoutType {
        val eccentricLoad = EccentricLoad.entries.find { it.percentage == eccentricLoadPercentage }
            ?: EccentricLoad.LOAD_100
        val echoLevel = EchoLevel.entries.find { it.levelValue == echoLevelValue }
            ?: EchoLevel.HARDER
        return WorkoutModeId.toWorkoutType(workoutModeId, eccentricLoad, echoLevel)
    }

    fun getCableConfiguration(): CableConfiguration =
        CableConfiguration.entries.find { it.name == cableConfig }
            ?: CableConfiguration.DOUBLE

    fun getEccentricLoad(): EccentricLoad =
        EccentricLoad.entries.find { it.percentage == eccentricLoadPercentage }
            ?: EccentricLoad.LOAD_100

    fun getEchoLevel(): EchoLevel =
        EchoLevel.entries.find { it.levelValue == echoLevelValue }
            ?: EchoLevel.HARDER
}

/**
 * Manager for user preferences using DataStore
 */
@Singleton
class PreferencesManager @Inject constructor(
    private val context: Context
) {
    /** Mutex to prevent concurrent save race conditions */
    private val exerciseDefaultsSaveMutex = Mutex()

    /** Maximum number of exercise defaults before triggering cleanup warning */
    private val MAX_EXERCISE_DEFAULTS_SIZE = 200

    private val WEIGHT_UNIT_KEY = stringPreferencesKey("weight_unit")
    private val AUTOPLAY_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("autoplay_enabled")
    private val STOP_AT_TOP_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("stop_at_top")
    private val BEEPS_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("beeps_enabled")

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
            val beepsEnabled = preferences[BEEPS_ENABLED_KEY] ?: true

            UserPreferences(
                weightUnit = weightUnit,
                autoplayEnabled = autoplayEnabled,
                stopAtTop = stopAtTop,
                beepsEnabled = beepsEnabled
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
     * Set the beeps enabled preference (workout audio cues)
     */
    suspend fun setBeepsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BEEPS_ENABLED_KEY] = enabled
        }
        Timber.d("Beeps enabled preference set to: $enabled")
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
        Timber.d("Just Lift defaults saved: mode=${defaults.workoutModeId}, weight=${defaults.weightPerCableKg}kg, progression=${defaults.weightChangePerRep}")
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
     *
     * Uses mutex to prevent concurrent save race conditions
     */
    suspend fun saveSingleExerciseDefaults(defaults: SingleExerciseDefaults) {
        exerciseDefaultsSaveMutex.withLock {
            val key = getExerciseKey(defaults.exerciseId, defaults.cableConfig)
            context.dataStore.edit { preferences ->
                // Load existing map
                val existingJson = preferences[SINGLE_EXERCISE_DEFAULTS_KEY]
                val existingMap: MutableMap<String, SingleExerciseDefaults> = if (existingJson != null) {
                    try {
                        Json.decodeFromString<Map<String, SingleExerciseDefaults>>(existingJson).toMutableMap()
                    } catch (e: Exception) {
                        // Data corruption or schema change - log error but preserve raw data
                        Timber.e(e, "Failed to parse existing exercise defaults - raw data preserved, saving only new entry")
                        // Instead of discarding all data, we'll just save the new entry
                        // The old corrupted data will be overwritten with just this one entry
                        // This is a data recovery strategy: lose old entries but don't crash
                        mutableMapOf()
                    }
                } else {
                    mutableMapOf()
                }

                // Update with new defaults
                existingMap[key] = defaults

                // Monitor map size for potential cleanup
                if (existingMap.size > MAX_EXERCISE_DEFAULTS_SIZE) {
                    Timber.w("Exercise defaults map has ${existingMap.size} entries - consider implementing cleanup strategy")
                }

                // Save back
                preferences[SINGLE_EXERCISE_DEFAULTS_KEY] = Json.encodeToString(existingMap)
            }
            Timber.d("Single Exercise defaults saved: exerciseId=${defaults.exerciseId}, cableConfig=${defaults.cableConfig}, mode=${defaults.workoutModeId}")
        }
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
