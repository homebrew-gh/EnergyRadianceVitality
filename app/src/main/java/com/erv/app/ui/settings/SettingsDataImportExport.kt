@file:OptIn(ExperimentalMaterial3Api::class)

package com.erv.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioHistoryImport
import com.erv.app.cardio.CardioImportOutcome
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioRepository
import com.erv.app.bodytracker.BodyTrackerRepository
import com.erv.app.dataexport.DataExportCategory
import com.erv.app.dataexport.DataExportFormat
import com.erv.app.dataexport.ErvAppDataExport
import com.erv.app.dataexport.ExportDateSelection
import com.erv.app.dataexport.LocalProfileExportV1
import com.erv.app.cardio.CardioSync
import com.erv.app.cardio.summaryLine
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightHistoryImport
import com.erv.app.weighttraining.WeightImportDatedSession
import com.erv.app.weighttraining.WeightImportOutcome
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.heatcold.HeatColdRepository
import com.erv.app.lighttherapy.LightTherapyRepository
import com.erv.app.stretching.StretchingRepository
import com.erv.app.programs.FitnessProgram
import com.erv.app.programs.ProgramImport
import com.erv.app.programs.ProgramImportEnvelope
import com.erv.app.programs.ProgramRepository
import com.erv.app.programs.ProgramSync
import com.erv.app.programs.ProgramsLibraryState
import com.erv.app.reminders.RoutineReminderRepository
import com.erv.app.supplements.SupplementRepository
import com.erv.app.unifiedroutines.UnifiedRoutineRepository
import com.erv.app.weighttraining.WeightSync
import com.erv.app.weighttraining.WeightWorkoutEntry
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.formatHiitBlockSummaryLine
import com.erv.app.weighttraining.formatSetSummaryLine
import com.erv.app.weighttraining.weightLoadUnitSuffix
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREVIEW_SESSION_SAMPLE_MAX = 5

private enum class ImportSilo {
    WEIGHT,
    CARDIO,
    PROGRAMS,
    BACKUP_RESTORE,
}

private data class PendingWeightImport(
    val outcome: WeightImportOutcome.Success,
    /** Library snapshot before merge — used to list only newly appended sessions. */
    val libraryBeforePreview: WeightLibraryState,
)

private data class PendingCardioImport(
    val outcome: CardioImportOutcome.Success,
    val libraryBeforePreview: CardioLibraryState,
)

private data class PendingProgramImport(
    val envelope: ProgramImportEnvelope,
    val libraryBeforePreview: ProgramsLibraryState,
)

private data class PendingBackupRestore(
    val preview: BackupRestorePreview,
)

private data class ImportPreviewBlock(
    val title: String,
    val lines: List<String>,
)

private fun sessionPreviewBlocks(
    library: WeightLibraryState,
    session: WeightWorkoutSession,
    loadUnit: BodyWeightUnit,
): List<ImportPreviewBlock> {
    val loadSuffix = weightLoadUnitSuffix(loadUnit)
    return session.entries.map { entry ->
        importPreviewBlockForEntry(library, entry, loadUnit, loadSuffix)
    }
}

private fun importPreviewBlockForEntry(
    library: WeightLibraryState,
    entry: WeightWorkoutEntry,
    loadUnit: BodyWeightUnit,
    loadSuffix: String,
): ImportPreviewBlock {
    val ex = library.exerciseById(entry.exerciseId)
    val title = ex?.name ?: "Exercise (${entry.exerciseId})"
    val addedLoad = ex?.equipment == WeightEquipment.OTHER
    val hiit = entry.hiitBlock
    val lines = if (hiit != null) {
        listOf(formatHiitBlockSummaryLine(hiit, loadUnit, loadSuffix, addedLoad))
    } else {
        entry.sets.mapIndexed { i, s ->
            formatSetSummaryLine(s, i + 1, loadUnit, loadSuffix, addedLoad)
        }
    }
    return ImportPreviewBlock(title, lines)
}

@Composable
fun SettingsDataImportExportScreen(
    onBack: () -> Unit,
    onOpenDoc: (docKey: String) -> Unit,
    userPreferences: UserPreferences,
    weightRepository: WeightRepository,
    cardioRepository: CardioRepository,
    stretchingRepository: StretchingRepository,
    heatColdRepository: HeatColdRepository,
    lightTherapyRepository: LightTherapyRepository,
    supplementRepository: SupplementRepository,
    programRepository: ProgramRepository,
    unifiedRoutineRepository: UnifiedRoutineRepository,
    bodyTrackerRepository: BodyTrackerRepository,
    reminderRepository: RoutineReminderRepository,
    relayPool: RelayPool?,
    signer: EventSigner?,
    initialExportCategory: DataExportCategory = DataExportCategory.ALL,
) {
    val context = LocalContext.current
    val keyManager = LocalKeyManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val loadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.LB)
    val cardioDistanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    val interchangeCategories = remember {
        DataExportCategory.entries.filter { it != DataExportCategory.ALL }
    }

    var exportCategory by remember(initialExportCategory) {
        mutableStateOf(
            initialExportCategory.takeIf { it != DataExportCategory.ALL } ?: DataExportCategory.WEIGHT_TRAINING
        )
    }
    var exportFormat by remember { mutableStateOf(DataExportFormat.JSON) }
    var exportDateAllTime by remember { mutableStateOf(true) }
    var exportRangeStart by remember { mutableStateOf(LocalDate.now().minusMonths(1)) }
    var exportRangeEnd by remember { mutableStateOf(LocalDate.now()) }
    var showExportStartPicker by remember { mutableStateOf(false) }
    var showExportEndPicker by remember { mutableStateOf(false) }
    var exportCategoryMenuExpanded by remember { mutableStateOf(false) }
    var backupExportCategory by remember(initialExportCategory) { mutableStateOf(initialExportCategory) }
    var backupCategoryMenuExpanded by remember { mutableStateOf(false) }
    var pendingExportFile by remember { mutableStateOf<PreparedExportFile?>(null) }
    var pendingBackupRestore by remember { mutableStateOf<PendingBackupRestore?>(null) }

    val zoneId = remember { ZoneId.systemDefault() }
    val exportCoordinator = remember(
        context.applicationContext,
        userPreferences,
        keyManager,
        weightRepository,
        cardioRepository,
        stretchingRepository,
        heatColdRepository,
        lightTherapyRepository,
        supplementRepository,
        programRepository,
        unifiedRoutineRepository,
        bodyTrackerRepository,
        reminderRepository,
        relayPool,
        signer,
    ) {
        ImportExportCoordinator(
            appContext = context.applicationContext,
            userPreferences = userPreferences,
            keyManager = keyManager,
            weightRepository = weightRepository,
            cardioRepository = cardioRepository,
            stretchingRepository = stretchingRepository,
            heatColdRepository = heatColdRepository,
            lightTherapyRepository = lightTherapyRepository,
            supplementRepository = supplementRepository,
            programRepository = programRepository,
            unifiedRoutineRepository = unifiedRoutineRepository,
            bodyTrackerRepository = bodyTrackerRepository,
            reminderRepository = reminderRepository,
            relayPool = relayPool,
            signer = signer,
        )
    }
    val backupRestoreCoordinator = remember(
        context.applicationContext,
        userPreferences,
        keyManager,
        weightRepository,
        cardioRepository,
        stretchingRepository,
        heatColdRepository,
        lightTherapyRepository,
        supplementRepository,
        programRepository,
        unifiedRoutineRepository,
        bodyTrackerRepository,
        reminderRepository,
        relayPool,
        signer,
    ) {
        BackupRestoreCoordinator(
            appContext = context.applicationContext,
            userPreferences = userPreferences,
            keyManager = keyManager,
            weightRepository = weightRepository,
            cardioRepository = cardioRepository,
            stretchingRepository = stretchingRepository,
            heatColdRepository = heatColdRepository,
            lightTherapyRepository = lightTherapyRepository,
            supplementRepository = supplementRepository,
            programRepository = programRepository,
            unifiedRoutineRepository = unifiedRoutineRepository,
            bodyTrackerRepository = bodyTrackerRepository,
            reminderRepository = reminderRepository,
            relayPool = relayPool,
            signer = signer,
        )
    }

    fun finishCreateExport(uri: Uri?) {
        val pending = pendingExportFile
        pendingExportFile = null
        if (uri == null || pending == null) return
        scope.launch {
            try {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(pending.bytes) }
                }
                snackbarHostState.showSnackbar("Export saved")
            } catch (_: Exception) {
                snackbarHostState.showSnackbar("Could not write export file")
            }
        }
    }

    val createJsonExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> finishCreateExport(uri) }
    val createCsvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? -> finishCreateExport(uri) }

    fun queueExport(prepared: PreparedExportFile, format: DataExportFormat) {
        pendingExportFile = prepared
        if (format == DataExportFormat.JSON) {
            createJsonExportLauncher.launch(prepared.fileName)
        } else {
            createCsvExportLauncher.launch(prepared.fileName)
        }
    }

    var activeImportSilo by remember { mutableStateOf<ImportSilo?>(null) }
    var pendingWeightImport by remember { mutableStateOf<PendingWeightImport?>(null) }
    var pendingCardioImport by remember { mutableStateOf<PendingCardioImport?>(null) }
    var pendingProgramImport by remember { mutableStateOf<PendingProgramImport?>(null) }
    var parseErrorMessages by remember { mutableStateOf<List<String>?>(null) }
    var weightReferenceExpanded by remember { mutableStateOf(false) }
    var cardioReferenceExpanded by remember { mutableStateOf(false) }
    var programReferenceExpanded by remember { mutableStateOf(false) }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val silo = activeImportSilo
        activeImportSilo = null
        if (silo == null) return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = exportCoordinator.readText(uri)
            if (text.isNullOrBlank()) {
                snackbarHostState.showSnackbar("Could not read file")
                return@launch
            }
            when (silo) {
                ImportSilo.WEIGHT -> {
                    val librarySnapshot = weightRepository.currentState()
                    when (val outcome = weightRepository.previewImportedWeightText(text)) {
                        is WeightImportOutcome.Failure -> {
                            parseErrorMessages = outcome.messages
                        }
                        is WeightImportOutcome.Success -> {
                            pendingWeightImport = PendingWeightImport(
                                outcome = outcome,
                                libraryBeforePreview = librarySnapshot,
                            )
                        }
                    }
                }
                ImportSilo.CARDIO -> {
                    val librarySnapshot = cardioRepository.currentState()
                    when (val outcome = cardioRepository.previewImportedCardioText(text)) {
                        is CardioImportOutcome.Failure -> {
                            parseErrorMessages = outcome.messages
                        }
                        is CardioImportOutcome.Success -> {
                            pendingCardioImport = PendingCardioImport(
                                outcome = outcome,
                                libraryBeforePreview = librarySnapshot,
                            )
                        }
                    }
                }
                ImportSilo.PROGRAMS -> {
                    val librarySnapshot = programRepository.currentState()
                    val (envelope, errs) = ProgramImport.parse(text)
                    if (envelope == null) {
                        parseErrorMessages = errs
                    } else {
                        pendingProgramImport = PendingProgramImport(
                            envelope = envelope,
                            libraryBeforePreview = librarySnapshot,
                        )
                    }
                }
                ImportSilo.BACKUP_RESTORE -> {
                    backupRestoreCoordinator.previewBackupText(text).fold(
                        onSuccess = { pendingBackupRestore = PendingBackupRestore(it) },
                        onFailure = {
                            parseErrorMessages = listOf(
                                it.message ?: "This file is not a valid ERV backup."
                            )
                        },
                    )
                }
            }
        }
    }

    pendingWeightImport?.let { pending ->
        WeightImportPreviewDialog(
            pending = pending,
            loadUnit = loadUnit,
            onDismiss = { pendingWeightImport = null },
            onConfirm = {
                val toApply = pending
                pendingWeightImport = null
                scope.launch {
                    snackbarHostState.showSnackbar(exportCoordinator.commitWeightImport(toApply.outcome))
                }
            },
        )
    }

    pendingCardioImport?.let { pending ->
        CardioImportPreviewDialog(
            pending = pending,
            distanceUnit = cardioDistanceUnit,
            onDismiss = { pendingCardioImport = null },
            onConfirm = {
                val toApply = pending
                pendingCardioImport = null
                scope.launch {
                    snackbarHostState.showSnackbar(exportCoordinator.commitCardioImport(toApply.outcome))
                }
            },
        )
    }

    pendingProgramImport?.let { pending ->
        ProgramImportPreviewDialog(
            pending = pending,
            onDismiss = { pendingProgramImport = null },
            onConfirm = {
                val toApply = pending
                pendingProgramImport = null
                scope.launch {
                    snackbarHostState.showSnackbar(exportCoordinator.commitProgramsImport(toApply.envelope))
                }
            },
        )
    }

    pendingBackupRestore?.let { pending ->
        BackupRestorePreviewDialog(
            pending = pending,
            onDismiss = { pendingBackupRestore = null },
            onConfirm = {
                val toApply = pending
                pendingBackupRestore = null
                scope.launch {
                    snackbarHostState.showSnackbar(backupRestoreCoordinator.restore(toApply.preview))
                }
            },
        )
    }

    parseErrorMessages?.let { messages ->
        ImportFailureDialog(
            messages = messages,
            onDismiss = { parseErrorMessages = null },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Data Interchange + Backup") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            ImportSectionTitle("Data Interchange", first = true)
            Text(
                "Use these files and guides when you want an AI assistant, coach, or your own scripts to inspect ERV data or create merge-oriented imports.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Text(
                "Interchange exports are for analysis and structured authoring. They are not the same guarantee as a device backup restore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                "Export For AI Or Coach Workflows",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            ExposedDropdownMenuBox(
                expanded = exportCategoryMenuExpanded,
                onExpandedChange = { exportCategoryMenuExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                OutlinedTextField(
                    value = exportCategory.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = exportCategoryMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = exportCategoryMenuExpanded,
                    onDismissRequest = { exportCategoryMenuExpanded = false }
                ) {
                    interchangeCategories.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                exportCategory = option
                                exportCategoryMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Text("Format", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                FilterChip(
                    selected = exportFormat == DataExportFormat.JSON,
                    onClick = { exportFormat = DataExportFormat.JSON },
                    label = { Text("JSON") }
                )
                FilterChip(
                    selected = exportFormat == DataExportFormat.CSV,
                    onClick = { exportFormat = DataExportFormat.CSV },
                    label = { Text("CSV") }
                )
            }
            if (!ErvAppDataExport.csvSupported(exportCategory, exportFormat)) {
                Text(
                    "CSV is only available for Weight training and Cardio. Use JSON for every other interchange export.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text("Dates", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                FilterChip(
                    selected = exportDateAllTime,
                    onClick = { exportDateAllTime = true },
                    label = { Text("All time") }
                )
                FilterChip(
                    selected = !exportDateAllTime,
                    onClick = { exportDateAllTime = false },
                    label = { Text("Date range") }
                )
            }
            if (!exportDateAllTime) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showExportStartPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Start ${exportRangeStart}", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = { showExportEndPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("End ${exportRangeEnd}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Button(
                onClick = {
                    if (exportFormat == DataExportFormat.CSV &&
                        !ErvAppDataExport.csvSupported(exportCategory, exportFormat)
                    ) {
                        scope.launch { snackbarHostState.showSnackbar("Choose JSON for this category") }
                        return@Button
                    }
                    val selection: ExportDateSelection = if (exportDateAllTime) {
                        ExportDateSelection.AllTime
                    } else {
                        if (exportRangeStart.isAfter(exportRangeEnd)) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Start date must be on or before end date")
                            }
                            return@Button
                        }
                        ExportDateSelection.Range(exportRangeStart, exportRangeEnd)
                    }
                    scope.launch {
                        try {
                            queueExport(
                                exportCoordinator.buildExport(exportCategory, exportFormat, selection),
                                exportFormat,
                            )
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar("Could not build export")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text("Export Interchange File", modifier = Modifier.padding(start = 8.dp))
            }
            HorizontalDivider()
            Text(
                "Import structured weight, cardio, or program files here. These imports preview before merge and are intended for coach- or AI-authored data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
            )

            ImportSectionTitle("Weight Training")
            Text(
                "Guides And Exercise IDs Help You Or An AI Build Files That Upload After Import Via The Outbox.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            OutlinedButton(
                onClick = {
                    activeImportSilo = ImportSilo.WEIGHT
                    pickLauncher.launch(arrayOf("application/json", "text/csv", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Text("Import Weight Training File", modifier = Modifier.padding(start = 8.dp))
            }
            ImportReferenceCollapsibleSection(
                sectionTitle = "Reference Files (Weight Training Upload)",
                summaryWhenCollapsed = "Tap To Open Guides, Exercise IDs, And A Combined File For Your AI.",
                expanded = weightReferenceExpanded,
                onExpandedChange = { weightReferenceExpanded = it },
                modifier = Modifier.padding(top = 14.dp),
                onShareBundle = {
                    scope.launch {
                        try {
                            if (!exportCoordinator.shareReferenceBundle(
                                    keys = ImportExportDocAssets.weightReferenceKeys,
                                    bundleTitle = "Weight Training — Combined Import Reference",
                                    fileName = "weight_training_import_reference_for_ai.md",
                                )
                            ) {
                                snackbarHostState.showSnackbar("Could Not Open Save Or Share")
                            }
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar("Could Not Prepare Reference Bundle")
                        }
                    }
                },
            ) {
                DocLinkRow(
                    title = ImportExportDocAssets.titleForKey(ImportExportDocAssets.KEY_WEIGHT_AI),
                    subtitle = "AI Prompt Rules For Weight Training JSON",
                    onClick = { onOpenDoc(ImportExportDocAssets.KEY_WEIGHT_AI) }
                )
                DocLinkRow(
                    title = ImportExportDocAssets.titleForKey(ImportExportDocAssets.KEY_WEIGHT_CSV),
                    subtitle = "Spreadsheet Columns For Weight Training CSV",
                    onClick = { onOpenDoc(ImportExportDocAssets.KEY_WEIGHT_CSV) }
                )
                DocLinkRow(
                    title = ImportExportDocAssets.titleForKey(ImportExportDocAssets.KEY_WEIGHT_BUILTIN),
                    subtitle = "Official Names And IDs For Lift Mapping",
                    onClick = { onOpenDoc(ImportExportDocAssets.KEY_WEIGHT_BUILTIN) }
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            ImportSectionTitle("Cardio Training")
            Text(
                "Guides And Nostr Reference Cover Cardio JSON, CSV, And Relay D-Tags (Kind 30078).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            OutlinedButton(
                onClick = {
                    activeImportSilo = ImportSilo.CARDIO
                    pickLauncher.launch(arrayOf("application/json", "text/csv", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Text("Import Cardio Training File", modifier = Modifier.padding(start = 8.dp))
            }
            ImportReferenceCollapsibleSection(
                sectionTitle = "Reference Files (Cardio Training Upload)",
                summaryWhenCollapsed = "Tap To Open Guides, Nostr Reference, And A Combined File For Your AI.",
                expanded = cardioReferenceExpanded,
                onExpandedChange = { cardioReferenceExpanded = it },
                modifier = Modifier.padding(top = 14.dp),
                onShareBundle = {
                    scope.launch {
                        try {
                            if (!exportCoordinator.shareReferenceBundle(
                                    keys = ImportExportDocAssets.cardioReferenceKeys,
                                    bundleTitle = "Cardio Training — Combined Import Reference",
                                    fileName = "cardio_training_import_reference_for_ai.md",
                                )
                            ) {
                                snackbarHostState.showSnackbar("Could Not Open Save Or Share")
                            }
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar("Could Not Prepare Reference Bundle")
                        }
                    }
                },
            ) {
                DocLinkRow(
                    title = ImportExportDocAssets.titleForKey(ImportExportDocAssets.KEY_CARDIO_AI),
                    subtitle = "AI Prompt Rules For Cardio JSON",
                    onClick = { onOpenDoc(ImportExportDocAssets.KEY_CARDIO_AI) }
                )
                DocLinkRow(
                    title = ImportExportDocAssets.titleForKey(ImportExportDocAssets.KEY_CARDIO_CSV),
                    subtitle = "Spreadsheet Columns For Cardio CSV",
                    onClick = { onOpenDoc(ImportExportDocAssets.KEY_CARDIO_CSV) }
                )
                DocLinkRow(
                    title = ImportExportDocAssets.titleForKey(ImportExportDocAssets.KEY_CARDIO_NOSTR),
                    subtitle = "Optional: Relay Kind 30078 And Cardio D-Tags When Sync Is On",
                    onClick = { onOpenDoc(ImportExportDocAssets.KEY_CARDIO_NOSTR) }
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            ImportSectionTitle("Programs")
            Text(
                "Weekly plans merge by program id. The AI bundle now includes the programs guide, built-in weight exercise ids, and a generated snapshot of this device's equipment and reusable ids.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            OutlinedButton(
                onClick = {
                    activeImportSilo = ImportSilo.PROGRAMS
                    pickLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Text("Import Programs File", modifier = Modifier.padding(start = 8.dp))
            }
            ImportReferenceCollapsibleSection(
                sectionTitle = "Reference Files (Programs)",
                summaryWhenCollapsed = "Tap To Open The Guide Or Save A Bundle With Current Equipment, Routine Ids, And Program Ids For Your AI.",
                expanded = programReferenceExpanded,
                onExpandedChange = { programReferenceExpanded = it },
                modifier = Modifier.padding(top = 14.dp),
                onShareBundle = {
                    scope.launch {
                        try {
                            if (!exportCoordinator.shareProgramsReferenceBundle()) {
                                snackbarHostState.showSnackbar("Could Not Open Save Or Share")
                            }
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar("Could Not Prepare Reference Bundle")
                        }
                    }
                },
            ) {
                DocLinkRow(
                    title = ImportExportDocAssets.titleForKey(ImportExportDocAssets.KEY_PROGRAM_AI),
                    subtitle = "JSON Shape, Block Kinds, ISO Weekdays, And Examples",
                    onClick = { onOpenDoc(ImportExportDocAssets.KEY_PROGRAM_AI) }
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            ImportSectionTitle("Backup And Restore")
            Text(
                "Use ERV backup JSON when you want to archive a section, move to a new device, or restore ERV-shaped snapshots. Restore replaces only the sections present in the file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            ExposedDropdownMenuBox(
                expanded = backupCategoryMenuExpanded,
                onExpandedChange = { backupCategoryMenuExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                OutlinedTextField(
                    value = backupExportCategory.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Backup scope") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = backupCategoryMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = backupCategoryMenuExpanded,
                    onDismissRequest = { backupCategoryMenuExpanded = false }
                ) {
                    DataExportCategory.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                backupExportCategory = option
                                backupCategoryMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Text(
                "Backup export always writes ERV JSON so it can be restored later. CSV stays an interchange-only format.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Button(
                onClick = {
                    scope.launch {
                        try {
                            queueExport(
                                exportCoordinator.buildExport(
                                    category = backupExportCategory,
                                    format = DataExportFormat.JSON,
                                    selection = ExportDateSelection.AllTime,
                                ),
                                DataExportFormat.JSON,
                            )
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar("Could not build backup export")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text("Export ERV Backup", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = {
                    activeImportSilo = ImportSilo.BACKUP_RESTORE
                    pickLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Text("Restore From ERV Backup JSON", modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                "Restore is replace-oriented for the sections in the file. Omitted sections stay untouched, and relay re-sync only happens if you are signed in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 16.dp)
            )
        }
    }

    if (showExportStartPicker) {
        key(exportRangeStart) {
            val startPickerState = rememberDatePickerState(
                initialSelectedDateMillis = exportRangeStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showExportStartPicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            startPickerState.selectedDateMillis?.let {
                                exportRangeStart = ErvAppDataExport.millisToLocalDateUtc(it)
                            }
                            showExportStartPicker = false
                        }
                    ) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showExportStartPicker = false }) { Text("Cancel") }
                },
            ) {
                DatePicker(state = startPickerState)
            }
        }
    }
    if (showExportEndPicker) {
        key(exportRangeEnd) {
            val endPickerState = rememberDatePickerState(
                initialSelectedDateMillis = exportRangeEnd.atStartOfDay(zoneId).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showExportEndPicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            endPickerState.selectedDateMillis?.let {
                                exportRangeEnd = ErvAppDataExport.millisToLocalDateUtc(it)
                            }
                            showExportEndPicker = false
                        }
                    ) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showExportEndPicker = false }) { Text("Cancel") }
                },
            ) {
                DatePicker(state = endPickerState)
            }
        }
    }
}

@Composable
private fun WeightImportPreviewDialog(
    pending: PendingWeightImport,
    loadUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val success = pending.outcome
    val library = success.newState
    val datedSessions = WeightHistoryImport.sessionsAddedByImport(pending.libraryBeforePreview, success)
    val sample = datedSessions.take(PREVIEW_SESSION_SAMPLE_MAX)
    val hiddenCount = datedSessions.size - sample.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weight Training Import Preview") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "${success.sessionsImported} session(s) on ${success.affectedDates.size} day(s). " +
                        if (success.exercisesUpserted > 0) {
                            "${success.exercisesUpserted} exercise row(s) from file merged into your library."
                        } else {
                            "No new exercise definitions in file."
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "Preview Sample (Imported)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                sample.forEachIndexed { idx, dated ->
                    Text(
                        dated.dateIso,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = if (idx == 0) 0.dp else 10.dp, bottom = 4.dp)
                    )
                    val blocks = sessionPreviewBlocks(library, dated.session, loadUnit)
                    blocks.forEach { block ->
                        Text(
                            block.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        block.lines.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    if (idx < sample.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
                if (hiddenCount > 0) {
                    Text(
                        "+ $hiddenCount more session(s) not shown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Text(
                    "Existing logs are kept; these sessions are appended per day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun CardioImportPreviewDialog(
    pending: PendingCardioImport,
    distanceUnit: CardioDistanceUnit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val success = pending.outcome
    val datedSessions = remember(pending) {
        CardioHistoryImport.sessionsAddedByImport(pending.libraryBeforePreview, success)
    }
    val sample = datedSessions.take(PREVIEW_SESSION_SAMPLE_MAX)
    val hiddenCount = datedSessions.size - sample.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cardio Training Import Preview") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "${success.sessionsImported} session(s) on ${success.affectedDates.size} day(s). " +
                        if (success.customTypesUpserted > 0) {
                            "${success.customTypesUpserted} custom activity type(s) merged from file."
                        } else {
                            "No new custom activity types in file."
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "Preview Sample (Imported)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                sample.forEachIndexed { idx, dated ->
                    Text(
                        dated.dateIso,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = if (idx == 0) 0.dp else 10.dp, bottom = 4.dp)
                    )
                    Text(
                        dated.session.summaryLine(distanceUnit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (idx < sample.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
                if (hiddenCount > 0) {
                    Text(
                        "+ $hiddenCount more session(s) not shown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Text(
                    "Existing logs are kept; these sessions are appended per day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun programTotalScheduledBlocks(program: FitnessProgram): Int =
    program.weeklySchedule.sumOf { it.blocks.size }

@Composable
private fun ProgramImportPreviewDialog(
    pending: PendingProgramImport,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val env = pending.envelope
    val active = env.activeProgramId
    val strategy = env.strategy
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Programs Import Preview") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val before = pending.libraryBeforePreview
                val replaced = env.programs.count { before.programById(it.id) != null }
                val added = env.programs.size - replaced
                Text(
                    buildString {
                        append("${env.programs.size} program(s) will merge: ")
                        append("$added new")
                        if (replaced > 0) append(", $replaced replace existing (same id)")
                        append(".")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (active != null) {
                    Text(
                        "Active program after import: id $active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Text(
                        "Active program unchanged (no activeProgramId in file).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (strategy != null) {
                    Text(
                        "Program strategy after import: ${strategy.mode.name.lowercase().replaceFirstChar(Char::titlecase)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Text(
                        "Program strategy unchanged (no strategy in file).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Text(
                    "Programs In File",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                env.programs.forEachIndexed { idx, p ->
                    val blocks = programTotalScheduledBlocks(p)
                    Text(
                        "${idx + 1}. ${p.name}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = if (idx == 0) 0.dp else 8.dp, bottom = 2.dp)
                    )
                    Text(
                        "id: ${p.id} · $blocks scheduled block(s)" +
                            (p.sourceLabel?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Programs sync through your encrypted data relays when signed in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ImportFailureDialog(
    messages: List<String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Failed") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                messages.forEach { msg ->
                    Text(
                        "• $msg",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

@Composable
private fun BackupRestorePreviewDialog(
    pending: PendingBackupRestore,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ERV Backup Restore Preview") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "The sections below will replace the matching local ERV data on this device. Sections that are not present in the file stay unchanged.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                pending.preview.sections.forEachIndexed { index, section ->
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp, bottom = 2.dp)
                    )
                    Text(
                        section.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "If you are signed in, ERV will also queue a relay resync after the local restore succeeds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Restore") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
