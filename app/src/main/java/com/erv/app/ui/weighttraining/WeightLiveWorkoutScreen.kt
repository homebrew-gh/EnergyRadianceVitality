package com.erv.app.ui.weighttraining

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erv.app.R
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.data.WeightLiveRestTimerMode
import com.erv.app.ui.media.WorkoutMediaControlSheet
import com.erv.app.ui.media.playHiitWorkCountdownTickCue
import com.erv.app.ui.media.playHiitWorkSegmentEndCue
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.weighttraining.WeightHiitBlockLog
import com.erv.app.weighttraining.WeightHiitIntervalPlan
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.WeightWorkoutDraft
import com.erv.app.weighttraining.weightNowEpochSeconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class WorkoutTimerDisplayMode { SESSION, TOTAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightLiveWorkoutScreen(
    draft: WeightWorkoutDraft,
    library: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    userPreferences: UserPreferences,
    unifiedWorkoutStartedAtEpochSeconds: Long? = null,
    /** When the user expands an exercise, logs sets, or starts HIIT — for HR correlation. */
    onRecordExerciseActivity: (String) -> Unit = {},
    /** Back arrow: leave this screen; workout keeps running (notification). Parent may clear an empty draft. */
    onLeaveWorkoutUi: () -> Unit,
    /** User explicitly abandons the live session (top bar Discard). */
    onDiscardWorkout: () -> Unit,
    onFinish: () -> Unit,
    onAddExercise: (String) -> Unit,
    onRemoveExerciseAt: (Int) -> Unit,
    onMoveExerciseUp: (Int) -> Unit,
    onMoveExerciseDown: (Int) -> Unit,
    onSaveSets: (String, List<WeightSet>) -> Unit,
    onSaveHiitBlock: (String, WeightHiitBlockLog) -> Unit,
    onClearHiitBlock: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableIntStateOf(0) }
    var showPickExercise by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showFinishBlocked by remember { mutableStateOf(false) }
    // Start every exercise collapsed (e.g. routine load) so the list is compact until the user expands.
    var setsCollapsedIds by remember(draft.startedAtEpochSeconds) {
        mutableStateOf(draft.exerciseOrder.toSet())
    }
    var recentWorkoutsExerciseId by remember { mutableStateOf<String?>(null) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var hiitTimerTarget by remember { mutableStateOf<Pair<String, WeightHiitIntervalPlan>?>(null) }
    var restEndAtEpochSeconds by remember(draft.startedAtEpochSeconds) { mutableStateOf<Long?>(null) }
    var restManualPending by remember(draft.startedAtEpochSeconds) { mutableStateOf(false) }
    var showRestTimerSettings by remember { mutableStateOf(false) }

    val restTimerMode by userPreferences.weightLiveRestTimerMode.collectAsState(
        initial = WeightLiveRestTimerMode.OFF
    )
    val restTimerDurationSec by userPreferences.weightLiveRestTimerSeconds.collectAsState(initial = 90)
    val restTimerCountdownSoundEnabled by userPreferences.weightLiveRestTimerCountdownSoundEnabled.collectAsState(initial = true)
    val restTimerEndSoundEnabled by userPreferences.weightLiveRestTimerEndSoundEnabled.collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    val latestCountdownSoundEnabled by rememberUpdatedState(restTimerCountdownSoundEnabled)
    val latestEndSoundEnabled by rememberUpdatedState(restTimerEndSoundEnabled)
    var previousRestRemainingSec by remember(draft.startedAtEpochSeconds) { mutableStateOf<Int?>(null) }

    LaunchedEffect(draft.startedAtEpochSeconds) {
        tick = 0
        while (true) {
            tick++
            delay(1_000L)
        }
    }

    LaunchedEffect(restEndAtEpochSeconds, tick) {
        val end = restEndAtEpochSeconds ?: return@LaunchedEffect
        if (weightNowEpochSeconds() >= end) {
            if (latestEndSoundEnabled) {
                playHiitWorkSegmentEndCue()
            }
            restEndAtEpochSeconds = null
        }
    }

    fun clearRestTimerUi() {
        restEndAtEpochSeconds = null
        restManualPending = false
    }

    val restRemainingSec = remember(tick, restEndAtEpochSeconds) {
        val end = restEndAtEpochSeconds ?: return@remember null
        val left = (end - weightNowEpochSeconds()).toInt().coerceAtLeast(0)
        if (left <= 0) null else left
    }

    LaunchedEffect(restRemainingSec) {
        val current = restRemainingSec
        val previous = previousRestRemainingSec
        if (
            latestCountdownSoundEnabled &&
            current != null &&
            current in 1..5 &&
            current != previous
        ) {
            playHiitWorkCountdownTickCue()
        }
        previousRestRemainingSec = current
    }

    fun onAddSetPressedForRest() {
        clearRestTimerUi()
        if (hiitTimerTarget != null) return
        val mode = restTimerMode
        val duration = restTimerDurationSec
        if (mode == WeightLiveRestTimerMode.OFF || duration <= 0) return
        when (mode) {
            WeightLiveRestTimerMode.OFF -> Unit
            WeightLiveRestTimerMode.AUTO ->
                restEndAtEpochSeconds = weightNowEpochSeconds() + duration
            WeightLiveRestTimerMode.MANUAL ->
                restManualPending = true
        }
    }

    val darkTheme = isSystemInDarkTheme()
    val headerMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val headerDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark
    var timerDisplayMode by rememberSaveable(draft.startedAtEpochSeconds, unifiedWorkoutStartedAtEpochSeconds) {
        mutableStateOf(WorkoutTimerDisplayMode.SESSION)
    }

    if (showPickExercise) {
        WeightPickExerciseDialog(
            exercises = library.exercises.sortedBy { it.name.lowercase() },
            excludeIds = draft.exerciseOrder.toSet(),
            onDismiss = { showPickExercise = false },
            onPick = { id ->
                onAddExercise(id)
                onRecordExerciseActivity(id)
                showPickExercise = false
            }
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard workout?") },
            text = { Text("Your session will not be saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onDiscardWorkout()
                    }
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Cancel") }
            }
        )
    }

    recentWorkoutsExerciseId?.let { id ->
        val name = library.exerciseById(id)?.name ?: id
        WeightExerciseRecentWorkoutsDialog(
            exerciseId = id,
            exerciseName = name,
            library = library,
            loadUnit = loadUnit,
            onDismiss = { recentWorkoutsExerciseId = null }
        )
    }

    hiitTimerTarget?.let { (exerciseId, plan) ->
        val exName = library.exerciseById(exerciseId)?.name ?: exerciseId
        WeightHiitIntervalTimerOverlay(
            exerciseName = exName,
            plan = plan,
            onFinished = { block ->
                onRecordExerciseActivity(exerciseId)
                onSaveHiitBlock(exerciseId, block)
                hiitTimerTarget = null
                setsCollapsedIds = setsCollapsedIds - exerciseId
            },
            onDismiss = { hiitTimerTarget = null }
        )
    }

    if (showRestTimerSettings) {
        WeightLiveRestTimerSettingsDialog(
            initialMode = restTimerMode,
            initialSeconds = restTimerDurationSec,
            initialCountdownSoundEnabled = restTimerCountdownSoundEnabled,
            initialEndSoundEnabled = restTimerEndSoundEnabled,
            onDismiss = { showRestTimerSettings = false },
            onSave = { mode, seconds, countdownEnabled, endEnabled ->
                scope.launch {
                    userPreferences.setWeightLiveRestTimerMode(mode)
                    userPreferences.setWeightLiveRestTimerSeconds(seconds)
                    userPreferences.setWeightLiveRestTimerCountdownSoundEnabled(countdownEnabled)
                    userPreferences.setWeightLiveRestTimerEndSoundEnabled(endEnabled)
                }
            }
        )
    }

    if (showFinishBlocked) {
        AlertDialog(
            onDismissRequest = { showFinishBlocked = false },
            title = { Text("Nothing to save") },
            text = { Text("Log at least one set or completed interval block before finishing.") },
            confirmButton = {
                TextButton(onClick = { showFinishBlocked = false }) { Text("OK") }
            }
        )
    }

    WorkoutMediaControlSheet(
        visible = showMediaSheet,
        onDismiss = { showMediaSheet = false }
    )

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.weight_live_screen_title)) },
                    navigationIcon = {
                        IconButton(onClick = onLeaveWorkoutUi) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Leave workout screen"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showMediaSheet = !showMediaSheet },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = stringResource(R.string.media_control_cd_music),
                                tint = Color.White.copy(alpha = if (showMediaSheet) 1f else 0.88f)
                            )
                        }
                        TextButton(
                            onClick = { showDiscardConfirm = true },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text("Discard", color = Color.White.copy(alpha = 0.92f))
                        }
                        TextButton(
                            onClick = {
                                val hasLogged = draft.exerciseOrder.any { id ->
                                    draft.hiitBlocksByExerciseId[id] != null ||
                                        draft.setsByExerciseId[id].orEmpty().any { it.reps > 0 }
                                }
                                if (!hasLogged) showFinishBlocked = true
                                else onFinish()
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text("Finish", color = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = headerMid,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            },
            containerColor = headerDark.copy(alpha = 0.08f)
            ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                val elapsedSec = remember(tick, draft.startedAtEpochSeconds) {
                    (weightNowEpochSeconds() - draft.startedAtEpochSeconds).coerceAtLeast(0)
                }
                val unifiedElapsedSec = remember(tick, unifiedWorkoutStartedAtEpochSeconds) {
                    unifiedWorkoutStartedAtEpochSeconds?.let {
                        (weightNowEpochSeconds() - it).coerceAtLeast(0)
                    }
                }
                val showingUnifiedTotal =
                    unifiedElapsedSec != null && timerDisplayMode == WorkoutTimerDisplayMode.TOTAL
                WeightLiveRestTimerHeaderRow(
                    restMode = restTimerMode,
                    workoutElapsedLabel = if (showingUnifiedTotal) "Total workout" else "Weight session",
                    workoutElapsedText = formatElapsed(if (showingUnifiedTotal) unifiedElapsedSec ?: elapsedSec else elapsedSec),
                    workoutElapsedHint = if (unifiedElapsedSec != null) {
                        if (showingUnifiedTotal) "Swipe to view weight session"
                        else "Swipe to view total workout"
                    } else {
                        null
                    },
                    restSecondsRemaining = restRemainingSec,
                    restManualPending = restManualPending && restEndAtEpochSeconds == null,
                    onStartManualRest = {
                        restManualPending = false
                        restEndAtEpochSeconds = weightNowEpochSeconds() + restTimerDurationSec
                    },
                    onSkipRest = { clearRestTimerUi() },
                    onRestZoneLongPress = { showRestTimerSettings = true },
                    onWorkoutTimerSwipe = {
                        if (unifiedElapsedSec != null) {
                            timerDisplayMode =
                                if (timerDisplayMode == WorkoutTimerDisplayMode.SESSION) {
                                    WorkoutTimerDisplayMode.TOTAL
                                } else {
                                    WorkoutTimerDisplayMode.SESSION
                                }
                        }
                    },
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                if (draft.routineName != null) {
                    Text(
                        "From routine: ${draft.routineName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Button(
                    onClick = { showPickExercise = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Text("Add exercise")
                }
                Spacer(Modifier.height(12.dp))
                if (draft.exerciseOrder.isEmpty()) {
                    Text(
                        "Empty workout — add exercises, then fill in reps, weight, and RPE under each lift. Tap + Add set for more rows.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        itemsIndexed(draft.exerciseOrder, key = { _, id -> id }) { index, exerciseId ->
                            val ex = library.exerciseById(exerciseId)
                            val sets = weightSetsInDraft(draft, exerciseId)
                            WeightExerciseInlineSetsCard(
                                exerciseName = ex?.name ?: exerciseId,
                                equipmentLabel = ex?.equipment?.displayLabel(),
                                equipment = ex?.equipment,
                                sets = sets,
                                loadUnit = loadUnit,
                                onSetsChange = {
                                    onRecordExerciseActivity(exerciseId)
                                    onSaveSets(exerciseId, it)
                                },
                                canMoveUp = index > 0,
                                canMoveDown = index < draft.exerciseOrder.lastIndex,
                                onMoveUp = { onMoveExerciseUp(index) },
                                onMoveDown = { onMoveExerciseDown(index) },
                                onRemoveExercise = {
                                    setsCollapsedIds = setsCollapsedIds - exerciseId
                                    onRemoveExerciseAt(index)
                                },
                                setsCollapsed = exerciseId in setsCollapsedIds,
                                onCollapseSets = {
                                    setsCollapsedIds = setsCollapsedIds + exerciseId
                                },
                                onExpandSets = {
                                    onRecordExerciseActivity(exerciseId)
                                    setsCollapsedIds = setsCollapsedIds - exerciseId
                                    clearRestTimerUi()
                                },
                                onRecentWorkouts = { recentWorkoutsExerciseId = exerciseId },
                                hiitCapable = ex?.hiitCapable == true,
                                hiitBlock = draft.hiitBlocksByExerciseId[exerciseId],
                                onClearHiitBlock = { onClearHiitBlock(exerciseId) },
                                onStartHiitTimer = { plan ->
                                    onRecordExerciseActivity(exerciseId)
                                    clearRestTimerUi()
                                    hiitTimerTarget = exerciseId to plan
                                },
                                onAfterAddSet = { onAddSetPressedForRest() }
                            )
                        }
                    }
                }
            }
            }
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}
