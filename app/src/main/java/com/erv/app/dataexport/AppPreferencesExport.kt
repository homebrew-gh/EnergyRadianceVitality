package com.erv.app.dataexport

import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.CardioLiveSplitMode
import com.erv.app.data.LaunchPadTileId
import com.erv.app.data.StretchGuidedTtsVoice
import com.erv.app.data.TemperatureUnit
import com.erv.app.data.ThemeMode
import com.erv.app.data.UserPreferences
import com.erv.app.data.WeightLiveRestTimerMode
import com.erv.app.data.WorkoutMediaUploadBackend
import kotlinx.coroutines.flow.first
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * User-facing app settings for backup/restore. Excludes Nostr private keys, relay URLs, and
 * other relay/identity state (those live in [com.erv.app.nostr.KeyManager]).
 */
@Serializable
data class AppPreferencesExportV1(
    val themeMode: String = ThemeMode.SYSTEM.name,
    val bodyWeightValue: String = "",
    val bodyWeightUnit: String = BodyWeightUnit.LB.name,
    val cardioDistanceUnit: String = CardioDistanceUnit.MILES.name,
    val weightTrainingLoadUnit: String = BodyWeightUnit.LB.name,
    val temperatureUnit: String = TemperatureUnit.FAHRENHEIT.name,
    val workoutBubbleEnabled: Boolean = true,
    val weightLiveFgsDisclosureSeen: Boolean = false,
    val cardioGpsRecordingPreferred: Boolean = true,
    val cardioGpsTrackRetainOnDevice: Boolean = true,
    val cardioLiveSplitMode: String = CardioLiveSplitMode.OFF.name,
    val cardioLiveAutoSplitQuarterMiles: Int = 1,
    val nip96MediaServerOrigin: String = "",
    val blossomPublicServerOrigin: String = "",
    val blossomPrivateServerOrigin: String = "",
    val mediaKeysSplitV1: Boolean = false,
    val attachRouteImageToWorkoutNostrShare: Boolean = true,
    val workoutMediaUploadBackend: String = WorkoutMediaUploadBackend.NIP96.name,
    val enabledWeightExercisePackIds: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val liveWeightWorkoutDraftJson: String? = null,
    val liveWeightWorkoutNotificationSuppressed: Boolean = false,
    val stretchGuidedTtsVoice: String = StretchGuidedTtsVoice.SYSTEM_DEFAULT.name,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val bleHeartRateDeviceAddress: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val bleCscDeviceAddress: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val bleConcept2DeviceAddress: String? = null,
    val cyclingWheelCircumferenceMm: Int = 2105,
    val heartRateBannerExpanded: Boolean = true,
    val weightLiveRestTimerMode: String = WeightLiveRestTimerMode.OFF.name,
    val weightLiveRestTimerSeconds: Int = 90,
    val weightLiveRestTimerCountdownSoundEnabled: Boolean = true,
    val weightLiveRestTimerEndSoundEnabled: Boolean = true,
    val launchPadTileOrder: List<LaunchPadTileId> = emptyList(),
    val launchPadHiddenTiles: List<LaunchPadTileId> = emptyList(),
    val firstRunSetupCompleted: Boolean = false,
    val trustSelfSignedLanTls: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val programDashboardStretchLaunchJson: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val programDashboardHeatColdLaunchJson: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val programDashboardUnifiedRoutineLaunchJson: String? = null,
)

suspend fun UserPreferences.snapshotAppPreferencesForBackup(): AppPreferencesExportV1 =
    AppPreferencesExportV1(
        themeMode = themeMode.first().name,
        bodyWeightValue = bodyWeightValue.first(),
        bodyWeightUnit = bodyWeightUnit.first().name,
        cardioDistanceUnit = cardioDistanceUnit.first().name,
        weightTrainingLoadUnit = weightTrainingLoadUnit.first().name,
        temperatureUnit = temperatureUnit.first().name,
        workoutBubbleEnabled = workoutBubbleEnabled.first(),
        weightLiveFgsDisclosureSeen = weightLiveWorkoutFgsDisclosureSeen.first(),
        cardioGpsRecordingPreferred = cardioGpsRecordingPreferred.first(),
        cardioGpsTrackRetainOnDevice = cardioGpsTrackRetainOnDevice.first(),
        cardioLiveSplitMode = cardioLiveSplitMode.first().name,
        cardioLiveAutoSplitQuarterMiles = cardioLiveAutoSplitQuarterMiles.first(),
        nip96MediaServerOrigin = nip96MediaServerOrigin.first(),
        blossomPublicServerOrigin = blossomPublicServerOrigin.first(),
        blossomPrivateServerOrigin = blossomPrivateServerOrigin.first(),
        mediaKeysSplitV1 = mediaKeysSplitApplied.first(),
        attachRouteImageToWorkoutNostrShare = attachRouteImageToWorkoutNostrShare.first(),
        workoutMediaUploadBackend = workoutMediaUploadBackend.first().name,
        enabledWeightExercisePackIds = enabledWeightExercisePackIds.first().sorted(),
        liveWeightWorkoutDraftJson = liveWeightWorkoutDraftJson.first(),
        liveWeightWorkoutNotificationSuppressed = liveWeightWorkoutNotificationSuppressed.first(),
        stretchGuidedTtsVoice = stretchGuidedTtsVoice.first().name,
        bleHeartRateDeviceAddress = bleHeartRateDeviceAddress.first(),
        bleCscDeviceAddress = bleCscDeviceAddress.first(),
        bleConcept2DeviceAddress = bleConcept2DeviceAddress.first(),
        cyclingWheelCircumferenceMm = cyclingWheelCircumferenceMm.first(),
        heartRateBannerExpanded = heartRateBannerExpanded.first(),
        weightLiveRestTimerMode = weightLiveRestTimerMode.first().name,
        weightLiveRestTimerSeconds = weightLiveRestTimerSeconds.first(),
        weightLiveRestTimerCountdownSoundEnabled = weightLiveRestTimerCountdownSoundEnabled.first(),
        weightLiveRestTimerEndSoundEnabled = weightLiveRestTimerEndSoundEnabled.first(),
        launchPadTileOrder = launchPadTileOrder.first(),
        launchPadHiddenTiles = launchPadHiddenTiles.first().toList(),
        firstRunSetupCompleted = firstRunSetupCompleted.first(),
        trustSelfSignedLanTls = trustSelfSignedLanTls.first(),
        programDashboardStretchLaunchJson = peekProgramDashboardStretchLaunchJson(),
        programDashboardHeatColdLaunchJson = peekProgramDashboardHeatColdLaunchJson(),
        programDashboardUnifiedRoutineLaunchJson = peekProgramDashboardUnifiedRoutineLaunchJson(),
    )

suspend fun UserPreferences.restoreAppPreferencesFromBackup(snapshot: AppPreferencesExportV1) {
    setThemeMode(parseThemeMode(snapshot.themeMode))
    setFallbackBodyWeight(snapshot.bodyWeightValue, parseBodyWeightUnit(snapshot.bodyWeightUnit))
    setCardioDistanceUnit(parseCardioDistanceUnit(snapshot.cardioDistanceUnit))
    setWeightTrainingLoadUnit(parseBodyWeightUnit(snapshot.weightTrainingLoadUnit))
    setTemperatureUnit(parseTemperatureUnit(snapshot.temperatureUnit))
    setWorkoutBubbleEnabled(snapshot.workoutBubbleEnabled)
    setWeightLiveWorkoutFgsDisclosureSeen(snapshot.weightLiveFgsDisclosureSeen)
    setCardioGpsRecordingPreferred(snapshot.cardioGpsRecordingPreferred)
    setCardioGpsTrackRetainOnDevice(snapshot.cardioGpsTrackRetainOnDevice)
    setCardioLiveSplitMode(parseCardioLiveSplitMode(snapshot.cardioLiveSplitMode))
    setCardioLiveAutoSplitQuarterMiles(snapshot.cardioLiveAutoSplitQuarterMiles)
    setNip96MediaServerOrigin(snapshot.nip96MediaServerOrigin)
    setBlossomPublicServerOrigin(snapshot.blossomPublicServerOrigin)
    setBlossomPrivateServerOrigin(snapshot.blossomPrivateServerOrigin)
    restoreMediaKeysSplitFlag(snapshot.mediaKeysSplitV1)
    setAttachRouteImageToWorkoutNostrShare(snapshot.attachRouteImageToWorkoutNostrShare)
    setWorkoutMediaUploadBackend(parseWorkoutMediaUploadBackend(snapshot.workoutMediaUploadBackend))
    setEnabledWeightExercisePackIds(snapshot.enabledWeightExercisePackIds.toSet())
    setLiveWeightWorkoutDraftJson(snapshot.liveWeightWorkoutDraftJson)
    setLiveWeightWorkoutNotificationSuppressed(snapshot.liveWeightWorkoutNotificationSuppressed)
    setStretchGuidedTtsVoice(parseStretchGuidedTtsVoice(snapshot.stretchGuidedTtsVoice))
    setBleHeartRateDeviceAddress(snapshot.bleHeartRateDeviceAddress)
    setBleCscDeviceAddress(snapshot.bleCscDeviceAddress)
    setBleConcept2DeviceAddress(snapshot.bleConcept2DeviceAddress)
    setCyclingWheelCircumferenceMm(snapshot.cyclingWheelCircumferenceMm)
    setHeartRateBannerExpanded(snapshot.heartRateBannerExpanded)
    setWeightLiveRestTimerMode(parseWeightLiveRestTimerMode(snapshot.weightLiveRestTimerMode))
    setWeightLiveRestTimerSeconds(snapshot.weightLiveRestTimerSeconds)
    setWeightLiveRestTimerCountdownSoundEnabled(snapshot.weightLiveRestTimerCountdownSoundEnabled)
    setWeightLiveRestTimerEndSoundEnabled(snapshot.weightLiveRestTimerEndSoundEnabled)
    if (snapshot.launchPadTileOrder.isNotEmpty()) {
        setLaunchPadTileOrder(snapshot.launchPadTileOrder)
    }
    setLaunchPadHiddenTiles(snapshot.launchPadHiddenTiles.toSet())
    setFirstRunSetupCompleted(snapshot.firstRunSetupCompleted)
    setTrustSelfSignedLanTls(snapshot.trustSelfSignedLanTls)
    setProgramDashboardStretchLaunchJson(snapshot.programDashboardStretchLaunchJson)
    setProgramDashboardHeatColdLaunchJson(snapshot.programDashboardHeatColdLaunchJson)
    setProgramDashboardUnifiedRoutineLaunchJson(snapshot.programDashboardUnifiedRoutineLaunchJson)
}

private fun parseThemeMode(raw: String): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == raw.uppercase() } ?: ThemeMode.SYSTEM

private fun parseBodyWeightUnit(raw: String): BodyWeightUnit =
    when (raw.uppercase()) {
        "KG" -> BodyWeightUnit.KG
        else -> BodyWeightUnit.LB
    }

private fun parseCardioDistanceUnit(raw: String): CardioDistanceUnit =
    when (raw.uppercase()) {
        "KILOMETERS", "KM" -> CardioDistanceUnit.KILOMETERS
        else -> CardioDistanceUnit.MILES
    }

private fun parseTemperatureUnit(raw: String): TemperatureUnit =
    when (raw.uppercase()) {
        "CELSIUS" -> TemperatureUnit.CELSIUS
        else -> TemperatureUnit.FAHRENHEIT
    }

private fun parseCardioLiveSplitMode(raw: String): CardioLiveSplitMode =
    CardioLiveSplitMode.entries.firstOrNull { it.name == raw.uppercase() } ?: CardioLiveSplitMode.OFF

private fun parseWorkoutMediaUploadBackend(raw: String): WorkoutMediaUploadBackend =
    WorkoutMediaUploadBackend.entries.firstOrNull { it.name == raw.uppercase() }
        ?: WorkoutMediaUploadBackend.NIP96

private fun parseStretchGuidedTtsVoice(raw: String): StretchGuidedTtsVoice =
    StretchGuidedTtsVoice.entries.firstOrNull { it.name == raw.uppercase() }
        ?: StretchGuidedTtsVoice.SYSTEM_DEFAULT

private fun parseWeightLiveRestTimerMode(raw: String): WeightLiveRestTimerMode =
    WeightLiveRestTimerMode.entries.firstOrNull { it.name == raw.uppercase() }
        ?: WeightLiveRestTimerMode.OFF
