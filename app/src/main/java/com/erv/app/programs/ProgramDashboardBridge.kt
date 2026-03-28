package com.erv.app.programs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ProgramDashboardStretchLaunch(
    val routineId: String? = null,
    val stretchIds: List<String> = emptyList(),
    val title: String? = null,
    val holdSecondsPerStretch: Int = 30
)

@Serializable
data class ProgramDashboardHeatColdLaunch(
    val mode: String,
    val durationSeconds: Int
)

@Serializable
data class ProgramDashboardUnifiedRoutineLaunch(
    val routineId: String? = null,
    val createNew: Boolean = false,
)

private val bridgeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun encodeStretchLaunch(payload: ProgramDashboardStretchLaunch): String =
    bridgeJson.encodeToString(ProgramDashboardStretchLaunch.serializer(), payload)

fun decodeStretchLaunch(raw: String): ProgramDashboardStretchLaunch? =
    runCatching { bridgeJson.decodeFromString(ProgramDashboardStretchLaunch.serializer(), raw) }.getOrNull()

fun encodeHeatColdLaunch(payload: ProgramDashboardHeatColdLaunch): String =
    bridgeJson.encodeToString(ProgramDashboardHeatColdLaunch.serializer(), payload)

fun decodeHeatColdLaunch(raw: String): ProgramDashboardHeatColdLaunch? =
    runCatching { bridgeJson.decodeFromString(ProgramDashboardHeatColdLaunch.serializer(), raw) }.getOrNull()

fun encodeUnifiedRoutineLaunch(payload: ProgramDashboardUnifiedRoutineLaunch): String =
    bridgeJson.encodeToString(ProgramDashboardUnifiedRoutineLaunch.serializer(), payload)

fun decodeUnifiedRoutineLaunch(raw: String): ProgramDashboardUnifiedRoutineLaunch? =
    runCatching { bridgeJson.decodeFromString(ProgramDashboardUnifiedRoutineLaunch.serializer(), raw) }.getOrNull()
