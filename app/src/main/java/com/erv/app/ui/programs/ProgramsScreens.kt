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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.erv.app.cardio.CardioRoutine
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
import com.erv.app.programs.ProgramSync
import com.erv.app.programs.ProgramTemplateOption
import com.erv.app.programs.ProgramWeekDay
import com.erv.app.programs.ProgramsLibraryState
import com.erv.app.programs.isoDayOfWeekLabel
import com.erv.app.programs.normalizedWeek
import com.erv.app.programs.summaryLine
import com.erv.app.programs.withWeekDayUpdated
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.stretching.StretchRoutine
import com.erv.app.ui.theme.ErvHeaderRed
import com.erv.app.unifiedroutines.UnifiedRoutine
import com.erv.app.unifiedroutines.UnifiedRoutineLibraryState
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightRoutine
import com.erv.app.weighttraining.WeightSync
import com.erv.app.weighttraining.displayLabel
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
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
            if (setActive) {
                programRepository.setActiveProgram(program.id)
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
            snackbarHostState.showSnackbar("Created \"${program.name}\"")
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
                        (env.activeProgramId?.let { "\nSet active: $it" } ?: "")
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
                                setActiveFromImport = toApply.activeProgramId
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
                items(state.programs, key = { it.id }) { p ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (p.id == state.activeProgramId) {
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
                                    if (p.id == state.activeProgramId) {
                                        Text(
                                            "Active",
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
fun ProgramDetailScreen(
    programId: String,
    programRepository: ProgramRepository,
    weightRepository: WeightRepository,
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
    var showQuickAttach by remember { mutableStateOf(false) }
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
            val envelope = ProgramImportEnvelope(programs = listOf(d), activeProgramId = d.id)
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

    if (showAddBlock || editBlock != null) {
        key(editBlock?.id ?: "new", showAddBlock) {
            BlockEditorDialog(
                initialDay = addBlockForDay,
                existing = editBlock,
                weightRepository = weightRepository,
                weightState = weightState,
                cardioState = cardioState,
                stretchState = stretchState,
                stretchCatalog = stretchCatalog,
                unifiedRoutineState = unifiedRoutineState,
                relayPool = relayPool,
                signer = signer,
                onDismiss = {
                    showAddBlock = false
                    editBlock = null
                },
                onSave = { day, block ->
                    var nextProgram = d
                    val oldId = editBlock?.id
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
                    draft = nextProgram.withWeekDayUpdated(updatedDay)
                    showAddBlock = false
                    editBlock = null
                }
            )
        }
    }

    if (showQuickAttach) {
        val attachSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showQuickAttach = false },
            sheetState = attachSheetState
        ) {
            QuickAttachRoutineSheet(
                weightLibrary = weightState,
                weightRoutines = weightState.routines.sortedBy { it.name.lowercase() },
                cardioRoutines = cardioState.routines.sortedBy { it.name.lowercase() },
                stretchRoutines = stretchState.routines.sortedBy { it.name.lowercase() },
                unifiedRoutines = unifiedRoutineState.routines.sortedBy { it.name.lowercase() },
                onDismiss = { showQuickAttach = false },
                onAttach = { day, block ->
                    val weekDay = d.normalizedWeek().first { it.dayOfWeek == day }
                    draft = d.withWeekDayUpdated(weekDay.copy(blocks = weekDay.blocks + block))
                    showQuickAttach = false
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
                                        programRepository.setActiveProgram(if (checked) d.id else null)
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
                            Text("Active program (shown in your list)", modifier = Modifier.padding(start = 8.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    val toSave = draft ?: return@launch
                                    programRepository.upsertProgram(toSave)
                                    if (relayPool != null && signer != null) {
                                        ProgramSync.publishMaster(
                                            appContext = context.applicationContext,
                                            relayPool = relayPool,
                                            signer = signer,
                                            state = programRepository.currentState(),
                                            dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
                                        )
                                    }
                                    draft = programRepository.currentState().programById(programId)
                                    snackbarHostState.showSnackbar("Saved")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save changes")
                        }
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { showQuickAttach = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Attach saved routine to a day")
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Text("Weekly plan", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Each day uses ISO Monday–Sunday. Tap a day chip to scroll. Use the arrows to reorder blocks, duplicate to reuse them, and edit a block to move it to a different day.",
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
                                    block.kind.name.replace('_', ' ').lowercase()
                                        .replaceFirstChar { it.titlecase() },
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
                                        label = "Edit",
                                        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                        onClick = {
                                            editBlock = block
                                            addBlockForDay = day.dayOfWeek
                                        }
                                    )
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BlockEditorDialog(
    initialDay: Int,
    existing: ProgramDayBlock?,
    weightRepository: WeightRepository,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    stretchState: StretchLibraryState,
    stretchCatalog: List<com.erv.app.stretching.StretchCatalogEntry>,
    unifiedRoutineState: UnifiedRoutineLibraryState,
    relayPool: RelayPool?,
    signer: EventSigner?,
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
    var weightIds by remember { mutableStateOf(existing?.weightExerciseIds.orEmpty()) }
    var weightRoutineId by rememberSaveable { mutableStateOf(existing?.weightRoutineId.orEmpty()) }
    var cardioAct by rememberSaveable { mutableStateOf(existing?.cardioActivity ?: CardioBuiltinActivity.RUN.name) }
    var cardioRId by rememberSaveable { mutableStateOf(existing?.cardioRoutineId.orEmpty()) }
    var unifiedRoutineId by rememberSaveable { mutableStateOf(existing?.unifiedRoutineId.orEmpty()) }
    var stretchRId by rememberSaveable { mutableStateOf(existing?.stretchRoutineId.orEmpty()) }
    var stretchCatIds by remember { mutableStateOf(existing?.stretchCatalogIds?.toSet() ?: emptySet()) }
    var heatMode by rememberSaveable { mutableStateOf(existing?.heatColdMode ?: "SAUNA") }
    var targetMin by rememberSaveable { mutableStateOf(existing?.targetMinutes?.toString().orEmpty()) }
    val checklistLines = remember(existing?.id) {
        val src = existing?.checklistItems.orEmpty()
        mutableStateListOf<String>().apply {
            if (src.isEmpty()) add("") else addAll(src)
        }
    }

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
                weightRoutineId = ""
                showWeightPickExercise = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add block" else "Edit block") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
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
                                    ProgramBlockKind.UNIFIED_ROUTINE -> "Unified Workout"
                                    ProgramBlockKind.FLEX_TRAINING -> "Workout (cardio or weight)"
                                    ProgramBlockKind.OTHER -> "Other (habit checklist)"
                                    else -> k.name.replace('_', ' ').lowercase()
                                        .replaceFirstChar { it.titlecase() }
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

                when (kind) {
                    ProgramBlockKind.WEIGHT -> {
                        Spacer(Modifier.height(12.dp))
                        Text("Exercises", style = MaterialTheme.typography.labelMedium)
                        if (weightState.exercises.isEmpty()) {
                            Text(
                                "Your weight exercise library is empty. Open Weight Training → Exercises to add lifts (or sync from relays), then edit this block again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                        } else {
                            Text(
                                "Add lifts from your library using muscle group and push/pull filters, or attach a saved routine below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = { showWeightPickExercise = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Add exercise from library")
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        weightIds.forEachIndexed { index, id ->
                            val ex = weightState.exerciseById(id)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            ex?.name ?: "Unknown exercise",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        ex?.let {
                                            Text(
                                                "${it.muscleGroup} · ${it.pushOrPull.displayLabel()} · ${it.equipment.displayLabel()}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
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
                        if (weightIds.isEmpty() && weightRoutineId.isBlank() && weightState.exercises.isNotEmpty()) {
                            Text(
                                "No exercises chosen yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        var routineExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = routineExpanded,
                            onExpandedChange = { routineExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = weightState.routines.firstOrNull { it.id == weightRoutineId }?.name
                                    ?: if (weightRoutineId.isBlank()) "No routine" else "Unknown routine",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Weight routine (optional)") },
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
                                            weightIds = emptyList()
                                            routineExpanded = false
                                        }
                                    )
                                }
                            }
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
                                    ?: if (unifiedRoutineId.isBlank()) "Choose unified routine" else "Unknown routine",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Unified routine") },
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
                            "Unified routines can combine saved weight, cardio, and stretch segments in one launcher.",
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
                            "This block can be completed with either a cardio session or a weight workout logged through ERV on that day.",
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
                    ProgramBlockKind.STRETCH_ROUTINE -> {
                        Spacer(Modifier.height(12.dp))
                        var srExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = srExpanded,
                            onExpandedChange = { srExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = stretchState.routines.firstOrNull { it.id == stretchRId }?.name
                                    ?: if (stretchRId.isBlank()) "Choose routine" else "Unknown",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Stretch routine") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = srExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = srExpanded, onDismissRequest = { srExpanded = false }) {
                                stretchState.routines.forEach { r ->
                                    DropdownMenuItem(
                                        text = { Text(r.name) },
                                        onClick = {
                                            stretchRId = r.id
                                            srExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    ProgramBlockKind.STRETCH_CATALOG -> {
                        Spacer(Modifier.height(12.dp))
                        Text("Catalog poses", style = MaterialTheme.typography.labelMedium)
                        stretchCatalog.sortedBy { it.name.lowercase() }.forEach { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = stretchCatIds.contains(entry.id),
                                        role = Role.Checkbox,
                                        onValueChange = { checked ->
                                            stretchCatIds =
                                                if (checked) stretchCatIds + entry.id else stretchCatIds - entry.id
                                        }
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = stretchCatIds.contains(entry.id), onCheckedChange = null)
                                Text(entry.name, modifier = Modifier.padding(start = 8.dp))
                            }
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

                if (kind in setOf(
                        ProgramBlockKind.UNIFIED_ROUTINE,
                        ProgramBlockKind.FLEX_TRAINING,
                        ProgramBlockKind.CARDIO,
                        ProgramBlockKind.STRETCH_ROUTINE,
                        ProgramBlockKind.STRETCH_CATALOG,
                        ProgramBlockKind.HEAT_COLD
                    )
                ) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetMin,
                        onValueChange = { targetMin = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Target minutes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val mins = targetMin.toIntOrNull()
                    val routineIdOrNull = weightRoutineId.ifBlank { null }
                    val block = ProgramDayBlock(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        kind = kind,
                        title = title.ifBlank { null },
                        notes = notes.ifBlank { null },
                        weightExerciseIds = if (routineIdOrNull != null) emptyList() else weightIds.toList(),
                        weightRoutineId = routineIdOrNull,
                        cardioActivity = if (kind == ProgramBlockKind.CARDIO) cardioAct else null,
                        cardioRoutineId = cardioRId.ifBlank { null },
                        unifiedRoutineId = if (kind == ProgramBlockKind.UNIFIED_ROUTINE) unifiedRoutineId.ifBlank { null } else null,
                        stretchRoutineId = stretchRId.ifBlank { null },
                        stretchCatalogIds = stretchCatIds.toList(),
                        heatColdMode = if (kind == ProgramBlockKind.HEAT_COLD) heatMode else null,
                        targetMinutes = mins,
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

private enum class QuickAttachKind { WEIGHT, CARDIO, STRETCH, UNIFIED }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickAttachRoutineSheet(
    weightLibrary: WeightLibraryState,
    weightRoutines: List<WeightRoutine>,
    cardioRoutines: List<CardioRoutine>,
    stretchRoutines: List<StretchRoutine>,
    unifiedRoutines: List<UnifiedRoutine>,
    onDismiss: () -> Unit,
    onAttach: (day: Int, block: ProgramDayBlock) -> Unit,
) {
    var kindName by rememberSaveable { mutableStateOf(QuickAttachKind.WEIGHT.name) }
    var dayOfWeek by rememberSaveable { mutableIntStateOf(1) }
    var search by rememberSaveable { mutableStateOf("") }
    val kind = QuickAttachKind.entries.firstOrNull { it.name == kindName } ?: QuickAttachKind.WEIGHT
    val q = search.trim().lowercase()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Attach saved routine", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onDismiss) { Text("Close") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Day of week", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
        Text("Library", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = kind == QuickAttachKind.WEIGHT,
                onClick = { kindName = QuickAttachKind.WEIGHT.name },
                label = { Text("Weight") }
            )
            FilterChip(
                selected = kind == QuickAttachKind.CARDIO,
                onClick = { kindName = QuickAttachKind.CARDIO.name },
                label = { Text("Cardio") }
            )
            FilterChip(
                selected = kind == QuickAttachKind.STRETCH,
                onClick = { kindName = QuickAttachKind.STRETCH.name },
                label = { Text("Stretch") }
            )
            FilterChip(
                selected = kind == QuickAttachKind.UNIFIED,
                onClick = { kindName = QuickAttachKind.UNIFIED.name },
                label = { Text("Unified") }
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search routines") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (kind) {
                QuickAttachKind.WEIGHT -> {
                    val rows = weightRoutines.filter { r -> q.isEmpty() || r.name.lowercase().contains(q) }
                    if (rows.isEmpty()) {
                        item {
                            Text(
                                "No weight routines match. Create one with “New weight routine” in a weight block, or in Weight Training → Routines.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(rows, key = { it.id }) { r ->
                            val preview = r.exerciseIds.mapNotNull { id -> weightLibrary.exerciseById(id)?.name }
                                .take(4)
                                .joinToString(" → ")
                            Card(
                                onClick = {
                                    onAttach(
                                        dayOfWeek,
                                        ProgramDayBlock(
                                            kind = ProgramBlockKind.WEIGHT,
                                            title = r.name,
                                            weightRoutineId = r.id
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(r.name, style = MaterialTheme.typography.titleSmall)
                                    if (preview.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            preview + if (r.exerciseIds.size > 4) "…" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                QuickAttachKind.CARDIO -> {
                    val rows = cardioRoutines.filter { r -> q.isEmpty() || r.name.lowercase().contains(q) }
                    if (rows.isEmpty()) {
                        item {
                            Text(
                                "No cardio routines match. Build routines in the Cardio category.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(rows, key = { it.id }) { r ->
                            Card(
                                onClick = {
                                    onAttach(
                                        dayOfWeek,
                                        ProgramDayBlock(
                                            kind = ProgramBlockKind.CARDIO,
                                            title = r.name,
                                            cardioRoutineId = r.id
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(r.name, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
                QuickAttachKind.STRETCH -> {
                    val rows = stretchRoutines.filter { r -> q.isEmpty() || r.name.lowercase().contains(q) }
                    if (rows.isEmpty()) {
                        item {
                            Text(
                                "No stretch routines match. Build routines in Stretching.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(rows, key = { it.id }) { r ->
                            Card(
                                onClick = {
                                    onAttach(
                                        dayOfWeek,
                                        ProgramDayBlock(
                                            kind = ProgramBlockKind.STRETCH_ROUTINE,
                                            title = r.name,
                                            stretchRoutineId = r.id
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(r.name, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
                QuickAttachKind.UNIFIED -> {
                    val rows = unifiedRoutines.filter { r -> q.isEmpty() || r.name.lowercase().contains(q) }
                    if (rows.isEmpty()) {
                        item {
                            Text(
                                "No unified workouts match. Build them in Unified Workouts.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(rows, key = { it.id }) { r ->
                            Card(
                                onClick = {
                                    onAttach(
                                        dayOfWeek,
                                        ProgramDayBlock(
                                            kind = ProgramBlockKind.UNIFIED_ROUTINE,
                                            title = r.name,
                                            unifiedRoutineId = r.id
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(r.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${r.blocks.size} block(s)",
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
    }
}
