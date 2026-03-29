package com.erv.app.data

import com.erv.app.cardio.CardioDistanceUnit
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_prefs")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class BodyWeightUnit {
    LB, KG
}

enum class TemperatureUnit {
    FAHRENHEIT, CELSIUS
}

/** Between-set rest during live weight workout (timer next to workout clock). */
enum class WeightLiveRestTimerMode {
    OFF,
    /** Start countdown when you tap Add set on an exercise. */
    AUTO,
    /** After Add set, tap Start next to the workout timer to begin the countdown. */
    MANUAL
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
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val BODY_WEIGHT_VALUE = stringPreferencesKey("body_weight_value")
        val BODY_WEIGHT_UNIT = stringPreferencesKey("body_weight_unit")
        val CARDIO_DISTANCE_UNIT = stringPreferencesKey("cardio_distance_unit")
        val WEIGHT_TRAINING_LOAD_UNIT = stringPreferencesKey("weight_training_load_unit")
        val SELECTED_GOAL_IDS = stringPreferencesKey("selected_goal_ids")
        val GOALS_JSON_V1 = stringPreferencesKey("goals_json_v1")
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
        val ENABLED_WEIGHT_EXERCISE_PACK_IDS_V1 = stringPreferencesKey("enabled_weight_exercise_pack_ids_v1")
        val LIVE_WEIGHT_WORKOUT_DRAFT_JSON_V1 = stringPreferencesKey("live_weight_workout_draft_json_v1")
        val LIVE_WEIGHT_WORKOUT_NOTIFICATION_SUPPRESSED_V1 =
            booleanPreferencesKey("live_weight_workout_notification_suppressed_v1")
        val STRETCH_GUIDED_TTS_VOICE = stringPreferencesKey("stretch_guided_tts_voice")
        /** MAC address of last paired BLE heart rate sensor (standard Heart Rate GATT service). */
        val BLE_HEART_RATE_DEVICE_ADDRESS = stringPreferencesKey("ble_heart_rate_device_address_v1")
        /** MAC address of preferred Cycling Speed and Cadence sensor for live cycling workouts. */
        val BLE_CSC_DEVICE_ADDRESS = stringPreferencesKey("ble_csc_device_address_v1")
        /** Saved BLE devices list for quick reconnects from Settings. */
        val BLE_SAVED_DEVICES_JSON_V1 = stringPreferencesKey("ble_saved_devices_json_v1")
        /** Wheel circumference used to convert CSC wheel revolutions into distance and speed. */
        val CYCLING_WHEEL_CIRCUMFERENCE_MM = intPreferencesKey("cycling_wheel_circumference_mm_v1")
        /** Optional max HR (bpm) for heart rate zone breakdown; empty = use workout peak as proxy. */
        val HEART_RATE_MAX_BPM = stringPreferencesKey("heart_rate_max_bpm_v1")
        /** Global BLE heart-rate strip above navigation; cardio screens hide it regardless. */
        val HEART_RATE_BANNER_EXPANDED = booleanPreferencesKey("heart_rate_banner_expanded_v1")
        /** User chose to use the app without creating a Nostr identity yet (local-only shell). */
        val USE_APP_WITHOUT_NOSTR_ACCOUNT_V1 = booleanPreferencesKey("use_app_without_nostr_account_v1")
        /** First-run setup / onboarding has been skipped or completed on this device. */
        val FIRST_RUN_SETUP_COMPLETED_V1 = booleanPreferencesKey("first_run_setup_completed_v1")
        /** Tracks whether this device has ever had a Nostr-backed ERV session. */
        val HAS_USED_NOSTR_IDENTITY_V1 = booleanPreferencesKey("has_used_nostr_identity_v1")
        /** Last known data relay URLs while signed in, used for later cleanup guidance. */
        val LAST_KNOWN_DATA_RELAYS_JSON_V1 = stringPreferencesKey("last_known_data_relays_json_v1")
        /** One-shot: dashboard Programs tile → Start stretch; consumed when Stretching opens. */
        val PROGRAM_DASHBOARD_STRETCH_LAUNCH_JSON_V1 = stringPreferencesKey("program_dashboard_stretch_launch_json_v1")
        /** One-shot: dashboard Programs tile → Start sauna/cold; consumed when Hot + Cold opens. */
        val PROGRAM_DASHBOARD_HEAT_COLD_LAUNCH_JSON_V1 = stringPreferencesKey("program_dashboard_heat_cold_launch_json_v1")
        /** One-shot: dashboard Programs tile → Start unified workout; consumed when Unified Workouts opens. */
        val PROGRAM_DASHBOARD_UNIFIED_ROUTINE_LAUNCH_JSON_V1 =
            stringPreferencesKey("program_dashboard_unified_routine_launch_json_v1")
        val WEIGHT_LIVE_REST_TIMER_MODE = stringPreferencesKey("weight_live_rest_timer_mode_v1")
        val WEIGHT_LIVE_REST_TIMER_SECONDS = intPreferencesKey("weight_live_rest_timer_seconds_v1")
        val WEIGHT_LIVE_REST_TIMER_COUNTDOWN_SOUND_ENABLED =
            booleanPreferencesKey("weight_live_rest_timer_countdown_sound_enabled_v1")
        val WEIGHT_LIVE_REST_TIMER_END_SOUND_ENABLED =
            booleanPreferencesKey("weight_live_rest_timer_end_sound_enabled_v1")
        /** Shown in Identity when using ERV without a Nostr key; not published to relays. */
        val LOCAL_PROFILE_DISPLAY_NAME_V1 = stringPreferencesKey("local_profile_display_name_v1")
        val LOCAL_PROFILE_PICTURE_URL_V1 = stringPreferencesKey("local_profile_picture_url_v1")
        val LOCAL_PROFILE_BIO_V1 = stringPreferencesKey("local_profile_bio_v1")
        val LAUNCH_PAD_TILE_ORDER_JSON_V1 = stringPreferencesKey("launch_pad_tile_order_json_v1")
        val LAUNCH_PAD_HIDDEN_TILES_JSON_V1 = stringPreferencesKey("launch_pad_hidden_tiles_json_v1")
        /**
         * When true (default), ERV does not publish NIP-65 kind 10002 relay list metadata.
         */
        val NEVER_PUBLISH_NIP65_RELAY_LIST_V1 = booleanPreferencesKey("never_publish_nip65_relay_list_v1")
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

    /**
     * Legacy fixed-goal selection kept for one-version migration into [goals].
     * New writes should use [setGoals].
     */
    val selectedGoalIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        parseGoalIdsFromStorage(prefs[Keys.SELECTED_GOAL_IDS])
    }

    suspend fun setSelectedGoalIds(ids: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_GOAL_IDS] = encodeGoalIdsForStorage(ids)
        }
    }

    /** User-authored weekly goals; migrates legacy goal ids on first read if needed. */
    val goals: Flow<List<UserGoalDefinition>> = context.dataStore.data.map { prefs ->
        decodeGoals(
            goalsJson = prefs[Keys.GOALS_JSON_V1],
            legacyGoalIds = prefs[Keys.SELECTED_GOAL_IDS],
        )
    }

    suspend fun setGoals(goals: List<UserGoalDefinition>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GOALS_JSON_V1] = json.encodeToString(ListSerializer(UserGoalDefinition.serializer()), goals)
            prefs[Keys.SELECTED_GOAL_IDS] = ""
        }
    }

    private fun decodeGoals(
        goalsJson: String?,
        legacyGoalIds: String?,
    ): List<UserGoalDefinition> {
        if (!goalsJson.isNullOrBlank()) {
            return runCatching {
                json.decodeFromString(ListSerializer(UserGoalDefinition.serializer()), goalsJson)
                    .map { goal ->
                        goal.copy(
                            title = goal.title.trim(),
                            target = goal.target.coerceAtLeast(1),
                        )
                    }
                    .filter { it.title.isNotBlank() }
            }.getOrDefault(emptyList())
        }
        return migrateLegacyGoalIds(parseGoalIdsFromStorage(legacyGoalIds))
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

    val weightLiveRestTimerMode: Flow<WeightLiveRestTimerMode> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.WEIGHT_LIVE_REST_TIMER_MODE]?.uppercase()) {
            "AUTO" -> WeightLiveRestTimerMode.AUTO
            "MANUAL" -> WeightLiveRestTimerMode.MANUAL
            else -> WeightLiveRestTimerMode.OFF
        }
    }

    val weightLiveRestTimerSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[Keys.WEIGHT_LIVE_REST_TIMER_SECONDS] ?: 90).coerceIn(10, 600)
    }

    val weightLiveRestTimerCountdownSoundEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.WEIGHT_LIVE_REST_TIMER_COUNTDOWN_SOUND_ENABLED] ?: true
    }

    val weightLiveRestTimerEndSoundEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.WEIGHT_LIVE_REST_TIMER_END_SOUND_ENABLED] ?: true
    }

    suspend fun setWeightLiveRestTimerMode(mode: WeightLiveRestTimerMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEIGHT_LIVE_REST_TIMER_MODE] = mode.name
        }
    }

    suspend fun setWeightLiveRestTimerSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEIGHT_LIVE_REST_TIMER_SECONDS] = seconds.coerceIn(10, 600)
        }
    }

    suspend fun setWeightLiveRestTimerCountdownSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEIGHT_LIVE_REST_TIMER_COUNTDOWN_SOUND_ENABLED] = enabled
        }
    }

    suspend fun setWeightLiveRestTimerEndSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEIGHT_LIVE_REST_TIMER_END_SOUND_ENABLED] = enabled
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

    /**
     * When true (default), ERV does not publish NIP-65 relay list metadata (kind 10002), so relay URLs are not
     * advertised on the network. Turn off only if you want other Nostr clients to discover your relay set.
     */
    val neverPublishNip65RelayList: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.NEVER_PUBLISH_NIP65_RELAY_LIST_V1] ?: true
    }

    suspend fun setNeverPublishNip65RelayList(neverPublish: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NEVER_PUBLISH_NIP65_RELAY_LIST_V1] = neverPublish
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

    /** Specialty exercise packs that should be visible in library pickers. */
    val enabledWeightExercisePackIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        decodeDelimitedIdSet(prefs[Keys.ENABLED_WEIGHT_EXERCISE_PACK_IDS_V1])
    }

    suspend fun setEnabledWeightExercisePackIds(ids: Set<String>) {
        context.dataStore.edit { prefs ->
            val encoded = encodeDelimitedIdSet(ids)
            if (encoded.isEmpty()) {
                prefs.remove(Keys.ENABLED_WEIGHT_EXERCISE_PACK_IDS_V1)
            } else {
                prefs[Keys.ENABLED_WEIGHT_EXERCISE_PACK_IDS_V1] = encoded
            }
        }
    }

    /** JSON snapshot of the in-progress weight live workout; null when none. */
    val liveWeightWorkoutDraftJson: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LIVE_WEIGHT_WORKOUT_DRAFT_JSON_V1]
    }

    val liveWeightWorkoutNotificationSuppressed: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.LIVE_WEIGHT_WORKOUT_NOTIFICATION_SUPPRESSED_V1] ?: false
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

    suspend fun setLiveWeightWorkoutNotificationSuppressed(suppressed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LIVE_WEIGHT_WORKOUT_NOTIFICATION_SUPPRESSED_V1] = suppressed
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

    val bleHeartRateDeviceAddress: Flow<String?> = context.dataStore.data.map { prefs ->
        normalizeBluetoothAddressOrNull(prefs[Keys.BLE_HEART_RATE_DEVICE_ADDRESS])
    }

    suspend fun setBleHeartRateDeviceAddress(address: String?) {
        context.dataStore.edit { prefs ->
            val normalized = normalizeBluetoothAddressOrNull(address)
            if (normalized == null) prefs.remove(Keys.BLE_HEART_RATE_DEVICE_ADDRESS)
            else prefs[Keys.BLE_HEART_RATE_DEVICE_ADDRESS] = normalized
        }
    }

    val bleCscDeviceAddress: Flow<String?> = context.dataStore.data.map { prefs ->
        normalizeBluetoothAddressOrNull(prefs[Keys.BLE_CSC_DEVICE_ADDRESS])
    }

    suspend fun setBleCscDeviceAddress(address: String?) {
        context.dataStore.edit { prefs ->
            val normalized = normalizeBluetoothAddressOrNull(address)
            if (normalized == null) prefs.remove(Keys.BLE_CSC_DEVICE_ADDRESS)
            else prefs[Keys.BLE_CSC_DEVICE_ADDRESS] = normalized
        }
    }

    val savedBleDevices: Flow<List<SavedBluetoothDevice>> = context.dataStore.data.map { prefs ->
        sanitizeSavedBluetoothDevices(
            decodeSavedBluetoothDevices(prefs[Keys.BLE_SAVED_DEVICES_JSON_V1])
        )
    }

    suspend fun setSavedBleDevices(devices: List<SavedBluetoothDevice>) {
        context.dataStore.edit { prefs ->
            val sanitized = sanitizeSavedBluetoothDevices(devices)
            if (sanitized.isEmpty()) {
                prefs.remove(Keys.BLE_SAVED_DEVICES_JSON_V1)
            } else {
                prefs[Keys.BLE_SAVED_DEVICES_JSON_V1] = encodeSavedBluetoothDevices(sanitized)
            }
        }
    }

    suspend fun upsertSavedBleDevice(device: SavedBluetoothDevice) {
        context.dataStore.edit { prefs ->
            val sanitized = sanitizeSavedBluetoothDevices(
                decodeSavedBluetoothDevices(prefs[Keys.BLE_SAVED_DEVICES_JSON_V1]) + device
            )
            if (sanitized.isEmpty()) {
                prefs.remove(Keys.BLE_SAVED_DEVICES_JSON_V1)
            } else {
                prefs[Keys.BLE_SAVED_DEVICES_JSON_V1] = encodeSavedBluetoothDevices(sanitized)
            }
        }
    }

    suspend fun removeSavedBleDevice(address: String) {
        context.dataStore.edit { prefs ->
            val normalized = normalizeBluetoothAddressOrNull(address)
            val filtered = sanitizeSavedBluetoothDevices(
                decodeSavedBluetoothDevices(prefs[Keys.BLE_SAVED_DEVICES_JSON_V1]).filterNot {
                    it.address == normalized
                }
            )
            if (filtered.isEmpty()) {
                prefs.remove(Keys.BLE_SAVED_DEVICES_JSON_V1)
            } else {
                prefs[Keys.BLE_SAVED_DEVICES_JSON_V1] = encodeSavedBluetoothDevices(filtered)
            }
        }
    }

    suspend fun ensureBleSavedDevicesMigration() {
        context.dataStore.edit { prefs ->
            if (!prefs[Keys.BLE_SAVED_DEVICES_JSON_V1].isNullOrBlank()) return@edit
            val legacyAddress = normalizeBluetoothAddressOrNull(prefs[Keys.BLE_HEART_RATE_DEVICE_ADDRESS])
                ?: return@edit
            prefs[Keys.BLE_SAVED_DEVICES_JSON_V1] = encodeSavedBluetoothDevices(
                listOf(
                    SavedBluetoothDevice(
                        address = legacyAddress,
                        kind = SavedBluetoothDeviceKind.HEART_RATE_MONITOR,
                    )
                )
            )
        }
    }

    val cyclingWheelCircumferenceMm: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[Keys.CYCLING_WHEEL_CIRCUMFERENCE_MM] ?: 2105).coerceIn(500, 4000)
    }

    suspend fun setCyclingWheelCircumferenceMm(mm: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CYCLING_WHEEL_CIRCUMFERENCE_MM] = mm.coerceIn(500, 4000)
        }
    }

    /** Null = unset (zones use each workout’s peak BPM as a proxy max). */
    val heartRateMaxBpm: Flow<Int?> = context.dataStore.data.map { prefs ->
        prefs[Keys.HEART_RATE_MAX_BPM]?.trim()?.toIntOrNull()?.takeIf { it in 90..230 }
    }

    suspend fun setHeartRateMaxBpm(bpm: Int?) {
        context.dataStore.edit { prefs ->
            if (bpm == null || bpm !in 90..230) prefs.remove(Keys.HEART_RATE_MAX_BPM)
            else prefs[Keys.HEART_RATE_MAX_BPM] = bpm.toString()
        }
    }

    /** When true, show the global heart-rate sensor strip (hidden on cardio routes). Default on for existing behavior. */
    val heartRateBannerExpanded: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HEART_RATE_BANNER_EXPANDED] ?: true
    }

    suspend fun setHeartRateBannerExpanded(expanded: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HEART_RATE_BANNER_EXPANDED] = expanded
        }
    }

    val useAppWithoutNostrAccount: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.USE_APP_WITHOUT_NOSTR_ACCOUNT_V1] == true
    }

    val firstRunSetupCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.FIRST_RUN_SETUP_COMPLETED_V1] == true
    }

    val hasUsedNostrIdentity: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HAS_USED_NOSTR_IDENTITY_V1] == true
    }

    val lastKnownDataRelayUrls: Flow<List<String>> = context.dataStore.data.map { prefs ->
        decodeStringList(prefs[Keys.LAST_KNOWN_DATA_RELAYS_JSON_V1])
    }

    /** Local-only profile (no Nostr identity). */
    val localProfileDisplayName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOCAL_PROFILE_DISPLAY_NAME_V1].orEmpty()
    }

    val localProfilePictureUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOCAL_PROFILE_PICTURE_URL_V1].orEmpty()
    }

    val localProfileBio: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOCAL_PROFILE_BIO_V1].orEmpty()
    }

    val launchPadTileOrder: Flow<List<LaunchPadTileId>> = context.dataStore.data.map { prefs ->
        decodeLaunchPadTileOrder(prefs[Keys.LAUNCH_PAD_TILE_ORDER_JSON_V1])
    }

    val launchPadHiddenTiles: Flow<Set<LaunchPadTileId>> = context.dataStore.data.map { prefs ->
        decodeLaunchPadHiddenTiles(prefs[Keys.LAUNCH_PAD_HIDDEN_TILES_JSON_V1])
    }

    suspend fun setLocalProfileDisplayName(value: String) {
        context.dataStore.edit { prefs ->
            val t = value.trim()
            if (t.isEmpty()) prefs.remove(Keys.LOCAL_PROFILE_DISPLAY_NAME_V1)
            else prefs[Keys.LOCAL_PROFILE_DISPLAY_NAME_V1] = t
        }
    }

    suspend fun setLocalProfilePictureUrl(value: String) {
        context.dataStore.edit { prefs ->
            val t = value.trim()
            if (t.isEmpty()) prefs.remove(Keys.LOCAL_PROFILE_PICTURE_URL_V1)
            else prefs[Keys.LOCAL_PROFILE_PICTURE_URL_V1] = t
        }
    }

    suspend fun setLocalProfileBio(value: String) {
        context.dataStore.edit { prefs ->
            val t = value.trim()
            if (t.isEmpty()) prefs.remove(Keys.LOCAL_PROFILE_BIO_V1)
            else prefs[Keys.LOCAL_PROFILE_BIO_V1] = t
        }
    }

    suspend fun setLaunchPadTileOrder(order: List<LaunchPadTileId>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAUNCH_PAD_TILE_ORDER_JSON_V1] = encodeLaunchPadTileOrder(order)
        }
    }

    suspend fun setLaunchPadHiddenTiles(hiddenTileIds: Set<LaunchPadTileId>) {
        context.dataStore.edit { prefs ->
            val normalized = normalizeLaunchPadHiddenTiles(hiddenTileIds)
            if (normalized.isEmpty()) {
                prefs.remove(Keys.LAUNCH_PAD_HIDDEN_TILES_JSON_V1)
            } else {
                prefs[Keys.LAUNCH_PAD_HIDDEN_TILES_JSON_V1] = encodeLaunchPadHiddenTiles(normalized)
            }
        }
    }

    suspend fun setUseAppWithoutNostrAccount(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USE_APP_WITHOUT_NOSTR_ACCOUNT_V1] = enabled
        }
    }

    suspend fun setFirstRunSetupCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FIRST_RUN_SETUP_COMPLETED_V1] = completed
        }
    }

    suspend fun rememberNostrRelayUsage(dataRelayUrls: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAS_USED_NOSTR_IDENTITY_V1] = true
            prefs[Keys.LAST_KNOWN_DATA_RELAYS_JSON_V1] = encodeStringList(dataRelayUrls.distinct().sorted())
        }
    }

    suspend fun hasUsedNostrIdentityNow(): Boolean =
        context.dataStore.data.map { prefs -> prefs[Keys.HAS_USED_NOSTR_IDENTITY_V1] == true }.first()

    suspend fun peekLastKnownDataRelayUrls(): List<String> =
        context.dataStore.data.map { prefs ->
            decodeStringList(prefs[Keys.LAST_KNOWN_DATA_RELAYS_JSON_V1])
        }.first()

    suspend fun setProgramDashboardStretchLaunchJson(json: String?) {
        context.dataStore.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.PROGRAM_DASHBOARD_STRETCH_LAUNCH_JSON_V1)
            else prefs[Keys.PROGRAM_DASHBOARD_STRETCH_LAUNCH_JSON_V1] = json
        }
    }

    /** Returns payload and clears the key (at most once). */
    suspend fun consumeProgramDashboardStretchLaunchJson(): String? {
        var out: String? = null
        context.dataStore.edit { prefs ->
            val v = prefs[Keys.PROGRAM_DASHBOARD_STRETCH_LAUNCH_JSON_V1]
            if (!v.isNullOrBlank()) {
                out = v
                prefs.remove(Keys.PROGRAM_DASHBOARD_STRETCH_LAUNCH_JSON_V1)
            }
        }
        return out
    }

    suspend fun setProgramDashboardHeatColdLaunchJson(json: String?) {
        context.dataStore.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.PROGRAM_DASHBOARD_HEAT_COLD_LAUNCH_JSON_V1)
            else prefs[Keys.PROGRAM_DASHBOARD_HEAT_COLD_LAUNCH_JSON_V1] = json
        }
    }

    suspend fun consumeProgramDashboardHeatColdLaunchJson(): String? {
        var out: String? = null
        context.dataStore.edit { prefs ->
            val v = prefs[Keys.PROGRAM_DASHBOARD_HEAT_COLD_LAUNCH_JSON_V1]
            if (!v.isNullOrBlank()) {
                out = v
                prefs.remove(Keys.PROGRAM_DASHBOARD_HEAT_COLD_LAUNCH_JSON_V1)
            }
        }
        return out
    }

    suspend fun setProgramDashboardUnifiedRoutineLaunchJson(json: String?) {
        context.dataStore.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.PROGRAM_DASHBOARD_UNIFIED_ROUTINE_LAUNCH_JSON_V1)
            else prefs[Keys.PROGRAM_DASHBOARD_UNIFIED_ROUTINE_LAUNCH_JSON_V1] = json
        }
    }

    suspend fun consumeProgramDashboardUnifiedRoutineLaunchJson(): String? {
        var out: String? = null
        context.dataStore.edit { prefs ->
            val v = prefs[Keys.PROGRAM_DASHBOARD_UNIFIED_ROUTINE_LAUNCH_JSON_V1]
            if (!v.isNullOrBlank()) {
                out = v
                prefs.remove(Keys.PROGRAM_DASHBOARD_UNIFIED_ROUTINE_LAUNCH_JSON_V1)
            }
        }
        return out
    }

    suspend fun clearPersonalData() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SELECTED_GOAL_IDS)
            prefs.remove(Keys.GOALS_JSON_V1)
            prefs.remove(Keys.GYM_MEMBERSHIP)
            prefs.remove(Keys.OWNED_EQUIPMENT_JSON_V1)
            prefs.remove(Keys.BLE_HEART_RATE_DEVICE_ADDRESS)
            prefs.remove(Keys.BLE_CSC_DEVICE_ADDRESS)
            prefs.remove(Keys.BLE_SAVED_DEVICES_JSON_V1)
            prefs.remove(Keys.LOCAL_PROFILE_DISPLAY_NAME_V1)
            prefs.remove(Keys.LOCAL_PROFILE_PICTURE_URL_V1)
            prefs.remove(Keys.LOCAL_PROFILE_BIO_V1)
        }
    }

    suspend fun clearProgramLaunchTargets() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.PROGRAM_DASHBOARD_STRETCH_LAUNCH_JSON_V1)
            prefs.remove(Keys.PROGRAM_DASHBOARD_HEAT_COLD_LAUNCH_JSON_V1)
            prefs.remove(Keys.PROGRAM_DASHBOARD_UNIFIED_ROUTINE_LAUNCH_JSON_V1)
        }
    }

    suspend fun clearAllData() {
        context.dataStore.edit { prefs ->
            prefs.clear()
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

private fun normalizeBluetoothAddressOrNull(address: String?): String? =
    address?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

private fun sanitizeSavedBluetoothDevices(
    devices: List<SavedBluetoothDevice>
): List<SavedBluetoothDevice> =
    devices
        .mapNotNull { device ->
            val normalizedAddress = normalizeBluetoothAddressOrNull(device.address) ?: return@mapNotNull null
            device.copy(
                address = normalizedAddress,
                name = device.name?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
        .sortedByDescending { it.lastConnectedEpochMillis ?: 0L }
        .distinctBy { it.address }

private fun decodeDelimitedIdSet(raw: String?): Set<String> =
    raw.orEmpty()
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

private fun encodeDelimitedIdSet(ids: Set<String>): String =
    ids
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
        .joinToString(",")

private fun encodeStringList(items: List<String>): String =
    Json.encodeToString(ListSerializer(String.serializer()), items)

private fun decodeStringList(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        Json.decodeFromString(ListSerializer(String.serializer()), raw)
    } catch (_: Exception) {
        emptyList()
    }
}
