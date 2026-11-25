package com.erv.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Latest AI coach review, published by the web companion as kind 30078 with `d` tag
 * **erv/coach/review** (replaceable). Android only reads it. Field names match
 * `apps/web/web/src/lib/coachReviewSync.ts`; unknown fields are ignored.
 */
@Serializable
data class CoachReviewSyncPayload(
    val ervCoachReviewVersion: Int = 1,
    val generatedAtEpochSeconds: Long = 0L,
    val periodLabel: String = "",
    val periodStart: String? = null,
    val periodEnd: String = "",
    val weightUnit: String = "lb",
    val model: String = "",
    val promptVersion: String = "",
    val review: CoachReview = CoachReview(),
    val liftTargets: List<CoachLiftTarget> = emptyList(),
)

@Serializable
data class CoachReview(
    val headline: String = "",
    val status: CoachStatus = CoachStatus(),
    val lifts: List<CoachLift> = emptyList(),
    val nextWeek: List<CoachAction> = emptyList(),
    val wins: List<String> = emptyList(),
    val watchOuts: List<CoachWatchOut> = emptyList(),
    val bodyWeight: String? = null,
    val confidence: String = "medium",
    val missingData: String? = null,
)

@Serializable
data class CoachStatus(
    val consistency: String = "unknown",
    val progress: String = "unknown",
    val recovery: String = "unknown",
)

@Serializable
data class CoachLift(
    val name: String = "",
    val trend: String = "flat",
    val next: String = "",
    val why: String = "",
)

@Serializable
data class CoachAction(
    val action: String = "",
    val why: String = "",
)

@Serializable
data class CoachWatchOut(
    val text: String = "",
    val evidence: String = "",
)

@Serializable
data class CoachLiftTarget(
    val exerciseId: String = "",
    val name: String = "",
    val trend: String = "flat",
    val next: String = "",
    val why: String = "",
)

/** A review older than this is shown as stale and stops adding coach lines to workouts. */
const val COACH_REVIEW_STALE_DAYS = 10L

private val coachReviewJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun decodeCoachReview(raw: String?): CoachReviewSyncPayload? {
    if (raw.isNullOrBlank()) return null
    return try {
        coachReviewJson.decodeFromString(CoachReviewSyncPayload.serializer(), raw)
            .takeIf { it.review.headline.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

fun encodeCoachReview(payload: CoachReviewSyncPayload): String =
    coachReviewJson.encodeToString(CoachReviewSyncPayload.serializer(), payload)

fun CoachReviewSyncPayload.ageDays(nowEpochSeconds: Long): Long =
    ((nowEpochSeconds - generatedAtEpochSeconds).coerceAtLeast(0L)) / 86_400L

fun CoachReviewSyncPayload.isStale(nowEpochSeconds: Long): Boolean =
    ageDays(nowEpochSeconds) > COACH_REVIEW_STALE_DAYS

/** "Coach: 185 × 8, 8, 8 lb" for [exerciseId], or null when the review has no target for it. */
fun CoachReviewSyncPayload.coachLineFor(exerciseId: String, nowEpochSeconds: Long): String? {
    if (isStale(nowEpochSeconds)) return null
    val target = liftTargets.firstOrNull { it.exerciseId == exerciseId } ?: return null
    val next = target.next.trim().ifEmpty { return null }
    val unit = weightUnit.trim()
    val withUnit = if (unit.isEmpty() || next.contains(unit, ignoreCase = true) || !next.any { it.isDigit() }) {
        next
    } else {
        "$next $unit"
    }
    return "Coach: $withUnit"
}

/** Plain label for a status value: "on_track" reads "On track". */
fun coachStatusLabel(value: String): String = when (value) {
    "on_track" -> "On track"
    "ok" -> "OK"
    else -> value.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
