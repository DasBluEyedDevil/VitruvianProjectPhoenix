package com.example.vitruvianredux.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.vitruvianredux.domain.model.UserPreferences
import com.example.vitruvianredux.domain.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Manager for user preferences using DataStore
 */
@Singleton
class PreferencesManager @Inject constructor(
    private val context: Context
) {
    private val WEIGHT_UNIT_KEY = stringPreferencesKey("weight_unit")
    private val AUTOPLAY_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("autoplay_enabled")

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
            
            UserPreferences(weightUnit = weightUnit, autoplayEnabled = autoplayEnabled)
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
}
