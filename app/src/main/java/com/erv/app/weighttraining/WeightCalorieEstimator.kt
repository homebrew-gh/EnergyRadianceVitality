package com.erv.app.weighttraining

/**
 * Approximate calorie estimation for weight training.
 *
 * We prefer heart rate when present, but blend it with workout structure so the estimate stays
 * reasonable for lifting sessions with uneven effort. When HR is missing, we fall back to a
 * conservative workload-based MET estimate.
 */
object WeightCalorieEstimator {

    fun estimateKcal(session: WeightWorkoutSession, bodyWeightKg: Double?): Double? {
        val weightKg = bodyWeightKg?.takeIf { it > 0.0 } ?: return null
        val durationSeconds = session.resolvedDurationSeconds() ?: return null
        if (durationSeconds < 60 || session.entries.isEmpty()) return null
        val met = session.heartRate?.avgBpm?.let { avgBpm ->
            blendedHrMet(session, durationSeconds, weightKg, avgBpm)
        } ?: structuralMet(session, durationSeconds, weightKg)
        return (met * weightKg * (durationSeconds / 3600.0)).takeIf { it > 0.0 }
    }

    private fun blendedHrMet(
        session: WeightWorkoutSession,
        durationSeconds: Int,
        bodyWeightKg: Double,
        avgBpm: Int
    ): Double {
        val structural = structuralMet(session, durationSeconds, bodyWeightKg)
        val hrMet = when {
            avgBpm >= 170 -> 9.0
            avgBpm >= 160 -> 8.4
            avgBpm >= 150 -> 7.8
            avgBpm >= 140 -> 7.1
            avgBpm >= 130 -> 6.4
            avgBpm >= 120 -> 5.7
            avgBpm >= 110 -> 5.0
            avgBpm >= 100 -> 4.4
            else -> 3.8
        }
        return (structural * 0.4 + hrMet * 0.6).coerceIn(3.5, 9.0)
    }

    private fun structuralMet(
        session: WeightWorkoutSession,
        durationSeconds: Int,
        bodyWeightKg: Double
    ): Double {
        val regularSets = session.entries.sumOf { it.sets.size }
        val hiitIntervals = session.entries.sumOf { it.hiitBlock?.intervals ?: 0 }
        val hiitWorkSeconds = session.entries.sumOf { entry ->
            entry.hiitBlock?.let { it.intervals * it.workSeconds } ?: 0
        }
        val estimatedSetWorkSeconds = regularSets * 35
        val activeRatio = ((hiitWorkSeconds + estimatedSetWorkSeconds).toDouble() / durationSeconds)
            .coerceIn(0.0, 1.0)
        val setsPerMinute = session.totalSetCount() / (durationSeconds / 60.0)
        val volumePerBodyWeight = session.totalVolumeKg() / bodyWeightKg

        var met = when {
            hiitIntervals >= 12 || hiitWorkSeconds >= 12 * 60 || activeRatio >= 0.55 -> 6.9
            setsPerMinute >= 0.95 || activeRatio >= 0.40 -> 6.1
            setsPerMinute >= 0.55 || activeRatio >= 0.25 -> 5.2
            else -> 4.1
        }
        if (volumePerBodyWeight >= 20.0) {
            met += 0.6
        } else if (volumePerBodyWeight >= 10.0) {
            met += 0.3
        }
        return met.coerceIn(3.5, 8.2)
    }
}
