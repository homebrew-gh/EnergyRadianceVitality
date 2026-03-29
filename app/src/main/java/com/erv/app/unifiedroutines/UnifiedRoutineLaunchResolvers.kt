package com.erv.app.unifiedroutines

import com.erv.app.cardio.CardioActiveTimerSession
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioTimerSessionDraft
import com.erv.app.programs.ProgramDashboardStretchLaunch
import com.erv.app.programs.ProgramBlockKind
import com.erv.app.programs.ProgramDayBlock
import com.erv.app.programs.cardioTimerSessionForProgramBlock
import com.erv.app.programs.weightRoutineForProgramBlock
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRoutine

fun UnifiedRoutineBlock.resolveWeightRoutine(weightState: WeightLibraryState): WeightRoutine? =
    weightRoutineForProgramBlock(
        ProgramDayBlock(
            kind = ProgramBlockKind.WEIGHT,
            title = title,
            notes = notes,
            weightExerciseIds = weightExerciseIds,
            weightRoutineId = weightRoutineId
        ),
        weightState
    )

fun UnifiedRoutineBlock.resolveCardioLaunch(cardioState: CardioLibraryState): CardioActiveTimerSession? {
    cardioInlineQuickLaunch?.let { inlineQuickLaunch ->
        return CardioActiveTimerSession.Single(CardioTimerSessionDraft.fromQuickLaunch(inlineQuickLaunch))
    }
    cardioQuickLaunchId?.let { quickLaunchId ->
        val quickLaunch = cardioState.quickLaunches.firstOrNull { it.id == quickLaunchId }
        if (quickLaunch != null) {
            return CardioActiveTimerSession.Single(CardioTimerSessionDraft.fromQuickLaunch(quickLaunch))
        }
    }
    return cardioTimerSessionForProgramBlock(
        ProgramDayBlock(
            kind = ProgramBlockKind.CARDIO,
            title = title,
            notes = notes,
            cardioActivity = cardioActivity,
            cardioRoutineId = cardioRoutineId,
            targetMinutes = targetMinutes
        ),
        cardioState
    )
}

fun UnifiedRoutineBlock.resolveStretchLaunch(): ProgramDashboardStretchLaunch? =
    when {
        !stretchRoutineId.isNullOrBlank() -> ProgramDashboardStretchLaunch(routineId = stretchRoutineId)
        stretchCatalogIds.isNotEmpty() -> ProgramDashboardStretchLaunch(
            stretchIds = stretchCatalogIds,
            title = title,
            holdSecondsPerStretch = stretchHoldSecondsPerStretch.coerceIn(5, 300)
        )
        else -> null
    }
