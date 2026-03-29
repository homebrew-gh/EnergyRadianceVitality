package com.erv.app.dataexport

import com.erv.app.bodytracker.BodyTrackerLibraryState
import com.erv.app.bodytracker.BodyTrackerRepository
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioSession
import com.erv.app.data.FitnessEquipmentNostrPayload
import com.erv.app.data.OwnedEquipmentItem
import com.erv.app.data.SavedBluetoothDevice
import com.erv.app.data.UserGoalDefinition
import com.erv.app.heatcold.HeatColdLibraryState
import com.erv.app.lighttherapy.LightLibraryState
import com.erv.app.programs.ProgramsLibraryState
import com.erv.app.reminders.RoutineReminderState
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.supplements.SupplementLibraryState
import com.erv.app.unifiedroutines.UnifiedRoutineLibraryState
import com.erv.app.weighttraining.WeightLibraryState
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64

/** What to include in an export (JSON always; CSV only for weight and cardio). */
enum class DataExportCategory(val label: String) {
    ALL("All categories"),
    WEIGHT_TRAINING("Weight training"),
    CARDIO("Cardio"),
    STRETCHING("Stretching"),
    HEAT_COLD("Heat and cold"),
    LIGHT_THERAPY("Light therapy"),
    SUPPLEMENTS("Supplements"),
    PROGRAMS("Programs"),
    UNIFIED_ROUTINES("Unified routines"),
    BODY_TRACKER("Body tracker"),
    REMINDERS("Reminders"),
    PERSONAL_DATA("Personal data"),
}

enum class DataExportFormat {
    JSON,
    CSV,
}

sealed class ExportDateSelection {
    data object AllTime : ExportDateSelection()
    data class Range(val startInclusive: LocalDate, val endInclusive: LocalDate) : ExportDateSelection()
}

@Serializable
data class LocalProfileExportV1(
    val displayName: String = "",
    val pictureUrl: String = "",
    val bio: String = "",
)

@Serializable
data class PersonalDataExportV1(
    val goals: List<UserGoalDefinition> = emptyList(),
    val savedBluetoothDevices: List<SavedBluetoothDevice> = emptyList(),
    val localProfile: LocalProfileExportV1 = LocalProfileExportV1(),
)

@Serializable
data class BodyTrackerPhotoExportV1(
    val photoId: String,
    val fileName: String,
    val mimeType: String = "image/jpeg",
    val base64Data: String,
)

@Serializable
data class BodyTrackerExportV1(
    val libraryState: BodyTrackerLibraryState,
    val photos: List<BodyTrackerPhotoExportV1> = emptyList(),
)

@Serializable
data class ErvAppDataExportV1(
    val ervAppDataExportVersion: Int = 1,
    val exportedAtEpochSeconds: Long,
    /** Human-readable, e.g. `all` or `2024-01-01..2024-12-31`. */
    val dateRangeLabel: String,
    /**
     * Same JSON shape as Nostr `erv/equipment` (Settings → Equipment & Gym). Omitted when the user
     * has no owned equipment and no commercial gym membership.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val fitnessEquipment: FitnessEquipmentNostrPayload? = null,
    val weightTraining: WeightLibraryState? = null,
    val cardio: CardioLibraryState? = null,
    val stretching: StretchLibraryState? = null,
    val heatCold: HeatColdLibraryState? = null,
    val lightTherapy: LightLibraryState? = null,
    val supplements: SupplementLibraryState? = null,
    val programs: ProgramsLibraryState? = null,
    val unifiedRoutines: UnifiedRoutineLibraryState? = null,
    val bodyTracker: BodyTrackerExportV1? = null,
    val reminders: RoutineReminderState? = null,
    val personalData: PersonalDataExportV1? = null,
)

object ErvAppDataExport {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val jsonPretty = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun dateRangeLabel(selection: ExportDateSelection): String = when (selection) {
        ExportDateSelection.AllTime -> "all"
        is ExportDateSelection.Range ->
            "${selection.startInclusive}..${selection.endInclusive}"
    }

    fun isDateInSelection(isoDay: String, selection: ExportDateSelection): Boolean {
        val d = try {
            LocalDate.parse(isoDay)
        } catch (_: Exception) {
            return false
        }
        return when (selection) {
            ExportDateSelection.AllTime -> true
            is ExportDateSelection.Range -> !d.isBefore(selection.startInclusive) && !d.isAfter(selection.endInclusive)
        }
    }

    fun filterWeightState(state: WeightLibraryState, selection: ExportDateSelection): WeightLibraryState =
        state.copy(logs = state.logs.filter { isDateInSelection(it.date, selection) })

    fun filterCardioState(state: CardioLibraryState, selection: ExportDateSelection): CardioLibraryState =
        state.copy(logs = state.logs.filter { isDateInSelection(it.date, selection) })

    fun filterStretchState(state: StretchLibraryState, selection: ExportDateSelection): StretchLibraryState =
        state.copy(logs = state.logs.filter { isDateInSelection(it.date, selection) })

    fun filterHeatColdState(state: HeatColdLibraryState, selection: ExportDateSelection): HeatColdLibraryState =
        state.copy(
            saunaLogs = state.saunaLogs.filter { isDateInSelection(it.date, selection) },
            coldLogs = state.coldLogs.filter { isDateInSelection(it.date, selection) },
        )

    fun filterLightState(state: LightLibraryState, selection: ExportDateSelection): LightLibraryState =
        state.copy(logs = state.logs.filter { isDateInSelection(it.date, selection) })

    fun filterSupplementState(state: SupplementLibraryState, selection: ExportDateSelection): SupplementLibraryState =
        state.copy(logs = state.logs.filter { isDateInSelection(it.date, selection) })

    fun filterBodyTrackerState(
        state: BodyTrackerLibraryState,
        selection: ExportDateSelection,
    ): BodyTrackerLibraryState = state.copy(logs = state.logs.filter { isDateInSelection(it.date, selection) })

    /**
     * Nostr-compatible equipment profile for exports. Null when there is nothing to constrain
     * programming (no listed equipment and no commercial gym access).
     */
    fun fitnessEquipmentForExport(
        gymMembership: Boolean,
        ownedEquipment: List<OwnedEquipmentItem>,
    ): FitnessEquipmentNostrPayload? {
        if (!gymMembership && ownedEquipment.isEmpty()) return null
        return FitnessEquipmentNostrPayload(
            gymMembership = gymMembership,
            equipment = ownedEquipment,
        )
    }

    suspend fun buildBodyTrackerExport(
        repository: BodyTrackerRepository,
        state: BodyTrackerLibraryState,
        selection: ExportDateSelection,
    ): BodyTrackerExportV1 {
        val filteredState = filterBodyTrackerState(state, selection)
        val photos = filteredState.logs
            .flatMap { log -> log.photos }
            .distinctBy { it.id }
            .mapNotNull { photo ->
                val file = repository.photoFile(photo.id)
                if (!file.exists() || !file.isFile) return@mapNotNull null
                val encoded = runCatching {
                    Base64.getEncoder().encodeToString(file.readBytes())
                }.getOrNull() ?: return@mapNotNull null
                BodyTrackerPhotoExportV1(
                    photoId = photo.id,
                    fileName = file.name,
                    base64Data = encoded,
                )
            }
        return BodyTrackerExportV1(
            libraryState = filteredState,
            photos = photos,
        )
    }

    fun buildBundle(
        category: DataExportCategory,
        selection: ExportDateSelection,
        weight: WeightLibraryState,
        cardio: CardioLibraryState,
        stretching: StretchLibraryState,
        heatCold: HeatColdLibraryState,
        light: LightLibraryState,
        supplements: SupplementLibraryState,
        programs: ProgramsLibraryState,
        unifiedRoutines: UnifiedRoutineLibraryState,
        bodyTracker: BodyTrackerExportV1,
        reminders: RoutineReminderState,
        gymMembership: Boolean,
        ownedEquipment: List<OwnedEquipmentItem>,
        goals: List<UserGoalDefinition>,
        savedBluetoothDevices: List<SavedBluetoothDevice>,
        localProfile: LocalProfileExportV1,
        exportedAtEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ): ErvAppDataExportV1 {
        val label = dateRangeLabel(selection)
        val equipmentPayload = fitnessEquipmentForExport(gymMembership, ownedEquipment)
        val personalDataPayload = PersonalDataExportV1(
            goals = goals,
            savedBluetoothDevices = savedBluetoothDevices,
            localProfile = localProfile,
        )
        fun w() = filterWeightState(weight, selection)
        fun c() = filterCardioState(cardio, selection)
        fun st() = filterStretchState(stretching, selection)
        fun hc() = filterHeatColdState(heatCold, selection)
        fun lt() = filterLightState(light, selection)
        fun sup() = filterSupplementState(supplements, selection)
        return when (category) {
            DataExportCategory.ALL -> ErvAppDataExportV1(
                exportedAtEpochSeconds = exportedAtEpochSeconds,
                dateRangeLabel = label,
                fitnessEquipment = equipmentPayload,
                weightTraining = w(),
                cardio = c(),
                stretching = st(),
                heatCold = hc(),
                lightTherapy = lt(),
                supplements = sup(),
                programs = programs,
                unifiedRoutines = unifiedRoutines,
                bodyTracker = bodyTracker,
                reminders = reminders,
                personalData = personalDataPayload,
            )
            DataExportCategory.WEIGHT_TRAINING -> ErvAppDataExportV1(
                exportedAtEpochSeconds = exportedAtEpochSeconds,
                dateRangeLabel = label,
                fitnessEquipment = equipmentPayload,
                weightTraining = w(),
            )
            DataExportCategory.CARDIO -> ErvAppDataExportV1(
                exportedAtEpochSeconds = exportedAtEpochSeconds,
                dateRangeLabel = label,
                fitnessEquipment = equipmentPayload,
                cardio = c(),
            )
            DataExportCategory.STRETCHING -> ErvAppDataExportV1(
                exportedAtEpochSeconds = exportedAtEpochSeconds,
                dateRangeLabel = label,
                fitnessEquipment = equipmentPayload,
                stretching = st(),
            )
            DataExportCategory.HEAT_COLD -> ErvAppDataExportV1(
                exportedAtEpochSeconds = exportedAtEpochSeconds,
                dateRangeLabel = label,
                fitnessEquipment = equipmentPayload,
                heatCold = hc(),
            )
            DataExportCategory.LIGHT_THERAPY -> ErvAppDataExportV1(
                exportedAtEpochSeconds = exportedAtEpochSeconds,
                dateRangeLabel = label,
                fitnessEquipment = equipmentPayload,
                lightTherapy = lt(),
            )
            DataExportCategory.SUPPLEMENTS -> ErvAppDataExportV1(
                exportedAtEpochSeconds = exportedAtEpochSeconds,
                dateRangeLabel = label,
                fitnessEquipment = equipmentPayload,
                supplements = sup(),
            )
            DataExportCategory.PROGRAMS -> ErvAppDataExportV1(
                exportedAtEpochSeconds = exportedAtEpochSeconds,
                dateRangeLabel = label,
                programs = programs,
            )
            DataExportCategory.UNIFIED_ROUTINES -> ErvAppDataExportV1(
                exportedAtEpochSeconds = exportedAtEpochSeconds,
                dateRangeLabel = label,
                unifiedRoutines = unifiedRoutines,
            )
            DataExportCategory.BODY_TRACKER -> ErvAppDataExportV1(
                exportedAtEpochSeconds = exportedAtEpochSeconds,
                dateRangeLabel = label,
                bodyTracker = bodyTracker,
            )
            DataExportCategory.REMINDERS -> ErvAppDataExportV1(
                exportedAtEpochSeconds = exportedAtEpochSeconds,
                dateRangeLabel = label,
                reminders = reminders,
            )
            DataExportCategory.PERSONAL_DATA -> ErvAppDataExportV1(
                exportedAtEpochSeconds = exportedAtEpochSeconds,
                dateRangeLabel = label,
                fitnessEquipment = equipmentPayload,
                personalData = personalDataPayload,
            )
        }
    }

    fun toJsonString(bundle: ErvAppDataExportV1): String =
        jsonPretty.encodeToString(ErvAppDataExportV1.serializer(), bundle)

    /** ERV weight CSV v1: one row per set (matches import). HIIT-only entries are omitted. */
    fun weightTrainingToCsv(state: WeightLibraryState): String = buildString {
        appendLine("date,session_key,exercise_id,set_index,reps,weight_kg,rpe")
        for (day in state.logs.sortedBy { it.date }) {
            for (workout in day.workouts) {
                val sessionKey = workout.id
                for (entry in workout.entries) {
                    if (entry.hiitBlock != null) continue
                    for ((i, set) in entry.sets.withIndex()) {
                        val setIndex = i + 1
                        val reps = set.reps
                        val wkg = set.weightKg?.toString().orEmpty()
                        val rpe = set.rpe?.toString().orEmpty()
                        append(csvCell(day.date))
                        append(',')
                        append(csvCell(sessionKey))
                        append(',')
                        append(csvCell(entry.exerciseId))
                        append(',')
                        append(setIndex)
                        append(',')
                        append(reps)
                        append(',')
                        append(csvCell(wkg))
                        append(',')
                        append(csvCell(rpe))
                        appendLine()
                    }
                }
            }
        }
    }

    /**
     * ERV cardio CSV v1 (matches import). One row per session using top-level rollups.
     * Multi-segment sessions use session-level duration, distance, and primary activity fields.
     */
    fun cardioToCsv(state: CardioLibraryState): String = buildString {
        appendLine(
            "date,duration_minutes,builtin,custom_activity_name,custom_type_id,display_label," +
                "distance_m,modality,speed,speed_unit,incline_pct,pack_kg,ruck_load_kg,estimated_kcal," +
                "start_epoch_seconds,end_epoch_seconds"
        )
        for (day in state.logs.sortedBy { it.date }) {
            for (session in day.sessions) {
                appendCardioRow(day.date, session)
            }
        }
    }

    private fun StringBuilder.appendCardioRow(date: String, session: CardioSession) {
        val act = session.activity
        val builtinStr = act.builtin?.name.orEmpty()
        val customName = if (act.builtin == null) act.customName.orEmpty() else ""
        val customId = if (act.builtin == null) act.customTypeId.orEmpty() else ""
        val display = csvCell(act.displayLabel)
        val dist = session.distanceMeters?.toString().orEmpty()
        val modality = session.modality.name
        val tm = session.treadmill
        val speed = tm?.speed?.toString().orEmpty()
        val speedUnit = tm?.speedUnit?.name.orEmpty()
        val incline = tm?.inclinePercent?.toString().orEmpty()
        val packKg = tm?.loadKg?.toString().orEmpty()
        val ruckOutdoor = session.ruckLoadKg?.toString().orEmpty()
        val kcal = session.estimatedKcal?.toString().orEmpty()
        val start = session.startEpochSeconds?.toString().orEmpty()
        val end = session.endEpochSeconds?.toString().orEmpty()
        append(csvCell(date))
        append(',')
        append(session.durationMinutes)
        append(',')
        append(csvCell(builtinStr))
        append(',')
        append(csvCell(customName))
        append(',')
        append(csvCell(customId))
        append(',')
        append(display)
        append(',')
        append(csvCell(dist))
        append(',')
        append(csvCell(modality))
        append(',')
        append(csvCell(speed))
        append(',')
        append(csvCell(speedUnit))
        append(',')
        append(csvCell(incline))
        append(',')
        append(csvCell(packKg))
        append(',')
        append(csvCell(ruckOutdoor))
        append(',')
        append(csvCell(kcal))
        append(',')
        append(csvCell(start))
        append(',')
        append(csvCell(end))
        appendLine()
    }

    private fun csvCell(raw: String): String {
        val cleaned = raw.replace("\r", " ").replace("\n", " ").replace(",", " ")
        return cleaned
    }

    fun csvSupported(category: DataExportCategory, format: DataExportFormat): Boolean =
        format != DataExportFormat.CSV ||
            category == DataExportCategory.WEIGHT_TRAINING ||
            category == DataExportCategory.CARDIO

    fun defaultExportFileStem(
        category: DataExportCategory,
        @Suppress("UNUSED_PARAMETER") format: DataExportFormat,
    ): String {
        val day = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val slug = when (category) {
            DataExportCategory.ALL -> "erv_all"
            DataExportCategory.WEIGHT_TRAINING -> "erv_weight"
            DataExportCategory.CARDIO -> "erv_cardio"
            DataExportCategory.STRETCHING -> "erv_stretching"
            DataExportCategory.HEAT_COLD -> "erv_heat_cold"
            DataExportCategory.LIGHT_THERAPY -> "erv_light"
            DataExportCategory.SUPPLEMENTS -> "erv_supplements"
            DataExportCategory.PROGRAMS -> "erv_programs"
            DataExportCategory.UNIFIED_ROUTINES -> "erv_unified_routines"
            DataExportCategory.BODY_TRACKER -> "erv_body_tracker"
            DataExportCategory.REMINDERS -> "erv_reminders"
            DataExportCategory.PERSONAL_DATA -> "erv_personal"
        }
        return "${slug}_export_$day"
    }

    fun millisToLocalDateUtc(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
