package com.erv.app.goals

import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.isCyclingActivity
import com.erv.app.data.CardioGoalFilter
import com.erv.app.data.UserGoalDefinition
import com.erv.app.data.UserGoalMetricType
import com.erv.app.lighttherapy.LightLibraryState
import com.erv.app.programs.ProgramBlockKind
import com.erv.app.programs.ProgramsLibraryState
import com.erv.app.programs.programBlockCompletionKey
import com.erv.app.programs.programBlocksForCalendarDay
import com.erv.app.programs.programChecklistCompletionKey
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.supplements.SupplementLibraryState
import com.erv.app.weighttraining.WeightLibraryState
import java.time.LocalDate
import java.time.temporal.WeekFields

data class GoalProgressRow(
    val goalId: String,
    val title: String,
    val detail: String,
    val current: Int,
    val target: Int,
    val met: Boolean,
    val summarySuffix: String,
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
    goals: List<UserGoalDefinition>,
    supplementState: SupplementLibraryState,
    lightState: LightLibraryState,
    cardioState: CardioLibraryState,
    weightState: WeightLibraryState,
    stretchState: StretchLibraryState,
    programsState: ProgramsLibraryState,
): List<GoalProgressRow> {
    if (goals.isEmpty()) return emptyList()
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

    val cyclingSessions = weekDays.sumOf { d ->
        cardioState.logFor(d)?.sessions?.count { it.activity.isCyclingActivity() } ?: 0
    }

    val weightWorkouts = weekDays.sumOf { d ->
        weightState.logFor(d)?.workouts?.size ?: 0
    }

    val stretchSessions = weekDays.sumOf { d ->
        stretchState.logFor(d)?.sessions?.size ?: 0
    }

    val programAdherenceDays = weekDays.count { day ->
        val activeProgram = programsState.activeProgramId?.let(programsState::programById) ?: return@count false
        val blocks = programBlocksForCalendarDay(activeProgram, day.dayOfWeek.value)
        if (blocks.isEmpty()) return@count false
        blocks.all { block ->
            if (block.kind == ProgramBlockKind.OTHER && block.checklistItems.isNotEmpty()) {
                block.checklistItems.indices.all { itemIndex ->
                    programsState.isCompletionDone(
                        programChecklistCompletionKey(activeProgram.id, block.id, itemIndex, day)
                    )
                }
            } else {
                programsState.isCompletionDone(programBlockCompletionKey(activeProgram.id, block.id, day))
            }
        }
    }

    return goals.map { goal ->
        val current = when (goal.metricType) {
            UserGoalMetricType.SUPPLEMENT_DAYS -> supplementActiveDays
            UserGoalMetricType.PROGRAM_ADHERENCE_DAYS -> programAdherenceDays
            UserGoalMetricType.CARDIO_SESSIONS ->
                if (goal.cardioFilter == CardioGoalFilter.CYCLING) cyclingSessions else cardioSessions
            UserGoalMetricType.STRENGTH_WORKOUTS -> weightWorkouts
            UserGoalMetricType.STRETCH_SESSIONS -> stretchSessions
            UserGoalMetricType.LIGHT_MINUTES -> lightMinutes
        }
        GoalProgressRow(
            goalId = goal.id,
            title = goal.title,
            detail = goalDetail(goal),
            current = current,
            target = goal.target,
            met = current >= goal.target,
            summarySuffix = goalSummarySuffix(goal),
        )
    }
}

fun anySelectedGoalMet(rows: List<GoalProgressRow>): Boolean = rows.any { it.met }

fun GoalProgressRow.summaryLine(): String = "$current / $target $summarySuffix"

private fun goalDetail(goal: UserGoalDefinition): String = when (goal.metricType) {
    UserGoalMetricType.SUPPLEMENT_DAYS -> "Days this week with supplement activity logged"
    UserGoalMetricType.PROGRAM_ADHERENCE_DAYS -> "Days this week where your active program is fully completed"
    UserGoalMetricType.CARDIO_SESSIONS ->
        if (goal.cardioFilter == CardioGoalFilter.CYCLING) "Cycling sessions logged this week"
        else "Cardio sessions logged this week"
    UserGoalMetricType.STRENGTH_WORKOUTS -> "Weight-training workouts logged this week"
    UserGoalMetricType.STRETCH_SESSIONS -> "Stretch sessions logged this week"
    UserGoalMetricType.LIGHT_MINUTES -> "Total light therapy minutes this week"
}

private fun goalSummarySuffix(goal: UserGoalDefinition): String = when (goal.metricType) {
    UserGoalMetricType.SUPPLEMENT_DAYS -> "days"
    UserGoalMetricType.PROGRAM_ADHERENCE_DAYS -> "days"
    UserGoalMetricType.CARDIO_SESSIONS -> "sessions"
    UserGoalMetricType.STRENGTH_WORKOUTS -> "workouts"
    UserGoalMetricType.STRETCH_SESSIONS -> "sessions"
    UserGoalMetricType.LIGHT_MINUTES -> "min"
}
