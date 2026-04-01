package com.erv.app.programs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Weekly schedule programs: each [ProgramWeekDay] is ISO day-of-week 1 (Monday) .. 7 (Sunday).
 * Blocks can reference weight exercises/routines, cardio activities/routines, saved stretch routines,
 * built-in stretch catalog ids, sauna / cold plunge, rest, free-form custom text,
 * or [OTHER] with [ProgramDayBlock.checklistItems] for habits ERV does not auto-track (user checks off on the dashboard).
 */
@Serializable
enum class ProgramBlockKind {
    @SerialName("weight") WEIGHT,
    @SerialName("cardio") CARDIO,
    @SerialName("unified_routine") UNIFIED_ROUTINE,
    @SerialName("flex_training") FLEX_TRAINING,
    @SerialName("stretch_routine") STRETCH_ROUTINE,
    @SerialName("stretch_catalog") STRETCH_CATALOG,
    @SerialName("heat_cold") HEAT_COLD,
    @SerialName("rest") REST,
    @SerialName("custom") CUSTOM,
    /** Checklist habits (diet, reading, water, etc.); completion is logged per day on the dashboard. */
    @SerialName("other") OTHER
}

@Serializable
data class ProgramDayBlock(
    val id: String = UUID.randomUUID().toString(),
    val kind: ProgramBlockKind,
    /** Optional short label shown in lists (and for CUSTOM / REST). */
    val title: String? = null,
    val notes: String? = null,
    val weightExerciseIds: List<String> = emptyList(),
    val weightRoutineId: String? = null,
    /** [com.erv.app.cardio.CardioBuiltinActivity] name, e.g. RUN, BIKE */
    val cardioActivity: String? = null,
    val cardioRoutineId: String? = null,
    val unifiedRoutineId: String? = null,
    val stretchRoutineId: String? = null,
    val stretchCatalogIds: List<String> = emptyList(),
    /**
     * Hold seconds per stretch when launching a [ProgramBlockKind.STRETCH_CATALOG] block from the dashboard.
     * Ignored for [ProgramBlockKind.STRETCH_ROUTINE] (saved routine supplies hold time).
     */
    val stretchHoldSecondsPerStretch: Int? = null,
    /** SAUNA | COLD_PLUNGE */
    val heatColdMode: String? = null,
    val targetMinutes: Int? = null,
    /** For [ProgramBlockKind.OTHER]: lines the user checks off on the Launch Pad for the selected date. */
    val checklistItems: List<String> = emptyList()
)

@Serializable
data class ProgramWeekDay(
    val dayOfWeek: Int,
    val blocks: List<ProgramDayBlock> = emptyList()
)

@Serializable
data class FitnessProgram(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    /** e.g. coach name, "ChatGPT", "Template" */
    val sourceLabel: String? = null,
    val weeklySchedule: List<ProgramWeekDay> = emptyList(),
    val createdAtEpochSeconds: Long = System.currentTimeMillis() / 1000,
    val lastModifiedEpochSeconds: Long = System.currentTimeMillis() / 1000
)

@Serializable
data class ProgramCompletionMark(
    val done: Boolean = false,
    val updatedAtEpochSeconds: Long = 0L
)

@Serializable
data class ProgramsLibraryState(
    val programs: List<FitnessProgram> = emptyList(),
    val activeProgramId: String? = null,
    val strategy: ProgramStrategy = ProgramStrategy(),
    /** Whole-library snapshot timestamp for synced program master data and active selection. */
    val masterUpdatedAtEpochSeconds: Long = 0L,
    /**
     * Per-day completion state for program items. Checklist rows use [programChecklistCompletionKey];
     * non-checklist blocks may use [com.erv.app.programs.programBlockCompletionKey].
     * Stores both checked and unchecked writes so progress merges deterministically across devices.
     */
    val completionState: Map<String, ProgramCompletionMark> = emptyMap(),
    /**
     * Legacy pre-sync shape kept for local migration. New writes use [completionState].
     */
    val checklistCompletion: Map<String, Boolean> = emptyMap()
) {
    fun programById(id: String): FitnessProgram? = programs.firstOrNull { it.id == id }

    fun completionMark(key: String): ProgramCompletionMark? =
        completionState[key] ?: checklistCompletion[key]?.let { done ->
            ProgramCompletionMark(done = done, updatedAtEpochSeconds = 0L)
        }

    fun isCompletionDone(key: String): Boolean = completionMark(key)?.done == true
}

fun isoDayOfWeekLabel(day: Int): String = when (day) {
    1 -> "Monday"
    2 -> "Tuesday"
    3 -> "Wednesday"
    4 -> "Thursday"
    5 -> "Friday"
    6 -> "Saturday"
    7 -> "Sunday"
    else -> "Day $day"
}

/** One row per ISO day 1..7; duplicate [dayOfWeek] rows are merged by concatenating blocks. */
fun FitnessProgram.normalizedWeek(): List<ProgramWeekDay> {
    val merged = weeklySchedule
        .filter { it.dayOfWeek in 1..7 }
        .groupBy { it.dayOfWeek }
        .mapValues { (_, days) ->
            ProgramWeekDay(
                dayOfWeek = days.first().dayOfWeek,
                blocks = days.flatMap { it.blocks }
            )
        }
    return (1..7).map { d -> merged[d] ?: ProgramWeekDay(dayOfWeek = d) }
}

fun ProgramWeekDay.withBlockAppended(block: ProgramDayBlock): ProgramWeekDay =
    copy(blocks = blocks + block)

fun ProgramWeekDay.withBlockRemoved(blockId: String): ProgramWeekDay =
    copy(blocks = blocks.filterNot { it.id == blockId })

fun ProgramWeekDay.withBlockReplaced(block: ProgramDayBlock): ProgramWeekDay =
    copy(blocks = blocks.map { if (it.id == block.id) block else it })

fun FitnessProgram.withWeekDayUpdated(day: ProgramWeekDay): FitnessProgram {
    val rest = weeklySchedule.filterNot { it.dayOfWeek == day.dayOfWeek }
    val merged = (rest + day).sortedBy { it.dayOfWeek }
    return copy(weeklySchedule = merged, lastModifiedEpochSeconds = System.currentTimeMillis() / 1000)
}
