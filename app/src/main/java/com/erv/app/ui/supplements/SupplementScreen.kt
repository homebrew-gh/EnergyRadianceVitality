package com.erv.app.ui.supplements

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.reminders.RoutineReminder
import com.erv.app.reminders.RoutineReminderFrequency
import com.erv.app.reminders.RoutineReminderRepository
import com.erv.app.reminders.RoutineReminderState
import com.erv.app.reminders.RoutineReminderScheduler
import com.erv.app.supplements.SupplementApiClient
import com.erv.app.supplements.SupplementApiResult
import com.erv.app.supplements.SupplementDosagePlan
import com.erv.app.supplements.SupplementDayLog
import com.erv.app.supplements.SupplementEntry
import com.erv.app.supplements.SupplementForm
import com.erv.app.supplements.SupplementLibraryState
import com.erv.app.supplements.SupplementRepository
import com.erv.app.supplements.SupplementRoutine
import com.erv.app.supplements.SupplementRoutineStep
import com.erv.app.supplements.SupplementTimeOfDay
import com.erv.app.supplements.SupplementWeekday
import com.erv.app.supplements.SupplementUnit
import com.erv.app.supplements.describe
import com.erv.app.supplements.label
import com.erv.app.supplements.SupplementSync
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class SupplementsTab { Supplements, Routines }

private data class RoutineStepDraft(
    val supplementId: String? = null,
    val timeOfDay: SupplementTimeOfDay = SupplementTimeOfDay.MORNING,
    val quantity: String = "1",
    val note: String = ""
)

private data class RoutineReminderDraft(
    val enabled: Boolean = false,
    val hour: String = "8",
    val minute: String = "00",
    val frequency: RoutineReminderFrequency = RoutineReminderFrequency.DAILY,
    val repeatDays: Set<SupplementWeekday> = SupplementWeekday.entries.toSet()
)

private data class SupplementDraft(
    val name: String = "",
    val brand: String = "",
    val servingSize: String = "",
    val form: SupplementForm = SupplementForm.POWDER,
    val servingAmount: String = "",
    val servingUnit: SupplementUnit = SupplementUnit.MG,
    val notes: String = ""
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
    val context = LocalContext.current
    val state by repository.state.collectAsState(initial = SupplementLibraryState())
    val reminderRepository = remember(context) { RoutineReminderRepository(context) }
    val reminderState by reminderRepository.state.collectAsState(initial = RoutineReminderState())
    val today = remember { LocalDate.now() }
    val todaySummary = remember(state, today) { state.summaryFor(today) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeTab by rememberSaveable { mutableIntStateOf(0) }
    var showLogDialog by remember { mutableStateOf(false) }
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

    LaunchedEffect(reminderRepository) {
        reminderRepository.restoreAllSchedules()
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
                },
                actions = {
                    TextButton(onClick = { showLogDialog = true }) {
                        Text("Log")
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
                    onCreateSupplement = { name, brand, dosagePlan, notes ->
                        scope.launch {
                            repository.addSupplement(name, brand, dosagePlan, notes)
                            syncMaster()
                            snackbarHostState.showSnackbar("Supplement saved")
                        }
                    },
                    onUpdateSupplement = { id, name, brand, dosagePlan, notes ->
                        scope.launch {
                            repository.renameSupplement(id, name, brand, dosagePlan, notes)
                            syncMaster()
                            snackbarHostState.showSnackbar("Supplement updated")
                        }
                    }
                )

                SupplementsTab.Routines -> RoutinesTab(
                    state = state,
                    reminderForRoutine = { routineId -> reminderState.reminderForRoutine(routineId) },
                    onAddClick = {
                        creatingRoutine = true
                        routineEditor = null
                    },
                    onEditRoutine = { routineEditor = it },
                    onDeleteRoutine = { routineId ->
                        scope.launch {
                            repository.deleteRoutine(routineId)
                            reminderRepository.deleteReminder(routineId)
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
                    onCreateRoutine = { name, steps, notes, reminderDraft ->
                        scope.launch {
                            val created = repository.addRoutine(name, steps, notes)
                            val scheduled = reminderRepository.upsertReminder(reminderDraft.toReminder(created.id, created.name))
                            syncMaster()
                            snackbarHostState.showSnackbar("Routine saved")
                            if (reminderDraft.enabled && !scheduled) {
                                snackbarHostState.showSnackbar("Enable exact alarms for reminder notifications")
                            }
                        }
                    },
                    onUpdateRoutine = { id, name, steps, notes, reminderDraft ->
                        scope.launch {
                            repository.renameRoutine(id, name, steps, notes)
                            val scheduled = reminderRepository.upsertReminder(reminderDraft.toReminder(id, name))
                            syncMaster()
                            snackbarHostState.showSnackbar("Routine updated")
                            if (reminderDraft.enabled && !scheduled) {
                                snackbarHostState.showSnackbar("Enable exact alarms for reminder notifications")
                            }
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
            }
        }
    }

    if (showLogDialog) {
        SupplementLogDialog(
            state = state,
            today = today,
            summary = todaySummary,
            onDismiss = { showLogDialog = false }
        )
    }
}

@Composable
private fun RoutinesTab(
    state: SupplementLibraryState,
    supplements: List<SupplementEntry>,
    reminderForRoutine: (String) -> RoutineReminder?,
    onAddClick: () -> Unit,
    onEditRoutine: (SupplementRoutine) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onRunRoutine: (SupplementRoutine) -> Unit,
    onCreateRoutine: (String, List<SupplementRoutineStep>, String, RoutineReminderDraft) -> Unit,
    onUpdateRoutine: (String, String, List<SupplementRoutineStep>, String, RoutineReminderDraft) -> Unit,
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
                                    "${index + 1}. ${step.describe(supplement?.name ?: "Missing supplement")}",
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
            existingReminder = routineEditor?.id?.let(reminderForRoutine),
            onDismiss = onDismissRoutineEditor,
            onDismissReset = onResetRoutineEditorMode,
            onSave = { id, name, steps, notes, reminderDraft ->
                if (id == null) onCreateRoutine(name, steps, notes, reminderDraft)
                else onUpdateRoutine(id, name, steps, notes, reminderDraft)
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
    onCreateSupplement: (String, String, SupplementDosagePlan, String) -> Unit,
    onUpdateSupplement: (String, String, String, SupplementDosagePlan, String) -> Unit
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
                            if (supplement.brand.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    supplement.brand,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                supplement.dosagePlan.summary(),
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
            onSave = { id, name, brand, plan, notes ->
                if (id == null) onCreateSupplement(name, brand, plan, notes)
                else onUpdateSupplement(id, name, brand, plan, notes)
                onResetSupplementEditorMode()
                onDismissSupplementEditor()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplementLogDialog(
    state: SupplementLibraryState,
    today: LocalDate,
    summary: com.erv.app.supplements.SupplementDaySummary,
    onDismiss: () -> Unit
) {
    val log = state.logFor(today)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                if (log == null) {
                    Text(
                        "No log for today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    if (log.routineRuns.isNotEmpty()) {
                        Text("Routine runs", style = MaterialTheme.typography.titleSmall)
                        log.routineRuns.forEach { run ->
                            Text(
                                "• ${run.routineName}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (log.adHocIntakes.isNotEmpty()) {
                        Text("Recent intakes", style = MaterialTheme.typography.titleSmall)
                        log.adHocIntakes.forEach { intake ->
                            Text(
                                "• ${intake.supplementName}",
                                style = MaterialTheme.typography.bodySmall
                            )
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
    onSave: (String?, String, String, SupplementDosagePlan, String) -> Unit
) {
    var draft by remember(supplement?.id, creating) {
        mutableStateOf(supplement?.toSupplementDraft() ?: SupplementDraft())
    }
    val plan = remember(draft) { draft.toDosagePlan() }
    val isValid = remember(draft) { draft.isValid() }

    AlertDialog(
        onDismissRequest = { onDismissReset(); onDismiss() },
        title = { Text(if (creating) "Add supplement" else "Edit supplement") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.brand,
                    onValueChange = { draft = draft.copy(brand = it) },
                    label = { Text("Brand") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.servingSize,
                    onValueChange = { draft = draft.copy(servingSize = it) },
                    label = { Text("Serving size") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Serving dosage", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EnumDropdownField(
                        value = draft.form,
                        label = "Form",
                        options = SupplementForm.entries,
                        optionLabel = {
                            when (it) {
                                SupplementForm.CAPSULE -> "Capsule"
                                SupplementForm.POWDER -> "Powder"
                            }
                        },
                        onSelected = { draft = draft.copy(form = it) },
                        modifier = Modifier.weight(0.9f)
                    )
                    OutlinedTextField(
                        value = draft.servingAmount,
                        onValueChange = { draft = draft.copy(servingAmount = it) },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    EnumDropdownField(
                        value = draft.servingUnit,
                        label = "Unit",
                        options = SupplementUnit.entries,
                        optionLabel = { it.label() },
                        onSelected = { draft = draft.copy(servingUnit = it) },
                        modifier = Modifier.weight(0.9f)
                    )
                }

                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Label preview", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        val preview = plan.summary()
                        Text(
                            if (preview.isBlank()) "Fill in the serving details to see the preview." else preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { draft = draft.copy(notes = it) },
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(supplement?.id, draft.name.trim(), draft.brand.trim(), plan, draft.notes.trim())
                },
                enabled = isValid
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
    existingReminder: RoutineReminder?,
    onDismiss: () -> Unit,
    onDismissReset: () -> Unit,
    onSave: (String?, String, List<SupplementRoutineStep>, String, RoutineReminderDraft) -> Unit
) {
    val context = LocalContext.current
    var name by remember(routine?.id, creating) { mutableStateOf(routine?.name.orEmpty()) }
    var notes by remember(routine?.id, creating) { mutableStateOf(routine?.notes.orEmpty()) }
    var reminderDraft by remember(routine?.id, creating, existingReminder) {
        mutableStateOf(existingReminder?.toDraft() ?: RoutineReminderDraft())
    }
    val steps = remember(routine?.id, creating) {
        mutableStateListOf<RoutineStepDraft>().apply {
            if (routine?.steps.isNullOrEmpty()) {
                add(RoutineStepDraft())
            } else {
                routine!!.steps.forEach {
                    add(
                        RoutineStepDraft(
                            supplementId = it.supplementId,
                            timeOfDay = it.timeOfDay ?: SupplementTimeOfDay.MORNING,
                            quantity = it.quantity?.toString() ?: "1",
                            note = it.note.orEmpty()
                        )
                    )
                }
            }
        }
    }
    val timeSlots = remember { listOf(SupplementTimeOfDay.MORNING, SupplementTimeOfDay.AFTERNOON, SupplementTimeOfDay.NIGHT) }

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

                Text("Schedule", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Set the supplements you want to take in each part of the day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                timeSlots.forEach { timeOfDay ->
                    val slotSteps = steps.withIndex().filter { it.value.timeOfDay == timeOfDay }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            timeOfDay.label(),
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (slotSteps.isEmpty()) {
                            Text(
                                "No supplements added for this time yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            slotSteps.forEach { indexedStep ->
                                RoutineStepRow(
                                    step = indexedStep.value,
                                    supplements = supplements,
                                    onStepChange = { updated -> steps[indexedStep.index] = updated },
                                    onRemove = { if (steps.size > 1) steps.removeAt(indexedStep.index) }
                                )
                            }
                        }
                        TextButton(onClick = { steps.add(RoutineStepDraft(timeOfDay = timeOfDay)) }) {
                            Text("Add ${timeOfDay.label().lowercase()}")
                        }
                    }
                }

                Divider()
                Text("Reminder", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = reminderDraft.enabled,
                        onCheckedChange = { reminderDraft = reminderDraft.copy(enabled = it) }
                    )
                    Text("Enable reminder")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = reminderDraft.hour,
                        onValueChange = { reminderDraft = reminderDraft.copy(hour = it.filter { ch -> ch.isDigit() }.take(2)) },
                        label = { Text("Hour") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = reminderDraft.minute,
                        onValueChange = { reminderDraft = reminderDraft.copy(minute = it.filter { ch -> ch.isDigit() }.take(2)) },
                        label = { Text("Minute") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                EnumDropdownField(
                    value = reminderDraft.frequency,
                    label = "Repeat",
                    options = RoutineReminderFrequency.entries,
                    optionLabel = {
                        when (it) {
                            RoutineReminderFrequency.ONCE -> "Once"
                            RoutineReminderFrequency.DAILY -> "Daily"
                            RoutineReminderFrequency.WEEKLY -> "Weekly"
                            RoutineReminderFrequency.CUSTOM_DAYS -> "Specific days"
                        }
                    },
                    onSelected = { selected ->
                        reminderDraft = reminderDraft.copy(
                            frequency = selected,
                            repeatDays = if (selected == RoutineReminderFrequency.DAILY || selected == RoutineReminderFrequency.ONCE) {
                                emptySet()
                            } else reminderDraft.repeatDays.ifEmpty { SupplementWeekday.entries.toSet() }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (reminderDraft.frequency == RoutineReminderFrequency.WEEKLY || reminderDraft.frequency == RoutineReminderFrequency.CUSTOM_DAYS) {
                    Text("Select days", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SupplementWeekday.entries.forEach { weekday ->
                            FilterChip(
                                selected = weekday in reminderDraft.repeatDays,
                                onClick = {
                                    val updated = reminderDraft.repeatDays.toMutableSet()
                                    if (!updated.add(weekday)) updated.remove(weekday)
                                    reminderDraft = reminderDraft.copy(repeatDays = updated)
                                },
                                label = { Text(weekday.shortLabel()) }
                            )
                        }
                    }
                }
                if (!RoutineReminderScheduler.canScheduleExactAlarms(context)) {
                    Text(
                        "Enable exact alarms on this device to keep reminder times precise.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Allow exact alarms")
                    }
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
                                timeOfDay = draft.timeOfDay,
                                quantity = draft.quantity.toIntOrNull() ?: 1,
                                note = draft.note.trim().ifBlank { null }
                            )
                        },
                        notes.trim(),
                        reminderDraft
                    )
                },
                enabled = name.isNotBlank() && steps.any { it.supplementId != null } && reminderDraft.isValid()
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
            value = step.quantity,
            onValueChange = { onStepChange(step.copy(quantity = it.filter { ch -> ch.isDigit() })) },
            label = { Text("Quantity") },
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

private fun SupplementTimeOfDay.label(): String = when (this) {
    SupplementTimeOfDay.MORNING -> "Morning"
    SupplementTimeOfDay.AFTERNOON -> "Afternoon"
    SupplementTimeOfDay.NIGHT -> "Night"
}

private fun RoutineReminder.toDraft(): RoutineReminderDraft = RoutineReminderDraft(
    enabled = enabled,
    hour = hour.coerceIn(0, 23).toString(),
    minute = minute.coerceIn(0, 59).toString().padStart(2, '0'),
    frequency = frequency,
    repeatDays = repeatDays.toSet()
)

private fun RoutineReminderDraft.toReminder(routineId: String, routineName: String): RoutineReminder = RoutineReminder(
    routineId = routineId,
    routineName = routineName,
    enabled = enabled,
    hour = hour.toIntOrNull()?.coerceIn(0, 23) ?: 8,
    minute = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0,
    frequency = frequency,
    repeatDays = repeatDays.toList().sortedBy { it.ordinal }
)

private fun RoutineReminderDraft.isValid(): Boolean {
    if (!enabled) return true
    val hourValue = hour.toIntOrNull()
    val minuteValue = minute.toIntOrNull()
    if (hourValue !in 0..23 || minuteValue !in 0..59) return false
    if ((frequency == RoutineReminderFrequency.WEEKLY || frequency == RoutineReminderFrequency.CUSTOM_DAYS) && repeatDays.isEmpty()) return false
    return true
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
                        if (supplement.brand.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                supplement.brand,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            supplement.dosagePlan.summary(),
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
                                    step.describe(state.supplementById(step.supplementId)?.name ?: "Missing")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdownField(
    value: T,
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = optionLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

private fun SupplementEntry.toSupplementDraft(): SupplementDraft = SupplementDraft(
    name = name,
    brand = brand,
    notes = notes,
    form = dosagePlan.form,
    servingSize = dosagePlan.servingSize,
    servingAmount = dosagePlan.amount?.toDisplayNumber().orEmpty(),
    servingUnit = dosagePlan.unit
)

private fun SupplementDraft.toDosagePlan(): SupplementDosagePlan = SupplementDosagePlan(
    form = form,
    servingSize = servingSize.trim(),
    amount = servingAmount.toDoubleOrNull(),
    unit = servingUnit
)

private fun SupplementDraft.isValid(): Boolean {
    if (name.isBlank()) return false
    return brand.isNotBlank() && servingSize.isNotBlank() && servingAmount.toDoubleOrNull() != null
}

private fun Double.toDisplayNumber(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

