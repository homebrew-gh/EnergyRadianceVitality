package com.erv.app.ui.dashboard

import com.erv.app.cardio.CardioLibraryState
import com.erv.app.heatcold.HeatColdLibraryState
import com.erv.app.lighttherapy.LightLibraryState
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
    heatColdState: HeatColdLibraryState
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
}
