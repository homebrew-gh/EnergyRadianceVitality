package com.erv.app.cardio

import kotlin.math.max

/**
 * Rough MET estimates for calorie display (MET × kg × hours).
 * Treadmill values approximate Compendium-style walking/running by speed.
 */
object CardioMetEstimator {

    fun applyEstimatedKcal(session: CardioSession, library: CardioLibraryState, weightKg: Double?): CardioSession {
        val customMet = session.activity.customTypeId?.let { id -> library.customTypeById(id)?.optionalMet }
        return session.copy(estimatedKcal = estimateKcal(session, customMet, weightKg))
    }

    fun estimateKcal(
        session: CardioSession,
        customMet: Double?,
        weightKg: Double?
    ): Double? {
        if (weightKg == null || weightKg <= 0) return null
        val met = resolveMet(session, customMet) ?: return null
        val hours = session.durationMinutes / 60.0
        return met * weightKg * hours
    }

    private fun resolveMet(session: CardioSession, customMet: Double?): Double? {
        session.activity.customTypeId?.let {
            return customMet?.takeIf { m -> m > 0 }
        }
        val builtin = session.activity.builtin ?: return 5.0
        return when (session.modality) {
            CardioModality.INDOOR_TREADMILL -> treadmillMet(builtin, session.treadmill)
            CardioModality.OUTDOOR -> outdoorMet(builtin, session.treadmill)
        }
    }

    private fun outdoorMet(builtin: CardioBuiltinActivity, treadmill: CardioTreadmillParams?): Double =
        when (builtin) {
            CardioBuiltinActivity.WALK -> 3.5
            CardioBuiltinActivity.RUN -> 9.0
            CardioBuiltinActivity.SPRINT -> 12.0
            CardioBuiltinActivity.RUCK -> if ((treadmill?.loadKg ?: 0.0) > 0) 8.5 else 7.0
            CardioBuiltinActivity.HIKE -> 6.5
            CardioBuiltinActivity.BIKE -> 7.5
            CardioBuiltinActivity.SWIM -> 6.0
            CardioBuiltinActivity.ELLIPTICAL -> 5.5
            CardioBuiltinActivity.ROWING -> 6.0
            CardioBuiltinActivity.OTHER -> 5.0
        }

    private fun treadmillMet(builtin: CardioBuiltinActivity, treadmill: CardioTreadmillParams?): Double {
        val t = treadmill ?: return outdoorMet(builtin, null)
        val mph = when (t.speedUnit) {
            CardioSpeedUnit.MPH -> t.speed
            CardioSpeedUnit.KMH -> t.speed / 1.60934
        }
        val inclineBoost = 1.0 + (t.inclinePercent / 100.0) * 0.12
        val base = when (builtin) {
            CardioBuiltinActivity.WALK -> walkTreadmillMet(mph)
            CardioBuiltinActivity.RUN, CardioBuiltinActivity.SPRINT -> runTreadmillMet(mph)
            CardioBuiltinActivity.RUCK -> {
                val load = t.loadKg ?: 0.0
                runTreadmillMet(mph) + load.coerceAtMost(40.0) * 0.08
            }
            else -> outdoorMet(builtin, t)
        }
        return max(1.0, base * inclineBoost)
    }

    private fun walkTreadmillMet(mph: Double): Double = when {
        mph < 2.0 -> 2.0
        mph < 2.5 -> 2.8
        mph < 3.0 -> 3.3
        mph < 3.5 -> 3.8
        mph < 4.0 -> 4.3
        mph < 4.5 -> 5.0
        else -> 5.5
    }

    private fun runTreadmillMet(mph: Double): Double = when {
        mph < 5.0 -> 6.0
        mph < 6.0 -> 8.0
        mph < 7.0 -> 9.8
        mph < 8.0 -> 11.0
        mph < 9.0 -> 11.5
        else -> 12.5
    }

    fun buildSessionFromRoutine(
        routine: CardioRoutine,
        durationMinutes: Int,
        source: CardioSessionSource,
        weightKg: Double?,
        library: CardioLibraryState
    ): CardioSession {
        var dist = routine.treadmill?.distanceMeters
        if (dist == null && routine.modality == CardioModality.INDOOR_TREADMILL && routine.treadmill != null) {
            dist = derivedTreadmillDistanceMeters(routine.treadmill, durationMinutes)
        }
        val base = CardioSession(
            activity = routine.activity,
            modality = routine.modality,
            treadmill = routine.treadmill,
            durationMinutes = durationMinutes,
            distanceMeters = dist,
            routineId = routine.id,
            routineName = routine.name,
            source = source,
            heartRate = CardioHrScaffolding(),
            estimatedKcal = null
        )
        return applyEstimatedKcal(base, library, weightKg)
    }
}
