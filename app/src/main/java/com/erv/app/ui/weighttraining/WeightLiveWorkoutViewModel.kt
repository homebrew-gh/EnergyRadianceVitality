package com.erv.app.ui.weighttraining

import androidx.lifecycle.ViewModel
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRoutine
import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.WeightWorkoutDraft
import com.erv.app.weighttraining.weightNowEpochSeconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WeightLiveWorkoutViewModel : ViewModel() {

    private val _activeDraft = MutableStateFlow<WeightWorkoutDraft?>(null)
    val activeDraft: StateFlow<WeightWorkoutDraft?> = _activeDraft.asStateFlow()

    val hasLiveSession: Boolean get() = _activeDraft.value != null

    fun tryStartBlank(): Boolean {
        if (_activeDraft.value != null) return false
        _activeDraft.value = WeightWorkoutDraft(
            startedAtEpochSeconds = weightNowEpochSeconds(),
            exerciseOrder = emptyList()
        )
        return true
    }

    fun tryStartFromRoutine(routine: WeightRoutine, library: WeightLibraryState): Boolean {
        if (_activeDraft.value != null) return false
        val ids = routine.exerciseIds.filter { id -> library.exerciseById(id) != null }
        val blankRow = listOf(WeightSet(reps = 0, weightKg = null, rpe = null))
        val setsSeed = ids.associateWith { blankRow }
        _activeDraft.value = WeightWorkoutDraft(
            startedAtEpochSeconds = weightNowEpochSeconds(),
            exerciseOrder = ids,
            routineId = routine.id,
            routineName = routine.name,
            setsByExerciseId = setsSeed
        )
        return true
    }

    fun clearDraft() {
        _activeDraft.value = null
    }

    fun addExercise(exerciseId: String) {
        val d = _activeDraft.value ?: return
        if (exerciseId in d.exerciseOrder) return
        val blankRow = listOf(WeightSet(reps = 0, weightKg = null, rpe = null))
        _activeDraft.value = d.copy(
            exerciseOrder = d.exerciseOrder + exerciseId,
            setsByExerciseId = d.setsByExerciseId + (exerciseId to blankRow)
        )
    }

    fun removeExerciseAt(index: Int) {
        val d = _activeDraft.value ?: return
        if (index !in d.exerciseOrder.indices) return
        val id = d.exerciseOrder[index]
        val newOrder = d.exerciseOrder.toMutableList().also { it.removeAt(index) }
        val newSets = d.setsByExerciseId - id
        _activeDraft.value = d.copy(exerciseOrder = newOrder, setsByExerciseId = newSets)
    }

    fun moveExerciseUp(index: Int) {
        val d = _activeDraft.value ?: return
        if (index <= 0) return
        val m = d.exerciseOrder.toMutableList()
        val t = m[index]
        m[index] = m[index - 1]
        m[index - 1] = t
        _activeDraft.value = d.copy(exerciseOrder = m)
    }

    fun moveExerciseDown(index: Int) {
        val d = _activeDraft.value ?: return
        if (index >= d.exerciseOrder.lastIndex) return
        val m = d.exerciseOrder.toMutableList()
        val t = m[index]
        m[index] = m[index + 1]
        m[index + 1] = t
        _activeDraft.value = d.copy(exerciseOrder = m)
    }

    fun setSetsForExercise(exerciseId: String, sets: List<WeightSet>) {
        val d = _activeDraft.value ?: return
        _activeDraft.value = d.copy(setsByExerciseId = d.setsByExerciseId + (exerciseId to sets))
    }
}
