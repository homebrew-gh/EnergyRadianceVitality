package com.erv.app.data

import com.erv.app.cardio.CardioDistanceUnit
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

enum class TemperatureUnit {
    FAHRENHEIT, CELSIUS
}

class UserPreferences(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val BODY_WEIGHT_VALUE = stringPreferencesKey("body_weight_value")
        val BODY_WEIGHT_UNIT = stringPreferencesKey("body_weight_unit")
        val CARDIO_DISTANCE_UNIT = stringPreferencesKey("cardio_distance_unit")
        val WEIGHT_TRAINING_LOAD_UNIT = stringPreferencesKey("weight_training_load_unit")
        val SELECTED_GOAL_IDS = stringPreferencesKey("selected_goal_ids")
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val WORKOUT_BUBBLE_ENABLED = booleanPreferencesKey("workout_bubble_enabled")
        val WEIGHT_LIVE_FGS_DISCLOSURE_SEEN = booleanPreferencesKey("weight_live_fgs_disclosure_seen")
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

    val temperatureUnit: Flow<TemperatureUnit> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.TEMPERATURE_UNIT]) {
            "CELSIUS" -> TemperatureUnit.CELSIUS
            else -> TemperatureUnit.FAHRENHEIT
        }
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TEMPERATURE_UNIT] = unit.name
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

    /**
     * When true (default), live weight workout notifications may show a bubble on API 30+.
     * Ongoing notification remains whenever a live workout is active.
     */
    val workoutBubbleEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.WORKOUT_BUBBLE_ENABLED] ?: true
    }

    suspend fun setWorkoutBubbleEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WORKOUT_BUBBLE_ENABLED] = enabled
        }
    }

    /** User has acknowledged the foreground-service / notification disclosure for live weight workouts. */
    val weightLiveWorkoutFgsDisclosureSeen: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.WEIGHT_LIVE_FGS_DISCLOSURE_SEEN] == true
    }

    suspend fun setWeightLiveWorkoutFgsDisclosureSeen(seen: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEIGHT_LIVE_FGS_DISCLOSURE_SEEN] = seen
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
