package com.erv.app.standards

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Fitness standards, read on the phone. The web companion owns them: built-ins ship in
 * `assets/standards/built_in_standards.json` (generated from `apps/web/web/src/lib/standardsLibrary.ts`),
 * custom standards arrive as kind 30078 `erv/standards/custom`, and logged test results as
 * `erv/standards/results`. Field names match `apps/web/web/src/lib/standards.ts`.
 *
 * Every target is in one canonical unit per measure: LIFT_MAX_BW a ratio of bodyweight,
 * LIFT_MAX_LOAD kg, MAX_REPS / REPS_AT_BW reps, LONGEST_HOLD / FASTEST seconds, MANUAL its own unit.
 */
object StandardMeasureKind {
    const val LIFT_MAX_BW = "LIFT_MAX_BW"
    const val LIFT_MAX_LOAD = "LIFT_MAX_LOAD"
    const val REPS_AT_BW = "REPS_AT_BW"
    const val MAX_REPS = "MAX_REPS"
    const val LONGEST_HOLD = "LONGEST_HOLD"
    const val FASTEST = "FASTEST"
    const val MANUAL = "MANUAL"
}

object StandardManualUnit {
    const val SECONDS = "SECONDS"
    const val REPS = "REPS"
    const val METERS = "METERS"
    const val CM_OVER_HEIGHT = "CM_OVER_HEIGHT"
}

enum class StandardTier(val label: String) {
    BASE("Base"),
    STRONG("Strong"),
    ELITE("Elite"),
}

@Serializable
data class StandardTierValues(
    @SerialName("BASE") val base: Double,
    @SerialName("STRONG") val strong: Double,
    @SerialName("ELITE") val elite: Double,
) {
    operator fun get(tier: StandardTier): Double = when (tier) {
        StandardTier.BASE -> base
        StandardTier.STRONG -> strong
        StandardTier.ELITE -> elite
    }
}

/** One flat shape for every measure kind; only the fields that kind uses are set. */
@Serializable
data class StandardMeasure(
    val kind: String,
    val reps: Int? = null,
    val meters: Double? = null,
    val cardioGroups: List<String> = emptyList(),
    val minPackKg: Double? = null,
    val unit: String? = null,
)

@Serializable
data class StandardVariant(
    val sex: String? = null,
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val tiers: StandardTierValues,
)

@Serializable
data class StandardEvent(
    val id: String,
    val name: String,
    val detail: String? = null,
    val measure: StandardMeasure,
    val exerciseIds: List<String> = emptyList(),
    val exerciseNamePattern: String? = null,
    val fallbackExerciseIds: List<String> = emptyList(),
    val lowerIsBetter: Boolean = false,
    val tiers: StandardTierValues,
    val variants: List<StandardVariant> = emptyList(),
)

@Serializable
data class StandardSource(val label: String, val url: String)

@Serializable
data class FitnessStandard(
    val id: String,
    val name: String,
    val description: String = "",
    val source: StandardSource? = null,
    val events: List<StandardEvent> = emptyList(),
)

/** A result the user logged on the web for one event; [value] is the raw measurement (kg, reps, s, m, cm). */
@Serializable
data class StoredStandardResult(
    val id: String,
    val standardId: String,
    val eventId: String,
    val value: Double,
    val date: String,
    val note: String? = null,
    val lastModifiedEpochSeconds: Long = 0L,
)

data class StandardResultsPayload(
    val results: List<StoredStandardResult> = emptyList(),
    val lastModifiedEpochSeconds: Long = 0L,
)

data class CustomStandardsPayload(
    val standards: List<FitnessStandard> = emptyList(),
    val lastModifiedEpochSeconds: Long = 0L,
)

const val STANDARD_RESULTS_D_TAG = "erv/standards/results"
const val CUSTOM_STANDARDS_D_TAG = "erv/standards/custom"
const val CUSTOM_STANDARD_ID_PREFIX = "custom-"

internal val standardsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun parseRoot(raw: String?): JsonObject? {
    if (raw.isNullOrBlank()) return null
    return try {
        standardsJson.parseToJsonElement(raw).asObject()
    } catch (_: Exception) {
        null
    }
}

private fun JsonObject.longField(name: String): Long =
    ((this[name] as? JsonPrimitive)?.longOrNull ?: (this[name] as? JsonPrimitive)?.doubleOrNull?.toLong() ?: 0L)
        .coerceAtLeast(0L)

private fun JsonObject.deletedMarkers(name: String): Map<String, Long> {
    val obj = this[name].asObject() ?: return emptyMap()
    return obj.mapNotNull { (id, at) ->
        val v = (at as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
        id to v.toLong()
    }.toMap()
}

/** Each entry on its own, so one bad entry does not drop the rest. */
private fun <T> JsonObject.decodeEach(name: String, decode: (JsonElement) -> T?): List<T> =
    (this[name] as? JsonArray).orEmpty().mapNotNull { el ->
        try {
            decode(el)
        } catch (_: Exception) {
            null
        }
    }

fun decodeStandardResults(raw: String?): StandardResultsPayload? {
    val root = parseRoot(raw) ?: return null
    val deleted = root.deletedMarkers("deletedResults")
    val results = root.decodeEach("results") { standardsJson.decodeFromJsonElement(StoredStandardResult.serializer(), it) }
        .filter { it.value > 0 && ISO_DATE.matches(it.date) && it.id.isNotBlank() }
        .filter { r -> deleted[r.id]?.let { r.lastModifiedEpochSeconds > it } ?: true }
        .sortedWith(compareByDescending<StoredStandardResult> { it.date }.thenByDescending { it.lastModifiedEpochSeconds })
    return StandardResultsPayload(results, root.longField("lastModifiedEpochSeconds"))
}

private fun FitnessStandard.isUsable(): Boolean = id.isNotBlank() && name.isNotBlank() && events.isNotEmpty()

fun decodeCustomStandards(raw: String?): CustomStandardsPayload? {
    val root = parseRoot(raw) ?: return null
    val deleted = root.deletedMarkers("deletedStandards")
    val standards = root.decodeEach("standards") { el ->
        val modified = el.asObject()?.longField("lastModifiedEpochSeconds") ?: 0L
        val s = standardsJson.decodeFromJsonElement(FitnessStandard.serializer(), el)
        s.takeIf { deleted[s.id]?.let { at -> modified > at } ?: true }
    }.filter { it.id.startsWith(CUSTOM_STANDARD_ID_PREFIX) && it.isUsable() }
    return CustomStandardsPayload(standards, root.longField("lastModifiedEpochSeconds"))
}

/** The bundled built-in standards file. */
fun decodeBuiltInStandards(raw: String): List<FitnessStandard> {
    val root = try {
        standardsJson.parseToJsonElement(raw)
    } catch (_: Exception) {
        return emptyList()
    }
    return (root as? JsonArray).orEmpty().mapNotNull { el ->
        try {
            standardsJson.decodeFromJsonElement(FitnessStandard.serializer(), el)
        } catch (_: Exception) {
            null
        }
    }.filter { it.isUsable() }
}

/** Replacement payloads a data delete publishes so relays stop holding results and custom standards. */
fun emptyStandardResultsJson(nowEpochSeconds: Long): String =
    """{"ervStandardResultsVersion":1,"results":[],"deletedResults":{},"lastModifiedEpochSeconds":$nowEpochSeconds}"""

fun emptyCustomStandardsJson(nowEpochSeconds: Long): String =
    """{"ervCustomStandardsVersion":1,"standards":[],"deletedStandards":{},"lastModifiedEpochSeconds":$nowEpochSeconds}"""
