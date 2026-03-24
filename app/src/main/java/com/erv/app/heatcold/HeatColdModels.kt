package com.erv.app.heatcold

import com.erv.app.SectionLogDateFilter
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID

@Serializable
enum class HeatColdMode { SAUNA, COLD_PLUNGE }

@Serializable
enum class TemperatureUnit { FAHRENHEIT, CELSIUS }

@Serializable
data class HeatColdSession(
    val id: String = "",
    val durationSeconds: Int,
    val tempValue: Double? = null,
    val tempUnit: TemperatureUnit? = null,
    val loggedAtEpochSeconds: Long = nowEpochSeconds()
)

@Serializable
data class HeatColdDayLog(
    val date: String,
    val sessions: List<HeatColdSession> = emptyList()
)

@Serializable
data class HeatColdLibraryState(
    val saunaLogs: List<HeatColdDayLog> = emptyList(),
    val coldLogs: List<HeatColdDayLog> = emptyList()
) {
    fun saunaLogFor(date: LocalDate): HeatColdDayLog? =
        saunaLogs.firstOrNull { it.date == date.toString() }

    fun coldLogFor(date: LocalDate): HeatColdDayLog? =
        coldLogs.firstOrNull { it.date == date.toString() }
}

fun HeatColdLibraryState.withStableSessionIds(): HeatColdLibraryState =
    copy(
        saunaLogs = saunaLogs.map { log ->
            log.copy(sessions = log.sessions.map { s ->
                if (s.id.isNotEmpty()) s else s.copy(id = stableLegacyHeatColdSessionId(s))
            })
        },
        coldLogs = coldLogs.map { log ->
            log.copy(sessions = log.sessions.map { s ->
                if (s.id.isNotEmpty()) s else s.copy(id = stableLegacyHeatColdSessionId(s))
            })
        }
    )

internal fun stableLegacyHeatColdSessionId(session: HeatColdSession): String {
    val key = listOf(
        session.loggedAtEpochSeconds.toString(),
        session.durationSeconds.toString(),
        session.tempValue?.toString().orEmpty(),
        session.tempUnit?.name.orEmpty()
    ).joinToString("\u0000")
    return UUID.nameUUIDFromBytes(key.toByteArray(StandardCharsets.UTF_8)).toString()
}

data class HeatColdActivityRow(
    val displayName: String,
    val durationSeconds: Int,
    val mode: HeatColdMode,
    val tempDisplay: String?,
    val session: HeatColdSession
)

fun HeatColdLibraryState.saunaActivityFor(date: LocalDate): List<HeatColdActivityRow> {
    val log = saunaLogFor(date) ?: return emptyList()
    return log.sessions.map { session ->
        HeatColdActivityRow(
            displayName = "Sauna",
            durationSeconds = session.durationSeconds,
            mode = HeatColdMode.SAUNA,
            tempDisplay = session.formatTemp(),
            session = session
        )
    }
}

fun HeatColdLibraryState.coldActivityFor(date: LocalDate): List<HeatColdActivityRow> {
    val log = coldLogFor(date) ?: return emptyList()
    return log.sessions.map { session ->
        HeatColdActivityRow(
            displayName = "Cold Plunge",
            durationSeconds = session.durationSeconds,
            mode = HeatColdMode.COLD_PLUNGE,
            tempDisplay = session.formatTemp(),
            session = session
        )
    }
}

fun HeatColdLibraryState.chronologicalSaunaLogFor(date: LocalDate): List<HeatColdSession> {
    val log = saunaLogFor(date) ?: return emptyList()
    return log.sessions.sortedWith(
        compareBy<HeatColdSession> { it.loggedAtEpochSeconds }.thenBy { it.id }
    )
}

fun HeatColdLibraryState.chronologicalColdLogFor(date: LocalDate): List<HeatColdSession> {
    val log = coldLogFor(date) ?: return emptyList()
    return log.sessions.sortedWith(
        compareBy<HeatColdSession> { it.loggedAtEpochSeconds }.thenBy { it.id }
    )
}

data class HeatColdTimelineEntry(
    val logDate: LocalDate,
    val mode: HeatColdMode,
    val session: HeatColdSession
)

fun HeatColdLibraryState.chronologicalHeatColdTimelineFor(date: LocalDate): List<HeatColdTimelineEntry> {
    val sauna = chronologicalSaunaLogFor(date).map { HeatColdTimelineEntry(date, HeatColdMode.SAUNA, it) }
    val cold = chronologicalColdLogFor(date).map { HeatColdTimelineEntry(date, HeatColdMode.COLD_PLUNGE, it) }
    return (sauna + cold).sortedWith(
        compareBy<HeatColdTimelineEntry> { it.session.loggedAtEpochSeconds }
            .thenBy { it.mode.ordinal }
            .thenBy { it.session.id }
    )
}

fun HeatColdLibraryState.chronologicalHeatColdTimelineForRange(start: LocalDate, end: LocalDate): List<HeatColdTimelineEntry> {
    val from = if (start <= end) start else end
    val to = if (start <= end) end else start
    val rows = mutableListOf<HeatColdTimelineEntry>()
    var d = from
    while (!d.isAfter(to)) {
        rows.addAll(chronologicalHeatColdTimelineFor(d))
        d = d.plusDays(1)
    }
    return rows.sortedWith(
        compareBy<HeatColdTimelineEntry> { it.logDate }
            .thenBy { it.session.loggedAtEpochSeconds }
            .thenBy { it.mode.ordinal }
            .thenBy { it.session.id }
    )
}

private fun List<HeatColdTimelineEntry>.sortedHeatColdNewestFirst(): List<HeatColdTimelineEntry> =
    sortedWith(
        compareByDescending<HeatColdTimelineEntry> { it.session.loggedAtEpochSeconds }
            .thenByDescending { it.logDate }
            .thenBy { it.mode.ordinal }
            .thenBy { it.session.id }
    )

fun HeatColdLibraryState.heatColdTimelineForSectionLog(filter: SectionLogDateFilter): List<HeatColdTimelineEntry> =
    when (filter) {
        SectionLogDateFilter.AllHistory -> {
            val rows = mutableListOf<HeatColdTimelineEntry>()
            for (dl in saunaLogs) {
                val d = LocalDate.parse(dl.date)
                dl.sessions.forEach { rows.add(HeatColdTimelineEntry(d, HeatColdMode.SAUNA, it)) }
            }
            for (dl in coldLogs) {
                val d = LocalDate.parse(dl.date)
                dl.sessions.forEach { rows.add(HeatColdTimelineEntry(d, HeatColdMode.COLD_PLUNGE, it)) }
            }
            rows.sortedHeatColdNewestFirst()
        }
        is SectionLogDateFilter.SingleDay ->
            chronologicalHeatColdTimelineFor(filter.day).sortedHeatColdNewestFirst()
        is SectionLogDateFilter.DateRange ->
            chronologicalHeatColdTimelineForRange(filter.startInclusive, filter.endInclusive).sortedHeatColdNewestFirst()
    }

fun HeatColdSession.formatTemp(): String? {
    val v = tempValue ?: return null
    val u = tempUnit ?: return null
    return when (u) {
        TemperatureUnit.FAHRENHEIT -> "${v.toInt()}°F"
        TemperatureUnit.CELSIUS -> "${v.toInt()}°C"
    }
}

fun formatDurationSeconds(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins == 0) "${secs}s"
    else if (secs == 0) "${mins} min"
    else "${mins}:%02d".format(secs)
}

val HeatColdActivityRow.summaryLine: String
    get() {
        val dur = formatDurationSeconds(durationSeconds)
        val temp = tempDisplay?.let { " @ $it" }.orEmpty()
        return "$displayName • $dur$temp"
    }

fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000
