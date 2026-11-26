package com.erv.app.stretching

import com.erv.app.SectionLogDateFilter
import com.erv.app.unifiedroutines.UnifiedSessionLink
import com.erv.app.workouts.WorkoutSessionLink
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

fun stretchCategoryDisplayLabel(categoryKey: String): String =
    when (categoryKey.lowercase(Locale.getDefault())) {
        "neck" -> "Neck"
        "shoulders" -> "Shoulders"
        "arms" -> "Arms"
        "chest" -> "Chest"
        "back" -> "Back"
        "core" -> "Core"
        "glutes" -> "Glutes"
        "legs" -> "Legs"
        "other" -> "Other"
        else -> categoryKey.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

/** Groups stretches for list UIs; sorts categories and names within each group. */
fun List<StretchCatalogEntry>.groupedByCategory(): List<Pair<String, List<StretchCatalogEntry>>> {
    val locale = Locale.getDefault()
    fun normCat(e: StretchCatalogEntry): String =
        e.category.trim().lowercase(locale).ifBlank { "other" }
    val byCat = groupBy { normCat(it) }
    val orderedKeys = byCat.keys.sorted()
    return orderedKeys.map { cat ->
        cat to (byCat[cat].orEmpty().sortedBy { it.name.lowercase(locale) })
    }.filter { it.second.isNotEmpty() }
}

@Serializable
data class StretchCatalogEntry(
    val id: String,
    val name: String,
    /** Broad area for catalog grouping (e.g. legs, neck). */
    val category: String = "other",
    /**
     * If true, the guided player runs **two** holds ([StretchSide.RIGHT] then [StretchSide.LEFT]),
     * each for that stretch's hold ([StretchRoutine.holdSecondsFor]).
     */
    val requiresBothSides: Boolean = false,
    val targetBodyParts: List<String> = emptyList(),
    val procedure: String = ""
)

/** Hold segments for one catalog slot (1 = single timer; 2 = left + right each for full hold). */
fun StretchCatalogEntry.holdSideSegments(): Int = if (requiresBothSides) 2 else 1

fun routineHoldSegments(stretchIds: List<String>, catalog: List<StretchCatalogEntry>): Int {
    val byId = catalog.associateBy { it.id }
    return stretchIds.sumOf { id -> byId[id]?.holdSideSegments() ?: 1 }
}

enum class StretchSide {
    RIGHT,
    LEFT
}

data class GuidedStretchStep(
    val entry: StretchCatalogEntry,
    /** Null for single-phase stretches; [StretchSide.RIGHT] then [StretchSide.LEFT] for bilateral. */
    val side: StretchSide?
)

/** Expands bilateral catalog entries into two timed steps (right, then left). */
fun List<StretchCatalogEntry>.toGuidedSessionSteps(): List<GuidedStretchStep> =
    flatMap { entry ->
        if (entry.requiresBothSides) {
            listOf(
                GuidedStretchStep(entry, StretchSide.RIGHT),
                GuidedStretchStep(entry, StretchSide.LEFT)
            )
        } else {
            listOf(GuidedStretchStep(entry, null))
        }
    }

@Serializable
data class StretchCatalogRoot(
    val stretches: List<StretchCatalogEntry> = emptyList()
)

@Serializable
data class StretchRoutine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val stretchIds: List<String> = emptyList(),
    /** Hold time for each stretch in the guided player (each side, for two-sided stretches). */
    val holdSecondsPerStretch: Int = 30,
    /**
     * Optional hold times for particular stretches, by catalog id; any stretch not listed uses
     * [holdSecondsPerStretch]. Routines saved before this existed have none.
     */
    val holdSecondsByStretchId: Map<String, Int> = emptyMap(),
    /** Seconds between different stretches in the guided player; null keeps the default. */
    val gapSeconds: Int? = null,
)

const val STRETCH_HOLD_MIN_SECONDS = 5
const val STRETCH_HOLD_MAX_SECONDS = 300
const val STRETCH_GAP_DEFAULT_SECONDS = 10
const val STRETCH_GAP_MIN_SECONDS = 5
const val STRETCH_GAP_MAX_SECONDS = 60

/** The hold, in seconds, for [stretchId] in this routine: its own time if set, else the routine's. */
fun StretchRoutine.holdSecondsFor(stretchId: String): Int =
    (holdSecondsByStretchId[stretchId]?.takeIf { it > 0 } ?: holdSecondsPerStretch)
        .coerceIn(STRETCH_HOLD_MIN_SECONDS, STRETCH_HOLD_MAX_SECONDS)

/** One hold time per entry of [StretchRoutine.stretchIds], in order. */
fun StretchRoutine.entryHoldSeconds(): List<Int> = stretchIds.map { holdSecondsFor(it) }

/** Seconds between different stretches, with the default when unset. */
fun StretchRoutine.gapSecondsOrDefault(): Int =
    (gapSeconds ?: STRETCH_GAP_DEFAULT_SECONDS).coerceIn(STRETCH_GAP_MIN_SECONDS, STRETCH_GAP_MAX_SECONDS)

/**
 * Only the per-stretch times that differ from [default] and belong to [stretchIds], so saving a
 * routine never keeps times for stretches it dropped or times that just repeat the routine's.
 */
fun cleanedStretchHoldOverrides(overrides: Map<String, Int>, stretchIds: List<String>, default: Int): Map<String, Int> {
    val ids = stretchIds.toSet()
    return overrides
        .filterKeys { it in ids }
        .mapValues { (_, v) -> v.coerceIn(STRETCH_HOLD_MIN_SECONDS, STRETCH_HOLD_MAX_SECONDS) }
        .filterValues { it != default }
}

/** Expands one hold per entry into one hold per guided step (both sides of a stretch get its hold). */
fun guidedStepHoldSeconds(entries: List<StretchCatalogEntry>, entryHolds: List<Int>, fallback: Int = 30): List<Int> =
    entries.flatMapIndexed { i, entry ->
        val hold = entryHolds.getOrNull(i) ?: fallback
        List(entry.holdSideSegments()) { hold }
    }

@Serializable
data class StretchSession(
    val routineId: String? = null,
    val routineName: String? = null,
    val stretchIds: List<String> = emptyList(),
    val totalMinutes: Int = 0,
    val loggedAtEpochSeconds: Long = nowEpochSeconds(),
    val id: String = "",
    val unifiedLink: UnifiedSessionLink? = null,
    val workoutLink: WorkoutSessionLink? = null,
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
    return log.sessions.sortedWith(
        compareBy<StretchSession> { it.loggedAtEpochSeconds }.thenBy { it.id }
    )
}

data class DatedStretchSession(val logDate: LocalDate, val session: StretchSession)

fun StretchLibraryState.chronologicalStretchLogForRange(start: LocalDate, end: LocalDate): List<DatedStretchSession> {
    val from = if (start <= end) start else end
    val to = if (start <= end) end else start
    val rows = mutableListOf<DatedStretchSession>()
    var d = from
    while (!d.isAfter(to)) {
        chronologicalStretchLogFor(d).forEach { rows.add(DatedStretchSession(d, it)) }
        d = d.plusDays(1)
    }
    return rows.sortedWith(
        compareBy<DatedStretchSession> { it.logDate }
            .thenBy { it.session.loggedAtEpochSeconds }
            .thenBy { it.session.id }
    )
}

private fun List<DatedStretchSession>.sortedStretchNewestFirst(): List<DatedStretchSession> =
    sortedWith(
        compareByDescending<DatedStretchSession> { it.session.loggedAtEpochSeconds }
            .thenByDescending { it.logDate }
            .thenBy { it.session.id }
    )

fun StretchLibraryState.datedStretchSessionsForSectionLog(filter: SectionLogDateFilter): List<DatedStretchSession> =
    when (filter) {
        SectionLogDateFilter.AllHistory -> {
            val rows = mutableListOf<DatedStretchSession>()
            for (dl in logs) {
                val d = LocalDate.parse(dl.date)
                dl.sessions.forEach { rows.add(DatedStretchSession(d, it)) }
            }
            rows.sortedStretchNewestFirst()
        }
        is SectionLogDateFilter.SingleDay ->
            chronologicalStretchLogFor(filter.day).map { DatedStretchSession(filter.day, it) }.sortedStretchNewestFirst()
        is SectionLogDateFilter.DateRange ->
            chronologicalStretchLogForRange(filter.startInclusive, filter.endInclusive).sortedStretchNewestFirst()
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
