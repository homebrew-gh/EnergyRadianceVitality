package com.erv.app.data

/** Catalog of goals the user can opt into; ids are stable for DataStore and future metric wiring. */
data class UserGoalOption(
    val id: String,
    val title: String,
    val description: String,
)

val AllUserGoalOptions: List<UserGoalOption> = listOf(
    UserGoalOption(
        id = "supplements_adherence",
        title = "Supplement routines",
        description = "Consistency with planned supplement timing",
    ),
    UserGoalOption(
        id = "light_therapy_time",
        title = "Light therapy",
        description = "Weekly red / infrared session time",
    ),
    UserGoalOption(
        id = "cardio_activity",
        title = "Cardio",
        description = "Weekly runs, rides, or intervals",
    ),
    UserGoalOption(
        id = "weight_training",
        title = "Strength training",
        description = "Weekly resistance sessions",
    ),
    UserGoalOption(
        id = "stretching_sessions",
        title = "Stretching",
        description = "Weekly guided or logged stretch sessions",
    ),
)

private val validGoalIds: Set<String> = AllUserGoalOptions.map { it.id }.toSet()

fun parseGoalIdsFromStorage(raw: String?): Set<String> =
    raw.orEmpty()
        .split(',')
        .map { it.trim() }
        .filter { it in validGoalIds }
        .toSet()

fun encodeGoalIdsForStorage(ids: Collection<String>): String =
    ids.filter { it in validGoalIds }.toSortedSet().joinToString(",")

fun goalOptionForId(id: String): UserGoalOption? =
    AllUserGoalOptions.firstOrNull { it.id == id }
