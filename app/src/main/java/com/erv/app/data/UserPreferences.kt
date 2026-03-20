package com.erv.app.data

import com.erv.app.cardio.CardioDistanceUnit
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_prefs")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class BodyWeightUnit {
    LB, KG
}

class UserPreferences(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val BODY_WEIGHT_VALUE = stringPreferencesKey("body_weight_value")
        val BODY_WEIGHT_UNIT = stringPreferencesKey("body_weight_unit")
        val CARDIO_DISTANCE_UNIT = stringPreferencesKey("cardio_distance_unit")
        val WEIGHT_TRAINING_LOAD_UNIT = stringPreferencesKey("weight_training_load_unit")
        val SELECTED_GOAL_IDS = stringPreferencesKey("selected_goal_ids")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.THEME]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME] = mode.name
        }
    }

    /** For MET-based cardio calorie estimate; null if unset. */
    val fallbackBodyWeightKg: Flow<Double?> = context.dataStore.data.map { prefs ->
        parseWeightKg(prefs[Keys.BODY_WEIGHT_VALUE], prefs[Keys.BODY_WEIGHT_UNIT])
    }

    val bodyWeightValue: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.BODY_WEIGHT_VALUE].orEmpty()
    }

    val bodyWeightUnit: Flow<BodyWeightUnit> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.BODY_WEIGHT_UNIT]) {
            "KG" -> BodyWeightUnit.KG
            else -> BodyWeightUnit.LB
        }
    }

    suspend fun setFallbackBodyWeight(rawValue: String, unit: BodyWeightUnit) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BODY_WEIGHT_VALUE] = rawValue.trim()
            prefs[Keys.BODY_WEIGHT_UNIT] = unit.name
        }
    }

    /** Cardio log / summaries / distance fields; default miles. */
    val cardioDistanceUnit: Flow<CardioDistanceUnit> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.CARDIO_DISTANCE_UNIT]) {
            "KILOMETERS", "KM" -> CardioDistanceUnit.KILOMETERS
            else -> CardioDistanceUnit.MILES
        }
    }

    suspend fun setCardioDistanceUnit(unit: CardioDistanceUnit) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CARDIO_DISTANCE_UNIT] = when (unit) {
                CardioDistanceUnit.KILOMETERS -> "KM"
                CardioDistanceUnit.MILES -> "MILES"
            }
        }
    }

    /** Bar/load when logging weight-training sets (stored on disk as kg). */
    val weightTrainingLoadUnit: Flow<BodyWeightUnit> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.WEIGHT_TRAINING_LOAD_UNIT]) {
            "LB" -> BodyWeightUnit.LB
            else -> BodyWeightUnit.KG
        }
    }

    suspend fun setWeightTrainingLoadUnit(unit: BodyWeightUnit) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEIGHT_TRAINING_LOAD_UNIT] = unit.name
        }
    }

    /** User-selected goals from [AllUserGoalOptions]; empty until they configure on Edit goals. */
    val selectedGoalIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        parseGoalIdsFromStorage(prefs[Keys.SELECTED_GOAL_IDS])
    }

    suspend fun setSelectedGoalIds(ids: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_GOAL_IDS] = encodeGoalIdsForStorage(ids)
        }
    }
}

private fun parseWeightKg(raw: String?, unitKey: String?): Double? {
    val v = raw?.trim()?.toDoubleOrNull() ?: return null
    if (v <= 0) return null
    return when (unitKey) {
        "KG" -> v
        else -> v * 0.453592
    }
}
