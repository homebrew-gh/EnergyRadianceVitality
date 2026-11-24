package com.erv.app.workouts

import com.erv.app.cardio.CardioActivityRow
import com.erv.app.stretching.StretchActivityRow
import com.erv.app.weighttraining.WeightActivityRow

/**
 * One logged part of a workout run in a day's activity. A run still saves to each silo's own log
 * (lifting to weight training, cardio to cardio, stretches to stretching), and every entry carries
 * the run's [WorkoutSessionLink]; history puts them back together by that link.
 */
sealed class WorkoutHistorySection {
    abstract val startEpochSeconds: Long
    abstract val endEpochSeconds: Long

    data class Lifting(val row: WeightActivityRow) : WorkoutHistorySection() {
        override val startEpochSeconds: Long
            get() = row.session.startedAtEpochSeconds ?: row.session.finishedAtEpochSeconds ?: 0L
        override val endEpochSeconds: Long
            get() = row.session.finishedAtEpochSeconds ?: startEpochSeconds
    }

    data class Cardio(val row: CardioActivityRow) : WorkoutHistorySection() {
        override val startEpochSeconds: Long
            get() = row.session.startEpochSeconds
                ?: (row.session.loggedAtEpochSeconds - row.session.durationMinutes * 60L)
        override val endEpochSeconds: Long
            get() = row.session.endEpochSeconds ?: row.session.loggedAtEpochSeconds
    }

    data class Stretching(val row: StretchActivityRow) : WorkoutHistorySection() {
        // A stretch log is stamped when it is saved, at the end of its section.
        override val startEpochSeconds: Long
            get() = row.session.loggedAtEpochSeconds - row.session.totalMinutes * 60L
        override val endEpochSeconds: Long
            get() = row.session.loggedAtEpochSeconds
    }

    val link: WorkoutSessionLink?
        get() = when (this) {
            is Lifting -> row.session.workoutLink
            is Cardio -> row.session.workoutLink
            is Stretching -> row.session.workoutLink
        }
}

/** Everything one workout run logged on a day, in the order it was done. */
data class WorkoutHistoryEntry(
    val sessionId: String,
    val workoutId: String,
    /** The workout's name, or "Workout" when it has since been deleted. */
    val title: String,
    val sections: List<WorkoutHistorySection>,
) {
    val startEpochSeconds: Long get() = sections.minOf { it.startEpochSeconds }

    /** "3 sections · 52 min · avg 131 bpm" */
    val headerLine: String
        get() {
            val parts = mutableListOf(if (sections.size == 1) "1 section" else "${sections.size} sections")
            val start = sections.minOf { it.startEpochSeconds }
            val end = sections.maxOf { it.endEpochSeconds }
            val minutes = ((end - start) / 60L).toInt()
            if (start > 0 && minutes > 0) parts += "$minutes min"
            sections.firstNotNullOfOrNull { it.link?.sessionHeartRate }
                ?.avgBpm?.takeIf { it > 0 }
                ?.let { parts += "avg $it bpm" }
            return parts.joinToString(" · ")
        }
}

/** A day's cardio, lifting and stretching rows, with the ones from workout runs pulled out into [workouts]. */
data class DayTrainingActivity(
    val workouts: List<WorkoutHistoryEntry>,
    val cardio: List<CardioActivityRow>,
    val weight: List<WeightActivityRow>,
    val stretch: List<StretchActivityRow>,
)

/**
 * Groups a day's rows by workout run, so a mixed workout shows as one entry with its sections.
 * Rows logged outside a workout stay in their own lists, in their order.
 */
fun groupDayActivityByWorkout(
    cardio: List<CardioActivityRow>,
    weight: List<WeightActivityRow>,
    stretch: List<StretchActivityRow>,
    workoutName: (workoutId: String) -> String?,
): DayTrainingActivity {
    val linked = buildList<WorkoutHistorySection> {
        weight.forEach { if (it.session.workoutLink != null) add(WorkoutHistorySection.Lifting(it)) }
        cardio.forEach { if (it.session.workoutLink != null) add(WorkoutHistorySection.Cardio(it)) }
        stretch.forEach { if (it.session.workoutLink != null) add(WorkoutHistorySection.Stretching(it)) }
    }
    val groups = linked.groupBy { it.link!!.sessionId }
    // A standalone workout that was only lifting reads as the weight workout it is.
    val liftingOnly = groups.values
        .filter { sections ->
            isStandaloneWorkoutId(sections.first().link!!.workoutId) &&
                sections.all { it is WorkoutHistorySection.Lifting }
        }
        .flatten()
        .map { (it as WorkoutHistorySection.Lifting).row }
    val shownOnTheirOwn = liftingOnly.map { it.session.id }.toSet()
    val workouts = groups
        .filterValues { sections -> sections.none { it is WorkoutHistorySection.Lifting && it.row.session.id in shownOnTheirOwn } }
        .map { (sessionId, sections) ->
            val link = sections.first().link!!
            WorkoutHistoryEntry(
                sessionId = sessionId,
                workoutId = link.workoutId,
                title = workoutName(link.workoutId)?.takeIf { it.isNotBlank() } ?: "Workout",
                sections = sections.sortedBy { it.startEpochSeconds },
            )
        }
        .sortedBy { it.startEpochSeconds }
    return DayTrainingActivity(
        workouts = workouts,
        cardio = cardio.filter { it.session.workoutLink == null },
        weight = weight.filter { it.session.workoutLink == null || it.session.id in shownOnTheirOwn },
        stretch = stretch.filter { it.session.workoutLink == null },
    )
}
