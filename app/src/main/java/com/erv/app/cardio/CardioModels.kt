package com.erv.app.cardio

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
enum class CardioBuiltinActivity {
    WALK, RUN, SPRINT, RUCK, HIKE, BIKE, SWIM, ELLIPTICAL, ROWING, OTHER
}

fun CardioBuiltinActivity.supportsTreadmillModality(): Boolean = when (this) {
    CardioBuiltinActivity.WALK, CardioBuiltinActivity.RUN,
    CardioBuiltinActivity.SPRINT, CardioBuiltinActivity.RUCK -> true
    else -> false
}

fun CardioBuiltinActivity.displayName(): String = when (this) {
    CardioBuiltinActivity.WALK -> "Walking"
    CardioBuiltinActivity.RUN -> "Running"
    CardioBuiltinActivity.SPRINT -> "Sprinting"
    CardioBuiltinActivity.RUCK -> "Rucking"
    CardioBuiltinActivity.HIKE -> "Hiking"
    CardioBuiltinActivity.BIKE -> "Cycling"
    CardioBuiltinActivity.SWIM -> "Swimming"
    CardioBuiltinActivity.ELLIPTICAL -> "Elliptical"
    CardioBuiltinActivity.ROWING -> "Rowing"
    CardioBuiltinActivity.OTHER -> "Other"
}

@Serializable
enum class CardioModality {
    OUTDOOR, INDOOR_TREADMILL
}

fun CardioModality.label(): String = when (this) {
    CardioModality.OUTDOOR -> "Outdoor"
    CardioModality.INDOOR_TREADMILL -> "Treadmill"
}

@Serializable
enum class CardioSpeedUnit {
    MPH, KMH
}

@Serializable
data class CardioTreadmillParams(
    val speed: Double,
    val speedUnit: CardioSpeedUnit,
    val inclinePercent: Double = 0.0,
    /** User or machine distance; meters */
    val distanceMeters: Double? = null,
    /** Ruck load for treadmill rucking; stored in kg */
    val loadKg: Double? = null
)

@Serializable
enum class CardioSessionSource {
    MANUAL, DURATION_TIMER
}

/** Placeholder for future BLE HR (Phase 10). */
@Serializable
data class CardioHrScaffolding(
    val avgBpm: Int? = null,
    val maxBpm: Int? = null,
    val minBpm: Int? = null
)

@Serializable
data class CardioActivitySnapshot(
    val builtin: CardioBuiltinActivity? = null,
    val customTypeId: String? = null,
    val customName: String? = null,
    val displayLabel: String
)

@Serializable
data class CardioSession(
    val id: String = UUID.randomUUID().toString(),
    val activity: CardioActivitySnapshot,
    val modality: CardioModality = CardioModality.OUTDOOR,
    val treadmill: CardioTreadmillParams? = null,
    val durationMinutes: Int,
    /** Total distance if known (outdoor manual, machine, or derived). Meters */
    val distanceMeters: Double? = null,
    val estimatedKcal: Double? = null,
    val routineId: String? = null,
    val routineName: String? = null,
    val source: CardioSessionSource = CardioSessionSource.MANUAL,
    val startEpochSeconds: Long? = null,
    val endEpochSeconds: Long? = null,
    val loggedAtEpochSeconds: Long = nowEpochSeconds(),
    val heartRate: CardioHrScaffolding? = null
)

@Serializable
enum class CardioWeekday {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}

fun CardioWeekday.shortLabel(): String = when (this) {
    CardioWeekday.MONDAY -> "Mon"
    CardioWeekday.TUESDAY -> "Tue"
    CardioWeekday.WEDNESDAY -> "Wed"
    CardioWeekday.THURSDAY -> "Thu"
    CardioWeekday.FRIDAY -> "Fri"
    CardioWeekday.SATURDAY -> "Sat"
    CardioWeekday.SUNDAY -> "Sun"
}

@Serializable
data class CardioRoutine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val activity: CardioActivitySnapshot,
    val modality: CardioModality = CardioModality.OUTDOOR,
    val treadmill: CardioTreadmillParams? = null,
    val targetDurationMinutes: Int? = null,
    val repeatDays: List<CardioWeekday> = emptyList(),
    val notes: String = ""
)

@Serializable
data class CardioCustomActivityType(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val optionalMet: Double? = null
)

@Serializable
data class CardioDayLog(
    val date: String,
    val sessions: List<CardioSession> = emptyList()
)

@Serializable
data class CardioLibraryState(
    val routines: List<CardioRoutine> = emptyList(),
    val customActivityTypes: List<CardioCustomActivityType> = emptyList(),
    val logs: List<CardioDayLog> = emptyList()
) {
    fun logFor(date: LocalDate): CardioDayLog? = logs.firstOrNull { it.date == date.toString() }
    fun customTypeById(id: String): CardioCustomActivityType? =
        customActivityTypes.firstOrNull { it.id == id }
}

data class CardioActivityRow(val summaryLine: String)

fun CardioLibraryState.chronologicalCardioLogFor(date: LocalDate): List<CardioSession> {
    val log = logFor(date) ?: return emptyList()
    return log.sessions.sortedByDescending { it.loggedAtEpochSeconds }
}

fun CardioLibraryState.cardioActivityRowsFor(date: LocalDate): List<CardioActivityRow> {
    val log = logFor(date) ?: return emptyList()
    return log.sessions.map { session ->
        CardioActivityRow(summaryLine = session.summaryLine())
    }
}

fun CardioSession.summaryLine(): String {
    val base = buildString {
        append(activity.displayLabel)
        if (modality == CardioModality.INDOOR_TREADMILL) append(" (treadmill)")
        append(" • ${durationMinutes} min")
        treadmill?.let { t ->
            append(" • ${formatSpeed(t.speed, t.speedUnit)}")
            if (t.inclinePercent > 0.01) append(" • ${t.inclinePercent.toInt()}% incline")
            t.loadKg?.let { kg ->
                append(" • ${formatLoadLbKg(kg)}")
            }
        }
        distanceMeters?.let { d ->
            if (d > 1) append(" • ${formatDistanceKm(d)}")
        }
        estimatedKcal?.let { k ->
            append(" • ~${k.toInt()} kcal")
        }
    }
    return base
}

private fun formatSpeed(speed: Double, unit: CardioSpeedUnit): String =
    if (unit == CardioSpeedUnit.MPH) String.format("%.1f mph", speed)
    else String.format("%.1f km/h", speed)

private fun formatLoadLbKg(kg: Double): String {
    val lb = kg / 0.453592
    return String.format("%.0f lb (%.1f kg)", lb, kg)
}

private fun formatDistanceKm(meters: Double): String =
    String.format("%.2f km", meters / 1000.0)

fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

/** Live session timer state; dashboard and Cardio screen share this. */
data class CardioTimerSessionDraft(
    val title: String,
    val activity: CardioActivitySnapshot,
    val modality: CardioModality,
    val treadmill: CardioTreadmillParams?,
    val startEpoch: Long,
    val routineId: String?,
    val routineName: String?
) {
    fun toSession(durationMinutes: Int, endEpoch: Long): CardioSession {
        var dist = treadmill?.distanceMeters
        if (dist == null && treadmill != null) {
            dist = derivedTreadmillDistanceMeters(treadmill, durationMinutes)
        }
        return CardioSession(
            activity = activity,
            modality = modality,
            treadmill = treadmill,
            durationMinutes = durationMinutes,
            distanceMeters = dist,
            routineId = routineId,
            routineName = routineName,
            source = CardioSessionSource.DURATION_TIMER,
            startEpochSeconds = startEpoch,
            endEpochSeconds = endEpoch,
            heartRate = CardioHrScaffolding(),
            estimatedKcal = null
        )
    }

    companion object {
        fun fromRoutine(routine: CardioRoutine) = CardioTimerSessionDraft(
            title = routine.name,
            activity = routine.activity,
            modality = routine.modality,
            treadmill = routine.treadmill,
            startEpoch = nowEpochSeconds(),
            routineId = routine.id,
            routineName = routine.name
        )

        fun fromQuickSnapshot(
            activity: CardioActivitySnapshot,
            modality: CardioModality,
            treadmill: CardioTreadmillParams?,
            title: String
        ) = CardioTimerSessionDraft(
            title = title,
            activity = activity,
            modality = modality,
            treadmill = treadmill,
            startEpoch = nowEpochSeconds(),
            routineId = null,
            routineName = null
        )
    }
}

fun CardioLibraryState.resolveSnapshot(
    builtin: CardioBuiltinActivity?,
    customId: String?
): CardioActivitySnapshot {
    if (customId != null) {
        val ct = customTypeById(customId)
        return CardioActivitySnapshot(
            builtin = null,
            customTypeId = customId,
            customName = ct?.name,
            displayLabel = ct?.name ?: "Custom"
        )
    }
    val b = builtin ?: CardioBuiltinActivity.OTHER
    return CardioActivitySnapshot(
        builtin = b,
        customTypeId = null,
        customName = null,
        displayLabel = b.displayName()
    )
}

/** Derive distance from treadmill speed × duration when distance not provided. */
fun derivedTreadmillDistanceMeters(params: CardioTreadmillParams, durationMinutes: Int): Double? {
    if (params.distanceMeters != null) return null
    if (durationMinutes <= 0 || params.speed <= 0) return null
    val hours = durationMinutes / 60.0
    val kmh = when (params.speedUnit) {
        CardioSpeedUnit.KMH -> params.speed
        CardioSpeedUnit.MPH -> params.speed * 1.60934
    }
    return kmh * hours * 1000.0
}
