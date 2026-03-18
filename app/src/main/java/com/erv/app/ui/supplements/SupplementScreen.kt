@file:OptIn(ExperimentalLayoutApi::class)
package com.erv.app.ui.supplements

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.erv.app.supplements.SupplementActivityRow
import com.erv.app.supplements.SupplementDosagePlan
import com.erv.app.supplements.SupplementDayLog
import com.erv.app.supplements.SupplementEntry
import com.erv.app.supplements.SupplementForm
import com.erv.app.supplements.SupplementLibraryState
import com.erv.app.supplements.SupplementLogEntry
import com.erv.app.supplements.SupplementRepository
import com.erv.app.supplements.SupplementRoutine
import com.erv.app.supplements.SupplementRoutineStep
import com.erv.app.supplements.SupplementTimeOfDay
import com.erv.app.supplements.SupplementWeekday
import com.erv.app.supplements.SupplementUnit
import com.erv.app.supplements.describe
import com.erv.app.supplements.label
import com.erv.app.supplements.shortLabel
import com.erv.app.supplements.SupplementSync
import com.erv.app.supplements.chronologicalSupplementLogFor
import com.erv.app.ui.dashboard.CalendarPopup
import com.erv.app.ui.dashboard.DateNavigator
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private enum class SupplementsTab { Supplements, Routines }

// Match Light Therapy header color scheme: lighter red top bar, darker wine tab row
private val SupplementRedDark = Color(0xFF4A0E0E)
private val SupplementRedMid = Color(0xFF8B0000)

private data class RoutineStepDraft(
    val supplementId: String? = null,
    val timeOfDay: SupplementTimeOfDay = SupplementTimeOfDay.MORNING,
    val quantity: String = "1",
    val dosageOverride: String = "",
    val note: String = ""
)

private data class RoutineReminderDraft(
    val enabled: Boolean = false,
    val hour: Int = 8,
    val minute: Int = 0,
    val isPm: Boolean = false,
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
    onOpenLog: () -> Unit,
    onOpenSupplementDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val state by repository.state.collectAsState(initial = SupplementLibraryState())
    val reminderRepository = remember(context) { RoutineReminderRepository(context) }
    val reminderState by reminderRepository.state.collectAsState(initial = RoutineReminderState())
    val today = remember { LocalDate.now() }
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
                    IconButton(onClick = onOpenLog) {
                        Icon(Icons.Default.DateRange, contentDescription = "Open log")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SupplementRedMid,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = SupplementRedDark,
                contentColor = Color.White
            ) {
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
                subtitle = "Create separate morning, afternoon, or night supplement routines."
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
fun SupplementLogScreen(
    repository: SupplementRepository,
    state: SupplementLibraryState,
    onBack: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showCalendar by remember { mutableStateOf(false) }
    val entries = remember(state, selectedDate) { state.chronologicalSupplementLogFor(selectedDate) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log") },
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
            DateNavigator(
                selectedDate = selectedDate,
                onPreviousDay = { selectedDate = selectedDate.minusDays(1) },
                onNextDay = { selectedDate = selectedDate.plusDays(1) },
                onPreviousWeek = { selectedDate = selectedDate.minusWeeks(1) },
                onNextWeek = { selectedDate = selectedDate.plusWeeks(1) },
                onTodayClick = { selectedDate = LocalDate.now() },
                onCalendarClick = { showCalendar = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            HorizontalDivider()

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No supplements logged for this date.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Newest first. Tap delete on an entry to remove it; Activity summary updates automatically.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    itemsIndexed(
                        items = entries,
                        key = { index, entry -> entry.intakeId ?: "$index-${entry.takenAtEpochSeconds}-${entry.supplementId}-${entry.sourceLabel}" }
                    ) { _, entry ->
                        SupplementLogEntryCard(
                            entry = entry,
                            onDelete = {
                                scope.launch {
                                    if (entry.intakeId != null) {
                                        repository.removeIntake(selectedDate, entry.intakeId!!)
                                    } else {
                                        repository.removeIntakeByMatch(
                                            selectedDate,
                                            entry.supplementId,
                                            entry.takenAtEpochSeconds,
                                            entry.sourceLabel
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCalendar) {
        CalendarPopup(
            selectedDate = selectedDate,
            onDateSelected = {
                selectedDate = it
                showCalendar = false
            },
            onDismiss = { showCalendar = false }
        )
    }
}

@Composable
private fun SupplementLogEntryCard(
    entry: SupplementLogEntry,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.supplementName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = entry.dosageTaken,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatLogTime(entry.takenAtEpochSeconds),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove this entry"
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = entry.sourceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatLogTime(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "Unknown time"
    val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    return Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(formatter)
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
                        modifier = Modifier.weight(1f)
                    )
                    if (draft.form == SupplementForm.CAPSULE) {
                        OutlinedTextField(
                            value = "-",
                            onValueChange = { },
                            label = { Text("Unit") },
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        EnumDropdownField(
                            value = draft.servingUnit,
                            label = "Unit",
                            options = SupplementUnit.entries,
                            optionLabel = { it.label() },
                            onSelected = { draft = draft.copy(servingUnit = it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = draft.servingAmount,
                    onValueChange = { draft = draft.copy(servingAmount = it) },
                    label = {
                        Text(
                            if (draft.form == SupplementForm.CAPSULE) "Capsules per serving"
                            else "Amount"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                            dosageOverride = it.dosageOverride.orEmpty(),
                            note = it.note.orEmpty()
                        )
                    )
                }
            }
        }
    }
    val timeSlots = remember { listOf(SupplementTimeOfDay.MORNING, SupplementTimeOfDay.AFTERNOON, SupplementTimeOfDay.NIGHT) }
    var selectedTimeSlot by remember(routine?.id, creating) {
        val firstWithSteps = timeSlots.firstOrNull { slot -> steps.any { it.timeOfDay == slot } }
        mutableStateOf(firstWithSteps ?: SupplementTimeOfDay.MORNING)
    }
    var showTimePicker by remember { mutableStateOf(false) }

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
                    text = "Select a time of day, then add supplements to that slot. Switch tabs to edit another time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TabRow(
                    selectedTabIndex = timeSlots.indexOf(selectedTimeSlot).coerceIn(0, timeSlots.lastIndex),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    timeSlots.forEachIndexed { index, timeOfDay ->
                        Tab(
                            selected = selectedTimeSlot == timeOfDay,
                            onClick = { selectedTimeSlot = timeOfDay },
                            text = { Text(timeOfDay.label()) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                val slotStepsWithIndex = remember(steps, selectedTimeSlot) {
                    steps.mapIndexed { index, draft -> index to draft }
                        .filter { (_, draft) -> draft.timeOfDay == selectedTimeSlot }
                }
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${selectedTimeSlot.label()} supplements",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (slotStepsWithIndex.isEmpty()) {
                            Text(
                                "No supplements for ${selectedTimeSlot.label().lowercase()} yet. Tap below to add.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            slotStepsWithIndex.forEach { (fullIndex, draft) ->
                                RoutineStepRow(
                                    step = draft,
                                    supplements = supplements,
                                    onStepChange = { updated -> steps[fullIndex] = updated },
                                    onRemove = { steps.removeAt(fullIndex) }
                                )
                            }
                        }
                        TextButton(
                            onClick = { steps.add(RoutineStepDraft(timeOfDay = selectedTimeSlot)) }
                        ) {
                            Text("Add supplement to ${selectedTimeSlot.label().lowercase()}")
                        }
                    }
                }

                HorizontalDivider()
                Text("Reminder", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = reminderDraft.enabled,
                        onCheckedChange = { reminderDraft = reminderDraft.copy(enabled = it) }
                    )
                    Text("Enable reminder")
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    enabled = reminderDraft.enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reminder time: ${reminderDraft.displayTimeLabel()}")
                }
                if (showTimePicker) {
                    TimeWheelPickerDialog(
                        initialHour = reminderDraft.hour,
                        initialMinute = reminderDraft.minute,
                        initialIsPm = reminderDraft.isPm,
                        onDismiss = { showTimePicker = false },
                        onConfirm = { hour, minute, isPm ->
                            reminderDraft = reminderDraft.copy(hour = hour, minute = minute, isPm = isPm)
                            showTimePicker = false
                        }
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
                            val supplement = supplements.firstOrNull { it.id == supplementId }
                            SupplementRoutineStep(
                                supplementId = supplementId,
                                timeOfDay = draft.timeOfDay,
                                quantity = draft.quantity.toIntOrNull() ?: 1,
                                dosageOverride = draft.dosageOverride.trim().ifBlank {
                                    supplement?.recommendedServingDisplay().orEmpty()
                                }.ifBlank { null },
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
    val recommendedServing = selectedSupplement?.recommendedServingDisplay().orEmpty()

    LaunchedEffect(step.supplementId, selectedSupplement?.id) {
        if (selectedSupplement != null && step.dosageOverride.isBlank()) {
            onStepChange(step.copy(dosageOverride = recommendedServing))
        }
    }

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
                            onStepChange(
                                step.copy(
                                    supplementId = supplement.id,
                                    dosageOverride = supplement.recommendedServingDisplay()
                                )
                            )
                        }
                    )
                }
            }
        }

        if (selectedSupplement != null) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Recommended daily serving size",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = recommendedServing.ifBlank { "Take as directed" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            OutlinedTextField(
                value = step.dosageOverride.ifBlank { recommendedServing },
                onValueChange = { onStepChange(step.copy(dosageOverride = it)) },
                label = { Text("Daily serving size") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = step.quantity,
            onValueChange = { onStepChange(step.copy(quantity = it.filter { ch -> ch.isDigit() })) },
            label = { Text("Quantity (multiplier)") },
            supportingText = { Text("e.g. 2 = 2× the serving size above") },
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

        TextButton(onClick = onRemove) {
            Text("Remove from this time")
        }
    }
}

private fun SupplementTimeOfDay.label(): String = when (this) {
    SupplementTimeOfDay.MORNING -> "Morning"
    SupplementTimeOfDay.AFTERNOON -> "Afternoon"
    SupplementTimeOfDay.NIGHT -> "Night"
}

private fun SupplementEntry.recommendedServingDisplay(): String =
    info?.servingSize?.takeIf { it.isNotBlank() }
        ?: dosagePlan.summary().takeIf { it.isNotBlank() }
        ?: "Take as directed"

private fun RoutineReminderDraft.displayTimeLabel(): String {
    val hourDisplay = hour.coerceIn(1, 12)
    val minuteDisplay = minute.coerceIn(0, 59).toString().padStart(2, '0')
    return "$hourDisplay:$minuteDisplay ${if (isPm) "PM" else "AM"}"
}

private fun RoutineReminder.toDraft(): RoutineReminderDraft = RoutineReminderDraft(
    enabled = enabled,
    hour = when (val normalized = hour.coerceIn(0, 23)) {
        0 -> 12
        in 1..11 -> normalized
        12 -> 12
        else -> normalized - 12
    },
    minute = minute.coerceIn(0, 59),
    isPm = hour.coerceIn(0, 23) >= 12,
    frequency = frequency,
    repeatDays = repeatDays.toSet()
)

private fun RoutineReminderDraft.toReminder(routineId: String, routineName: String): RoutineReminder = RoutineReminder(
    routineId = routineId,
    routineName = routineName,
    enabled = enabled,
    hour = if (isPm) {
        when (hour.coerceIn(1, 12)) {
            12 -> 12
            else -> hour.coerceIn(1, 12) + 12
        }
    } else {
        when (hour.coerceIn(1, 12)) {
            12 -> 0
            else -> hour.coerceIn(1, 12)
        }
    },
    minute = minute.coerceIn(0, 59),
    frequency = frequency,
    repeatDays = repeatDays.toList().sortedBy { it.ordinal }
)

private fun RoutineReminderDraft.isValid(): Boolean {
    if (!enabled) return true
    if (hour !in 1..12 || minute !in 0..59) return false
    if ((frequency == RoutineReminderFrequency.WEEKLY || frequency == RoutineReminderFrequency.CUSTOM_DAYS) && repeatDays.isEmpty()) return false
    return true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeWheelPickerDialog(
    initialHour: Int,
    initialMinute: Int,
    initialIsPm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Boolean) -> Unit
) {
    var hour by remember(initialHour, initialMinute, initialIsPm) { mutableIntStateOf(initialHour.coerceIn(1, 12)) }
    var minute by remember(initialHour, initialMinute, initialIsPm) { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }
    var isPm by remember(initialHour, initialMinute, initialIsPm) { mutableStateOf(initialIsPm) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set reminder time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Scroll the wheels to choose the hour, minute, and AM or PM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    WheelPickerColumn(
                        label = "Hour",
                        values = (1..12).map { it.toString() },
                        selectedIndex = hour - 1,
                        onSelectedIndexChange = { hour = it + 1 },
                        modifier = Modifier.weight(1f)
                    )
                    WheelPickerColumn(
                        label = "Minute",
                        values = (0..59).map { it.toString().padStart(2, '0') },
                        selectedIndex = minute,
                        onSelectedIndexChange = { minute = it },
                        modifier = Modifier.weight(1f)
                    )
                    WheelPickerColumn(
                        label = "AM/PM",
                        values = listOf("AM", "PM"),
                        selectedIndex = if (isPm) 1 else 0,
                        onSelectedIndexChange = { isPm = it == 1 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour, minute, isPm) }) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun WheelPickerColumn(
    label: String,
    values: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val itemHeight = 40.dp
    val centerOffset = 2
    val clampedSelectedIndex = selectedIndex.coerceIn(0, values.lastIndex.coerceAtLeast(0))
    val totalItems = values.size + centerOffset * 2

    LaunchedEffect(values, clampedSelectedIndex) {
        if (values.isNotEmpty()) {
            listState.scrollToItem(clampedSelectedIndex + centerOffset)
        }
    }

    LaunchedEffect(listState, values) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && values.isNotEmpty()) {
                val viewportCenter = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
                val centeredIndex = listState.layoutInfo.visibleItemsInfo.minByOrNull { itemInfo ->
                    abs((itemInfo.offset + itemInfo.size / 2) - viewportCenter)
                }?.index ?: return@collect
                onSelectedIndexChange((centeredIndex - centerOffset).coerceIn(0, values.lastIndex))
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * 5)
        ) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                items(totalItems) { lazyIndex ->
                    val valueIndex = lazyIndex - centerOffset
                    val isSpacer = valueIndex !in values.indices
                    val value = if (isSpacer) "" else values[valueIndex]
                    val isSelected = valueIndex == clampedSelectedIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .clickable(enabled = !isSpacer) { onSelectedIndexChange(valueIndex) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = value,
                            style = if (isSelected) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            )
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
    val defaultQuery = remember(supplementId, supplement?.name, supplement?.brand) {
        listOfNotNull(
            supplement?.brand?.takeIf { it.isNotBlank() },
            supplement?.name?.takeIf { it.isNotBlank() }
        ).joinToString(" ").trim()
    }
    var query by rememberSaveable(supplementId) { mutableStateOf(defaultQuery) }
    var apiResults by remember { mutableStateOf<List<SupplementApiResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var autoSearchDone by rememberSaveable(supplementId) { mutableStateOf(false) }
    var autoAttachDone by rememberSaveable(supplementId) { mutableStateOf(false) }

    suspend fun syncMaster() {
        if (relayPool != null && signer != null) {
            SupplementSync.publishMaster(relayPool, signer, repository.currentState())
        }
    }

    suspend fun searchNih(term: String) {
        val cleaned = term.trim()
        if (cleaned.isBlank()) return
        searching = true
        try {
            query = cleaned
            apiResults = apiClient.search(cleaned)
            if (apiResults.isEmpty() && supplement != null) {
                val fallbacks = listOfNotNull(
                    apiClient.normalizeSearchQuery(cleaned).takeIf { it != cleaned },
                    supplement.name.takeIf { it.isNotBlank() && it != cleaned },
                    supplement.brand.takeIf { it.isNotBlank() && it != cleaned }
                ).distinct()
                for (fallback in fallbacks) {
                    if (apiResults.isNotEmpty()) break
                    apiResults = apiClient.search(fallback)
                    if (apiResults.isNotEmpty()) {
                        query = fallback
                        break
                    }
                }
            }
            if (apiResults.isEmpty()) {
                snackbarHostState.showSnackbar("No API results found")
            }
        } finally {
            searching = false
        }
    }

    LaunchedEffect(supplement?.id) {
        if (!autoSearchDone && defaultQuery.isNotBlank()) {
            autoSearchDone = true
            searchNih(defaultQuery)
        }
    }

    LaunchedEffect(apiResults) {
        if (!autoAttachDone && apiResults.size == 1 && supplement != null) {
            autoAttachDone = true
            val match = apiResults.first()
            repository.attachInfo(supplement.id, match.info, match.productId)
            syncMaster()
            snackbarHostState.showSnackbar("Matched ${match.productName}")
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
                                    searchNih(query)
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
                        colors = if (apiResults.size == 1) {
                            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            CardDefaults.elevatedCardColors()
                        },
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

