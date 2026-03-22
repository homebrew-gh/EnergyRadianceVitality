package com.erv.app.weighttraining

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

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
    val notes: String? = null
)

@Serializable
data class WeightSet(
    val reps: Int,
    val weightKg: Double? = null,
    val rpe: Double? = null
)

@Serializable
data class WeightWorkoutEntry(
    val exerciseId: String,
    val sets: List<WeightSet> = emptyList()
)

@Serializable
enum class WeightWorkoutSource {
    @SerialName("LIVE") LIVE,
    @SerialName("MANUAL") MANUAL
}

@Serializable
data class WeightWorkoutSession(
    val id: String = UUID.randomUUID().toString(),
    val source: WeightWorkoutSource,
    val startedAtEpochSeconds: Long? = null,
    val finishedAtEpochSeconds: Long? = null,
    val durationSeconds: Int? = null,
    val routineId: String? = null,
    val routineName: String? = null,
    val entries: List<WeightWorkoutEntry> = emptyList()
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
    val routineId: String? = null,
    val routineName: String? = null
)

/** Build a finished LIVE session or `null` if nothing was logged. */
fun WeightWorkoutDraft.toFinishedLiveSession(): WeightWorkoutSession? {
    val entries = exerciseOrder.mapNotNull { id ->
        val s = setsByExerciseId[id].orEmpty().filter { it.reps > 0 }
        if (s.isEmpty()) null else WeightWorkoutEntry(exerciseId = id, sets = s)
    }
    if (entries.isEmpty()) return null
    return WeightWorkoutSession(
        source = WeightWorkoutSource.LIVE,
        startedAtEpochSeconds = startedAtEpochSeconds,
        finishedAtEpochSeconds = weightNowEpochSeconds(),
        routineId = routineId,
        routineName = routineName,
        entries = entries
    )
}

/**
 * Build or update a session from the log editor. New workouts are MANUAL; existing sessions keep
 * [WeightWorkoutSession.source] and timestamps. Returns `null` if nothing valid was logged.
 */
fun buildSessionFromLogEditor(
    existing: WeightWorkoutSession?,
    exerciseOrder: List<String>,
    setsByExerciseId: Map<String, List<WeightSet>>
): WeightWorkoutSession? {
    val entries = exerciseOrder.mapNotNull { id ->
        val s = setsByExerciseId[id].orEmpty().filter { it.reps > 0 }
        if (s.isEmpty()) null else WeightWorkoutEntry(exerciseId = id, sets = s)
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
