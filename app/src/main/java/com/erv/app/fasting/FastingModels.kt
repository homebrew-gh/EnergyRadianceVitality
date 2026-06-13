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

@Serializable
data class IntermittentFastingPlan(
    val protocolLabel: String = "16:8",
    val fastingHours: Int = 16,
    val eatingHours: Int = 8,
    val eatingStartMinutes: Int = 12 * 60,
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
)

@Serializable
data class FastingLibraryState(
    val activeSession: FastingSession? = null,
    val history: List<FastingSession> = emptyList(),
    val intermittentPlan: IntermittentFastingPlan = IntermittentFastingPlan(),
)

data class DatedFastingSession(
    val date: LocalDate,
    val session: FastingSession,
)

fun fastingNowEpochSeconds(): Long = Instant.now().epochSecond

fun fastingTargetSeconds(days: Int): Long = days.coerceIn(1, 3) * 24L * 60L * 60L

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

fun IntermittentFastingPlan.normalized(): IntermittentFastingPlan {
    val fasting = fastingHours.coerceIn(1, 23)
    val eating = eatingHours.coerceIn(1, 23)
    return copy(
        fastingHours = fasting,
        eatingHours = eating,
        eatingStartMinutes = eatingStartMinutes.floorMod(24 * 60),
        protocolLabel = protocolLabel.ifBlank { "${fasting}:${eating}" },
    )
}

fun IntermittentFastingPlan.eatingEndMinutes(): Int =
    (normalized().eatingStartMinutes + normalized().eatingHours * 60).floorMod(24 * 60)

fun IntermittentFastingPlan.currentStatus(nowEpochSeconds: Long = fastingNowEpochSeconds()): IntermittentFastingStatus {
    val plan = normalized()
    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochSecond(nowEpochSeconds).atZone(zone)
    val windows = (-2..2).map { offset ->
        val date = now.toLocalDate().plusDays(offset.toLong())
        val start = date.atMinuteOfDay(plan.eatingStartMinutes, zone)
        start to start.plusHours(plan.eatingHours.toLong())
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
