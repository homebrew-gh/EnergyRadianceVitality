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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_prefs")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class BodyWeightUnit {
    LB, KG
}

enum class TemperatureUnit {
    FAHRENHEIT, CELSIUS
}

/** Guided stretching “Next stretch” TTS voice; actual voices depend on the device TTS engine. */
enum class StretchGuidedTtsVoice {
    /** Follow the engine default for the current language. */
    SYSTEM_DEFAULT,

    /** Prefer a voice the engine labels as female, when available. */
    PREFER_FEMALE,

    /** Prefer a voice the engine labels as male, when available. */
    PREFER_MALE
}

/** How “Share Workout” uploads the route PNG when a media server URL is set. */
enum class WorkoutMediaUploadBackend {
    /** NIP-96 multipart + NIP-98 (kind 27235). */
    NIP96,

    /** Blossom PUT `/upload` + kind 24242 (e.g. Primal `blossom.primal.net`). */
    BLOSSOM
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
        val CARDIO_GPS_RECORDING_PREFERRED = booleanPreferencesKey("cardio_gps_recording_preferred")
        val CARDIO_GPS_TRACK_RETAIN_ON_DEVICE = booleanPreferencesKey("cardio_gps_track_retain_on_device")
        val NIP96_MEDIA_SERVER_ORIGIN = stringPreferencesKey("nip96_media_server_origin")
        val BLOSSOM_PUBLIC_SERVER_ORIGIN = stringPreferencesKey("blossom_public_server_origin")
        val BLOSSOM_PRIVATE_SERVER_ORIGIN = stringPreferencesKey("blossom_private_server_origin")
        val MEDIA_KEYS_SPLIT_V1 = booleanPreferencesKey("media_keys_split_v1")
        val ATTACH_ROUTE_IMAGE_WORKOUT_NOSTR = booleanPreferencesKey("attach_route_image_workout_nostr")
        val WORKOUT_MEDIA_UPLOAD_BACKEND = stringPreferencesKey("workout_media_upload_backend")
        val GYM_MEMBERSHIP = booleanPreferencesKey("gym_membership")
        val OWNED_EQUIPMENT_JSON_V1 = stringPreferencesKey("owned_equipment_json_v1")
        val LIVE_WEIGHT_WORKOUT_DRAFT_JSON_V1 = stringPreferencesKey("live_weight_workout_draft_json_v1")
        val STRETCH_GUIDED_TTS_VOICE = stringPreferencesKey("stretch_guided_tts_voice")
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

    /** Cardio log / summaries / distance fields; default miles (and feet for elevation labels). */
    val cardioDistanceUnit: Flow<CardioDistanceUnit> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.CARDIO_DISTANCE_UNIT]?.uppercase()) {
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

    /** Writes miles if no cardio distance key exists yet (first launch). Does not change km/mi if already set. */
    suspend fun ensureCardioDistanceDefaultMiles() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.CARDIO_DISTANCE_UNIT] == null) {
                prefs[Keys.CARDIO_DISTANCE_UNIT] = "MILES"
            }
        }
    }

    /**
     * One-time migration: older builds stored the Blossom base URL in [nip96MediaServerOrigin].
     * Copies that value into [blossomPublicServerOrigin] when upload backend is Blossom and public is still empty.
     */
    suspend fun ensureMediaKeysSplitV1() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.MEDIA_KEYS_SPLIT_V1] == true) return@edit
            val legacy = prefs[Keys.NIP96_MEDIA_SERVER_ORIGIN]?.trim().orEmpty()
            val backend = prefs[Keys.WORKOUT_MEDIA_UPLOAD_BACKEND]?.uppercase()
            if (legacy.isNotEmpty() && backend == WorkoutMediaUploadBackend.BLOSSOM.name) {
                if (prefs[Keys.BLOSSOM_PUBLIC_SERVER_ORIGIN].isNullOrBlank()) {
                    prefs[Keys.BLOSSOM_PUBLIC_SERVER_ORIGIN] = legacy
                }
            }
            prefs[Keys.MEDIA_KEYS_SPLIT_V1] = true
        }
    }

    /** Bar/load when logging weight-training sets (stored on disk as kg). Default lb when unset. */
    val weightTrainingLoadUnit: Flow<BodyWeightUnit> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.WEIGHT_TRAINING_LOAD_UNIT]) {
            "KG" -> BodyWeightUnit.KG
            else -> BodyWeightUnit.LB
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

    /**
     * When true (default), outdoor walk / run / hike / ruck timer sessions may record GPS if the user
     * grants location permission. Does not affect manual logs or indoor (treadmill) activities.
     */
    val cardioGpsRecordingPreferred: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.CARDIO_GPS_RECORDING_PREFERRED] ?: true
    }

    suspend fun setCardioGpsRecordingPreferred(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CARDIO_GPS_RECORDING_PREFERRED] = enabled
        }
    }

    /**
     * When true (default), completed sessions may persist [com.erv.app.cardio.CardioSession.gpsTrack] locally.
     * When false, point lists are not stored after save (summaries and optional route image URL still can be).
     */
    val cardioGpsTrackRetainOnDevice: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.CARDIO_GPS_TRACK_RETAIN_ON_DEVICE] ?: true
    }

    suspend fun setCardioGpsTrackRetainOnDevice(retain: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CARDIO_GPS_TRACK_RETAIN_ON_DEVICE] = retain
        }
    }

    val workoutMediaUploadBackend: Flow<WorkoutMediaUploadBackend> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.WORKOUT_MEDIA_UPLOAD_BACKEND]?.uppercase()) {
            "BLOSSOM" -> WorkoutMediaUploadBackend.BLOSSOM
            else -> WorkoutMediaUploadBackend.NIP96
        }
    }

    suspend fun setWorkoutMediaUploadBackend(backend: WorkoutMediaUploadBackend) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WORKOUT_MEDIA_UPLOAD_BACKEND] = backend.name
        }
    }

    /** HTTPS origin for NIP-96 only (`/.well-known/nostr/nip96.json`). */
    val nip96MediaServerOrigin: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.NIP96_MEDIA_SERVER_ORIGIN].orEmpty()
    }

    suspend fun setNip96MediaServerOrigin(origin: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NIP96_MEDIA_SERVER_ORIGIN] = origin.trim()
        }
    }

    /**
     * Public Blossom base URL (`PUT …/upload`). Used for workout route images linked from kind 1 notes.
     * Not for sensitive media — use [blossomPrivateServerOrigin] for that.
     */
    val blossomPublicServerOrigin: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.BLOSSOM_PUBLIC_SERVER_ORIGIN].orEmpty()
    }

    suspend fun setBlossomPublicServerOrigin(origin: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BLOSSOM_PUBLIC_SERVER_ORIGIN] = origin.trim()
        }
    }

    /**
     * Private Blossom base URL for personal media (e.g. future body-progress photos). ERV does not use this
     * for Nostr posts or kind 1 content.
     */
    val blossomPrivateServerOrigin: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.BLOSSOM_PRIVATE_SERVER_ORIGIN].orEmpty()
    }

    suspend fun setBlossomPrivateServerOrigin(origin: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BLOSSOM_PRIVATE_SERVER_ORIGIN] = origin.trim()
        }
    }

    /** One-shot read for post-login import of **public** Blossom (kind 10063). */
    suspend fun peekBlossomPublicServerOrigin(): String =
        context.dataStore.data.map { prefs -> prefs[Keys.BLOSSOM_PUBLIC_SERVER_ORIGIN].orEmpty() }.first()

    /**
     * When true (default), “Share Workout” uploads the watermarked route PNG (if GPS was recorded) and adds
     * [imeta] + URL to the kind 1 note. Set false in Settings for text-only shares.
     */
    val attachRouteImageToWorkoutNostrShare: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ATTACH_ROUTE_IMAGE_WORKOUT_NOSTR] ?: true
    }

    suspend fun setAttachRouteImageToWorkoutNostrShare(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ATTACH_ROUTE_IMAGE_WORKOUT_NOSTR] = enabled
        }
    }

    /** Commercial or other gym access; useful for AI / workout planning. */
    val gymMembership: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.GYM_MEMBERSHIP] == true
    }

    suspend fun setGymMembership(member: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GYM_MEMBERSHIP] = member
        }
    }

    /** Equipment the user owns at home (or elsewhere), tagged by modality. */
    val ownedEquipment: Flow<List<OwnedEquipmentItem>> = context.dataStore.data.map { prefs ->
        decodeOwnedEquipmentList(prefs[Keys.OWNED_EQUIPMENT_JSON_V1])
    }

    suspend fun setOwnedEquipment(items: List<OwnedEquipmentItem>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OWNED_EQUIPMENT_JSON_V1] = encodeOwnedEquipmentList(items)
        }
    }

    /** JSON snapshot of the in-progress weight live workout; null when none. */
    val liveWeightWorkoutDraftJson: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LIVE_WEIGHT_WORKOUT_DRAFT_JSON_V1]
    }

    suspend fun setLiveWeightWorkoutDraftJson(json: String?) {
        context.dataStore.edit { prefs ->
            if (json.isNullOrBlank()) {
                prefs.remove(Keys.LIVE_WEIGHT_WORKOUT_DRAFT_JSON_V1)
            } else {
                prefs[Keys.LIVE_WEIGHT_WORKOUT_DRAFT_JSON_V1] = json
            }
        }
    }

    val stretchGuidedTtsVoice: Flow<StretchGuidedTtsVoice> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.STRETCH_GUIDED_TTS_VOICE]?.uppercase()) {
            "PREFER_FEMALE" -> StretchGuidedTtsVoice.PREFER_FEMALE
            "PREFER_MALE" -> StretchGuidedTtsVoice.PREFER_MALE
            else -> StretchGuidedTtsVoice.SYSTEM_DEFAULT
        }
    }

    suspend fun setStretchGuidedTtsVoice(voice: StretchGuidedTtsVoice) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STRETCH_GUIDED_TTS_VOICE] = voice.name
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
