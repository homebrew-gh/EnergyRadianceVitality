package com.erv.app.cardio

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

/** Top-level JSON object the app accepts for cardio history import (v1). */
@Serializable
data class ErvCardioHistoryImportEnvelope(
    /** Must be `1` for the format described in the in-app import guide. */
    val ervCardioHistoryImportVersion: Int = 1,
    /** Optional custom activity types to upsert before validating `dayLogs`. */
    val customActivityTypes: List<CardioCustomActivityType> = emptyList(),
    /** Per-day payloads; each session is merged with `source` forced to [CardioSessionSource.IMPORTED]. */
    val dayLogs: List<CardioDayLog> = emptyList(),
)

data class CardioImportDatedSession(
    val dateIso: String,
    val session: CardioSession,
)

sealed class CardioImportOutcome {
    data class Success(
        val newState: CardioLibraryState,
        val affectedDates: List<String>,
        val sessionsImported: Int,
        val customTypesUpserted: Int,
    ) : CardioImportOutcome()

    data class Failure(val messages: List<String>) : CardioImportOutcome()
}

object CardioHistoryImport {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val isoDate = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun parseJsonEnvelope(text: String): Pair<ErvCardioHistoryImportEnvelope?, List<String>> {
        val errors = mutableListOf<String>()
        val env = try {
            json.decodeFromString(ErvCardioHistoryImportEnvelope.serializer(), text.trim())
        } catch (e: SerializationException) {
            errors += "Invalid JSON: ${e.message ?: "parse error"}"
            null
        } catch (e: IllegalArgumentException) {
            errors += "Invalid JSON: ${e.message ?: "parse error"}"
            null
        }
        return env to errors
    }

    fun merge(current: CardioLibraryState, envelope: ErvCardioHistoryImportEnvelope): CardioImportOutcome {
        val errors = mutableListOf<String>()
        if (envelope.ervCardioHistoryImportVersion != 1) {
            return CardioImportOutcome.Failure(
                listOf("Unsupported ervCardioHistoryImportVersion: ${envelope.ervCardioHistoryImportVersion} (expected 1)")
            )
        }
        if (envelope.dayLogs.isEmpty()) {
            return CardioImportOutcome.Failure(listOf("No dayLogs to import"))
        }

        var customTypes = current.customActivityTypes
        for (t in envelope.customActivityTypes) {
            customTypes = customTypes.upsertCustomTypeById(t)
        }
        val customIdSet = customTypes.map { it.id }.toSet()

        for (day in envelope.dayLogs) {
            if (!isoDate.matches(day.date)) {
                errors += "Invalid date (use YYYY-MM-DD): ${day.date}"
            }
            for (session in day.sessions) {
                collectSessionErrors(session, customIdSet, day.date, errors)
            }
        }
        if (errors.isNotEmpty()) {
            return CardioImportOutcome.Failure(errors.distinct())
        }

        val logs = current.logs.toMutableList()
        val affectedDates = mutableListOf<String>()
        var sessionsImported = 0

        for (day in envelope.dayLogs) {
            val normalizedSessions = mutableListOf<CardioSession>()
            for (s in day.sessions) {
                normalizedSessions += normalizeImportedSession(s)
                sessionsImported++
            }
            if (normalizedSessions.isEmpty()) continue
            affectedDates += day.date
            val idx = logs.indexOfFirst { it.date == day.date }
            if (idx >= 0) {
                val old = logs[idx]
                logs[idx] = old.copy(sessions = old.sessions + normalizedSessions)
            } else {
                logs += CardioDayLog(date = day.date, sessions = normalizedSessions)
            }
        }
        logs.sortBy { it.date }

        if (sessionsImported == 0) {
            return CardioImportOutcome.Failure(listOf("No sessions to import"))
        }

        val newState = current.copy(customActivityTypes = customTypes, logs = logs)
        return CardioImportOutcome.Success(
            newState = newState,
            affectedDates = affectedDates.distinct().sorted(),
            sessionsImported = sessionsImported,
            customTypesUpserted = envelope.customActivityTypes.size,
        )
    }

    fun sessionsAddedByImport(
        current: CardioLibraryState,
        success: CardioImportOutcome.Success,
    ): List<CardioImportDatedSession> {
        val out = mutableListOf<CardioImportDatedSession>()
        for (dateIso in success.affectedDates.sorted()) {
            val date = LocalDate.parse(dateIso)
            val oldCount = current.logFor(date)?.sessions?.size ?: 0
            val newLog = success.newState.logFor(date) ?: continue
            for (s in newLog.sessions.drop(oldCount)) {
                out += CardioImportDatedSession(dateIso = dateIso, session = s)
            }
        }
        return out
    }

    private fun collectSessionErrors(
        session: CardioSession,
        customIdSet: Set<String>,
        date: String,
        errors: MutableList<String>,
    ) {
        val prefix = "Session on $date"
        if (session.durationMinutes < 1) {
            errors += "$prefix: durationMinutes must be at least 1"
        }
        activityErrors(session.activity, customIdSet, prefix, errors)
        if (session.modality == CardioModality.INDOOR_TREADMILL) {
            val b = session.activity.builtin
            if (b == null) {
                errors += "$prefix: INDOOR_TREADMILL is only for built-in walk, run, sprint, or ruck"
            } else if (!b.supportsTreadmillModality()) {
                errors += "$prefix: ${b.name} cannot use INDOOR_TREADMILL modality"
            }
            if (b != null && b.supportsTreadmillModality()) {
                val t = session.treadmill
                if (t == null && b != CardioBuiltinActivity.SPRINT) {
                    errors += "$prefix: treadmill speed/incline required for indoor ${b.name}"
                }
                if (t != null && t.speed <= 0) {
                    errors += "$prefix: treadmill speed must be positive"
                }
            }
        }
        for (seg in session.segments) {
            val sp = "Segment (${seg.activity.displayLabel}) on $date"
            if (seg.durationMinutes < 1) {
                errors += "$sp: durationMinutes must be at least 1"
            }
            activityErrors(seg.activity, customIdSet, sp, errors)
            if (seg.modality == CardioModality.INDOOR_TREADMILL) {
                val b = seg.activity.builtin
                if (b == null) {
                    errors += "$sp: INDOOR_TREADMILL is only for built-in walk, run, sprint, or ruck"
                } else if (!b.supportsTreadmillModality()) {
                    errors += "$sp: ${b.name} cannot use INDOOR_TREADMILL modality"
                }
                if (b != null && b.supportsTreadmillModality()) {
                    val t = seg.treadmill
                    if (t == null && b != CardioBuiltinActivity.SPRINT) {
                        errors += "$sp: treadmill params required for indoor ${b.name}"
                    }
                    if (t != null && t.speed <= 0) {
                        errors += "$sp: treadmill speed must be positive"
                    }
                }
            }
        }
    }

    private fun activityErrors(
        activity: CardioActivitySnapshot,
        customIdSet: Set<String>,
        prefix: String,
        errors: MutableList<String>,
    ) {
        if (activity.displayLabel.isBlank()) {
            errors += "$prefix: activity.displayLabel is required"
        }
        if (activity.builtin != null) {
            return
        }
        val cid = activity.customTypeId
        if (cid.isNullOrBlank()) {
            errors += "$prefix: set activity.builtin or activity.customTypeId"
        } else if (cid !in customIdSet) {
            errors += "$prefix: unknown customTypeId `$cid` (add to customActivityTypes in file or app)"
        }
    }

    private fun normalizeImportedSession(s: CardioSession): CardioSession {
        val treadmillEff = if (s.modality == CardioModality.INDOOR_TREADMILL) {
            effectiveTreadmill(s.modality, s.activity.builtin, s.treadmill)
        } else {
            null
        }
        val segments = s.segments.map { seg ->
            val tm = if (seg.modality == CardioModality.INDOOR_TREADMILL) {
                effectiveTreadmill(seg.modality, seg.activity.builtin, seg.treadmill)
            } else {
                null
            }
            seg.copy(id = UUID.randomUUID().toString(), treadmill = tm)
        }
        return s.copy(
            id = UUID.randomUUID().toString(),
            modality = s.modality,
            treadmill = treadmillEff,
            source = CardioSessionSource.IMPORTED,
            routineId = null,
            routineName = null,
            gpsTrack = null,
            routeImageUrl = null,
            loggedAtEpochSeconds = nowEpochSeconds(),
            segments = segments,
        )
    }

    private fun effectiveTreadmill(
        modality: CardioModality,
        builtin: CardioBuiltinActivity?,
        treadmill: CardioTreadmillParams?,
    ): CardioTreadmillParams? {
        if (modality != CardioModality.INDOOR_TREADMILL) return null
        if (treadmill != null) return treadmill
        return if (builtin == CardioBuiltinActivity.SPRINT) {
            defaultSprintIndoorTreadmillParams()
        } else {
            null
        }
    }

    private fun List<CardioCustomActivityType>.upsertCustomTypeById(
        type: CardioCustomActivityType,
    ): List<CardioCustomActivityType> {
        val list = toMutableList()
        val i = list.indexOfFirst { it.id == type.id }
        if (i >= 0) list[i] = type else list.add(type)
        return list
    }
}
