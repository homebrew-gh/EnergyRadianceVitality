package com.erv.app.goals

import com.erv.app.cardio.CardioLibraryState
import com.erv.app.data.AllUserGoalOptions
import com.erv.app.lighttherapy.LightLibraryState
import com.erv.app.supplements.SupplementLibraryState
import com.erv.app.weighttraining.WeightLibraryState
import java.time.LocalDate
import java.time.temporal.WeekFields

/** Default weekly targets; conservative so new users can hit them with light use. */
data class WeeklyGoalDefaults(
    /** Days in the ISO week with at least one supplement log (routine or ad hoc). */
    val supplementActiveDays: Int = 4,
    val lightMinutesTotal: Int = 60,
    val cardioSessions: Int = 2,
    val weightWorkouts: Int = 2,
)

data class GoalProgressRow(
    val goalId: String,
    val title: String,
    val detail: String,
    val current: Int,
    val target: Int,
    val met: Boolean,
) {
    val progress: Float
        get() = if (target <= 0) 0f else (current.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}

/** The seven calendar days of the ISO week containing [day], Monday first. */
fun isoWeekDaysContaining(day: LocalDate): List<LocalDate> {
    val monday = day.with(WeekFields.ISO.dayOfWeek(), 1)
    return (0..6).map { monday.plusDays(it.toLong()) }
}

fun computeWeeklyGoalProgress(
    asOfDate: LocalDate,
    selectedGoalIds: Set<String>,
    supplementState: SupplementLibraryState,
    lightState: LightLibraryState,
    cardioState: CardioLibraryState,
    weightState: WeightLibraryState,
    defaults: WeeklyGoalDefaults = WeeklyGoalDefaults(),
): List<GoalProgressRow> {
    if (selectedGoalIds.isEmpty()) return emptyList()
    val weekDays = isoWeekDaysContaining(asOfDate)

    val supplementActiveDays = weekDays.count { d ->
        val log = supplementState.logFor(d) ?: return@count false
        log.routineRuns.isNotEmpty() || log.adHocIntakes.isNotEmpty()
    }

    val lightMinutes = weekDays.sumOf { d ->
        lightState.logFor(d)?.sessions?.sumOf { it.minutes.coerceAtLeast(0) } ?: 0
    }

    val cardioSessions = weekDays.sumOf { d ->
        cardioState.logFor(d)?.sessions?.size ?: 0
    }

    val weightWorkouts = weekDays.sumOf { d ->
        weightState.logFor(d)?.workouts?.size ?: 0
    }

    val byId = mapOf(
        "supplements_adherence" to GoalProgressRow(
            goalId = "supplements_adherence",
            title = "Supplement routines",
            detail = "Days this week with something logged",
            current = supplementActiveDays,
            target = defaults.supplementActiveDays,
            met = supplementActiveDays >= defaults.supplementActiveDays,
        ),
        "light_therapy_time" to GoalProgressRow(
            goalId = "light_therapy_time",
            title = "Light therapy",
            detail = "Total minutes this week",
            current = lightMinutes,
            target = defaults.lightMinutesTotal,
            met = lightMinutes >= defaults.lightMinutesTotal,
        ),
        "cardio_activity" to GoalProgressRow(
            goalId = "cardio_activity",
            title = "Cardio",
            detail = "Sessions logged this week",
            current = cardioSessions,
            target = defaults.cardioSessions,
            met = cardioSessions >= defaults.cardioSessions,
        ),
        "weight_training" to GoalProgressRow(
            goalId = "weight_training",
            title = "Strength training",
            detail = "Workouts logged this week",
            current = weightWorkouts,
            target = defaults.weightWorkouts,
            met = weightWorkouts >= defaults.weightWorkouts,
        ),
    )

    return AllUserGoalOptions
        .mapNotNull { opt -> if (opt.id in selectedGoalIds) byId[opt.id] else null }
}

fun anySelectedGoalMet(rows: List<GoalProgressRow>): Boolean = rows.any { it.met }

fun GoalProgressRow.summaryLine(): String = when (goalId) {
    "supplements_adherence" -> "$current / $target days"
    "light_therapy_time" -> "$current / $target min"
    "weight_training" -> "$current / $target workouts"
    else -> "$current / $target sessions"
}
