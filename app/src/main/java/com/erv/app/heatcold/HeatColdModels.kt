package com.erv.app.heatcold

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
    return log.sessions.sortedByDescending { it.loggedAtEpochSeconds }
}

fun HeatColdLibraryState.chronologicalColdLogFor(date: LocalDate): List<HeatColdSession> {
    val log = coldLogFor(date) ?: return emptyList()
    return log.sessions.sortedByDescending { it.loggedAtEpochSeconds }
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
