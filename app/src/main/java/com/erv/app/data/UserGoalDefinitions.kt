package com.erv.app.data

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UserGoalMetricType {
    @SerialName("supplement_days") SUPPLEMENT_DAYS,
    @SerialName("program_adherence_days") PROGRAM_ADHERENCE_DAYS,
    @SerialName("cardio_sessions") CARDIO_SESSIONS,
    @SerialName("strength_workouts") STRENGTH_WORKOUTS,
    @SerialName("stretch_sessions") STRETCH_SESSIONS,
    @SerialName("light_minutes") LIGHT_MINUTES,
}

@Serializable
enum class CardioGoalFilter {
    @SerialName("any") ANY,
    @SerialName("cycling") CYCLING,
}

@Serializable
data class UserGoalDefinition(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val metricType: UserGoalMetricType,
    val target: Int,
    val cardioFilter: CardioGoalFilter = CardioGoalFilter.ANY,
)

data class GoalTemplateOption(
    val id: String,
    val title: String,
    val description: String,
    val defaultTarget: Int,
    val metricType: UserGoalMetricType,
    val cardioFilter: CardioGoalFilter = CardioGoalFilter.ANY,
)

val GoalTemplateOptions: List<GoalTemplateOption> = listOf(
    GoalTemplateOption(
        id = "supplements_daily",
        title = "Take supplements daily",
        description = "Count days this week with supplement activity logged.",
        defaultTarget = 7,
        metricType = UserGoalMetricType.SUPPLEMENT_DAYS,
    ),
    GoalTemplateOption(
        id = "follow_program",
        title = "Follow my program",
        description = "Count days where every scheduled item in your active program is completed.",
        defaultTarget = 3,
        metricType = UserGoalMetricType.PROGRAM_ADHERENCE_DAYS,
    ),
    GoalTemplateOption(
        id = "cardio_sessions",
        title = "Complete cardio sessions",
        description = "Count all cardio sessions logged this week.",
        defaultTarget = 3,
        metricType = UserGoalMetricType.CARDIO_SESSIONS,
    ),
    GoalTemplateOption(
        id = "cycling_sessions",
        title = "Complete cycling sessions",
        description = "Count cycling sessions logged this week.",
        defaultTarget = 3,
        metricType = UserGoalMetricType.CARDIO_SESSIONS,
        cardioFilter = CardioGoalFilter.CYCLING,
    ),
    GoalTemplateOption(
        id = "strength_sessions",
        title = "Complete strength sessions",
        description = "Count weight-training workouts logged this week.",
        defaultTarget = 2,
        metricType = UserGoalMetricType.STRENGTH_WORKOUTS,
    ),
    GoalTemplateOption(
        id = "stretch_sessions",
        title = "Complete stretch sessions",
        description = "Count stretch sessions logged this week.",
        defaultTarget = 3,
        metricType = UserGoalMetricType.STRETCH_SESSIONS,
    ),
    GoalTemplateOption(
        id = "light_minutes",
        title = "Hit weekly light therapy time",
        description = "Count total light therapy minutes logged this week.",
        defaultTarget = 60,
        metricType = UserGoalMetricType.LIGHT_MINUTES,
    ),
)

private val legacyGoalIdTemplates: Map<String, GoalTemplateOption> = mapOf(
    "supplements_adherence" to GoalTemplateOption(
        id = "legacy_supplements_adherence",
        title = "Supplement routines",
        description = "Count days this week with supplement activity logged.",
        defaultTarget = 4,
        metricType = UserGoalMetricType.SUPPLEMENT_DAYS,
    ),
    "light_therapy_time" to GoalTemplateOption(
        id = "legacy_light_therapy_time",
        title = "Light therapy",
        description = "Count total light therapy minutes logged this week.",
        defaultTarget = 60,
        metricType = UserGoalMetricType.LIGHT_MINUTES,
    ),
    "cardio_activity" to GoalTemplateOption(
        id = "legacy_cardio_activity",
        title = "Cardio",
        description = "Count cardio sessions logged this week.",
        defaultTarget = 2,
        metricType = UserGoalMetricType.CARDIO_SESSIONS,
    ),
    "weight_training" to GoalTemplateOption(
        id = "legacy_weight_training",
        title = "Strength training",
        description = "Count weight-training workouts logged this week.",
        defaultTarget = 2,
        metricType = UserGoalMetricType.STRENGTH_WORKOUTS,
    ),
    "stretching_sessions" to GoalTemplateOption(
        id = "legacy_stretching_sessions",
        title = "Stretching",
        description = "Count stretch sessions logged this week.",
        defaultTarget = 3,
        metricType = UserGoalMetricType.STRETCH_SESSIONS,
    ),
)

fun parseGoalIdsFromStorage(raw: String?): Set<String> =
    raw.orEmpty()
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() && legacyGoalIdTemplates.containsKey(it) }
        .toSet()

fun encodeGoalIdsForStorage(ids: Collection<String>): String =
    ids.filter { legacyGoalIdTemplates.containsKey(it) }.toSortedSet().joinToString(",")

fun goalTemplateOptionForId(id: String): GoalTemplateOption? =
    GoalTemplateOptions.firstOrNull { it.id == id }

fun createGoalFromTemplate(
    template: GoalTemplateOption,
    title: String = template.title,
    target: Int = template.defaultTarget,
    goalId: String = UUID.randomUUID().toString(),
): UserGoalDefinition =
    UserGoalDefinition(
        id = goalId,
        title = title.trim().ifEmpty { template.title },
        metricType = template.metricType,
        target = target.coerceAtLeast(1),
        cardioFilter = template.cardioFilter,
    )

fun migrateLegacyGoalIds(ids: Collection<String>): List<UserGoalDefinition> =
    ids.mapNotNull { id ->
        legacyGoalIdTemplates[id]?.let { template ->
            createGoalFromTemplate(template = template)
        }
    }

fun UserGoalDefinition.metricSummaryLabel(): String = when (metricType) {
    UserGoalMetricType.SUPPLEMENT_DAYS -> "supplement days"
    UserGoalMetricType.PROGRAM_ADHERENCE_DAYS -> "program days"
    UserGoalMetricType.CARDIO_SESSIONS ->
        if (cardioFilter == CardioGoalFilter.CYCLING) "cycling sessions" else "cardio sessions"
    UserGoalMetricType.STRENGTH_WORKOUTS -> "strength workouts"
    UserGoalMetricType.STRETCH_SESSIONS -> "stretch sessions"
    UserGoalMetricType.LIGHT_MINUTES -> "light minutes"
}
