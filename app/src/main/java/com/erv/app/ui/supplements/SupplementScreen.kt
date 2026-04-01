@file:OptIn(ExperimentalLayoutApi::class)
package com.erv.app.ui.supplements

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.LibraryStateMerge
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPayloadDigestStore
import com.erv.app.nostr.RelayPool
import com.erv.app.reminders.RoutineReminder
import com.erv.app.reminders.RoutineReminderDraft
import com.erv.app.reminders.RoutineReminderRepository
import com.erv.app.reminders.RoutineReminderState
import com.erv.app.reminders.isValid
import com.erv.app.reminders.toDraft
import com.erv.app.reminders.toReminder
import com.erv.app.ui.reminders.RoutineReminderFormSection
import com.erv.app.supplements.OpenFoodFactsClient
import com.erv.app.supplements.SupplementApiClient
import com.erv.app.supplements.SupplementApiResult
import com.erv.app.supplements.parseNihServingSizeToDosage
import com.erv.app.supplements.SupplementBarcodeLookup
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
import com.erv.app.supplements.activityStyleSummary
import com.erv.app.supplements.label
import com.erv.app.supplements.shortLabel
import com.erv.app.supplements.SupplementSync
import com.erv.app.supplements.SupplementDayLogSection
import com.erv.app.supplements.groupIntoDaySectionsNewestFirst
import com.erv.app.supplements.isFromRoutine
import com.erv.app.supplements.routineNameFromSource
import com.erv.app.SectionLogDateFilter
import com.erv.app.supplements.datedSupplementEntriesForSectionLog
import com.erv.app.ui.dashboard.SectionLogCalendarSheet
import com.erv.app.ui.dashboard.SectionLogFilterBar
import com.erv.app.ui.dashboard.datesWithSupplementActivity
import com.erv.app.ui.theme.ErvHeaderRed
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
private enum class SupplementsTab { Supplements, Routines }

// Match Light Therapy header color scheme: lighter red top bar, darker wine tab row
private val SupplementRedDark = Color(0xFF4A0E0E)
private val SupplementRedMid = Color(0xFF8B0000)

private val SupplementLogFriendlyDate: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())

private data class RoutineStepDraft(
    val uiKey: String = UUID.randomUUID().toString(),
    val supplementId: String? = null,
    val timeOfDay: SupplementTimeOfDay = SupplementTimeOfDay.MORNING,
    val quantity: String = "1",
    val dosageOverride: String = "",
    val note: String = "",
    val isExpanded: Boolean = true,
    val paused: Boolean = false
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

private data class CreateSupplementBootstrap(
    val sessionKey: String,
    val initialDraft: SupplementDraft,
    val pendingNih: SupplementApiResult? = null
)

private data class BarcodeLookupState(
    val rawCode: String,
    val results: List<SupplementApiResult>,
    val lookupSummary: String,
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
    var showRoutineTimePicker by remember { mutableStateOf(false) }
    var initialTimeForNewRoutine by remember { mutableStateOf<SupplementTimeOfDay?>(null) }
    val singleTimeSlots = remember { listOf(SupplementTimeOfDay.MORNING, SupplementTimeOfDay.MIDDAY, SupplementTimeOfDay.NIGHT) }

    val supplementApiClient = remember { SupplementApiClient() }
    val openFoodFactsClient = remember { OpenFoodFactsClient() }
    var showAddSupplementChooser by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var barcodeLookup by remember { mutableStateOf<BarcodeLookupState?>(null) }
    var createBootstrap by remember { mutableStateOf<CreateSupplementBootstrap?>(null) }
    var barcodeSearchBusy by remember { mutableStateOf(false) }
    val keyManager = LocalKeyManager.current

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showBarcodeScanner = true
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Camera permission denied — use manual entry or try again.")
            }
        }
    }

    suspend fun syncMaster() {
        if (relayPool != null && signer != null) {
            SupplementSync.publishMaster(
                context.applicationContext,
                relayPool,
                signer,
                repository.currentState(),
                keyManager.relayUrlsForKind30078Publish(),
            )
        }
    }

    suspend fun syncDailyLog(log: SupplementDayLog) {
        if (relayPool != null && signer != null) {
            SupplementSync.publishDailyLog(
                context.applicationContext,
                relayPool,
                signer,
                log,
                keyManager.relayUrlsForKind30078Publish(),
            )
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
                    containerColor = ErvHeaderRed,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                        onRequestAddSupplementChooser = { showAddSupplementChooser = true },
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
                        createBootstrap = createBootstrap,
                        onDismissSupplementEditor = {
                            supplementEditor = null
                            creatingSupplement = false
                            createBootstrap = null
                        },
                        onResetSupplementEditorMode = { creatingSupplement = false },
                        onCreateSupplement = { name, brand, dosagePlan, notes, nih ->
                            scope.launch {
                                val created = repository.addSupplement(name, brand, dosagePlan, notes)
                                if (nih != null) {
                                    repository.attachInfo(created.id, nih.info, nih.productId)
                                }
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
                        showRoutineTimePicker = true
                    },
                    showRoutineTimePicker = showRoutineTimePicker,
                    onTimeSelectedForNew = { slot ->
                        if (slot in singleTimeSlots && state.routines.any { it.timeOfDay == slot }) {
                            scope.launch {
                                snackbarHostState.showSnackbar("You already have a ${slot.label()} routine. Edit it instead.")
                            }
                        } else {
                            initialTimeForNewRoutine = slot
                            creatingRoutine = true
                            showRoutineTimePicker = false
                        }
                    },
                    onDismissRoutineTimePicker = { showRoutineTimePicker = false },
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
                            val ok = repository.logRoutineRun(today, routine.id)
                            if (ok) {
                                repository.currentState().logFor(today)?.let { syncDailyLog(it) }
                                snackbarHostState.showSnackbar("Logged ${routine.name}")
                            } else {
                                snackbarHostState.showSnackbar("Nothing to log — all steps are paused or missing supplements.")
                            }
                        }
                    },
                    onCreateRoutine = { name, timeOfDay, steps, notes, reminderDraft ->
                        scope.launch {
                            val created = repository.addRoutine(name, timeOfDay, steps, notes)
                            val scheduled = reminderRepository.upsertReminder(reminderDraft.toReminder(created.id, created.name))
                            syncMaster()
                            snackbarHostState.showSnackbar("Routine saved")
                            if (reminderDraft.enabled && !scheduled) {
                                snackbarHostState.showSnackbar("Enable exact alarms for reminder notifications")
                            }
                        }
                    },
                    onUpdateRoutine = { id, name, timeOfDay, steps, notes, reminderDraft ->
                        scope.launch {
                            repository.renameRoutine(id, name, timeOfDay, steps, notes)
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
                        initialTimeForNewRoutine = null
                    },
                    onResetRoutineEditorMode = { creatingRoutine = false },
                    initialTimeForNewRoutine = initialTimeForNewRoutine,
                    supplements = state.supplements,
                )
                }
            }

            if (barcodeSearchBusy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            if (showBarcodeScanner) {
                SupplementBarcodeScannerScreen(
                    headerColor = ErvHeaderRed,
                    onBarcode = { code ->
                        showBarcodeScanner = false
                        scope.launch {
                            barcodeSearchBusy = true
                            val resolution = SupplementBarcodeLookup.resolve(
                                scannedCode = code,
                                offClient = openFoodFactsClient,
                                dsldClient = supplementApiClient
                            )
                            barcodeSearchBusy = false
                            if (resolution.dsldResults.isEmpty()) {
                                snackbarHostState.showSnackbar(
                                    if (resolution.offFoundProduct) {
                                        "No NIH DSLD match (Open Food Facts had a product name)"
                                    } else {
                                        "No NIH DSLD match for this barcode"
                                    }
                                )
                                createBootstrap = CreateSupplementBootstrap(
                                    sessionKey = UUID.randomUUID().toString(),
                                    initialDraft = SupplementDraft(
                                        name = resolution.suggestedNameFromOff.orEmpty(),
                                        brand = resolution.suggestedBrandFromOff.orEmpty(),
                                        notes = buildString {
                                            append("Barcode $code. ")
                                            append(resolution.userMessage)
                                        }
                                    ),
                                    pendingNih = null
                                )
                                creatingSupplement = true
                                supplementEditor = null
                            } else {
                                barcodeLookup = BarcodeLookupState(
                                    rawCode = code,
                                    results = resolution.dsldResults,
                                    lookupSummary = resolution.userMessage
                                )
                            }
                        }
                    },
                    onClose = { showBarcodeScanner = false }
                )
            }
        }
    }

    if (showAddSupplementChooser) {
        AlertDialog(
            onDismissRequest = { showAddSupplementChooser = false },
            title = { Text("Add supplement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Scan looks up the code on Open Food Facts (open data, ODbL), then searches NIH DSLD by product name for label details. " +
                            "Either path can miss; you can always add manually and search NIH on the supplement screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = {
                            showAddSupplementChooser = false
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                showBarcodeScanner = true
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan barcode")
                    }
                    FilledTonalButton(
                        onClick = {
                            showAddSupplementChooser = false
                            createBootstrap = CreateSupplementBootstrap(
                                sessionKey = UUID.randomUUID().toString(),
                                initialDraft = SupplementDraft(),
                                pendingNih = null
                            )
                            creatingSupplement = true
                            supplementEditor = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enter manually")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddSupplementChooser = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    barcodeLookup?.let { lookup ->
        AlertDialog(
            onDismissRequest = { barcodeLookup = null },
            title = { Text("NIH results") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        lookup.lookupSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Barcode ${lookup.rawCode}. Tap a row to use that NIH label, or choose manual below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Open Food Facts data is ODbL — see openfoodfacts.org.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    lookup.results.forEach { result ->
                        ElevatedCard(
                            onClick = {
                                createBootstrap = CreateSupplementBootstrap(
                                    sessionKey = UUID.randomUUID().toString(),
                                    initialDraft = supplementDraftFromNihSearchResult(result),
                                    pendingNih = result
                                )
                                creatingSupplement = true
                                supplementEditor = null
                                barcodeLookup = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
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
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        createBootstrap = CreateSupplementBootstrap(
                            sessionKey = UUID.randomUUID().toString(),
                            initialDraft = SupplementDraft(
                                notes = "Barcode ${lookup.rawCode} — none of the NIH results matched this bottle."
                            ),
                            pendingNih = null
                        )
                        creatingSupplement = true
                        supplementEditor = null
                        barcodeLookup = null
                    }
                ) {
                    Text("None of these — manual")
                }
            }
        )
    }
}

@Composable
private fun RoutinesTab(
    state: SupplementLibraryState,
    supplements: List<SupplementEntry>,
    reminderForRoutine: (String) -> RoutineReminder?,
    onAddClick: () -> Unit,
    showRoutineTimePicker: Boolean,
    onTimeSelectedForNew: (SupplementTimeOfDay) -> Unit,
    onDismissRoutineTimePicker: () -> Unit,
    onEditRoutine: (SupplementRoutine) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onRunRoutine: (SupplementRoutine) -> Unit,
    onCreateRoutine: (String, SupplementTimeOfDay, List<SupplementRoutineStep>, String, RoutineReminderDraft) -> Unit,
    onUpdateRoutine: (String, String, SupplementTimeOfDay, List<SupplementRoutineStep>, String, RoutineReminderDraft) -> Unit,
    routineEditor: SupplementRoutine?,
    creatingRoutine: Boolean,
    onDismissRoutineEditor: () -> Unit,
    onResetRoutineEditorMode: () -> Unit,
    initialTimeForNewRoutine: SupplementTimeOfDay?
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.routines.isEmpty()) {
            EmptyState(
                title = "No routines yet",
                subtitle = "Add a routine for Morning, Midday, Night, or Other. One routine per time (except Other)."
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
                            Text(
                                if (routine.timeOfDay == SupplementTimeOfDay.OTHER) routine.name else "${routine.timeOfDay.label()} • ${routine.name}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                routine.steps.forEach { step ->
                                    val supplement = supplements.firstOrNull { it.id == step.supplementId }
                                    Text(
                                        text = step.activityStyleSummary(supplement),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(onClick = { onRunRoutine(routine) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Log")
                                }
                                IconButton(onClick = { onEditRoutine(routine) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit routine")
                                }
                                IconButton(onClick = { onDeleteRoutine(routine.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete routine")
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

    if (showRoutineTimePicker) {
        AlertDialog(
            onDismissRequest = onDismissRoutineTimePicker,
            title = { Text("Select time of day") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "One routine per Morning, Midday, or Night. You can create multiple \"Other\" routines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        SupplementTimeOfDay.MORNING,
                        SupplementTimeOfDay.MIDDAY,
                        SupplementTimeOfDay.NIGHT,
                        SupplementTimeOfDay.OTHER
                    ).forEach { slot ->
                        val alreadyExists = slot != SupplementTimeOfDay.OTHER && state.routines.any { it.timeOfDay == slot }
                        FilledTonalButton(
                            onClick = { onTimeSelectedForNew(slot) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !alreadyExists
                        ) {
                            Text(
                                if (alreadyExists) "${slot.label()} (already have one — edit it)"
                                else slot.label()
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismissRoutineTimePicker) { Text("Cancel") } }
        )
    }
    if (creatingRoutine || routineEditor != null) {
        RoutineEditorDialog(
            routine = routineEditor,
            creating = creatingRoutine,
            initialTimeOfDay = routineEditor?.timeOfDay ?: initialTimeForNewRoutine ?: SupplementTimeOfDay.MORNING,
            supplements = supplements,
            existingReminder = routineEditor?.id?.let(reminderForRoutine),
            onDismiss = onDismissRoutineEditor,
            onDismissReset = onResetRoutineEditorMode,
            onSave = { id, name, timeOfDay, steps, notes, reminderDraft ->
                if (id == null) onCreateRoutine(name, timeOfDay, steps, notes, reminderDraft)
                else onUpdateRoutine(id, name, timeOfDay, steps, notes, reminderDraft)
                onResetRoutineEditorMode()
                onDismissRoutineEditor()
            }
        )
    }
}

@Composable
private fun SupplementsTabContent(
    state: SupplementLibraryState,
    onRequestAddSupplementChooser: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onTakeNow: (SupplementEntry) -> Unit,
    supplementEditor: SupplementEntry?,
    creatingSupplement: Boolean,
    createBootstrap: CreateSupplementBootstrap?,
    onDismissSupplementEditor: () -> Unit,
    onResetSupplementEditorMode: () -> Unit,
    onCreateSupplement: (String, String, SupplementDosagePlan, String, SupplementApiResult?) -> Unit,
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
                            FilledTonalButton(onClick = { onTakeNow(supplement) }) {
                                Text("Take now")
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onRequestAddSupplementChooser,
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
            createBootstrap = if (creatingSupplement && supplementEditor == null) createBootstrap else null,
            onDismiss = onDismissSupplementEditor,
            onDismissReset = onResetSupplementEditorMode,
            onSave = { id, name, brand, plan, notes, nih ->
                if (id == null) onCreateSupplement(name, brand, plan, notes, nih)
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
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit
) {
    var dateFilter by remember { mutableStateOf<SectionLogDateFilter>(SectionLogDateFilter.AllHistory) }
    var showCalendar by remember { mutableStateOf(false) }
    val datedEntries = remember(state, dateFilter) {
        state.datedSupplementEntriesForSectionLog(dateFilter)
    }
    val daySections = remember(datedEntries) {
        datedEntries.groupIntoDaySectionsNewestFirst()
    }
    var expandedLogDates by remember { mutableStateOf(setOf<LocalDate>()) }
    LaunchedEffect(dateFilter) {
        when (val f = dateFilter) {
            is SectionLogDateFilter.SingleDay -> expandedLogDates = setOf(f.day)
            else -> { }
        }
    }
    val datesWithActivity = remember(state) { datesWithSupplementActivity(state) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val headerMid = ErvHeaderRed
    val keyManager = LocalKeyManager.current
    val logAppContext = LocalContext.current.applicationContext

    suspend fun syncDailyLogForDate(date: LocalDate) {
        if (relayPool != null && signer != null) {
            repository.currentState().logFor(date)?.let { log ->
                SupplementSync.publishDailyLog(
                    logAppContext,
                    relayPool,
                    signer,
                    log,
                    keyManager.relayUrlsForKind30078Publish(),
                )
                repository.markLogPublished(date.toString())
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Supplement Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerMid,
                    titleContentColor = Color.White,
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
            SectionLogFilterBar(
                filter = dateFilter,
                onOpenCalendar = { showCalendar = true },
                onClearFilter = { dateFilter = SectionLogDateFilter.AllHistory }
            )

            if (datedEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when (dateFilter) {
                                SectionLogDateFilter.AllHistory -> "No supplements logged yet."
                                is SectionLogDateFilter.SingleDay -> "No supplements logged for this date."
                                is SectionLogDateFilter.DateRange -> "No supplements logged in this date range."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Use Take now on the Supplements tab or your routines.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Grouped by day, newest first. Open a day to see each intake. Delete removes one line.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    items(
                        items = daySections,
                        key = { it.logDate.toString() }
                    ) { section ->
                        SupplementLogDayCard(
                            section = section,
                            expanded = section.logDate in expandedLogDates,
                            onToggleExpand = {
                                expandedLogDates =
                                    if (section.logDate in expandedLogDates) {
                                        expandedLogDates - section.logDate
                                    } else {
                                        expandedLogDates + section.logDate
                                    }
                            },
                            onDeleteEntry = { entry ->
                                scope.launch {
                                    val logDate = section.logDate
                                    val intakeId = entry.intakeId
                                    if (intakeId != null) {
                                        repository.removeIntake(logDate, intakeId)
                                    } else {
                                        repository.removeIntakeByMatch(
                                            logDate,
                                            entry.supplementId,
                                            entry.takenAtEpochSeconds,
                                            entry.sourceLabel
                                        )
                                    }
                                    syncDailyLogForDate(logDate)
                                    snackbarHostState.showSnackbar("Entry removed")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCalendar) {
        SectionLogCalendarSheet(
            filter = dateFilter,
            onDismiss = { showCalendar = false },
            datesWithActivity = datesWithActivity,
            onApplyFilter = { dateFilter = it }
        )
    }
}

@Composable
private fun SupplementLogDayCard(
    section: SupplementDayLogSection,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDeleteEntry: (SupplementLogEntry) -> Unit
) {
    val distinctNames = remember(section.entries) {
        section.entries.map { it.supplementName }.distinct().size
    }
    val intakeCount = section.entries.size
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = section.logDate.format(SupplementLogFriendlyDate),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = when {
                            intakeCount == 0 -> "No entries"
                            distinctNames == 1 && intakeCount == 1 -> "1 supplement · 1 intake"
                            distinctNames == 1 -> "1 supplement · $intakeCount intakes"
                            distinctNames == intakeCount -> "$distinctNames supplements"
                            else -> "$distinctNames supplements · $intakeCount intakes"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide intakes" else "Show intakes",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded && section.entries.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                section.entries.forEachIndexed { index, entry ->
                    key(entry.intakeId ?: "legacy-$index", entry.takenAtEpochSeconds, entry.supplementId) {
                        SupplementLogIntakeRow(
                            entry = entry,
                            onDelete = { onDeleteEntry(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SupplementLogIntakeRow(
    entry: SupplementLogEntry,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = entry.supplementName,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = entry.dosageTaken,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatLogTime(entry.takenAtEpochSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (entry.isFromRoutine()) {
                    val rn = entry.routineNameFromSource()
                    if (rn != null) "Part of routine: $rn" else "Part of a routine"
                } else {
                    "Ad hoc — not from a routine"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (entry.isFromRoutine()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove this intake"
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
    createBootstrap: CreateSupplementBootstrap?,
    onDismiss: () -> Unit,
    onDismissReset: () -> Unit,
    onSave: (String?, String, String, SupplementDosagePlan, String, SupplementApiResult?) -> Unit
) {
    var draft by remember(supplement?.id, creating, createBootstrap?.sessionKey) {
        mutableStateOf(
            when {
                supplement != null -> supplement.toSupplementDraft()
                createBootstrap != null -> createBootstrap.initialDraft
                else -> SupplementDraft()
            }
        )
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
                    val nih = if (creating) createBootstrap?.pendingNih else null
                    onSave(supplement?.id, draft.name.trim(), draft.brand.trim(), plan, draft.notes.trim(), nih)
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
    initialTimeOfDay: SupplementTimeOfDay,
    supplements: List<SupplementEntry>,
    existingReminder: RoutineReminder?,
    onDismiss: () -> Unit,
    onDismissReset: () -> Unit,
    onSave: (String?, String, SupplementTimeOfDay, List<SupplementRoutineStep>, String, RoutineReminderDraft) -> Unit
) {
    val routineTimeOfDay = routine?.timeOfDay ?: initialTimeOfDay
    var name by remember(routine?.id, creating, initialTimeOfDay) {
        mutableStateOf(routine?.name.orEmpty().ifBlank { "${initialTimeOfDay.label()} routine" })
    }
    var notes by remember(routine?.id, creating) { mutableStateOf(routine?.notes.orEmpty()) }
    var reminderDraft by remember(routine?.id, creating, existingReminder) {
        mutableStateOf(existingReminder?.toDraft() ?: RoutineReminderDraft())
    }
    val steps = remember(routine?.id, creating) {
        mutableStateListOf<RoutineStepDraft>().apply {
            if (routine?.steps.isNullOrEmpty()) {
                add(RoutineStepDraft(timeOfDay = routineTimeOfDay))
            } else {
                routine!!.steps.forEach {
                    add(
                        RoutineStepDraft(
                            supplementId = it.supplementId,
                            timeOfDay = it.timeOfDay ?: routineTimeOfDay,
                            quantity = it.quantity?.toString() ?: "1",
                            dosageOverride = it.dosageOverride.orEmpty(),
                            note = it.note.orEmpty(),
                            isExpanded = false,
                            paused = it.paused
                        )
                    )
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = { onDismissReset(); onDismiss() },
        title = { Text(if (creating) "Add ${initialTimeOfDay.label().lowercase()} routine" else "Edit routine") },
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

                Text("Supplements for ${routineTimeOfDay.label()}", style = MaterialTheme.typography.titleSmall)
                if (steps.isEmpty()) {
                    Text(
                        "Tap \"Add supplement\" below. Then tap \"Select supplement\" and pick one — serving size will appear.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                steps.forEachIndexed { index, draft ->
                    key(draft.uiKey) {
                        RoutineStepRow(
                            step = draft,
                            supplements = supplements,
                            onStepChange = { steps[index] = it },
                            onRemove = { steps.removeAt(index) }
                        )
                    }
                }
                TextButton(
                    onClick = { steps.add(RoutineStepDraft(timeOfDay = routineTimeOfDay)) }
                ) {
                    Text("Add supplement")
                }

                RoutineReminderFormSection(
                    reminderDraft = reminderDraft,
                    onReminderDraftChange = { reminderDraft = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        routine?.id,
                        name.trim(),
                        routineTimeOfDay,
                        steps.mapNotNull { draft ->
                            val supplementId = draft.supplementId ?: return@mapNotNull null
                            val supplement = supplements.firstOrNull { it.id == supplementId }
                            SupplementRoutineStep(
                                supplementId = supplementId,
                                timeOfDay = routineTimeOfDay,
                                quantity = draft.quantity.toIntOrNull() ?: 1,
                                dosageOverride = draft.dosageOverride.trim().ifBlank {
                                    supplement?.recommendedServingDisplay().orEmpty()
                                }.ifBlank { null },
                                note = draft.note.trim().ifBlank { null },
                                paused = draft.paused
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
    var showSupplementPicker by remember { mutableStateOf(false) }
    var stepMenuExpanded by remember { mutableStateOf(false) }
    val selectedSupplement = supplements.firstOrNull { it.id == step.supplementId }
    val recommendedServing = selectedSupplement?.recommendedServingDisplay().orEmpty()
    val servingSizeLabel = selectedSupplement?.info?.servingSize?.takeIf { it.isNotBlank() }
        ?: selectedSupplement?.dosagePlan?.summary()?.takeIf { it.isNotBlank() }
        ?: recommendedServing

    LaunchedEffect(step.supplementId, selectedSupplement?.id) {
        if (selectedSupplement != null && step.dosageOverride.isBlank()) {
            onStepChange(step.copy(dosageOverride = recommendedServing))
        }
    }

    val summary = buildString {
        if (selectedSupplement == null) {
            append("Select a supplement to configure this step.")
        } else {
            append(step.dosageOverride.ifBlank { recommendedServing }.ifBlank { "Take as directed" })
            append(" • Qty ")
            append(step.quantity.ifBlank { "1" })
            if (step.note.isNotBlank()) {
                append(" • ")
                append(step.note)
            }
            if (step.paused) {
                append(" • Paused")
            }
        }
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (step.paused) 0.55f else 1f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = selectedSupplement?.name ?: "New supplement",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onStepChange(step.copy(isExpanded = !step.isExpanded)) },
                    enabled = selectedSupplement != null
                ) {
                    Icon(
                        imageVector = if (step.isExpanded) Icons.Default.Remove else Icons.Default.Edit,
                        contentDescription = if (step.isExpanded) "Collapse step" else "Edit step"
                    )
                }
                IconButton(
                    onClick = { onStepChange(step.copy(paused = !step.paused)) },
                    enabled = selectedSupplement != null
                ) {
                    Icon(
                        imageVector = if (step.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (step.paused) "Resume step" else "Pause step"
                    )
                }
                Box {
                    IconButton(onClick = { stepMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Step options")
                    }
                    DropdownMenu(
                        expanded = stepMenuExpanded,
                        onDismissRequest = { stepMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Remove from routine") },
                            onClick = {
                                stepMenuExpanded = false
                                onRemove()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        )
                    }
                }
            }

            if (showSupplementPicker) {
                AlertDialog(
                    onDismissRequest = { showSupplementPicker = false },
                    title = { Text("Choose supplement") },
                    text = {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.heightIn(max = 400.dp)
                        ) {
                            items(supplements, key = { it.id }) { supplement ->
                                TextButton(
                                    onClick = {
                                        onStepChange(
                                            step.copy(
                                                supplementId = supplement.id,
                                                dosageOverride = supplement.recommendedServingDisplay(),
                                                isExpanded = true
                                            )
                                        )
                                        showSupplementPicker = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(supplement.name, style = MaterialTheme.typography.bodyLarge)
                                        if (supplement.brand.isNotBlank()) {
                                            Text(
                                                supplement.brand,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { showSupplementPicker = false }) { Text("Cancel") } }
                )
            }

            if (step.isExpanded) {
                OutlinedButton(
                    onClick = { showSupplementPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (selectedSupplement != null) selectedSupplement.name
                        else "Select supplement"
                    )
                }

                if (selectedSupplement != null) {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Serving size for this supplement",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = servingSizeLabel.ifBlank { "Take as directed" },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    OutlinedTextField(
                        value = step.dosageOverride.ifBlank { recommendedServing },
                        onValueChange = { onStepChange(step.copy(dosageOverride = it)) },
                        label = { Text("Daily serving (edit if needed)") },
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
            }
        }
    }
}

private fun SupplementTimeOfDay.label(): String = when (this) {
    SupplementTimeOfDay.MORNING -> "Morning"
    SupplementTimeOfDay.MIDDAY -> "Midday"
    SupplementTimeOfDay.NIGHT -> "Night"
    SupplementTimeOfDay.OTHER -> "Other"
}

private fun SupplementEntry.recommendedServingDisplay(): String =
    info?.servingSize?.takeIf { it.isNotBlank() }
        ?: dosagePlan.summary().takeIf { it.isNotBlank() }
        ?: "Take as directed"

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
    /** When true: no auto-search on open; lookup field hidden until user unchecks. */
    var turnOffSearch by rememberSaveable(supplementId) { mutableStateOf(false) }
    /** One-time: if supplement already has NIH info when first seen, default turnOffSearch on without clobbering user later. */
    var seededTurnOffFromInfo by rememberSaveable(supplementId) { mutableStateOf(false) }
    var showEditor by remember(supplementId) { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    val keyManager = LocalKeyManager.current
    val detailAppContext = LocalContext.current.applicationContext

    suspend fun syncMaster() {
        if (relayPool != null && signer != null) {
            SupplementSync.publishMaster(
                detailAppContext,
                relayPool,
                signer,
                repository.currentState(),
                keyManager.relayUrlsForKind30078Publish(),
            )
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

    LaunchedEffect(supplement?.id, supplement?.info, defaultQuery, turnOffSearch, autoSearchDone, seededTurnOffFromInfo) {
        val s = supplement ?: return@LaunchedEffect
        var skipAutoLookup = turnOffSearch
        if (s.info != null && !seededTurnOffFromInfo) {
            turnOffSearch = true
            seededTurnOffFromInfo = true
            skipAutoLookup = true
        }
        if (skipAutoLookup) return@LaunchedEffect
        if (!autoSearchDone && defaultQuery.isNotBlank()) {
            autoSearchDone = true
            searchNih(defaultQuery)
        }
    }

    LaunchedEffect(apiResults, supplement?.id, turnOffSearch, autoAttachDone) {
        val s = supplement ?: return@LaunchedEffect
        if (turnOffSearch || autoAttachDone) return@LaunchedEffect
        if (apiResults.size == 1) {
            autoAttachDone = true
            val match = apiResults.first()
            repository.attachInfo(s.id, match.info, match.productId)
            syncMaster()
            turnOffSearch = true
            seededTurnOffFromInfo = true
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ErvHeaderRed,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
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

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("Delete supplement?") },
            text = {
                Text(
                    "This removes \"${supplement.name}\" from your library. Steps that reference it are removed from routines.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = false
                        val id = supplement.id
                        scope.launch {
                            repository.deleteSupplement(id)
                            syncMaster()
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(supplement.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditor = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit supplement")
                    }
                    IconButton(onClick = { pendingDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete supplement")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ErvHeaderRed,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = turnOffSearch,
                                onCheckedChange = { checked ->
                                    turnOffSearch = checked
                                    if (checked) {
                                        apiResults = emptyList()
                                    } else {
                                        autoAttachDone = false
                                    }
                                }
                            )
                            Text(
                                "Turn off search",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (turnOffSearch) {
                            Text(
                                "Automatic lookup is disabled. Uncheck \"Turn off search\" to run a new NIH search.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
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
            }

            if (!turnOffSearch && apiResults.isNotEmpty()) {
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
                                turnOffSearch = true
                                seededTurnOffFromInfo = true
                                apiResults = emptyList()
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
                                routine.steps.joinToString(" · ") { step ->
                                    step.activityStyleSummary(state.supplementById(step.supplementId))
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

    if (showEditor) {
        SupplementEditorDialog(
            supplement = supplement,
            creating = false,
            createBootstrap = null,
            onDismiss = { showEditor = false },
            onDismissReset = { },
            onSave = { id, name, brand, plan, notes, _ ->
                if (id != null) {
                    scope.launch {
                        repository.renameSupplement(id, name, brand, plan, notes)
                        syncMaster()
                        snackbarHostState.showSnackbar("Supplement updated")
                    }
                }
                showEditor = false
            }
        )
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

private fun supplementDraftFromNihSearchResult(result: SupplementApiResult): SupplementDraft {
    val label = result.info.servingSize?.trim().orEmpty()
    val parsed = parseNihServingSizeToDosage(result.info.servingSize)
    return SupplementDraft(
        name = result.productName,
        brand = result.brand?.trim().orEmpty(),
        servingSize = label,
        form = parsed?.form ?: SupplementForm.POWDER,
        servingAmount = parsed?.amount?.toDisplayNumber().orEmpty(),
        servingUnit = parsed?.unit ?: SupplementUnit.MG,
    )
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
    return brand.isNotBlank() && servingAmount.toDoubleOrNull() != null
}

private fun Double.toDisplayNumber(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

