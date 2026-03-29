@file:OptIn(ExperimentalMaterial3Api::class)

package com.erv.app.ui.bodytracker

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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import com.erv.app.bodytracker.BodyMeasurementKey
import com.erv.app.bodytracker.BodyMeasurementLengthUnit
import com.erv.app.bodytracker.BodyTrackerDayLog
import com.erv.app.bodytracker.BodyTrackerLibraryState
import com.erv.app.bodytracker.BodyTrackerPhoto
import com.erv.app.bodytracker.BodyTrackerRepository
import com.erv.app.bodytracker.BodyTrackerSync
import com.erv.app.bodytracker.bodyTrackerDayLogsForSectionLog
import com.erv.app.bodytracker.datesWithBodyTrackerActivity
import com.erv.app.bodytracker.displayLabel
import com.erv.app.bodytracker.isEffectivelyEmpty
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.theme.ErvHeaderRed
import kotlinx.coroutines.CoroutineScope
import com.erv.app.SectionLogDateFilter
import com.erv.app.ui.dashboard.SectionLogCalendarSheet
import com.erv.app.ui.dashboard.SectionLogFilterBar
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val LB_PER_KG = 2.2046226218
private const val CM_PER_IN = 2.54

private val bodyTrackerLogCardDateFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())

private fun kgToDisplay(kg: Double, unit: BodyWeightUnit): Double =
    when (unit) {
        BodyWeightUnit.KG -> kg
        BodyWeightUnit.LB -> kg * LB_PER_KG
    }

private fun displayToKg(value: Double, unit: BodyWeightUnit): Double =
    when (unit) {
        BodyWeightUnit.KG -> value
        BodyWeightUnit.LB -> value / LB_PER_KG
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
            "Weight, measurements, and notes sync as encrypted Nostr events (kind 30078) via the same outbox as other ERV logs. Progress photos stay on this device only — they are never uploaded to relays or Blossom.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun BodyTrackerLengthUnitChips(
    state: BodyTrackerLibraryState,
    scope: CoroutineScope,
    repository: BodyTrackerRepository,
    relayPool: RelayPool?,
    signer: EventSigner?,
    keyManager: KeyManager,
    appContext: android.content.Context
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.lengthUnit == BodyMeasurementLengthUnit.CENTIMETERS,
            onClick = {
                scope.launch {
                    if (state.lengthUnit == BodyMeasurementLengthUnit.CENTIMETERS) return@launch
                    repository.setLengthUnit(BodyMeasurementLengthUnit.CENTIMETERS)
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
            },
            label = { Text("cm") }
        )
        FilterChip(
            selected = state.lengthUnit == BodyMeasurementLengthUnit.INCHES,
            onClick = {
                scope.launch {
                    if (state.lengthUnit == BodyMeasurementLengthUnit.INCHES) return@launch
                    repository.setLengthUnit(BodyMeasurementLengthUnit.INCHES)
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
            },
            label = { Text("in") }
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
    Text("Progress photos", style = MaterialTheme.typography.titleSmall)
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
private fun BodyTrackerLogEntryCard(
    log: BodyTrackerDayLog,
    weightUnit: BodyWeightUnit,
    lengthUnit: BodyMeasurementLengthUnit,
    showDateHeader: Boolean,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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
                        "${log.photos.size} progress photo(s) on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete entry")
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
    onOpenLog: () -> Unit
) {
    BodyTrackerEditorScaffold(
        title = "Body Tracker",
        showLogAction = true,
        onOpenLog = onOpenLog,
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
    onOpenEditor: () -> Unit,
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

    val appContext = LocalContext.current.applicationContext
    val keyManager = LocalKeyManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state by repository.state.collectAsState(initial = BodyTrackerLibraryState())
    val weightUnit by userPreferences.bodyWeightUnit.collectAsState(initial = BodyWeightUnit.LB)

    val datedEntries = remember(state, dateFilter) {
        state.bodyTrackerDayLogsForSectionLog(dateFilter)
    }
    val showLogDateOnCards = dateFilter !is SectionLogDateFilter.SingleDay
    val datesWithActivity = remember(state) { datesWithBodyTrackerActivity(state) }

    val headerColor = ErvHeaderRed
    val onHeader = Color.White

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Body Tracker log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onOpenEditor,
                        colors = ButtonDefaults.textButtonColors(contentColor = onHeader)
                    ) {
                        Text("Log day")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerColor,
                    titleContentColor = onHeader,
                    navigationIconContentColor = onHeader,
                    actionIconContentColor = onHeader
                )
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
                            "Save from Body Tracker to create an entry for that day.",
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
                            "Newest first. Tap delete to remove a day.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    items(datedEntries, key = { it.date }) { log ->
                        BodyTrackerLogEntryCard(
                            log = log,
                            weightUnit = weightUnit,
                            lengthUnit = state.lengthUnit,
                            showDateHeader = showLogDateOnCards,
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

    val selectedDate = LocalDate.now()
    var weightText by remember { mutableStateOf("") }
    val measurementTexts = remember {
        BodyMeasurementKey.entries.associateWith { mutableStateOf("") }
    }
    var noteText by remember { mutableStateOf("") }
    var pendingPhotoDelete by remember { mutableStateOf<BodyTrackerPhoto?>(null) }
    var fullscreenPhotoId by remember { mutableStateOf<String?>(null) }

    val savedLog = remember(state.logs, selectedDate) {
        state.logs.firstOrNull { it.date == selectedDate.toString() }
    }

    LaunchedEffect(selectedDate, state.logs, weightUnit, state.lengthUnit) {
        val log = state.logs.firstOrNull { it.date == selectedDate.toString() }
        weightText = log?.weightKg?.let { formatDoubleForField(kgToDisplay(it, weightUnit)) }.orEmpty()
        BodyMeasurementKey.entries.forEach { key ->
            val cm = log?.measurementsCm?.get(key.name)
            measurementTexts[key]?.value =
                cm?.let { formatDoubleForField(cmToDisplay(it, state.lengthUnit)) }.orEmpty()
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

    val headerColor = ErvHeaderRed
    val onHeader = Color.White

    fun buildLogFromDraft(): BodyTrackerDayLog {
        val w = parsePositiveDouble(weightText)?.let { displayToKg(it, weightUnit) }
        val mm = mutableMapOf<String, Double>()
        BodyMeasurementKey.entries.forEach { key ->
            parsePositiveDouble(measurementTexts[key]!!.value)?.let { display ->
                mm[key.name] = displayToCm(display, state.lengthUnit)
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

    pendingPhotoDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { pendingPhotoDelete = null },
            title = { Text("Remove photo?") },
            text = {
                Text(
                    "This deletes the file from this device. It was never uploaded.",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerColor,
                    titleContentColor = onHeader,
                    navigationIconContentColor = onHeader,
                    actionIconContentColor = onHeader
                )
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
                Text(
                    "Fill in what you want to track for today, then save. Open the log to browse or filter by date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text("Units", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                BodyTrackerLengthUnitChips(
                    state = state,
                    scope = scope,
                    repository = repository,
                    relayPool = relayPool,
                    signer = signer,
                    keyManager = keyManager,
                    appContext = appContext
                )
                Spacer(Modifier.height(16.dp))
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
                BodyTrackerPrivacyCard()
                Spacer(Modifier.height(16.dp))
                Text("Weight", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Uses the same lb/kg preference as Settings → Units & Body.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (weightUnit == BodyWeightUnit.KG) "Weight (kg)" else "Weight (lb)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(16.dp))
                Text("Measurements", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Optional. Leave blank to skip. Stored internally as cm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                BodyMeasurementKey.entries.forEach { key ->
                    OutlinedTextField(
                        value = measurementTexts[key]!!.value,
                        onValueChange = { measurementTexts[key]!!.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true,
                        label = {
                            val u =
                                if (state.lengthUnit == BodyMeasurementLengthUnit.INCHES) "in" else "cm"
                            Text("${key.displayLabel()} ($u)")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("Note") }
                )
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val built = buildLogFromDraft()
                            val cleared = built.isEffectivelyEmpty()
                            repository.saveDayLog(built, fromMeasurementFormSave = true)
                            val kg = built.weightKg
                            if (kg != null) {
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
                                if (cleared) "Entry cleared" else "Saved for ${selectedDate}"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save this day")
                }
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
