package com.erv.app.workouts

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val workoutImportJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

fun decodeWorkoutImportEnvelope(raw: String): Result<WorkoutImportEnvelope> =
    runCatching { workoutImportJson.decodeFromString<WorkoutImportEnvelope>(raw.trim()) }

fun mergeWorkoutImport(
    current: WorkoutLibraryState,
    envelope: WorkoutImportEnvelope,
): WorkoutLibraryState {
    require(envelope.ervWorkoutImportVersion == 1) {
        "Unsupported ervWorkoutImportVersion: ${envelope.ervWorkoutImportVersion}"
    }
    var next = current
    envelope.workouts.forEach { imported ->
        val stamped = imported.copy(
            lastModifiedEpochSeconds = nowWorkoutEpochSeconds(),
        )
        next = next.copy(
            workouts = next.workouts.upsertWorkout(stamped),
        )
    }
    return next
}

private fun List<Workout>.upsertWorkout(workout: Workout): List<Workout> {
    val items = toMutableList()
    val index = items.indexOfFirst { it.id == workout.id }
    if (index >= 0) items[index] = workout else items.add(workout)
    return items
}

fun encodeWorkoutImportEnvelope(envelope: WorkoutImportEnvelope): String =
    workoutImportJson.encodeToString(WorkoutImportEnvelope.serializer(), envelope)
