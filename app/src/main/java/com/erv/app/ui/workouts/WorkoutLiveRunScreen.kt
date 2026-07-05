package com.erv.app.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import com.erv.app.ui.components.FormSectionLabel
import com.erv.app.ui.components.FormSectionLabelSmall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erv.app.hr.LocalHeartRateBle
import com.erv.app.ui.theme.ErvHeaderRed
import com.erv.app.ui.weighttraining.WeightLiveWorkoutFgsDisclosureDialog
import com.erv.app.ui.weighttraining.WeightLiveWorkoutViewModel
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.workouts.Workout
import com.erv.app.workouts.WorkoutActiveRun
import com.erv.app.workouts.WorkoutItem
import com.erv.app.workouts.WorkoutRepository
import com.erv.app.workouts.WorkoutRunEngine
import com.erv.app.workouts.WorkoutRunPosition
import com.erv.app.workouts.WorkoutSegment
import com.erv.app.workouts.WorkoutSegmentKind
import com.erv.app.workouts.displayExerciseName
import com.erv.app.workouts.displaySummary
import com.erv.app.workouts.isStarted
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioRepository
import com.erv.app.cardio.formatCardioAveragePaceForSession
import com.erv.app.cardio.formatCardioDistanceFromMeters
import com.erv.app.cardio.isCyclingActivity
import com.erv.app.cardio.resolveSnapshot
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.programs.encodeStretchLaunch
import com.erv.app.hr.HeartRateSessionAnalyticsSection
import com.erv.app.hr.HeartRateZoneInputs
import com.erv.app.stretching.StretchingRepository
import com.erv.app.ui.cardio.CardioBikeErgConnectInlineSection
import com.erv.app.ui.cardio.CardioLiveWorkoutViewModel
import com.erv.app.ui.cardio.supportsBikeErgSensorConnect
import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.formatHiitBlockSummaryLine
import com.erv.app.weighttraining.formatSetSummaryLine
import com.erv.app.weighttraining.totalSetCount
import com.erv.app.weighttraining.totalVolumeLoadTimesReps
import com.erv.app.weighttraining.weightLoadUnitSuffix
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.buildWorkoutShareHashtagContentLineFromTopics
import com.erv.app.nostr.workoutShareBaseTopicTags
import com.erv.app.workouts.ComposedWorkoutHrSection
import com.erv.app.workouts.ComposedWorkoutHrSummary
import com.erv.app.workouts.summaryLabel
import com.erv.app.workouts.attachComposedWorkoutHeartRateToLinkedLogs
import com.erv.app.workouts.buildComposedWorkoutHrSummary
import com.erv.app.workouts.publishComposedWorkoutNote
import com.erv.app.workouts.resolveCardioLaunch
import com.erv.app.workouts.resolveStretchLaunch
import com.erv.app.workouts.weightItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ActiveWorkoutRest(
    val advanceFrom: WorkoutRunPosition,
    val secondsRemaining: Int,
    val label: String,
)

/** Between-section splash shown after finishing one section, counting down into the next. */
private data class WorkoutSectionTransition(
    val finishedTitle: String,
    val nextTitle: String,
    val secondsRemaining: Int,
    val autoAdvance: Boolean,
)

private const val SECTION_TRANSITION_COUNTDOWN_SECONDS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLiveRunScreen(
    workoutId: String,
    repository: WorkoutRepository,
    cardioRepository: CardioRepository,
    weightRepository: WeightRepository,
    stretchingRepository: StretchingRepository,
    activeRun: WorkoutActiveRun?,
    workout: Workout?,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    userPreferences: UserPreferences,
    weightLiveWorkoutViewModel: WeightLiveWorkoutViewModel,
    cardioLiveWorkoutViewModel: CardioLiveWorkoutViewModel,
    relayPool: RelayPool? = null,
    signer: EventSigner? = null,
    onBack: () -> Unit,
    onOpenWeightCategory: () -> Unit,
    onOpenCardioCategory: () -> Unit,
    onOpenStretchCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val heartRateBle = LocalHeartRateBle.current
    val liveWeightDraft by weightLiveWorkoutViewModel.activeDraft.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val fgsDisclosureSeen by userPreferences.weightLiveWorkoutFgsDisclosureSeen.collectAsState(initial = false)
    val heartRateZoneInputs by userPreferences.heartRateZoneInputs.collectAsState(initial = HeartRateZoneInputs())
    val loadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.LB)
    val distanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    var composedSummary by remember { mutableStateOf<ComposedWorkoutHrSummary?>(null) }
    var runFinalized by remember { mutableStateOf(false) }
    var finishingRun by remember { mutableStateOf(false) }
    var pendingWeightItem by remember { mutableStateOf<WorkoutItem.Weight?>(null) }
    var pendingWeightLabel by remember { mutableStateOf<String?>(null) }
    var pendingAlternativeChoice by remember {
        mutableStateOf<Pair<WorkoutItem.Weight, String>?>(null)
    }
    var pendingCircuitSegment by remember { mutableStateOf<WorkoutSegment?>(null) }
    var pendingWeightBatch by remember { mutableStateOf<List<WorkoutItem.Weight>?>(null) }
    var activeRest by remember { mutableStateOf<ActiveWorkoutRest?>(null) }
    var sectionTransition by remember { mutableStateOf<WorkoutSectionTransition?>(null) }

    // A finished section leaves both a "next section" title and (when the workout continues) a
    // "just finished" title. Turn that into a full-screen transition splash that counts down into
    // the next section instead of a quick snackbar + instant auto-advance.
    LaunchedEffect(activeRun?.pendingNextSegmentTitle) {
        val nextTitle = activeRun?.pendingNextSegmentTitle ?: return@LaunchedEffect
        sectionTransition = WorkoutSectionTransition(
            finishedTitle = activeRun.pendingCompletedSegmentTitle ?: "Section",
            nextTitle = nextTitle,
            secondsRemaining = SECTION_TRANSITION_COUNTDOWN_SECONDS,
            autoAdvance = activeRun.autoAdvanceRequested,
        )
        // Consume the prompt + auto-advance flag now; the countdown drives the launch itself so
        // the auto-advance effect below does not fire underneath the splash.
        repository.clearPendingNextSegmentPrompt()
        repository.clearAutoAdvance()
    }

    LaunchedEffect(workoutId, workout) {
        if (!finishingRun && workout != null && (activeRun == null || activeRun.workoutId != workoutId)) {
            repository.startRun(workoutId)
        }
    }

    LaunchedEffect(activeRun?.startedAtEpochSeconds, workoutId) {
        if (activeRun?.workoutId == workoutId && activeRun.isStarted()) {
            heartRateBle.startComposedWorkoutRunRecording()
            heartRateBle.tryPreferredDeviceReconnectOnce()
        }
    }

    fun launchCircuitSegment(segment: WorkoutSegment) {
        if (weightLiveWorkoutViewModel.hasLiveSession) {
            weightLiveWorkoutViewModel.setLiveWorkoutUiExpanded(true)
            onOpenWeightCategory()
            return
        }
        val segmentIndex = activeRun?.position?.segmentIndex ?: 0
        val position = activeRun?.position ?: WorkoutRunPosition()
        val started = weightLiveWorkoutViewModel.tryStartFromWorkoutCircuit(
            segment = segment,
            workoutId = workoutId,
            workoutName = workout?.name,
            library = weightState,
            segmentIndex = segmentIndex,
            initialRound = position.round,
            initialSlotIndex = position.itemIndex,
            suppressNotification = true,
        )
        if (started) {
            // Re-arm the per-section HR buffer so every section (not just the first) captures its
            // own snapshot; the continuous whole-workout buffer keeps running untouched.
            heartRateBle.resetWorkoutRecordingOnLiveStart()
            val firstItem = segment.weightItems().firstOrNull()
            scope.launch {
                if (firstItem != null) {
                    repository.setLastLaunchedItem(segment.id, firstItem.id)
                }
                weightLiveWorkoutViewModel.setLiveWorkoutUiExpanded(true)
                onOpenWeightCategory()
            }
        }
    }

    fun proceedToCircuitLaunch(segment: WorkoutSegment) {
        if (!fgsDisclosureSeen) {
            pendingCircuitSegment = segment
            return
        }
        launchCircuitSegment(segment)
    }

    fun launchWeightItemInternal(item: WorkoutItem.Weight, label: String) {
        if (weightLiveWorkoutViewModel.hasLiveSession) {
            weightLiveWorkoutViewModel.setLiveWorkoutUiExpanded(true)
            onOpenWeightCategory()
            return
        }
        val segmentIndex = activeRun?.position?.segmentIndex ?: 0
        val segmentId = workout?.segments?.getOrNull(segmentIndex)?.id
        val started = weightLiveWorkoutViewModel.tryStartFromWorkoutPrescription(
            exerciseId = item.exerciseId,
            prescription = item.prescription,
            library = weightState,
            sessionLabel = label,
            suppressNotification = true,
        )
        if (started) {
            heartRateBle.resetWorkoutRecordingOnLiveStart()
            scope.launch {
                if (segmentId != null) {
                    repository.setLastLaunchedItem(segmentId, item.id)
                }
                weightLiveWorkoutViewModel.setLiveWorkoutUiExpanded(true)
                onOpenWeightCategory()
            }
        }
    }

    fun proceedToWeightLaunch(item: WorkoutItem.Weight, label: String) {
        if (!fgsDisclosureSeen) {
            pendingWeightItem = item
            pendingWeightLabel = label
            return
        }
        launchWeightItemInternal(item, label)
    }

    fun launchWeightItem(item: WorkoutItem.Weight, label: String) {
        if (item.alternativeExerciseIds.isNotEmpty()) {
            pendingAlternativeChoice = item to label
            return
        }
        proceedToWeightLaunch(item, label)
    }

    fun launchWeightBatchInternal(items: List<WorkoutItem.Weight>, label: String) {
        if (weightLiveWorkoutViewModel.hasLiveSession) {
            weightLiveWorkoutViewModel.setLiveWorkoutUiExpanded(true)
            onOpenWeightCategory()
            return
        }
        val segmentIndex = activeRun?.position?.segmentIndex ?: 0
        val segmentId = workout?.segments?.getOrNull(segmentIndex)?.id
        val started = weightLiveWorkoutViewModel.tryStartFromWorkoutWeightItems(
            items = items,
            library = weightState,
            sessionLabel = label,
            suppressNotification = true,
        )
        if (started) {
            heartRateBle.resetWorkoutRecordingOnLiveStart()
            scope.launch {
                if (segmentId != null) {
                    repository.setLastLaunchedItems(segmentId, items.map { it.id })
                }
                weightLiveWorkoutViewModel.setLiveWorkoutUiExpanded(true)
                onOpenWeightCategory()
            }
        }
    }

    fun proceedToWeightBatch(items: List<WorkoutItem.Weight>, label: String) {
        if (!fgsDisclosureSeen) {
            pendingWeightBatch = items
            return
        }
        launchWeightBatchInternal(items, label)
    }

    fun launchWeightSection() {
        val current = workout ?: return
        val pos = activeRun?.position ?: WorkoutRunPosition()
        val sectionRun = WorkoutRunEngine.consecutiveWeightItemRun(current, pos)
        when {
            sectionRun.size > 1 -> proceedToWeightBatch(sectionRun, current.name)
            sectionRun.size == 1 -> launchWeightItem(sectionRun.first(), current.name)
        }
    }

    fun launchCardioItem(item: WorkoutItem.Cardio) {
        if (weightLiveWorkoutViewModel.hasLiveSession) {
            scope.launch {
                snackbarHostState.showSnackbar("Finish or cancel your live weight workout first.")
            }
            return
        }
        if (cardioLiveWorkoutViewModel.hasActiveTimer) {
            cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(true)
            onOpenCardioCategory()
            return
        }
        val session = item.resolveCardioLaunch(cardioState, workout?.name) ?: run {
            scope.launch {
                snackbarHostState.showSnackbar("This cardio step is missing a valid activity or interval setup.")
            }
            return
        }
        if (!cardioLiveWorkoutViewModel.tryStartSession(session, suppressNotification = true)) {
            scope.launch {
                snackbarHostState.showSnackbar("Finish or cancel your cardio timer first.")
            }
            return
        }
        heartRateBle.resetWorkoutRecordingOnLiveStart()
        val segmentIndex = activeRun?.position?.segmentIndex ?: 0
        val cardioSegmentId = workout?.segments?.getOrNull(segmentIndex)?.id
        scope.launch {
            if (cardioSegmentId != null) {
                repository.setLastLaunchedItem(cardioSegmentId, item.id)
            }
            cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(true)
            onOpenCardioCategory()
        }
    }

    fun launchMobilityItem(item: WorkoutItem.Mobility) {
        val payload = item.resolveStretchLaunch() ?: run {
            scope.launch {
                snackbarHostState.showSnackbar("This stretch step is missing a catalog pose.")
            }
            return
        }
        scope.launch {
            val segmentIndex = activeRun?.position?.segmentIndex ?: 0
            workout?.segments?.getOrNull(segmentIndex)?.id?.let { segmentId ->
                repository.setLastLaunchedItem(segmentId, item.id)
            }
            userPreferences.setProgramDashboardStretchLaunchJson(encodeStretchLaunch(payload))
            onOpenStretchCategory()
        }
    }

    val alternativeChoice = pendingAlternativeChoice
    if (alternativeChoice != null) {
        val (item, label) = alternativeChoice
        AlertDialog(
            onDismissRequest = { pendingAlternativeChoice = null },
            title = { Text("Choose exercise") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Pick which movement to log for this step.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val options = buildList {
                        add(item.exerciseId)
                        addAll(item.alternativeExerciseIds)
                    }
                    options.forEach { exerciseId ->
                        TextButton(
                            onClick = {
                                pendingAlternativeChoice = null
                                proceedToWeightLaunch(item.copy(exerciseId = exerciseId), label)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                weightState.exerciseById(exerciseId)?.name ?: exerciseId,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingAlternativeChoice = null }) { Text("Cancel") }
            },
        )
    }

    WeightLiveWorkoutFgsDisclosureDialog(
        visible = (
            pendingWeightItem != null ||
                pendingCircuitSegment != null ||
                pendingWeightBatch != null
            ) && !fgsDisclosureSeen,
        onDismiss = {
            pendingWeightItem = null
            pendingWeightLabel = null
            pendingCircuitSegment = null
            pendingWeightBatch = null
        },
        onContinue = {
            scope.launch { userPreferences.setWeightLiveWorkoutFgsDisclosureSeen(true) }
            val item = pendingWeightItem
            val label = pendingWeightLabel
            val circuit = pendingCircuitSegment
            val batch = pendingWeightBatch
            pendingWeightItem = null
            pendingWeightLabel = null
            pendingCircuitSegment = null
            pendingWeightBatch = null
            when {
                circuit != null -> launchCircuitSegment(circuit)
                batch != null -> launchWeightBatchInternal(batch, workout?.name.orEmpty())
                item != null -> launchWeightItemInternal(item, label ?: workout?.name.orEmpty())
            }
        },
    )

    if (workout == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Workout") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Text("Workout not found.", modifier = Modifier.padding(padding).padding(24.dp))
        }
        return
    }

    val position = activeRun?.position ?: WorkoutRunPosition()
    val runStarted = activeRun?.isStarted() == true
    val currentStep = remember(workout, position) { WorkoutRunEngine.currentStep(workout, position) }
    val isComplete = runStarted && (
        WorkoutRunEngine.isWorkoutComplete(workout, position) ||
            currentStep?.isComplete == true
    )
    val isResting = activeRest != null

    // Finalize once the run completes, regardless of how completion was reached: stamp the
    // continuous whole-workout HR onto each linked section log and build the finish summary.
    LaunchedEffect(isComplete) {
        if (isComplete && !runFinalized) {
            runFinalized = true
            val run = repository.currentState().activeRun
            val wholeRunHeartRate = heartRateBle.takeComposedWorkoutRunHeartRateSummary()
            if (run != null) {
                if (wholeRunHeartRate != null) {
                    attachComposedWorkoutHeartRateToLinkedLogs(
                        run = run,
                        heartRate = wholeRunHeartRate,
                        cardioRepository = cardioRepository,
                        weightRepository = weightRepository,
                        stretchingRepository = stretchingRepository,
                    )
                }
                composedSummary = buildComposedWorkoutHrSummary(
                    run = run,
                    wholeRun = wholeRunHeartRate,
                    cardioRepository = cardioRepository,
                    weightRepository = weightRepository,
                    stretchingRepository = stretchingRepository,
                )
            }
        }
    }

    val summary = composedSummary
    if (summary != null) {
        ComposedWorkoutSummaryScreen(
            summary = summary,
            weightState = weightState,
            loadUnit = loadUnit,
            distanceUnit = distanceUnit,
            zoneInputs = heartRateZoneInputs,
            relayPool = relayPool,
            signer = signer,
            onDone = {
                finishingRun = true
                scope.launch {
                    heartRateBle.discardComposedWorkoutRunRecording()
                    repository.clearActiveRun()
                    onBack()
                }
            },
        )
        return
    }

    suspend fun applyAdvance(from: WorkoutRunPosition) {
        val next = WorkoutRunEngine.advance(workout, from)
        repository.updateRunPosition(next)
    }

    fun startRest(from: WorkoutRunPosition, seconds: Int, label: String) {
        if (seconds <= 0) {
            scope.launch { applyAdvance(from) }
            return
        }
        activeRest = ActiveWorkoutRest(
            advanceFrom = from,
            secondsRemaining = seconds,
            label = label,
        )
    }

    fun finishCurrentStep(from: WorkoutRunPosition) {
        val pending = WorkoutRunEngine.pendingRestBeforeAdvance(workout, from)
        if (pending != null) {
            startRest(from, pending.seconds, pending.label)
        } else {
            scope.launch { applyAdvance(from) }
        }
    }

    // Launch the silo screen for the current step. Shared by continuous auto-advance and by the
    // section-transition splash once its countdown reaches zero.
    fun launchCurrentStepSilo() {
        if (!runStarted || isResting || isComplete) return
        if (weightLiveWorkoutViewModel.hasLiveSession || cardioLiveWorkoutViewModel.hasActiveTimer) return
        val step = currentStep ?: return
        val segment = step.segment
        if (segment.kind == WorkoutSegmentKind.CIRCUIT || segment.kind == WorkoutSegmentKind.SUPERSET) {
            proceedToCircuitLaunch(segment)
            return
        }
        when (val item = step.item) {
            is WorkoutItem.Weight -> launchWeightSection()
            is WorkoutItem.Cardio -> launchCardioItem(item)
            is WorkoutItem.Mobility -> launchMobilityItem(item)
            else -> Unit
        }
    }

    // Drive the between-section splash countdown, then continue into the next section.
    LaunchedEffect(sectionTransition?.secondsRemaining) {
        val transition = sectionTransition ?: return@LaunchedEffect
        if (transition.secondsRemaining <= 0) {
            val shouldAutoAdvance = transition.autoAdvance
            sectionTransition = null
            if (shouldAutoAdvance) launchCurrentStepSilo()
            return@LaunchedEffect
        }
        delay(1_000L)
        sectionTransition = transition.copy(secondsRemaining = transition.secondsRemaining - 1)
    }

    LaunchedEffect(activeRest?.advanceFrom, activeRest?.secondsRemaining) {
        val rest = activeRest ?: return@LaunchedEffect
        if (rest.secondsRemaining <= 0) {
            val from = rest.advanceFrom
            activeRest = null
            applyAdvance(from)
            return@LaunchedEffect
        }
        delay(1_000L)
        activeRest = rest.copy(secondsRemaining = rest.secondsRemaining - 1)
    }

    LaunchedEffect(
        position.segmentIndex,
        position.itemIndex,
        position.round,
        currentStep?.item?.id,
        runStarted,
        isResting,
    ) {
        if (!runStarted || isResting || isComplete) return@LaunchedEffect
        val duration = WorkoutRunEngine.restDurationForItem(currentStep?.item) ?: return@LaunchedEffect
        startRest(position, duration, "Rest")
    }

    LaunchedEffect(
        activeRun?.autoAdvanceRequested,
        activeRun?.pendingNextSegmentTitle,
        position.segmentIndex,
        position.itemIndex,
        position.round,
    ) {
        if (activeRun?.autoAdvanceRequested != true) return@LaunchedEffect
        // Section boundaries are handled by the transition splash, which launches the next
        // section when its countdown finishes; skip the instant auto-advance in that case.
        if (activeRun.pendingNextSegmentTitle != null || sectionTransition != null) {
            return@LaunchedEffect
        }
        repository.clearAutoAdvance()
        launchCurrentStepSilo()
    }

    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(workout.name) },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            activeRest = null
                            heartRateBle.discardComposedWorkoutRunRecording()
                            repository.clearActiveRun()
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ErvHeaderRed,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = when {
                        !runStarted -> "Ready to start"
                        isComplete -> "Workout complete"
                        isResting -> activeRest!!.label
                        else -> currentStep?.label ?: "Ready"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!runStarted) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = workout.name,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "${workout.segments.size} section(s). Start begins the overall workout clock and heart-rate recording.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        repository.beginRun(workoutId)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Start Workout")
                            }
                        }
                    }
                }
            }
            if (isResting) {
                item {
                    WorkoutRestCountdownCard(
                        label = activeRest!!.label,
                        secondsRemaining = activeRest!!.secondsRemaining,
                        onSkip = {
                            val from = activeRest!!.advanceFrom
                            activeRest = null
                            scope.launch { applyAdvance(from) }
                        },
                    )
                }
            }
            val stepItem = currentStep?.item
            val circuitSegment = currentStep?.segment?.takeIf { segment ->
                segment.kind == WorkoutSegmentKind.CIRCUIT || segment.kind == WorkoutSegmentKind.SUPERSET
            }
            val circuitSessionActive = liveWeightDraft?.circuitRun?.segmentId == circuitSegment?.id
            if (runStarted && !isComplete && !isResting && circuitSegment != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = circuitSegment.title
                                    ?: defaultWorkoutSegmentKindLabel(circuitSegment.kind),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = buildString {
                                    append("${circuitSegment.rounds} rounds")
                                    append(" · ${circuitSegment.weightItems().size} exercises")
                                    if (circuitSegment.restPolicy.restBetweenItemsSeconds > 0) {
                                        append(" · ${circuitSegment.restPolicy.restBetweenItemsSeconds}s between exercises")
                                    }
                                    if (circuitSegment.restPolicy.restAfterRoundSeconds > 0) {
                                        append(" · ${circuitSegment.restPolicy.restAfterRoundSeconds}s between rounds")
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            circuitSegment.weightItems().forEach { item ->
                                val name = weightState.exerciseById(item.exerciseId)?.name ?: item.exerciseId
                                Text("· $name (${item.prescription.displaySummary()})", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = {
                                    if (circuitSessionActive) {
                                        weightLiveWorkoutViewModel.setLiveWorkoutUiExpanded(true)
                                        onOpenWeightCategory()
                                    } else {
                                        proceedToCircuitLaunch(circuitSegment)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (circuitSessionActive) "Resume circuit" else "Start circuit")
                            }
                        }
                    }
                }
            }
            if (runStarted && !isComplete && !isResting && stepItem is WorkoutItem.Weight && circuitSegment == null) {
                val weightItem = stepItem
                val exerciseName = weightState.exerciseById(weightItem.exerciseId)?.name
                item {
                    val sectionRun = remember(workout, position) {
                        WorkoutRunEngine.consecutiveWeightItemRun(workout, position)
                    }
                    val extraInSection = (sectionRun.size - 1).coerceAtLeast(0)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = weightItem.displayExerciseName(exerciseName),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = weightItem.prescription.displaySummary(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (extraInSection > 0) {
                                Text(
                                    text = "+$extraInSection more in this section",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(
                                onClick = { launchWeightSection() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (extraInSection > 0) "Log section" else "Log sets")
                            }
                        }
                    }
                }
            }
            if (runStarted && !isComplete && !isResting && stepItem is WorkoutItem.Note) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Coach note",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stepItem.text.ifBlank { "—" },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Button(
                                onClick = { finishCurrentStep(position) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Continue")
                            }
                        }
                    }
                }
            }
            if (runStarted && !isComplete && !isResting && stepItem is WorkoutItem.Cardio) {
                val cardioItem = stepItem
                item {
                    val cardioErgSnapshot = remember(cardioItem.cardio.activity) {
                        runCatching { CardioBuiltinActivity.valueOf(cardioItem.cardio.activity) }.getOrNull()
                            ?.let { builtin -> cardioState.resolveSnapshot(builtin, null) }
                    }
                    val cardioSupportsErg = cardioErgSnapshot?.supportsBikeErgSensorConnect() ?: false
                    val cardioCyclingApplicable = cardioErgSnapshot?.isCyclingActivity() ?: false
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = cardioItem.title?.takeIf { it.isNotBlank() } ?: "Cardio",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = cardioItem.displaySummary(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (cardioSupportsErg) {
                                CardioBikeErgConnectInlineSection(
                                    activitySupportsErg = true,
                                    sessionKey = cardioItem.id,
                                    compact = true,
                                    cyclingSensorApplicable = cardioCyclingApplicable,
                                )
                            }
                            Button(
                                onClick = { launchCardioItem(cardioItem) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Start timer")
                            }
                        }
                    }
                }
            }
            if (runStarted && !isComplete && !isResting && stepItem is WorkoutItem.Mobility) {
                val mobilityItem = stepItem
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = mobilityItem.title?.takeIf { it.isNotBlank() } ?: "Mobility",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = mobilityItem.displaySummary(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(
                                onClick = { launchMobilityItem(mobilityItem) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Start stretch")
                            }
                        }
                    }
                }
            }
            if (runStarted && !isComplete && !isResting && stepItem is WorkoutItem.Rest) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Rest",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "${stepItem.durationSeconds}s",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Button(
                                onClick = { finishCurrentStep(position) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Continue")
                            }
                        }
                    }
                }
            }
            item {
                FormSectionLabelSmall("Storyboard")
            }
            itemsIndexed(workout.segments, key = { _, segment -> segment.id }) { index, segment ->
                val isCurrent = runStarted && index == position.segmentIndex && !isComplete && !isResting
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = (segment.title ?: defaultWorkoutSegmentKindLabel(segment.kind)) +
                                if (isCurrent) " · now" else "",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (segment.kind == WorkoutSegmentKind.CIRCUIT ||
                            segment.kind == WorkoutSegmentKind.SUPERSET
                        ) {
                            Text(
                                text = buildString {
                                    append("${segment.rounds} rounds")
                                    if (segment.restPolicy.restBetweenItemsSeconds > 0) {
                                        append(" · ${segment.restPolicy.restBetweenItemsSeconds}s between exercises")
                                    }
                                    if (segment.restPolicy.restAfterRoundSeconds > 0) {
                                        append(" · ${segment.restPolicy.restAfterRoundSeconds}s between rounds")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        segment.restAfterSeconds?.takeIf { it > 0 }?.let { seconds ->
                            Text(
                                text = "${seconds}s rest after segment",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (segment.kind == WorkoutSegmentKind.CIRCUIT ||
                            segment.kind == WorkoutSegmentKind.SUPERSET
                        ) {
                            segment.weightItems().forEach { item ->
                                val name = weightState.exerciseById(item.exerciseId)?.name ?: item.exerciseId
                                Text("· $name", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            segment.items.forEachIndexed { itemIndex, item ->
                                val isCurrentItem = isCurrent && itemIndex == position.itemIndex
                                val prefix = if (isCurrentItem) "▸ " else "· "
                                when (item) {
                                    is WorkoutItem.Weight -> {
                                        val name = weightState.exerciseById(item.exerciseId)?.name
                                            ?: item.exerciseId
                                        Text(
                                            text = prefix + name + " (${item.prescription.displaySummary()})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isCurrentItem) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    }
                                    is WorkoutItem.Cardio -> Text(
                                        text = prefix + item.displaySummary(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCurrentItem) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    is WorkoutItem.Mobility -> Text(
                                        text = prefix + item.displaySummary(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCurrentItem) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    is WorkoutItem.Rest -> Text(
                                        text = prefix + "Rest ${item.durationSeconds}s",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCurrentItem) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    is WorkoutItem.Note -> Text(
                                        text = prefix + "Note: " + item.text.take(48).ifBlank { "…" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCurrentItem) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (isComplete) {
                // The completion summary is shown automatically once finalize runs; this is a
                // fallback to leave the run if the summary could not be built.
                item {
                    TextButton(
                        onClick = {
                            finishingRun = true
                            scope.launch {
                                heartRateBle.discardComposedWorkoutRunRecording()
                                repository.clearActiveRun()
                                onBack()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Finish")
                    }
                }
            }
        }
    }
    sectionTransition?.let { transition ->
        WorkoutSectionTransitionSplash(
            finishedTitle = transition.finishedTitle,
            nextTitle = transition.nextTitle,
            secondsRemaining = transition.secondsRemaining,
            onStartNow = {
                sectionTransition = transition.copy(secondsRemaining = 0)
            },
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposedWorkoutSummaryScreen(
    summary: ComposedWorkoutHrSummary,
    weightState: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    distanceUnit: CardioDistanceUnit,
    zoneInputs: HeartRateZoneInputs,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var sharing by remember { mutableStateOf(false) }
    var shared by remember { mutableStateOf(false) }
    var sharePersonalMessage by remember { mutableStateOf("") }
    var shareHashtags by remember {
        mutableStateOf(buildWorkoutShareHashtagContentLineFromTopics(workoutShareBaseTopicTags))
    }
    val loadSuffix = weightLoadUnitSuffix(loadUnit)
    val weightSessions = summary.sections.mapNotNull { it.weightSession }
    val totalSets = weightSessions.sumOf { it.totalSetCount() }
    val totalVolume = weightSessions.sumOf { it.totalVolumeLoadTimesReps(loadUnit) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout complete") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ErvHeaderRed,
                    titleContentColor = Color.White,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(summary.workoutName, style = MaterialTheme.typography.headlineSmall)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        summary.totalElapsedSeconds?.takeIf { it > 0 }?.let { seconds ->
                            Text(
                                text = "Total time  ${seconds / 60}:${"%02d".format(seconds % 60)}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        if (summary.sectionCount > 0) {
                            Text(
                                text = "${summary.sectionCount} section(s) completed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (weightSessions.isNotEmpty()) {
                            Text(
                                text = buildString {
                                    append("${weightSessions.sumOf { it.entries.size }} exercises • $totalSets sets")
                                    if (totalVolume > 0.5) {
                                        append(" • Volume ~${totalVolume.toInt()} ${loadSuffix}×reps")
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        summary.wholeRun?.let { hr ->
                            Text(
                                text = "Heart rate  avg ${hr.avgBpm} · max ${hr.maxBpm} bpm",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (summary.wholeRun != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Heart rate", style = MaterialTheme.typography.titleMedium)
                            HeartRateSessionAnalyticsSection(
                                heartRate = summary.wholeRun,
                                zoneInputs = zoneInputs,
                                useLightOnDarkBackground = false,
                                sectionMarkers = summary.sectionChartMarkers(),
                            )
                        }
                    }
                }
            } else if (!summary.hasAnyHeartRate) {
                item {
                    Text(
                        "No heart rate was recorded for this workout. Connect a heart rate monitor " +
                            "before your next session to see effort analysis.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (summary.sections.isNotEmpty()) {
                item {
                    FormSectionLabel("Workout log")
                }
                summary.sections.forEach { section ->
                    item(key = "section_log_${section.title}_${section.kind}") {
                        ComposedWorkoutSectionLogCard(
                            section = section,
                            weightState = weightState,
                            loadUnit = loadUnit,
                            loadSuffix = loadSuffix,
                            distanceUnit = distanceUnit,
                        )
                    }
                }
            }
            if (relayPool != null && signer != null) {
                item {
                    OutlinedTextField(
                        value = sharePersonalMessage,
                        onValueChange = { sharePersonalMessage = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !sharing && !shared,
                        label = { Text("Add a note (optional)") },
                        minLines = 2,
                        maxLines = 5,
                    )
                }
                item {
                    OutlinedTextField(
                        value = shareHashtags,
                        onValueChange = { shareHashtags = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !sharing && !shared,
                        label = { Text("Hashtags") },
                        minLines = 1,
                        maxLines = 3,
                    )
                }
                item {
                    OutlinedButton(
                        onClick = {
                            if (sharing || shared) return@OutlinedButton
                            sharing = true
                            scope.launch {
                                val ok = publishComposedWorkoutNote(
                                    relayPool = relayPool,
                                    signer = signer,
                                    summary = summary,
                                    personalMessage = sharePersonalMessage,
                                    hashtagsInput = shareHashtags,
                                )
                                sharing = false
                                shared = ok
                                snackbarHostState.showSnackbar(
                                    if (ok) "Shared to your relays!" else "Failed to share — check relay connection",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !sharing && !shared,
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                sharing -> "Sharing…"
                                shared -> "Shared"
                                else -> "Share workout"
                            },
                        )
                    }
                }
            }
            item {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun ComposedWorkoutSectionLogCard(
    section: ComposedWorkoutHrSection,
    weightState: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    loadSuffix: String,
    distanceUnit: CardioDistanceUnit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            Text(
                section.kind.summaryLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                section.weightSession != null -> {
                    section.weightSession.entries.forEach { entry ->
                        val exercise = weightState.exerciseById(entry.exerciseId)
                        val addedLoad = exercise?.equipment == WeightEquipment.OTHER
                        Text(
                            "• ${exercise?.name ?: entry.exerciseId}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        entry.hiitBlock?.let { block ->
                            Text(
                                formatHiitBlockSummaryLine(block, loadUnit, loadSuffix, addedLoad),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        } ?: entry.sets.forEachIndexed { idx, set ->
                            Text(
                                formatSetSummaryLine(
                                    set = set,
                                    setNumber = idx + 1,
                                    loadUnit = loadUnit,
                                    loadSuffix = loadSuffix,
                                    weightIsAddedLoad = addedLoad,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
                section.cardioSession != null -> {
                    val session = section.cardioSession
                    Text(
                        session.activity.displayLabel,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        buildString {
                            append("${session.durationMinutes} min")
                            session.distanceMeters?.takeIf { it > 1.0 }?.let { meters ->
                                append(" • ${formatCardioDistanceFromMeters(meters, distanceUnit)}")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    formatCardioAveragePaceForSession(session, distanceUnit, null)?.let { pace ->
                        Text(
                            "Avg pace: $pace",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                section.stretchSession != null -> {
                    val session = section.stretchSession
                    Text(
                        buildString {
                            append("${session.totalMinutes} min")
                            session.routineName?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                            if (session.routineName.isNullOrBlank() && session.stretchIds.isNotEmpty()) {
                                append(" • ${session.stretchIds.size} stretches")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(
                        "No logged details for this section.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutSectionTransitionSplash(
    finishedTitle: String,
    nextTitle: String,
    secondsRemaining: Int,
    onStartNow: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ErvHeaderRed),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "$finishedTitle finished",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Next section",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(
                text = nextTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = secondsRemaining.coerceAtLeast(0).toString(),
                style = MaterialTheme.typography.displayLarge,
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = "Starts in ${secondsRemaining.coerceAtLeast(0)}s",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onStartNow) {
                Text("Start now", color = Color.White)
            }
        }
    }
}
