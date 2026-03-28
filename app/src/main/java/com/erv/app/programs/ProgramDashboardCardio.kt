package com.erv.app.programs

import com.erv.app.cardio.CardioActiveTimerSession
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioModality
import com.erv.app.cardio.CardioMultiLegTimerState
import com.erv.app.cardio.CardioTimerSessionDraft
import com.erv.app.cardio.CardioTimerStyle
import com.erv.app.cardio.resolveSnapshot

fun cardioTimerSessionForProgramBlock(
    block: ProgramDayBlock,
    cardioState: CardioLibraryState
): CardioActiveTimerSession? {
    val routineId = block.cardioRoutineId?.takeIf { it.isNotBlank() }
    if (routineId != null) {
        val routine = cardioState.routines.firstOrNull { it.id == routineId } ?: return null
        CardioMultiLegTimerState.fromRoutine(routine)?.let { return CardioActiveTimerSession.Multi(it) }
        val single = CardioTimerSessionDraft.fromRoutine(routine) ?: return null
        return CardioActiveTimerSession.Single(single)
    }
    val actName = block.cardioActivity ?: return null
    val builtin = runCatching { CardioBuiltinActivity.valueOf(actName) }.getOrNull() ?: return null
    val snapshot = cardioState.resolveSnapshot(builtin, null)
    val timerStyle: CardioTimerStyle = block.targetMinutes?.coerceIn(1, 24 * 60)?.let { m ->
        CardioTimerStyle.CountDown(m * 60)
    } ?: CardioTimerStyle.CountUp
    val draft = CardioTimerSessionDraft.fromQuickSnapshot(
        activity = snapshot,
        modality = CardioModality.OUTDOOR,
        treadmill = null,
        title = snapshot.displayLabel,
        timerStyle = timerStyle,
    )
    return CardioActiveTimerSession.Single(draft)
}
