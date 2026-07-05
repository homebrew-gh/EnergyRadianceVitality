package com.erv.app.ui.weighttraining

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erv.app.R
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.data.WeightLiveRestTimerMode
import com.erv.app.hr.LocalHeartRateBle
import com.erv.app.ui.components.FormSectionLabelMedium
import com.erv.app.ui.media.WorkoutMediaControlPanel
import com.erv.app.ui.media.playHiitWorkCountdownTickCue
import com.erv.app.ui.media.playHiitWorkSegmentEndCue
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvHeaderRed
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.WeightHiitBlockLog
import com.erv.app.weighttraining.WeightHiitIntervalPlan
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.WeightWorkoutDraft
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.formatHiitBlockSummaryLine
import com.erv.app.weighttraining.formatSetValueLine
import com.erv.app.weighttraining.isLogged
import com.erv.app.weighttraining.setLoggingStyle
import com.erv.app.weighttraining.timedPrepSecondsFor
import com.erv.app.weighttraining.useTimedSetLogging
import com.erv.app.weighttraining.usesTimedHoldCountdownBeeps
import com.erv.app.weighttraining.weightLoadUnitSuffix
import com.erv.app.weighttraining.weightNowEpochSeconds
import com.erv.app.weighttraining.weightSetLoggingTriggersRest
import com.erv.app.workouts.currentSlot
import com.erv.app.workouts.isCurrentSlotLogged
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
    composedWorkoutStartedAtEpochSeconds: Long? = null,
    /** Section progress label like "Section 2 of 5" when run from a composed workout. */
    composedSectionLabel: String? = null,
    /** Top-bar primary action label; section-aware when run from a composed workout. */
    finishLabel: String = "Finish",
    /** When the user expands an exercise, logs sets, or starts HIIT — for HR correlation. */
    onRecordExerciseActivity: (String) -> Unit = {},
    /** After a set is logged during a composed-workout circuit — parent may advance the circuit. */
    onAfterCircuitSetLogged: () -> Unit = {},
    /** Circuit finished all rounds — parent should save and return to the workout storyboard. */
    onCircuitSegmentComplete: () -> Unit = {},
    /** Back arrow: leave this screen; workout keeps running (notification). Parent may clear an empty draft. */
    onLeaveWorkoutUi: () -> Unit,
    /** User explicitly abandons the live session (top bar Discard). */
    onDiscardWorkout: () -> Unit,
    /** Called when Finish is tapped but nothing is logged yet (parent may show a snackbar). */
    onCannotFinishNothingLogged: () -> Unit = {},
    onFinish: () -> Unit,
    onAddExercise: (String) -> Unit,
    onCreateExercise: (WeightExercise) -> Unit = {},
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
    var showExerciseCreator by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showFinishBlocked by remember { mutableStateOf(false) }
    // Start every exercise collapsed (e.g. routine load) so the list is compact until the user expands.
    var setsCollapsedIds by remember(draft.startedAtEpochSeconds) {
        mutableStateOf(draft.exerciseOrder.toSet())
    }
    var knownExerciseIds by remember(draft.startedAtEpochSeconds) {
        mutableStateOf(draft.exerciseOrder.toSet())
    }
    var recentWorkoutsExerciseId by remember { mutableStateOf<String?>(null) }
    // When non-null, the screen body shows a single-exercise sub-page (set entry focused on
    // that lift) instead of the exercises list. Auto-clears if the exercise is removed.
    var editingExerciseId by rememberSaveable(draft.startedAtEpochSeconds) {
        mutableStateOf<String?>(null)
    }
    LaunchedEffect(draft.exerciseOrder, editingExerciseId) {
        val id = editingExerciseId
        if (id != null && id !in draft.exerciseOrder) {
            editingExerciseId = null
        }
    }
    var mediaControlsEnabled by rememberSaveable { mutableStateOf(false) }
    var hiitTimerTarget by remember { mutableStateOf<Pair<String, WeightHiitIntervalPlan>?>(null) }
    var timedSetTimerTarget by remember { mutableStateOf<WeightTimedSetTimerTarget?>(null) }
    var restEndAtEpochSeconds by remember(draft.startedAtEpochSeconds) { mutableStateOf<Long?>(null) }
    var restManualPending by remember(draft.startedAtEpochSeconds) { mutableStateOf(false) }
    var activeRestExerciseId by remember(draft.startedAtEpochSeconds) { mutableStateOf<String?>(null) }
    var showRestTimerSettings by remember { mutableStateOf(false) }

    val restTimerMode by userPreferences.weightLiveRestTimerMode.collectAsState(
        initial = WeightLiveRestTimerMode.OFF
    )
    val restTimerDurationSec by userPreferences.weightLiveRestTimerSeconds.collectAsState(initial = 90)
    val restTimerCountdownSoundEnabled by userPreferences.weightLiveRestTimerCountdownSoundEnabled.collectAsState(initial = true)
    val restTimerEndSoundEnabled by userPreferences.weightLiveRestTimerEndSoundEnabled.collectAsState(initial = true)
    val heartRateBannerExpanded by userPreferences.heartRateBannerExpanded.collectAsState(initial = true)
    val heartRateBle = LocalHeartRateBle.current
    val scope = rememberCoroutineScope()
    val latestCountdownSoundEnabled by rememberUpdatedState(restTimerCountdownSoundEnabled)
    val latestEndSoundEnabled by rememberUpdatedState(restTimerEndSoundEnabled)
    var previousRestRemainingSec by remember(draft.startedAtEpochSeconds) { mutableStateOf<Int?>(null) }

    val circuitRun = draft.circuitRun
    // Keyed on round/slot/completion only, so this force-focuses the active slot on an
    // actual circuit advance — not on every recomposition. Backing out to the overview
    // (editingExerciseId = null) leaves these keys unchanged, so the user is not re-trapped.
    LaunchedEffect(circuitRun?.currentRound, circuitRun?.currentSlotIndex, circuitRun?.isComplete) {
        val circuit = circuitRun ?: return@LaunchedEffect
        if (circuit.isComplete) {
            onCircuitSegmentComplete()
            return@LaunchedEffect
        }
        circuit.currentSlot()?.exerciseId?.let { exerciseId ->
            editingExerciseId = exerciseId
            onRecordExerciseActivity(exerciseId)
        }
    }
    LaunchedEffect(circuitRun?.pendingRestSeconds) {
        val seconds = circuitRun?.pendingRestSeconds ?: return@LaunchedEffect
        if (seconds > 0) {
            restManualPending = false
            restEndAtEpochSeconds = weightNowEpochSeconds() + seconds
        }
    }

    LaunchedEffect(draft.startedAtEpochSeconds) {
        tick = 0
        while (true) {
            tick++
            delay(1_000L)
        }
    }

    LaunchedEffect(draft.exerciseOrder) {
        val currentIds = draft.exerciseOrder.toSet()
        val addedIds = currentIds - knownExerciseIds
        setsCollapsedIds = (setsCollapsedIds intersect currentIds) + addedIds
        knownExerciseIds = currentIds
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

    fun restDurationFor(exerciseId: String?): Int {
        val planned = exerciseId
            ?.let { draft.restBetweenSetsSecondsByExerciseId[it] }
            ?.takeIf { it > 0 }
        return planned ?: restTimerDurationSec
    }

    fun triggerRestAfterSet(exerciseId: String) {
        if (hiitTimerTarget != null) return
        if (timedSetTimerTarget != null) return
        val mode = restTimerMode
        val duration = restDurationFor(exerciseId)
        if (mode == WeightLiveRestTimerMode.OFF || duration <= 0) return
        activeRestExerciseId = exerciseId
        restEndAtEpochSeconds = null
        when (mode) {
            WeightLiveRestTimerMode.OFF -> Unit
            WeightLiveRestTimerMode.AUTO -> {
                restManualPending = false
                restEndAtEpochSeconds = weightNowEpochSeconds() + duration
            }
            WeightLiveRestTimerMode.MANUAL -> {
                restManualPending = true
            }
        }
    }

    fun openTimedSetTimer(exerciseId: String, storageSetIndex: Int) {
        val exercise = library.exerciseById(exerciseId) ?: return
        val sets = weightSetsInDraft(draft, exerciseId)
        val set = sets.getOrNull(storageSetIndex) ?: return
        val goalSeconds = set.targetDurationSeconds?.takeIf { it > 0 } ?: return
        onRecordExerciseActivity(exerciseId)
        clearRestTimerUi()
        timedSetTimerTarget = WeightTimedSetTimerTarget(
            exerciseId = exerciseId,
            setIndex = storageSetIndex,
            goalSeconds = goalSeconds,
            prepSeconds = draft.timedPrepSecondsFor(exerciseId, exercise.setLoggingStyle()),
            countdownBeeps = exercise.usesTimedHoldCountdownBeeps(),
        )
    }

    val darkTheme = isSystemInDarkTheme()
    val headerMid = ErvHeaderRed
    val headerDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark
    // When this weight session is part of a larger workout (composed run or unified routine),
    // default to showing the total workout clock so it keeps counting across sections instead
    // of appearing to reset each time a new section starts. The athlete can still swipe to the
    // per-section "Weight session" clock.
    val hasTotalWorkoutClock =
        unifiedWorkoutStartedAtEpochSeconds != null || composedWorkoutStartedAtEpochSeconds != null
    var timerDisplayMode by rememberSaveable(
        draft.startedAtEpochSeconds,
        unifiedWorkoutStartedAtEpochSeconds,
        composedWorkoutStartedAtEpochSeconds,
    ) {
        mutableStateOf(
            if (hasTotalWorkoutClock) {
                WorkoutTimerDisplayMode.TOTAL
            } else {
                WorkoutTimerDisplayMode.SESSION
            },
        )
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
                editingExerciseId = id
            },
            onCreateNew = {
                showPickExercise = false
                showExerciseCreator = true
            }
        )
    }

    if (showExerciseCreator) {
        WeightExerciseEditorDialog(
            initial = null,
            title = "Add exercise",
            availableMuscleGroups = library.exercises.map { it.muscleGroup },
            onDismiss = { showExerciseCreator = false },
            onSave = { exercise ->
                onCreateExercise(exercise)
                onAddExercise(exercise.id)
                onRecordExerciseActivity(exercise.id)
                editingExerciseId = exercise.id
                showExerciseCreator = false
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
                if (circuitRun != null) onAfterCircuitSetLogged()
            },
            onDismiss = { hiitTimerTarget = null }
        )
    }

    timedSetTimerTarget?.let { target ->
        val exName = library.exerciseById(target.exerciseId)?.name ?: target.exerciseId
        WeightTimedSetTimerOverlay(
            exerciseName = exName,
            goalSeconds = target.goalSeconds,
            prepSeconds = target.prepSeconds,
            countdownBeeps = target.countdownBeeps,
            onFinished = { durationSeconds ->
                val exerciseId = target.exerciseId
                val setIndex = target.setIndex
                onRecordExerciseActivity(exerciseId)
                val allSets = weightSetsInDraft(draft, exerciseId)
                val previousSets = allSets
                val updatedSets = allSets.toMutableList()
                if (setIndex in updatedSets.indices) {
                    updatedSets[setIndex] = updatedSets[setIndex].copy(durationSeconds = durationSeconds)
                    onSaveSets(exerciseId, updatedSets)
                    if (weightSetLoggingTriggersRest(previousSets, updatedSets)) {
                        triggerRestAfterSet(exerciseId)
                    }
                }
                timedSetTimerTarget = null
                setsCollapsedIds = setsCollapsedIds - exerciseId
                if (circuitRun != null) onAfterCircuitSetLogged()
            },
            onDismiss = { timedSetTimerTarget = null },
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
            title = { Text(stringResource(R.string.weight_live_finish_blocked_title)) },
            text = { Text(stringResource(R.string.weight_live_finish_blocked_body)) },
            confirmButton = {
                TextButton(onClick = { showFinishBlocked = false }) { Text("OK") }
            }
        )
    }

    val mediaSheetScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )
    LaunchedEffect(mediaControlsEnabled) {
        if (mediaControlsEnabled) {
            mediaSheetScaffoldState.bottomSheetState.expand()
        }
    }

    val topBar: @Composable () -> Unit = {
        val editingId = editingExerciseId
        val editingName = editingId?.let { library.exerciseById(it)?.name ?: it }
        TopAppBar(
            title = {
                if (editingName != null) {
                    Text(editingName, maxLines = 1)
                } else if (composedSectionLabel != null) {
                    Column {
                        Text(stringResource(R.string.weight_live_screen_title), maxLines = 1)
                        Text(
                            composedSectionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                    }
                } else {
                    Text(stringResource(R.string.weight_live_screen_title))
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (editingExerciseId != null) {
                            // Auto-save: every keystroke already propagates via onSetsChange,
                            // so leaving the sub-page just returns to the exercises list.
                            editingExerciseId = null
                        } else {
                            onLeaveWorkoutUi()
                        }
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (editingId != null) {
                            "Back to exercises"
                        } else {
                            "Leave workout screen"
                        }
                    )
                }
            },
            actions = {
                val isEditingExercise = editingId != null
                IconButton(
                    onClick = {
                        scope.launch {
                            val showHeartRateBanner = !heartRateBannerExpanded
                            userPreferences.setHeartRateBannerExpanded(showHeartRateBanner)
                            if (showHeartRateBanner) {
                                heartRateBle.tryPreferredDeviceReconnectOnce()
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (heartRateBannerExpanded) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Heart rate monitor",
                        tint = if (heartRateBannerExpanded) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.88f)
                    )
                }
                IconButton(
                    onClick = { mediaControlsEnabled = !mediaControlsEnabled },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = stringResource(R.string.media_control_cd_music),
                        tint = Color.White.copy(alpha = if (mediaControlsEnabled) 1f else 0.88f)
                    )
                }
                if (!isEditingExercise) {
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
                                    draft.setsByExerciseId[id].orEmpty().any { it.isLogged() }
                            }
                            if (!hasLogged) {
                                onCannotFinishNothingLogged()
                                showFinishBlocked = true
                            } else {
                                onFinish()
                            }
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(finishLabel, color = Color.White)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = headerMid,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White
            )
        )
    }

    val screenContent: @Composable (PaddingValues) -> Unit = { padding ->
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
            val composedElapsedSec = remember(tick, composedWorkoutStartedAtEpochSeconds) {
                composedWorkoutStartedAtEpochSeconds?.let {
                    (weightNowEpochSeconds() - it).coerceAtLeast(0)
                }
            }
            val totalElapsedSource = unifiedElapsedSec ?: composedElapsedSec
            val showingUnifiedTotal =
                totalElapsedSource != null && timerDisplayMode == WorkoutTimerDisplayMode.TOTAL
            WeightLiveRestTimerHeaderRow(
                restMode = restTimerMode,
                workoutElapsedLabel = if (showingUnifiedTotal) "Total workout" else "Weight session",
                workoutElapsedText = formatElapsed(
                    if (showingUnifiedTotal) totalElapsedSource ?: elapsedSec else elapsedSec,
                ),
                workoutElapsedHint = if (totalElapsedSource != null) {
                    if (showingUnifiedTotal) "Swipe to view weight session"
                    else "Swipe to view total workout"
                } else {
                    null
                },
                restSecondsRemaining = restRemainingSec,
                restManualPending = restManualPending && restEndAtEpochSeconds == null,
                onStartManualRest = {
                    restManualPending = false
                    val exerciseId = activeRestExerciseId ?: editingExerciseId
                    restEndAtEpochSeconds = weightNowEpochSeconds() + restDurationFor(exerciseId)
                },
                onSkipRest = { clearRestTimerUi() },
                onRestZoneLongPress = { showRestTimerSettings = true },
                onWorkoutTimerSwipe = {
                    if (totalElapsedSource != null) {
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
            circuitRun?.let { circuit ->
                Text(
                    text = buildString {
                        append(circuit.segmentTitle?.takeIf { it.isNotBlank() } ?: "Circuit")
                        append(" · Round ${circuit.currentRound}/${circuit.rounds}")
                        append(" · ${circuit.currentSlotIndex + 1}/${circuit.slots.size}")
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            val editingId = editingExerciseId
            if (editingId != null) {
                // Sub-page: focused set entry for one exercise. Back arrow in the top bar
                // (or the card's Save button) returns to the list. Field changes auto-save
                // through onSaveSets, so no explicit commit step is needed.
                val ex = library.exerciseById(editingId)
                val allSets = weightSetsInDraft(draft, editingId)
                val circuitRoundSetIndex = circuitRun?.currentRound?.minus(1)
                    ?.takeIf { it in allSets.indices }
                val sets = circuitRoundSetIndex?.let { listOf(allSets[it]) } ?: allSets
                val editingActiveSlot = circuitRun?.currentSlot()?.exerciseId == editingId
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Circuit: surface every round's logged values so earlier rounds stay visible
                    // when this exercise comes back around. The current round is edited below.
                    if (circuitRun != null && circuitRoundSetIndex != null && allSets.size > 1) {
                        item(key = "rounds_recap_${editingId}") {
                            CircuitRoundsRecapCard(
                                sets = allSets,
                                currentRoundIndex = circuitRoundSetIndex,
                                loadUnit = loadUnit,
                                equipment = ex?.equipment,
                            )
                        }
                    }
                    item(key = "edit_${editingId}") {
                        WeightExerciseInlineSetsCard(
                            exerciseName = ex?.name ?: editingId,
                            equipmentLabel = ex?.equipment?.displayLabel(),
                            equipment = ex?.equipment,
                            sets = sets,
                            loadUnit = loadUnit,
                            onSetsChange = { changedSets ->
                                val previousSets = allSets
                                onRecordExerciseActivity(editingId)
                                val updatedSets = if (circuitRoundSetIndex != null) {
                                    val updated = allSets.toMutableList()
                                    updated[circuitRoundSetIndex] =
                                        changedSets.firstOrNull() ?: updated[circuitRoundSetIndex]
                                    updated
                                } else {
                                    changedSets
                                }
                                onSaveSets(editingId, updatedSets)
                                if (weightSetLoggingTriggersRest(previousSets, updatedSets)) {
                                    triggerRestAfterSet(editingId)
                                }
                            },
                            canMoveUp = false,
                            canMoveDown = false,
                            onMoveUp = {},
                            onMoveDown = {},
                            onRemoveExercise = {
                                if (circuitRun != null) return@WeightExerciseInlineSetsCard
                                val idx = draft.exerciseOrder.indexOf(editingId)
                                setsCollapsedIds = setsCollapsedIds - editingId
                                editingExerciseId = null
                                if (idx >= 0) onRemoveExerciseAt(idx)
                            },
                            showMoveButtons = false,
                            setsCollapsed = false,
                            onCollapseSets = { editingExerciseId = null },
                            onExpandSets = {
                                onRecordExerciseActivity(editingId)
                                clearRestTimerUi()
                            },
                            onRecentWorkouts = { recentWorkoutsExerciseId = editingId },
                            hiitCapable = ex?.hiitCapable == true,
                            timePerSetCapable = ex?.useTimedSetLogging(
                                draft.setsByExerciseId[editingId].orEmpty(),
                            ) == true,
                            timedHoldCountdownBeeps = ex?.usesTimedHoldCountdownBeeps() == true,
                            allowAddSet = circuitRun == null,
                            onClearHiitBlock = { onClearHiitBlock(editingId) },
                            onStartHiitTimer = { plan ->
                                onRecordExerciseActivity(editingId)
                                clearRestTimerUi()
                                hiitTimerTarget = editingId to plan
                            },
                            onAfterAddSet = {
                                if (circuitRun != null) {
                                    onAfterCircuitSetLogged()
                                } else {
                                    triggerRestAfterSet(editingId)
                                }
                            },
                            onStartTimedSetTimer = { setIndex ->
                                val storageIndex = circuitRoundSetIndex ?: setIndex
                                openTimedSetTimer(editingId, storageIndex)
                            },
                        )
                    }
                    if (circuitRun != null && editingActiveSlot) {
                        item(key = "circuit_advance_${editingId}") {
                            val slotLogged = circuitRun.isCurrentSlotLogged(
                                draft.setsByExerciseId,
                                draft.hiitBlocksByExerciseId,
                            )
                            Button(
                                onClick = { onAfterCircuitSetLogged() },
                                enabled = slotLogged,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Log set & continue")
                            }
                        }
                    }
                }
            } else if (circuitRun != null) {
                // Overview of the whole circuit: review every slot's per-round values,
                // see which slot is active, and tap any exercise to re-open its editor.
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    itemsIndexed(
                        circuitRun.slots,
                        key = { _, slot -> slot.workoutItemId },
                    ) { index, slot ->
                        val slotExercise = library.exerciseById(slot.exerciseId)
                        CircuitSlotOverviewCard(
                            slotNumber = index + 1,
                            exerciseName = slotExercise?.name ?: slot.exerciseId,
                            equipmentLabel = slotExercise?.equipment?.displayLabel(),
                            equipment = slotExercise?.equipment,
                            sets = weightSetsInDraft(draft, slot.exerciseId),
                            hiitBlock = draft.hiitBlocksByExerciseId[slot.exerciseId],
                            currentRound = circuitRun.currentRound,
                            isActive = index == circuitRun.currentSlotIndex,
                            loadUnit = loadUnit,
                            onClick = {
                                onRecordExerciseActivity(slot.exerciseId)
                                editingExerciseId = slot.exerciseId
                            },
                        )
                    }
                }
            } else {
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
                                onSetsChange = { newSets ->
                                    val previousSets = weightSetsInDraft(draft, exerciseId)
                                    onRecordExerciseActivity(exerciseId)
                                    onSaveSets(exerciseId, newSets)
                                    if (weightSetLoggingTriggersRest(previousSets, newSets)) {
                                        triggerRestAfterSet(exerciseId)
                                    }
                                },
                                canMoveUp = index > 0,
                                canMoveDown = index < draft.exerciseOrder.lastIndex,
                                onMoveUp = { onMoveExerciseUp(index) },
                                onMoveDown = { onMoveExerciseDown(index) },
                                onRemoveExercise = {
                                    setsCollapsedIds = setsCollapsedIds - exerciseId
                                    onRemoveExerciseAt(index)
                                },
                                // List rows are always collapsed summaries; tap opens the
                                // single-exercise sub-page above instead of inline expand.
                                setsCollapsed = true,
                                onCollapseSets = { /* no-op: list rows stay collapsed */ },
                                onExpandSets = {
                                    onRecordExerciseActivity(exerciseId)
                                    clearRestTimerUi()
                                    editingExerciseId = exerciseId
                                },
                                onRecentWorkouts = { recentWorkoutsExerciseId = exerciseId },
                                hiitCapable = ex?.hiitCapable == true,
                                timePerSetCapable = ex?.useTimedSetLogging(
                                    draft.setsByExerciseId[exerciseId].orEmpty(),
                                ) == true,
                                timedHoldCountdownBeeps = ex?.usesTimedHoldCountdownBeeps() == true,
                                onClearHiitBlock = { onClearHiitBlock(exerciseId) },
                                onStartHiitTimer = { plan ->
                                    onRecordExerciseActivity(exerciseId)
                                    clearRestTimerUi()
                                    hiitTimerTarget = exerciseId to plan
                                },
                                onAfterAddSet = { triggerRestAfterSet(exerciseId) },
                                onStartTimedSetTimer = { setIndex ->
                                    openTimedSetTimer(exerciseId, setIndex)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (mediaControlsEnabled) {
            BottomSheetScaffold(
                scaffoldState = mediaSheetScaffoldState,
                sheetPeekHeight = 48.dp,
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                sheetTonalElevation = 2.dp,
                sheetShadowElevation = 4.dp,
                sheetContent = {
                    WorkoutMediaControlPanel(
                        useLightOnDarkBackground = false,
                        showHeaderTitle = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                    )
                },
                sheetDragHandle = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(headerMid)
                        )
                        HorizontalDivider(
                            thickness = 2.dp,
                            color = headerDark
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.media_control_sheet_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                topBar = topBar,
                containerColor = headerDark.copy(alpha = 0.08f)
            ) { padding ->
                screenContent(padding)
            }
        } else {
            Scaffold(
                topBar = topBar,
                containerColor = headerDark.copy(alpha = 0.08f)
            ) { padding ->
                screenContent(padding)
            }
        }
    }
}

/** Read-only recap of every round's logged values for the exercise being edited in a circuit. */
@Composable
private fun CircuitRoundsRecapCard(
    sets: List<WeightSet>,
    currentRoundIndex: Int,
    loadUnit: BodyWeightUnit,
    equipment: WeightEquipment?,
) {
    val loadSuffix = weightLoadUnitSuffix(loadUnit)
    val weightIsAddedLoad = equipment == WeightEquipment.OTHER
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FormSectionLabelMedium("All rounds")
            sets.forEachIndexed { idx, set ->
                val isCurrent = idx == currentRoundIndex
                val hasValue = set.isLogged() || set.reps > 0 || set.durationSeconds != null ||
                    set.weightKg != null || set.rpe != null
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Round ${idx + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (hasValue) {
                            formatSetValueLine(set, loadUnit, loadSuffix, weightIsAddedLoad)
                        } else {
                            "Not logged yet"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (isCurrent) {
                        Text(
                            text = "Current",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** One tappable circuit slot in the overview, showing its per-round logged values. */
@Composable
private fun CircuitSlotOverviewCard(
    slotNumber: Int,
    exerciseName: String,
    equipmentLabel: String?,
    equipment: WeightEquipment?,
    sets: List<WeightSet>,
    hiitBlock: WeightHiitBlockLog?,
    currentRound: Int,
    isActive: Boolean,
    loadUnit: BodyWeightUnit,
    onClick: () -> Unit,
) {
    val loadSuffix = weightLoadUnitSuffix(loadUnit)
    val weightIsAddedLoad = equipment == WeightEquipment.OTHER
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$slotNumber. $exerciseName",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (isActive) {
                    Text(
                        text = "Current",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            equipmentLabel?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hiitBlock != null) {
                Text(
                    formatHiitBlockSummaryLine(hiitBlock, loadUnit, loadSuffix, weightIsAddedLoad),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                sets.forEachIndexed { idx, set ->
                    val isCurrent = isActive && (idx + 1) == currentRound
                    val hasValue = set.isLogged() || set.reps > 0 || set.durationSeconds != null ||
                        set.weightKg != null || set.rpe != null
                    Text(
                        text = "Round ${idx + 1}: " + if (hasValue) {
                            formatSetValueLine(set, loadUnit, loadSuffix, weightIsAddedLoad)
                        } else {
                            "Not logged yet"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                    )
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
