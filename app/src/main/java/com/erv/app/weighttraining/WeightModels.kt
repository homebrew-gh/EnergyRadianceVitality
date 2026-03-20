package com.erv.app.weighttraining

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
    /** Lowercase slug e.g. chest, back, legs, shoulders, arms, core, or custom label */
    val muscleGroup: String,
    val pushOrPull: WeightPushPull,
    val equipment: WeightEquipment
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
    listOf("chest", "back", "legs", "shoulders", "arms", "core")

/** Sticky list sections: known groups first, then remaining alpha. */
fun WeightLibraryState.exercisesGroupedByMuscle(): List<Pair<String, List<WeightExercise>>> {
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
