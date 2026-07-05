package com.erv.app.weighttraining

import com.erv.app.SectionLogDateFilter
import com.erv.app.cardio.CardioHrSample
import com.erv.app.cardio.CardioHrScaffolding
import com.erv.app.unifiedroutines.UnifiedSessionLink
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import com.erv.app.workouts.DEFAULT_TIMED_PREP_SECONDS
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
    /** Bodyweight / calisthenics; optional added load when logging sets. Sync JSON stays `other`. */
    @SerialName("other") OTHER
}

@Serializable
enum class WeightSetLoggingStyle {
    /** Standard rep-based sets (default). */
    @SerialName("reps") REPS,
    /** Duration per set only (carries, isometric holds). */
    @SerialName("time_only") TIME_ONLY,
    /** Prescription may use reps or timed sets (swings, conditioning). */
    @SerialName("reps_or_time") REPS_OR_TIME,
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
     * Optional built-in specialty pack gate. Exercises in a pack stay hidden from general library pickers
     * until the user enables that pack in settings.
     */
    val exercisePackId: String? = null,
    /**
     * When true, live workout can run a guided interval timer for this movement (built-ins set from
     * catalog rules; custom exercises opt in via the exercise editor).
     */
    val hiitCapable: Boolean = false,
    /**
     * When true, sets for this movement are logged as a timed hold (duration per set) instead of
     * reps — e.g. planks. Built-ins resolve this from catalog rules; weight/RPE remain optional.
     */
    val timePerSetCapable: Boolean = false,
    /**
     * When false with [timePerSetCapable], sets are time-only (no rep prescription).
     * When true with [timePerSetCapable], builder offers reps or time ([WeightSetLoggingStyle.REPS_OR_TIME]).
     */
    val repPerSetCapable: Boolean = true,
    /**
     * Per-workout rollup for this exercise only (date + workout id, volume, est. 1RM).
     * Rebuilt from [WeightLibraryState.logs] on save; omitted when syncing the shared exercise list.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val sessionSummaries: List<WeightExerciseSessionSummary> = emptyList()
)

/** How a workout builder / live run should prescribe and log sets for this movement. */
fun WeightExercise.setLoggingStyle(): WeightSetLoggingStyle = when {
    timePerSetCapable && !repPerSetCapable -> WeightSetLoggingStyle.TIME_ONLY
    timePerSetCapable && repPerSetCapable -> WeightSetLoggingStyle.REPS_OR_TIME
    else -> WeightSetLoggingStyle.REPS
}

/** Whether live UI should show timed-set rows for this exercise given prescription hints. */
fun WeightExercise.useTimedSetLogging(sets: List<WeightSet> = emptyList()): Boolean =
    when (setLoggingStyle()) {
        WeightSetLoggingStyle.TIME_ONLY -> true
        WeightSetLoggingStyle.REPS_OR_TIME ->
            sets.any { (it.targetDurationSeconds ?: 0) > 0 }
        WeightSetLoggingStyle.REPS -> false
    }

/** Plank holds always use a countdown timer with final-five-second beeps (like the live rest timer). */
fun WeightExercise.usesTimedHoldCountdownBeeps(): Boolean = when (id) {
    "erv-weight-exercise-bw-plank-v1",
    "erv-weight-exercise-bw-side-plank-v1" -> true
    else -> false
}

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
    val reps: Int = 0,
    val weightKg: Double? = null,
    val rpe: Double? = null,
    /** Reps in reserve hint for prescription / live ghost display. */
    val rir: Int? = null,
    /** Per-side or per-leg rep target when [side] is set. */
    val repsPerSide: Int? = null,
    /** `left`, `right`, `each`, or `alternating`. */
    val side: String? = null,
    /** Hold duration for time-based movements (e.g. planks). Mutually exclusive with [reps] in practice. */
    val durationSeconds: Int? = null,
    /** Builder / prescription hint shown as a ghost value until the athlete logs reps. */
    val targetReps: Int? = null,
    /** Ghost rep hint for a prescription range (e.g. "5-8") when [targetReps] is unset. */
    val targetRepsRangeLabel: String? = null,
    /** Builder / prescription hint shown as a ghost load until the athlete logs weight. */
    val targetWeightKg: Double? = null,
    /** Builder / prescription hint for timed holds; live timer defaults to this or 30s. */
    val targetDurationSeconds: Int? = null,
)

/** Ghost text for the live-workout reps field (single target or range label). */
fun WeightSet.repsShadowText(): String? =
    targetReps?.takeIf { it > 0 }?.toString()
        ?: targetRepsRangeLabel?.takeIf { it.isNotBlank() }

/** A set is "logged" (worth keeping) when it has reps or a timed hold. */
fun WeightSet.isLogged(): Boolean = reps > 0 || (durationSeconds ?: 0) > 0

/** True when a set row transitions from empty to logged (reps or timed hold). */
fun weightSetLoggingTriggersRest(previous: List<WeightSet>, updated: List<WeightSet>): Boolean {
    val limit = maxOf(previous.size, updated.size)
    for (i in 0 until limit) {
        val before = previous.getOrNull(i) ?: WeightSet()
        val after = updated.getOrNull(i) ?: continue
        if (!before.isLogged() && after.isLogged()) return true
    }
    return false
}

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
    val estimatedKcal: Double? = null,
    /** Populated when a live session used a connected BLE HR sensor (avg/min/max over the workout). */
    val heartRate: CardioHrScaffolding? = null,
    /**
     * HR stats per exercise window, from focus timestamps during a live lift (approximate).
     * Omitted when syncing day logs to relays (local detail).
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val heartRateExerciseSegments: List<WeightExerciseHrSegment> = emptyList(),
    val unifiedLink: UnifiedSessionLink? = null,
    val workoutLink: com.erv.app.workouts.WorkoutSessionLink? = null,
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

/**
 * Group exercises by muscle with alphabetical section headers and exercise names.
 * Used by the library list and the workout exercise picker.
 */
fun groupExercisesByMuscle(exercises: List<WeightExercise>): List<Pair<String, List<WeightExercise>>> {
    if (exercises.isEmpty()) return emptyList()
    val grouped = exercises.groupBy { ex ->
        ex.muscleGroup.trim().lowercase().ifBlank { "other" }
    }
    val orderedKeys = grouped.keys.sorted()
    return orderedKeys.map { key -> key to grouped.getValue(key).sortedBy { it.name.lowercase() } }
}

/** Sticky list sections sorted alphabetically by muscle group. */
fun WeightLibraryState.exercisesGroupedByMuscle(): List<Pair<String, List<WeightExercise>>> =
    groupExercisesByMuscle(exercises)

fun WeightEquipment.displayLabel(): String = when (this) {
    WeightEquipment.BARBELL -> "Barbell"
    WeightEquipment.DUMBBELL -> "Dumbbell"
    WeightEquipment.KETTLEBELL -> "Kettlebell"
    WeightEquipment.MACHINE -> "Machine"
    WeightEquipment.OTHER -> "Body Weight"
}

fun WeightPushPull.displayLabel(): String = when (this) {
    WeightPushPull.PUSH -> "Push"
    WeightPushPull.PULL -> "Pull"
}

fun formatMuscleGroupHeader(key: String): String =
    key.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

fun weightNowEpochSeconds(): Long = System.currentTimeMillis() / 1000

@Serializable
data class WeightWorkoutCircuitRun(
    val workoutId: String,
    val segmentId: String,
    val segmentTitle: String? = null,
    val segmentIndex: Int,
    val rounds: Int,
    val restBetweenItemsSeconds: Int = 0,
    val restAfterRoundSeconds: Int = 0,
    val slots: List<com.erv.app.workouts.WeightCircuitSlot>,
    /** 1-based round within the circuit. */
    val currentRound: Int = 1,
    val currentSlotIndex: Int = 0,
    val lastAcknowledgedSlotKey: String? = null,
    val pendingRestSeconds: Int? = null,
    val isComplete: Boolean = false,
)

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
    val exerciseFocusMarks: List<WeightExerciseFocusMark> = emptyList(),
    val circuitRun: WeightWorkoutCircuitRun? = null,
    /** Per-exercise rest between sets from a planned workout prescription (seconds). */
    val restBetweenSetsSecondsByExerciseId: Map<String, Int> = emptyMap(),
    /** Per-exercise get-ready countdown before each timed set (seconds); 0 = none. */
    val timedPrepSecondsByExerciseId: Map<String, Int> = emptyMap(),
)

/** Get-ready seconds before a timed set; falls back to [DEFAULT_TIMED_PREP_SECONDS] for time-only exercises. */
fun WeightWorkoutDraft.timedPrepSecondsFor(
    exerciseId: String,
    loggingStyle: WeightSetLoggingStyle,
): Int {
    if (exerciseId in timedPrepSecondsByExerciseId) {
        return timedPrepSecondsByExerciseId[exerciseId] ?: 0
    }
    if (loggingStyle == WeightSetLoggingStyle.TIME_ONLY) return DEFAULT_TIMED_PREP_SECONDS
    return 0
}

private fun WeightWorkoutDraft.entryForOrderedExercise(id: String): WeightWorkoutEntry? {
    val hiit = hiitBlocksByExerciseId[id]
    if (hiit != null) {
        return WeightWorkoutEntry(exerciseId = id, sets = emptyList(), hiitBlock = hiit)
    }
    val s = setsByExerciseId[id].orEmpty().filter { it.isLogged() }
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
            val s = setsByExerciseId[id].orEmpty().filter { it.isLogged() }
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
