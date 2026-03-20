package com.erv.app.cardio

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

@Serializable
enum class CardioBuiltinActivity {
    WALK, RUN, SPRINT, RUCK, HIKE, BIKE, SWIM, ELLIPTICAL, ROWING,
    STATIONARY_BIKE,
    JUMP_ROPE,
    OTHER
}

fun CardioBuiltinActivity.supportsTreadmillModality(): Boolean = when (this) {
    CardioBuiltinActivity.WALK, CardioBuiltinActivity.RUN,
    CardioBuiltinActivity.SPRINT, CardioBuiltinActivity.RUCK -> true
    else -> false
}

/** Outdoor pace-based distance estimate: walk, run, bike, etc. (not swim / machine-only types). */
fun CardioActivitySnapshot.supportsOutdoorPaceEstimate(): Boolean {
    val b = builtin ?: return true
    return when (b) {
        CardioBuiltinActivity.SWIM, CardioBuiltinActivity.ELLIPTICAL,
        CardioBuiltinActivity.ROWING, CardioBuiltinActivity.STATIONARY_BIKE,
        CardioBuiltinActivity.JUMP_ROPE -> false
        else -> true
    }
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
    CardioBuiltinActivity.STATIONARY_BIKE -> "Stationary bike"
    CardioBuiltinActivity.JUMP_ROPE -> "Jump rope"
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

/** Display / entry for distance fields and summaries (stored values remain meters). */
enum class CardioDistanceUnit {
    MILES,
    KILOMETERS
}

fun CardioDistanceUnit.distanceFieldLabelOptional(): String = when (this) {
    CardioDistanceUnit.MILES -> "Distance (mi, optional)"
    CardioDistanceUnit.KILOMETERS -> "Distance (km, optional)"
}

fun formatCardioDistanceFromMeters(meters: Double, unit: CardioDistanceUnit): String =
    when (unit) {
        CardioDistanceUnit.MILES -> String.format("%.2f mi", meters / 1609.344)
        CardioDistanceUnit.KILOMETERS -> String.format("%.2f km", meters / 1000.0)
    }

fun parseCardioDistanceInputToMeters(value: Double, unit: CardioDistanceUnit): Double =
    when (unit) {
        CardioDistanceUnit.MILES -> value * 1609.344
        CardioDistanceUnit.KILOMETERS -> value * 1000.0
    }

fun metersToCardioDistanceInputString(meters: Double, unit: CardioDistanceUnit): String =
    String.format(
        "%.2f",
        when (unit) {
            CardioDistanceUnit.MILES -> meters / 1609.344
            CardioDistanceUnit.KILOMETERS -> meters / 1000.0
        }
    )

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
data class CardioSessionSegment(
    val id: String = UUID.randomUUID().toString(),
    val activity: CardioActivitySnapshot,
    val modality: CardioModality = CardioModality.OUTDOOR,
    val treadmill: CardioTreadmillParams? = null,
    val durationMinutes: Int,
    val distanceMeters: Double? = null,
    val estimatedKcal: Double? = null,
    val orderIndex: Int = 0
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
    val heartRate: CardioHrScaffolding? = null,
    /** Non-empty = multi-activity workout (brick, tri prep, etc.); top-level fields are rollups. */
    val segments: List<CardioSessionSegment> = emptyList()
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
data class CardioRoutineStep(
    val id: String = UUID.randomUUID().toString(),
    val activity: CardioActivitySnapshot,
    val modality: CardioModality = CardioModality.OUTDOOR,
    val treadmill: CardioTreadmillParams? = null,
    val targetDurationMinutes: Int? = null,
    val orderIndex: Int = 0
)

@Serializable
data class CardioRoutine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Ordered legs; empty means legacy single-activity fields below apply. */
    val steps: List<CardioRoutineStep> = emptyList(),
    val activity: CardioActivitySnapshot,
    val modality: CardioModality = CardioModality.OUTDOOR,
    val treadmill: CardioTreadmillParams? = null,
    val targetDurationMinutes: Int? = null,
    val repeatDays: List<CardioWeekday> = emptyList(),
    val notes: String = ""
)

fun CardioRoutine.effectiveSteps(): List<CardioRoutineStep> {
    if (steps.isNotEmpty()) return steps.sortedBy { it.orderIndex }
    return listOf(
        CardioRoutineStep(
            activity = activity,
            modality = modality,
            treadmill = treadmill,
            targetDurationMinutes = targetDurationMinutes,
            orderIndex = 0
        )
    )
}

fun CardioRoutine.stepsSummaryLabel(): String =
    effectiveSteps().joinToString(" → ") { it.activity.displayLabel }

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

fun CardioLibraryState.cardioActivityRowsFor(
    date: LocalDate,
    distanceUnit: CardioDistanceUnit = CardioDistanceUnit.MILES
): List<CardioActivityRow> {
    val log = logFor(date) ?: return emptyList()
    return log.sessions.map { session ->
        CardioActivityRow(summaryLine = session.summaryLine(distanceUnit))
    }
}

fun CardioSession.summaryLine(
    distanceUnit: CardioDistanceUnit = CardioDistanceUnit.MILES
): String {
    if (segments.isNotEmpty()) {
        val ordered = segments.sortedBy { it.orderIndex }
        return buildString {
            append(ordered.joinToString(" → ") { it.activity.displayLabel })
            append(" • ${durationMinutes} min total")
            val dist = distanceMeters
            if (dist != null && dist > 1) append(" • ${formatCardioDistanceFromMeters(dist, distanceUnit)}")
            estimatedKcal?.let { k -> append(" • ~${k.toInt()} kcal") }
        }
    }
    return buildString {
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
            if (d > 1) append(" • ${formatCardioDistanceFromMeters(d, distanceUnit)}")
        }
        estimatedKcal?.let { k ->
            append(" • ~${k.toInt()} kcal")
        }
    }
}

private fun formatSpeed(speed: Double, unit: CardioSpeedUnit): String =
    if (unit == CardioSpeedUnit.MPH) String.format("%.1f mph", speed)
    else String.format("%.1f km/h", speed)

private fun formatLoadLbKg(kg: Double): String {
    val lb = kg / 0.453592
    return String.format("%.0f lb (%.1f kg)", lb, kg)
}

fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

/** Placeholder treadmill params when sprinting indoors without a detail form (pace × time estimates only). */
fun defaultSprintIndoorTreadmillParams(): CardioTreadmillParams = CardioTreadmillParams(
    speed = 10.0,
    speedUnit = CardioSpeedUnit.MPH,
    inclinePercent = 0.0,
    distanceMeters = null,
    loadKg = null
)

sealed class CardioTimerStyle {
    data object CountUp : CardioTimerStyle()
    data class CountDown(val totalSeconds: Int) : CardioTimerStyle()
    /** Outdoor sprint-style: stop when estimated distance covered reaches [targetMeters]. Requires pace on draft. */
    data class CountDownDistance(val targetMeters: Double) : CardioTimerStyle()
}

/** Live session timer state; dashboard and Cardio screen share this. */
data class CardioTimerSessionDraft(
    val title: String,
    val activity: CardioActivitySnapshot,
    val modality: CardioModality,
    val treadmill: CardioTreadmillParams?,
    val startEpoch: Long,
    val routineId: String?,
    val routineName: String?,
    val timerStyle: CardioTimerStyle = CardioTimerStyle.CountUp,
    /** Optional outdoor average speed for a live distance estimate (not GPS). */
    val outdoorPaceSpeed: Double? = null,
    val outdoorPaceSpeedUnit: CardioSpeedUnit? = null
) {
    fun toSession(durationMinutes: Int, endEpoch: Long, elapsedSecondsForDistance: Int? = null): CardioSession {
        val elapsed = elapsedSecondsForDistance ?: (durationMinutes * 60)
        var dist = treadmill?.distanceMeters
        if (dist == null && treadmill != null) {
            dist = derivedTreadmillDistanceMeters(treadmill, max(1, (elapsed + 59) / 60))
        }
        if (dist == null && modality == CardioModality.OUTDOOR &&
            outdoorPaceSpeed != null && outdoorPaceSpeedUnit != null
        ) {
            dist = outdoorDistanceMetersAtElapsed(outdoorPaceSpeed, outdoorPaceSpeedUnit, elapsed)
        }
        if (timerStyle is CardioTimerStyle.CountDownDistance && dist != null) {
            dist = min(dist, timerStyle.targetMeters)
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
            estimatedKcal = null,
            segments = emptyList()
        )
    }

    companion object {
        fun fromRoutine(routine: CardioRoutine): CardioTimerSessionDraft? {
            val legs = routine.effectiveSteps()
            if (legs.size != 1) return null
            val leg = legs.first()
            return CardioTimerSessionDraft(
                title = routine.name,
                activity = leg.activity,
                modality = leg.modality,
                treadmill = leg.treadmill,
                startEpoch = nowEpochSeconds(),
                routineId = routine.id,
                routineName = routine.name,
                timerStyle = CardioTimerStyle.CountUp,
                outdoorPaceSpeed = null,
                outdoorPaceSpeedUnit = null
            )
        }

        fun fromQuickSnapshot(
            activity: CardioActivitySnapshot,
            modality: CardioModality,
            treadmill: CardioTreadmillParams?,
            title: String,
            timerStyle: CardioTimerStyle = CardioTimerStyle.CountUp,
            outdoorPaceSpeed: Double? = null,
            outdoorPaceSpeedUnit: CardioSpeedUnit? = null
        ) = CardioTimerSessionDraft(
            title = title,
            activity = activity,
            modality = modality,
            treadmill = treadmill,
            startEpoch = nowEpochSeconds(),
            routineId = null,
            routineName = null,
            timerStyle = timerStyle,
            outdoorPaceSpeed = outdoorPaceSpeed,
            outdoorPaceSpeedUnit = outdoorPaceSpeedUnit
        )

        fun fromActivitySnapshot(snapshot: CardioActivitySnapshot) = fromQuickSnapshot(
            activity = snapshot,
            modality = CardioModality.OUTDOOR,
            treadmill = null,
            title = snapshot.displayLabel
        )
    }
}

/** Sequential timer for multi-leg routines (brick, tri prep, etc.). */
data class CardioMultiLegTimerState(
    val routineId: String?,
    val routineName: String?,
    val legs: List<CardioRoutineStep>,
    val completedSegments: List<CardioSessionSegment>,
    val currentLegIndex: Int,
    val workoutStartEpoch: Long,
    val legStartedEpoch: Long
) {
    val currentLeg: CardioRoutineStep get() = legs[currentLegIndex]

    fun legProgressLabel(): String =
        "Leg ${currentLegIndex + 1} of ${legs.size}: ${currentLeg.activity.displayLabel}"

    companion object {
        fun fromRoutine(routine: CardioRoutine): CardioMultiLegTimerState? {
            val legs = routine.effectiveSteps()
            if (legs.size <= 1) return null
            val now = nowEpochSeconds()
            return CardioMultiLegTimerState(
                routineId = routine.id,
                routineName = routine.name,
                legs = legs,
                completedSegments = emptyList(),
                currentLegIndex = 0,
                workoutStartEpoch = now,
                legStartedEpoch = now
            )
        }
    }
}

/** Single- or multi-leg timer shown full-screen from Cardio or the dashboard. */
sealed class CardioActiveTimerSession {
    data class Single(val draft: CardioTimerSessionDraft) : CardioActiveTimerSession()
    data class Multi(val state: CardioMultiLegTimerState) : CardioActiveTimerSession()
}

/** Shown after Stop & log; [elapsedSeconds] is exact timer time when from a single-leg timer, else null. */
data class CardioTimerCompletionResult(
    val session: CardioSession,
    val elapsedSeconds: Int?
)

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

/** Live distance from treadmill speed × elapsed seconds (ignores optional fixed distance field). */
fun treadmillLiveDistanceMeters(params: CardioTreadmillParams, elapsedSeconds: Int): Double? {
    if (elapsedSeconds <= 0 || params.speed <= 0) return null
    val hours = elapsedSeconds / 3600.0
    val kmh = when (params.speedUnit) {
        CardioSpeedUnit.KMH -> params.speed
        CardioSpeedUnit.MPH -> params.speed * 1.60934
    }
    return kmh * hours * 1000.0
}

fun outdoorDistanceMetersAtElapsed(speed: Double, unit: CardioSpeedUnit, elapsedSeconds: Int): Double? {
    if (elapsedSeconds <= 0 || speed <= 0) return null
    val hours = elapsedSeconds / 3600.0
    val kmh = when (unit) {
        CardioSpeedUnit.KMH -> speed
        CardioSpeedUnit.MPH -> speed * 1.60934
    }
    return kmh * hours * 1000.0
}

fun CardioTimerSessionDraft.liveDistanceMeters(elapsedSeconds: Int): Double? {
    if (elapsedSeconds <= 0) return null
    if (modality == CardioModality.INDOOR_TREADMILL && treadmill != null) {
        return treadmillLiveDistanceMeters(treadmill, elapsedSeconds)
    }
    if (modality == CardioModality.OUTDOOR && outdoorPaceSpeed != null && outdoorPaceSpeedUnit != null) {
        return outdoorDistanceMetersAtElapsed(outdoorPaceSpeed, outdoorPaceSpeedUnit, elapsedSeconds)
    }
    return null
}
