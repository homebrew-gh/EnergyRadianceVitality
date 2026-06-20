package com.erv.app.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.erv.app.ui.components.FieldLabel
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.stretching.StretchCatalogEntry
import com.erv.app.ui.stretching.StretchPickStretchDialog
import com.erv.app.ui.theme.ErvHeaderRed
import com.erv.app.ui.weighttraining.WeightPickExerciseDialog
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.WeightSetLoggingStyle
import com.erv.app.weighttraining.setLoggingStyle
import com.erv.app.workouts.defaultWorkoutPrescriptionForExercise
import com.erv.app.workouts.defaultWorkoutSegment
import com.erv.app.workouts.newWorkoutCardioItem
import com.erv.app.workouts.newWorkoutMobilityItem
import com.erv.app.workouts.supportsFullItemEditor
import com.erv.app.workouts.ensureSetRows
import com.erv.app.workouts.usesPerSideReps
import com.erv.app.workouts.Workout
import com.erv.app.workouts.WorkoutItem
import com.erv.app.workouts.WorkoutRepository
import com.erv.app.workouts.WorkoutRestPolicy
import com.erv.app.workouts.WorkoutSegment
import com.erv.app.workouts.WorkoutSegmentKind
import com.erv.app.workouts.WorkoutWeightPrescription
import com.erv.app.workouts.WorkoutWeightPrescriptionMode
import com.erv.app.workouts.displaySummary
import com.erv.app.workouts.moveItemDown
import com.erv.app.workouts.moveItemUp
import com.erv.app.workouts.removeItemAt
import com.erv.app.workouts.updateItemAt
import com.erv.app.workouts.weightItems
import com.erv.app.workouts.WorkoutSync
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutComposerScreen(
    existing: Workout?,
    repository: WorkoutRepository,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    stretchCatalog: List<StretchCatalogEntry>,
    keyManager: KeyManager,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    val segments = remember(existing?.id) {
        mutableStateListOf<WorkoutSegment>().apply {
            if (existing != null) addAll(existing.segments)
        }
    }
    var addSegmentMenuExpanded by remember { mutableStateOf(false) }
    var pickExercisesForSegmentIndex by remember { mutableIntStateOf(-1) }
    var pickCardioForSegmentIndex by remember { mutableIntStateOf(-1) }
    var pickStretchForSegmentIndex by remember { mutableIntStateOf(-1) }

    if (pickExercisesForSegmentIndex >= 0 && pickExercisesForSegmentIndex < segments.size) {
        val segment = segments[pickExercisesForSegmentIndex]
        WeightPickExerciseDialog(
            exercises = weightState.exercises,
            excludeIds = segment.weightItems().map { it.exerciseId }.toSet(),
            onDismiss = { pickExercisesForSegmentIndex = -1 },
            onPick = { exerciseId ->
                val index = pickExercisesForSegmentIndex
                pickExercisesForSegmentIndex = -1
                val current = segments[index]
                val exercise = weightState.exerciseById(exerciseId)
                segments[index] = current.copy(
                    items = current.items + WorkoutItem.Weight(
                        exerciseId = exerciseId,
                        prescription = defaultWorkoutPrescriptionForExercise(exercise, current.kind),
                    ),
                )
            },
        )
    }

    if (pickCardioForSegmentIndex >= 0 && pickCardioForSegmentIndex < segments.size) {
        val segment = segments[pickCardioForSegmentIndex]
        val exclude = segment.items.filterIsInstance<WorkoutItem.Cardio>()
            .map { it.cardio.activity }
            .toSet()
        WorkoutPickCardioActivityDialog(
            excludeActivities = exclude,
            onDismiss = { pickCardioForSegmentIndex = -1 },
            onPick = { activity ->
                val index = pickCardioForSegmentIndex
                pickCardioForSegmentIndex = -1
                val current = segments[index]
                segments[index] = current.copy(
                    items = current.items + newWorkoutCardioItem(activity, current.kind),
                )
            },
        )
    }

    if (pickStretchForSegmentIndex >= 0 && pickStretchForSegmentIndex < segments.size) {
        val segment = segments[pickStretchForSegmentIndex]
        StretchPickStretchDialog(
            catalog = stretchCatalog,
            excludeIds = segment.items.filterIsInstance<WorkoutItem.Mobility>()
                .map { it.mobility.catalogId }
                .toSet(),
            onDismiss = { pickStretchForSegmentIndex = -1 },
            onPick = { catalogId ->
                val index = pickStretchForSegmentIndex
                pickStretchForSegmentIndex = -1
                val current = segments[index]
                segments[index] = current.copy(
                    items = current.items + newWorkoutMobilityItem(catalogId),
                )
            },
        )
    }

    fun persist() {
        scope.launch {
            val workout = (existing ?: Workout(name = name.ifBlank { "Workout" })).copy(
                name = name.ifBlank { "Workout" },
                segments = segments.toList(),
            )
            repository.upsertWorkout(workout)
            WorkoutSync.publishLibraryIfSignedIn(
                appContext = context.applicationContext,
                relayPool = relayPool,
                signer = signer,
                state = repository.currentState(),
                dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
            )
            onSaved(workout.id)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New workout" else "Edit workout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { persist() },
                        enabled = name.isNotBlank() && segments.isNotEmpty(),
                    ) {
                        Text("Save", color = Color.White)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { FieldLabel("Workout name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                text = "Segments (storyboard order)",
                style = MaterialTheme.typography.titleSmall,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(segments, key = { _, segment -> segment.id }) { index, segment ->
                    SegmentEditorCard(
                        segment = segment,
                        weightState = weightState,
                        cardioState = cardioState,
                        stretchCatalog = stretchCatalog,
                        onUpdate = { segments[index] = it },
                        onMoveUp = {
                            if (index > 0) {
                                val item = segments.removeAt(index)
                                segments.add(index - 1, item)
                            }
                        },
                        onMoveDown = {
                            if (index < segments.lastIndex) {
                                val item = segments.removeAt(index)
                                segments.add(index + 1, item)
                            }
                        },
                        onDelete = { segments.removeAt(index) },
                        onAddExercises = { pickExercisesForSegmentIndex = index },
                        onAddCardio = { pickCardioForSegmentIndex = index },
                        onAddStretch = { pickStretchForSegmentIndex = index },
                    )
                }
                item {
                    ExposedDropdownMenuBox(
                        expanded = addSegmentMenuExpanded,
                        onExpandedChange = { addSegmentMenuExpanded = it },
                    ) {
                        TextButton(
                            onClick = { addSegmentMenuExpanded = true },
                            modifier = Modifier.menuAnchor(),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("Add segment")
                        }
                        DropdownMenu(
                            expanded = addSegmentMenuExpanded,
                            onDismissRequest = { addSegmentMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Straight sets") },
                                onClick = {
                                    addSegmentMenuExpanded = false
                                    segments.add(defaultWorkoutSegment(WorkoutSegmentKind.STRAIGHT_SETS))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Flow block") },
                                onClick = {
                                    addSegmentMenuExpanded = false
                                    segments.add(defaultWorkoutSegment(WorkoutSegmentKind.COMPOSITE))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Cardio block") },
                                onClick = {
                                    addSegmentMenuExpanded = false
                                    segments.add(defaultWorkoutSegment(WorkoutSegmentKind.CARDIO))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Interval block") },
                                onClick = {
                                    addSegmentMenuExpanded = false
                                    segments.add(defaultWorkoutSegment(WorkoutSegmentKind.INTERVAL))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Mobility block") },
                                onClick = {
                                    addSegmentMenuExpanded = false
                                    segments.add(defaultWorkoutSegment(WorkoutSegmentKind.MOBILITY))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Circuit") },
                                onClick = {
                                    addSegmentMenuExpanded = false
                                    segments.add(defaultWorkoutSegment(WorkoutSegmentKind.CIRCUIT))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Superset") },
                                onClick = {
                                    addSegmentMenuExpanded = false
                                    segments.add(defaultWorkoutSegment(WorkoutSegmentKind.SUPERSET))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentEditorCard(
    segment: WorkoutSegment,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    stretchCatalog: List<StretchCatalogEntry>,
    onUpdate: (WorkoutSegment) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onAddExercises: () -> Unit,
    onAddCardio: () -> Unit,
    onAddStretch: () -> Unit,
) {
    var collapsed by remember { mutableStateOf(false) }
    var pickAlternativeForItemIndex by remember { mutableIntStateOf(-1) }

    if (pickAlternativeForItemIndex >= 0 && pickAlternativeForItemIndex < segment.items.size) {
        val item = segment.items[pickAlternativeForItemIndex]
        if (item is WorkoutItem.Weight) {
            WeightPickExerciseDialog(
                exercises = weightState.exercises,
                excludeIds = buildSet {
                    add(item.exerciseId)
                    addAll(item.alternativeExerciseIds)
                },
                onDismiss = { pickAlternativeForItemIndex = -1 },
                onPick = { exerciseId ->
                    val index = pickAlternativeForItemIndex
                    pickAlternativeForItemIndex = -1
                    val current = segment.items[index] as WorkoutItem.Weight
                    onUpdate(
                        segment.updateItemAt(
                            index,
                            current.copy(
                                alternativeExerciseIds = current.alternativeExerciseIds + exerciseId,
                            ),
                        ),
                    )
                },
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = defaultWorkoutSegmentKindLabel(segment.kind),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { collapsed = !collapsed }) {
                    Icon(
                        if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = if (collapsed) "Expand segment" else "Collapse segment",
                    )
                }
                IconButton(onClick = onMoveUp) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete segment")
                }
            }
            OutlinedTextField(
                value = segment.title.orEmpty(),
                onValueChange = { onUpdate(segment.copy(title = it.ifBlank { null })) },
                label = { FieldLabel("Segment title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (!collapsed) {
            if (segment.kind == WorkoutSegmentKind.CIRCUIT || segment.kind == WorkoutSegmentKind.SUPERSET) {
                OutlinedTextField(
                    value = segment.rounds.toString(),
                    onValueChange = { raw ->
                        val rounds = raw.toIntOrNull()?.coerceAtLeast(1) ?: segment.rounds
                        onUpdate(segment.copy(rounds = rounds))
                    },
                    label = { FieldLabel("Rounds") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = segment.restPolicy.restBetweenItemsSeconds.toString(),
                    onValueChange = { raw ->
                        val seconds = raw.toIntOrNull()?.coerceAtLeast(0)
                            ?: segment.restPolicy.restBetweenItemsSeconds
                        onUpdate(
                            segment.copy(
                                restPolicy = segment.restPolicy.copy(restBetweenItemsSeconds = seconds),
                            ),
                        )
                    },
                    label = { FieldLabel("Rest between exercises (seconds)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = segment.restPolicy.restAfterRoundSeconds.toString(),
                    onValueChange = { raw ->
                        val seconds = raw.toIntOrNull()?.coerceAtLeast(0) ?: segment.restPolicy.restAfterRoundSeconds
                        onUpdate(
                            segment.copy(
                                restPolicy = segment.restPolicy.copy(restAfterRoundSeconds = seconds),
                            ),
                        )
                    },
                    label = { FieldLabel("Rest after round (seconds)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = segment.restAfterSeconds?.toString().orEmpty(),
                onValueChange = { raw ->
                    val seconds = raw.toIntOrNull()?.coerceAtLeast(0)
                    onUpdate(segment.copy(restAfterSeconds = seconds))
                },
                label = { FieldLabel("Rest after segment (seconds)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (segment.kind.supportsFullItemEditor()) {
                segment.items.forEachIndexed { itemIndex, item ->
                    StraightSetsItemEditor(
                        item = item,
                        itemIndex = itemIndex,
                        itemCount = segment.items.size,
                        weightState = weightState,
                        cardioState = cardioState,
                        stretchCatalog = stretchCatalog,
                        onUpdate = { updated ->
                            onUpdate(segment.updateItemAt(itemIndex, updated))
                        },
                        onMoveUp = { onUpdate(segment.moveItemUp(itemIndex)) },
                        onMoveDown = { onUpdate(segment.moveItemDown(itemIndex)) },
                        onDelete = { onUpdate(segment.removeItemAt(itemIndex)) },
                        onAddAlternative = { pickAlternativeForItemIndex = itemIndex },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (segment.kind == WorkoutSegmentKind.STRAIGHT_SETS ||
                        segment.kind == WorkoutSegmentKind.COMPOSITE
                    ) {
                        TextButton(onClick = onAddExercises, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("Exercise")
                        }
                    }
                    if (segment.kind == WorkoutSegmentKind.COMPOSITE ||
                        segment.kind == WorkoutSegmentKind.CARDIO ||
                        segment.kind == WorkoutSegmentKind.INTERVAL
                    ) {
                        TextButton(onClick = onAddCardio, modifier = Modifier.weight(1f)) {
                            Text("Cardio")
                        }
                    }
                    if (segment.kind == WorkoutSegmentKind.COMPOSITE ||
                        segment.kind == WorkoutSegmentKind.MOBILITY
                    ) {
                        TextButton(onClick = onAddStretch, modifier = Modifier.weight(1f)) {
                            Text("Stretch")
                        }
                    }
                    if (segment.kind == WorkoutSegmentKind.STRAIGHT_SETS ||
                        segment.kind == WorkoutSegmentKind.COMPOSITE ||
                        segment.kind == WorkoutSegmentKind.CARDIO ||
                        segment.kind == WorkoutSegmentKind.MOBILITY ||
                        segment.kind == WorkoutSegmentKind.INTERVAL
                    ) {
                        TextButton(
                            onClick = {
                                onUpdate(
                                    segment.copy(
                                        items = segment.items + WorkoutItem.Rest(durationSeconds = 60),
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Rest")
                        }
                        TextButton(
                            onClick = {
                                onUpdate(
                                    segment.copy(
                                        items = segment.items + WorkoutItem.Note(text = ""),
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Note")
                        }
                    }
                }
            } else {
                segment.weightItems().forEach { item ->
                    val exerciseName = weightState.exerciseById(item.exerciseId)?.name ?: item.exerciseId
                    Text(
                        text = "· $exerciseName (${item.prescription.displaySummary()})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = onAddExercises) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add exercises")
                }
            }
            }
        }
    }
}

@Composable
private fun StraightSetsItemEditor(
    item: WorkoutItem,
    itemIndex: Int,
    itemCount: Int,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    stretchCatalog: List<StretchCatalogEntry>,
    onUpdate: (WorkoutItem) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onAddAlternative: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (item) {
                        is WorkoutItem.Weight -> weightState.exerciseById(item.exerciseId)?.name
                            ?: item.exerciseId
                        is WorkoutItem.Rest -> "Rest"
                        is WorkoutItem.Note -> "Coach note"
                        is WorkoutItem.Cardio -> item.title?.takeIf { it.isNotBlank() } ?: "Cardio"
                        is WorkoutItem.Mobility -> item.title?.takeIf { it.isNotBlank() } ?: "Mobility"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onMoveUp, enabled = itemIndex > 0) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = itemIndex < itemCount - 1) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete item")
                }
            }
            when (item) {
                is WorkoutItem.Weight -> {
                    val loggingStyle =
                        weightState.exerciseById(item.exerciseId)?.setLoggingStyle()
                            ?: WeightSetLoggingStyle.REPS
                    WeightPrescriptionFields(
                        prescription = item.prescription,
                        loggingStyle = loggingStyle,
                        onUpdate = { onUpdate(item.copy(prescription = it)) },
                    )
                    if (item.alternativeExerciseIds.isNotEmpty()) {
                        Text(
                            text = "Alternatives: " + item.alternativeExerciseIds.joinToString(", ") { altId ->
                                weightState.exerciseById(altId)?.name ?: altId
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            item.alternativeExerciseIds.forEach { altId ->
                                TextButton(
                                    onClick = {
                                        onUpdate(
                                            item.copy(
                                                alternativeExerciseIds = item.alternativeExerciseIds - altId,
                                            ),
                                        )
                                    },
                                ) {
                                    Text("Remove ${weightState.exerciseById(altId)?.name ?: altId}")
                                }
                            }
                        }
                    }
                    TextButton(onClick = onAddAlternative) {
                        Text("Add alternative exercise")
                    }
                }
                is WorkoutItem.Rest -> OutlinedTextField(
                    value = item.durationSeconds.toString(),
                    onValueChange = { raw ->
                        val seconds = raw.toIntOrNull()?.coerceAtLeast(1) ?: item.durationSeconds
                        onUpdate(item.copy(durationSeconds = seconds))
                    },
                    label = { FieldLabel("Duration (seconds)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                is WorkoutItem.Note -> OutlinedTextField(
                    value = item.text,
                    onValueChange = { onUpdate(item.copy(text = it)) },
                    label = { FieldLabel("Note text") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                is WorkoutItem.Cardio -> CardioPrescriptionEditor(
                    prescription = item.cardio,
                    cardioState = cardioState,
                    onUpdate = { onUpdate(item.copy(cardio = it)) },
                )
                is WorkoutItem.Mobility -> {
                    val stretchName = stretchCatalog.firstOrNull { it.id == item.mobility.catalogId }?.name
                    Text(
                        text = stretchName ?: item.mobility.catalogId,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    MobilityPrescriptionEditor(
                        mobility = item.mobility,
                        onUpdate = { onUpdate(item.copy(mobility = it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WeightPrescriptionFields(
    prescription: WorkoutWeightPrescription,
    loggingStyle: WeightSetLoggingStyle = WeightSetLoggingStyle.REPS,
    onUpdate: (WorkoutWeightPrescription) -> Unit,
) {
    var showPerSetEditor by remember { mutableStateOf(prescription.sets.isNotEmpty()) }
    val timedPrescription = loggingStyle == WeightSetLoggingStyle.TIME_ONLY ||
        (loggingStyle == WeightSetLoggingStyle.REPS_OR_TIME &&
            prescription.mode == WorkoutWeightPrescriptionMode.TIME_BASED)
    val isMaxReps = prescription.mode == WorkoutWeightPrescriptionMode.MAX_REPS
    val perSide = prescription.usesPerSideReps()

    if (loggingStyle != WeightSetLoggingStyle.TIME_ONLY) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !isMaxReps && prescription.mode != WorkoutWeightPrescriptionMode.TIME_BASED,
                onClick = {
                    onUpdate(
                        prescription.copy(
                            mode = WorkoutWeightPrescriptionMode.STRAIGHT,
                            durationSeconds = null,
                        ),
                    )
                },
                label = { FieldLabel("Straight") },
            )
            FilterChip(
                selected = isMaxReps,
                onClick = {
                    onUpdate(
                        prescription.copy(
                            mode = WorkoutWeightPrescriptionMode.MAX_REPS,
                            targetReps = null,
                            repRangeMin = null,
                            repRangeMax = null,
                            durationSeconds = null,
                        ),
                    )
                },
                label = { FieldLabel("Max reps") },
            )
            if (loggingStyle == WeightSetLoggingStyle.REPS_OR_TIME) {
                FilterChip(
                    selected = prescription.mode == WorkoutWeightPrescriptionMode.TIME_BASED,
                    onClick = {
                        onUpdate(
                            prescription.copy(
                                mode = WorkoutWeightPrescriptionMode.TIME_BASED,
                                targetReps = null,
                                repRangeMin = null,
                                repRangeMax = null,
                                durationSeconds = prescription.durationSeconds ?: 45,
                            ),
                        )
                    },
                    label = { FieldLabel("Time") },
                )
            }
        }
    } else if (loggingStyle == WeightSetLoggingStyle.REPS_OR_TIME) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = prescription.mode != WorkoutWeightPrescriptionMode.TIME_BASED,
                onClick = {
                    onUpdate(
                        prescription.copy(
                            mode = WorkoutWeightPrescriptionMode.STRAIGHT,
                            durationSeconds = null,
                        ),
                    )
                },
                label = { FieldLabel("Reps") },
            )
            FilterChip(
                selected = prescription.mode == WorkoutWeightPrescriptionMode.TIME_BASED,
                onClick = {
                    onUpdate(
                        prescription.copy(
                            mode = WorkoutWeightPrescriptionMode.TIME_BASED,
                            targetReps = null,
                            repRangeMin = null,
                            repRangeMax = null,
                            durationSeconds = prescription.durationSeconds ?: 45,
                        ),
                    )
                },
                label = { FieldLabel("Time") },
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = prescription.setCount?.toString().orEmpty(),
            onValueChange = { raw ->
                val count = raw.toIntOrNull()?.coerceAtLeast(1) ?: return@OutlinedTextField
                val sets = prescription.ensureSetRows().take(count).toMutableList()
                while (sets.size < count) sets.add(WeightSet(reps = 0))
                onUpdate(prescription.copy(setCount = count, sets = sets.take(count)))
            },
            label = { FieldLabel("Sets") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        if (timedPrescription) {
            OutlinedTextField(
                value = prescription.durationSeconds?.toString().orEmpty(),
                onValueChange = { raw ->
                    val seconds = raw.toIntOrNull()?.coerceAtLeast(1)
                    onUpdate(
                        prescription.copy(
                            durationSeconds = seconds,
                            mode = WorkoutWeightPrescriptionMode.TIME_BASED,
                        ),
                    )
                },
                label = {
                    Text(
                        if (loggingStyle == WeightSetLoggingStyle.TIME_ONLY) {
                            "Target duration (s)"
                        } else {
                            "Target hold (s)"
                        },
                    )
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        } else if (!isMaxReps && !perSide && !showPerSetEditor) {
            OutlinedTextField(
                value = prescription.targetReps?.toString().orEmpty(),
                onValueChange = { raw ->
                    val reps = raw.toIntOrNull()?.coerceAtLeast(1)
                    onUpdate(prescription.copy(targetReps = reps))
                },
                label = { FieldLabel("Target reps") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        if (!timedPrescription && !isMaxReps && !perSide && !showPerSetEditor) {
            OutlinedTextField(
                value = prescription.repRangeMin?.toString().orEmpty(),
                onValueChange = { raw ->
                    val reps = raw.toIntOrNull()?.coerceAtLeast(1)
                    onUpdate(prescription.copy(repRangeMin = reps))
                },
                label = { FieldLabel("Reps min") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = prescription.repRangeMax?.toString().orEmpty(),
                onValueChange = { raw ->
                    val reps = raw.toIntOrNull()?.coerceAtLeast(1)
                    onUpdate(prescription.copy(repRangeMax = reps))
                },
                label = { FieldLabel("Reps max") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
    }
    if (!timedPrescription && !isMaxReps) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = perSide,
                onClick = {
                    if (perSide) {
                        onUpdate(
                            prescription.copy(
                                sets = prescription.sets.map {
                                    it.copy(repsPerSide = null, side = null)
                                },
                            ),
                        )
                    } else {
                        val sets = prescription.ensureSetRows().toMutableList()
                        val first = sets.firstOrNull() ?: WeightSet(reps = 0)
                        sets[0] = first.copy(
                            repsPerSide = first.repsPerSide ?: prescription.targetReps ?: 10,
                            side = first.side ?: "each",
                        )
                        onUpdate(prescription.copy(sets = sets, setCount = sets.size))
                    }
                },
                label = { FieldLabel("Per-side reps") },
            )
            if (!perSide) {
                TextButton(onClick = { showPerSetEditor = !showPerSetEditor }) {
                    Text(if (showPerSetEditor) "Hide per-set editor" else "Edit individual sets")
                }
            }
        }
    }
    if (perSide) {
        val first = prescription.sets.firstOrNull() ?: WeightSet(reps = 0)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = first.repsPerSide?.toString().orEmpty(),
                onValueChange = { raw ->
                    val reps = raw.toIntOrNull()?.coerceAtLeast(1) ?: return@OutlinedTextField
                    val sets = prescription.ensureSetRows().toMutableList()
                    sets[0] = (sets.firstOrNull() ?: WeightSet(reps = 0)).copy(
                        repsPerSide = reps,
                        side = sets.firstOrNull()?.side ?: "each",
                    )
                    onUpdate(prescription.copy(sets = sets))
                },
                label = { FieldLabel("Reps per side") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = first.side.orEmpty(),
                onValueChange = { raw ->
                    val sets = prescription.ensureSetRows().toMutableList()
                    sets[0] = (sets.firstOrNull() ?: WeightSet(reps = 0)).copy(
                        side = raw.ifBlank { "each" },
                    )
                    onUpdate(prescription.copy(sets = sets))
                },
                label = { FieldLabel("Side (each/left/right/alternating)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
    }
    if (showPerSetEditor && !perSide && !isMaxReps && !timedPrescription) {
        prescription.ensureSetRows().forEachIndexed { index, set ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Set ${index + 1}", modifier = Modifier.padding(top = 16.dp))
                OutlinedTextField(
                    value = (if (set.reps > 0) set.reps else set.targetReps)?.toString().orEmpty(),
                    onValueChange = { raw ->
                        val reps = raw.toIntOrNull()?.coerceAtLeast(0) ?: return@OutlinedTextField
                        val sets = prescription.ensureSetRows().toMutableList()
                        sets[index] = set.copy(reps = reps, targetReps = reps)
                        onUpdate(prescription.copy(sets = sets, setCount = sets.size))
                    },
                    label = { FieldLabel("Reps") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = set.rir?.toString().orEmpty(),
                    onValueChange = { raw ->
                        val rir = raw.toIntOrNull()?.coerceAtLeast(0)
                        val sets = prescription.ensureSetRows().toMutableList()
                        sets[index] = set.copy(rir = rir)
                        onUpdate(prescription.copy(sets = sets, setCount = sets.size))
                    },
                    label = { FieldLabel("RIR") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!isMaxReps) {
            OutlinedTextField(
                value = prescription.targetRir?.toString().orEmpty(),
                onValueChange = { raw ->
                    val rir = raw.toIntOrNull()?.coerceAtLeast(0)
                    onUpdate(prescription.copy(targetRir = rir))
                },
                label = { FieldLabel("Target RIR") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = prescription.restBetweenSetsSeconds?.toString().orEmpty(),
            onValueChange = { raw ->
                val seconds = raw.toIntOrNull()?.coerceAtLeast(0)
                onUpdate(prescription.copy(restBetweenSetsSeconds = seconds))
            },
            label = { FieldLabel("Rest between sets (s)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedTextField(
            value = prescription.restAfterExerciseSeconds?.toString().orEmpty(),
            onValueChange = { raw ->
                val seconds = raw.toIntOrNull()?.coerceAtLeast(0)
                onUpdate(prescription.copy(restAfterExerciseSeconds = seconds))
            },
            label = { FieldLabel("Rest after exercise (s)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
    }
}
