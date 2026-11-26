package com.erv.app.standards

import android.content.Context
import com.erv.app.bodytracker.BodyTrackerLibraryState
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioRecordGroup
import com.erv.app.cardio.ruckLoadKgResolved
import com.erv.app.data.TrainingProfileNostrPayload
import com.erv.app.weighttraining.WeightLibraryState

/** Built-in standards bundled with the app, read once. */
object BuiltInStandards {
    private const val ASSET = "standards/built_in_standards.json"

    @Volatile
    private var cached: List<FitnessStandard>? = null

    fun all(context: Context): List<FitnessStandard> =
        cached ?: synchronized(this) {
            cached ?: try {
                context.applicationContext.assets.open(ASSET).bufferedReader().use { decodeBuiltInStandards(it.readText()) }
            } catch (_: Exception) {
                emptyList()
            }.also { cached = it }
        }
}

/** The standards the profile follows, built-in or custom, in follow order; the main one first. */
fun followedStandards(
    profile: TrainingProfileNostrPayload,
    builtIns: List<FitnessStandard>,
    custom: List<FitnessStandard>,
): List<FitnessStandard> {
    val byId = (builtIns + custom).associateBy { it.id }
    val followed = profile.followedStandardIds.mapNotNull { byId[it] }.distinctBy { it.id }
    val main = followed.firstOrNull { it.id == profile.mainStandardId } ?: return followed
    return listOf(main) + followed.filter { it.id != main.id }
}

/** What the engine needs, from the phone's own weight, cardio and body tracker data. */
fun buildStandardInputs(
    weight: WeightLibraryState,
    cardio: CardioLibraryState,
    body: BodyTrackerLibraryState,
    profile: TrainingProfileNostrPayload,
    testResults: List<StoredStandardResult>,
): StandardInputs {
    val liftSets = buildList {
        for (day in weight.logs) for (workout in day.workouts) for (entry in workout.entries) {
            if (entry.hiitBlock != null) continue
            for (set in entry.sets) {
                if (set.warmup == true) continue
                if (set.reps <= 0 && (set.durationSeconds ?: 0) <= 0) continue
                add(StandardLiftSet(day.date, entry.exerciseId, set.reps, set.weightKg, set.durationSeconds))
            }
        }
    }
    val cardioSessions = buildList {
        for (day in cardio.logs) for (session in day.sessions) {
            if (session.segments.isNotEmpty()) continue
            val group = CardioRecordGroup.of(session.activity) ?: continue
            val start = session.startEpochSeconds
            val end = session.endEpochSeconds
            val seconds = if (start != null && end != null && end > start) (end - start).toDouble() else session.durationMinutes * 60.0
            add(
                StandardCardioSession(
                    date = day.date,
                    group = group.name,
                    groupLabel = group.label,
                    distanceMeters = session.distanceMeters ?: 0.0,
                    seconds = seconds,
                    packKg = session.ruckLoadKgResolved(),
                ),
            )
        }
    }
    val bodyWeights = body.logs.mapNotNull { log -> log.weightKg?.takeIf { it > 0 }?.let { StandardBodyWeight(log.date, it) } }
        .sortedBy { it.date }
    return StandardInputs(
        liftSets = liftSets,
        cardioSessions = cardioSessions,
        bodyWeights = bodyWeights,
        profile = StandardProfile(profile.ageYears, profile.sex, profile.heightCm),
        exerciseNames = weight.exercises.associate { it.id to it.name },
        testResults = testResults,
    )
}
