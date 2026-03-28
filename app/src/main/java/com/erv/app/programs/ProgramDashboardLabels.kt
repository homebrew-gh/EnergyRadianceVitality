package com.erv.app.programs

import com.erv.app.cardio.CardioBuiltinActivity

/** Short names for Launch Pad list (duplicates get numeric suffixes). */
fun baseLaunchLabel(block: ProgramDayBlock): String = when (block.kind) {
    ProgramBlockKind.WEIGHT -> "Workout"
    ProgramBlockKind.UNIFIED_ROUTINE -> block.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Routine"
    ProgramBlockKind.FLEX_TRAINING -> block.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Workout"
    ProgramBlockKind.CARDIO -> cardioActivityLaunchLabel(block.cardioActivity)
    ProgramBlockKind.STRETCH_ROUTINE, ProgramBlockKind.STRETCH_CATALOG -> "Stretch"
    ProgramBlockKind.HEAT_COLD -> when (block.heatColdMode) {
        "COLD_PLUNGE" -> "Cold plunge"
        "SAUNA" -> "Sauna"
        else -> "Hot / cold"
    }
    ProgramBlockKind.REST -> "Rest"
    ProgramBlockKind.CUSTOM -> block.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Activity"
    ProgramBlockKind.OTHER -> block.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Habits"
}

private fun cardioActivityLaunchLabel(raw: String?): String {
    val act = raw?.let { runCatching { CardioBuiltinActivity.valueOf(it) }.getOrNull() } ?: return "Cardio"
    return when (act) {
        CardioBuiltinActivity.RUN, CardioBuiltinActivity.SPRINT -> "Run"
        CardioBuiltinActivity.WALK, CardioBuiltinActivity.HIKE, CardioBuiltinActivity.RUCK -> "Walk"
        CardioBuiltinActivity.BIKE, CardioBuiltinActivity.STATIONARY_BIKE -> "Bike"
        CardioBuiltinActivity.SWIM -> "Swim"
        CardioBuiltinActivity.ROWING -> "Row"
        CardioBuiltinActivity.ELLIPTICAL -> "Elliptical"
        CardioBuiltinActivity.JUMP_ROPE -> "Jump rope"
        else -> act.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
    }
}

/**
 * One display label per block; when several blocks share the same [baseLaunchLabel], yields
 * e.g. Workout1, Workout2 or Run1, Run2.
 */
fun launchPadLabelsForBlocks(blocks: List<ProgramDayBlock>): List<String> {
    if (blocks.isEmpty()) return emptyList()
    val bases = blocks.map { baseLaunchLabel(it) }
    val counts = bases.groupingBy { it }.eachCount()
    val nextIndex = mutableMapOf<String, Int>()
    return bases.map { base ->
        val total = counts[base] ?: 1
        if (total <= 1) base
        else {
            val i = (nextIndex[base] ?: 0) + 1
            nextIndex[base] = i
            "$base$i"
        }
    }
}

fun programBlocksForCalendarDay(program: FitnessProgram?, dayOfWeekIso: Int): List<ProgramDayBlock> {
    if (program == null || dayOfWeekIso !in 1..7) return emptyList()
    return program.normalizedWeek().firstOrNull { it.dayOfWeek == dayOfWeekIso }?.blocks.orEmpty()
}
