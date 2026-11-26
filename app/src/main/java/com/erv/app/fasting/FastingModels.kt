package com.erv.app.fasting

import com.erv.app.SectionLogDateFilter
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.max

@Serializable
enum class FastingStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED,
}

@Serializable
enum class FastingMood {
    GREAT,
    GOOD,
    OKAY,
    HARD,
}

@Serializable
enum class FastingSessionKind {
    EXTENDED,
    INTERMITTENT,
}

@Serializable
enum class IntermittentFastingPhase {
    FASTING,
    EATING,
}

/**
 * A daily fasting schedule. The eating window starts at [eatingStartMinutes] (minute of day) and
 * lasts [eatingMinutes] (or [eatingHours] for plans saved before minutes existed); the fast is the
 * rest of the day. A custom schedule such as 7:00 PM to 10:30 AM is eating 10:30 AM, 510 minutes.
 */
@Serializable
data class IntermittentFastingPlan(
    val protocolLabel: String = "16:8",
    val fastingHours: Int = 16,
    val eatingHours: Int = 8,
    val eatingStartMinutes: Int = 12 * 60,
    /** Eating window length in minutes (15-minute steps); 0 = use [eatingHours]. */
    val eatingMinutes: Int = 0,
    /** Log each finished fasting window automatically. */
    val autoLog: Boolean = false,
    /** Only windows that end after this are logged automatically (when auto-log was turned on). */
    val autoLogSinceEpochSeconds: Long = 0L,
    val lastModifiedEpochSeconds: Long = 0L,
)

data class IntermittentFastingStatus(
    val phase: IntermittentFastingPhase,
    val currentStartEpochSeconds: Long,
    val currentEndEpochSeconds: Long,
    val completedFastStartEpochSeconds: Long?,
    val completedFastEndEpochSeconds: Long?,
)

@Serializable
data class FastingSession(
    val id: String = UUID.randomUUID().toString(),
    val targetDays: Int,
    val startedAtEpochSeconds: Long,
    val targetEndEpochSeconds: Long,
    val status: FastingStatus = FastingStatus.ACTIVE,
    val endedAtEpochSeconds: Long? = null,
    val mood: FastingMood? = null,
    val weight: String = "",
    val notes: String = "",
    val kind: FastingSessionKind = FastingSessionKind.EXTENDED,
    val protocolLabel: String = "",
    val fastingHours: Int = 0,
    val eatingHours: Int = 0,
    /** Target length in hours (24, 48, a custom number); 0 = [targetDays] days (older fasts). */
    val targetHours: Int = 0,
    /** Bumped on every local change; newer wins when merging with the relay copy. */
    val lastModifiedEpochSeconds: Long = 0L,
)

@Serializable
data class FastingLibraryState(
    val activeSession: FastingSession? = null,
    val history: List<FastingSession> = emptyList(),
    val intermittentPlan: IntermittentFastingPlan = IntermittentFastingPlan(),
    /**
     * Deleted fasts: session id to deleted-at epoch seconds. A session is dropped unless edited after
     * its marker, so a delete sticks across devices and an auto-logged window is not logged again.
     */
    val deletedSessionIds: Map<String, Long> = emptyMap(),
)

/** One day's fasts on the relay (`erv/fasting/<date>`), dated like the log ([logDate]). */
@Serializable
data class FastingDayLog(
    val date: String,
    val sessions: List<FastingSession> = emptyList(),
)

data class DatedFastingSession(
    val date: LocalDate,
    val session: FastingSession,
)

fun fastingNowEpochSeconds(): Long = Instant.now().epochSecond

fun fastingTargetSeconds(days: Int): Long = days.coerceIn(1, 3) * 24L * 60L * 60L

/** Longest custom target, a week. */
const val FASTING_MAX_TARGET_HOURS = 168

/** The fast's target in hours, for old day-based fasts too. */
fun FastingSession.targetHoursTotal(): Int =
    if (targetHours > 0) targetHours else targetDays.coerceAtLeast(1) * 24

/** Whole days to store in [FastingSession.targetDays] for readers that only know days. */
fun targetDaysForHours(hours: Int): Int = ((hours + 23) / 24).coerceAtLeast(1)

/** "36 h", or "2 days" for a whole number of days. */
fun formatTargetHours(hours: Int): String =
    if (hours >= 24 && hours % 24 == 0) {
        val d = hours / 24
        if (d == 1) "24 h" else "$d days"
    } else {
        "$hours h"
    }

fun FastingSession.elapsedSeconds(nowEpochSeconds: Long = fastingNowEpochSeconds()): Long =
    max(0L, (endedAtEpochSeconds ?: nowEpochSeconds) - startedAtEpochSeconds)

fun FastingSession.remainingSeconds(nowEpochSeconds: Long = fastingNowEpochSeconds()): Long =
    max(0L, targetEndEpochSeconds - nowEpochSeconds)

fun FastingSession.progress(nowEpochSeconds: Long = fastingNowEpochSeconds()): Float {
    val total = max(1L, targetEndEpochSeconds - startedAtEpochSeconds)
    return (elapsedSeconds(nowEpochSeconds).toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

fun formatFastingDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0L) return "0m"
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    return when {
        days > 0L && hours > 0L -> "${days}d ${hours}h"
        days > 0L -> "${days}d"
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }
}

fun formatFastingDateTime(epochSeconds: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
    return Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(formatter)
}

fun FastingMood.displayName(): String = when (this) {
    FastingMood.GREAT -> "Great"
    FastingMood.GOOD -> "Good"
    FastingMood.OKAY -> "Okay"
    FastingMood.HARD -> "Hard"
}

private const val MINUTES_PER_DAY = 24 * 60

/** Rounds a minute of day (or a length) to the nearest 15 minutes. */
fun roundToQuarterHour(minutes: Int): Int = ((minutes + 7).floorDiv(15)) * 15

fun IntermittentFastingPlan.normalized(): IntermittentFastingPlan {
    val rawEating = if (eatingMinutes > 0) eatingMinutes else eatingHours.coerceIn(1, 23) * 60
    val eatingLen = roundToQuarterHour(rawEating).coerceIn(15, MINUTES_PER_DAY - 15)
    val eatingH = ((eatingLen + 30) / 60).coerceIn(1, 23)
    return copy(
        fastingHours = (24 - eatingH).coerceIn(1, 23),
        eatingHours = eatingH,
        eatingMinutes = eatingLen,
        eatingStartMinutes = roundToQuarterHour(eatingStartMinutes).floorMod(MINUTES_PER_DAY),
        protocolLabel = protocolLabel.ifBlank { "Custom" },
    )
}

/** Eating window length in minutes. */
fun IntermittentFastingPlan.eatingLengthMinutes(): Int = normalized().eatingMinutes

/** Fasting window length in minutes. */
fun IntermittentFastingPlan.fastingLengthMinutes(): Int = MINUTES_PER_DAY - eatingLengthMinutes()

fun IntermittentFastingPlan.eatingEndMinutes(): Int =
    (normalized().eatingStartMinutes + eatingLengthMinutes()).floorMod(MINUTES_PER_DAY)

/** Minute of day the fast starts (the eating window's end). */
fun IntermittentFastingPlan.fastStartMinutes(): Int = eatingEndMinutes()

/** Minute of day the fast ends (the eating window's start). */
fun IntermittentFastingPlan.fastEndMinutes(): Int = normalized().eatingStartMinutes

/** Preset schedules by fasting hours; the label matches the old chips. */
val INTERMITTENT_PRESETS: List<Pair<String, Int>> =
    listOf("14:10" to 14, "16:8" to 16, "18:6" to 18, "20:4" to 20, "OMAD" to 23)

/**
 * A plan that fasts from [fastStartMinute] to [fastEndMinute] (minutes of day, rounded to 15),
 * keeping the auto-log settings of this plan. Named after a preset when the length matches one.
 */
fun IntermittentFastingPlan.withFastWindow(fastStartMinute: Int, fastEndMinute: Int): IntermittentFastingPlan {
    val start = roundToQuarterHour(fastStartMinute).floorMod(MINUTES_PER_DAY)
    val end = roundToQuarterHour(fastEndMinute).floorMod(MINUTES_PER_DAY)
    val fastLen = (end - start).floorMod(MINUTES_PER_DAY).let { if (it == 0) MINUTES_PER_DAY - 15 else it }
        .coerceIn(15, MINUTES_PER_DAY - 15)
    val label = INTERMITTENT_PRESETS.firstOrNull { it.second * 60 == fastLen }?.first ?: "Custom"
    return copy(
        protocolLabel = label,
        eatingStartMinutes = end,
        eatingMinutes = MINUTES_PER_DAY - fastLen,
    ).normalized()
}

/** A preset length that keeps the fast's end time (when you start eating). */
fun IntermittentFastingPlan.withPreset(label: String, fastingHours: Int): IntermittentFastingPlan =
    copy(
        protocolLabel = label,
        eatingMinutes = (24 - fastingHours.coerceIn(1, 23)) * 60,
    ).normalized()

/** "15h 30m" style length for a number of minutes. */
fun formatMinutesLength(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

/** Deterministic id of the intermittent fast from [startEpochSeconds] to [endEpochSeconds]. */
fun intermittentWindowId(startEpochSeconds: Long, endEpochSeconds: Long): String =
    "if-$startEpochSeconds-$endEpochSeconds"

/**
 * Fasting windows of [this] plan that ended after [afterEpochSeconds] and by [nowEpochSeconds],
 * oldest first, as (start, end) epoch seconds. Each ends at an eating-window start.
 */
fun IntermittentFastingPlan.finishedFastWindows(
    afterEpochSeconds: Long,
    nowEpochSeconds: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Pair<Long, Long>> {
    if (nowEpochSeconds <= afterEpochSeconds) return emptyList()
    val plan = normalized()
    val fastLenSeconds = plan.fastingLengthMinutes() * 60L
    val firstDay = Instant.ofEpochSecond(afterEpochSeconds).atZone(zone).toLocalDate().minusDays(1)
    val lastDay = Instant.ofEpochSecond(nowEpochSeconds).atZone(zone).toLocalDate()
    val out = mutableListOf<Pair<Long, Long>>()
    var d = firstDay
    while (!d.isAfter(lastDay)) {
        val end = d.atMinuteOfDay(plan.eatingStartMinutes, zone).toEpochSecond()
        if (end in (afterEpochSeconds + 1)..nowEpochSeconds) out += (end - fastLenSeconds) to end
        d = d.plusDays(1)
    }
    return out
}

fun IntermittentFastingPlan.currentStatus(nowEpochSeconds: Long = fastingNowEpochSeconds()): IntermittentFastingStatus {
    val plan = normalized()
    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochSecond(nowEpochSeconds).atZone(zone)
    val windows = (-2..2).map { offset ->
        val date = now.toLocalDate().plusDays(offset.toLong())
        val start = date.atMinuteOfDay(plan.eatingStartMinutes, zone)
        start to start.plusMinutes(plan.eatingMinutes.toLong())
    }.sortedBy { it.first.toEpochSecond() }

    val currentEating = windows.firstOrNull { (start, end) ->
        !now.isBefore(start) && now.isBefore(end)
    }
    if (currentEating != null) {
        val previousEating = windows.lastOrNull { it.second <= currentEating.first }
        return IntermittentFastingStatus(
            phase = IntermittentFastingPhase.EATING,
            currentStartEpochSeconds = currentEating.first.toEpochSecond(),
            currentEndEpochSeconds = currentEating.second.toEpochSecond(),
            completedFastStartEpochSeconds = previousEating?.second?.toEpochSecond(),
            completedFastEndEpochSeconds = currentEating.first.toEpochSecond(),
        )
    }

    val previousEating = windows.last { it.second <= now }
    val nextEating = windows.first { it.first > now }
    return IntermittentFastingStatus(
        phase = IntermittentFastingPhase.FASTING,
        currentStartEpochSeconds = previousEating.second.toEpochSecond(),
        currentEndEpochSeconds = nextEating.first.toEpochSecond(),
        completedFastStartEpochSeconds = null,
        completedFastEndEpochSeconds = null,
    )
}

private fun LocalDate.atMinuteOfDay(minuteOfDay: Int, zone: ZoneId): ZonedDateTime =
    atTime(LocalTime.of(minuteOfDay.floorMod(24 * 60) / 60, minuteOfDay.floorMod(60))).atZone(zone)

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

fun FastingSession.logDate(): LocalDate =
    Instant.ofEpochSecond(endedAtEpochSeconds ?: startedAtEpochSeconds)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

fun FastingLibraryState.datedFastingSessionsForSectionLog(filter: SectionLogDateFilter): List<DatedFastingSession> {
    fun include(date: LocalDate): Boolean = when (filter) {
        SectionLogDateFilter.AllHistory -> true
        is SectionLogDateFilter.SingleDay -> date == filter.day
        is SectionLogDateFilter.DateRange -> !date.isBefore(filter.startInclusive) && !date.isAfter(filter.endInclusive)
    }

    return history
        .map { DatedFastingSession(it.logDate(), it) }
        .filter { include(it.date) }
        .sortedWith(
            compareByDescending<DatedFastingSession> { it.date }
                .thenByDescending { it.session.endedAtEpochSeconds ?: it.session.startedAtEpochSeconds }
        )
}

fun datesWithFastingActivity(state: FastingLibraryState): Set<LocalDate> =
    state.history.mapTo(mutableSetOf()) { it.logDate() }

/** History grouped into relay day logs, dated by [logDate]. */
fun FastingLibraryState.dayLogs(): List<FastingDayLog> =
    history.groupBy { it.logDate().toString() }
        .map { (date, sessions) ->
            FastingDayLog(date, sessions.sortedBy { it.endedAtEpochSeconds ?: it.startedAtEpochSeconds })
        }
        .sortedBy { it.date }

/** Delete markers older than this are dropped. */
private const val FASTING_DELETE_MARKER_TTL_SECONDS = 400L * 24 * 60 * 60

/**
 * Merges this device's fasting state with the relay copy: newest [FastingSession.lastModifiedEpochSeconds]
 * wins per session (ties keep local), delete markers are combined and drop older copies, the newer
 * plan wins, and an active fast already in history (finished elsewhere) is dropped.
 */
fun mergeFastingStates(
    local: FastingLibraryState,
    remote: FastingLibraryState,
    nowEpochSeconds: Long = fastingNowEpochSeconds(),
): FastingLibraryState {
    val deleted = (local.deletedSessionIds.keys + remote.deletedSessionIds.keys).associateWith { id ->
        maxOf(local.deletedSessionIds[id] ?: 0L, remote.deletedSessionIds[id] ?: 0L)
    }.filterValues { nowEpochSeconds - it < FASTING_DELETE_MARKER_TTL_SECONDS }

    fun alive(s: FastingSession): Boolean {
        val marker = deleted[s.id] ?: return true
        return s.lastModifiedEpochSeconds > marker
    }

    val byId = LinkedHashMap<String, FastingSession>()
    remote.history.forEach { byId[it.id] = it }
    local.history.forEach { l ->
        val r = byId[l.id]
        byId[l.id] = if (r == null || l.lastModifiedEpochSeconds >= r.lastModifiedEpochSeconds) l else r
    }
    val history = byId.values.filter(::alive)
        .sortedByDescending { it.endedAtEpochSeconds ?: it.startedAtEpochSeconds }
    val historyIds = history.mapTo(mutableSetOf()) { it.id }

    val active = listOfNotNull(local.activeSession, remote.activeSession)
        .filter { it.id !in historyIds && alive(it) && it.status == FastingStatus.ACTIVE }
        .maxByOrNull { it.lastModifiedEpochSeconds }
        ?.let { best ->
            // Prefer local on a tie.
            val l = local.activeSession
            if (l != null && l.id !in historyIds && alive(l) && l.lastModifiedEpochSeconds == best.lastModifiedEpochSeconds) l else best
        }

    val plan = if (remote.intermittentPlan.lastModifiedEpochSeconds > local.intermittentPlan.lastModifiedEpochSeconds) {
        remote.intermittentPlan
    } else {
        local.intermittentPlan
    }

    return FastingLibraryState(
        activeSession = active,
        history = history,
        intermittentPlan = plan,
        deletedSessionIds = deleted,
    )
}
