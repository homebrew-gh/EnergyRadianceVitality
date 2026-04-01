package com.erv.app.programs

import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.heatcold.HeatColdMode
import com.erv.app.stretching.StretchCatalogEntry
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.unifiedroutines.UnifiedRoutineLibraryState
import com.erv.app.weighttraining.WeightLibraryState

fun ProgramDayBlock.summaryLine(
    weightState: WeightLibraryState,
    stretchState: StretchLibraryState,
    stretchCatalog: List<StretchCatalogEntry>,
    cardioState: CardioLibraryState,
    unifiedRoutineState: UnifiedRoutineLibraryState,
): String {
    val titlePart = title?.let { "$it — " }.orEmpty()
    return titlePart + when (kind) {
        ProgramBlockKind.WEIGHT -> {
            val names = weightExerciseIds.mapNotNull { id -> weightState.exerciseById(id)?.name }
            val routine = weightRoutineId?.let { rid ->
                weightState.routines.firstOrNull { it.id == rid }?.name?.let { n -> "Routine: $n" }
            }
            when {
                names.isNotEmpty() && routine != null -> names.joinToString(", ") + " · $routine"
                names.isNotEmpty() -> names.joinToString(", ")
                routine != null -> routine
                else -> "Weight training (choose exercises)"
            }
        }
        ProgramBlockKind.FLEX_TRAINING -> {
            val mins = targetMinutes?.let { m -> " · ~${m} min" }.orEmpty()
            "Cardio or weight training$mins"
        }
        ProgramBlockKind.CARDIO -> {
            val act = cardioActivity?.let { runCatching { CardioBuiltinActivity.valueOf(it) }.getOrNull() }
            val actLabel = act?.name?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.titlecase() }
                ?: cardioActivity ?: "Cardio"
            val routine = cardioRoutineId?.let { rid ->
                cardioState.routines.firstOrNull { it.id == rid }?.name?.let { n -> " · $n" }.orEmpty()
            }
            val mins = targetMinutes?.let { m -> " · ~${m} min" }.orEmpty()
            "$actLabel$routine$mins"
        }
        ProgramBlockKind.UNIFIED_ROUTINE -> {
            val routineName = unifiedRoutineId?.let { id ->
                unifiedRoutineState.routines.firstOrNull { it.id == id }?.name
            } ?: "Unified workout"
            routineName
        }
        ProgramBlockKind.STRETCH_ROUTINE -> {
            val name = stretchRoutineId?.let { rid ->
                stretchState.routines.firstOrNull { it.id == rid }?.name
            } ?: "Stretch routine"
            val mins = targetMinutes?.let { m -> " · ~${m} min" }.orEmpty()
            "$name$mins"
        }
        ProgramBlockKind.STRETCH_CATALOG -> {
            val byId = stretchCatalog.associateBy { it.id }
            val names = stretchCatalogIds.mapNotNull { byId[it]?.name }
            val mins = targetMinutes?.let { m -> " · ~${m} min" }.orEmpty()
            val hold = stretchHoldSecondsPerStretch?.let { h -> " · ${h}s hold" }.orEmpty()
            if (names.isEmpty()) "Stretch (catalog poses)$mins$hold"
            else names.take(3).joinToString(", ") + (if (names.size > 3) "…" else "") + mins + hold
        }
        ProgramBlockKind.HEAT_COLD -> {
            val mode = heatColdMode?.let { runCatching { HeatColdMode.valueOf(it) }.getOrNull() }
            val label = when (mode) {
                HeatColdMode.SAUNA -> "Sauna"
                HeatColdMode.COLD_PLUNGE -> "Cold plunge"
                null -> heatColdMode ?: "Hot / cold"
            }
            val mins = targetMinutes?.let { m -> " · ~${m} min" }.orEmpty()
            "$label$mins"
        }
        ProgramBlockKind.REST -> notes?.takeIf { it.isNotBlank() } ?: "Rest"
        ProgramBlockKind.CUSTOM -> notes?.takeIf { it.isNotBlank() } ?: title ?: "Note"
        ProgramBlockKind.OTHER -> {
            val lines = checklistItems.filter { it.isNotBlank() }
            when {
                lines.isNotEmpty() -> lines.take(4).joinToString(" · ") + if (lines.size > 4) "…" else ""
                notes?.isNotBlank() == true -> notes
                title?.isNotBlank() == true -> title
                else -> "Habits / checklist"
            }
        }
    }
}
