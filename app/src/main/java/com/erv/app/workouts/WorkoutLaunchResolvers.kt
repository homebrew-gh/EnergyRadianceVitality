package com.erv.app.workouts

import com.erv.app.cardio.CardioActiveTimerSession
import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioModality
import com.erv.app.cardio.CardioMultiLegTimerState
import com.erv.app.cardio.CardioRoutineStep
import com.erv.app.cardio.CardioTimerSessionDraft
import com.erv.app.cardio.CardioTimerStyle
import com.erv.app.cardio.displayName
import com.erv.app.cardio.resolveSnapshot
import com.erv.app.programs.ProgramDashboardStretchLaunch
import com.erv.app.programs.cardioTimerSessionForProgramBlock
import com.erv.app.programs.ProgramBlockKind
import com.erv.app.programs.ProgramDayBlock
import kotlin.math.max

fun WorkoutItem.Cardio.resolveCardioLaunch(
    cardioState: CardioLibraryState,
    displayTitle: String? = null,
): CardioActiveTimerSession? {
    val prescription = cardio
    prescription.cardioRoutineId?.takeIf { it.isNotBlank() }?.let { routineId ->
        return cardioTimerSessionForProgramBlock(
            ProgramDayBlock(
                kind = ProgramBlockKind.CARDIO,
                title = title,
                cardioRoutineId = routineId,
            ),
            cardioState,
        )
    }

    val builtin = runCatching { CardioBuiltinActivity.valueOf(prescription.activity) }.getOrNull()
        ?: return null
    val snapshot = cardioState.resolveSnapshot(builtin, null)
    val sessionName = displayTitle?.takeIf { it.isNotBlank() }
        ?: title?.takeIf { it.isNotBlank() }
        ?: snapshot.displayLabel

    return when (prescription.mode.lowercase()) {
        "interval_template" -> {
            val templateLegs = prescription.legs.filter { it.workSeconds > 0 }
            if (templateLegs.isEmpty()) return null
            CardioActiveTimerSession.Multi(
                buildIntervalTemplateState(
                    workActivity = snapshot,
                    routineName = sessionName,
                    outerRounds = prescription.outerRounds?.coerceAtLeast(1) ?: 1,
                    legs = templateLegs,
                ),
            )
        }
        "sprint_intervals" -> {
            val rounds = prescription.rounds?.coerceAtLeast(1) ?: return null
            val workSeconds = prescription.workSeconds?.coerceAtLeast(1) ?: return null
            val restSeconds = prescription.restSeconds?.coerceAtLeast(0) ?: 0
            CardioActiveTimerSession.Multi(
                buildSprintIntervalState(
                    workActivity = snapshot,
                    routineName = sessionName,
                    rounds = rounds,
                    workSeconds = workSeconds,
                    restSeconds = restSeconds,
                ),
            )
        }
        else -> {
            val timerStyle = prescription.targetMinutes?.coerceIn(1, 24 * 60)?.let { minutes ->
                CardioTimerStyle.CountDown(minutes * 60)
            } ?: CardioTimerStyle.CountUp
            CardioActiveTimerSession.Single(
                CardioTimerSessionDraft.fromQuickSnapshot(
                    activity = snapshot,
                    modality = CardioModality.OUTDOOR,
                    treadmill = null,
                    title = sessionName,
                    timerStyle = timerStyle,
                ),
            )
        }
    }
}

fun WorkoutItem.Mobility.resolveStretchLaunch(): ProgramDashboardStretchLaunch? {
    val catalogId = mobility.catalogId.takeIf { it.isNotBlank() } ?: return null
    val holdSeconds = mobility.holdSecondsPerSide
        ?: mobility.holdSeconds
        ?: 30
    return ProgramDashboardStretchLaunch(
        stretchIds = listOf(catalogId),
        title = title?.takeIf { it.isNotBlank() },
        holdSecondsPerStretch = holdSeconds.coerceIn(5, 300),
    )
}

internal fun buildSprintIntervalState(
    workActivity: CardioActivitySnapshot,
    routineName: String,
    rounds: Int,
    workSeconds: Int,
    restSeconds: Int,
): CardioMultiLegTimerState {
    val recoverySnap = activeRecoverySnapshot()
    val workMinutes = secondsToGuidedMinutes(workSeconds)
    val restMinutes = if (restSeconds > 0) secondsToGuidedMinutes(restSeconds) else 0
    val legs = buildList {
        var order = 0
        repeat(rounds) { roundIndex ->
            add(
                CardioRoutineStep(
                    activity = workActivity,
                    modality = CardioModality.OUTDOOR,
                    treadmill = null,
                    targetDurationMinutes = workMinutes,
                    orderIndex = order++,
                ),
            )
            if (restSeconds > 0 && roundIndex < rounds - 1) {
                add(
                    CardioRoutineStep(
                        activity = recoverySnap,
                        modality = CardioModality.OUTDOOR,
                        treadmill = null,
                        targetDurationMinutes = restMinutes,
                        orderIndex = order++,
                    ),
                )
            }
        }
    }
    return CardioMultiLegTimerState(
        routineId = null,
        routineName = routineName,
        legs = legs,
        completedSegments = emptyList(),
        currentLegIndex = 0,
        workoutStartEpoch = com.erv.app.cardio.CARDIO_TIMER_PENDING_START_EPOCH,
        legStartedEpoch = com.erv.app.cardio.CARDIO_TIMER_PENDING_START_EPOCH,
    )
}

internal fun buildIntervalTemplateState(
    workActivity: CardioActivitySnapshot,
    routineName: String,
    outerRounds: Int,
    legs: List<WorkoutCardioIntervalLeg>,
): CardioMultiLegTimerState {
    val recoverySnap = activeRecoverySnapshot()
    val builtLegs = buildList {
        var order = 0
        repeat(outerRounds) { outerIndex ->
            legs.forEachIndexed { legIndex, leg ->
                add(
                    CardioRoutineStep(
                        activity = workActivity,
                        modality = CardioModality.OUTDOOR,
                        treadmill = null,
                        targetDurationMinutes = secondsToGuidedMinutes(leg.workSeconds),
                        orderIndex = order++,
                    ),
                )
                val isLastLeg = legIndex >= legs.lastIndex
                val isLastOuter = outerIndex >= outerRounds - 1
                if (leg.restSeconds > 0 && !(isLastLeg && isLastOuter)) {
                    add(
                        CardioRoutineStep(
                            activity = recoverySnap,
                            modality = CardioModality.OUTDOOR,
                            treadmill = null,
                            targetDurationMinutes = secondsToGuidedMinutes(leg.restSeconds),
                            orderIndex = order++,
                        ),
                    )
                }
            }
        }
    }
    return CardioMultiLegTimerState(
        routineId = null,
        routineName = routineName,
        legs = builtLegs,
        completedSegments = emptyList(),
        currentLegIndex = 0,
        workoutStartEpoch = com.erv.app.cardio.CARDIO_TIMER_PENDING_START_EPOCH,
        legStartedEpoch = com.erv.app.cardio.CARDIO_TIMER_PENDING_START_EPOCH,
    )
}

private fun activeRecoverySnapshot(): CardioActivitySnapshot =
    CardioActivitySnapshot(
        builtin = CardioBuiltinActivity.ACTIVE_RECOVERY,
        customTypeId = null,
        customName = null,
        displayLabel = CardioBuiltinActivity.ACTIVE_RECOVERY.displayName(),
    )

internal fun secondsToGuidedMinutes(seconds: Int): Int =
    max(1, (seconds + 59) / 60)
