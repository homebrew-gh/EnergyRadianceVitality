package com.erv.app.data

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
}

private fun parseWeightKg(raw: String?, unitKey: String?): Double? {
    val v = raw?.trim()?.toDoubleOrNull() ?: return null
    if (v <= 0) return null
    return when (unitKey) {
        "KG" -> v
        else -> v * 0.453592
    }
}
