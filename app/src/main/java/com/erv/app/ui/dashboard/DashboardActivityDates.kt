package com.erv.app.ui.dashboard

import com.erv.app.cardio.CardioLibraryState
import com.erv.app.heatcold.HeatColdLibraryState
import com.erv.app.lighttherapy.LightLibraryState
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.supplements.SupplementLibraryState
import com.erv.app.weighttraining.WeightLibraryState
import java.time.LocalDate

/**
 * Dates where the user logged at least one activity across dashboard-tracked domains.
 */
fun datesWithActivityLogged(
    supplementState: SupplementLibraryState,
    lightState: LightLibraryState,
    cardioState: CardioLibraryState,
    weightState: WeightLibraryState,
    heatColdState: HeatColdLibraryState,
    stretchState: StretchLibraryState
): Set<LocalDate> = buildSet {
    supplementState.logs.forEach { log ->
        if (log.routineRuns.isNotEmpty() || log.adHocIntakes.isNotEmpty()) {
            runCatching { add(LocalDate.parse(log.date)) }
        }
    }
    lightState.logs.forEach { log ->
        if (log.sessions.isNotEmpty()) runCatching { add(LocalDate.parse(log.date)) }
    }
    cardioState.logs.forEach { log ->
        if (log.sessions.isNotEmpty()) runCatching { add(LocalDate.parse(log.date)) }
    }
    weightState.logs.forEach { log ->
        if (log.workouts.isNotEmpty()) runCatching { add(LocalDate.parse(log.date)) }
    }
    heatColdState.saunaLogs.forEach { log ->
        if (log.sessions.isNotEmpty()) runCatching { add(LocalDate.parse(log.date)) }
    }
    heatColdState.coldLogs.forEach { log ->
        if (log.sessions.isNotEmpty()) runCatching { add(LocalDate.parse(log.date)) }
    }
    stretchState.logs.forEach { log ->
        if (log.sessions.isNotEmpty()) runCatching { add(LocalDate.parse(log.date)) }
    }
}

/** Dates with at least one supplement log entry (routine or ad hoc). */
fun datesWithSupplementActivity(state: SupplementLibraryState): Set<LocalDate> = buildSet {
    state.logs.forEach { log ->
        if (log.routineRuns.isNotEmpty() || log.adHocIntakes.isNotEmpty()) {
            runCatching { add(LocalDate.parse(log.date)) }
        }
    }
}

/** Dates with at least one light therapy session logged. */
fun datesWithLightActivity(state: LightLibraryState): Set<LocalDate> = buildSet {
    state.logs.forEach { log ->
        if (log.sessions.isNotEmpty()) runCatching { add(LocalDate.parse(log.date)) }
    }
}

/** Dates with at least one cardio session logged. */
fun datesWithCardioActivity(state: CardioLibraryState): Set<LocalDate> = buildSet {
    state.logs.forEach { log ->
        if (log.sessions.isNotEmpty()) runCatching { add(LocalDate.parse(log.date)) }
    }
}

/** Dates with at least one weight training workout logged. */
fun datesWithWeightActivity(state: WeightLibraryState): Set<LocalDate> = buildSet {
    state.logs.forEach { log ->
        if (log.workouts.isNotEmpty()) runCatching { add(LocalDate.parse(log.date)) }
    }
}

/** Dates with at least one sauna or cold plunge session logged. */
fun datesWithHeatColdActivity(state: HeatColdLibraryState): Set<LocalDate> = buildSet {
    state.saunaLogs.forEach { log ->
        if (log.sessions.isNotEmpty()) runCatching { add(LocalDate.parse(log.date)) }
    }
    state.coldLogs.forEach { log ->
        if (log.sessions.isNotEmpty()) runCatching { add(LocalDate.parse(log.date)) }
    }
}

/** Dates with at least one stretching session logged. */
fun datesWithStretchActivity(state: StretchLibraryState): Set<LocalDate> = buildSet {
    state.logs.forEach { log ->
        if (log.sessions.isNotEmpty()) runCatching { add(LocalDate.parse(log.date)) }
    }
}
