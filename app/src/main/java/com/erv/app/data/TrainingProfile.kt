package com.erv.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Plaintext payload encrypted into kind 30078, `d` tag `erv/training-profile` (replaceable).
 *
 * Edited primarily on the Start9 web companion; Android syncs and shows a read-only summary.
 */
@Serializable
enum class TrainingPrimaryGoal {
    @SerialName("general_fitness") GENERAL_FITNESS,
    @SerialName("strength") STRENGTH,
    @SerialName("hypertrophy") HYPERTROPHY,
    @SerialName("endurance") ENDURANCE,
    @SerialName("longevity") LONGEVITY,
    @SerialName("sport") SPORT,
}

@Serializable
enum class TrainingExperienceLevel {
    @SerialName("beginner") BEGINNER,
    @SerialName("intermediate") INTERMEDIATE,
    @SerialName("advanced") ADVANCED,
}

@Serializable
enum class TrainingProgressionStyle {
    @SerialName("conservative") CONSERVATIVE,
    @SerialName("moderate") MODERATE,
    @SerialName("aggressive") AGGRESSIVE,
}

@Serializable
enum class TrainingCardioBias {
    @SerialName("none") NONE,
    @SerialName("zone2_base") ZONE2_BASE,
    @SerialName("intervals") INTERVALS,
    @SerialName("mixed") MIXED,
}

@Serializable
data class TrainingProfileNostrPayload(
    val profileVersion: Int = 2,
    val primaryGoal: TrainingPrimaryGoal? = null,
    val experienceLevel: TrainingExperienceLevel? = null,
    val typicalSessionMinutes: Int? = null,
    val typicalTrainingDaysPerWeek: Int? = null,
    val stylePresetIds: List<String> = emptyList(),
    val styleNotes: String? = null,
    val avoidMovementPatterns: List<String> = emptyList(),
    val customAvoidNotes: String? = null,
    val progressionStyle: TrainingProgressionStyle? = null,
    val cardioBias: TrainingCardioBias? = null,
    val ageYears: Int? = null,
    val heartRateMaxBpm: Int? = null,
    val heartRateRestingBpm: Int? = null,
    /** `percent_max_hr` or `karvonen_hrr` */
    val heartRateZoneMethod: String? = null,
    val lastModifiedEpochSeconds: Long = 0L,
)

fun TrainingPrimaryGoal.displayLabel(): String =
    when (this) {
        TrainingPrimaryGoal.GENERAL_FITNESS -> "General fitness"
        TrainingPrimaryGoal.STRENGTH -> "Strength"
        TrainingPrimaryGoal.HYPERTROPHY -> "Hypertrophy"
        TrainingPrimaryGoal.ENDURANCE -> "Endurance"
        TrainingPrimaryGoal.LONGEVITY -> "Longevity"
        TrainingPrimaryGoal.SPORT -> "Sport-specific"
    }

fun TrainingExperienceLevel.displayLabel(): String =
    when (this) {
        TrainingExperienceLevel.BEGINNER -> "Beginner"
        TrainingExperienceLevel.INTERMEDIATE -> "Intermediate"
        TrainingExperienceLevel.ADVANCED -> "Advanced"
    }

fun trainingStylePresetLabel(id: String): String =
    when (id) {
        "longevity_recovery", "longevity_blueprint" -> "Longevity & Recovery"
        "joint_durability", "kot_durable" -> "Joint Durability / ATG"
        "hypertrophy_bodybuilding", "hypertrophy" -> "Hypertrophy / Bodybuilding"
        "strength_powerlifting", "powerlifting" -> "Strength / Powerlifting"
        "zone2_endurance", "zone2_minimal" -> "Zone 2 / Aerobic Base"
        "hiit_conditioning" -> "HIIT / Conditioning"
        "general_athletic" -> "General Athletic Performance"
        "mobility_movement" -> "Mobility & Movement Quality"
        "calisthenics_minimalist" -> "Calisthenics / Minimal Equipment"
        else -> id.replace('_', ' ').replaceFirstChar { it.titlecase() }
    }

fun avoidMovementPatternLabel(id: String): String =
    when (id) {
        "heavy_overhead_press" -> "Heavy overhead press"
        "deep_knee_flexion" -> "Deep knee flexion under load"
        "spinal_axial_load" -> "Heavy spinal axial load"
        "jumping_plyometrics" -> "Jumping / plyometrics"
        "hanging_from_bar" -> "Hanging from bar"
        "high_impact_cardio" -> "High-impact cardio"
        else -> id.replace('_', ' ').replaceFirstChar { it.titlecase() }
    }

fun TrainingProfileNostrPayload.isBlank(): Boolean {
    if (primaryGoal != null) return false
    if (experienceLevel != null) return false
    if (typicalSessionMinutes != null) return false
    if (typicalTrainingDaysPerWeek != null) return false
    if (stylePresetIds.isNotEmpty()) return false
    if (!styleNotes.isNullOrBlank()) return false
    if (avoidMovementPatterns.isNotEmpty()) return false
    if (!customAvoidNotes.isNullOrBlank()) return false
    if (progressionStyle != null) return false
    if (cardioBias != null) return false
    if (ageYears != null) return false
    if (heartRateMaxBpm != null) return false
    if (heartRateRestingBpm != null) return false
    if (!heartRateZoneMethod.isNullOrBlank()) return false
    return true
}

fun TrainingProfileNostrPayload.hasSummaryContent(): Boolean = !isBlank()

private val trainingProfileJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

fun encodeTrainingProfile(payload: TrainingProfileNostrPayload): String =
    trainingProfileJson.encodeToString(TrainingProfileNostrPayload.serializer(), payload)

fun decodeTrainingProfile(raw: String?): TrainingProfileNostrPayload {
    if (raw.isNullOrBlank()) return TrainingProfileNostrPayload()
    return try {
        trainingProfileJson.decodeFromString(TrainingProfileNostrPayload.serializer(), raw)
    } catch (_: Exception) {
        TrainingProfileNostrPayload()
    }
}
