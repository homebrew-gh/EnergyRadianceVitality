package com.erv.app.weighttraining

/** The muscle group an exercise is listed under in pickers: its slug, or "other" when blank. */
fun WeightExercise.pickerMuscleKey(): String = muscleGroup.trim().lowercase().ifBlank { "other" }

/**
 * Exercises logged most recently, newest workout first, each once. Ids no longer in the library are
 * left out.
 */
fun WeightLibraryState.recentExerciseIds(limit: Int = 8): List<String> {
    if (limit <= 0) return emptyList()
    val known = exercises.mapTo(HashSet()) { it.id }
    val workouts = logs
        .flatMap { log -> log.workouts.map { log.date to it } }
        // Day log dates are ISO (yyyy-MM-dd), so they sort as text.
        .sortedWith(
            compareByDescending<Pair<String, WeightWorkoutSession>> { it.first }
                .thenByDescending { it.second.finishedAtEpochSeconds ?: it.second.startedAtEpochSeconds ?: 0L },
        )
    val out = LinkedHashSet<String>()
    for ((_, workout) in workouts) {
        for (entry in workout.entries) {
            if (entry.exerciseId in known) out.add(entry.exerciseId)
            if (out.size >= limit) return out.toList()
        }
    }
    return out.toList()
}

/** Lowercase words only, so "Pull-Up" and "pull up" match. */
internal fun normalizePickerText(text: String): String =
    text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/**
 * Exercises where every word of [query] appears in the name, muscle group or equipment, so
 * "dumbbell chest" works. Name matches come first: starts with the query, then contains it, then
 * the rest, each alphabetical. A blank query returns [exercises] as given.
 */
fun searchExercisesForPicker(exercises: List<WeightExercise>, query: String): List<WeightExercise> {
    val q = normalizePickerText(query)
    if (q.isEmpty()) return exercises
    val words = q.split(' ')
    return exercises
        .filter { ex ->
            val haystack = normalizePickerText("${ex.name} ${ex.muscleGroup} ${ex.equipment.displayLabel()}")
            words.all { haystack.contains(it) }
        }
        .sortedWith(
            compareBy<WeightExercise> { ex ->
                val name = normalizePickerText(ex.name)
                when {
                    name.startsWith(q) -> 0
                    name.contains(q) -> 1
                    words.all { name.contains(it) } -> 2
                    else -> 3
                }
            }.thenBy { it.name.lowercase() },
        )
}
