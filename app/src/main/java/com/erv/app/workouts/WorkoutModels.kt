package com.erv.app.workouts

import com.erv.app.cardio.CardioHrScaffolding
import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.WeightSetLoggingStyle
import com.erv.app.weighttraining.repsShadowText
import com.erv.app.weighttraining.setLoggingStyle
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
enum class WorkoutSegmentKind {
    @SerialName("straight_sets") STRAIGHT_SETS,
    @SerialName("superset") SUPERSET,
    @SerialName("circuit") CIRCUIT,
    @SerialName("composite") COMPOSITE,
    @SerialName("cardio") CARDIO,
    @SerialName("interval") INTERVAL,
    @SerialName("mobility") MOBILITY,
    @SerialName("recovery") RECOVERY,
    @SerialName("freestyle") FREESTYLE,
}

@Serializable
enum class WorkoutWeightPrescriptionMode {
    @SerialName("straight") STRAIGHT,
    @SerialName("interval") INTERVAL,
    @SerialName("time_based") TIME_BASED,
    @SerialName("max_reps") MAX_REPS,
}

@Serializable
data class WorkoutRestPolicy(
    val restBetweenItemsSeconds: Int = 0,
    val restAfterRoundSeconds: Int = 0,
)

@Serializable
data class WorkoutWeightPrescription(
    val mode: WorkoutWeightPrescriptionMode = WorkoutWeightPrescriptionMode.STRAIGHT,
    val setCount: Int? = null,
    /** Single rep target for live workout ghost display. */
    val targetReps: Int? = null,
    /** Single load target (kg) for live workout ghost display. */
    val targetWeightKg: Double? = null,
    val repRangeMin: Int? = null,
    val repRangeMax: Int? = null,
    val targetRir: Int? = null,
    val restBetweenSetsSeconds: Int? = null,
    val restAfterExerciseSeconds: Int? = null,
    /** Hold duration target (seconds) for timed exercises. */
    val durationSeconds: Int? = null,
    /** Get-ready countdown before each timed set starts. Null/0 = start immediately on tap. */
    val timedPrepSeconds: Int? = null,
    val sets: List<WeightSet> = emptyList(),
)

/** Default get-ready countdown for new time-based prescriptions (builder + live fallback). */
const val DEFAULT_TIMED_PREP_SECONDS = 10

@Serializable
enum class WorkoutCardioLogField {
    @SerialName("INCLINE") INCLINE,
    @SerialName("SPEED") SPEED,
    @SerialName("DISTANCE") DISTANCE,
    @SerialName("NOTES") NOTES,
}

@Serializable
data class WorkoutCardioIntervalLeg(
    val workSeconds: Int,
    val restSeconds: Int = 0,
    val label: String? = null,
    val hrTargetBpm: Int? = null,
)

@Serializable
data class WorkoutCardioPrescription(
    val activity: String,
    val mode: String = "steady",
    val targetMinutes: Int? = null,
    val hrTargetBpm: Int? = null,
    val hrTargetMinBpm: Int? = null,
    val hrTargetMaxBpm: Int? = null,
    val hrZoneLabel: String? = null,
    val logFields: List<WorkoutCardioLogField> = emptyList(),
    val cardioRoutineId: String? = null,
    val outerRounds: Int? = null,
    val legs: List<WorkoutCardioIntervalLeg> = emptyList(),
    val rounds: Int? = null,
    val workSeconds: Int? = null,
    val restSeconds: Int? = null,
)

@Serializable
data class WorkoutMobilityPrescription(
    val catalogId: String,
    val holdSeconds: Int? = null,
    val holdSecondsPerSide: Int? = null,
)

@Serializable
@JsonClassDiscriminator("type")
sealed class WorkoutItem {
    abstract val id: String
    abstract val title: String?

    @Serializable
    @SerialName("weight")
    data class Weight(
        override val id: String = UUID.randomUUID().toString(),
        override val title: String? = null,
        val exerciseId: String,
        val alternativeExerciseIds: List<String> = emptyList(),
        val prescription: WorkoutWeightPrescription = WorkoutWeightPrescription(),
    ) : WorkoutItem()

    @Serializable
    @SerialName("cardio")
    data class Cardio(
        override val id: String = UUID.randomUUID().toString(),
        override val title: String? = null,
        val cardio: WorkoutCardioPrescription,
    ) : WorkoutItem()

    @Serializable
    @SerialName("mobility")
    data class Mobility(
        override val id: String = UUID.randomUUID().toString(),
        override val title: String? = null,
        val mobility: WorkoutMobilityPrescription,
    ) : WorkoutItem()

    @Serializable
    @SerialName("note")
    data class Note(
        override val id: String = UUID.randomUUID().toString(),
        override val title: String? = null,
        val text: String,
    ) : WorkoutItem()

    @Serializable
    @SerialName("rest")
    data class Rest(
        override val id: String = UUID.randomUUID().toString(),
        override val title: String? = null,
        val durationSeconds: Int,
    ) : WorkoutItem()
}

@Serializable
data class WorkoutSegment(
    val id: String = UUID.randomUUID().toString(),
    val kind: WorkoutSegmentKind,
    val title: String? = null,
    val notes: String? = null,
    val items: List<WorkoutItem> = emptyList(),
    val rounds: Int = 1,
    val restPolicy: WorkoutRestPolicy = WorkoutRestPolicy(),
    val restAfterSeconds: Int? = null,
)

@Serializable
data class Workout(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val sourceLabel: String? = null,
    val tags: List<String> = emptyList(),
    val segments: List<WorkoutSegment> = emptyList(),
    val createdAtEpochSeconds: Long = nowWorkoutEpochSeconds(),
    val lastModifiedEpochSeconds: Long = nowWorkoutEpochSeconds(),
)

@Serializable
data class WorkoutLibraryState(
    val workouts: List<Workout> = emptyList(),
    val activeRun: WorkoutActiveRun? = null,
    val libraryUpdatedAtEpochSeconds: Long = 0L,
) {
    fun workoutById(id: String): Workout? = workouts.firstOrNull { it.id == id }
}

fun WorkoutLibraryState.sanitized(): WorkoutLibraryState {
    val validActiveRun = activeRun?.takeIf { run -> workouts.any { it.id == run.workoutId } }
    val fallbackLibraryUpdated = workouts.maxOfOrNull { it.lastModifiedEpochSeconds } ?: 0L
    return copy(
        activeRun = validActiveRun,
        libraryUpdatedAtEpochSeconds = maxOf(libraryUpdatedAtEpochSeconds, fallbackLibraryUpdated),
    )
}

@Serializable
enum class WorkoutLoggedItemKind {
    @SerialName("cardio") CARDIO,
    @SerialName("weight") WEIGHT,
    @SerialName("mobility") MOBILITY,
}

@Serializable
data class WorkoutSessionLink(
    val sessionId: String,
    val workoutId: String,
    val segmentId: String,
    val itemId: String,
    val displayRef: String,
    /** Full-workout HR summary; set when the composed live run finishes. */
    val sessionHeartRate: CardioHrScaffolding? = null,
)

@Serializable
data class WorkoutItemRecap(
    val segmentId: String,
    val itemId: String,
    val kind: WorkoutLoggedItemKind,
    val linkedLogDate: String? = null,
    val linkedEntryId: String? = null,
    val finishedAtEpochSeconds: Long? = null,
)

@Serializable
data class WorkoutActiveRun(
    val sessionId: String = UUID.randomUUID().toString(),
    val workoutId: String,
    val workoutSnapshot: Workout,
    val createdAtEpochSeconds: Long = nowWorkoutEpochSeconds(),
    val startedAtEpochSeconds: Long? = null,
    val position: WorkoutRunPosition = WorkoutRunPosition(),
    val completedSegmentIds: List<String> = emptyList(),
    val lastLaunchedSegmentId: String? = null,
    val lastLaunchedItemId: String? = null,
    /** All storyboard items batched into the active silo session (e.g. consecutive weight lifts). */
    val lastLaunchedItemIds: List<String> = emptyList(),
    val itemRecaps: List<WorkoutItemRecap> = emptyList(),
    /** Set after a segment finishes; cleared when the athlete acknowledges the next-section prompt. */
    val pendingNextSegmentTitle: String? = null,
    /** Title of the section that just finished; paired with [pendingNextSegmentTitle] for the transition splash. */
    val pendingCompletedSegmentTitle: String? = null,
    /** Set after a section finishes when the next step is silo-backed; drives continuous auto-advance. */
    val autoAdvanceRequested: Boolean = false,
    val displayRef: String = generateWorkoutRunDisplayRef(),
)

fun WorkoutActiveRun.isStarted(): Boolean = startedAtEpochSeconds != null

fun generateWorkoutRunDisplayRef(nowEpochSeconds: Long = nowWorkoutEpochSeconds()): String {
    val timePart = nowEpochSeconds.toString(36).uppercase()
    val randomPart = UUID.randomUUID().toString().replace("-", "").takeLast(4).uppercase()
    return "WR-$timePart-$randomPart"
}

fun WorkoutActiveRun.linkFor(segmentId: String, itemId: String): WorkoutSessionLink =
    WorkoutSessionLink(
        sessionId = sessionId,
        workoutId = workoutId,
        segmentId = segmentId,
        itemId = itemId,
        displayRef = displayRef,
    )

@Serializable
data class WorkoutRunPosition(
    val segmentIndex: Int = 0,
    val itemIndex: Int = 0,
    /** 1-based round within circuit/superset segments. */
    val round: Int = 1,
)

@Serializable
data class WorkoutImportEnvelope(
    val ervWorkoutImportVersion: Int = 1,
    val workouts: List<Workout> = emptyList(),
)

fun nowWorkoutEpochSeconds(): Long = System.currentTimeMillis() / 1000

fun Workout.touch(): Workout = copy(lastModifiedEpochSeconds = nowWorkoutEpochSeconds())

fun WorkoutSegment.weightItems(): List<WorkoutItem.Weight> =
    items.filterIsInstance<WorkoutItem.Weight>()

fun WorkoutSegment.effectiveRounds(): Int = rounds.coerceAtLeast(1)

fun WorkoutItem.Weight.displayExerciseName(libraryName: String?): String =
    title?.takeIf { it.isNotBlank() } ?: libraryName ?: exerciseId

fun WorkoutItem.Weight.toSingleExerciseRoutine(name: String): com.erv.app.weighttraining.WeightRoutine =
    com.erv.app.weighttraining.WeightRoutine(
        name = name,
        exerciseIds = listOf(exerciseId),
    )

fun WorkoutItem.Cardio.displaySummary(activityLabel: String? = null): String = buildString {
    append(activityLabel?.takeIf { it.isNotBlank() } ?: cardio.activity)
    when (cardio.mode.lowercase()) {
        "sprint_intervals" -> {
            val rounds = cardio.rounds?.takeIf { it > 0 }
            val work = cardio.workSeconds?.takeIf { it > 0 }
            val rest = cardio.restSeconds?.takeIf { it > 0 }
            if (rounds != null && work != null) {
                append(" · ${rounds}× ${work}s")
                rest?.let { append("/${it}s") }
            }
        }
        "interval_template" -> {
            val outer = cardio.outerRounds?.takeIf { it > 0 }
            val leg = cardio.legs.firstOrNull()
            if (outer != null && leg != null) {
                append(" · ${outer} rounds · ${leg.workSeconds}s work")
                if (leg.restSeconds > 0) append("/${leg.restSeconds}s rest")
            }
        }
        else -> {
            cardio.targetMinutes?.takeIf { it > 0 }?.let { append(" · ${it} min") }
        }
    }
    cardio.hrZoneLabel?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
    val hrMin = cardio.hrTargetMinBpm?.takeIf { it > 0 }
    val hrMax = cardio.hrTargetMaxBpm?.takeIf { it > 0 }
    when {
        hrMin != null && hrMax != null -> append(" · ${hrMin}–${hrMax} bpm")
        cardio.hrTargetBpm?.takeIf { it > 0 } != null -> append(" · ${cardio.hrTargetBpm} bpm")
    }
    cardio.cardioRoutineId?.takeIf { it.isNotBlank() }?.let { append(" · saved routine") }
    if (cardio.logFields.isNotEmpty()) {
        append(" · log: ${cardio.logFields.joinToString(", ") { it.name.lowercase() }}")
    }
}

data class WorkoutPlannedItemLabelContext(
    val weightExerciseName: (String) -> String? = { null },
    val stretchName: (String) -> String? = { null },
    val cardioActivityLabel: (String) -> String? = { null },
)

fun WorkoutItem.plannedLabel(context: WorkoutPlannedItemLabelContext = WorkoutPlannedItemLabelContext()): String? =
    when (this) {
        is WorkoutItem.Weight -> displayExerciseName(context.weightExerciseName(exerciseId))
        is WorkoutItem.Cardio ->
            title?.takeIf { it.isNotBlank() }
                ?: displaySummary(context.cardioActivityLabel(cardio.activity))
        is WorkoutItem.Mobility ->
            title?.takeIf { it.isNotBlank() }
                ?: displaySummary(context.stretchName(mobility.catalogId))
        is WorkoutItem.Rest, is WorkoutItem.Note -> null
    }

fun Workout.plannedExerciseLabels(
    context: WorkoutPlannedItemLabelContext = WorkoutPlannedItemLabelContext(),
): List<String> = segments.flatMap { segment -> segment.items }.mapNotNull { it.plannedLabel(context) }

fun WorkoutItem.Mobility.displaySummary(stretchLabel: String? = null): String = buildString {
    append(stretchLabel?.takeIf { it.isNotBlank() } ?: mobility.catalogId)
    val hold = mobility.holdSecondsPerSide ?: mobility.holdSeconds
    hold?.takeIf { it > 0 }?.let { seconds ->
        append(
            if (mobility.holdSecondsPerSide != null) " · ${seconds}s/side" else " · ${seconds}s hold",
        )
    }
}

fun WorkoutWeightPrescription.effectiveTargetReps(): Int? =
    targetReps?.takeIf { it > 0 }
        ?: repRangeMin?.takeIf { it > 0 }?.takeIf { repRangeMax == null || repRangeMax == repRangeMin }
        ?: repRangeMax?.takeIf { it > 0 }?.takeIf { repRangeMin == null || repRangeMax == repRangeMin }

/** Live-workout ghost label when prescription uses a min–max rep range (e.g. "5-8"). */
fun WorkoutWeightPrescription.repRangeLabel(): String? {
    val min = repRangeMin?.takeIf { it > 0 } ?: return null
    val max = repRangeMax?.takeIf { it > 0 } ?: return null
    if (min == max) return null
    return "$min-$max"
}

fun WorkoutWeightPrescription.effectiveTargetWeightKg(): Double? =
    targetWeightKg?.takeIf { it > 0 }
        ?: sets.firstNotNullOfOrNull { set ->
            set.targetWeightKg?.takeIf { it > 0 } ?: set.weightKg?.takeIf { it > 0 }
        }

fun WeightSet.seedForLiveWorkout(): WeightSet {
    val repHint = targetReps?.takeIf { it > 0 } ?: reps.takeIf { it > 0 }
    val weightHint = targetWeightKg?.takeIf { it > 0 } ?: weightKg?.takeIf { it > 0 }
    val durationHint = targetDurationSeconds?.takeIf { it > 0 } ?: durationSeconds?.takeIf { it > 0 }
    return copy(
        reps = 0,
        weightKg = null,
        durationSeconds = null,
        targetReps = repHint,
        targetWeightKg = weightHint,
        targetDurationSeconds = durationHint,
    )
}

fun WorkoutWeightPrescription.effectiveTargetDurationSeconds(): Int? =
    durationSeconds?.takeIf { it > 0 }

fun WorkoutWeightPrescription.effectiveTimedPrepSeconds(): Int =
    timedPrepSeconds?.takeIf { it > 0 } ?: 0

fun WorkoutWeightPrescription.resolvedSets(loggingStyle: WeightSetLoggingStyle = WeightSetLoggingStyle.REPS): List<WeightSet> {
    val repRangeLabel = repRangeLabel()
    if (sets.isNotEmpty()) {
        return sets.map { set ->
            val seeded = set.seedForLiveWorkout()
            if (seeded.repsShadowText() == null && repRangeLabel != null) {
                seeded.copy(targetRepsRangeLabel = repRangeLabel)
            } else {
                seeded
            }
        }
    }
    val count = setCount?.coerceAtLeast(1) ?: 1
    val repTarget = effectiveTargetReps()
    val repRangeGhost = if (repTarget == null) repRangeLabel else null
    val weightTarget = effectiveTargetWeightKg()
    val durationTarget = effectiveTargetDurationSeconds()
    val timed = when (loggingStyle) {
        WeightSetLoggingStyle.TIME_ONLY -> true
        WeightSetLoggingStyle.REPS_OR_TIME -> mode == WorkoutWeightPrescriptionMode.TIME_BASED
        WeightSetLoggingStyle.REPS -> mode == WorkoutWeightPrescriptionMode.TIME_BASED
    } || mode == WorkoutWeightPrescriptionMode.TIME_BASED ||
        (durationTarget != null && loggingStyle != WeightSetLoggingStyle.REPS)
    val maxReps = mode == WorkoutWeightPrescriptionMode.MAX_REPS
    return List(count) {
        WeightSet(
            reps = 0,
            weightKg = null,
            rpe = null,
            durationSeconds = null,
            targetReps = if (!timed && !maxReps) repTarget else null,
            targetRepsRangeLabel = if (!timed && !maxReps) repRangeGhost else null,
            targetWeightKg = weightTarget,
            targetDurationSeconds = if (timed) durationTarget else null,
        )
    }
}

fun WorkoutWeightPrescription.usesPerSideReps(): Boolean =
    sets.any { (it.repsPerSide ?: 0) > 0 }

fun WorkoutWeightPrescription.displaySummary(): String {
    val setTotal = setCount ?: sets.size.takeIf { it > 0 } ?: 1
    val repPart = when {
        mode == WorkoutWeightPrescriptionMode.TIME_BASED && durationSeconds != null ->
            "${durationSeconds}s hold"
        durationSeconds != null && durationSeconds > 0 ->
            "${durationSeconds}s hold"
        mode == WorkoutWeightPrescriptionMode.MAX_REPS ->
            "max reps"
        usesPerSideReps() -> {
            val first = sets.firstOrNull { (it.repsPerSide ?: 0) > 0 }
            if (first != null) {
                buildString {
                    append("${first.repsPerSide}/side")
                    first.side?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
                }
            } else {
                null
            }
        }
        sets.isNotEmpty() && sets.any { it.reps > 0 || (it.targetReps ?: 0) > 0 } -> {
            val reps = sets.mapNotNull { s ->
                when {
                    s.reps > 0 -> s.reps
                    (s.targetReps ?: 0) > 0 -> s.targetReps
                    else -> null
                }
            }
            if (reps.size == sets.size && reps.toSet().size == 1) "${reps.first()} reps" else "varied reps"
        }
        targetReps != null && targetReps > 0 ->
            "$targetReps reps"
        repRangeMin != null && repRangeMax != null && repRangeMin != repRangeMax ->
            "$repRangeMin–$repRangeMax reps"
        repRangeMin != null -> "$repRangeMin reps"
        repRangeMax != null -> "$repRangeMax reps"
        else -> null
    }
    return buildString {
        append("$setTotal sets")
        repPart?.let { append(" · $it") }
        effectiveTargetWeightKg()?.let { append(" · ${it.toInt()} kg") }
        targetRir?.let { append(" · $it RIR") }
        restBetweenSetsSeconds?.takeIf { it > 0 }?.let { append(" · ${it}s between sets") }
        effectiveTimedPrepSeconds().takeIf { it > 0 }?.let { append(" · ${it}s get-ready") }
    }
}

fun Workout.duplicateCopy(): Workout {
    fun WorkoutItem.cloneWithNewId(): WorkoutItem = when (this) {
        is WorkoutItem.Weight -> copy(
            id = UUID.randomUUID().toString(),
            prescription = prescription.copy(sets = prescription.sets.map { it.copy() }),
        )
        is WorkoutItem.Cardio -> copy(
            id = UUID.randomUUID().toString(),
            cardio = cardio.copy(legs = cardio.legs.map { it.copy() }),
        )
        is WorkoutItem.Mobility -> copy(id = UUID.randomUUID().toString())
        is WorkoutItem.Note -> copy(id = UUID.randomUUID().toString())
        is WorkoutItem.Rest -> copy(id = UUID.randomUUID().toString())
    }
    return copy(
        id = UUID.randomUUID().toString(),
        name = "$name (copy)",
        segments = segments.map { segment ->
            segment.copy(
                id = UUID.randomUUID().toString(),
                items = segment.items.map { it.cloneWithNewId() },
            )
        },
        lastModifiedEpochSeconds = nowWorkoutEpochSeconds(),
        createdAtEpochSeconds = nowWorkoutEpochSeconds(),
    )
}

fun WorkoutWeightPrescription.ensureSetRows(): List<WeightSet> {
    val count = setCount?.coerceAtLeast(1) ?: sets.size.coerceAtLeast(1)
    val mutable = sets.toMutableList()
    while (mutable.size < count) {
        mutable.add(WeightSet(reps = 0))
    }
    return mutable.take(count)
}

fun defaultWorkoutPrescriptionForExercise(
    exercise: com.erv.app.weighttraining.WeightExercise?,
    segmentKind: WorkoutSegmentKind,
): WorkoutWeightPrescription {
    val setCount = if (segmentKind == WorkoutSegmentKind.CIRCUIT ||
        segmentKind == WorkoutSegmentKind.SUPERSET
    ) {
        1
    } else {
        3
    }
    return when (exercise?.setLoggingStyle()) {
        WeightSetLoggingStyle.TIME_ONLY -> WorkoutWeightPrescription(
            setCount = setCount,
            mode = WorkoutWeightPrescriptionMode.TIME_BASED,
            durationSeconds = 45,
            timedPrepSeconds = DEFAULT_TIMED_PREP_SECONDS,
        )
        WeightSetLoggingStyle.REPS_OR_TIME -> WorkoutWeightPrescription(
            setCount = setCount,
            targetReps = 10,
        )
        else -> WorkoutWeightPrescription(
            setCount = setCount,
            repRangeMin = 8,
            repRangeMax = 12,
        )
    }
}

fun WorkoutSegment.updateItemAt(index: Int, item: WorkoutItem): WorkoutSegment {
    if (index !in items.indices) return this
    return copy(items = items.toMutableList().apply { this[index] = item })
}

fun WorkoutSegment.removeItemAt(index: Int): WorkoutSegment {
    if (index !in items.indices) return this
    return copy(items = items.filterIndexed { i, _ -> i != index })
}

fun WorkoutSegment.moveItemUp(index: Int): WorkoutSegment {
    if (index <= 0 || index >= items.size) return this
    val mutable = items.toMutableList()
    val item = mutable.removeAt(index)
    mutable.add(index - 1, item)
    return copy(items = mutable)
}

fun WorkoutSegment.moveItemDown(index: Int): WorkoutSegment {
    if (index < 0 || index >= items.lastIndex) return this
    val mutable = items.toMutableList()
    val item = mutable.removeAt(index)
    mutable.add(index + 1, item)
    return copy(items = mutable)
}

fun WorkoutSegmentKind.supportsFullItemEditor(): Boolean =
    this == WorkoutSegmentKind.STRAIGHT_SETS ||
        this == WorkoutSegmentKind.COMPOSITE ||
        this == WorkoutSegmentKind.CARDIO ||
        this == WorkoutSegmentKind.INTERVAL ||
        this == WorkoutSegmentKind.MOBILITY ||
        this == WorkoutSegmentKind.CIRCUIT ||
        this == WorkoutSegmentKind.SUPERSET

fun WorkoutSegmentKind.defaultTitle(): String = when (this) {
    WorkoutSegmentKind.STRAIGHT_SETS -> "Main work"
    WorkoutSegmentKind.SUPERSET -> "Superset"
    WorkoutSegmentKind.CIRCUIT -> "Circuit"
    WorkoutSegmentKind.COMPOSITE -> "Warm-up"
    WorkoutSegmentKind.CARDIO -> "Cardio"
    WorkoutSegmentKind.INTERVAL -> "HIIT / intervals"
    WorkoutSegmentKind.MOBILITY -> "Mobility"
    else -> name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
}

fun defaultCardioModeForSegment(kind: WorkoutSegmentKind): String =
    if (kind == WorkoutSegmentKind.INTERVAL) "sprint_intervals" else "steady"

fun defaultWorkoutSegment(kind: WorkoutSegmentKind): WorkoutSegment {
    val base = WorkoutSegment(kind = kind, title = kind.defaultTitle())
    return if (kind == WorkoutSegmentKind.CIRCUIT || kind == WorkoutSegmentKind.SUPERSET) {
        base.copy(
            rounds = 3,
            restPolicy = WorkoutRestPolicy(restBetweenItemsSeconds = 0, restAfterRoundSeconds = 90),
        )
    } else {
        base
    }
}

fun newWorkoutCardioItem(activity: String, segmentKind: WorkoutSegmentKind): WorkoutItem.Cardio {
    val mode = defaultCardioModeForSegment(segmentKind)
    val prescription = when (mode) {
        "sprint_intervals" -> WorkoutCardioPrescription(
            activity = activity,
            mode = mode,
            rounds = 10,
            workSeconds = 60,
            restSeconds = 60,
        )
        "interval_template" -> WorkoutCardioPrescription(
            activity = activity,
            mode = mode,
            outerRounds = 3,
            legs = listOf(WorkoutCardioIntervalLeg(workSeconds = 240, restSeconds = 240, label = "Work leg")),
        )
        else -> WorkoutCardioPrescription(
            activity = activity,
            mode = "steady",
            targetMinutes = 10,
        )
    }
    return WorkoutItem.Cardio(cardio = prescription)
}

fun newWorkoutMobilityItem(catalogId: String): WorkoutItem.Mobility =
    WorkoutItem.Mobility(mobility = WorkoutMobilityPrescription(catalogId = catalogId, holdSeconds = 30))
