package com.erv.app.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.erv.app.data.UserPreferences
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
import com.erv.app.workouts.WorkoutSegmentKind
import com.erv.app.workouts.displayExerciseName
import com.erv.app.workouts.displaySummary
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.programs.encodeStretchLaunch
import com.erv.app.ui.cardio.CardioLiveWorkoutViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLiveRunScreen(
    workoutId: String,
    repository: WorkoutRepository,
    activeRun: WorkoutActiveRun?,
    workout: Workout?,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    userPreferences: UserPreferences,
    weightLiveWorkoutViewModel: WeightLiveWorkoutViewModel,
    cardioLiveWorkoutViewModel: CardioLiveWorkoutViewModel,
    onBack: () -> Unit,
    onOpenWeightCategory: () -> Unit,
    onOpenCardioCategory: () -> Unit,
    onOpenStretchCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val fgsDisclosureSeen by userPreferences.weightLiveWorkoutFgsDisclosureSeen.collectAsState(initial = false)
    var pendingWeightItem by remember { mutableStateOf<WorkoutItem.Weight?>(null) }
    var pendingWeightLabel by remember { mutableStateOf<String?>(null) }
    var pendingAlternativeChoice by remember {
        mutableStateOf<Pair<WorkoutItem.Weight, String>?>(null)
    }
    var activeRest by remember { mutableStateOf<ActiveWorkoutRest?>(null) }

    LaunchedEffect(workoutId, workout) {
        if (workout != null && (activeRun == null || activeRun.workoutId != workoutId)) {
            repository.startRun(workoutId)
        }
    }

    fun launchWeightItemInternal(item: WorkoutItem.Weight, label: String) {
        if (weightLiveWorkoutViewModel.hasLiveSession) {
            weightLiveWorkoutViewModel.setLiveWorkoutUiExpanded(true)
            onOpenWeightCategory()
            return
        }
        val started = weightLiveWorkoutViewModel.tryStartFromWorkoutPrescription(
            exerciseId = item.exerciseId,
            prescription = item.prescription,
            library = weightState,
            sessionLabel = label,
            suppressNotification = true,
        )
        if (started) {
            weightLiveWorkoutViewModel.setLiveWorkoutUiExpanded(true)
            onOpenWeightCategory()
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
        cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(true)
        onOpenCardioCategory()
    }

    fun launchMobilityItem(item: WorkoutItem.Mobility) {
        val payload = item.resolveStretchLaunch() ?: run {
            scope.launch {
                snackbarHostState.showSnackbar("This stretch step is missing a catalog pose.")
            }
            return
        }
        scope.launch {
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
        visible = pendingWeightItem != null && !fgsDisclosureSeen,
        onDismiss = {
            pendingWeightItem = null
            pendingWeightLabel = null
        },
        onContinue = {
            scope.launch { userPreferences.setWeightLiveWorkoutFgsDisclosureSeen(true) }
            val item = pendingWeightItem
            val label = pendingWeightLabel
            pendingWeightItem = null
            pendingWeightLabel = null
            if (item != null) {
                launchWeightItemInternal(item, label ?: workout?.name.orEmpty())
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
    val currentStep = remember(workout, position) { WorkoutRunEngine.currentStep(workout, position) }
    val isComplete = WorkoutRunEngine.isWorkoutComplete(workout, position) ||
        currentStep?.isComplete == true
    val isResting = activeRest != null

    suspend fun applyAdvance(from: WorkoutRunPosition) {
        val next = WorkoutRunEngine.advance(workout, from)
        repository.updateRunPosition(next)
        if (WorkoutRunEngine.isWorkoutComplete(workout, next)) {
            repository.clearActiveRun()
        }
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
        isResting,
    ) {
        if (isResting || isComplete) return@LaunchedEffect
        val duration = WorkoutRunEngine.restDurationForItem(currentStep?.item) ?: return@LaunchedEffect
        startRest(position, duration, "Rest")
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(workout.name) },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            activeRest = null
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
                        isComplete -> "Workout complete"
                        isResting -> activeRest!!.label
                        else -> currentStep?.label ?: "Ready"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
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
            if (!isComplete && !isResting && stepItem is WorkoutItem.Weight) {
                val weightItem = stepItem
                val exerciseName = weightState.exerciseById(weightItem.exerciseId)?.name
                item {
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
                            Button(
                                onClick = {
                                    launchWeightItem(weightItem, workout.name)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Log sets")
                            }
                            Button(
                                onClick = { finishCurrentStep(position) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Text("Done — next")
                            }
                        }
                    }
                }
            }
            if (!isComplete && !isResting && stepItem is WorkoutItem.Note) {
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
            if (!isComplete && !isResting && stepItem is WorkoutItem.Cardio) {
                val cardioItem = stepItem
                item {
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
                            Button(
                                onClick = { launchCardioItem(cardioItem) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Start timer")
                            }
                            Button(
                                onClick = { finishCurrentStep(position) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Text("Done — next")
                            }
                        }
                    }
                }
            }
            if (!isComplete && !isResting && stepItem is WorkoutItem.Mobility) {
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
                            Button(
                                onClick = { finishCurrentStep(position) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Text("Done — next")
                            }
                        }
                    }
                }
            }
            if (!isComplete && !isResting && stepItem is WorkoutItem.Rest) {
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
                Text("Storyboard", style = MaterialTheme.typography.titleSmall)
            }
            itemsIndexed(workout.segments, key = { _, segment -> segment.id }) { index, segment ->
                val isCurrent = index == position.segmentIndex && !isComplete && !isResting
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
                item {
                    TextButton(
                        onClick = {
                            scope.launch {
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
}
