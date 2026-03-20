package com.erv.app.ui.weighttraining

// Equipment uses FilterChips only — no MenuAnchorType / ExposedDropdownMenu (avoids Material3 API drift).

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightPushPull
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightRoutine
import com.erv.app.weighttraining.WeightSync
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.exercisesGroupedByMuscle
import com.erv.app.weighttraining.formatMuscleGroupHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.launch

private enum class WeightTrainingTab { Exercises, Routines }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightTrainingCategoryScreen(
    selectedDate: LocalDate,
    repository: WeightRepository,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by repository.state.collectAsState(initial = WeightLibraryState())
    val dateLabel = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeTab by rememberSaveable { mutableStateOf(WeightTrainingTab.Exercises.name) }
    val tabEnum = WeightTrainingTab.entries.firstOrNull { it.name == activeTab } ?: WeightTrainingTab.Exercises

    var exerciseEditorTarget by remember { mutableStateOf<WeightExercise?>(null) }
    var showExerciseCreator by remember { mutableStateOf(false) }
    var exercisePendingDelete by remember { mutableStateOf<WeightExercise?>(null) }

    var routineBeingEdited by remember { mutableStateOf<WeightRoutine?>(null) }
    var routinePendingDelete by remember { mutableStateOf<WeightRoutine?>(null) }

    val darkTheme = isSystemInDarkTheme()
    val headerDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark
    val headerMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid

    suspend fun pushMasters() {
        if (relayPool == null || signer == null) return
        val s = repository.currentState()
        WeightSync.publishExercises(relayPool, signer, s.exercises)
        WeightSync.publishRoutines(relayPool, signer, s.routines)
    }

    LaunchedEffect(relayPool, signer?.publicKey) {
        if (relayPool != null && signer != null) {
            WeightSync.fetchFromNetwork(relayPool, signer, signer.publicKey)?.let { remote ->
                repository.replaceAll(remote)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Weight Training")
                        Text(
                            text = "Dashboard: $dateLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        floatingActionButton = {
            when (tabEnum) {
                WeightTrainingTab.Exercises -> {
                    ExtendedFloatingActionButton(
                        onClick = {
                            showExerciseCreator = true
                            exerciseEditorTarget = null
                        },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("Add exercise") }
                    )
                }
                WeightTrainingTab.Routines -> {
                    ExtendedFloatingActionButton(
                        onClick = {
                            routineBeingEdited = WeightRoutine(
                                id = UUID.randomUUID().toString(),
                                name = "",
                                exerciseIds = emptyList()
                            )
                        },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("Add routine") }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = tabEnum.ordinal,
                containerColor = headerDark,
                contentColor = Color.White
            ) {
                WeightTrainingTab.entries.forEach { tab ->
                    Tab(
                        selected = tabEnum == tab,
                        onClick = { activeTab = tab.name },
                        text = { Text(tab.name) }
                    )
                }
            }

            when (tabEnum) {
                WeightTrainingTab.Exercises -> ExercisesTabBody(
                    grouped = state.exercisesGroupedByMuscle(),
                    state = state,
                    onEdit = { exerciseEditorTarget = it },
                    onDeleteRequest = { exercisePendingDelete = it }
                )

                WeightTrainingTab.Routines -> RoutinesTabBody(
                    state = state,
                    onEdit = { routineBeingEdited = it },
                    onDeleteRequest = { routinePendingDelete = it }
                )
            }
        }
    }

    exercisePendingDelete?.let { ex ->
        AlertDialog(
            onDismissRequest = { exercisePendingDelete = null },
            title = { Text("Delete exercise?") },
            text = { Text("Remove “${ex.name}” from your library? Routines that include it may need editing.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteExercise(ex.id)
                            pushMasters()
                            snackbarHostState.showSnackbar("Exercise removed")
                        }
                        exercisePendingDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { exercisePendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    routinePendingDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { routinePendingDelete = null },
            title = { Text("Delete routine?") },
            text = { Text("Remove “${r.name}”?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteRoutine(r.id)
                            pushMasters()
                            snackbarHostState.showSnackbar("Routine removed")
                        }
                        routinePendingDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { routinePendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showExerciseCreator || exerciseEditorTarget != null) {
        key(exerciseEditorTarget?.id, showExerciseCreator) {
            ExerciseEditorDialog(
                initial = exerciseEditorTarget,
                title = if (exerciseEditorTarget == null) "Add exercise" else "Edit exercise",
                onDismiss = {
                    showExerciseCreator = false
                    exerciseEditorTarget = null
                },
                onSave = { draft ->
                    scope.launch {
                        repository.upsertExercise(draft)
                        pushMasters()
                        snackbarHostState.showSnackbar(if (exerciseEditorTarget == null) "Exercise added" else "Exercise updated")
                    }
                    showExerciseCreator = false
                    exerciseEditorTarget = null
                }
            )
        }
    }

    val routineDraft = routineBeingEdited
    if (routineDraft != null) {
        key(routineDraft.id) {
            RoutineEditorDialog(
                initial = routineDraft,
                exerciseLibrary = state.exercises.sortedBy { it.name.lowercase() },
                title = if (state.routines.none { it.id == routineDraft.id }) {
                    "New routine"
                } else {
                    "Edit routine"
                },
                onDismiss = { routineBeingEdited = null },
                onSave = { routine ->
                    scope.launch {
                        repository.upsertRoutine(routine)
                        pushMasters()
                        snackbarHostState.showSnackbar("Routine saved")
                    }
                    routineBeingEdited = null
                }
            )
        }
    }
}

@Composable
private fun ExercisesTabBody(
    grouped: List<Pair<String, List<WeightExercise>>>,
    state: WeightLibraryState,
    onEdit: (WeightExercise) -> Unit,
    onDeleteRequest: (WeightExercise) -> Unit
) {
    if (grouped.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "No exercises yet. Add one with the button below.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        grouped.forEach { (muscleKey, list) ->
            items(
                items = listOf(muscleKey),
                key = { k -> "mg_$k" }
            ) { key ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Text(
                        formatMuscleGroupHeader(key),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            items(list, key = { it.id }) { exercise ->
                val inRoutines = state.routines.count { r -> exercise.id in r.exerciseIds }
                ListItem(
                    headlineContent = { Text(exercise.name) },
                    supportingContent = {
                        Text(
                            "${exercise.equipment.displayLabel()} · ${exercise.pushOrPull.displayLabel()}" +
                                if (inRoutines > 0) " · Used in $inRoutines routine(s)" else ""
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onEdit(exercise) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { onDeleteRequest(exercise) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RoutinesTabBody(
    state: WeightLibraryState,
    onEdit: (WeightRoutine) -> Unit,
    onDeleteRequest: (WeightRoutine) -> Unit
) {
    if (state.routines.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "No routines yet. Build a template with the button below.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.routines.sortedBy { it.name.lowercase() }, key = { it.id }) { routine ->
            val preview = routine.exerciseIds.mapNotNull { id -> state.exerciseById(id)?.name }
                .take(4)
                .joinToString(" → ")
            ListItem(
                headlineContent = { Text(routine.name) },
                supportingContent = {
                    Text(
                        if (preview.isNotEmpty()) "$preview${if (routine.exerciseIds.size > 4) "…" else ""}" else "No exercises — tap edit to add"
                    )
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { onEdit(routine) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { onDeleteRequest(routine) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ExerciseEditorDialog(
    initial: WeightExercise?,
    title: String,
    onDismiss: () -> Unit,
    onSave: (WeightExercise) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var muscleGroup by remember(initial?.id) { mutableStateOf(initial?.muscleGroup.orEmpty()) }
    var pushOrPull by remember(initial?.id) { mutableStateOf(initial?.pushOrPull ?: WeightPushPull.PUSH) }
    var equipment by remember(initial?.id) { mutableStateOf(initial?.equipment ?: WeightEquipment.BARBELL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = muscleGroup,
                    onValueChange = { muscleGroup = it },
                    label = { Text("Muscle group") },
                    supportingText = { Text("e.g. chest, back, legs, or a custom label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = pushOrPull == WeightPushPull.PUSH,
                        onClick = { pushOrPull = WeightPushPull.PUSH },
                        label = { Text("Push") }
                    )
                    FilterChip(
                        selected = pushOrPull == WeightPushPull.PULL,
                        onClick = { pushOrPull = WeightPushPull.PULL },
                        label = { Text("Pull") }
                    )
                }
                Text("Equipment", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WeightEquipment.entries.chunked(3).forEach { rowOpts ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowOpts.forEach { opt ->
                                FilterChip(
                                    selected = equipment == opt,
                                    onClick = { equipment = opt },
                                    label = { Text(opt.displayLabel()) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || muscleGroup.isBlank()) return@Button
                    onSave(
                        WeightExercise(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            muscleGroup = muscleGroup.trim().lowercase(),
                            pushOrPull = pushOrPull,
                            equipment = equipment
                        )
                    )
                },
                enabled = name.isNotBlank() && muscleGroup.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RoutineEditorDialog(
    initial: WeightRoutine,
    exerciseLibrary: List<WeightExercise>,
    title: String,
    onDismiss: () -> Unit,
    onSave: (WeightRoutine) -> Unit
) {
    var routineName by remember(initial.id) { mutableStateOf(initial.name) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes.orEmpty()) }
    var exerciseIds by remember(initial.id) { mutableStateOf(initial.exerciseIds.toMutableList()) }
    var showPickExercise by remember { mutableStateOf(false) }

    if (showPickExercise) {
        PickExerciseDialog(
            exercises = exerciseLibrary,
            excludeIds = exerciseIds.toSet(),
            onDismiss = { showPickExercise = false },
            onPick = { id ->
                exerciseIds = exerciseIds.toMutableList().also { it.add(id) }
                showPickExercise = false
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = routineName,
                    onValueChange = { routineName = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text("Exercises (order)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showPickExercise = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Text("Add exercise from library")
                }
                Spacer(Modifier.height(8.dp))
                exerciseIds.forEachIndexed { index, id ->
                    val ex = exerciseLibrary.firstOrNull { it.id == id }
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ex?.name ?: "Unknown id", style = MaterialTheme.typography.bodyLarge)
                                ex?.let {
                                    Text(
                                        "${it.muscleGroup} · ${it.equipment.displayLabel()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val m = exerciseIds.toMutableList()
                                        val t = m[index]
                                        m[index] = m[index - 1]
                                        m[index - 1] = t
                                        exerciseIds = m
                                    }
                                },
                                enabled = index > 0
                            ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Up") }
                            IconButton(
                                onClick = {
                                    if (index < exerciseIds.lastIndex) {
                                        val m = exerciseIds.toMutableList()
                                        val t = m[index]
                                        m[index] = m[index + 1]
                                        m[index + 1] = t
                                        exerciseIds = m
                                    }
                                },
                                enabled = index < exerciseIds.lastIndex
                            ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Down") }
                            IconButton(
                                onClick = {
                                    exerciseIds = exerciseIds.toMutableList().also { it.removeAt(index) }
                                }
                            ) { Icon(Icons.Default.Close, contentDescription = "Remove") }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Button(
                        onClick = {
                            if (routineName.isBlank()) return@Button
                            onSave(
                                WeightRoutine(
                                    id = initial.id,
                                    name = routineName.trim(),
                                    exerciseIds = exerciseIds.toList(),
                                    notes = notes.trim().ifBlank { null }
                                )
                            )
                        },
                        enabled = routineName.isNotBlank()
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun PickExerciseDialog(
    exercises: List<WeightExercise>,
    excludeIds: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val choices = exercises.filter { it.id !in excludeIds }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to routine") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (choices.isEmpty()) {
                    Text(
                        "All exercises are already in this routine.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    choices.forEach { ex ->
                        TextButton(
                            onClick = { onPick(ex.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${ex.name} (${ex.muscleGroup})", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
