package com.erv.app.weighttraining

import java.time.LocalDate

/**
 * Personal records for one lift. Same kinds and rules as the web app's `liftRecords.ts`.
 * Warm-up sets and interval blocks never count.
 */
enum class WeightRecordKind(val title: String) {
    /** Best estimated one-rep max ([estimatedOneRmKg]); value in kg. */
    EST_ONE_RM("Est. 1RM"),
    /** Heaviest weight lifted for 10 reps or more in one set; value in kg. */
    TEN_REP_MAX("10 Rep Max"),
    /** Heaviest weight in any set; value in kg, more reps breaks a tie. */
    HEAVIEST("Heaviest"),
    /** Most reps × weight in one workout; value in kg. */
    BEST_VOLUME("Best Volume"),
    /** Most reps in one set; value in reps, more weight breaks a tie. */
    MOST_REPS("Most Reps"),
    /** Longest timed hold in one set; value in seconds. */
    LONGEST_HOLD("Longest Hold"),
}

/**
 * One record: [value] in the kind's unit, the set behind it ([reps], [weightKg], [durationSeconds];
 * null for volume), and the workout and day it was set.
 */
data class WeightRecord(
    val exerciseId: String,
    val kind: WeightRecordKind,
    val value: Double,
    val workoutId: String,
    val date: LocalDate,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
) {
    /** True when this beats [other] for the same kind (or there is none yet). */
    fun beats(other: WeightRecord?): Boolean {
        if (other == null) return true
        if (value > other.value + EPSILON) return true
        if (value < other.value - EPSILON) return false
        return when (kind) {
            WeightRecordKind.HEAVIEST -> (reps ?: 0) > (other.reps ?: 0)
            WeightRecordKind.MOST_REPS -> (weightKg ?: 0.0) > (other.weightKg ?: 0.0) + EPSILON
            else -> false
        }
    }

    private companion object {
        const val EPSILON = 0.001
    }
}

/** A record [WeightRecords.newRecords] found, with the one it beat. */
data class WeightNewRecord(val record: WeightRecord, val previous: WeightRecord)

object WeightRecords {

    /** Every record [workout] holds for [exerciseId] on its own, as if it were the only one logged. */
    fun recordsOf(workout: WeightWorkoutSession, date: LocalDate, exerciseId: String): List<WeightRecord> {
        val sets = workout.entries
            .filter { it.exerciseId == exerciseId && it.hiitBlock == null }
            .flatMap { it.sets }
            .filter { it.isWorkingSet() }
        if (sets.isEmpty()) return emptyList()
        val out = mutableListOf<WeightRecord>()
        fun offer(record: WeightRecord) {
            val i = out.indexOfFirst { it.kind == record.kind }
            if (i < 0) out += record else if (record.beats(out[i])) out[i] = record
        }
        fun record(kind: WeightRecordKind, value: Double, set: WeightSet?) = WeightRecord(
            exerciseId = exerciseId,
            kind = kind,
            value = value,
            workoutId = workout.id,
            date = date,
            reps = set?.reps?.takeIf { it > 0 },
            weightKg = set?.weightKg?.takeIf { it > 0 },
            durationSeconds = set?.durationSeconds?.takeIf { it > 0 },
        )
        var volume = 0.0
        for (set in sets) {
            val kg = set.weightKg?.takeIf { it > 0 }
            if (set.reps > 0) {
                offer(record(WeightRecordKind.MOST_REPS, set.reps.toDouble(), set))
                if (kg != null) {
                    volume += kg * set.reps
                    offer(record(WeightRecordKind.HEAVIEST, kg, set))
                    estimatedOneRmKg(kg, set.reps)?.let { offer(record(WeightRecordKind.EST_ONE_RM, it, set)) }
                    if (set.reps >= 10) offer(record(WeightRecordKind.TEN_REP_MAX, kg, set))
                }
            }
            set.durationSeconds?.takeIf { it > 0 }?.let {
                offer(record(WeightRecordKind.LONGEST_HOLD, it.toDouble(), set))
            }
        }
        if (volume > 0) offer(record(WeightRecordKind.BEST_VOLUME, volume, null))
        return out.sortedBy { it.kind.ordinal }
    }

    /** The best of each kind for [exerciseId] across [workouts], in kind order. */
    fun best(
        workouts: List<DatedWeightWorkout>,
        exerciseId: String,
        excludeWorkoutIds: Set<String> = emptySet(),
    ): List<WeightRecord> {
        val best = LinkedHashMap<WeightRecordKind, WeightRecord>()
        // Oldest first, so a tie keeps the day it was first set.
        for (dated in workouts.sortedBy { it.logDate }) {
            if (dated.workout.id in excludeWorkoutIds) continue
            for (record in recordsOf(dated.workout, dated.logDate, exerciseId)) {
                if (record.beats(best[record.kind])) best[record.kind] = record
            }
        }
        return best.values.sortedBy { it.kind.ordinal }
    }

    /**
     * The records [workout] (logged on [date]) sets against every other workout in [workouts], for
     * each lift in it. Only beating an earlier record counts, so a first time doing a lift (or a
     * first set of 10) announces nothing.
     */
    fun newRecords(
        workouts: List<DatedWeightWorkout>,
        workout: WeightWorkoutSession,
        date: LocalDate,
    ): List<WeightNewRecord> {
        val others = workouts.filter { it.workout.id != workout.id }
        return workout.entries.map { it.exerciseId }.distinct().flatMap { exerciseId ->
            val before = best(others, exerciseId).associateBy { it.kind }
            recordsOf(workout, date, exerciseId).mapNotNull { record ->
                val previous = before[record.kind] ?: return@mapNotNull null
                if (record.beats(previous)) WeightNewRecord(record, previous) else null
            }
        }
    }
}

/** Every logged workout with its day, for records across the whole log. */
fun WeightLibraryState.allDatedWorkouts(): List<DatedWeightWorkout> =
    logs.flatMap { log ->
        val date = runCatching { LocalDate.parse(log.date) }.getOrNull() ?: return@flatMap emptyList()
        log.workouts.map { DatedWeightWorkout(date, it) }
    }
