package com.erv.app.ui.heatcold

import com.erv.app.data.TemperatureUnit
import com.erv.app.heatcold.HeatColdMode
import com.erv.app.heatcold.displayName
import com.erv.app.heatcold.formatDurationSeconds
import com.erv.app.ui.wellness.WellnessTimerKind
import com.erv.app.ui.wellness.WellnessTimerStep

/** A contrast session: [rounds] of sauna then cold plunge, with a switch timer between. */
internal data class ContrastPlan(
    val rounds: Int,
    val saunaSeconds: Int,
    val coldSeconds: Int,
    val switchSeconds: Int,
    val saunaTemp: Int,
    val coldTemp: Int,
    val unit: TemperatureUnit,
)

private sealed class ContrastPhase {
    abstract val round: Int
    data class Session(val mode: HeatColdMode, override val round: Int) : ContrastPhase()
    data class Switch(val to: HeatColdMode, override val round: Int) : ContrastPhase()
}

/** The phases in order: sauna, switch, cold, switch, sauna, … ending on cold. */
private fun ContrastPlan.phases(): List<ContrastPhase> = buildList {
    for (round in 1..rounds) {
        add(ContrastPhase.Session(HeatColdMode.SAUNA, round))
        if (switchSeconds > 0) add(ContrastPhase.Switch(HeatColdMode.COLD_PLUNGE, round))
        add(ContrastPhase.Session(HeatColdMode.COLD_PLUNGE, round))
        if (round < rounds && switchSeconds > 0) add(ContrastPhase.Switch(HeatColdMode.SAUNA, round + 1))
    }
}

/** Total time of the plan, switches included. */
internal fun ContrastPlan.totalSeconds(): Int = phases().sumOf { phaseSeconds(it) }

private fun ContrastPlan.phaseSeconds(p: ContrastPhase): Int = when (p) {
    is ContrastPhase.Switch -> switchSeconds
    is ContrastPhase.Session -> if (p.mode == HeatColdMode.SAUNA) saunaSeconds else coldSeconds
}

/**
 * The first step of the plan for [com.erv.app.ui.wellness.WellnessTimerController.startSteps].
 * Each sauna and cold round is saved through [logRound]; switch timers save nothing.
 */
internal fun ContrastPlan.firstStep(
    logRound: suspend (mode: HeatColdMode, seconds: Int, temp: Int) -> Unit,
): WellnessTimerStep? {
    val phases = phases()
    val suffix = if (unit == TemperatureUnit.FAHRENHEIT) "°F" else "°C"
    val lastSessionIndex = phases.indexOfLast { it is ContrastPhase.Session }

    fun stepAt(i: Int): WellnessTimerStep? {
        val p = phases.getOrNull(i) ?: return null
        val roundText = "Round ${p.round} of $rounds"
        return when (p) {
            is ContrastPhase.Switch -> WellnessTimerStep(
                kind = if (p.to == HeatColdMode.SAUNA) WellnessTimerKind.SAUNA else WellnessTimerKind.COLD_PLUNGE,
                title = "Switch to ${p.to.displayName()}",
                detail = roundText,
                totalSeconds = switchSeconds,
                onSave = { null },
                next = { stepAt(i + 1) },
            )
            is ContrastPhase.Session -> {
                val temp = if (p.mode == HeatColdMode.SAUNA) saunaTemp else coldTemp
                WellnessTimerStep(
                    kind = if (p.mode == HeatColdMode.SAUNA) WellnessTimerKind.SAUNA else WellnessTimerKind.COLD_PLUNGE,
                    title = p.mode.displayName(),
                    detail = "$roundText · $temp $suffix",
                    totalSeconds = phaseSeconds(p),
                    onSave = { elapsed ->
                        logRound(p.mode, elapsed, temp)
                        if (i == lastSessionIndex) {
                            "Contrast done · $rounds round${if (rounds == 1) "" else "s"}"
                        } else {
                            "Logged ${p.mode.displayName()} · ${formatDurationSeconds(elapsed)}"
                        }
                    },
                    next = { stepAt(i + 1) },
                )
            }
        }
    }
    return stepAt(0)
}
