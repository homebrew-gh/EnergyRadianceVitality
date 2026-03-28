package com.erv.app.weighttraining

import com.erv.app.SectionLogDateFilter
import com.erv.app.cardio.CardioHrSample
import com.erv.app.cardio.CardioHrScaffolding
import com.erv.app.unifiedroutines.UnifiedSessionLink
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlin.math.max

@Serializable
enum class WeightPushPull {
    @SerialName("push") PUSH,
    @SerialName("pull") PULL
}

@Serializable
enum class WeightEquipment {
    @SerialName("barbell") BARBELL,
    @SerialName("dumbbell") DUMBBELL,
    @SerialName("kettlebell") KETTLEBELL,
    @SerialName("machine") MACHINE,
    @SerialName("other") OTHER
}

@Serializable
data class WeightExercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Lowercase slug e.g. chest, back, legs, shoulders, biceps, triceps, core, or custom label */
    val muscleGroup: String,
    val pushOrPull: WeightPushPull,
    val equipment: WeightEquipment,
    /**
     * When true, live workout can run a guided interval timer for this movement (built-ins set from
     * catalog rules; custom exercises opt in via the exercise editor).
     */
    val hiitCapable: Boolean = false,
    /**
     * Per-workout rollup for this exercise only (date + workout id, volume, est. 1RM).
     * Rebuilt from [WeightLibraryState.logs] on save; omitted when syncing the shared exercise list.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val sessionSummaries: List<WeightExerciseSessionSummary> = emptyList()
)

@Serializable
data class WeightRoutine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val exerciseIds: List<String> = emptyList(),
    val notes: String? = null,
    /**
     * Unix epoch seconds; bumped on each local save so a relay fetch with stale routine data
     * does not overwrite a just-edited name or exercise list.
     */
    val lastModifiedEpochSeconds: Long = 0
)

@Serializable
data class WeightSet(
    val reps: Int,
    val weightKg: Double? = null,
    val rpe: Double? = null
)

/** Logged result of completing a guided interval block for one exercise in a session. */
@Serializable
data class WeightHiitBlockLog(
    val intervals: Int,
    val workSeconds: Int,
    val restSeconds: Int,
    val weightKg: Double? = null,
    val rpe: Double? = null,
)

/** Parameters when starting the full-screen interval timer (not persisted until completed). */
data class WeightHiitIntervalPlan(
    val intervals: Int,
    val workSeconds: Int,
    val restSeconds: Int,
    val weightKg: Double?,
)

@Serializable
data class WeightWorkoutEntry(
    val exerciseId: String,
    val sets: List<WeightSet> = emptyList(),
    /** When present, this exercise was logged via the interval timer (mutually exclusive with reps/sets). */
    val hiitBlock: WeightHiitBlockLog? = null,
)

@Serializable
enum class WeightWorkoutSource {
    @SerialName("LIVE") LIVE,
    @SerialName("MANUAL") MANUAL,
    /** Session created from file import (JSON or CSV); shown as "Imported" in the UI. */
    @SerialName("IMPORTED") IMPORTED,
}

@Serializable
data class WeightExerciseHrSegment(
    val exerciseId: String,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
    val sampleCount: Int = 0,
    val avgBpm: Int? = null,
    val maxBpm: Int? = null,
    val minBpm: Int? = null
)

@Serializable
data class WeightExerciseFocusMark(
    val exerciseId: String,
    val epochSeconds: Long
)

@Serializable
data class WeightWorkoutSession(
    val id: String = UUID.randomUUID().toString(),
    val source: WeightWorkoutSource,
    val startedAtEpochSeconds: Long? = null,
    val finishedAtEpochSeconds: Long? = null,
    val durationSeconds: Int? = null,
    val routineId: String? = null,
    val routineName: String? = null,
    val entries: List<WeightWorkoutEntry> = emptyList(),
    /** Populated when a live session used a connected BLE HR sensor (avg/min/max over the workout). */
    val heartRate: CardioHrScaffolding? = null,
    /**
     * HR stats per exercise window, from focus timestamps during a live lift (approximate).
     * Omitted when syncing day logs to relays (local detail).
     */
    val heartRateExerciseSegments: List<WeightExerciseHrSegment> = emptyList(),
    val unifiedLink: UnifiedSessionLink? = null
)

@Serializable
data class WeightDayLog(
    val date: String,
    val workouts: List<WeightWorkoutSession> = emptyList()
)

@Serializable
data class WeightLibraryState(
    val exercises: List<WeightExercise> = emptyList(),
    val routines: List<WeightRoutine> = emptyList(),
    val logs: List<WeightDayLog> = emptyList()
) {
    fun logFor(date: LocalDate): WeightDayLog? = logs.firstOrNull { it.date == date.toString() }

    fun exerciseById(id: String): WeightExercise? = exercises.firstOrNull { it.id == id }
}

data class DatedWeightWorkout(val logDate: LocalDate, val workout: WeightWorkoutSession)

fun WeightLibraryState.chronologicalWeightWorkoutsForPeriod(start: LocalDate, end: LocalDate): List<DatedWeightWorkout> {
    val from = if (start <= end) start else end
    val to = if (start <= end) end else start
    val rows = mutableListOf<DatedWeightWorkout>()
    var d = from
    while (!d.isAfter(to)) {
        logFor(d)?.workouts?.forEach { w -> rows.add(DatedWeightWorkout(d, w)) }
        d = d.plusDays(1)
    }
    return rows.sortedWith(
        compareBy<DatedWeightWorkout> { it.logDate }
            .thenBy { it.workout.startedAtEpochSeconds ?: it.workout.finishedAtEpochSeconds ?: 0L }
            .thenBy { it.workout.id }
    )
}

private fun weightWorkoutEpoch(w: WeightWorkoutSession): Long =
    w.startedAtEpochSeconds ?: w.finishedAtEpochSeconds ?: 0L

private fun List<DatedWeightWorkout>.sortedWeightNewestFirst(): List<DatedWeightWorkout> =
    sortedWith(
        compareByDescending<DatedWeightWorkout> { weightWorkoutEpoch(it.workout) }
            .thenByDescending { it.logDate }
            .thenBy { it.workout.id }
    )

fun WeightLibraryState.datedWeightWorkoutsForSectionLog(filter: SectionLogDateFilter): List<DatedWeightWorkout> =
    when (filter) {
        SectionLogDateFilter.AllHistory -> {
            val rows = mutableListOf<DatedWeightWorkout>()
            for (dl in logs) {
                val d = LocalDate.parse(dl.date)
                dl.workouts.forEach { w -> rows.add(DatedWeightWorkout(d, w)) }
            }
            rows.sortedWeightNewestFirst()
        }
        is SectionLogDateFilter.SingleDay ->
            (logFor(filter.day)?.workouts ?: emptyList()).map { DatedWeightWorkout(filter.day, it) }
                .sortedWeightNewestFirst()
        is SectionLogDateFilter.DateRange ->
            chronologicalWeightWorkoutsForPeriod(filter.startInclusive, filter.endInclusive).sortedWeightNewestFirst()
    }

private val muscleGroupDisplayOrder: List<String> =
    listOf("chest", "back", "legs", "shoulders", "biceps", "triceps", "core")

/**
 * Same section order as the exercise library (known muscle keys first, then alpha).
 * Used by the library list and the workout exercise picker.
 */
fun groupExercisesByMuscle(exercises: List<WeightExercise>): List<Pair<String, List<WeightExercise>>> {
    if (exercises.isEmpty()) return emptyList()
    val grouped = exercises.groupBy { ex ->
        ex.muscleGroup.trim().lowercase().ifBlank { "other" }
    }
    val orderedKeys = buildList {
        val known = muscleGroupDisplayOrder.filter { it in grouped }
        addAll(known)
        addAll((grouped.keys - known.toSet()).sorted())
    }
    return orderedKeys.map { key -> key to grouped.getValue(key).sortedBy { it.name.lowercase() } }
}

/** Sticky list sections: known groups first, then remaining alpha. */
fun WeightLibraryState.exercisesGroupedByMuscle(): List<Pair<String, List<WeightExercise>>> =
    groupExercisesByMuscle(exercises)

fun WeightEquipment.displayLabel(): String = when (this) {
    WeightEquipment.BARBELL -> "Barbell"
    WeightEquipment.DUMBBELL -> "Dumbbell"
    WeightEquipment.KETTLEBELL -> "Kettlebell"
    WeightEquipment.MACHINE -> "Machine"
    WeightEquipment.OTHER -> "Other"
}

fun WeightPushPull.displayLabel(): String = when (this) {
    WeightPushPull.PUSH -> "Push"
    WeightPushPull.PULL -> "Pull"
}

fun formatMuscleGroupHeader(key: String): String =
    key.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

fun weightNowEpochSeconds(): Long = System.currentTimeMillis() / 1000

/** Live workout draft (local only; not synced to Nostr). Persisted while a session is active. */
@Serializable
data class WeightWorkoutDraft(
    val startedAtEpochSeconds: Long,
    val exerciseOrder: List<String>,
    val setsByExerciseId: Map<String, List<WeightSet>> = emptyMap(),
    val hiitBlocksByExerciseId: Map<String, WeightHiitBlockLog> = emptyMap(),
    val routineId: String? = null,
    val routineName: String? = null,
    /** When the user focused an exercise (expand / log sets / HIIT); used to correlate HR samples. */
    val exerciseFocusMarks: List<WeightExerciseFocusMark> = emptyList()
)

private fun WeightWorkoutDraft.entryForOrderedExercise(id: String): WeightWorkoutEntry? {
    val hiit = hiitBlocksByExerciseId[id]
    if (hiit != null) {
        return WeightWorkoutEntry(exerciseId = id, sets = emptyList(), hiitBlock = hiit)
    }
    val s = setsByExerciseId[id].orEmpty().filter { it.reps > 0 }
    if (s.isEmpty()) return null
    return WeightWorkoutEntry(exerciseId = id, sets = s)
}

/** Build a finished LIVE session or `null` if nothing was logged. */
fun WeightWorkoutDraft.toFinishedLiveSession(
    heartRate: CardioHrScaffolding? = null,
    heartRateExerciseSegments: List<WeightExerciseHrSegment> = emptyList()
): WeightWorkoutSession? {
    val entries = exerciseOrder.mapNotNull { id -> entryForOrderedExercise(id) }
    if (entries.isEmpty()) return null
    return WeightWorkoutSession(
        source = WeightWorkoutSource.LIVE,
        startedAtEpochSeconds = startedAtEpochSeconds,
        finishedAtEpochSeconds = weightNowEpochSeconds(),
        routineId = routineId,
        routineName = routineName,
        entries = entries,
        heartRate = heartRate,
        heartRateExerciseSegments = heartRateExerciseSegments
    )
}

/**
 * Maps [WeightWorkoutDraft.exerciseFocusMarks] to HR aggregates per exercise window.
 * Windows are [mark_i, mark_{i+1}) (last window runs through [sessionEnd]).
 */
fun buildWeightExerciseHrSegments(
    marks: List<WeightExerciseFocusMark>,
    sessionStart: Long,
    sessionEnd: Long,
    samples: List<CardioHrSample>
): List<WeightExerciseHrSegment> {
    if (samples.isEmpty()) return emptyList()
    val sorted = marks.sortedBy { it.epochSeconds }
    if (sorted.isEmpty()) return emptyList()
    val out = mutableListOf<WeightExerciseHrSegment>()
    for (i in sorted.indices) {
        val start = max(sessionStart, sorted[i].epochSeconds)
        val endExclusive = if (i < sorted.lastIndex) sorted[i + 1].epochSeconds else sessionEnd + 1
        if (start >= endExclusive) continue
        val slice = samples.filter { it.epochSeconds >= start && it.epochSeconds < endExclusive }
        if (slice.isEmpty()) continue
        val avg = (slice.sumOf { it.bpm.toLong() } / slice.size).toInt()
        out.add(
            WeightExerciseHrSegment(
                exerciseId = sorted[i].exerciseId,
                startEpochSeconds = start,
                endEpochSeconds = (endExclusive - 1).coerceAtLeast(start),
                sampleCount = slice.size,
                avgBpm = avg,
                maxBpm = slice.maxOf { it.bpm },
                minBpm = slice.minOf { it.bpm }
            )
        )
    }
    return out
}

/**
 * Build or update a session from the log editor. New workouts are MANUAL; existing sessions keep
 * [WeightWorkoutSession.source] and timestamps. Returns `null` if nothing valid was logged.
 */
fun buildSessionFromLogEditor(
    existing: WeightWorkoutSession?,
    exerciseOrder: List<String>,
    setsByExerciseId: Map<String, List<WeightSet>>,
    hiitBlocksByExerciseId: Map<String, WeightHiitBlockLog> = emptyMap()
): WeightWorkoutSession? {
    val entries = exerciseOrder.mapNotNull { id ->
        val hiit = hiitBlocksByExerciseId[id]
        if (hiit != null) {
            WeightWorkoutEntry(exerciseId = id, sets = emptyList(), hiitBlock = hiit)
        } else {
            val s = setsByExerciseId[id].orEmpty().filter { it.reps > 0 }
            if (s.isEmpty()) null else WeightWorkoutEntry(exerciseId = id, sets = s)
        }
    }
    if (entries.isEmpty()) return null
    return if (existing == null) {
        WeightWorkoutSession(
            id = UUID.randomUUID().toString(),
            source = WeightWorkoutSource.MANUAL,
            startedAtEpochSeconds = null,
            finishedAtEpochSeconds = null,
            entries = entries
        )
    } else {
        existing.copy(entries = entries)
    }
}

/** Stable IDs so synced devices agree on built-in compounds. */
fun defaultCompoundExercises(): List<WeightExercise> = listOf(
    WeightExercise(
        id = "erv-weight-exercise-bench-v1",
        name = "Bench Press",
        muscleGroup = "chest",
        pushOrPull = WeightPushPull.PUSH,
        equipment = WeightEquipment.BARBELL
    ),
    WeightExercise(
        id = "erv-weight-exercise-deadlift-v1",
        name = "Deadlift",
        muscleGroup = "legs",
        pushOrPull = WeightPushPull.PULL,
        equipment = WeightEquipment.BARBELL
    ),
    WeightExercise(
        id = "erv-weight-exercise-squat-v1",
        name = "Squat",
        muscleGroup = "legs",
        pushOrPull = WeightPushPull.PUSH,
        equipment = WeightEquipment.BARBELL
    ),
    WeightExercise(
        id = "erv-weight-exercise-ohp-v1",
        name = "Military Press",
        muscleGroup = "shoulders",
        pushOrPull = WeightPushPull.PUSH,
        equipment = WeightEquipment.BARBELL
    )
)
