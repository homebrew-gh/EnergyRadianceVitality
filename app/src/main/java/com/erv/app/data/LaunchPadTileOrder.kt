package com.erv.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
enum class LaunchPadTileId {
    PROGRAMS,
    WORKOUT_LAUNCHER,
    STRETCHING,
    CARDIO,
    WEIGHT_TRAINING,
    HOT_COLD,
    BODY_TRACKER,
    SUPPLEMENTS,
    LIGHT_THERAPY,
}

val defaultLaunchPadTileOrder: List<LaunchPadTileId> = listOf(
    LaunchPadTileId.PROGRAMS,
    LaunchPadTileId.WORKOUT_LAUNCHER,
    LaunchPadTileId.STRETCHING,
    LaunchPadTileId.CARDIO,
    LaunchPadTileId.WEIGHT_TRAINING,
    LaunchPadTileId.HOT_COLD,
    LaunchPadTileId.BODY_TRACKER,
    LaunchPadTileId.SUPPLEMENTS,
    LaunchPadTileId.LIGHT_THERAPY,
)

private val launchPadTileOrderJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

fun normalizeLaunchPadTileOrder(items: List<LaunchPadTileId>): List<LaunchPadTileId> {
    val distinctKnown = items.filter { it in defaultLaunchPadTileOrder }.distinct()
    return distinctKnown + defaultLaunchPadTileOrder.filterNot { it in distinctKnown }
}

fun resolveVisibleLaunchPadTileOrder(
    storedOrder: List<LaunchPadTileId>,
    visibleTileIds: Set<LaunchPadTileId>,
): List<LaunchPadTileId> =
    normalizeLaunchPadTileOrder(storedOrder).filter { it in visibleTileIds }

fun mergeVisibleLaunchPadTileOrder(
    storedOrder: List<LaunchPadTileId>,
    visibleOrder: List<LaunchPadTileId>,
): List<LaunchPadTileId> {
    val normalizedStored = normalizeLaunchPadTileOrder(storedOrder)
    val normalizedVisible = visibleOrder.filter { it in defaultLaunchPadTileOrder }.distinct()
    val visibleSlots = normalizedStored.mapIndexedNotNull { index, tileId ->
        if (tileId in normalizedVisible.toSet()) index else null
    }
    if (visibleSlots.size != normalizedVisible.size) {
        return normalizedStored
    }
    val merged = normalizedStored.toMutableList()
    visibleSlots.forEachIndexed { index, slot ->
        merged[slot] = normalizedVisible[index]
    }
    return merged
}

fun encodeLaunchPadTileOrder(items: List<LaunchPadTileId>): String =
    launchPadTileOrderJson.encodeToString(
        ListSerializer(LaunchPadTileId.serializer()),
        normalizeLaunchPadTileOrder(items),
    )

fun decodeLaunchPadTileOrder(raw: String?): List<LaunchPadTileId> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        normalizeLaunchPadTileOrder(
            launchPadTileOrderJson.decodeFromString(
                ListSerializer(LaunchPadTileId.serializer()),
                raw,
            )
        )
    } catch (_: Exception) {
        emptyList()
    }
}
