package com.erv.app.cardio

import java.time.LocalDate

/** Activities whose records are kept together. */
enum class CardioRecordGroup(
    val label: String,
    val activities: Set<CardioBuiltinActivity>,
    /** Fastest-effort distances for this group, in metres, with their names. */
    val efforts: List<Pair<String, Double>>,
    val tracksClimbing: Boolean,
) {
    RUN(
        "Run",
        setOf(CardioBuiltinActivity.RUN, CardioBuiltinActivity.SPRINT),
        listOf("Mile" to MILE, "5K" to 5_000.0, "10K" to 10_000.0, "Half Marathon" to 21_097.5),
        tracksClimbing = true,
    ),
    RIDE(
        "Ride",
        setOf(CardioBuiltinActivity.BIKE),
        listOf("10 Miles" to 10 * MILE, "25 Miles" to 25 * MILE, "50 Miles" to 50 * MILE),
        tracksClimbing = true,
    ),
    INDOOR_RIDE(
        "Indoor Ride",
        setOf(CardioBuiltinActivity.STATIONARY_BIKE),
        listOf("10 Miles" to 10 * MILE, "20 Miles" to 20 * MILE),
        tracksClimbing = false,
    ),
    RUCK(
        "Ruck",
        setOf(CardioBuiltinActivity.RUCK),
        listOf("Mile" to MILE, "5K" to 5_000.0, "12 Miles" to 12 * MILE),
        tracksClimbing = true,
    ),
    WALK(
        "Walk",
        setOf(CardioBuiltinActivity.WALK),
        listOf("Mile" to MILE, "5K" to 5_000.0),
        tracksClimbing = false,
    ),
    HIKE("Hike", setOf(CardioBuiltinActivity.HIKE), emptyList(), tracksClimbing = true),
    ROW(
        "Row",
        setOf(CardioBuiltinActivity.ROWING),
        listOf("500 m" to 500.0, "2K" to 2_000.0, "5K" to 5_000.0),
        tracksClimbing = false,
    ),
    ;

    companion object {
        fun of(activity: CardioActivitySnapshot): CardioRecordGroup? =
            activity.builtin?.let { b -> entries.firstOrNull { b in it.activities } }
    }
}

private const val MILE = 1_609.344

sealed class CardioRecordKind {
    /** Fastest time over a distance; the value is seconds, lower is better. */
    data class Fastest(val name: String, val meters: Double) : CardioRecordKind()
    /** Value in metres. */
    data object LongestDistance : CardioRecordKind()
    /** Value in seconds. */
    data object LongestTime : CardioRecordKind()
    /** Value in metres of climbing. */
    data object MostClimbing : CardioRecordKind()
    /** Value in kilograms of pack weight. */
    data object HeaviestPack : CardioRecordKind()

    val title: String
        get() = when (this) {
            is Fastest -> "Fastest $name"
            LongestDistance -> "Longest Distance"
            LongestTime -> "Longest Time"
            MostClimbing -> "Most Climbing"
            HeaviestPack -> "Heaviest Pack"
        }

    val lowerIsBetter: Boolean get() = this is Fastest
}

data class CardioRecord(
    val group: CardioRecordGroup,
    val kind: CardioRecordKind,
    val value: Double,
    val sessionId: String,
    val date: LocalDate,
) {
    fun beats(other: CardioRecord?): Boolean = when {
        other == null -> true
        kind.lowerIsBetter -> value < other.value - 0.5
        else -> value > other.value + 0.5
    }
}

/** A record [session] set, with the one it beat (null for the first of its kind). */
data class CardioNewRecord(val record: CardioRecord, val previous: CardioRecord?)

object CardioRecords {

    /** Fastest time over [meters] in this session, in seconds, or null when it didn't go that far. */
    fun fastestSeconds(session: CardioSession, meters: Double): Double? {
        val points = session.gpsTrack?.points?.takeIf { it.size >= 2 }?.sortedBy { it.epochSeconds }
        if (points != null) {
            fromRoute(points, meters)?.let { return it }
        }
        // Without a route, the whole session at its average pace, when it covered the distance.
        val distance = session.totalLoggedDistanceMeters() ?: return null
        if (distance + 0.5 < meters) return null
        val seconds = sessionSeconds(session).takeIf { it > 0 } ?: return null
        return seconds * meters / distance
    }

    /** The quickest stretch of the route that covers [meters], scaled to exactly [meters]. */
    private fun fromRoute(points: List<CardioGpsPoint>, meters: Double): Double? {
        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            cumulative[i] = cumulative[i - 1] + CardioGpsMath.haversineMeters(a.lat, a.lon, b.lat, b.lon)
        }
        if (cumulative.last() + 0.5 < meters) return null
        var best: Double? = null
        var start = 0
        for (end in 1 until points.size) {
            // Move the start up while the stretch still covers the distance.
            while (start + 1 < end && cumulative[end] - cumulative[start + 1] >= meters) start++
            val covered = cumulative[end] - cumulative[start]
            if (covered >= meters) {
                val seconds = (points[end].epochSeconds - points[start].epochSeconds).toDouble()
                if (seconds > 0) {
                    val scaled = seconds * meters / covered
                    if (best == null || scaled < best) best = scaled
                }
            }
        }
        return best
    }

    internal fun sessionSeconds(session: CardioSession): Double {
        val start = session.startEpochSeconds
        val end = session.endEpochSeconds
        if (start != null && end != null && end > start) return (end - start).toDouble()
        return session.durationMinutes * 60.0
    }

    /** Every record [session] holds on its own, as if it were the only one logged. */
    fun recordsOf(session: CardioSession, date: LocalDate): List<CardioRecord> {
        val group = CardioRecordGroup.of(session.activity) ?: return emptyList()
        // Several activities in one session have no single record to hold.
        if (session.segments.isNotEmpty()) return emptyList()
        val out = mutableListOf<CardioRecord>()
        fun add(kind: CardioRecordKind, value: Double?) {
            if (value != null && value > 0 && value.isFinite()) out += CardioRecord(group, kind, value, session.id, date)
        }
        group.efforts.forEach { (name, meters) -> add(CardioRecordKind.Fastest(name, meters), fastestSeconds(session, meters)) }
        add(CardioRecordKind.LongestDistance, session.totalLoggedDistanceMeters())
        add(CardioRecordKind.LongestTime, sessionSeconds(session))
        if (group.tracksClimbing) add(CardioRecordKind.MostClimbing, session.resolvedElevationMeters()?.first?.takeIf { it >= 1.0 })
        if (group == CardioRecordGroup.RUCK) add(CardioRecordKind.HeaviestPack, session.ruckLoadKgResolved())
        return out
    }

    /** The best of each record across [entries], in group then kind order. */
    fun best(entries: List<DatedCardioSession>, excludeSessionIds: Set<String> = emptySet()): List<CardioRecord> {
        val best = LinkedHashMap<Pair<CardioRecordGroup, CardioRecordKind>, CardioRecord>()
        for (entry in entries) {
            if (entry.session.id in excludeSessionIds) continue
            for (record in recordsOf(entry.session, entry.logDate)) {
                val key = record.group to record.kind
                if (record.beats(best[key])) best[key] = record
            }
        }
        return best.values.sortedWith(
            compareBy<CardioRecord> { it.group.ordinal }.thenBy { kindOrder(it.group, it.kind) },
        )
    }

    private fun kindOrder(group: CardioRecordGroup, kind: CardioRecordKind): Int = when (kind) {
        is CardioRecordKind.Fastest -> group.efforts.indexOfFirst { it.second == kind.meters }
        CardioRecordKind.LongestDistance -> 100
        CardioRecordKind.LongestTime -> 101
        CardioRecordKind.MostClimbing -> 102
        CardioRecordKind.HeaviestPack -> 103
    }

    /**
     * The records [session] (logged on [date]) sets against everything else in [entries]. A first
     * of its kind counts only for distance efforts, so a first ever ride doesn't announce five
     * records at once.
     */
    fun newRecords(entries: List<DatedCardioSession>, session: CardioSession, date: LocalDate): List<CardioNewRecord> {
        val before = best(entries, excludeSessionIds = setOf(session.id)).associateBy { it.group to it.kind }
        val hasHistory = before.keys.any { it.first == CardioRecordGroup.of(session.activity) }
        return recordsOf(session, date).mapNotNull { record ->
            val previous = before[record.group to record.kind]
            when {
                previous != null && record.beats(previous) -> CardioNewRecord(record, previous)
                previous == null && hasHistory && record.kind is CardioRecordKind.Fastest -> CardioNewRecord(record, null)
                else -> null
            }
        }
    }
}

/** "24:31", "1.20 mi", "320 ft", "45 lb (20.4 kg)", in the athlete's units. */
fun CardioRecord.formatValue(distanceUnit: CardioDistanceUnit): String = when (kind) {
    is CardioRecordKind.Fastest, CardioRecordKind.LongestTime -> formatCardioElapsedClock(value.toInt())
    CardioRecordKind.LongestDistance -> formatCardioDistanceFromMeters(value, distanceUnit)
    CardioRecordKind.MostClimbing -> if (distanceUnit == CardioDistanceUnit.MILES) {
        "${(value * 3.28084).toInt()} ft"
    } else {
        "${value.toInt()} m"
    }
    CardioRecordKind.HeaviestPack -> formatCardioPackWeightFromKg(value)
}

/** Every logged session with its day, for records across the whole log. */
fun CardioLibraryState.allDatedSessions(): List<DatedCardioSession> =
    logs.flatMap { log ->
        val date = runCatching { LocalDate.parse(log.date) }.getOrNull() ?: return@flatMap emptyList()
        log.sessions.map { DatedCardioSession(date, it) }
    }
