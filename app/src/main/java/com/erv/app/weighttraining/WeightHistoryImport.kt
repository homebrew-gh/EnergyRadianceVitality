package com.erv.app.weighttraining

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

/** One session that [merge] would append for a calendar day (for import preview). */
data class WeightImportDatedSession(
    val dateIso: String,
    val session: WeightWorkoutSession,
)

/** Top-level JSON object the app accepts for weight history import (v1). */
@Serializable
data class ErvWeightHistoryImportEnvelope(
    /** Must be `1` for the format described in the in-app import guide. */
    val ervWeightHistoryImportVersion: Int = 1,
    /**
     * Optional custom exercises to upsert before validating `dayLogs`.
     * Omit `sessionSummaries`; the app strips it on merge. Built-ins should use stable `erv-weight-exercise-*` ids from the guide.
     */
    val exercises: List<WeightExercise> = emptyList(),
    /** Per-day payloads; each session is merged with `source` forced to [WeightWorkoutSource.IMPORTED]. */
    val dayLogs: List<WeightDayLog> = emptyList(),
)

sealed class WeightImportOutcome {
    data class Success(
        val newState: WeightLibraryState,
        val affectedDates: List<String>,
        val sessionsImported: Int,
        val exercisesUpserted: Int,
    ) : WeightImportOutcome()

    data class Failure(val messages: List<String>) : WeightImportOutcome()
}

object WeightHistoryImport {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val isoDate = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun parseJsonEnvelope(text: String): Pair<ErvWeightHistoryImportEnvelope?, List<String>> {
        val errors = mutableListOf<String>()
        val env = try {
            json.decodeFromString(ErvWeightHistoryImportEnvelope.serializer(), text.trim())
        } catch (e: SerializationException) {
            errors += "Invalid JSON: ${e.message ?: "parse error"}"
            null
        } catch (e: IllegalArgumentException) {
            errors += "Invalid JSON: ${e.message ?: "parse error"}"
            null
        }
        return env to errors
    }

    fun merge(current: WeightLibraryState, envelope: ErvWeightHistoryImportEnvelope): WeightImportOutcome {
        val errors = mutableListOf<String>()
        if (envelope.ervWeightHistoryImportVersion != 1) {
            return WeightImportOutcome.Failure(
                listOf("Unsupported ervWeightHistoryImportVersion: ${envelope.ervWeightHistoryImportVersion} (expected 1)")
            )
        }
        if (envelope.dayLogs.isEmpty()) {
            return WeightImportOutcome.Failure(listOf("No dayLogs to import"))
        }

        var exercises = current.exercises
        for (raw in envelope.exercises) {
            val ex = raw.copy(sessionSummaries = emptyList())
            exercises = exercises.upsertExerciseById(ex)
        }
        val exercisesUpserted = envelope.exercises.size

        val idSet = exercises.map { it.id }.toSet()

        for (day in envelope.dayLogs) {
            if (!isoDate.matches(day.date)) {
                errors += "Invalid date (use YYYY-MM-DD): ${day.date}"
            }
            for (w in day.workouts) {
                if (w.entries.isEmpty()) {
                    errors += "Workout on ${day.date} has no entries"
                }
                for (e in w.entries) {
                    if (e.exerciseId !in idSet) {
                        errors += "Unknown exerciseId `${e.exerciseId}` on ${day.date}"
                    }
                    if (e.hiitBlock != null && e.sets.isNotEmpty()) {
                        errors += "Entry for `${e.exerciseId}` on ${day.date} cannot have both sets and hiitBlock"
                    }
                }
            }
        }
        if (errors.isNotEmpty()) {
            return WeightImportOutcome.Failure(errors.distinct())
        }

        val logs = current.logs.toMutableList()
        val affectedDates = mutableListOf<String>()
        var sessionsImported = 0

        for (day in envelope.dayLogs) {
            val normalizedWorkouts = mutableListOf<WeightWorkoutSession>()
            for (w in day.workouts) {
                if (w.entries.isEmpty()) continue
                normalizedWorkouts += w.copy(
                    id = UUID.randomUUID().toString(),
                    source = WeightWorkoutSource.IMPORTED,
                    routineId = null,
                    routineName = null,
                )
                sessionsImported++
            }
            if (normalizedWorkouts.isEmpty()) continue
            affectedDates += day.date
            val idx = logs.indexOfFirst { it.date == day.date }
            if (idx >= 0) {
                val old = logs[idx]
                logs[idx] = old.copy(workouts = old.workouts + normalizedWorkouts)
            } else {
                logs += WeightDayLog(date = day.date, workouts = normalizedWorkouts)
            }
        }
        logs.sortBy { it.date }

        if (sessionsImported == 0) {
            return WeightImportOutcome.Failure(listOf("No sessions with at least one entry to import"))
        }

        val newState = current.copy(exercises = exercises, logs = logs)
        return WeightImportOutcome.Success(
            newState = newState,
            affectedDates = affectedDates.distinct().sorted(),
            sessionsImported = sessionsImported,
            exercisesUpserted = envelope.exercises.size,
        )
    }

    /**
     * Lists sessions [merge] would append, in chronological order (by date, then append order).
     * Use with a [WeightImportOutcome.Success] from [merge] against the same [current] state.
     */
    fun sessionsAddedByImport(
        current: WeightLibraryState,
        success: WeightImportOutcome.Success,
    ): List<WeightImportDatedSession> {
        val out = mutableListOf<WeightImportDatedSession>()
        for (dateIso in success.affectedDates.sorted()) {
            val date = LocalDate.parse(dateIso)
            val oldCount = current.logFor(date)?.workouts?.size ?: 0
            val newLog = success.newState.logFor(date) ?: continue
            for (w in newLog.workouts.drop(oldCount)) {
                out += WeightImportDatedSession(dateIso = dateIso, session = w)
            }
        }
        return out
    }

    private fun List<WeightExercise>.upsertExerciseById(ex: WeightExercise): List<WeightExercise> {
        val list = toMutableList()
        val i = list.indexOfFirst { it.id == ex.id }
        if (i >= 0) list[i] = ex else list.add(ex)
        return list
    }
}
