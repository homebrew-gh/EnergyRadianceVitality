package com.erv.app.weighttraining

data class WeightExercisePackDefinition(
    val id: String,
    val title: String,
    val description: String,
)

const val IRON_NECK_EXERCISE_PACK_ID: String = "iron-neck"
const val FREAK_ATHLETE_HYPER_PRO_EXERCISE_PACK_ID: String = "freak-athlete-hyper-pro"

private val specialtyExercisePacks: List<WeightExercisePackDefinition> = listOf(
    WeightExercisePackDefinition(
        id = IRON_NECK_EXERCISE_PACK_ID,
        title = "Iron Neck",
        description = "Unlock neck-specific drills that rely on Iron Neck resistance work."
    ),
    WeightExercisePackDefinition(
        id = FREAK_ATHLETE_HYPER_PRO_EXERCISE_PACK_ID,
        title = "Freak Athlete Hyper Pro",
        description = "Adds reverse hyper, GHD, Nordic, and back-extension style movements."
    ),
)

fun availableWeightExercisePacks(): List<WeightExercisePackDefinition> = specialtyExercisePacks

fun WeightExercise.isVisibleForEnabledPacks(enabledPackIds: Set<String>): Boolean =
    exercisePackId == null || exercisePackId in enabledPackIds

fun filterWeightExercisesForEnabledPacks(
    exercises: List<WeightExercise>,
    enabledPackIds: Set<String>,
): List<WeightExercise> = exercises.filter { it.isVisibleForEnabledPacks(enabledPackIds) }

fun weightExercisePackExerciseCount(packId: String): Int =
    defaultCatalogExercises().count { it.exercisePackId == packId }
