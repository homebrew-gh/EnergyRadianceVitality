package com.erv.app.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erv.app.R
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.navigation.Category
import com.erv.app.ui.navigation.categories
import com.erv.app.supplements.SupplementLibraryState
import com.erv.app.supplements.SupplementActivityRow
import com.erv.app.supplements.SupplementRepository
import com.erv.app.supplements.SupplementRoutine
import com.erv.app.supplements.SupplementRoutineStep
import com.erv.app.lighttherapy.LightActivityRow
import com.erv.app.lighttherapy.LightLibraryState
import com.erv.app.lighttherapy.LightRoutine
import com.erv.app.lighttherapy.LightSync
import com.erv.app.lighttherapy.LightTherapyRepository
import com.erv.app.lighttherapy.LightTimeOfDay
import com.erv.app.lighttherapy.lightActivityFor
import com.erv.app.supplements.SupplementSync
import com.erv.app.supplements.SupplementTimeOfDay
import com.erv.app.supplements.describe
import com.erv.app.supplements.groupedSupplementActivityFor
import com.erv.app.reminders.RoutineReminderRepository
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    supplementRepository: SupplementRepository,
    lightTherapyRepository: LightTherapyRepository,
    relayPool: RelayPool?,
    signer: EventSigner?,
    pendingReminderRoutineId: String?,
    onConsumePendingReminderRoutineId: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedDate.collectAsState()
    val supplementState by supplementRepository.state.collectAsState(initial = SupplementLibraryState())
    val lightState by lightTherapyRepository.state.collectAsState(initial = LightLibraryState())
    val reminderRepository = remember(context) { RoutineReminderRepository(context) }
    val supplementRows = remember(supplementState, selectedDate) {
        supplementState.groupedSupplementActivityFor(selectedDate)
    }
    val lightRows = remember(lightState, selectedDate) {
        lightState.lightActivityFor(selectedDate)
    }
    val today = remember { LocalDate.now() }
    val scope = rememberCoroutineScope()
    var showCalendar by remember { mutableStateOf(false) }
    var routinePreview by remember { mutableStateOf<SupplementRoutine?>(null) }
    var routineEditor by remember { mutableStateOf<SupplementRoutine?>(null) }
    var lightRoutinePreview by remember { mutableStateOf<LightRoutine?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    suspend fun syncMaster() {
        if (relayPool != null && signer != null) {
            SupplementSync.publishMaster(relayPool, signer, supplementRepository.currentState())
        }
    }

    suspend fun syncDailyLog() {
        if (relayPool != null && signer != null) {
            supplementRepository.currentState().logFor(today)?.let { log ->
                SupplementSync.publishDailyLog(relayPool, signer, log)
            }
            lightTherapyRepository.currentState().logFor(today)?.let { log ->
                LightSync.publishDailyLog(relayPool, signer, log)
            }
        }
    }

    LaunchedEffect(pendingReminderRoutineId, supplementState.routines) {
        val routineId = pendingReminderRoutineId ?: return@LaunchedEffect
        for (attempt in 0 until 20) {
            val routine = supplementState.routines.firstOrNull { it.id == routineId }
            if (routine != null) {
                routinePreview = routine
                onConsumePendingReminderRoutineId()
                return@LaunchedEffect
            }
            if (supplementState.routines.isNotEmpty()) break
            delay(100)
        }
        onConsumePendingReminderRoutineId()
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        sheetPeekHeight = 48.dp,
        sheetContent = {
            CategorySheet(onCategoryClick = onNavigateToCategory)
        },
        sheetDragHandle = { BottomSheetDefaults.DragHandle() },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sun),
                            contentDescription = null,
                            tint = Color(0xFFFFD600),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ERV",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFC62828)
                )
            )
        },
        modifier = modifier
    ) { padding ->
        // The dashboard keeps routines lightweight: select, preview, then log or edit.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            DateNavigator(
                selectedDate = selectedDate,
                onPreviousDay = viewModel::previousDay,
                onNextDay = viewModel::nextDay,
                onPreviousWeek = viewModel::previousWeek,
                onNextWeek = viewModel::nextWeek,
                onTodayClick = viewModel::goToToday,
                onCalendarClick = { showCalendar = true }
            )

            Spacer(Modifier.height(20.dp))

            GoalsSection()

            Spacer(Modifier.height(16.dp))

            RoutinesSection(
                supplementRoutines = supplementState.routines,
                lightRoutines = lightState.routines,
                onSupplementRoutineSelected = { routinePreview = it },
                onLightRoutineSelected = { lightRoutinePreview = it },
                onNavigateToCategory = onNavigateToCategory
            )

            Spacer(Modifier.height(16.dp))

            ActivitySection(
                selectedDate = selectedDate,
                supplementRows = supplementRows,
                lightRows = lightRows
            )

            Spacer(Modifier.height(16.dp))
        }

        if (showCalendar) {
            CalendarPopup(
                selectedDate = selectedDate,
                onDateSelected = { date ->
                    viewModel.selectDate(date)
                    showCalendar = false
                },
                onDismiss = { showCalendar = false }
            )
        }

        routinePreview?.let { routine ->
            RoutinePreviewSheet(
                routine = routine,
                supplements = supplementState.supplements,
                onDismiss = { routinePreview = null },
                onLogAsIs = {
                    scope.launch {
                        supplementRepository.logRoutineRun(today, routine.id, routine.name, routine.steps)
                        syncDailyLog()
                        snackbarHostState.showSnackbar("Logged ${routine.name}")
                        routinePreview = null
                    }
                },
                onModify = {
                    routineEditor = routine
                    routinePreview = null
                }
            )
        }

        routineEditor?.let { routine ->
            RoutineModifyDialog(
                routine = routine,
                supplements = supplementState.supplements,
                onDismiss = { routineEditor = null },
                onSave = { updatedName, steps, notes, savePermanently ->
                    scope.launch {
                        val updatedRoutine = routine.copy(
                            name = updatedName,
                            steps = steps,
                            notes = notes
                        )
                        if (savePermanently) {
                            supplementRepository.renameRoutine(
                                routineId = routine.id,
                                name = updatedRoutine.name,
                                steps = updatedRoutine.steps,
                                notes = updatedRoutine.notes
                            )
                            reminderRepository.updateRoutineName(routine.id, updatedRoutine.name)
                            syncMaster()
                        }
                        supplementRepository.logRoutineRun(
                            date = today,
                            routineId = updatedRoutine.id,
                            routineName = updatedRoutine.name,
                            steps = updatedRoutine.steps
                        )
                        syncDailyLog()
                        snackbarHostState.showSnackbar(
                            if (savePermanently) {
                                "Updated and logged ${updatedRoutine.name}"
                            } else {
                                "Logged edited ${updatedRoutine.name}"
                            }
                        )
                        routineEditor = null
                    }
                }
            )
        }

        lightRoutinePreview?.let { routine ->
            LightRoutinePreviewSheet(
                routine = routine,
                deviceName = routine.deviceId?.let { lightState.deviceById(it)?.name },
                onDismiss = { lightRoutinePreview = null },
                onLogSession = {
                    scope.launch {
                        val device = routine.deviceId?.let { lightState.deviceById(it) }
                        lightTherapyRepository.logSession(
                            date = today,
                            minutes = routine.durationMinutes,
                            deviceId = routine.deviceId,
                            deviceName = device?.name,
                            routineId = routine.id,
                            routineName = routine.name
                        )
                        lightTherapyRepository.currentState().logFor(today)?.let { log ->
                            if (relayPool != null && signer != null) {
                                LightSync.publishDailyLog(relayPool, signer, log)
                            }
                        }
                        snackbarHostState.showSnackbar("Logged ${routine.name} • ${routine.durationMinutes} min")
                        lightRoutinePreview = null
                    }
                },
                onStartTimer = {
                    categories.firstOrNull { it.id == "light_therapy" }?.let { onNavigateToCategory(it) }
                    lightRoutinePreview = null
                }
            )
        }
    }
}

@Composable
private fun GoalsSection() {
    Text(
        text = "Goals",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Set goals to track progress",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Goals will appear here once you configure them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutinesSection(
    supplementRoutines: List<SupplementRoutine>,
    lightRoutines: List<LightRoutine>,
    onSupplementRoutineSelected: (SupplementRoutine) -> Unit,
    onLightRoutineSelected: (LightRoutine) -> Unit,
    onNavigateToCategory: (Category) -> Unit
) {
    Text(
        text = "Routines",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (supplementRoutines.isEmpty() && lightRoutines.isEmpty()) {
                Text(
                    text = "Create a routine in Supplements or Light Therapy to log it from here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Tap a routine to preview, log, or modify it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    supplementRoutines.forEachIndexed { index, routine ->
                        RoutineTile(
                            icon = routineIconFor(index),
                            label = routine.name,
                            subtitle = "Supplements",
                            onClick = { onSupplementRoutineSelected(routine) },
                            modifier = Modifier.width(120.dp)
                        )
                    }
                    lightRoutines.forEachIndexed { _, routine ->
                        RoutineTile(
                            icon = Icons.Default.WbSunny,
                            label = routine.name,
                            subtitle = "Light • ${routine.durationMinutes} min",
                            onClick = { onLightRoutineSelected(routine) },
                            modifier = Modifier.width(120.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivitySection(
    selectedDate: LocalDate,
    supplementRows: List<SupplementActivityRow>,
    lightRows: List<LightActivityRow>
) {
    Text(
        text = "Activity",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val hasSupplements = supplementRows.isNotEmpty()
            val hasLight = lightRows.isNotEmpty()

            if (!hasSupplements && !hasLight) {
                Text(
                    text = "No activity logged yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (hasSupplements) {
                    ActivityCategorySection(title = "Supplements") {
                        supplementRows.forEach { row ->
                            Text(
                                text = "${row.supplementName} ${row.amountDisplay}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (hasLight) {
                    if (hasSupplements) Spacer(Modifier.height(12.dp))
                    ActivityCategorySection(title = "Light therapy") {
                        lightRows.forEach { row ->
                            Text(
                                text = "${row.displayName} • ${row.minutes} min",
                                style = MaterialTheme.typography.bodyMedium,
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
private fun ActivityCategorySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LightRoutinePreviewSheet(
    routine: LightRoutine,
    deviceName: String?,
    onDismiss: () -> Unit,
    onLogSession: () -> Unit,
    onStartTimer: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(routine.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${when (routine.timeOfDay) { LightTimeOfDay.MORNING -> "Morning"; LightTimeOfDay.AFTERNOON -> "Afternoon"; LightTimeOfDay.NIGHT -> "Night" }} • ${routine.durationMinutes} min${deviceName?.let { " • $it" }.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (routine.notes.isNotBlank()) {
                Text(
                    routine.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onLogSession) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log session")
                }
                OutlinedButton(onClick = onStartTimer) {
                    Icon(Icons.Default.WbSunny, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start timer")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutinePreviewSheet(
    routine: SupplementRoutine,
    supplements: List<com.erv.app.supplements.SupplementEntry>,
    onDismiss: () -> Unit,
    onLogAsIs: () -> Unit,
    onModify: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(routine.name, style = MaterialTheme.typography.titleLarge)
            if (routine.notes.isNotBlank()) {
                Text(
                    routine.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            routine.steps.forEachIndexed { index, step ->
                val supplement = supplements.firstOrNull { it.id == step.supplementId }
                Text(
                    text = "${index + 1}. ${step.describe(supplement?.name ?: "Missing supplement")}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onLogAsIs) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log as is")
                }
                OutlinedButton(onClick = onModify) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Modify")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private data class RoutineStepDraft(
    val supplementId: String? = null,
    val timeOfDay: SupplementTimeOfDay = SupplementTimeOfDay.MORNING,
    val quantity: String = "1",
    val dosageOverride: String = "",
    val note: String = ""
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RoutineModifyDialog(
    routine: SupplementRoutine,
    supplements: List<com.erv.app.supplements.SupplementEntry>,
    onDismiss: () -> Unit,
    onSave: (String, List<SupplementRoutineStep>, String, Boolean) -> Unit
) {
    var name by remember(routine.id) { mutableStateOf(routine.name) }
    var notes by remember(routine.id) { mutableStateOf(routine.notes) }
    var savePermanently by remember(routine.id) { mutableStateOf(false) }
    val steps = remember(routine.id) {
        mutableStateListOf<RoutineStepDraft>().apply {
            if (routine.steps.isEmpty()) {
                add(RoutineStepDraft())
            } else {
                routine.steps.forEach {
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modify routine") },
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
                timeSlots.forEach { timeOfDay ->
                    val slotSteps = steps.withIndex().filter { it.value.timeOfDay == timeOfDay }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(timeOfDay.label(), style = MaterialTheme.typography.labelLarge)
                        if (slotSteps.isEmpty()) {
                            Text(
                                text = "No supplements added for this time yet.",
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
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add ${timeOfDay.label().lowercase()}")
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = savePermanently, onCheckedChange = { savePermanently = it })
                    Text("Update routine permanently")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.trim(),
                        steps.mapNotNull { draft ->
                            val supplementId = draft.supplementId ?: return@mapNotNull null
                            SupplementRoutineStep(
                                supplementId = supplementId,
                                timeOfDay = draft.timeOfDay,
                                quantity = draft.quantity.toIntOrNull() ?: 1,
                                dosageOverride = draft.dosageOverride.trim().ifBlank { null },
                                note = draft.note.trim().ifBlank { null }
                            )
                        },
                        notes.trim(),
                        savePermanently
                    )
                },
                enabled = name.isNotBlank() && steps.any { it.supplementId != null }
            ) {
                Text("Log")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineStepRow(
    step: RoutineStepDraft,
    supplements: List<com.erv.app.supplements.SupplementEntry>,
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

@Composable
private fun RoutineTile(
    icon: ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                minLines = 2
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun routineIconFor(index: Int) = when (index % 8) {
    0 -> Icons.Default.Medication
    1 -> Icons.Default.Bedtime
    2 -> Icons.Default.WbSunny
    3 -> Icons.Default.FitnessCenter
    4 -> Icons.Default.DirectionsRun
    5 -> Icons.Default.Spa
    6 -> Icons.Default.AcUnit
    else -> Icons.Default.Medication
}
