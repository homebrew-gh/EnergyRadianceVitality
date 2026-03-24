@file:OptIn(ExperimentalMaterial3Api::class)

package com.erv.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioHistoryImport
import com.erv.app.cardio.CardioImportOutcome
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioRepository
import com.erv.app.cardio.CardioSync
import com.erv.app.cardio.summaryLine
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.RelayPublishOutbox
import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightHistoryImport
import com.erv.app.weighttraining.WeightImportDatedSession
import com.erv.app.weighttraining.WeightImportOutcome
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightSync
import com.erv.app.weighttraining.WeightWorkoutEntry
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.formatHiitBlockSummaryLine
import com.erv.app.weighttraining.formatSetSummaryLine
import com.erv.app.weighttraining.weightLoadUnitSuffix
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREVIEW_SESSION_SAMPLE_MAX = 5

private enum class ImportSilo {
    WEIGHT,
    CARDIO,
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

private data class ImportPreviewBlock(
    val title: String,
    val lines: List<String>,
)

internal object ImportExportDocAssets {
    const val KEY_AI = "ai"
    const val KEY_CSV = "csv"
    const val KEY_BUILTIN = "builtin"
    const val KEY_CARDIO_AI = "cardio_ai"
    const val KEY_CARDIO_CSV = "cardio_csv"

    fun pathForKey(docKey: String): String? = when (docKey) {
        KEY_AI -> "import_export/erv_weight_import_ai_guide.md"
        KEY_CSV -> "import_export/erv_weight_import_csv.md"
        KEY_BUILTIN -> "import_export/erv_weight_import_builtin_exercises.md"
        KEY_CARDIO_AI -> "import_export/erv_cardio_import_ai_guide.md"
        KEY_CARDIO_CSV -> "import_export/erv_cardio_import_csv.md"
        else -> null
    }

    fun titleForKey(docKey: String): String = when (docKey) {
        KEY_AI -> "AI import guide"
        KEY_CSV -> "CSV import format"
        KEY_BUILTIN -> "Built-in exercise IDs"
        KEY_CARDIO_AI -> "Cardio import (AI guide)"
        KEY_CARDIO_CSV -> "Cardio CSV import format"
        else -> "Reference"
    }
}

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
    relayPool: RelayPool?,
    signer: EventSigner?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val loadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.LB)
    val cardioDistanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)

    var showImportMenu by remember { mutableStateOf(false) }
    var activeImportSilo by remember { mutableStateOf<ImportSilo?>(null) }
    var pendingWeightImport by remember { mutableStateOf<PendingWeightImport?>(null) }
    var pendingCardioImport by remember { mutableStateOf<PendingCardioImport?>(null) }
    var parseErrorMessages by remember { mutableStateOf<List<String>?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val silo = activeImportSilo
        activeImportSilo = null
        if (silo == null) return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).readText()
                }
            }
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
            }
        }
    }

    if (showImportMenu) {
        AlertDialog(
            onDismissRequest = { showImportMenu = false },
            title = { Text("Import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Choose which data the file contains, then pick JSON or CSV.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(
                        onClick = {
                            showImportMenu = false
                            activeImportSilo = ImportSilo.WEIGHT
                            pickLauncher.launch(arrayOf("application/json", "text/csv", "text/plain", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Weight training") }
                    TextButton(
                        onClick = {
                            showImportMenu = false
                            activeImportSilo = ImportSilo.CARDIO
                            pickLauncher.launch(arrayOf("application/json", "text/csv", "text/plain", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cardio") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportMenu = false }) { Text("Cancel") }
            },
        )
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
                    weightRepository.commitImportedMerge(toApply.outcome)
                    val baseMsg =
                        "Imported ${toApply.outcome.sessionsImported} session(s); ${toApply.outcome.affectedDates.size} day(s)"
                    if (relayPool != null && signer != null) {
                        val state = weightRepository.currentState()
                        val entries = WeightSync.weightImportOutboxEntries(
                            state,
                            toApply.outcome.affectedDates,
                        )
                        val drain = withContext(Dispatchers.IO) {
                            val outbox = RelayPublishOutbox.get(context.applicationContext)
                            outbox.enqueueAll(entries)
                            outbox.kickDrain(relayPool, signer)
                        }
                        val relayMsg = buildString {
                            append(baseMsg)
                            append(". Queued ${entries.size} relay upload(s)")
                            if (drain.publishedOk > 0 || drain.publishedFail > 0) {
                                append(" — sent ${drain.publishedOk} now")
                                if (drain.publishedFail > 0) {
                                    append(", ${drain.publishedFail} will retry")
                                }
                            }
                            if (drain.remaining > 0) {
                                append(" — ${drain.remaining} still queued (auto-retry)")
                            }
                        }
                        snackbarHostState.showSnackbar(relayMsg)
                    } else {
                        snackbarHostState.showSnackbar("$baseMsg (not signed in — local only)")
                    }
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
                    cardioRepository.commitImportedCardioMerge(toApply.outcome)
                    val baseMsg =
                        "Imported ${toApply.outcome.sessionsImported} cardio session(s); ${toApply.outcome.affectedDates.size} day(s)"
                    if (relayPool != null && signer != null) {
                        val state = cardioRepository.currentState()
                        val entries = CardioSync.cardioImportOutboxEntries(
                            state,
                            toApply.outcome.affectedDates,
                        )
                        val drain = withContext(Dispatchers.IO) {
                            val outbox = RelayPublishOutbox.get(context.applicationContext)
                            outbox.enqueueAll(entries)
                            outbox.kickDrain(relayPool, signer)
                        }
                        val relayMsg = buildString {
                            append(baseMsg)
                            append(". Queued ${entries.size} relay upload(s)")
                            if (drain.publishedOk > 0 || drain.publishedFail > 0) {
                                append(" — sent ${drain.publishedOk} now")
                                if (drain.publishedFail > 0) {
                                    append(", ${drain.publishedFail} will retry")
                                }
                            }
                            if (drain.remaining > 0) {
                                append(" — ${drain.remaining} still queued (auto-retry)")
                            }
                        }
                        snackbarHostState.showSnackbar(relayMsg)
                    } else {
                        snackbarHostState.showSnackbar("$baseMsg (not signed in — local only)")
                    }
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
                title = { Text("Import / export") },
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
                .padding(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Merge weight training or cardio history from a JSON or CSV file that matches the ERV import spec. " +
                    "Pick the silo, then choose a file — preview shows what will be appended. Sessions are stored as Imported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { showImportMenu = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Text("Import", modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                "Export from the app will be added later. For now you can back up via Nostr sync (encrypted) when relays are configured.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Reference (bundled in the app)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            DocLinkRow(
                title = ImportExportDocAssets.titleForKey(ImportExportDocAssets.KEY_CARDIO_AI),
                subtitle = "Prompt and rules for an AI to emit valid cardio JSON",
                onClick = { onOpenDoc(ImportExportDocAssets.KEY_CARDIO_AI) }
            )
            DocLinkRow(
                title = ImportExportDocAssets.titleForKey(ImportExportDocAssets.KEY_CARDIO_CSV),
                subtitle = "Column headers for spreadsheet cardio import",
                onClick = { onOpenDoc(ImportExportDocAssets.KEY_CARDIO_CSV) }
            )
            DocLinkRow(
                title = ImportExportDocAssets.titleForKey(ImportExportDocAssets.KEY_AI),
                subtitle = "Prompt and rules for an AI to emit valid JSON",
                onClick = { onOpenDoc(ImportExportDocAssets.KEY_AI) }
            )
            DocLinkRow(
                title = ImportExportDocAssets.titleForKey(ImportExportDocAssets.KEY_CSV),
                subtitle = "Column headers and row rules for spreadsheet import",
                onClick = { onOpenDoc(ImportExportDocAssets.KEY_CSV) }
            )
            DocLinkRow(
                title = ImportExportDocAssets.titleForKey(ImportExportDocAssets.KEY_BUILTIN),
                subtitle = "All built-in exercise names and stable ids",
                onClick = { onOpenDoc(ImportExportDocAssets.KEY_BUILTIN) }
            )
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
        title = { Text("Import preview") },
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
                    "Sample (Imported)",
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
        title = { Text("Cardio import preview") },
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
                    "Sample (Imported)",
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

@Composable
private fun ImportFailureDialog(
    messages: List<String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import failed") },
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
private fun DocLinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
            supportingContent = {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingsImportDocViewerScreen(
    docKey: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val body = remember(docKey, context) {
        val path = ImportExportDocAssets.pathForKey(docKey)
        if (path == null) {
            "Unknown document."
        } else {
            try {
                context.assets.open(path).use { stream ->
                    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).readText()
                }
            } catch (_: Exception) {
                "Could not load this document from the app bundle."
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ImportExportDocAssets.titleForKey(docKey)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        )
    }
}
