package com.erv.app.cardio

import kotlin.math.max

/**
 * Rough calorie estimates for cardio display.
 *
 * MET remains the baseline because it is stable for structured steady-state work. When a session
 * includes heart-rate data, we blend in a lighter HR-derived estimate to personalize the result
 * without letting noisy HR dominate.
 *
 * Treadmill values approximate Compendium-style walking/running by speed.
 */
object CardioMetEstimator {

    fun applyEstimatedKcal(session: CardioSession, library: CardioLibraryState, weightKg: Double?): CardioSession {
        if (session.segments.isEmpty()) {
            val customMet = session.activity.customTypeId?.let { id -> library.customTypeById(id)?.optionalMet }
            return session.copy(estimatedKcal = estimateKcal(session, customMet, weightKg))
        }
        val updated = session.segments.map { seg ->
            val mini = segmentToSession(seg)
            val customMet = seg.activity.customTypeId?.let { id -> library.customTypeById(id)?.optionalMet }
            seg.copy(estimatedKcal = estimateKcal(mini, customMet, weightKg))
        }
        val metTotal = updated.mapNotNull { it.estimatedKcal }.sum().takeIf { it > 0 }
        val total = metTotal?.let { blendWithHeartRate(it, session, weightKg) }
        return session.copy(segments = updated, estimatedKcal = total)
    }

    private fun segmentToSession(seg: CardioSessionSegment): CardioSession =
        CardioSession(
            activity = seg.activity,
            modality = seg.modality,
            treadmill = seg.treadmill,
            durationMinutes = seg.durationMinutes,
            distanceMeters = seg.distanceMeters,
            source = CardioSessionSource.MANUAL,
            heartRate = CardioHrScaffolding(),
            estimatedKcal = null,
            segments = emptyList()
        )

    fun estimateKcal(
        session: CardioSession,
        customMet: Double?,
        weightKg: Double?
    ): Double? {
        if (weightKg == null || weightKg <= 0) return null
        val met = resolveMet(session, customMet) ?: return null
        val hours = session.durationMinutes / 60.0
        val metKcal = met * weightKg * hours
        return blendWithHeartRate(metKcal, session, weightKg)
    }

    private fun blendWithHeartRate(metKcal: Double, session: CardioSession, weightKg: Double?): Double {
        val avgBpm = session.heartRate?.avgBpm?.takeIf { it > 0 } ?: return metKcal
        val kg = weightKg?.takeIf { it > 0 } ?: return metKcal
        val hours = session.durationMinutes / 60.0
        if (hours <= 0.0) return metKcal
        val hrMet = hrDerivedMet(avgBpm)
        val hrKcal = hrMet * kg * hours
        return metKcal * 0.7 + hrKcal * 0.3
    }

    private fun hrDerivedMet(avgBpm: Int): Double = when {
        avgBpm >= 180 -> 13.0
        avgBpm >= 170 -> 12.0
        avgBpm >= 160 -> 10.8
        avgBpm >= 150 -> 9.6
        avgBpm >= 140 -> 8.4
        avgBpm >= 130 -> 7.2
        avgBpm >= 120 -> 6.0
        avgBpm >= 110 -> 4.8
        avgBpm >= 100 -> 3.9
        else -> 3.2
    }

    private fun resolveMet(session: CardioSession, customMet: Double?): Double? {
        session.activity.customTypeId?.let {
            return customMet?.takeIf { m -> m > 0 }
        }
        val builtin = session.activity.builtin ?: return 5.0
        return when (session.modality) {
            CardioModality.INDOOR_TREADMILL -> treadmillMet(builtin, session.treadmill)
            CardioModality.OUTDOOR -> outdoorMet(builtin, session.treadmill, session.ruckLoadKg)
        }
    }

    private fun outdoorMet(
        builtin: CardioBuiltinActivity,
        treadmill: CardioTreadmillParams?,
        ruckLoadKg: Double?
    ): Double =
        when (builtin) {
            CardioBuiltinActivity.WALK -> 3.5
            CardioBuiltinActivity.RUN -> 9.0
            CardioBuiltinActivity.SPRINT -> 12.0
            CardioBuiltinActivity.RUCK -> {
                val load = treadmill?.loadKg ?: ruckLoadKg ?: 0.0
                if (load > 0) 8.5 else 7.0
            }
            CardioBuiltinActivity.HIKE -> 6.5
            CardioBuiltinActivity.BIKE -> 7.5
            CardioBuiltinActivity.SWIM -> 6.0
            CardioBuiltinActivity.ELLIPTICAL -> 5.5
            CardioBuiltinActivity.ROWING -> 6.0
            // Compendium: stationary cycling ~7 (general) to ~11 (161–200 W); middle ground for typical sessions
            CardioBuiltinActivity.STATIONARY_BIKE -> 8.0
            // Compendium: rope jumping moderate ~11.8; slightly conservative for mixed pacing
            CardioBuiltinActivity.JUMP_ROPE -> 11.0
            CardioBuiltinActivity.BATTLE_ROPE -> 10.0
            CardioBuiltinActivity.BURPEES -> 8.0
            CardioBuiltinActivity.JUMPING_JACKS -> 7.0
            // Fan bike: moderate–vigorous typical; intervals often higher — rough average
            CardioBuiltinActivity.AIR_BIKE -> 9.0
            CardioBuiltinActivity.SKI_ERG -> 8.5
            CardioBuiltinActivity.ACTIVE_RECOVERY -> 3.0
            CardioBuiltinActivity.OTHER -> 5.0
        }

    private fun treadmillMet(builtin: CardioBuiltinActivity, treadmill: CardioTreadmillParams?): Double {
        val t = treadmill ?: return outdoorMet(builtin, null, null)
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
            else -> outdoorMet(builtin, t, null)
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

    /** Per-leg minutes for quick log when the routine has multiple legs. */
    fun segmentDurationsForQuickLog(routine: CardioRoutine, fallbackTotalMinutes: Int): List<Int> {
        val legs = routine.effectiveSteps()
        require(legs.isNotEmpty())
        if (legs.size == 1) {
            return listOf(legs.first().targetDurationMinutes ?: fallbackTotalMinutes)
        }
        val targets = legs.map { it.targetDurationMinutes }
        if (targets.all { it != null && it!! > 0 }) {
            return targets.map { it!! }
        }
        val total = routine.targetDurationMinutes ?: fallbackTotalMinutes
        val base = total / legs.size
        val rem = total % legs.size
        return legs.indices.map { i -> base + if (i < rem) 1 else 0 }
    }

    fun buildSessionFromRoutine(
        routine: CardioRoutine,
        durationMinutes: Int,
        source: CardioSessionSource,
        weightKg: Double?,
        library: CardioLibraryState
    ): CardioSession {
        val durs = segmentDurationsForQuickLog(routine, durationMinutes)
        return buildSessionFromRoutineWithSegmentDurations(routine, durs, source, weightKg, library)
    }

    fun buildSessionFromRoutineWithSegmentDurations(
        routine: CardioRoutine,
        segmentMinutes: List<Int>,
        source: CardioSessionSource,
        weightKg: Double?,
        library: CardioLibraryState
    ): CardioSession {
        val legs = routine.effectiveSteps()
        require(segmentMinutes.size == legs.size && segmentMinutes.all { it > 0 }) {
            "segment minutes must match legs"
        }
        if (legs.size == 1) {
            val leg = legs.first()
            var dist = leg.treadmill?.distanceMeters
            if (dist == null && leg.modality == CardioModality.INDOOR_TREADMILL && leg.treadmill != null) {
                dist = derivedTreadmillDistanceMeters(leg.treadmill, segmentMinutes.first())
            }
            val base = CardioSession(
                activity = leg.activity,
                modality = leg.modality,
                treadmill = leg.treadmill,
                durationMinutes = segmentMinutes.first(),
                distanceMeters = dist,
                routineId = routine.id,
                routineName = routine.name,
                source = source,
                heartRate = CardioHrScaffolding(),
                estimatedKcal = null,
                segments = emptyList()
            )
            return applyEstimatedKcal(base, library, weightKg)
        }
        val segments = legs.mapIndexed { idx, leg ->
            val dur = segmentMinutes[idx]
            var dist = leg.treadmill?.distanceMeters
            if (dist == null && leg.modality == CardioModality.INDOOR_TREADMILL && leg.treadmill != null) {
                dist = derivedTreadmillDistanceMeters(leg.treadmill, dur)
            }
            val mini = CardioSession(
                activity = leg.activity,
                modality = leg.modality,
                treadmill = leg.treadmill,
                durationMinutes = dur,
                distanceMeters = dist,
                source = source,
                heartRate = CardioHrScaffolding(),
                estimatedKcal = null,
                segments = emptyList()
            )
            val customMet = leg.activity.customTypeId?.let { id -> library.customTypeById(id)?.optionalMet }
            val kcal = estimateKcal(mini, customMet, weightKg)
            CardioSessionSegment(
                activity = leg.activity,
                modality = leg.modality,
                treadmill = leg.treadmill,
                durationMinutes = dur,
                distanceMeters = dist,
                estimatedKcal = kcal,
                orderIndex = idx
            )
        }
        val totalMin = segmentMinutes.sum()
        val distParts = segments.mapNotNull { it.distanceMeters }
        val totalDist = distParts.takeIf { it.size == segments.size }?.sum()
        val rollup = CardioActivitySnapshot(
            builtin = null,
            customTypeId = null,
            customName = null,
            displayLabel = routine.stepsSummaryLabel()
        )
        val totalKcal = segments.mapNotNull { it.estimatedKcal }.sum().takeIf { it > 0 }
        val base = CardioSession(
            activity = rollup,
            modality = legs.first().modality,
            treadmill = null,
            durationMinutes = totalMin,
            distanceMeters = totalDist?.takeIf { it > 1 },
            estimatedKcal = totalKcal,
            routineId = routine.id,
            routineName = routine.name,
            source = source,
            heartRate = CardioHrScaffolding(),
            segments = segments
        )
        return applyEstimatedKcal(base, library, weightKg)
    }

    fun segmentFromLeg(
        leg: CardioRoutineStep,
        durationMinutes: Int,
        orderIndex: Int,
        library: CardioLibraryState,
        weightKg: Double?
    ): CardioSessionSegment {
        var dist = leg.treadmill?.distanceMeters
        if (dist == null && leg.modality == CardioModality.INDOOR_TREADMILL && leg.treadmill != null) {
            dist = derivedTreadmillDistanceMeters(leg.treadmill, durationMinutes)
        }
        val mini = CardioSession(
            activity = leg.activity,
            modality = leg.modality,
            treadmill = leg.treadmill,
            durationMinutes = durationMinutes,
            distanceMeters = dist,
            source = CardioSessionSource.DURATION_TIMER,
            heartRate = CardioHrScaffolding(),
            estimatedKcal = null,
            segments = emptyList()
        )
        val customMet = leg.activity.customTypeId?.let { id -> library.customTypeById(id)?.optionalMet }
        val kcal = estimateKcal(mini, customMet, weightKg)
        return CardioSessionSegment(
            activity = leg.activity,
            modality = leg.modality,
            treadmill = leg.treadmill,
            durationMinutes = durationMinutes,
            distanceMeters = dist,
            estimatedKcal = kcal,
            orderIndex = orderIndex
        )
    }

    fun rollupSessionFromSegments(
        routineId: String?,
        routineName: String?,
        segments: List<CardioSessionSegment>,
        workoutStartEpoch: Long,
        workoutEndEpoch: Long,
        library: CardioLibraryState,
        weightKg: Double?
    ): CardioSession {
        val ordered = segments.sortedBy { it.orderIndex }
        val totalMin = ordered.sumOf { it.durationMinutes }
        val label = ordered.joinToString(" → ") { it.activity.displayLabel }
        val distParts = ordered.mapNotNull { it.distanceMeters }
        val totalDist = distParts.takeIf { it.size == ordered.size }?.sum()
        val rollup = CardioActivitySnapshot(
            builtin = null,
            customTypeId = null,
            customName = null,
            displayLabel = label
        )
        val base = CardioSession(
            activity = rollup,
            modality = ordered.first().modality,
            treadmill = null,
            durationMinutes = totalMin,
            distanceMeters = totalDist?.takeIf { it > 1 },
            estimatedKcal = null,
            routineId = routineId,
            routineName = routineName,
            source = CardioSessionSource.DURATION_TIMER,
            startEpochSeconds = workoutStartEpoch,
            endEpochSeconds = workoutEndEpoch,
            heartRate = CardioHrScaffolding(),
            segments = ordered
        )
        return applyEstimatedKcal(base, library, weightKg)
    }

    /**
     * After logging one leg of a multi-leg timer: either advance to the next leg or produce the final session.
     */
    fun advanceMultiLegTimer(
        state: CardioMultiLegTimerState,
        legDurationMinutes: Int,
        legEndEpoch: Long,
        library: CardioLibraryState,
        weightKg: Double?
    ): Pair<CardioMultiLegTimerState?, CardioSession?> {
        val leg = state.currentLeg
        val seg = segmentFromLeg(
            leg = leg,
            durationMinutes = max(1, legDurationMinutes),
            orderIndex = state.completedSegments.size,
            library = library,
            weightKg = weightKg
        )
        val done = state.completedSegments + seg
        if (state.currentLegIndex < state.legs.lastIndex) {
            val next = CardioMultiLegTimerState(
                routineId = state.routineId,
                routineName = state.routineName,
                legs = state.legs,
                completedSegments = done,
                currentLegIndex = state.currentLegIndex + 1,
                workoutStartEpoch = state.workoutStartEpoch,
                legStartedEpoch = legEndEpoch
            )
            return next to null
        }
        val finalSession = rollupSessionFromSegments(
            routineId = state.routineId,
            routineName = state.routineName,
            segments = done,
            workoutStartEpoch = state.workoutStartEpoch,
            workoutEndEpoch = legEndEpoch,
            library = library,
            weightKg = weightKg
        )
        return null to finalSession
    }
}
