package com.erv.app.stretching

import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID

@Serializable
data class StretchCatalogEntry(
    val id: String,
    val name: String,
    val targetBodyParts: List<String> = emptyList(),
    val procedure: String = ""
)

@Serializable
data class StretchCatalogRoot(
    val stretches: List<StretchCatalogEntry> = emptyList()
)

@Serializable
data class StretchRoutine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val stretchIds: List<String> = emptyList(),
    /** Hold time for each stretch in the guided player. */
    val holdSecondsPerStretch: Int = 30
)

@Serializable
data class StretchSession(
    val routineId: String? = null,
    val routineName: String? = null,
    val stretchIds: List<String> = emptyList(),
    val totalMinutes: Int = 0,
    val loggedAtEpochSeconds: Long = nowEpochSeconds(),
    val id: String = ""
)

internal fun stableLegacyStretchSessionId(session: StretchSession): String {
    val key = listOf(
        session.loggedAtEpochSeconds.toString(),
        session.totalMinutes.toString(),
        session.routineId.orEmpty(),
        session.routineName.orEmpty(),
        session.stretchIds.joinToString(",")
    ).joinToString("\u0000")
    return UUID.nameUUIDFromBytes(key.toByteArray(StandardCharsets.UTF_8)).toString()
}

fun StretchLibraryState.withStableSessionIds(): StretchLibraryState =
    copy(logs = logs.map { log ->
        log.copy(sessions = log.sessions.map { s ->
            if (s.id.isNotEmpty()) s else s.copy(id = stableLegacyStretchSessionId(s))
        })
    })

@Serializable
data class StretchDayLog(
    val date: String,
    val sessions: List<StretchSession> = emptyList()
)

@Serializable
data class StretchLibraryState(
    val routines: List<StretchRoutine> = emptyList(),
    val logs: List<StretchDayLog> = emptyList()
) {
    fun routineById(id: String): StretchRoutine? = routines.firstOrNull { it.id == id }
    fun logFor(date: LocalDate): StretchDayLog? = logs.firstOrNull { it.date == date.toString() }
}

data class StretchActivityRow(
    val summaryLine: String,
    val session: StretchSession
)

fun StretchLibraryState.chronologicalStretchLogFor(date: LocalDate): List<StretchSession> {
    val log = logFor(date) ?: return emptyList()
    return log.sessions.sortedByDescending { it.loggedAtEpochSeconds }
}

fun StretchLibraryState.stretchActivityFor(date: LocalDate): List<StretchActivityRow> {
    val log = logFor(date) ?: return emptyList()
    return log.sessions.map { session ->
        val label = session.routineName ?: "Stretch session"
        val parts = mutableListOf<String>()
        parts.add(label)
        if (session.totalMinutes > 0) parts.add("${session.totalMinutes} min")
        if (session.stretchIds.isNotEmpty()) parts.add("${session.stretchIds.size} stretches")
        StretchActivityRow(summaryLine = parts.joinToString(" • "), session = session)
    }
}

fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000
