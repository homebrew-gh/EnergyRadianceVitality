package com.erv.app.cardio

import com.erv.app.SectionLogDateFilter
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
    CardioBuiltinActivity.STATIONARY_BIKE -> "Stationary Bike"
    CardioBuiltinActivity.JUMP_ROPE -> "Jump Rope"
    CardioBuiltinActivity.OTHER -> "Other"
}

@Serializable
enum class CardioModality {
    OUTDOOR, INDOOR_TREADMILL
}

fun CardioModality.label(): String = when (this) {
    CardioModality.OUTDOOR -> "Outdoor"
    CardioModality.INDOOR_TREADMILL -> "Indoor"
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

/** M:SS per mile or per km; null if time or distance unusable. */
fun formatCardioAveragePace(elapsedSeconds: Int, distanceMeters: Double, unit: CardioDistanceUnit): String? {
    if (elapsedSeconds <= 0 || distanceMeters <= 1.0 || !distanceMeters.isFinite()) return null
    val distInUnit = when (unit) {
        CardioDistanceUnit.MILES -> distanceMeters / 1609.344
        CardioDistanceUnit.KILOMETERS -> distanceMeters / 1000.0
    }
    if (distInUnit <= 0 || !distInUnit.isFinite()) return null
    val minutesPerUnit = (elapsedSeconds / 60.0) / distInUnit
    if (!minutesPerUnit.isFinite() || minutesPerUnit <= 0) return null
    val totalSec = (minutesPerUnit * 60.0).toInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    val pace = "%d:%02d".format(m, s)
    return when (unit) {
        CardioDistanceUnit.MILES -> "$pace /mi"
        CardioDistanceUnit.KILOMETERS -> "$pace /km"
    }
}

/**
 * Uses exact timer seconds when [elapsedSecondsFromTimer] is set; otherwise whole logged minutes × 60
 * (slightly coarse for pace).
 */
fun formatCardioAveragePaceForSession(
    session: CardioSession,
    distanceUnit: CardioDistanceUnit,
    elapsedSecondsFromTimer: Int?
): String? {
    val dist = session.distanceMeters?.takeIf { it > 1.0 } ?: return null
    val sec = when {
        elapsedSecondsFromTimer != null && elapsedSecondsFromTimer > 0 -> elapsedSecondsFromTimer
        session.durationMinutes > 0 -> session.durationMinutes * 60
        else -> return null
    }
    return formatCardioAveragePace(sec, dist, distanceUnit)
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
    MANUAL,
    DURATION_TIMER,
    /** Merged from JSON/CSV file import (Settings → Import / export). */
    IMPORTED,
}

@Serializable
data class CardioHrSample(
    val epochSeconds: Long,
    val bpm: Int
)

/** Session-level heart rate summary (from BLE during live cardio, import, or manual entry later). */
@Serializable
data class CardioHrScaffolding(
    val avgBpm: Int? = null,
    val maxBpm: Int? = null,
    val minBpm: Int? = null,
    /** Time series during live BLE capture; included in encrypted data-relay backup. */
    val samples: List<CardioHrSample> = emptyList()
)

@Serializable
data class CardioActivitySnapshot(
    val builtin: CardioBuiltinActivity? = null,
    val customTypeId: String? = null,
    val customName: String? = null,
    val displayLabel: String
)

/** Outdoor walk, run, hike, ruck — phone GPS is offered when modality is outdoor and user opts in. */
fun CardioActivitySnapshot.supportsPhoneGpsTracking(): Boolean {
    val b = builtin ?: return false
    return when (b) {
        CardioBuiltinActivity.WALK,
        CardioBuiltinActivity.RUN,
        CardioBuiltinActivity.HIKE,
        CardioBuiltinActivity.RUCK -> true
        else -> false
    }
}

@Serializable
data class CardioGpsPoint(
    val lat: Double,
    val lon: Double,
    val epochSeconds: Long,
    /** WGS84 ellipsoidal height when the device reported altitude (meters). */
    val altitudeMeters: Double? = null
)

@Serializable
data class CardioGpsTrack(
    val points: List<CardioGpsPoint> = emptyList(),
    val source: String = "phone_gps",
    val recordedAtVersion: Int = 1
)

fun CardioTimerSessionDraft.eligibleForPhoneGps(): Boolean =
    modality == CardioModality.OUTDOOR && activity.supportsPhoneGpsTracking()

/** Strip GPS paths before syncing cardio logs to data relays (heart rate stays full for encrypted backup). */
fun CardioDayLog.withoutGpsTracks(): CardioDayLog =
    copy(
        sessions = sessions.map { session ->
            session.copy(gpsTrack = null)
        }
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
    val segments: List<CardioSessionSegment> = emptyList(),
    /**
     * Local-only detailed path; omitted when publishing daily logs to Nostr.
     * May be cleared on save when the user turns off “Keep detailed GPS track” in Settings.
     */
    val gpsTrack: CardioGpsTrack? = null,
    /** Public URL of the route map image (same upload as “Share Workout” / Blossom or NIP-96). */
    val routeImageUrl: String? = null,
    /** Outdoor ruck pack weight (kg). Indoor ruck uses [treadmill].loadKg instead. */
    val ruckLoadKg: Double? = null,
    /**
     * Cumulative elevation gain/loss (meters) from GPS when recorded; also used in log summary and social post.
     * Null if unknown; [resolvedElevationMeters] can recompute from [gpsTrack] for older sessions.
     */
    val elevationGainMeters: Double? = null,
    val elevationLossMeters: Double? = null
)

/** Prefer stored values; otherwise derive from [CardioSession.gpsTrack] points. */
fun CardioSession.resolvedElevationMeters(): Pair<Double, Double>? {
    val g = elevationGainMeters
    val l = elevationLossMeters
    if (g != null && l != null) return g to l
    val pts = gpsTrack?.points ?: return null
    return CardioGpsElevation.computeGainLossMeters(pts)
}

/** Pack weight for display / MET: outdoor field or treadmill load. */
fun CardioSession.ruckLoadKgResolved(): Double? =
    (ruckLoadKg ?: treadmill?.loadKg)?.takeIf { it > 0.0 }

fun formatCardioPackWeightFromKg(kg: Double): String {
    val lb = kg / 0.453592
    return String.format("%.0f lb (%.1f kg)", lb, kg)
}

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

/**
 * Saved shortcut for “start this timer now” — single activity, full timer prefs.
 * Unlike [CardioRoutine], this is not for multi-leg workouts or repeat-day scheduling.
 */
@Serializable
enum class CardioQuickTimerMode {
    COUNT_UP,
    COUNT_DOWN
}

@Serializable
data class CardioQuickLaunch(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val activity: CardioActivitySnapshot,
    val modality: CardioModality = CardioModality.OUTDOOR,
    val treadmill: CardioTreadmillParams? = null,
    val timerMode: CardioQuickTimerMode = CardioQuickTimerMode.COUNT_UP,
    /** Used when [timerMode] is [CardioQuickTimerMode.COUNT_DOWN]. */
    val countDownMinutes: Int? = null,
    val outdoorPaceSpeed: Double? = null,
    val outdoorPaceSpeedUnit: CardioSpeedUnit? = null,
    /** Default outdoor ruck pack (kg); user can change when starting. */
    val defaultRuckLoadKg: Double? = null
)

fun CardioQuickLaunch.toTimerStyle(): CardioTimerStyle = when (timerMode) {
    CardioQuickTimerMode.COUNT_UP -> CardioTimerStyle.CountUp
    CardioQuickTimerMode.COUNT_DOWN -> CardioTimerStyle.CountDown(
        ((countDownMinutes ?: 30).coerceIn(1, 24 * 60)) * 60
    )
}

fun CardioQuickLaunch.summaryLabel(distanceUnit: CardioDistanceUnit): String = buildString {
    append(name)
    append(" • ")
    append(activity.displayLabel)
    append(" • ")
    append(modality.label())
    when (timerMode) {
        CardioQuickTimerMode.COUNT_UP -> append(" • count up")
        CardioQuickTimerMode.COUNT_DOWN -> append(" • ${countDownMinutes ?: 30} min timer")
    }
    defaultRuckLoadKg?.takeIf { it > 0 }?.let {
        append(" • ")
        append(formatCardioPackWeightFromKg(it))
    }
}

/** Outdoor ruck: confirm pack weight each time (default from template). */
fun CardioQuickLaunch.needsOutdoorRuckWeightPrompt(): Boolean =
    activity.builtin == CardioBuiltinActivity.RUCK && modality == CardioModality.OUTDOOR

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
    val quickLaunches: List<CardioQuickLaunch> = emptyList(),
    val logs: List<CardioDayLog> = emptyList()
) {
    fun logFor(date: LocalDate): CardioDayLog? = logs.firstOrNull { it.date == date.toString() }
    fun customTypeById(id: String): CardioCustomActivityType? =
        customActivityTypes.firstOrNull { it.id == id }
}

data class CardioActivityRow(val summaryLine: String)

fun CardioLibraryState.chronologicalCardioLogFor(date: LocalDate): List<CardioSession> {
    val log = logFor(date) ?: return emptyList()
    return log.sessions.sortedWith(
        compareBy<CardioSession> { it.loggedAtEpochSeconds }.thenBy { it.id }
    )
}

data class DatedCardioSession(val logDate: LocalDate, val session: CardioSession)

fun CardioLibraryState.chronologicalCardioLogForRange(start: LocalDate, end: LocalDate): List<DatedCardioSession> {
    val from = if (start <= end) start else end
    val to = if (start <= end) end else start
    val rows = mutableListOf<DatedCardioSession>()
    var d = from
    while (!d.isAfter(to)) {
        chronologicalCardioLogFor(d).forEach { rows.add(DatedCardioSession(d, it)) }
        d = d.plusDays(1)
    }
    return rows.sortedWith(
        compareBy<DatedCardioSession> { it.logDate }
            .thenBy { it.session.loggedAtEpochSeconds }
            .thenBy { it.session.id }
    )
}

private fun List<DatedCardioSession>.sortedCardioNewestFirst(): List<DatedCardioSession> =
    sortedWith(
        compareByDescending<DatedCardioSession> { it.session.loggedAtEpochSeconds }
            .thenByDescending { it.logDate }
            .thenBy { it.session.id }
    )

fun CardioLibraryState.datedCardioSessionsForSectionLog(filter: SectionLogDateFilter): List<DatedCardioSession> =
    when (filter) {
        SectionLogDateFilter.AllHistory -> {
            val rows = mutableListOf<DatedCardioSession>()
            for (dl in logs) {
                val d = LocalDate.parse(dl.date)
                dl.sessions.forEach { rows.add(DatedCardioSession(d, it)) }
            }
            rows.sortedCardioNewestFirst()
        }
        is SectionLogDateFilter.SingleDay ->
            chronologicalCardioLogFor(filter.day).map { DatedCardioSession(filter.day, it) }.sortedCardioNewestFirst()
        is SectionLogDateFilter.DateRange ->
            chronologicalCardioLogForRange(filter.startInclusive, filter.endInclusive).sortedCardioNewestFirst()
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
            resolvedElevationMeters()?.let { (gain, loss) ->
                append(formatCardioElevationSummarySuffix(gain, loss, distanceUnit))
            }
            estimatedKcal?.let { k -> append(" • ~${k.toInt()} kcal") }
        }
    }
    return buildString {
        append(activity.displayLabel)
        if (modality == CardioModality.INDOOR_TREADMILL) append(" (indoor)")
        append(" • ${durationMinutes} min")
        treadmill?.let { t ->
            append(" • ${formatSpeed(t.speed, t.speedUnit)}")
            if (t.inclinePercent > 0.01) append(" • ${t.inclinePercent.toInt()}% incline")
            t.loadKg?.let { kg ->
                append(" • ${formatCardioPackWeightFromKg(kg)}")
            }
        }
        if (modality == CardioModality.OUTDOOR && activity.builtin == CardioBuiltinActivity.RUCK) {
            ruckLoadKg?.takeIf { it > 0 }?.let { kg ->
                append(" • ${formatCardioPackWeightFromKg(kg)}")
            }
        }
        distanceMeters?.let { d ->
            if (d > 1) append(" • ${formatCardioDistanceFromMeters(d, distanceUnit)}")
        }
        resolvedElevationMeters()?.let { (gain, loss) ->
            append(formatCardioElevationSummarySuffix(gain, loss, distanceUnit))
        }
        estimatedKcal?.let { k ->
            append(" • ~${k.toInt()} kcal")
        }
    }
}

private fun formatSpeed(speed: Double, unit: CardioSpeedUnit): String =
    if (unit == CardioSpeedUnit.MPH) String.format("%.1f mph", speed)
    else String.format("%.1f km/h", speed)

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
    /** Optional outdoor average speed for a live pace × time distance estimate (separate from optional GPS route). */
    val outdoorPaceSpeed: Double? = null,
    val outdoorPaceSpeedUnit: CardioSpeedUnit? = null,
    /** Outdoor ruck pack (kg); indoor ruck uses [treadmill].loadKg. */
    val ruckLoadKg: Double? = null
) {
    fun toSession(
        durationMinutes: Int,
        endEpoch: Long,
        elapsedSecondsForDistance: Int? = null,
        gpsPoints: List<CardioGpsPoint> = emptyList()
    ): CardioSession {
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
        val track = if (gpsPoints.size >= 2) CardioGpsTrack(points = gpsPoints) else null
        val elevPair = track?.points?.let { CardioGpsElevation.computeGainLossMeters(it) }
        val gpsPathM = track?.let { CardioGpsMath.pathLengthMeters(it.points) } ?: 0.0
        val useGpsDistance = track != null &&
            gpsPathM >= CardioGpsDistanceRules.MIN_PATH_METERS &&
            gpsPoints.size >= CardioGpsDistanceRules.MIN_POINTS
        if (useGpsDistance) {
            dist = gpsPathM
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
            segments = emptyList(),
            gpsTrack = track,
            ruckLoadKg = ruckLoadKg,
            elevationGainMeters = elevPair?.first,
            elevationLossMeters = elevPair?.second
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
                outdoorPaceSpeedUnit = null,
                ruckLoadKg = null
            )
        }

        fun fromQuickSnapshot(
            activity: CardioActivitySnapshot,
            modality: CardioModality,
            treadmill: CardioTreadmillParams?,
            title: String,
            timerStyle: CardioTimerStyle = CardioTimerStyle.CountUp,
            outdoorPaceSpeed: Double? = null,
            outdoorPaceSpeedUnit: CardioSpeedUnit? = null,
            ruckLoadKg: Double? = null
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
            outdoorPaceSpeedUnit = outdoorPaceSpeedUnit,
            ruckLoadKg = ruckLoadKg
        )

        fun fromActivitySnapshot(snapshot: CardioActivitySnapshot) = fromQuickSnapshot(
            activity = snapshot,
            modality = CardioModality.OUTDOOR,
            treadmill = null,
            title = snapshot.displayLabel
        )

        fun fromQuickLaunch(quick: CardioQuickLaunch, ruckLoadKg: Double? = quick.defaultRuckLoadKg) =
            fromQuickSnapshot(
                activity = quick.activity,
                modality = quick.modality,
                treadmill = quick.treadmill,
                title = quick.name,
                timerStyle = quick.toTimerStyle(),
                outdoorPaceSpeed = quick.outdoorPaceSpeed,
                outdoorPaceSpeedUnit = quick.outdoorPaceSpeedUnit,
                ruckLoadKg = ruckLoadKg
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

/** Epoch seconds when the overall cardio timer session began (notification chronometer / bubble). */
fun CardioActiveTimerSession.timerStartEpochSeconds(): Long = when (this) {
    is CardioActiveTimerSession.Single -> draft.startEpoch
    is CardioActiveTimerSession.Multi -> state.workoutStartEpoch
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
