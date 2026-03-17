package com.erv.app.ui.supplements

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.supplements.SupplementApiClient
import com.erv.app.supplements.SupplementApiResult
import com.erv.app.supplements.SupplementDayLog
import com.erv.app.supplements.SupplementEntry
import com.erv.app.supplements.SupplementLibraryState
import com.erv.app.supplements.SupplementRepository
import com.erv.app.supplements.SupplementRoutine
import com.erv.app.supplements.SupplementRoutineStep
import com.erv.app.supplements.SupplementSync
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class SupplementsTab { Routines, Supplements, Log }

private data class RoutineStepDraft(
    val supplementId: String? = null,
    val dosageOverride: String = "",
    val note: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementCategoryScreen(
    repository: SupplementRepository,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onOpenSupplementDetail: (String) -> Unit
) {
    val state by repository.state.collectAsState(initial = SupplementLibraryState())
    val today = remember { LocalDate.now() }
    val todaySummary = remember(state, today) { state.summaryFor(today) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeTab by rememberSaveable { mutableIntStateOf(0) }
    var supplementEditor by remember { mutableStateOf<SupplementEntry?>(null) }
    var creatingSupplement by remember { mutableStateOf(false) }
    var routineEditor by remember { mutableStateOf<SupplementRoutine?>(null) }
    var creatingRoutine by remember { mutableStateOf(false) }

    suspend fun syncMaster() {
        if (relayPool != null && signer != null) {
            SupplementSync.publishMaster(relayPool, signer, repository.currentState())
        }
    }

    suspend fun syncDailyLog(log: SupplementDayLog) {
        if (relayPool != null && signer != null) {
            SupplementSync.publishDailyLog(relayPool, signer, log)
        }
    }

    LaunchedEffect(relayPool, signer?.publicKey) {
        if (relayPool != null && signer != null) {
            SupplementSync.fetchFromNetwork(relayPool, signer, signer.publicKey)?.let { remote ->
                repository.replaceAll(remote)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Supplements") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = activeTab) {
                SupplementsTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        text = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            when (SupplementsTab.entries[activeTab]) {
                SupplementsTab.Routines -> RoutinesTab(
                    state = state,
                    onAddClick = {
                        creatingRoutine = true
                        routineEditor = null
                    },
                    onEditRoutine = { routineEditor = it },
                    onDeleteRoutine = { routineId ->
                        scope.launch {
                            repository.deleteRoutine(routineId)
                            syncMaster()
                        }
                    },
                    onRunRoutine = { routine ->
                        scope.launch {
                            repository.logRoutineRun(today, routine.id)
                            repository.currentState().logFor(today)?.let { syncDailyLog(it) }
                            snackbarHostState.showSnackbar("Logged ${routine.name}")
                        }
                    },
                    onCreateRoutine = { name, steps, notes ->
                        scope.launch {
                            repository.addRoutine(name, steps, notes)
                            syncMaster()
                            snackbarHostState.showSnackbar("Routine saved")
                        }
                    },
                    onUpdateRoutine = { id, name, steps, notes ->
                        scope.launch {
                            repository.renameRoutine(id, name, steps, notes)
                            syncMaster()
                            snackbarHostState.showSnackbar("Routine updated")
                        }
                    },
                    routineEditor = routineEditor,
                    creatingRoutine = creatingRoutine,
                    onDismissRoutineEditor = {
                        routineEditor = null
                        creatingRoutine = false
                    },
                    onResetRoutineEditorMode = { creatingRoutine = false },
                    supplements = state.supplements,
                )

                SupplementsTab.Supplements -> SupplementsTabContent(
                    state = state,
                    onAddSupplement = {
                        creatingSupplement = true
                        supplementEditor = null
                    },
                    onEditSupplement = { supplementEditor = it },
                    onDeleteSupplement = { supplementId ->
                        scope.launch {
                            repository.deleteSupplement(supplementId)
                            syncMaster()
                        }
                    },
                    onOpenDetail = onOpenSupplementDetail,
                    onTakeNow = { supplement ->
                        scope.launch {
                            repository.logAdHocIntake(today, supplement.id)
                            repository.currentState().logFor(today)?.let { syncDailyLog(it) }
                            snackbarHostState.showSnackbar("Logged ${supplement.name}")
                        }
                    },
                    supplementEditor = supplementEditor,
                    creatingSupplement = creatingSupplement,
                    onDismissSupplementEditor = {
                        supplementEditor = null
                        creatingSupplement = false
                    },
                    onResetSupplementEditorMode = { creatingSupplement = false },
                    onCreateSupplement = { name, dosage, frequency, whenToTake, notes ->
                        scope.launch {
                            repository.addSupplement(name, dosage, frequency, whenToTake, notes)
                            syncMaster()
                            snackbarHostState.showSnackbar("Supplement saved")
                        }
                    },
                    onUpdateSupplement = { id, name, dosage, frequency, whenToTake, notes ->
                        scope.launch {
                            repository.renameSupplement(id, name, dosage, frequency, whenToTake, notes)
                            syncMaster()
                            snackbarHostState.showSnackbar("Supplement updated")
                        }
                    }
                )

                SupplementsTab.Log -> LogTab(
                    state = state,
                    today = today,
                    summary = todaySummary
                )
            }
        }
    }
}

@Composable
private fun RoutinesTab(
    state: SupplementLibraryState,
    supplements: List<SupplementEntry>,
    onAddClick: () -> Unit,
    onEditRoutine: (SupplementRoutine) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onRunRoutine: (SupplementRoutine) -> Unit,
    onCreateRoutine: (String, List<SupplementRoutineStep>, String) -> Unit,
    onUpdateRoutine: (String, String, List<SupplementRoutineStep>, String) -> Unit,
    routineEditor: SupplementRoutine?,
    creatingRoutine: Boolean,
    onDismissRoutineEditor: () -> Unit,
    onResetRoutineEditorMode: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.routines.isEmpty()) {
            EmptyState(
                title = "No routines yet",
                subtitle = "Create morning, night, or custom supplement routines."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.routines, key = { it.id }) { routine ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(routine.name, style = MaterialTheme.typography.titleMedium)
                            if (routine.notes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    routine.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            routine.steps.forEachIndexed { index, step ->
                                val supplement = supplements.firstOrNull { it.id == step.supplementId }
                                Text(
                                    "${index + 1}. ${supplement?.name ?: "Missing supplement"}" +
                                        (step.dosageOverride?.let { " • $it" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { onRunRoutine(routine) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Run")
                                }
                                OutlinedButton(onClick = { onEditRoutine(routine) }) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Edit")
                                }
                                OutlinedButton(onClick = { onDeleteRoutine(routine.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add routine")
        }
    }

    if (creatingRoutine || routineEditor != null) {
        RoutineEditorDialog(
            routine = routineEditor,
            creating = creatingRoutine,
            supplements = supplements,
            onDismiss = onDismissRoutineEditor,
            onDismissReset = onResetRoutineEditorMode,
            onSave = { id, name, steps, notes ->
                if (id == null) onCreateRoutine(name, steps, notes) else onUpdateRoutine(id, name, steps, notes)
                onResetRoutineEditorMode()
                onDismissRoutineEditor()
            }
        )
    }
}

@Composable
private fun SupplementsTabContent(
    state: SupplementLibraryState,
    onAddSupplement: () -> Unit,
    onEditSupplement: (SupplementEntry) -> Unit,
    onDeleteSupplement: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onTakeNow: (SupplementEntry) -> Unit,
    supplementEditor: SupplementEntry?,
    creatingSupplement: Boolean,
    onDismissSupplementEditor: () -> Unit,
    onResetSupplementEditorMode: () -> Unit,
    onCreateSupplement: (String, String, String, String, String) -> Unit,
    onUpdateSupplement: (String, String, String, String, String, String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.supplements.isEmpty()) {
            EmptyState(
                title = "No supplements yet",
                subtitle = "Add your first supplement and link an NIH info page."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.supplements, key = { it.id }) { supplement ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenDetail(supplement.id) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(supplement.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${supplement.dosage} • ${supplement.frequency} • ${supplement.whenToTake}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (supplement.info != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    supplement.info.brand ?: supplement.info.productName ?: "API info cached",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (supplement.notes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    supplement.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { onTakeNow(supplement) }) {
                                    Text("Take now")
                                }
                                OutlinedButton(onClick = { onEditSupplement(supplement) }) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Edit")
                                }
                                OutlinedButton(onClick = { onDeleteSupplement(supplement.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddSupplement,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add supplement")
        }
    }

    if (creatingSupplement || supplementEditor != null) {
        SupplementEditorDialog(
            supplement = supplementEditor,
            creating = creatingSupplement,
            onDismiss = onDismissSupplementEditor,
            onDismissReset = onResetSupplementEditorMode,
            onSave = { id, name, dosage, frequency, whenToTake, notes ->
                if (id == null) onCreateSupplement(name, dosage, frequency, whenToTake, notes)
                else onUpdateSupplement(id, name, dosage, frequency, whenToTake, notes)
                onResetSupplementEditorMode()
                onDismissSupplementEditor()
            }
        )
    }
}

@Composable
private fun LogTab(
    state: SupplementLibraryState,
    today: LocalDate,
    summary: com.erv.app.supplements.SupplementDaySummary
) {
    val log = state.logFor(today)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Today", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (summary.uniqueSupplementCount == 0) "No supplements logged yet"
                        else "${summary.routineCount} routine run(s), ${summary.adHocCount} ad hoc intake(s)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (summary.routineNames.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            summary.routineNames.joinToString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (log == null) {
            item {
                EmptyState(
                    title = "No log for today",
                    subtitle = "Run a routine or tap Take now on a supplement."
                )
            }
        } else {
            if (log.routineRuns.isNotEmpty()) {
                item {
                    Text("Routine runs", style = MaterialTheme.typography.titleMedium)
                }
                items(log.routineRuns, key = { it.id }) { run ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(run.routineName, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            run.stepIntakes.forEach {
                                Text(
                                    "• ${it.supplementName} ${it.dosageTaken ?: ""}".trim(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            if (log.adHocIntakes.isNotEmpty()) {
                item {
                    Text("Ad hoc intakes", style = MaterialTheme.typography.titleMedium)
                }
                items(log.adHocIntakes) { intake ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(intake.supplementName, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                intake.dosageTaken ?: "No dosage recorded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplementEditorDialog(
    supplement: SupplementEntry?,
    creating: Boolean,
    onDismiss: () -> Unit,
    onDismissReset: () -> Unit,
    onSave: (String?, String, String, String, String, String) -> Unit
) {
    var name by remember(supplement?.id, creating) { mutableStateOf(supplement?.name.orEmpty()) }
    var dosage by remember(supplement?.id, creating) { mutableStateOf(supplement?.dosage.orEmpty()) }
    var frequency by remember(supplement?.id, creating) { mutableStateOf(supplement?.frequency.orEmpty()) }
    var whenToTake by remember(supplement?.id, creating) { mutableStateOf(supplement?.whenToTake.orEmpty()) }
    var notes by remember(supplement?.id, creating) { mutableStateOf(supplement?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = { onDismissReset(); onDismiss() },
        title = { Text(if (creating) "Add supplement" else "Edit supplement") },
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
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dosage") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = frequency,
                    onValueChange = { frequency = it },
                    label = { Text("Frequency") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = whenToTake,
                    onValueChange = { whenToTake = it },
                    label = { Text("When to take") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(supplement?.id, name.trim(), dosage.trim(), frequency.trim(), whenToTake.trim(), notes.trim())
                },
                enabled = name.isNotBlank() && dosage.isNotBlank() && frequency.isNotBlank() && whenToTake.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismissReset(); onDismiss() }) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineEditorDialog(
    routine: SupplementRoutine?,
    creating: Boolean,
    supplements: List<SupplementEntry>,
    onDismiss: () -> Unit,
    onDismissReset: () -> Unit,
    onSave: (String?, String, List<SupplementRoutineStep>, String) -> Unit
) {
    var name by remember(routine?.id, creating) { mutableStateOf(routine?.name.orEmpty()) }
    var notes by remember(routine?.id, creating) { mutableStateOf(routine?.notes.orEmpty()) }
    val steps = remember(routine?.id) {
        mutableStateListOf<RoutineStepDraft>().apply {
            if (routine?.steps.isNullOrEmpty()) {
                add(RoutineStepDraft())
            } else {
                routine!!.steps.forEach { add(RoutineStepDraft(it.supplementId, it.dosageOverride.orEmpty(), it.note.orEmpty())) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { onDismissReset(); onDismiss() },
        title = { Text(if (creating) "Add routine" else "Edit routine") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Steps", style = MaterialTheme.typography.titleSmall)
                steps.forEachIndexed { index, step ->
                    RoutineStepRow(
                        step = step,
                        supplements = supplements,
                        onStepChange = { updated -> steps[index] = updated },
                        onRemove = { if (steps.size > 1) steps.removeAt(index) }
                    )
                }
                TextButton(onClick = { steps.add(RoutineStepDraft()) }) {
                    Text("Add step")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        routine?.id,
                        name.trim(),
                        steps.mapNotNull { draft ->
                            val supplementId = draft.supplementId ?: return@mapNotNull null
                            SupplementRoutineStep(
                                supplementId = supplementId,
                                dosageOverride = draft.dosageOverride.trim().ifBlank { null },
                                note = draft.note.trim().ifBlank { null }
                            )
                        },
                        notes.trim()
                    )
                },
                enabled = name.isNotBlank() && steps.any { it.supplementId != null }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismissReset(); onDismiss() }) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineStepRow(
    step: RoutineStepDraft,
    supplements: List<SupplementEntry>,
    onStepChange: (RoutineStepDraft) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedSupplement = supplements.firstOrNull { it.id == step.supplementId }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedSupplement?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Supplement") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                supplements.forEach { supplement ->
                    DropdownMenuItem(
                        text = { Text(supplement.name) },
                        onClick = {
                            expanded = false
                            onStepChange(step.copy(supplementId = supplement.id))
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = step.dosageOverride,
            onValueChange = { onStepChange(step.copy(dosageOverride = it)) },
            label = { Text("Dosage override (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = step.note,
            onValueChange = { onStepChange(step.copy(note = it)) },
            label = { Text("Step note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        TextButton(
            onClick = onRemove,
            enabled = supplements.size > 1
        ) {
            Text("Remove step")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementDetailScreen(
    repository: SupplementRepository,
    relayPool: RelayPool?,
    signer: EventSigner?,
    supplementId: String,
    onBack: () -> Unit
) {
    val state by repository.state.collectAsState(initial = SupplementLibraryState())
    val supplement = state.supplementById(supplementId)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val apiClient = remember { SupplementApiClient() }
    var query by rememberSaveable(supplementId) { mutableStateOf(supplement?.name.orEmpty()) }
    var apiResults by remember { mutableStateOf<List<SupplementApiResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    suspend fun syncMaster() {
        if (relayPool != null && signer != null) {
            SupplementSync.publishMaster(relayPool, signer, repository.currentState())
        }
    }

    if (supplement == null) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Supplement") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Supplement not found")
            }
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(supplement.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(supplement.name, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${supplement.dosage} • ${supplement.frequency} • ${supplement.whenToTake}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (supplement.notes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(supplement.notes, style = MaterialTheme.typography.bodySmall)
                        }
                        if (supplement.productId != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Product ID: ${supplement.productId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("NIH info", style = MaterialTheme.typography.titleMedium)
                        if (supplement.info == null) {
                            Text(
                                "No cached info yet. Search the NIH DSLD database and save a result here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            KeyValueRow("Brand", supplement.info.brand)
                            KeyValueRow("Product", supplement.info.productName)
                            KeyValueRow("Serving size", supplement.info.servingSize)
                            KeyValueRow("Suggested use", supplement.info.suggestedUse)
                            KeyValueRow("Claims", supplement.info.claimsOrUses.joinToString())
                            KeyValueRow("Form", supplement.info.supplementForm.joinToString())
                            KeyValueRow("Target group", supplement.info.targetGroup.joinToString())
                            KeyValueRow("Ingredients", supplement.info.ingredients.joinToString())
                            KeyValueRow("Other ingredients", supplement.info.otherIngredients)
                        }
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Lookup from NIH DSLD", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Search query") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    searching = true
                                    try {
                                        apiResults = apiClient.search(query)
                                        if (apiResults.isEmpty()) {
                                            snackbarHostState.showSnackbar("No API results found")
                                        }
                                    } finally {
                                        searching = false
                                    }
                                }
                            },
                            enabled = query.isNotBlank() && !searching,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (searching) "Searching…" else "Search")
                        }
                    }
                }
            }

            if (apiResults.isNotEmpty()) {
                item {
                    Text("Results", style = MaterialTheme.typography.titleMedium)
                }
                items(apiResults, key = { it.productId }) { result ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                repository.attachInfo(supplement.id, result.info, result.productId)
                                syncMaster()
                                snackbarHostState.showSnackbar("Saved ${result.productName}")
                            }
                        }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(result.productName, style = MaterialTheme.typography.titleSmall)
                            if (!result.brand.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    result.brand,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            val routinesUsingSupplement = state.routines.filter { routine ->
                routine.steps.any { it.supplementId == supplement.id }
            }

            if (routinesUsingSupplement.isNotEmpty()) {
                item {
                    Text("Used in routines", style = MaterialTheme.typography.titleMedium)
                }
                items(routinesUsingSupplement, key = { it.id }) { routine ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(routine.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                routine.steps.joinToString { step ->
                                    val name = state.supplementById(step.supplementId)?.name ?: "Missing"
                                    if (step.dosageOverride.isNullOrBlank()) name else "$name (${step.dosageOverride})"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

