@file:OptIn(ExperimentalMaterial3Api::class)

package com.erv.app.ui.bodytracker

import com.erv.app.ui.theme.ervTopAppBarColors
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.erv.app.ui.components.FieldLabel
import com.erv.app.ui.components.FormSectionLabelSmall
import com.erv.app.bodytracker.BodyMeasurementKey
import com.erv.app.bodytracker.BodyMeasurementLengthUnit
import com.erv.app.bodytracker.BodyTrackerMediaBackup
import com.erv.app.bodytracker.BodyTrackerDayLog
import com.erv.app.bodytracker.BodyTrackerLibraryState
import com.erv.app.bodytracker.BodyTrackerPhoto
import com.erv.app.bodytracker.BodyTrackerRepository
import com.erv.app.bodytracker.BodyTrackerSync
import com.erv.app.bodytracker.bodyTrackerDayLogsForSectionLog
import com.erv.app.bodytracker.datesWithBodyTrackerActivity
import com.erv.app.bodytracker.displayLabel
import com.erv.app.bodytracker.effectiveLengthUnit
import com.erv.app.bodytracker.hasActivityEntry
import com.erv.app.bodytracker.measurementKeysForForm
import com.erv.app.bodytracker.trackedMeasurementKeys
import com.erv.app.ui.components.PastDatePickerDialog
import com.erv.app.ui.components.pastEntryDateFormatter
import com.erv.app.bodytracker.isEffectivelyEmpty
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.data.kgToLb
import com.erv.app.data.lbToKg
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPool
import kotlinx.coroutines.CoroutineScope
import com.erv.app.SectionLogDateFilter
import com.erv.app.ui.dashboard.SectionLogCalendarSheet
import com.erv.app.ui.dashboard.SectionLogFilterBar
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val CM_PER_IN = 2.54

private val bodyTrackerLogCardDateFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())

private fun kgToDisplay(kg: Double, unit: BodyWeightUnit): Double =
    when (unit) {
        BodyWeightUnit.KG -> kg
        BodyWeightUnit.LB -> kgToLb(kg)
    }

private fun displayToKg(value: Double, unit: BodyWeightUnit): Double =
    when (unit) {
        BodyWeightUnit.KG -> value
        BodyWeightUnit.LB -> lbToKg(value)
    }

private fun cmToDisplay(cm: Double, lengthUnit: BodyMeasurementLengthUnit): Double =
    when (lengthUnit) {
        BodyMeasurementLengthUnit.CENTIMETERS -> cm
        BodyMeasurementLengthUnit.INCHES -> cm / CM_PER_IN
    }

private fun displayToCm(value: Double, lengthUnit: BodyMeasurementLengthUnit): Double =
    when (lengthUnit) {
        BodyMeasurementLengthUnit.CENTIMETERS -> value
        BodyMeasurementLengthUnit.INCHES -> value * CM_PER_IN
    }

private fun formatDoubleForField(d: Double): String {
    if (d.isNaN() || d.isInfinite()) return ""
    val rounded = (d * 1000.0).roundToInt() / 1000.0
    val s = if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    return s
}

private fun parsePositiveDouble(raw: String): Double? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    val v = t.replace(",", ".").toDoubleOrNull() ?: return null
    if (v <= 0.0) return null
    return v
}

private fun weightSummaryLine(log: BodyTrackerDayLog, weightUnit: BodyWeightUnit): String? {
    val kg = log.weightKg ?: return null
    val v = kgToDisplay(kg, weightUnit)
    val u = if (weightUnit == BodyWeightUnit.KG) "kg" else "lb"
    return "Weight · ${formatDoubleForField(v)} $u"
}

private fun measurementSummaryLine(
    key: BodyMeasurementKey,
    cm: Double,
    lengthUnit: BodyMeasurementLengthUnit
): String {
    val v = cmToDisplay(cm, lengthUnit)
    val u = if (lengthUnit == BodyMeasurementLengthUnit.INCHES) "in" else "cm"
    return "${key.displayLabel()} · ${formatDoubleForField(v)} $u"
}

@Composable
private fun BodyTrackerPrivacyCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Text(
            "Weight, measurements, and notes sync as encrypted Nostr events (kind 30078) via the same outbox as other ERV logs. Progress photos stay on this device unless you tap photo backup; backed-up blobs are encrypted before Blossom upload.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun BodyTrackerLengthUnitChips(
    selected: BodyMeasurementLengthUnit,
    scope: CoroutineScope,
    repository: BodyTrackerRepository,
    relayPool: RelayPool?,
    signer: EventSigner?,
    keyManager: KeyManager,
    appContext: android.content.Context
) {
    fun pick(unit: BodyMeasurementLengthUnit) {
        scope.launch {
            val current = repository.currentState()
            // Re-picking the shown unit still saves it, so it stops following lb/kg.
            if (current.lengthUnitUpdatedAtEpochSeconds > 0L && current.lengthUnit == unit) return@launch
            repository.setLengthUnit(unit)
            val pool = relayPool ?: return@launch
            val sig = signer ?: return@launch
            BodyTrackerSync.publishSettings(
                appContext,
                pool,
                sig,
                repository.currentState(),
                keyManager.relayUrlsForKind30078Publish()
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == BodyMeasurementLengthUnit.INCHES,
            onClick = { pick(BodyMeasurementLengthUnit.INCHES) },
            label = { Text("in") }
        )
        FilterChip(
            selected = selected == BodyMeasurementLengthUnit.CENTIMETERS,
            onClick = { pick(BodyMeasurementLengthUnit.CENTIMETERS) },
            label = { Text("cm") }
        )
    }
}

@Composable
private fun BodyTrackerPhotoSection(
    selectedDate: LocalDate,
    savedLog: BodyTrackerDayLog?,
    repository: BodyTrackerRepository,
    onLaunchPicker: () -> Unit,
    onRequestDelete: (BodyTrackerPhoto) -> Unit,
    onOpenFullscreen: (String) -> Unit
) {
    FormSectionLabelSmall("Progress photos")
    Spacer(Modifier.height(4.dp))
    Text(
        "On-device only — useful for a future time-lapse or side-by-side review.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onLaunchPicker) {
            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add photo")
        }
    }
    Spacer(Modifier.height(8.dp))
    val photos = savedLog?.photos.orEmpty()
    if (photos.isEmpty()) {
        Text(
            "No photos for ${selectedDate}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(photos, key = { it.id }) { photo ->
                BodyTrackerPhotoThumb(
                    file = repository.photoFile(photo.id),
                    onOpen = { onOpenFullscreen(photo.id) },
                    onDelete = { onRequestDelete(photo) }
                )
            }
        }
    }
}

@Composable
private fun BodyTrackerPhotoBackupCard(
    photoCount: Int,
    backingUp: Boolean,
    onBackUp: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FormSectionLabelSmall("Photo backup")
            Text(
                "Back up encrypted progress-photo blobs to Blossom. ERV uses your private Blossom server if set, otherwise it tries the Blossom endpoint on your Data relay.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onBackUp,
                enabled = !backingUp && photoCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        backingUp -> "Backing up…"
                        photoCount == 0 -> "No photos to back up"
                        else -> "Back up $photoCount photo(s)"
                    }
                )
            }
        }
    }
}

@Composable
private fun BodyTrackerLogEntryCard(
    log: BodyTrackerDayLog,
    repository: BodyTrackerRepository,
    weightUnit: BodyWeightUnit,
    lengthUnit: BodyMeasurementLengthUnit,
    showDateHeader: Boolean,
    onOpenPhoto: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var photosExpanded by remember(log.date) { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (log.photos.isNotEmpty()) {
                photosExpanded = !photosExpanded
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (showDateHeader) {
                    Text(
                        LocalDate.parse(log.date).format(bodyTrackerLogCardDateFormatter),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                }
                weightSummaryLine(log, weightUnit)?.let { line ->
                    Text(line, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                }
                BodyMeasurementKey.entries.forEach { key ->
                    val cm = log.measurementsCm[key.name] ?: return@forEach
                    Spacer(Modifier.height(2.dp))
                    Text(
                        measurementSummaryLine(key, cm, lengthUnit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (log.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Note", style = MaterialTheme.typography.labelSmall)
                    Text(log.note, style = MaterialTheme.typography.bodyMedium)
                }
                if (log.photos.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (photosExpanded) {
                            "${log.photos.size} progress photo(s) on this device"
                        } else {
                            "Tap to preview ${log.photos.size} progress photo(s)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (photosExpanded) {
                        Spacer(Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(log.photos, key = { it.id }) { photo ->
                                BodyTrackerPhotoPreviewThumb(
                                    file = repository.photoFile(photo.id),
                                    onOpen = { onOpenPhoto(photo.id) }
                                )
                            }
                        }
                    }
                }
            }
            Column {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit this day")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete entry")
                }
            }
        }
    }
}

@Composable
fun BodyTrackerCategoryScreen(
    repository: BodyTrackerRepository,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onOpenLog: (() -> Unit)?,
    initialDate: LocalDate? = null
) {
    BodyTrackerEditorScaffold(
        title = "Body Tracker",
        showLogAction = onOpenLog != null,
        onOpenLog = onOpenLog,
        initialDate = initialDate,
        onBack = onBack,
        repository = repository,
        userPreferences = userPreferences,
        relayPool = relayPool,
        signer = signer
    )
}

@Composable
fun BodyTrackerLogScreen(
    repository: BodyTrackerRepository,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onEditDay: (LocalDate) -> Unit,
    initialDate: LocalDate? = null,
    openCalendarInitially: Boolean = false
) {
    var dateFilter by remember(initialDate) {
        mutableStateOf(
            if (initialDate != null) SectionLogDateFilter.SingleDay(initialDate)
            else SectionLogDateFilter.AllHistory
        )
    }
    var showCalendar by remember(openCalendarInitially) { mutableStateOf(openCalendarInitially) }
    var pendingDelete by remember { mutableStateOf<BodyTrackerDayLog?>(null) }
    var fullscreenPhotoId by remember { mutableStateOf<String?>(null) }
    var pickingPastDay by remember { mutableStateOf(false) }

    val appContext = LocalContext.current.applicationContext
    val keyManager = LocalKeyManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state by repository.state.collectAsState(initial = BodyTrackerLibraryState())
    val weightUnit by userPreferences.bodyWeightUnit.collectAsState(initial = BodyWeightUnit.LB)
    val lengthUnit = state.effectiveLengthUnit(weightInKg = weightUnit == BodyWeightUnit.KG)

    if (pickingPastDay) {
        PastDatePickerDialog(
            initial = LocalDate.now(),
            onDismiss = { pickingPastDay = false },
            onPicked = {
                pickingPastDay = false
                onEditDay(it)
            }
        )
    }

    val datedEntries = remember(state, dateFilter) {
        state.bodyTrackerDayLogsForSectionLog(dateFilter)
    }
    val showLogDateOnCards = dateFilter !is SectionLogDateFilter.SingleDay
    val datesWithActivity = remember(state) { datesWithBodyTrackerActivity(state) }


    pendingDelete?.let { log ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove this entry?") },
            text = {
                Text(
                    "Removes this day from Body Tracker, deletes on-device photos for that day, and updates your relay copy.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val d = runCatching { LocalDate.parse(log.date) }.getOrNull()
                        pendingDelete = null
                        if (d != null) {
                            scope.launch {
                                repository.deleteDayLog(d)
                                val pool = relayPool
                                val sig = signer
                                if (pool != null && sig != null) {
                                    BodyTrackerSync.publishClearedDay(
                                        appContext,
                                        pool,
                                        sig,
                                        log.date,
                                        keyManager.relayUrlsForKind30078Publish()
                                    )
                                }
                                snackbarHostState.showSnackbar("Entry removed")
                            }
                        }
                    }
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    fullscreenPhotoId?.let { pid ->
        val bmp = remember(pid) {
            BitmapFactory.decodeFile(repository.photoFile(pid).absolutePath)
        }
        AlertDialog(
            onDismissRequest = { fullscreenPhotoId = null },
            confirmButton = {
                TextButton(onClick = { fullscreenPhotoId = null }) { Text("Close") }
            },
            title = { Text("Progress photo") },
            text = {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("Could not load image.")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickingPastDay = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Log a past day") }
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("Body Tracker Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = ervTopAppBarColors()
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                            when (dateFilter) {
                                SectionLogDateFilter.AllHistory -> "No Body Tracker entries yet."
                                is SectionLogDateFilter.SingleDay -> "Nothing logged for this date."
                                is SectionLogDateFilter.DateRange -> "Nothing logged in this date range."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Save from Body Tracker, or tap Log a past day to fill in an earlier date.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Newest first. Tap edit to change a day.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    items(datedEntries, key = { it.date }) { log ->
                        BodyTrackerLogEntryCard(
                            log = log,
                            repository = repository,
                            weightUnit = weightUnit,
                            lengthUnit = lengthUnit,
                            showDateHeader = showLogDateOnCards,
                            onOpenPhoto = { fullscreenPhotoId = it },
                            onEdit = {
                                runCatching { LocalDate.parse(log.date) }.getOrNull()?.let(onEditDay)
                            },
                            onDelete = { pendingDelete = log }
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
            onApplyFilter = {
                dateFilter = it
                showCalendar = false
            }
        )
    }
}

@Composable
private fun BodyTrackerEditorScaffold(
    title: String,
    showLogAction: Boolean,
    onOpenLog: (() -> Unit)?,
    initialDate: LocalDate?,
    onBack: () -> Unit,
    repository: BodyTrackerRepository,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
) {
    val appContext = LocalContext.current.applicationContext
    val keyManager = LocalKeyManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val formScrollState = rememberScrollState()
    val state by repository.state.collectAsState(initial = BodyTrackerLibraryState())
    val weightUnit by userPreferences.bodyWeightUnit.collectAsState(initial = BodyWeightUnit.LB)
    val blossomPrivateOrigin by userPreferences.blossomPrivateServerOrigin.collectAsState(initial = "")
    val trustSelfSignedLanTls by userPreferences.trustSelfSignedLanTls.collectAsState(initial = false)

    val lengthUnit = state.effectiveLengthUnit(weightInKg = weightUnit == BodyWeightUnit.KG)

    var selectedDate by rememberSaveable(initialDate) { mutableStateOf(initialDate ?: LocalDate.now()) }
    var pickingDate by remember { mutableStateOf(false) }
    var choosingMeasurements by remember { mutableStateOf(false) }
    var weightText by remember { mutableStateOf("") }
    val measurementTexts = remember {
        BodyMeasurementKey.entries.associateWith { mutableStateOf("") }
    }
    var noteText by remember { mutableStateOf("") }
    var pendingPhotoDelete by remember { mutableStateOf<BodyTrackerPhoto?>(null) }
    var fullscreenPhotoId by remember { mutableStateOf<String?>(null) }
    var backingUpPhotos by remember { mutableStateOf(false) }

    val savedLog = remember(state.logs, selectedDate) {
        state.logs.firstOrNull { it.date == selectedDate.toString() }
    }
    val photoCount = remember(state.logs) { state.logs.sumOf { it.photos.size } }

    val formKeys = remember(state, savedLog) { state.measurementKeysForForm(savedLog) }

    LaunchedEffect(selectedDate, state.logs, weightUnit, lengthUnit) {
        val log = state.logs.firstOrNull { it.date == selectedDate.toString() }
        weightText = log?.weightKg?.let { formatDoubleForField(kgToDisplay(it, weightUnit)) }.orEmpty()
        BodyMeasurementKey.entries.forEach { key ->
            val cm = log?.measurementsCm?.get(key.name)
            measurementTexts[key]?.value =
                cm?.let { formatDoubleForField(cmToDisplay(it, lengthUnit)) }.orEmpty()
        }
        noteText = log?.note.orEmpty()
    }

    val pickPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                repository.addPhotoFromUri(selectedDate, uri)
                snackbarHostState.showSnackbar("Photo saved on this device only")
            }
        }
    }


    fun buildLogFromDraft(): BodyTrackerDayLog {
        val w = parsePositiveDouble(weightText)?.let { displayToKg(it, weightUnit) }
        val mm = mutableMapOf<String, Double>()
        BodyMeasurementKey.entries.forEach { key ->
            parsePositiveDouble(measurementTexts[key]!!.value)?.let { display ->
                mm[key.name] = displayToCm(display, lengthUnit)
            }
        }
        return BodyTrackerDayLog(
            date = selectedDate.toString(),
            weightKg = w,
            measurementsCm = mm,
            note = noteText.trim(),
            photos = savedLog?.photos.orEmpty()
        )
    }

    if (pickingDate) {
        PastDatePickerDialog(
            initial = selectedDate,
            onDismiss = { pickingDate = false },
            onPicked = {
                pickingDate = false
                selectedDate = it
            }
        )
    }

    if (choosingMeasurements) {
        BodyTrackerMeasurementPickerDialog(
            initial = state.trackedMeasurementKeys(),
            onDismiss = { choosingMeasurements = false },
            onSave = { picked ->
                choosingMeasurements = false
                scope.launch {
                    repository.setTrackedMeasurements(picked)
                    val pool = relayPool ?: return@launch
                    val sig = signer ?: return@launch
                    BodyTrackerSync.publishSettings(
                        appContext,
                        pool,
                        sig,
                        repository.currentState(),
                        keyManager.relayUrlsForKind30078Publish()
                    )
                }
            }
        )
    }

    pendingPhotoDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { pendingPhotoDelete = null },
            title = { Text("Remove photo?") },
            text = {
                Text(
                    "This deletes the file from this device. Any prior Blossom backup remains listed in your encrypted media manifest until the next photo backup.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val p = photo
                        pendingPhotoDelete = null
                        scope.launch {
                            repository.removePhoto(selectedDate, p.id)
                            val pool = relayPool
                            val sig = signer
                            if (pool != null && sig != null) {
                                BodyTrackerSync.publishDayAfterLocalChange(
                                    appContext,
                                    pool,
                                    sig,
                                    repository,
                                    selectedDate,
                                    keyManager.relayUrlsForKind30078Publish()
                                )
                            }
                            snackbarHostState.showSnackbar("Photo removed")
                        }
                    }
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPhotoDelete = null }) { Text("Cancel") }
            }
        )
    }

    fullscreenPhotoId?.let { pid ->
        val bmp = remember(pid) {
            BitmapFactory.decodeFile(repository.photoFile(pid).absolutePath)
        }
        AlertDialog(
            onDismissRequest = { fullscreenPhotoId = null },
            confirmButton = {
                TextButton(onClick = { fullscreenPhotoId = null }) { Text("Close") }
            },
            title = { Text("Progress photo") },
            text = {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("Could not load image.")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (showLogAction && onOpenLog != null) {
                        IconButton(onClick = onOpenLog) {
                            Icon(Icons.Default.DateRange, contentDescription = "Open log")
                        }
                    }
                },
                colors = ervTopAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(formScrollState)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (selectedDate == LocalDate.now()) "Today" else selectedDate.format(bodyTrackerLogCardDateFormatter),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (savedLog?.hasActivityEntry() == true) "Editing the saved entry for this day." else "Nothing saved for this day yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selectedDate != LocalDate.now()) {
                        TextButton(onClick = { selectedDate = LocalDate.now() }) { Text("Today") }
                    }
                    OutlinedButton(onClick = { pickingDate = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Change date")
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (weightUnit == BodyWeightUnit.KG) "Weight (kg)" else "Weight (lb)") },
                    supportingText = { Text("Uses the lb/kg choice in Settings.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FormSectionLabelSmall("Measurements", modifier = Modifier.weight(1f))
                    BodyTrackerLengthUnitChips(
                        selected = lengthUnit,
                        scope = scope,
                        repository = repository,
                        relayPool = relayPool,
                        signer = signer,
                        keyManager = keyManager,
                        appContext = appContext
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Optional. Leave blank to skip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                val lengthSuffix = if (lengthUnit == BodyMeasurementLengthUnit.INCHES) "in" else "cm"
                formKeys.forEach { key ->
                    OutlinedTextField(
                        value = measurementTexts[key]!!.value,
                        onValueChange = { measurementTexts[key]!!.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true,
                        label = { Text("${key.displayLabel()} ($lengthSuffix)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                if (formKeys.isEmpty()) {
                    Text(
                        "No measurements picked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { choosingMeasurements = true }) { Text("Choose measurements") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { FieldLabel("Note") }
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val built = buildLogFromDraft()
                            val cleared = built.isEffectivelyEmpty()
                            repository.saveDayLog(built, fromMeasurementFormSave = true)
                            val kg = built.weightKg
                            // Only the newest weigh-in feeds the default body weight, not a backfilled day.
                            val newerWeighIn = state.logs.any { it.weightKg != null && it.date > built.date }
                            if (kg != null && !newerWeighIn) {
                                val displayVal = kgToDisplay(kg, weightUnit)
                                val raw = formatDoubleForField(displayVal)
                                if (raw.isNotBlank()) {
                                    userPreferences.setFallbackBodyWeight(raw, weightUnit)
                                }
                            }
                            val pool = relayPool
                            val sig = signer
                            if (pool != null && sig != null) {
                                BodyTrackerSync.publishDayAfterLocalChange(
                                    appContext,
                                    pool,
                                    sig,
                                    repository,
                                    selectedDate,
                                    keyManager.relayUrlsForKind30078Publish()
                                )
                            }
                            snackbarHostState.showSnackbar(
                                if (cleared) "Entry cleared" else "Saved for ${selectedDate.format(pastEntryDateFormatter)}"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selectedDate == LocalDate.now()) "Save today" else "Save this day")
                }
                Spacer(Modifier.height(20.dp))
                BodyTrackerTrendCard(
                    state = state,
                    weightUnit = weightUnit,
                    lengthUnit = lengthUnit
                )
                Spacer(Modifier.height(20.dp))
                BodyTrackerPhotoSection(
                    selectedDate = selectedDate,
                    savedLog = savedLog,
                    repository = repository,
                    onLaunchPicker = {
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRequestDelete = { pendingPhotoDelete = it },
                    onOpenFullscreen = { fullscreenPhotoId = it }
                )
                Spacer(Modifier.height(16.dp))
                BodyTrackerPhotoBackupCard(
                    photoCount = photoCount,
                    backingUp = backingUpPhotos,
                    onBackUp = {
                        if (backingUpPhotos) return@BodyTrackerPhotoBackupCard
                        scope.launch {
                            val pool = relayPool
                            val sig = signer
                            if (pool == null || sig == null) {
                                snackbarHostState.showSnackbar("Sign in with Nostr to back up photos.")
                                return@launch
                            }
                            backingUpPhotos = true
                            try {
                                val result = BodyTrackerMediaBackup.backupProgressPhotos(
                                    appContext = appContext,
                                    repository = repository,
                                    relayPool = pool,
                                    signer = sig,
                                    dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
                                    explicitPrivateBlossomOrigin = blossomPrivateOrigin,
                                    trustSelfSignedLanTls = trustSelfSignedLanTls,
                                )
                                val message = when {
                                    result.origin == null ->
                                        "Set a private Blossom server or use Haven as your Data relay."
                                    result.totalPhotos == 0 ->
                                        "No progress photos to back up."
                                    else ->
                                        "Photo backup: ${result.uploaded} uploaded, ${result.reused} already backed up, ${result.failed} failed. Manifest ${if (result.manifestQueued) "queued" else "not queued"}."
                                }
                                snackbarHostState.showSnackbar(message)
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Photo backup failed: ${e.message ?: "unknown error"}")
                            } finally {
                                backingUpPhotos = false
                            }
                        }
                    }
                )
                Spacer(Modifier.height(16.dp))
                BodyTrackerPrivacyCard()
            }
        }
    }
}

@Composable
private fun BodyTrackerPhotoThumb(
    file: java.io.File,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val bmp = remember(file.absolutePath) {
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }
    Box(modifier = Modifier.size(96.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpen() }
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Progress photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(36.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete photo",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun BodyTrackerPhotoPreviewThumb(
    file: java.io.File,
    onOpen: () -> Unit
) {
    val bmp = remember(file.absolutePath) {
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpen() }
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Progress photo preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("?", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
