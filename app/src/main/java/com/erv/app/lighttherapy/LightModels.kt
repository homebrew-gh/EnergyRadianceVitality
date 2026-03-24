package com.erv.app.lighttherapy

import com.erv.app.SectionLogDateFilter
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID

/** Type of light device (e.g. red/NIR, blue/UV, circadian). See Chroma-style categories. */
@Serializable
enum class LightDeviceType {
    RED_NIR,       // Deep red & NIR (mitochondria, recovery)
    BLUE_UV,       // Blue, UVA, UVB (e.g. SAD, vitamin D)
    CIRCADIAN,    // Full-spectrum / daylight simulation (focus, desk)
    BLUE_BLOCKER,  // Eyewear / filters
    OTHER
}

@Serializable
enum class LightTimeOfDay { MORNING, AFTERNOON, NIGHT }

@Serializable
enum class LightWeekday {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}

/** User's catalog entry for a light device (Chroma-style: wavelengths, power, recommended duration). */
@Serializable
data class LightDevice(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val brand: String = "",
    val deviceType: LightDeviceType = LightDeviceType.RED_NIR,
    /** e.g. "660nm, 850nm" or "narrowband UVB" */
    val wavelengths: String = "",
    /** Power output description, e.g. "50W", "irradiance at 6 inches" */
    val powerOutput: String = "",
    /** Recommended session duration in minutes; null = user decides */
    val recommendedDurationMinutes: Int? = null,
    val notes: String = ""
)

/** A scheduled light therapy routine (part of day, duration, which device, which days). */
@Serializable
data class LightRoutine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val timeOfDay: LightTimeOfDay = LightTimeOfDay.MORNING,
    val durationMinutes: Int = 15,
    val deviceId: String? = null,
    /** Days of the week this routine is active; empty = every day */
    val repeatDays: List<LightWeekday> = emptyList(),
    val notes: String = ""
) {
    fun repeatDaysSet(): Set<LightWeekday> = repeatDays.toSet()
}

/** A single logged session (from timer or manual). */
@Serializable
data class LightSession(
    val minutes: Int,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val routineId: String? = null,
    val routineName: String? = null,
    val loggedAtEpochSeconds: Long = nowEpochSeconds(),
    /** Stable id for delete/sync; empty in legacy JSON is filled by [LightLibraryState.withStableSessionIds]. */
    val id: String = ""
)

/** Deterministic id for sessions stored before [LightSession.id] existed. */
internal fun stableLegacyLightSessionId(session: LightSession): String {
    val key = listOf(
        session.loggedAtEpochSeconds.toString(),
        session.minutes.toString(),
        session.routineId.orEmpty(),
        session.deviceId.orEmpty(),
        session.routineName.orEmpty(),
        session.deviceName.orEmpty()
    ).joinToString("\u0000")
    return UUID.nameUUIDFromBytes(key.toByteArray(StandardCharsets.UTF_8)).toString()
}

fun LightLibraryState.withStableSessionIds(): LightLibraryState =
    copy(logs = logs.map { log ->
        log.copy(sessions = log.sessions.map { s ->
            if (s.id.isNotEmpty()) s else s.copy(id = stableLegacyLightSessionId(s))
        })
    })

@Serializable
data class LightDayLog(
    val date: String,
    val sessions: List<LightSession> = emptyList()
)

@Serializable
data class LightLibraryState(
    val devices: List<LightDevice> = emptyList(),
    val routines: List<LightRoutine> = emptyList(),
    val logs: List<LightDayLog> = emptyList()
) {
    fun deviceById(id: String): LightDevice? = devices.firstOrNull { it.id == id }
    fun routineById(id: String): LightRoutine? = routines.firstOrNull { it.id == id }
    fun logFor(date: LocalDate): LightDayLog? = logs.firstOrNull { it.date == date.toString() }
}

/** For dashboard activity: display name + duration. */
data class LightActivityRow(
    val displayName: String,
    val minutes: Int,
    val session: LightSession
)

fun LightLibraryState.lightActivityFor(date: LocalDate): List<LightActivityRow> {
    val log = logFor(date) ?: return emptyList()
    return log.sessions.map { session ->
        val name = session.routineName
            ?: session.deviceName
            ?: "Light therapy"
        LightActivityRow(
            displayName = name,
            minutes = session.minutes,
            session = session
        )
    }
}

fun LightLibraryState.chronologicalLightLogFor(date: LocalDate): List<LightSession> {
    val log = logFor(date) ?: return emptyList()
    return log.sessions.sortedWith(
        compareBy<LightSession> { it.loggedAtEpochSeconds }.thenBy { it.id }
    )
}

data class DatedLightSession(val logDate: LocalDate, val session: LightSession)

fun LightLibraryState.chronologicalLightLogForRange(start: LocalDate, end: LocalDate): List<DatedLightSession> {
    val from = if (start <= end) start else end
    val to = if (start <= end) end else start
    val rows = mutableListOf<DatedLightSession>()
    var d = from
    while (!d.isAfter(to)) {
        chronologicalLightLogFor(d).forEach { rows.add(DatedLightSession(d, it)) }
        d = d.plusDays(1)
    }
    return rows.sortedWith(
        compareBy<DatedLightSession> { it.logDate }
            .thenBy { it.session.loggedAtEpochSeconds }
            .thenBy { it.session.id }
    )
}

private fun List<DatedLightSession>.sortedLightNewestFirst(): List<DatedLightSession> =
    sortedWith(
        compareByDescending<DatedLightSession> { it.session.loggedAtEpochSeconds }
            .thenByDescending { it.logDate }
            .thenBy { it.session.id }
    )

fun LightLibraryState.datedLightSessionsForSectionLog(filter: SectionLogDateFilter): List<DatedLightSession> =
    when (filter) {
        SectionLogDateFilter.AllHistory -> {
            val rows = mutableListOf<DatedLightSession>()
            for (dl in logs) {
                val d = LocalDate.parse(dl.date)
                dl.sessions.forEach { rows.add(DatedLightSession(d, it)) }
            }
            rows.sortedLightNewestFirst()
        }
        is SectionLogDateFilter.SingleDay ->
            chronologicalLightLogFor(filter.day).map { DatedLightSession(filter.day, it) }.sortedLightNewestFirst()
        is SectionLogDateFilter.DateRange ->
            chronologicalLightLogForRange(filter.startInclusive, filter.endInclusive).sortedLightNewestFirst()
    }

fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

fun LightDeviceType.label(): String = when (this) {
    LightDeviceType.RED_NIR -> "Red / NIR"
    LightDeviceType.BLUE_UV -> "Blue / UV"
    LightDeviceType.CIRCADIAN -> "Circadian / daylight"
    LightDeviceType.BLUE_BLOCKER -> "Blue blocker"
    LightDeviceType.OTHER -> "Other"
}

fun LightTimeOfDay.label(): String = when (this) {
    LightTimeOfDay.MORNING -> "Morning"
    LightTimeOfDay.AFTERNOON -> "Afternoon"
    LightTimeOfDay.NIGHT -> "Night"
}

fun LightWeekday.shortLabel(): String = when (this) {
    LightWeekday.MONDAY -> "Mon"
    LightWeekday.TUESDAY -> "Tue"
    LightWeekday.WEDNESDAY -> "Wed"
    LightWeekday.THURSDAY -> "Thu"
    LightWeekday.FRIDAY -> "Fri"
    LightWeekday.SATURDAY -> "Sat"
    LightWeekday.SUNDAY -> "Sun"
}
