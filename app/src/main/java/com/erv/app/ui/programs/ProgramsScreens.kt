package com.erv.app.ui.programs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.cardioBuiltinActivitiesForUserSelection
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.weighttraining.WeightPickExerciseDialog
import com.erv.app.ui.weighttraining.WeightRoutineEditorDialog
import com.erv.app.programs.FitnessProgram
import com.erv.app.programs.ProgramBlockKind
import com.erv.app.programs.ProgramDayBlock
import com.erv.app.programs.ProgramImport
import com.erv.app.programs.ProgramImportEnvelope
import com.erv.app.programs.ProgramRepository
import com.erv.app.programs.ProgramRotationEntry
import com.erv.app.programs.ProgramStrategy
import com.erv.app.programs.ProgramStrategyMode
import com.erv.app.programs.ProgramSync
import com.erv.app.programs.ProgramTemplateOption
import com.erv.app.programs.ProgramWeekDay
import com.erv.app.programs.ProgramsLibraryState
import com.erv.app.programs.isoDayOfWeekLabel
import com.erv.app.programs.normalizedWeek
import com.erv.app.programs.resolveProgramStrategyForDate
import com.erv.app.programs.sanitized
import com.erv.app.programs.summaryLine
import com.erv.app.programs.strategySummaryForDate
import com.erv.app.programs.withWeekDayUpdated
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.stretching.StretchRoutine
import com.erv.app.stretching.StretchingRepository
import com.erv.app.stretching.StretchingSync
import com.erv.app.stretching.stretchCategoryDisplayLabel
import com.erv.app.ui.stretching.StretchPickStretchDialog
import com.erv.app.ui.theme.ErvHeaderRed
import com.erv.app.unifiedroutines.UnifiedRoutineLibraryState
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightRoutine
import com.erv.app.weighttraining.WeightSync
import com.erv.app.weighttraining.displayLabel
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsCategoryScreen(
    programRepository: ProgramRepository,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onOpenProgram: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by programRepository.state.collectAsState(initial = ProgramsLibraryState())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val keyManager = LocalKeyManager.current
    val headerColor = ErvHeaderRed
    val onHeader = Color.White
    var showCreateSheet by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var showCustomWizard by remember { mutableStateOf(false) }
    var pendingTemplateCustomize by remember { mutableStateOf<ProgramTemplateOption?>(null) }
    var pendingImport by remember { mutableStateOf<ProgramImportEnvelope?>(null) }
    var importErrors by remember { mutableStateOf<List<String>?>(null) }
    var pendingDeleteFromList by remember { mutableStateOf<FitnessProgram?>(null) }
    var showStrategyEditor by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val todayStrategy = remember(state, today) { state.resolveProgramStrategyForDate(today) }
    val todayStrategySummary = remember(state, today) { state.strategySummaryForDate(today) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(StandardCharsets.UTF_8).readText()
                }
            }
            if (text.isNullOrBlank()) {
                snackbarHostState.showSnackbar("Could not read file")
                return@launch
            }
            val (envelope, errs) = ProgramImport.parse(text)
            if (envelope == null) {
                importErrors = errs
                return@launch
            }
            val validation = ProgramImport.validate(envelope)
            if (validation.isNotEmpty()) {
                importErrors = validation
                return@launch
            }
            pendingImport = envelope
        }
    }

    fun createProgramAndOpen(program: FitnessProgram, setActive: Boolean) {
        scope.launch {
            programRepository.upsertProgram(program)
            val autoStrategy =
                if (setActive) {
                    programRepository.activateProgramForDate(program.id)
                } else {
                    null
                }
            if (relayPool != null && signer != null) {
                ProgramSync.publishMaster(
                    appContext = context.applicationContext,
                    relayPool = relayPool,
                    signer = signer,
                    state = programRepository.currentState(),
                    dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
                )
            }
            programRepository.state.first { it.programById(program.id) != null }
            snackbarHostState.showSnackbar(
                if (autoStrategy?.mode == ProgramStrategyMode.CHALLENGE) {
                    "Created \"${program.name}\" and started a 75-day challenge strategy"
                } else {
                    "Created \"${program.name}\""
                }
            )
            onOpenProgram(program.id)
        }
    }

    importErrors?.let { errs ->
        AlertDialog(
            onDismissRequest = { importErrors = null },
            title = { Text("Import issue") },
            text = { Text(errs.joinToString("\n")) },
            confirmButton = {
                TextButton(onClick = { importErrors = null }) { Text("OK") }
            }
        )
    }

    pendingImport?.let { env ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import programs") },
            text = {
                Text(
                    "Merge ${env.programs.size} program(s) into your library?" +
                        (env.activeProgramId?.let { "\nSet active: $it" } ?: "") +
                        (env.strategy?.let { "\nApply strategy: ${programStrategyModeLabel(it.mode)}" } ?: "")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toApply = env
                        pendingImport = null
                        scope.launch {
                            programRepository.mergeImportedPrograms(
                                imported = toApply.programs,
                                setActiveFromImport = toApply.activeProgramId,
                                strategyFromImport = toApply.strategy,
                            )
                            if (relayPool != null && signer != null) {
                                ProgramSync.publishMaster(
                                    appContext = context.applicationContext,
                                    relayPool = relayPool,
                                    signer = signer,
                                    state = programRepository.currentState(),
                                    dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
                                )
                            }
                            snackbarHostState.showSnackbar("Programs imported")
                        }
                    }
                ) { Text("Merge") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            }
        )
    }

    pendingDeleteFromList?.let { doomed ->
        AlertDialog(
            onDismissRequest = { pendingDeleteFromList = null },
            title = { Text("Delete program") },
            text = { Text("Remove \"${doomed.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = doomed.id
                        pendingDeleteFromList = null
                        scope.launch {
                            programRepository.deleteProgram(id)
                            if (relayPool != null && signer != null) {
                                ProgramSync.publishMaster(
                                    appContext = context.applicationContext,
                                    relayPool = relayPool,
                                    signer = signer,
                                    state = programRepository.currentState(),
                                    dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
                                )
                            }
                            snackbarHostState.showSnackbar("Program deleted")
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteFromList = null }) { Text("Cancel") }
            }
        )
    }

    if (showStrategyEditor) {
        ProgramStrategyEditorDialog(
            current = state.strategy,
            programs = state.programs.sortedBy { it.name.lowercase() },
            onDismiss = { showStrategyEditor = false },
            onSave = { strategy ->
                showStrategyEditor = false
                scope.launch {
                    programRepository.setProgramStrategy(strategy)
                    if (relayPool != null && signer != null) {
                        ProgramSync.publishMaster(
                            appContext = context.applicationContext,
                            relayPool = relayPool,
                            signer = signer,
                            state = programRepository.currentState(),
                            dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
                        )
                    }
                    snackbarHostState.showSnackbar("Program strategy saved")
                }
            }
        )
    }

    if (showCreateSheet) {
        ProgramCreateEntrySheet(
            onDismiss = { showCreateSheet = false },
            onStartCustom = {
                showCreateSheet = false
                showCustomWizard = true
            },
            onStartTemplate = {
                showCreateSheet = false
                showTemplatePicker = true
            },
            onImportJson = {
                showCreateSheet = false
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        )
    }

    if (showCustomWizard) {
        CustomProgramWizardSheet(
            onDismiss = { showCustomWizard = false },
            onCreateProgram = { program, setActive ->
                showCustomWizard = false
                createProgramAndOpen(program, setActive)
            }
        )
    }

    if (showTemplatePicker) {
        ProgramTemplatePickerSheet(
            onDismiss = { showTemplatePicker = false },
            onPickTemplate = { option ->
                showTemplatePicker = false
                pendingTemplateCustomize = option
            }
        )
    }

    pendingTemplateCustomize?.let { option ->
        ProgramTemplateCustomizeSheet(
            option = option,
            onDismiss = { pendingTemplateCustomize = null },
            onCreateProgram = { program, setActive ->
                pendingTemplateCustomize = null
                createProgramAndOpen(program, setActive)
            }
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Programs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = "Import JSON")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerColor,
                    titleContentColor = onHeader,
                    navigationIconContentColor = onHeader,
                    actionIconContentColor = onHeader
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create program")
            }
        }
    ) { padding ->
        if (state.programs.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Build multi-week plans that map training, cardio, stretching, and sauna or cold plunge to each day.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Tap + to start a custom plan, choose a template, or import JSON from a coach or AI tool.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            "Save reusable weekly plans here. Set one active to surface it in your daily flow.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Program Strategy", style = MaterialTheme.typography.titleMedium)
                            Text(
                                todayStrategySummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            todayStrategy.detail?.takeIf { it.isNotBlank() && it != todayStrategySummary }?.let { detail ->
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = { showStrategyEditor = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Edit Strategy")
                            }
                        }
                    }
                }
                items(state.programs, key = { it.id }) { p ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (todayStrategy.program?.id == p.id) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            }
                        )
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable { onOpenProgram(p.id) }
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                                    if (todayStrategy.program?.id == p.id) {
                                        Text(
                                            if (state.strategy.mode == ProgramStrategyMode.MANUAL) "Active" else "Today",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                p.description?.takeIf { it.isNotBlank() }?.let { d ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        d,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                p.sourceLabel?.let { s ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        s,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            IconButton(onClick = { pendingDeleteFromList = p }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete program")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProgramStrategyEditorDialog(
    current: ProgramStrategy,
    programs: List<FitnessProgram>,
    onDismiss: () -> Unit,
    onSave: (ProgramStrategy) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(current.mode.name) }
    var repeatProgramId by rememberSaveable { mutableStateOf(current.repeatProgramId.orEmpty()) }
    var rotationStartDate by rememberSaveable { mutableStateOf(current.rotationStartDate.orEmpty()) }
    var rotationRepeats by rememberSaveable { mutableStateOf(current.rotationRepeats) }
    var rotationEntries by remember {
        mutableStateOf(
            if (current.rotationEntries.isEmpty()) {
                listOf(ProgramRotationEntry())
            } else {
                current.rotationEntries
            }
        )
    }
    var challengeName by rememberSaveable { mutableStateOf(current.challengeName.orEmpty()) }
    var challengeProgramId by rememberSaveable { mutableStateOf(current.challengeProgramId.orEmpty()) }
    var challengeStartDate by rememberSaveable { mutableStateOf(current.challengeStartDate.orEmpty()) }
    var challengeLengthDays by rememberSaveable { mutableStateOf(current.challengeLengthDays.toString()) }
    val selectedMode = ProgramStrategyMode.entries.firstOrNull { it.name == mode } ?: ProgramStrategyMode.MANUAL
    val canSave = when (selectedMode) {
        ProgramStrategyMode.MANUAL -> true
        ProgramStrategyMode.REPEAT -> programs.any { it.id == repeatProgramId }
        ProgramStrategyMode.ROTATION ->
            parseIsoDateOrNull(rotationStartDate) != null &&
                rotationEntries.isNotEmpty() &&
                rotationEntries.all { entry ->
                    entry.programId?.let { id -> programs.any { it.id == id } } == true && entry.repeatWeeks > 0
                }
        ProgramStrategyMode.CHALLENGE ->
            programs.any { it.id == challengeProgramId } &&
                parseIsoDateOrNull(challengeStartDate) != null &&
                challengeLengthDays.toIntOrNull()?.let { it in 1..365 } == true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Program Strategy") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Choose how ERV decides which weekly program is active for a given date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProgramStrategyMode.entries.forEach { entry ->
                        FilterChip(
                            selected = selectedMode == entry,
                            onClick = { mode = entry.name },
                            label = { Text(programStrategyModeLabel(entry)) }
                        )
                    }
                }
                when (selectedMode) {
                    ProgramStrategyMode.MANUAL -> {
                        Text(
                            "Manual mode uses the program you mark active in the list below. Change it any time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ProgramStrategyMode.REPEAT -> {
                        ProgramStrategyProgramSelector(
                            label = "Program to repeat every week",
                            selectedProgramId = repeatProgramId,
                            programs = programs,
                            onSelected = { repeatProgramId = it }
                        )
                    }
                    ProgramStrategyMode.ROTATION -> {
                        OutlinedTextField(
                            value = rotationStartDate,
                            onValueChange = { rotationStartDate = it },
                            label = { Text("Rotation start date (YYYY-MM-DD)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Repeat cycle", style = MaterialTheme.typography.bodyMedium)
                            FilterChip(
                                selected = rotationRepeats,
                                onClick = { rotationRepeats = !rotationRepeats },
                                label = { Text(if (rotationRepeats) "Yes" else "No") }
                            )
                        }
                        rotationEntries.forEachIndexed { index, entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Phase ${index + 1}", style = MaterialTheme.typography.titleSmall)
                                    ProgramStrategyProgramSelector(
                                        label = "Program",
                                        selectedProgramId = entry.programId.orEmpty(),
                                        programs = programs,
                                        onSelected = { programId ->
                                            rotationEntries = rotationEntries.map {
                                                if (it.id == entry.id) it.copy(programId = programId) else it
                                            }
                                        }
                                    )
                                    OutlinedTextField(
                                        value = entry.repeatWeeks.toString(),
                                        onValueChange = { raw ->
                                            val weeks = raw.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, 52) ?: 1
                                            rotationEntries = rotationEntries.map {
                                                if (it.id == entry.id) it.copy(repeatWeeks = weeks) else it
                                            }
                                        },
                                        label = { Text("Weeks") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    if (rotationEntries.size > 1) {
                                        TextButton(
                                            onClick = { rotationEntries = rotationEntries.filterNot { it.id == entry.id } },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Remove phase")
                                        }
                                    }
                                }
                            }
                        }
                        TextButton(
                            onClick = { rotationEntries = rotationEntries + ProgramRotationEntry() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add phase")
                        }
                    }
                    ProgramStrategyMode.CHALLENGE -> {
                        OutlinedTextField(
                            value = challengeName,
                            onValueChange = { challengeName = it },
                            label = { Text("Challenge name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        ProgramStrategyProgramSelector(
                            label = "Weekly program for the challenge",
                            selectedProgramId = challengeProgramId,
                            programs = programs,
                            onSelected = { challengeProgramId = it }
                        )
                        OutlinedTextField(
                            value = challengeStartDate,
                            onValueChange = { challengeStartDate = it },
                            label = { Text("Start date (YYYY-MM-DD)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = challengeLengthDays,
                            onValueChange = { challengeLengthDays = it.filter(Char::isDigit) },
                            label = { Text("Duration days") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val strategy = when (selectedMode) {
                        ProgramStrategyMode.MANUAL -> ProgramStrategy()
                        ProgramStrategyMode.REPEAT -> ProgramStrategy(
                            mode = ProgramStrategyMode.REPEAT,
                            repeatProgramId = repeatProgramId.ifBlank { null },
                        )
                        ProgramStrategyMode.ROTATION -> ProgramStrategy(
                            mode = ProgramStrategyMode.ROTATION,
                            rotationStartDate = rotationStartDate.ifBlank { null },
                            rotationEntries = rotationEntries,
                            rotationRepeats = rotationRepeats,
                        )
                        ProgramStrategyMode.CHALLENGE -> ProgramStrategy(
                            mode = ProgramStrategyMode.CHALLENGE,
                            challengeName = challengeName.ifBlank { null },
                            challengeProgramId = challengeProgramId.ifBlank { null },
                            challengeStartDate = challengeStartDate.ifBlank { null },
                            challengeLengthDays = challengeLengthDays.toIntOrNull() ?: 75,
                        )
                    }
                    onSave(strategy)
                },
                enabled = canSave
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramStrategyProgramSelector(
    label: String,
    selectedProgramId: String,
    programs: List<FitnessProgram>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = programs.firstOrNull { it.id == selectedProgramId }?.name ?: "Choose program",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            programs.forEach { program ->
                DropdownMenuItem(
                    text = { Text(program.name) },
                    onClick = {
                        onSelected(program.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun programStrategyModeLabel(mode: ProgramStrategyMode): String = when (mode) {
    ProgramStrategyMode.MANUAL -> "Manual"
    ProgramStrategyMode.REPEAT -> "Repeat"
    ProgramStrategyMode.ROTATION -> "Rotation"
    ProgramStrategyMode.CHALLENGE -> "Challenge"
}

private fun parseIsoDateOrNull(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        null
    }

private fun initialProgramStretchIds(
    existing: ProgramDayBlock?,
    stretchState: StretchLibraryState,
    stretchCatalog: List<com.erv.app.stretching.StretchCatalogEntry>,
): List<String> {
    val valid = stretchCatalog.map { it.id }.toSet()
    if (existing == null) return emptyList()
    val rid = existing.stretchRoutineId?.takeIf { it.isNotBlank() }
    if (rid != null && existing.kind == ProgramBlockKind.STRETCH_ROUTINE) {
        val r = stretchState.routineById(rid)
        if (r != null) return r.stretchIds.filter { it in valid }
    }
    return existing.stretchCatalogIds.filter { it in valid }
}

private fun initialProgramStretchHoldString(
    existing: ProgramDayBlock?,
    stretchState: StretchLibraryState,
): String {
    if (existing == null) return "30"
    val rid = existing.stretchRoutineId?.takeIf { it.isNotBlank() }
    if (rid != null && existing.kind == ProgramBlockKind.STRETCH_ROUTINE) {
        val r = stretchState.routineById(rid)
        if (r != null) return r.holdSecondsPerStretch.coerceIn(5, 300).toString()
    }
    return (existing.stretchHoldSecondsPerStretch ?: 30).coerceIn(5, 300).toString()
}

private fun initialProgramWeightExerciseIds(
    existing: ProgramDayBlock?,
    weightState: WeightLibraryState,
): List<String> {
    if (existing == null || existing.kind != ProgramBlockKind.WEIGHT) return emptyList()
    val rid = existing.weightRoutineId?.takeIf { it.isNotBlank() }
    if (rid != null) {
        val r = weightState.routines.firstOrNull { it.id == rid }
        if (r != null) return r.exerciseIds.filter { weightState.exerciseById(it) != null }
    }
    return existing.weightExerciseIds.filter { weightState.exerciseById(it) != null }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgramDetailScreen(
    programId: String,
    programRepository: ProgramRepository,
    weightRepository: WeightRepository,
    stretchingRepository: StretchingRepository,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    stretchState: StretchLibraryState,
    stretchCatalog: List<com.erv.app.stretching.StretchCatalogEntry>,
    unifiedRoutineState: UnifiedRoutineLibraryState,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val programsState by programRepository.state.collectAsState(initial = ProgramsLibraryState())
    val keyManager = LocalKeyManager.current
    val program = programsState.programById(programId)
    var draft by remember(programId) { mutableStateOf<FitnessProgram?>(null) }
    // Load draft: flow can lag right after upsert + navigate; also hydrate from DataStore immediately.
    LaunchedEffect(programId, program) {
        when {
            program != null && draft == null -> draft = program
            program == null && draft == null -> {
                val sync = programRepository.currentState().programById(programId)
                if (sync != null) {
                    draft = sync
                } else {
                    val lib = withTimeoutOrNull(5000L) {
                        programRepository.state.first { it.programById(programId) != null }
                    }
                    val p = lib?.programById(programId)
                    if (p != null) {
                        draft = p
                    } else {
                        onBack()
                    }
                }
            }
        }
    }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val headerColor = ErvHeaderRed
    val onHeader = Color.White
    var showMenu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    var addBlockForDay by remember { mutableIntStateOf(1) }
    var showAddBlock by remember { mutableStateOf(false) }
    var editBlock by remember { mutableStateOf<ProgramDayBlock?>(null) }
    var editContentBlock by remember { mutableStateOf<ProgramDayBlock?>(null) }
    val weekPlanListState = rememberLazyListState()

    val exportJson = remember {
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val d = draft ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val exportedStrategy = programsState.strategy.sanitized(setOf(d.id)).takeIf { it != ProgramStrategy() }
            val envelope = ProgramImportEnvelope(
                programs = listOf(d),
                activeProgramId = d.id,
                strategy = exportedStrategy,
            )
            val text = exportJson.encodeToString(ProgramImportEnvelope.serializer(), envelope)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        BufferedWriter(OutputStreamWriter(out, StandardCharsets.UTF_8)).use { w ->
                            w.write(text)
                        }
                    } != null
                }.getOrDefault(false)
            }
            snackbarHostState.showSnackbar(if (ok) "Exported" else "Could not write file")
        }
    }

    if (draft == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val d = draft!!

    fun updateBlocksForDay(dayOfWeek: Int, transform: (List<ProgramDayBlock>) -> List<ProgramDayBlock>) {
        val current = draft ?: return
        val day = current.normalizedWeek().first { it.dayOfWeek == dayOfWeek }
        draft = current.withWeekDayUpdated(day.copy(blocks = transform(day.blocks)))
    }

    suspend fun persistProgramEdits(program: FitnessProgram, snackbarMessage: String? = null) {
        programRepository.upsertProgram(program)
        if (relayPool != null && signer != null) {
            ProgramSync.publishMaster(
                appContext = context.applicationContext,
                relayPool = relayPool,
                signer = signer,
                state = programRepository.currentState(),
                dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
            )
        }
        draft = programRepository.currentState().programById(program.id)
        if (!snackbarMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(snackbarMessage)
        }
    }

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("Delete program") },
            text = { Text("Remove \"${d.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = false
                        scope.launch {
                            programRepository.deleteProgram(programId)
                            if (relayPool != null && signer != null) {
                                ProgramSync.publishMaster(
                                    appContext = context.applicationContext,
                                    relayPool = relayPool,
                                    signer = signer,
                                    state = programRepository.currentState(),
                                    dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
                                )
                            }
                            onBack()
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddBlock || editBlock != null || editContentBlock != null) {
        key(editBlock?.id ?: editContentBlock?.id ?: "new", showAddBlock) {
            BlockEditorDialog(
                initialDay = addBlockForDay,
                existing = editBlock ?: editContentBlock,
                editorMode = if (editContentBlock != null) {
                    ProgramBlockEditorMode.CONTENT
                } else {
                    ProgramBlockEditorMode.STRUCTURE
                },
                weightRepository = weightRepository,
                stretchingRepository = stretchingRepository,
                weightState = weightState,
                cardioState = cardioState,
                stretchState = stretchState,
                stretchCatalog = stretchCatalog,
                unifiedRoutineState = unifiedRoutineState,
                relayPool = relayPool,
                signer = signer,
                onNotify = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                onDismiss = {
                    showAddBlock = false
                    editBlock = null
                    editContentBlock = null
                },
                onSave = { day, block ->
                    scope.launch {
                        var nextProgram = d
                        val oldId = editBlock?.id ?: editContentBlock?.id
                        if (oldId != null) {
                            d.normalizedWeek().forEach { wd ->
                                if (wd.blocks.any { it.id == oldId }) {
                                    nextProgram = nextProgram.withWeekDayUpdated(
                                        wd.copy(blocks = wd.blocks.filterNot { it.id == oldId })
                                    )
                                }
                            }
                        }
                        val weekDay = nextProgram.normalizedWeek().first { it.dayOfWeek == day }
                        val updatedDay = weekDay.copy(blocks = weekDay.blocks + block)
                        val savedProgram = nextProgram.withWeekDayUpdated(updatedDay)
                        persistProgramEdits(savedProgram)
                        showAddBlock = false
                        editBlock = null
                        editContentBlock = null
                    }
                }
            )
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(d.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export JSON") },
                            onClick = {
                                showMenu = false
                                val safeName = d.name.replace(Regex("[^a-zA-Z0-9_-]+"), "_").trim('_').ifBlank { "erv_program" }
                                exportLauncher.launch("$safeName.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete program") },
                            onClick = {
                                showMenu = false
                                pendingDelete = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerColor,
                    titleContentColor = onHeader,
                    navigationIconContentColor = onHeader,
                    actionIconContentColor = onHeader
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    addBlockForDay = 1
                    showAddBlock = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add day block")
            }
        }
    ) { padding ->
        LazyColumn(
            state = weekPlanListState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        OutlinedTextField(
                            value = d.name,
                            onValueChange = {
                                draft = d.copy(
                                    name = it,
                                    lastModifiedEpochSeconds = System.currentTimeMillis() / 1000
                                )
                            },
                            label = { Text("Program name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = d.description.orEmpty(),
                            onValueChange = {
                                draft = d.copy(
                                    description = it.ifBlank { null },
                                    lastModifiedEpochSeconds = System.currentTimeMillis() / 1000
                                )
                            },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = d.sourceLabel.orEmpty(),
                            onValueChange = {
                                draft = d.copy(
                                    sourceLabel = it.ifBlank { null },
                                    lastModifiedEpochSeconds = System.currentTimeMillis() / 1000
                                )
                            },
                            label = { Text("Source label (coach, AI, etc.)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.toggleable(
                                value = programsState.activeProgramId == d.id,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                    scope.launch {
                                        if (checked) {
                                            programRepository.activateProgramForDate(d.id)
                                        } else {
                                            programRepository.setActiveProgram(null)
                                        }
                                        if (relayPool != null && signer != null) {
                                            ProgramSync.publishMaster(
                                                appContext = context.applicationContext,
                                                relayPool = relayPool,
                                                signer = signer,
                                                state = programRepository.currentState(),
                                                dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
                                            )
                                        }
                                        snackbarHostState.showSnackbar(if (checked) "Set as active program" else "Cleared active program")
                                    }
                                }
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = programsState.activeProgramId == d.id,
                                onCheckedChange = null
                            )
                            Text(
                                if (programsState.strategy.mode == ProgramStrategyMode.MANUAL) {
                                    "Active program (shown in your list)"
                                } else {
                                    "Manual fallback program"
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    val toSave = draft ?: return@launch
                                    persistProgramEdits(toSave, "Saved")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save changes")
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Text("Weekly plan", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Each day uses ISO Monday–Sunday. Tap a day chip to scroll. Use Edit Block for day, type, notes, and timing, and use the block-specific content button for exercises, routines, stretches, or cardio setup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp)
                ) {
                    items(7) { idx ->
                        val dow = idx + 1
                        val nBlocks = d.normalizedWeek().first { it.dayOfWeek == dow }.blocks.size
                        FilterChip(
                            selected = false,
                            onClick = {
                                scope.launch {
                                    weekPlanListState.animateScrollToItem(2 + idx)
                                }
                            },
                            label = { Text("${isoDayOfWeekLabel(dow).take(3)} · $nBlocks") }
                        )
                    }
                }
            }

            items(d.normalizedWeek(), key = { it.dayOfWeek }) { day ->
                Text(isoDayOfWeekLabel(day.dayOfWeek), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                if (day.blocks.isEmpty()) {
                    TextButton(onClick = {
                        addBlockForDay = day.dayOfWeek
                        showAddBlock = true
                    }) { Text("Add block") }
                } else {
                    day.blocks.forEachIndexed { index, block ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(
                                Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    programBlockKindLabel(block.kind),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    block.summaryLine(weightState, stretchState, stretchCatalog, cardioState, unifiedRoutineState),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                block.notes?.takeIf { it.isNotBlank() }?.let { n ->
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        n,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    ProgramBlockActionButton(
                                        label = "Up",
                                        icon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                                        enabled = index > 0,
                                        onClick = {
                                            if (index > 0) {
                                                updateBlocksForDay(day.dayOfWeek) { blocks ->
                                                    blocks.toMutableList().also { items ->
                                                        val moved = items.removeAt(index)
                                                        items.add(index - 1, moved)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    ProgramBlockActionButton(
                                        label = "Down",
                                        icon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                                        enabled = index < day.blocks.lastIndex,
                                        onClick = {
                                            if (index < day.blocks.lastIndex) {
                                                updateBlocksForDay(day.dayOfWeek) { blocks ->
                                                    blocks.toMutableList().also { items ->
                                                        val moved = items.removeAt(index)
                                                        items.add(index + 1, moved)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    ProgramBlockActionButton(
                                        label = "Duplicate",
                                        icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                        onClick = {
                                            updateBlocksForDay(day.dayOfWeek) { blocks ->
                                                blocks.toMutableList().also { items ->
                                                    items.add(index + 1, block.copy(id = UUID.randomUUID().toString()))
                                                }
                                            }
                                        }
                                    )
                                    ProgramBlockActionButton(
                                        label = "Edit Block",
                                        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                        onClick = {
                                            editBlock = block
                                            editContentBlock = null
                                            addBlockForDay = day.dayOfWeek
                                        }
                                    )
                                    programBlockContentActionLabel(block.kind)?.let { contentLabel ->
                                        ProgramBlockActionButton(
                                            label = contentLabel,
                                            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                            onClick = {
                                                editBlock = null
                                                editContentBlock = block
                                                addBlockForDay = day.dayOfWeek
                                            }
                                        )
                                    }
                                    ProgramBlockActionButton(
                                        label = "Remove",
                                        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                        onClick = {
                                            updateBlocksForDay(day.dayOfWeek) { blocks ->
                                                blocks.filterNot { it.id == block.id }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = {
                        addBlockForDay = day.dayOfWeek
                        showAddBlock = true
                    }) { Text("Add another block") }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ProgramBlockActionButton(
    label: String,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        icon()
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}

private enum class ProgramBlockEditorMode { STRUCTURE, CONTENT }

private fun programBlockKindLabel(kind: ProgramBlockKind): String = when (kind) {
    ProgramBlockKind.WEIGHT -> "Weight Training"
    ProgramBlockKind.CARDIO -> "Cardio"
    ProgramBlockKind.UNIFIED_ROUTINE -> "Unified Workout"
    ProgramBlockKind.FLEX_TRAINING -> "Flexible Training"
    ProgramBlockKind.STRETCH_ROUTINE -> "Stretch Routine"
    ProgramBlockKind.STRETCH_CATALOG -> "Stretch"
    ProgramBlockKind.HEAT_COLD -> "Heat / Cold"
    ProgramBlockKind.REST -> "Rest"
    ProgramBlockKind.CUSTOM -> "Custom"
    ProgramBlockKind.OTHER -> "Habits"
}

private fun programBlockContentActionLabel(kind: ProgramBlockKind): String? = when (kind) {
    ProgramBlockKind.WEIGHT -> "Exercises"
    ProgramBlockKind.CARDIO -> "Cardio Setup"
    ProgramBlockKind.UNIFIED_ROUTINE -> "Routine"
    ProgramBlockKind.STRETCH_ROUTINE -> "Routine"
    ProgramBlockKind.STRETCH_CATALOG -> "Stretches"
    ProgramBlockKind.HEAT_COLD -> "Heat / Cold"
    ProgramBlockKind.OTHER -> "Checklist"
    ProgramBlockKind.FLEX_TRAINING, ProgramBlockKind.REST, ProgramBlockKind.CUSTOM -> null
}

private fun programBlockContentEditorTitle(kind: ProgramBlockKind): String = when (kind) {
    ProgramBlockKind.WEIGHT -> "Edit Exercises"
    ProgramBlockKind.CARDIO -> "Edit Cardio Setup"
    ProgramBlockKind.UNIFIED_ROUTINE -> "Edit Routine"
    ProgramBlockKind.STRETCH_ROUTINE -> "Edit Stretch Routine"
    ProgramBlockKind.STRETCH_CATALOG -> "Edit Stretches"
    ProgramBlockKind.HEAT_COLD -> "Edit Heat / Cold"
    ProgramBlockKind.OTHER -> "Edit Checklist"
    else -> "Edit Content"
}

private fun programBlockSupportsTargetMinutes(kind: ProgramBlockKind): Boolean =
    kind in setOf(
        ProgramBlockKind.UNIFIED_ROUTINE,
        ProgramBlockKind.FLEX_TRAINING,
        ProgramBlockKind.CARDIO,
        ProgramBlockKind.STRETCH_ROUTINE,
        ProgramBlockKind.STRETCH_CATALOG,
        ProgramBlockKind.HEAT_COLD
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BlockEditorDialog(
    initialDay: Int,
    existing: ProgramDayBlock?,
    editorMode: ProgramBlockEditorMode,
    weightRepository: WeightRepository,
    stretchingRepository: StretchingRepository,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    stretchState: StretchLibraryState,
    stretchCatalog: List<com.erv.app.stretching.StretchCatalogEntry>,
    unifiedRoutineState: UnifiedRoutineLibraryState,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (day: Int, block: ProgramDayBlock) -> Unit
) {
    val blockEditorScope = rememberCoroutineScope()
    val keyManager = LocalKeyManager.current
    val appContext = LocalContext.current.applicationContext
    var weightRoutineEditTarget by remember { mutableStateOf<WeightRoutine?>(null) }
    var showWeightPickExercise by remember { mutableStateOf(false) }
    var dayOfWeek by rememberSaveable { mutableIntStateOf(initialDay) }
    var kind by rememberSaveable {
        mutableStateOf(existing?.kind ?: ProgramBlockKind.WEIGHT)
    }
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    val weightBlockKey = existing?.id ?: "new-block"
    val weightSig = remember(
        weightBlockKey,
        existing?.kind,
        existing?.weightRoutineId,
        existing?.weightExerciseIds
    ) {
        listOf(
            existing?.kind?.name.orEmpty(),
            existing?.weightRoutineId.orEmpty(),
            existing?.weightExerciseIds?.joinToString(",").orEmpty()
        ).joinToString("|")
    }
    var weightRoutineId by rememberSaveable(weightBlockKey, weightSig) {
        mutableStateOf(existing?.weightRoutineId.orEmpty())
    }
    var weightIds by rememberSaveable(weightBlockKey, weightSig) {
        mutableStateOf(initialProgramWeightExerciseIds(existing, weightState))
    }
    var showSaveAsWeightRoutineDialog by remember { mutableStateOf(false) }
    var saveAsRoutineName by remember { mutableStateOf("") }
    var cardioAct by rememberSaveable { mutableStateOf(existing?.cardioActivity ?: CardioBuiltinActivity.RUN.name) }
    var cardioRId by rememberSaveable { mutableStateOf(existing?.cardioRoutineId.orEmpty()) }
    var unifiedRoutineId by rememberSaveable { mutableStateOf(existing?.unifiedRoutineId.orEmpty()) }
    val stretchBlockKey = existing?.id ?: "new-block"
    val stretchSig = remember(
        stretchBlockKey,
        existing?.kind,
        existing?.stretchRoutineId,
        existing?.stretchCatalogIds,
        existing?.stretchHoldSecondsPerStretch
    ) {
        listOf(
            existing?.kind?.name.orEmpty(),
            existing?.stretchRoutineId.orEmpty(),
            existing?.stretchCatalogIds?.joinToString(",").orEmpty(),
            "${existing?.stretchHoldSecondsPerStretch ?: -1}"
        ).joinToString("|")
    }
    var stretchRId by rememberSaveable(stretchBlockKey, stretchSig) {
        mutableStateOf(existing?.stretchRoutineId.orEmpty())
    }
    var stretchIds by rememberSaveable(stretchBlockKey, stretchSig) {
        mutableStateOf(initialProgramStretchIds(existing, stretchState, stretchCatalog))
    }
    var stretchHoldSec by rememberSaveable(stretchBlockKey, stretchSig) {
        mutableStateOf(initialProgramStretchHoldString(existing, stretchState))
    }
    var showStretchPicker by remember { mutableStateOf(false) }
    var showSaveAsStretchRoutineDialog by remember { mutableStateOf(false) }
    var saveAsStretchRoutineName by remember { mutableStateOf("") }
    var heatMode by rememberSaveable { mutableStateOf(existing?.heatColdMode ?: "SAUNA") }
    var targetMin by rememberSaveable { mutableStateOf(existing?.targetMinutes?.toString().orEmpty()) }
    val checklistLines = remember(existing?.id) {
        val src = existing?.checklistItems.orEmpty()
        mutableStateListOf<String>().apply {
            if (src.isEmpty()) add("") else addAll(src)
        }
    }
    val isStructureEditor = editorMode == ProgramBlockEditorMode.STRUCTURE
    val isContentEditor = editorMode == ProgramBlockEditorMode.CONTENT
    val linkedWeightRoutineRefProgram = weightRoutineId.takeIf { it.isNotBlank() }?.let { lid ->
        weightState.routines.firstOrNull { it.id == lid }
    }
    val linkedWeightCanonProgram = linkedWeightRoutineRefProgram?.exerciseIds
        ?.filter { id -> weightState.exerciseById(id) != null }
        .orEmpty()
    val linkedWeightDirtyProgram = linkedWeightRoutineRefProgram != null && weightIds != linkedWeightCanonProgram
    val catalogIdSet = remember(stretchCatalog) { stretchCatalog.map { it.id }.toSet() }
    val linkedStretchRoutineRefProgram = stretchRId.takeIf { it.isNotBlank() }?.let { lid ->
        stretchState.routineById(lid)
    }
    val holdParsedStretchProgram = stretchHoldSec.toIntOrNull()?.coerceIn(5, 300) ?: 30
    val linkedStretchCanonProgram = linkedStretchRoutineRefProgram?.stretchIds
        ?.filter { it in catalogIdSet }
        .orEmpty()
    val linkedStretchDirtyProgram = linkedStretchRoutineRefProgram != null && (
        stretchIds != linkedStretchCanonProgram ||
            holdParsedStretchProgram != linkedStretchRoutineRefProgram.holdSecondsPerStretch.coerceIn(5, 300)
        )

    weightRoutineEditTarget?.let { initial ->
        key(initial.id) {
            WeightRoutineEditorDialog(
                initial = initial,
                exerciseLibrary = weightState.exercises.sortedBy { it.name.lowercase() },
                title = if (weightState.routines.any { it.id == initial.id }) "Edit routine" else "New routine",
                onDismiss = { weightRoutineEditTarget = null },
                onSave = { routine ->
                    blockEditorScope.launch {
                        runCatching {
                            weightRepository.upsertRoutine(routine)
                            if (relayPool != null && signer != null) {
                                val urls = keyManager.relayUrlsForKind30078Publish()
                                val s = weightRepository.currentState()
                                WeightSync.publishExercises(appContext, relayPool, signer, s.exercises, urls)
                                WeightSync.publishRoutines(appContext, relayPool, signer, s.routines, urls)
                            }
                        }
                        weightRoutineId = routine.id
                        weightIds = emptyList()
                        weightRoutineEditTarget = null
                    }
                }
            )
        }
    }

    if (showWeightPickExercise) {
        WeightPickExerciseDialog(
            exercises = weightState.exercises.sortedBy { it.name.lowercase() },
            excludeIds = weightIds.toSet(),
            onDismiss = { showWeightPickExercise = false },
            onPick = { id ->
                weightIds = weightIds + id
                showWeightPickExercise = false
            }
        )
    }

    if (showSaveAsWeightRoutineDialog && isContentEditor && kind == ProgramBlockKind.WEIGHT) {
        AlertDialog(
            onDismissRequest = { showSaveAsWeightRoutineDialog = false },
            title = { Text("Save as weight routine") },
            text = {
                OutlinedTextField(
                    value = saveAsRoutineName,
                    onValueChange = { saveAsRoutineName = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = saveAsRoutineName.trim()
                        if (trimmed.isEmpty() || weightIds.isEmpty()) return@TextButton
                        blockEditorScope.launch {
                            runCatching {
                                val routine = WeightRoutine(
                                    id = UUID.randomUUID().toString(),
                                    name = trimmed,
                                    exerciseIds = weightIds.toList()
                                )
                                weightRepository.upsertRoutine(routine)
                                if (relayPool != null && signer != null) {
                                    val urls = keyManager.relayUrlsForKind30078Publish()
                                    val s = weightRepository.currentState()
                                    WeightSync.publishExercises(appContext, relayPool, signer, s.exercises, urls)
                                    WeightSync.publishRoutines(appContext, relayPool, signer, s.routines, urls)
                                }
                                weightRoutineId = routine.id
                                weightIds = emptyList()
                                saveAsRoutineName = ""
                                showSaveAsWeightRoutineDialog = false
                                onNotify("Saved to Weight Training — open Routines there to run or edit it.")
                            }
                        }
                    },
                    enabled = saveAsRoutineName.trim().isNotEmpty()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsWeightRoutineDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSaveAsStretchRoutineDialog && isContentEditor &&
        (kind == ProgramBlockKind.STRETCH_ROUTINE || kind == ProgramBlockKind.STRETCH_CATALOG)
    ) {
        AlertDialog(
            onDismissRequest = { showSaveAsStretchRoutineDialog = false },
            title = { Text("Save as stretch routine") },
            text = {
                OutlinedTextField(
                    value = saveAsStretchRoutineName,
                    onValueChange = { saveAsStretchRoutineName = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = saveAsStretchRoutineName.trim()
                        if (trimmed.isEmpty() || stretchIds.isEmpty()) return@TextButton
                        blockEditorScope.launch {
                            runCatching {
                                val holdSec = stretchHoldSec.toIntOrNull()?.coerceIn(5, 300) ?: 30
                                val routine = StretchRoutine(
                                    id = UUID.randomUUID().toString(),
                                    name = trimmed,
                                    stretchIds = stretchIds.toList(),
                                    holdSecondsPerStretch = holdSec
                                )
                                stretchingRepository.addRoutine(routine)
                                if (relayPool != null && signer != null) {
                                    StretchingSync.publishRoutinesMaster(
                                        appContext,
                                        relayPool,
                                        signer,
                                        stretchingRepository.currentState(),
                                        keyManager.relayUrlsForKind30078Publish()
                                    )
                                }
                                stretchRId = routine.id
                                stretchIds = emptyList()
                                saveAsStretchRoutineName = ""
                                showSaveAsStretchRoutineDialog = false
                                onNotify("Saved to Stretching — open Routines there to run or edit it.")
                            }
                        }
                    },
                    enabled = saveAsStretchRoutineName.trim().isNotEmpty()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsStretchRoutineDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showStretchPicker && isContentEditor &&
        (kind == ProgramBlockKind.STRETCH_ROUTINE || kind == ProgramBlockKind.STRETCH_CATALOG)
    ) {
        StretchPickStretchDialog(
            catalog = stretchCatalog,
            excludeIds = stretchIds.toSet(),
            onDismiss = { showStretchPicker = false },
            onPick = { id ->
                stretchIds = stretchIds + id
                showStretchPicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    existing == null -> "Add Block"
                    isContentEditor -> programBlockContentEditorTitle(kind)
                    else -> "Edit Block"
                }
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (isStructureEditor) {
                    Text("Day", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        (1..7).forEach { d ->
                            FilterChip(
                                selected = dayOfWeek == d,
                                onClick = { dayOfWeek = d },
                                label = { Text(isoDayOfWeekLabel(d).take(3)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Type", style = MaterialTheme.typography.labelMedium)
                    Column {
                        ProgramBlockKind.entries.forEach { k ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = kind == k,
                                        role = Role.RadioButton,
                                        onValueChange = { if (it) kind = k }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = kind == k, onClick = null)
                                Text(
                                    when (k) {
                                        ProgramBlockKind.FLEX_TRAINING -> "Flexible Training (cardio or weight)"
                                        ProgramBlockKind.OTHER -> "Other (habit checklist)"
                                        else -> programBlockKindLabel(k)
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    if (programBlockSupportsTargetMinutes(kind)) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = targetMin,
                            onValueChange = { targetMin = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Target minutes (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                } else {
                    Text(
                        "Use this screen to edit the content for ${programBlockKindLabel(kind).lowercase()}. Use Edit Block from the main page for day, type, notes, and timing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isContentEditor) when (kind) {
                    ProgramBlockKind.WEIGHT -> {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Pick a saved routine to load its exercises below, or add exercises yourself. " +
                                "Changing the list does not change the saved routine unless you choose update.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        var routineExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = routineExpanded,
                            onExpandedChange = { routineExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = weightState.routines.firstOrNull { it.id == weightRoutineId }?.name
                                    ?: if (weightRoutineId.isBlank()) "None" else "Unknown routine",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Weight routine") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = routineExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = routineExpanded,
                                onDismissRequest = { routineExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None") },
                                    onClick = {
                                        weightRoutineId = ""
                                        routineExpanded = false
                                    }
                                )
                                weightState.routines.forEach { r ->
                                    DropdownMenuItem(
                                        text = { Text(r.name) },
                                        onClick = {
                                            weightRoutineId = r.id
                                            weightIds = r.exerciseIds.filter { id ->
                                                weightState.exerciseById(id) != null
                                            }
                                            routineExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        if (linkedWeightRoutineRefProgram != null && linkedWeightDirtyProgram) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "This list no longer matches \"${linkedWeightRoutineRefProgram.name}\" in Weight Training.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            val basis = linkedWeightRoutineRefProgram
                                            blockEditorScope.launch {
                                                runCatching {
                                                    weightRepository.upsertRoutine(
                                                        basis.copy(exerciseIds = weightIds.toList())
                                                    )
                                                    if (relayPool != null && signer != null) {
                                                        val urls = keyManager.relayUrlsForKind30078Publish()
                                                        val s = weightRepository.currentState()
                                                        WeightSync.publishExercises(
                                                            appContext, relayPool, signer, s.exercises, urls
                                                        )
                                                        WeightSync.publishRoutines(
                                                            appContext, relayPool, signer, s.routines, urls
                                                        )
                                                    }
                                                    onNotify("Updated \"${basis.name}\" in Weight Training.")
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Update saved routine") }
                                    TextButton(
                                        onClick = {
                                            weightRoutineId = ""
                                            onNotify(
                                                "Detached from saved routine. Tap Save on this screen to keep this block as its own exercise list."
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Keep changes on this program block only") }
                                }
                            }
                        }
                        if (weightState.exercises.isEmpty()) {
                            Text(
                                "Your weight exercise library is empty. Open Weight Training → Exercises to add lifts (or sync from relays), then edit this block again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                        } else {
                            FilledTonalButton(
                                onClick = { showWeightPickExercise = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Add Exercise")
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (weightIds.isNotEmpty()) {
                            Text(
                                "Exercises run top to bottom when you start this block from the dashboard.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        weightIds.forEachIndexed { index, id ->
                            val ex = weightState.exerciseById(id)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        ex?.name ?: "Unknown exercise",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    ex?.let {
                                        Text(
                                            "${it.pushOrPull.displayLabel()} · ${it.equipment.displayLabel()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val m = weightIds.toMutableList()
                                                val t = m[index]
                                                m[index] = m[index - 1]
                                                m[index - 1] = t
                                                weightIds = m
                                            }
                                        },
                                        enabled = index > 0
                                    ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move exercise up") }
                                    IconButton(
                                        onClick = {
                                            if (index < weightIds.lastIndex) {
                                                val m = weightIds.toMutableList()
                                                val t = m[index]
                                                m[index] = m[index + 1]
                                                m[index + 1] = t
                                                weightIds = m
                                            }
                                        },
                                        enabled = index < weightIds.lastIndex
                                    ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move exercise down") }
                                    IconButton(
                                        onClick = {
                                            weightIds = weightIds.toMutableList().also { it.removeAt(index) }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove exercise")
                                    }
                                }
                            }
                        }
                        if (weightRoutineId.isBlank() && weightIds.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    saveAsRoutineName = title.trim().ifBlank {
                                        weightIds.firstOrNull()?.let { exId ->
                                            weightState.exerciseById(exId)?.name
                                        }.orEmpty()
                                    }
                                    showSaveAsWeightRoutineDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save exercise order as weight routine…") }
                        }
                        if (weightIds.isEmpty() && weightRoutineId.isBlank() && weightState.exercises.isNotEmpty()) {
                            Text(
                                "No exercises chosen yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                weightRoutineEditTarget = WeightRoutine(
                                    id = UUID.randomUUID().toString(),
                                    name = "",
                                    exerciseIds = emptyList()
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("New weight routine (saves to Weight Training)")
                        }
                        if (weightRoutineId.isNotBlank()) {
                            val existingRoutine = weightState.routines.firstOrNull { it.id == weightRoutineId }
                            if (existingRoutine != null) {
                                Spacer(Modifier.height(4.dp))
                                TextButton(
                                    onClick = { weightRoutineEditTarget = existingRoutine },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Edit this routine in library")
                                }
                            }
                        }
                    }
                    ProgramBlockKind.UNIFIED_ROUTINE -> {
                        Spacer(Modifier.height(12.dp))
                        var unifiedExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = unifiedExpanded,
                            onExpandedChange = { unifiedExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = unifiedRoutineState.routines.firstOrNull { it.id == unifiedRoutineId }?.name
                                    ?: if (unifiedRoutineId.isBlank()) "Choose unified workout" else "Unknown workout",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Unified workout") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unifiedExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = unifiedExpanded,
                                onDismissRequest = { unifiedExpanded = false }
                            ) {
                                unifiedRoutineState.routines.forEach { routine ->
                                    DropdownMenuItem(
                                        text = { Text(routine.name) },
                                        onClick = {
                                            unifiedRoutineId = routine.id
                                            unifiedExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Text(
                            "Unified workouts can combine saved weight, cardio, and stretch segments in one launcher.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    ProgramBlockKind.CARDIO -> {
                        Spacer(Modifier.height(12.dp))
                        var cardioExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = cardioExpanded,
                            onExpandedChange = { cardioExpanded = it }
                        ) {
                            val label = runCatching { CardioBuiltinActivity.valueOf(cardioAct) }.getOrNull()?.name
                                ?: cardioAct
                            OutlinedTextField(
                                value = label.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Activity") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cardioExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = cardioExpanded,
                                onDismissRequest = { cardioExpanded = false }
                            ) {
                                cardioBuiltinActivitiesForUserSelection().forEach { act ->
                                    DropdownMenuItem(
                                        text = { Text(act.name.replace('_', ' ')) },
                                        onClick = {
                                            cardioAct = act.name
                                            cardioExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        var crExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = crExpanded,
                            onExpandedChange = { crExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = cardioState.routines.firstOrNull { it.id == cardioRId }?.name
                                    ?: if (cardioRId.isBlank()) "No cardio routine" else "Unknown",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Cardio routine (optional)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = crExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = crExpanded, onDismissRequest = { crExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("None") },
                                    onClick = {
                                        cardioRId = ""
                                        crExpanded = false
                                    }
                                )
                                cardioState.routines.forEach { r ->
                                    DropdownMenuItem(
                                        text = { Text(r.name) },
                                        onClick = {
                                            cardioRId = r.id
                                            crExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    ProgramBlockKind.FLEX_TRAINING -> {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "This flexible training block can be completed with either a cardio session or a weight workout logged through ERV on that day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "You can leave it flexible for challenge-style templates or switch the block type above to lock in cardio or weight training.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ProgramBlockKind.STRETCH_ROUTINE, ProgramBlockKind.STRETCH_CATALOG -> {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Pick a saved routine to load stretches below, or tap Add stretch. " +
                                "Edits do not change Stretching routines unless you update the saved routine.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        var srExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = srExpanded,
                            onExpandedChange = { srExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = stretchState.routines.firstOrNull { it.id == stretchRId }?.name
                                    ?: if (stretchRId.isBlank()) "None" else "Unknown routine",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Stretch routine") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = srExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = srExpanded, onDismissRequest = { srExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("None") },
                                    onClick = {
                                        stretchRId = ""
                                        srExpanded = false
                                    }
                                )
                                stretchState.routines.forEach { r ->
                                    DropdownMenuItem(
                                        text = { Text(r.name) },
                                        onClick = {
                                            stretchRId = r.id
                                            stretchIds = r.stretchIds.filter { sid -> sid in catalogIdSet }
                                            stretchHoldSec = r.holdSecondsPerStretch.coerceIn(5, 300).toString()
                                            srExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        if (linkedStretchRoutineRefProgram != null && linkedStretchDirtyProgram) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "This setup no longer matches \"${linkedStretchRoutineRefProgram.name}\" in Stretching.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            val basis = linkedStretchRoutineRefProgram
                                            blockEditorScope.launch {
                                                runCatching {
                                                    stretchingRepository.updateRoutine(
                                                        basis.copy(
                                                            stretchIds = stretchIds.toList(),
                                                            holdSecondsPerStretch = holdParsedStretchProgram
                                                        )
                                                    )
                                                    if (relayPool != null && signer != null) {
                                                        StretchingSync.publishRoutinesMaster(
                                                            appContext,
                                                            relayPool,
                                                            signer,
                                                            stretchingRepository.currentState(),
                                                            keyManager.relayUrlsForKind30078Publish()
                                                        )
                                                    }
                                                    onNotify("Updated \"${basis.name}\" in Stretching.")
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Update saved routine") }
                                    TextButton(
                                        onClick = {
                                            stretchRId = ""
                                            onNotify(
                                                "Detached from saved routine. Tap Save on this screen to keep this list on the program block."
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Keep changes on this program block only") }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = stretchHoldSec,
                            onValueChange = { stretchHoldSec = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Hold seconds per stretch") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { showStretchPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Add stretch") }
                        if (stretchIds.isNotEmpty()) {
                            Text(
                                "Order is the order used when you start this block from the dashboard.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        stretchIds.forEachIndexed { index, sid ->
                            val entry = stretchCatalog.firstOrNull { it.id == sid }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(entry?.name ?: "Unknown stretch")
                                        entry?.let {
                                            val side =
                                                if (it.requiresBothSides) "Both sides" else "Single hold"
                                            Text(
                                                "${stretchCategoryDisplayLabel(it.category)} · $side",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    val m = stretchIds.toMutableList()
                                                    val t = m[index]
                                                    m[index] = m[index - 1]
                                                    m[index - 1] = t
                                                    stretchIds = m
                                                }
                                            },
                                            enabled = index > 0
                                        ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move stretch up") }
                                        IconButton(
                                            onClick = {
                                                if (index < stretchIds.lastIndex) {
                                                    val m = stretchIds.toMutableList()
                                                    val t = m[index]
                                                    m[index] = m[index + 1]
                                                    m[index + 1] = t
                                                    stretchIds = m
                                                }
                                            },
                                            enabled = index < stretchIds.lastIndex
                                        ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move stretch down") }
                                        IconButton(onClick = {
                                            stretchIds = stretchIds.toMutableList().also { it.removeAt(index) }
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove stretch")
                                        }
                                    }
                                }
                            }
                        }
                        if (stretchRId.isBlank() && stretchIds.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    saveAsStretchRoutineName = title.trim().ifBlank {
                                        stretchIds.firstOrNull()?.let { id ->
                                            stretchCatalog.firstOrNull { it.id == id }?.name
                                        }.orEmpty()
                                    }
                                    showSaveAsStretchRoutineDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save stretch order as routine…") }
                        }
                    }
                    ProgramBlockKind.HEAT_COLD -> {
                        Spacer(Modifier.height(12.dp))
                        Row {
                            FilterChip(
                                selected = heatMode == "SAUNA",
                                onClick = { heatMode = "SAUNA" },
                                label = { Text("Sauna") }
                            )
                            Spacer(Modifier.padding(4.dp))
                            FilterChip(
                                selected = heatMode == "COLD_PLUNGE",
                                onClick = { heatMode = "COLD_PLUNGE" },
                                label = { Text("Cold plunge") }
                            )
                        }
                    }
                    ProgramBlockKind.OTHER -> {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Checklist lines appear on the dashboard for the selected date so you can tap each habit when done (diet, reading, water, progress photo, etc.).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        checklistLines.forEachIndexed { index, line ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = line,
                                    onValueChange = { v -> checklistLines[index] = v },
                                    label = { Text("Item ${index + 1}") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = false,
                                    maxLines = 2
                                )
                                IconButton(
                                    onClick = {
                                        if (checklistLines.size > 1) checklistLines.removeAt(index)
                                    },
                                    enabled = checklistLines.size > 1
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove line")
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        TextButton(
                            onClick = { checklistLines.add("") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Add checklist line") }
                    }
                    ProgramBlockKind.REST, ProgramBlockKind.CUSTOM -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Use title and notes above. Custom is free-form; Rest is for recovery days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val mins = targetMin.toIntOrNull()
                    val (outWeightRoutineId, outWeightExerciseIds) =
                        if (kind == ProgramBlockKind.WEIGHT) {
                            val wLib = weightRoutineId.takeIf { it.isNotBlank() }?.let { lid ->
                                weightState.routines.firstOrNull { r -> r.id == lid }
                            }
                            val wCanon = wLib?.exerciseIds
                                ?.filter { id -> weightState.exerciseById(id) != null }
                                .orEmpty()
                            val wLinked = wLib != null && weightIds == wCanon
                            if (wLinked) {
                                requireNotNull(wLib).id to emptyList()
                            } else {
                                null to weightIds
                            }
                        } else {
                            null to emptyList()
                        }
                    val stretchLib = if (kind == ProgramBlockKind.STRETCH_ROUTINE ||
                        kind == ProgramBlockKind.STRETCH_CATALOG
                    ) {
                        stretchRId.takeIf { it.isNotBlank() }?.let { stretchState.routineById(it) }
                    } else {
                        null
                    }
                    val stretchHoldParsed = stretchHoldSec.toIntOrNull()?.coerceIn(5, 300) ?: 30
                    val stretchCanonical = stretchLib?.stretchIds?.filter { it in catalogIdSet }.orEmpty()
                    val stretchLinked = stretchLib != null && stretchIds == stretchCanonical &&
                        stretchHoldParsed == stretchLib.holdSecondsPerStretch.coerceIn(5, 300)
                    val outBlockKind = when {
                        kind == ProgramBlockKind.STRETCH_ROUTINE || kind == ProgramBlockKind.STRETCH_CATALOG -> {
                            if (stretchLinked) ProgramBlockKind.STRETCH_ROUTINE else ProgramBlockKind.STRETCH_CATALOG
                        }
                        else -> kind
                    }
                    val outStretchRoutineId =
                        if ((kind == ProgramBlockKind.STRETCH_ROUTINE || kind == ProgramBlockKind.STRETCH_CATALOG) &&
                            stretchLinked
                        ) {
                            requireNotNull(stretchLib).id
                        } else {
                            null
                        }
                    val outStretchCatalogIds =
                        if ((kind == ProgramBlockKind.STRETCH_ROUTINE || kind == ProgramBlockKind.STRETCH_CATALOG) &&
                            !stretchLinked
                        ) {
                            stretchIds
                        } else {
                            emptyList()
                        }
                    val outStretchHold =
                        if ((kind == ProgramBlockKind.STRETCH_ROUTINE || kind == ProgramBlockKind.STRETCH_CATALOG) &&
                            !stretchLinked
                        ) {
                            stretchHoldParsed
                        } else {
                            null
                        }
                    val block = ProgramDayBlock(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        kind = outBlockKind,
                        title = title.ifBlank { null },
                        notes = notes.ifBlank { null },
                        weightExerciseIds = outWeightExerciseIds,
                        weightRoutineId = outWeightRoutineId,
                        cardioActivity = if (kind == ProgramBlockKind.CARDIO) cardioAct else null,
                        cardioRoutineId = if (kind == ProgramBlockKind.CARDIO) cardioRId.ifBlank { null } else null,
                        unifiedRoutineId = if (kind == ProgramBlockKind.UNIFIED_ROUTINE) unifiedRoutineId.ifBlank { null } else null,
                        stretchRoutineId = outStretchRoutineId,
                        stretchCatalogIds = outStretchCatalogIds,
                        stretchHoldSecondsPerStretch = outStretchHold,
                        heatColdMode = if (kind == ProgramBlockKind.HEAT_COLD) heatMode else null,
                        targetMinutes = if (programBlockSupportsTargetMinutes(kind)) mins else null,
                        checklistItems = if (kind == ProgramBlockKind.OTHER) {
                            checklistLines.map { it.trim() }.filter { it.isNotBlank() }
                        } else {
                            emptyList()
                        }
                    )
                    onSave(dayOfWeek, block)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
