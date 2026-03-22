package com.erv.app.ui.weighttraining

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.erv.app.data.UserPreferences
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightLiveWorkoutForegroundService
import com.erv.app.weighttraining.WeightRoutine
import com.erv.app.weighttraining.WeightSet
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
                    WeightLiveWorkoutForegroundService.start(getApplication(), draft.startedAtEpochSeconds)
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
            exerciseOrder = emptyList()
        )
        _activeDraft.value = draft
        _liveWorkoutUiExpanded.value = true
        WeightLiveWorkoutForegroundService.start(getApplication(), draft.startedAtEpochSeconds)
        persistDraft()
        return true
    }

    fun tryStartFromRoutine(routine: WeightRoutine, library: WeightLibraryState): Boolean {
        if (_activeDraft.value != null) return false
        val ids = routine.exerciseIds.filter { id -> library.exerciseById(id) != null }
        val blankRow = listOf(WeightSet(reps = 0, weightKg = null, rpe = null))
        val setsSeed = ids.associateWith { blankRow }
        val draft = WeightWorkoutDraft(
            startedAtEpochSeconds = weightNowEpochSeconds(),
            exerciseOrder = ids,
            routineId = routine.id,
            routineName = routine.name,
            setsByExerciseId = setsSeed
        )
        _activeDraft.value = draft
        _liveWorkoutUiExpanded.value = true
        WeightLiveWorkoutForegroundService.start(getApplication(), draft.startedAtEpochSeconds)
        persistDraft()
        return true
    }

    fun clearDraft() {
        if (_activeDraft.value != null) {
            WeightLiveWorkoutForegroundService.stop(getApplication())
        }
        _activeDraft.value = null
        _liveWorkoutUiExpanded.value = true
        clearPersistedDraft()
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
        _activeDraft.value = d.copy(exerciseOrder = newOrder, setsByExerciseId = newSets)
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
        _activeDraft.value = d.copy(setsByExerciseId = d.setsByExerciseId + (exerciseId to sets))
        persistDraft()
    }
}
