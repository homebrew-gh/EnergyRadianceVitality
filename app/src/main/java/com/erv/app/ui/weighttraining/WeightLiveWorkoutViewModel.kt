package com.erv.app.ui.weighttraining

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.erv.app.data.UserPreferences
import com.erv.app.weighttraining.setLoggingStyle
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightLiveWorkoutForegroundService
import com.erv.app.weighttraining.WeightHiitBlockLog
import com.erv.app.weighttraining.WeightRoutine
import com.erv.app.weighttraining.WeightSet
import com.erv.app.workouts.WorkoutSegment
import com.erv.app.workouts.WorkoutWeightPrescription
import com.erv.app.workouts.advanceAfterSlot
import com.erv.app.workouts.buildCircuitSetsSeed
import com.erv.app.workouts.buildWorkoutCircuitRun
import com.erv.app.workouts.circuitSlotKey
import com.erv.app.workouts.currentSlot
import com.erv.app.workouts.isCurrentSlotLogged
import com.erv.app.workouts.pendingRestBeforeAdvance
import com.erv.app.workouts.resolvedSets
import com.erv.app.workouts.toWorkoutRunPosition
import com.erv.app.weighttraining.WeightWorkoutCircuitRun
import com.erv.app.weighttraining.WeightExerciseFocusMark
import com.erv.app.weighttraining.WeightWorkoutDraft
import com.erv.app.weighttraining.weightNowEpochSeconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CircuitAdvanceResult(
    val restSeconds: Int?,
    val isSegmentComplete: Boolean,
    val segmentIndex: Int?,
    val workoutRunPosition: com.erv.app.workouts.WorkoutRunPosition?,
)

class WeightLiveWorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val userPreferences = UserPreferences(application)

    private val draftJson = Json { ignoreUnknownKeys = true }

    private val _activeDraft = MutableStateFlow<WeightWorkoutDraft?>(null)
    val activeDraft: StateFlow<WeightWorkoutDraft?> = _activeDraft.asStateFlow()

    /** When false, the category screen shows tabs while the draft + FGS keep running (user left via back arrow). */
    private val _liveWorkoutUiExpanded = MutableStateFlow(true)
    val liveWorkoutUiExpanded: StateFlow<Boolean> = _liveWorkoutUiExpanded.asStateFlow()

    init {
        runBlocking {
            val json = userPreferences.liveWeightWorkoutDraftJson.first()
            if (!json.isNullOrBlank()) {
                val draft = runCatching { draftJson.decodeFromString<WeightWorkoutDraft>(json) }.getOrNull()
                if (draft != null) {
                    _activeDraft.value = draft
                    val suppressNotification = userPreferences.liveWeightWorkoutNotificationSuppressed.first()
                    if (!suppressNotification) {
                        WeightLiveWorkoutForegroundService.start(getApplication(), draft.startedAtEpochSeconds)
                    }
                }
            }
        }
    }

    fun setLiveWorkoutUiExpanded(expanded: Boolean) {
        _liveWorkoutUiExpanded.value = expanded
    }

    val hasLiveSession: Boolean get() = _activeDraft.value != null

    private fun persistDraft() {
        val d = _activeDraft.value ?: return
        viewModelScope.launch {
            userPreferences.setLiveWeightWorkoutDraftJson(draftJson.encodeToString(d))
        }
    }

    private fun clearPersistedDraft() {
        viewModelScope.launch {
            userPreferences.setLiveWeightWorkoutDraftJson(null)
        }
    }

    fun tryStartBlank(): Boolean {
        if (_activeDraft.value != null) return false
        val draft = WeightWorkoutDraft(
            startedAtEpochSeconds = weightNowEpochSeconds(),
            exerciseOrder = emptyList(),
            hiitBlocksByExerciseId = emptyMap()
        )
        _activeDraft.value = draft
        _liveWorkoutUiExpanded.value = true
        viewModelScope.launch {
            userPreferences.setLiveWeightWorkoutNotificationSuppressed(false)
            WeightLiveWorkoutForegroundService.start(getApplication(), draft.startedAtEpochSeconds)
            persistDraft()
        }
        return true
    }

    fun tryStartFromRoutine(
        routine: WeightRoutine,
        library: WeightLibraryState,
        suppressNotification: Boolean = false,
    ): Boolean {
        if (_activeDraft.value != null) return false
        val ids = routine.exerciseIds.filter { id -> library.exerciseById(id) != null }
        val blankRow = listOf(WeightSet(reps = 0, weightKg = null, rpe = null))
        val setsSeed = ids.associateWith { blankRow }
        val started = weightNowEpochSeconds()
        val initialMarks =
            if (ids.isNotEmpty()) listOf(WeightExerciseFocusMark(ids.first(), started)) else emptyList()
        val draft = WeightWorkoutDraft(
            startedAtEpochSeconds = started,
            exerciseOrder = ids,
            setsByExerciseId = setsSeed,
            hiitBlocksByExerciseId = emptyMap(),
            routineId = routine.id,
            routineName = routine.name,
            exerciseFocusMarks = initialMarks
        )
        _activeDraft.value = draft
        _liveWorkoutUiExpanded.value = true
        viewModelScope.launch {
            userPreferences.setLiveWeightWorkoutNotificationSuppressed(suppressNotification)
            if (!suppressNotification) {
                WeightLiveWorkoutForegroundService.start(getApplication(), draft.startedAtEpochSeconds)
            }
            persistDraft()
        }
        return true
    }

    fun tryStartFromWorkoutPrescription(
        exerciseId: String,
        prescription: WorkoutWeightPrescription,
        library: WeightLibraryState,
        sessionLabel: String?,
        suppressNotification: Boolean = false,
    ): Boolean {
        if (_activeDraft.value != null) return false
        val exercise = library.exerciseById(exerciseId) ?: return false
        val sets = prescription.resolvedSets(loggingStyle = exercise.setLoggingStyle())
        val started = weightNowEpochSeconds()
        val draft = WeightWorkoutDraft(
            startedAtEpochSeconds = started,
            exerciseOrder = listOf(exerciseId),
            setsByExerciseId = mapOf(exerciseId to sets),
            hiitBlocksByExerciseId = emptyMap(),
            routineName = sessionLabel,
            exerciseFocusMarks = listOf(WeightExerciseFocusMark(exerciseId, started)),
        )
        _activeDraft.value = draft
        _liveWorkoutUiExpanded.value = true
        viewModelScope.launch {
            userPreferences.setLiveWeightWorkoutNotificationSuppressed(suppressNotification)
            if (!suppressNotification) {
                WeightLiveWorkoutForegroundService.start(getApplication(), draft.startedAtEpochSeconds)
            }
            persistDraft()
        }
        return true
    }

    fun tryStartFromWorkoutCircuit(
        segment: WorkoutSegment,
        workoutId: String,
        workoutName: String?,
        library: WeightLibraryState,
        segmentIndex: Int,
        initialRound: Int = 1,
        initialSlotIndex: Int = 0,
        suppressNotification: Boolean = false,
    ): Boolean {
        if (_activeDraft.value != null) return false
        val circuit = buildWorkoutCircuitRun(
            segment = segment,
            workoutId = workoutId,
            segmentIndex = segmentIndex,
            initialRound = initialRound,
            initialSlotIndex = initialSlotIndex,
        ) ?: return false
        val exerciseIds = circuit.slots.map { it.exerciseId }
        val setsSeed = segment.buildCircuitSetsSeed(library)
        val started = weightNowEpochSeconds()
        val firstExerciseId = circuit.currentSlot()?.exerciseId
        val draft = WeightWorkoutDraft(
            startedAtEpochSeconds = started,
            exerciseOrder = exerciseIds,
            setsByExerciseId = setsSeed,
            hiitBlocksByExerciseId = emptyMap(),
            routineName = workoutName?.let { name ->
                buildString {
                    append(name)
                    circuit.segmentTitle?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                }
            },
            exerciseFocusMarks = firstExerciseId?.let {
                listOf(WeightExerciseFocusMark(it, started))
            } ?: emptyList(),
            circuitRun = circuit,
        )
        _activeDraft.value = draft
        _liveWorkoutUiExpanded.value = true
        viewModelScope.launch {
            userPreferences.setLiveWeightWorkoutNotificationSuppressed(suppressNotification)
            if (!suppressNotification) {
                WeightLiveWorkoutForegroundService.start(getApplication(), draft.startedAtEpochSeconds)
            }
            persistDraft()
        }
        return true
    }

    fun tryAdvanceCircuitAfterSlotComplete(): CircuitAdvanceResult? {
        val draft = _activeDraft.value ?: return null
        val circuit = draft.circuitRun ?: return null
        if (circuit.isComplete) {
            return CircuitAdvanceResult(
                restSeconds = null,
                isSegmentComplete = true,
                segmentIndex = circuit.segmentIndex,
                workoutRunPosition = null,
            )
        }
        if (!circuit.isCurrentSlotLogged(draft.setsByExerciseId, draft.hiitBlocksByExerciseId)) {
            return null
        }
        val slotKey = circuitSlotKey(circuit.currentRound, circuit.currentSlotIndex)
        if (slotKey == circuit.lastAcknowledgedSlotKey) return null
        val restSeconds = circuit.pendingRestBeforeAdvance()
        val withRest = circuit.advanceAfterSlot().copy(pendingRestSeconds = restSeconds)
        _activeDraft.value = draft.copy(circuitRun = withRest)
        persistDraft()
        return CircuitAdvanceResult(
            restSeconds = restSeconds,
            isSegmentComplete = withRest.isComplete,
            segmentIndex = withRest.segmentIndex,
            workoutRunPosition = if (!withRest.isComplete) {
                withRest.toWorkoutRunPosition(withRest.segmentIndex)
            } else {
                null
            },
        )
    }

    fun clearCircuitPendingRest() {
        val draft = _activeDraft.value ?: return
        val circuit = draft.circuitRun ?: return
        if (circuit.pendingRestSeconds == null) return
        _activeDraft.value = draft.copy(circuitRun = circuit.copy(pendingRestSeconds = null))
        persistDraft()
    }

    fun clearDraft() {
        if (_activeDraft.value != null) {
            WeightLiveWorkoutForegroundService.stop(getApplication())
        }
        _activeDraft.value = null
        _liveWorkoutUiExpanded.value = true
        viewModelScope.launch {
            userPreferences.setLiveWeightWorkoutNotificationSuppressed(false)
            clearPersistedDraft()
        }
    }

    fun addExercise(exerciseId: String) {
        val d = _activeDraft.value ?: return
        if (exerciseId in d.exerciseOrder) return
        val blankRow = listOf(WeightSet(reps = 0, weightKg = null, rpe = null))
        _activeDraft.value = d.copy(
            exerciseOrder = d.exerciseOrder + exerciseId,
            setsByExerciseId = d.setsByExerciseId + (exerciseId to blankRow)
        )
        persistDraft()
    }

    fun removeExerciseAt(index: Int) {
        val d = _activeDraft.value ?: return
        if (index !in d.exerciseOrder.indices) return
        val id = d.exerciseOrder[index]
        val newOrder = d.exerciseOrder.toMutableList().also { it.removeAt(index) }
        val newSets = d.setsByExerciseId - id
        val newHiit = d.hiitBlocksByExerciseId - id
        _activeDraft.value = d.copy(
            exerciseOrder = newOrder,
            setsByExerciseId = newSets,
            hiitBlocksByExerciseId = newHiit
        )
        persistDraft()
    }

    fun moveExerciseUp(index: Int) {
        val d = _activeDraft.value ?: return
        if (index <= 0) return
        val m = d.exerciseOrder.toMutableList()
        val t = m[index]
        m[index] = m[index - 1]
        m[index - 1] = t
        _activeDraft.value = d.copy(exerciseOrder = m)
        persistDraft()
    }

    fun moveExerciseDown(index: Int) {
        val d = _activeDraft.value ?: return
        if (index >= d.exerciseOrder.lastIndex) return
        val m = d.exerciseOrder.toMutableList()
        val t = m[index]
        m[index] = m[index + 1]
        m[index + 1] = t
        _activeDraft.value = d.copy(exerciseOrder = m)
        persistDraft()
    }

    fun setSetsForExercise(exerciseId: String, sets: List<WeightSet>) {
        val d = _activeDraft.value ?: return
        _activeDraft.value = d.copy(
            setsByExerciseId = d.setsByExerciseId + (exerciseId to sets),
            hiitBlocksByExerciseId = d.hiitBlocksByExerciseId - exerciseId
        )
        persistDraft()
    }

    fun setHiitBlockForExercise(exerciseId: String, block: WeightHiitBlockLog) {
        val d = _activeDraft.value ?: return
        val blankRow = listOf(WeightSet(reps = 0, weightKg = null, rpe = null))
        _activeDraft.value = d.copy(
            hiitBlocksByExerciseId = d.hiitBlocksByExerciseId + (exerciseId to block),
            setsByExerciseId = d.setsByExerciseId + (exerciseId to blankRow)
        )
        persistDraft()
    }

    fun clearHiitBlockForExercise(exerciseId: String) {
        val d = _activeDraft.value ?: return
        if (exerciseId !in d.hiitBlocksByExerciseId) return
        _activeDraft.value = d.copy(hiitBlocksByExerciseId = d.hiitBlocksByExerciseId - exerciseId)
        persistDraft()
    }

    /** Records that the user focused this exercise (for HR ↔ lift correlation). Skips duplicate consecutive ids. */
    fun recordExerciseFocus(exerciseId: String) {
        val d = _activeDraft.value ?: return
        if (exerciseId !in d.exerciseOrder) return
        val now = weightNowEpochSeconds()
        if (d.exerciseFocusMarks.lastOrNull()?.exerciseId == exerciseId) return
        _activeDraft.value = d.copy(
            exerciseFocusMarks = d.exerciseFocusMarks + WeightExerciseFocusMark(exerciseId, now)
        )
        persistDraft()
    }
}
