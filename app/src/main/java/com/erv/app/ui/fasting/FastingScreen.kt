package com.erv.app.ui.fasting

import com.erv.app.ui.theme.ervTopAppBarColors
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erv.app.SectionLogDateFilter
import com.erv.app.bodytracker.BodyTrackerDayLog
import com.erv.app.bodytracker.BodyTrackerRepository
import com.erv.app.bodytracker.BodyTrackerSync
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.data.lbToKg
import com.erv.app.fasting.FastingCompletionScheduler
import com.erv.app.fasting.FastingForegroundService
import com.erv.app.fasting.FastingLibraryState
import com.erv.app.fasting.FastingRepository
import com.erv.app.fasting.FastingSession
import com.erv.app.fasting.FastingSessionKind
import com.erv.app.fasting.FastingStatus
import com.erv.app.fasting.INTERMITTENT_PRESETS
import com.erv.app.fasting.IntermittentFastingPhase
import com.erv.app.fasting.IntermittentFastingPlan
import com.erv.app.fasting.IntermittentFastingStatus
import com.erv.app.fasting.currentStatus
import com.erv.app.fasting.datedFastingSessionsForSectionLog
import com.erv.app.fasting.datesWithFastingActivity
import com.erv.app.fasting.displayName
import com.erv.app.fasting.eatingLengthMinutes
import com.erv.app.fasting.elapsedSeconds
import com.erv.app.fasting.fastEndMinutes
import com.erv.app.fasting.fastStartMinutes
import com.erv.app.fasting.fastingLengthMinutes
import com.erv.app.fasting.fastingNowEpochSeconds
import com.erv.app.fasting.formatFastingDateTime
import com.erv.app.fasting.formatFastingDuration
import com.erv.app.fasting.formatMinutesLength
import com.erv.app.fasting.formatTargetHours
import com.erv.app.fasting.logDate
import com.erv.app.fasting.normalized
import com.erv.app.fasting.progress
import com.erv.app.fasting.publishFastingChange
import com.erv.app.fasting.remainingSeconds
import com.erv.app.fasting.targetDaysForHours
import com.erv.app.fasting.targetHoursTotal
import com.erv.app.fasting.withFastWindow
import com.erv.app.fasting.withPreset
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.components.ClockTimePickerDialog
import com.erv.app.ui.components.FormSectionLabel
import com.erv.app.ui.components.FormSectionLabelSmall
import com.erv.app.ui.dashboard.SectionLogCalendarSheet
import com.erv.app.ui.dashboard.SectionLogFilterBar
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

private enum class FastingMode {
    INTERMITTENT,
    EXTENDED,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastingScreen(
    repository: FastingRepository,
    bodyTrackerRepository: BodyTrackerRepository,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val keyManager = LocalKeyManager.current
    val state by repository.state.collectAsState(initial = FastingLibraryState())
    val weightUnit by userPreferences.bodyWeightUnit.collectAsState(initial = BodyWeightUnit.LB)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var nowEpochSeconds by remember { mutableLongStateOf(fastingNowEpochSeconds()) }
    var completionSession by remember { mutableStateOf<FastingSession?>(null) }
    var cancelSession by remember { mutableStateOf<FastingSession?>(null) }
    var editActiveSession by remember { mutableStateOf<FastingSession?>(null) }
    var selectedMode by rememberSaveable { mutableStateOf<FastingMode?>(null) }
    val mode = selectedMode ?: if (state.activeSession != null) FastingMode.EXTENDED else FastingMode.INTERMITTENT

    suspend fun publish(dates: Collection<LocalDate>) {
        publishFastingChange(appContext, relayPool, signer, repository, dates, keyManager.relayUrlsForKind30078Publish())
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowEpochSeconds = fastingNowEpochSeconds()
            delay(1_000L)
        }
    }

    // Log finished windows of the daily schedule while the screen is open (also done at app start).
    LaunchedEffect(state.intermittentPlan.autoLog) {
        if (!state.intermittentPlan.autoLog) return@LaunchedEffect
        while (true) {
            val added = repository.autoLogIntermittentWindows()
            if (added.isNotEmpty()) {
                publish(added.map { it.logDate() })
                snackbarHostState.showSnackbar(
                    if (added.size == 1) "Logged your ${formatFastingDuration(added[0].elapsedSeconds())} fast"
                    else "Logged ${added.size} fasts"
                )
            }
            delay(60_000L)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Fasting") },
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
                colors = ervTopAppBarColors(),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Spacer(Modifier.height(2.dp))
                FastingModeSelector(
                    selectedMode = mode,
                    onSelected = { selectedMode = it },
                )
            }
            item {
                val active = state.activeSession
                when (mode) {
                    FastingMode.INTERMITTENT -> {
                        IntermittentFastingCard(
                            plan = state.intermittentPlan,
                            nowEpochSeconds = nowEpochSeconds,
                            onPlanChanged = { plan ->
                                scope.launch {
                                    repository.setIntermittentPlan(plan)
                                    publish(emptyList())
                                }
                            },
                            onLogCompletedWindow = { plan, status ->
                                scope.launch {
                                    val logged = repository.logCompletedIntermittentWindow(plan, status, nowEpochSeconds)
                                    if (logged != null) publish(listOf(logged.logDate()))
                                    snackbarHostState.showSnackbar(
                                        if (logged == null) "This fasting window is already logged" else "Intermittent fast logged"
                                    )
                                }
                            },
                        )
                    }
                    FastingMode.EXTENDED -> {
                        if (active == null) {
                            FastingStartCard(
                                onStart = { hours, startedAt ->
                                    scope.launch {
                                        val session = repository.startFast(hours, startedAt)
                                        FastingForegroundService.start(context, session)
                                        val scheduled = FastingCompletionScheduler.schedule(context, session)
                                        publish(emptyList())
                                        snackbarHostState.showSnackbar(
                                            if (scheduled) {
                                                "${formatTargetHours(hours)} fast started"
                                            } else {
                                                "Fast started. Enable exact alarms for end-time reminders."
                                            }
                                        )
                                    }
                                },
                            )
                        } else {
                            ActiveFastingCard(
                                session = active,
                                nowEpochSeconds = nowEpochSeconds,
                                onComplete = { completionSession = active },
                                onCancel = { cancelSession = active },
                                onEdit = { editActiveSession = active },
                            )
                        }
                    }
                }
            }
            if (mode == FastingMode.EXTENDED) {
                item {
                    FastingSafetyCard()
                }
            }
            item {
                FastingRecentHistoryCard(
                    history = state.history.take(3),
                    onOpenLog = onOpenLog,
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    editActiveSession?.let { session ->
        ActiveFastEditDialog(
            session = session,
            onDismiss = { editActiveSession = null },
            onSave = { start, hours ->
                editActiveSession = null
                scope.launch {
                    val updated = repository.editActive(start, hours) ?: return@launch
                    FastingCompletionScheduler.cancel(context, session.id)
                    FastingCompletionScheduler.schedule(context, updated)
                    FastingForegroundService.start(context, updated)
                    publish(emptyList())
                    snackbarHostState.showSnackbar("Fast updated")
                }
            }
        )
    }

    completionSession?.let { session ->
        FastingCompletionDialog(
            session = session,
            weightUnit = weightUnit,
            onDismiss = { completionSession = null },
            onSave = { input ->
                completionSession = null
                scope.launch {
                    val unitLabel = if (weightUnit == BodyWeightUnit.KG) "kg" else "lb"
                    val weightText = input.weight?.let { "${formatWeight(it)} $unitLabel" }.orEmpty()
                    val saved = repository.completeActive(input.mood, weightText, input.notes)
                    FastingCompletionScheduler.cancel(context, session.id)
                    FastingForegroundService.stop(context)
                    if (saved != null) publish(listOf(saved.logDate()))
                    val weight = input.weight
                    if (input.saveWeightToBodyTracker && weight != null) {
                        val kg = if (weightUnit == BodyWeightUnit.KG) weight else lbToKg(weight)
                        val day = saved?.logDate() ?: LocalDate.now()
                        val existing = bodyTrackerRepository.currentState().logFor(day) ?: BodyTrackerDayLog(date = day.toString())
                        bodyTrackerRepository.saveDayLog(existing.copy(weightKg = kg), fromMeasurementFormSave = true)
                        if (relayPool != null && signer != null) {
                            BodyTrackerSync.publishDayAfterLocalChange(
                                appContext, relayPool, signer, bodyTrackerRepository, day,
                                keyManager.relayUrlsForKind30078Publish(),
                            )
                        }
                        snackbarHostState.showSnackbar("Fast saved. Weight added to Body Tracker.")
                    } else {
                        snackbarHostState.showSnackbar("Fast saved")
                    }
                }
            },
        )
    }

    cancelSession?.let { session ->
        AlertDialog(
            onDismissRequest = { cancelSession = null },
            title = { Text("Cancel fast?") },
            text = { Text("This stops the active timer and records the fast as cancelled in history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val saved = repository.cancelActive()
                            FastingCompletionScheduler.cancel(context, session.id)
                            FastingForegroundService.stop(context)
                            cancelSession = null
                            if (saved != null) publish(listOf(saved.logDate()))
                            snackbarHostState.showSnackbar("Fast cancelled")
                        }
                    },
                ) {
                    Text("Cancel fast")
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelSession = null }) {
                    Text("Keep fasting")
                }
            },
        )
    }
}

private fun formatWeight(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return if (tenths % 10 == 0L) (tenths / 10).toString() else "%.1f".format(Locale.US, tenths / 10.0)
}

@Composable
private fun FastingModeSelector(
    selectedMode: FastingMode,
    onSelected: (FastingMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilterChip(
            selected = selectedMode == FastingMode.INTERMITTENT,
            onClick = { onSelected(FastingMode.INTERMITTENT) },
            label = { Text("Daily Schedule") },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = selectedMode == FastingMode.EXTENDED,
            onClick = { onSelected(FastingMode.EXTENDED) },
            label = { Text("Longer Fast") },
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntermittentFastingCard(
    plan: IntermittentFastingPlan,
    nowEpochSeconds: Long,
    onPlanChanged: (IntermittentFastingPlan) -> Unit,
    onLogCompletedWindow: (IntermittentFastingPlan, IntermittentFastingStatus) -> Unit,
) {
    val normalized = plan.normalized()
    val status = remember(normalized, nowEpochSeconds) { normalized.currentStatus(nowEpochSeconds) }
    val phaseLabel = if (status.phase == IntermittentFastingPhase.FASTING) "Fasting" else "Eating window"
    val nextLabel = if (status.phase == IntermittentFastingPhase.FASTING) "Eating starts" else "Fast starts"
    val timeRemaining = formatFastingDuration(status.currentEndEpochSeconds - nowEpochSeconds)
    var pickFastStart by remember { mutableStateOf(false) }
    var pickFastEnd by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    text = "Daily fasting schedule",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Set when your fast starts and ends each day. Times move in 15-minute steps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormSectionLabel("Fasting window")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    FormSectionLabelSmall("Fast starts")
                    OutlinedButton(onClick = { pickFastStart = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(formatMinuteOfDay(normalized.fastStartMinutes()))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    FormSectionLabelSmall("Fast ends")
                    OutlinedButton(onClick = { pickFastEnd = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(formatMinuteOfDay(normalized.fastEndMinutes()))
                    }
                }
            }
            Text(
                text = "Fasting ${formatMinutesLength(normalized.fastingLengthMinutes())}, eating ${formatMinutesLength(normalized.eatingLengthMinutes())}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormSectionLabel("Quick lengths")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                INTERMITTENT_PRESETS.forEach { (label, hours) ->
                    FilterChip(
                        selected = normalized.protocolLabel == label,
                        onClick = { onPlanChanged(normalized.withPreset(label, hours)) },
                        label = { Text(label) },
                    )
                }
                FilterChip(
                    selected = normalized.protocolLabel == "Custom",
                    onClick = { pickFastStart = true },
                    label = { Text("Custom") },
                )
            }
            Text(
                text = "A quick length keeps your end time and moves the start.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = phaseLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = timeRemaining,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "$nextLabel ${formatFastingDateTime(status.currentEndEpochSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Log each fast automatically", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Each fast is saved to your log when it ends. Skipped a day? Delete it in the log.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = normalized.autoLog,
                    onCheckedChange = { onPlanChanged(normalized.copy(autoLog = it)) },
                )
            }
            if (!normalized.autoLog) {
                if (status.phase == IntermittentFastingPhase.EATING && status.completedFastStartEpochSeconds != null) {
                    Button(
                        onClick = { onLogCompletedWindow(normalized, status) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Log today's fast")
                    }
                } else {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("You can log the fast once it ends")
                    }
                }
            }
        }
    }

    if (pickFastStart) {
        ClockTimePickerDialog(
            initial = minuteOfDayToTime(normalized.fastStartMinutes()),
            title = "Fast starts",
            onDismiss = { pickFastStart = false },
            onPicked = { t ->
                pickFastStart = false
                onPlanChanged(normalized.withFastWindow(t.hour * 60 + t.minute, normalized.fastEndMinutes()))
            }
        )
    }
    if (pickFastEnd) {
        ClockTimePickerDialog(
            initial = minuteOfDayToTime(normalized.fastEndMinutes()),
            title = "Fast ends",
            onDismiss = { pickFastEnd = false },
            onPicked = { t ->
                pickFastEnd = false
                onPlanChanged(normalized.withFastWindow(normalized.fastStartMinutes(), t.hour * 60 + t.minute))
            }
        )
    }
}

@Composable
private fun FastingStartCard(onStart: (hours: Int, startedAtEpochSeconds: Long) -> Unit) {
    var hours by rememberSaveable { mutableStateOf(24) }
    var startedEarlier by rememberSaveable { mutableStateOf(false) }
    var startedAt by rememberSaveable { mutableStateOf(fastingNowEpochSeconds() - 3600L) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restaurant, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    text = "Start a fast",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Pick a target. ERV keeps the timer running in the background and reminds you when the fast ends.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FastingTargetPicker(hours = hours, onHoursChange = { hours = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !startedEarlier, onClick = { startedEarlier = false }, label = { Text("Starting Now") })
                FilterChip(selected = startedEarlier, onClick = { startedEarlier = true }, label = { Text("Started Earlier") })
            }
            if (startedEarlier) {
                PastMomentButtons(label = "Started", epochSeconds = startedAt, onChange = { startedAt = it })
            }
            Button(
                onClick = { onStart(hours, if (startedEarlier) startedAt else fastingNowEpochSeconds()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start ${formatTargetHours(hours)} fast")
            }
        }
    }
}

@Composable
private fun ActiveFastingCard(
    session: FastingSession,
    nowEpochSeconds: Long,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
) {
    val targetReached = nowEpochSeconds >= session.targetEndEpochSeconds
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    text = if (targetReached) "Target reached" else "Fast in progress",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit fast")
                }
            }
            Text(
                text = formatFastingDuration(session.elapsedSeconds(nowEpochSeconds)),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(
                progress = { session.progress(nowEpochSeconds) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FastingStatChip("Target", formatTargetHours(session.targetHoursTotal()))
                FastingStatChip("Remaining", formatFastingDuration(session.remainingSeconds(nowEpochSeconds)))
            }
            Text(
                text = "Started ${formatFastingDateTime(session.startedAtEpochSeconds)}\nEnds ${formatFastingDateTime(session.targetEndEpochSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text(if (targetReached) "Complete" else "Complete early")
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun FastingStatChip(label: String, value: String) {
    AssistChip(
        onClick = {},
        label = { Text("$label: $value") },
    )
}

@Composable
private fun FastingSafetyCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Helpful reminders",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Hydrate, consider electrolytes, and stop if you feel unwell. Longer fasts may not be appropriate for everyone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FastingRecentHistoryCard(
    history: List<FastingSession>,
    onOpenLog: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recent fasts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onOpenLog) {
                    Text("Open log")
                }
            }
            if (history.isEmpty()) {
                Text(
                    text = "Completed and cancelled fasts will appear in the log.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                history.forEach { session ->
                    FastingHistoryRow(session = session)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastingLogScreen(
    repository: FastingRepository,
    onBack: () -> Unit,
    initialDate: LocalDate? = null,
    openCalendarInitially: Boolean = false,
    relayPool: RelayPool? = null,
    signer: EventSigner? = null,
) {
    var dateFilter by remember(initialDate) {
        mutableStateOf(
            if (initialDate != null) SectionLogDateFilter.SingleDay(initialDate)
            else SectionLogDateFilter.AllHistory
        )
    }
    var showCalendar by remember(openCalendarInitially) { mutableStateOf(openCalendarInitially) }
    val state by repository.state.collectAsState(initial = FastingLibraryState())
    val datedEntries = remember(state, dateFilter) {
        state.datedFastingSessionsForSectionLog(dateFilter)
    }
    val showLogDateOnCards = dateFilter !is SectionLogDateFilter.SingleDay
    val datesWithActivity = remember(state) { datesWithFastingActivity(state) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val keyManager = LocalKeyManager.current
    var editing by remember { mutableStateOf<FastingSession?>(null) }
    var addingPast by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<FastingSession?>(null) }

    suspend fun publish(dates: Collection<LocalDate>) {
        publishFastingChange(appContext, relayPool, signer, repository, dates, keyManager.relayUrlsForKind30078Publish())
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addingPast = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Log a past fast") }
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("Fasting Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = ervTopAppBarColors(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            SectionLogFilterBar(
                filter = dateFilter,
                onOpenCalendar = { showCalendar = true },
                onClearFilter = { dateFilter = SectionLogDateFilter.AllHistory },
            )
            if (datedEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when (dateFilter) {
                                SectionLogDateFilter.AllHistory -> "No fasting logged yet."
                                is SectionLogDateFilter.SingleDay -> "No fasts logged for this date."
                                is SectionLogDateFilter.DateRange -> "No fasts logged in this date range."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Finish a fast, turn on automatic logging, or use Log a past fast.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text = "Newest first, dated by when each fast ended. Tap a fast to edit or delete it.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(datedEntries, key = { it.session.id }) { entry ->
                        FastingHistoryRow(
                            session = entry.session,
                            showDateHeader = showLogDateOnCards,
                            date = entry.date,
                            onClick = { editing = entry.session },
                        )
                    }
                }
            }
        }
    }

    if (addingPast || editing != null) {
        val target = editing
        FastingSessionDialog(
            editing = target,
            onDismiss = {
                addingPast = false
                editing = null
            },
            onSave = { input ->
                addingPast = false
                editing = null
                scope.launch {
                    val hours = (((input.endedAtEpochSeconds - input.startedAtEpochSeconds) + 1800) / 3600).toInt().coerceAtLeast(1)
                    val session = target?.copy(
                        startedAtEpochSeconds = input.startedAtEpochSeconds,
                        endedAtEpochSeconds = input.endedAtEpochSeconds,
                        mood = input.mood,
                        weight = input.weight,
                        notes = input.notes,
                    ) ?: FastingSession(
                        targetDays = targetDaysForHours(hours),
                        targetHours = hours,
                        startedAtEpochSeconds = input.startedAtEpochSeconds,
                        targetEndEpochSeconds = input.endedAtEpochSeconds,
                        status = FastingStatus.COMPLETED,
                        endedAtEpochSeconds = input.endedAtEpochSeconds,
                        mood = input.mood,
                        weight = input.weight,
                        notes = input.notes,
                    )
                    repository.saveHistorySession(session)
                    publish(listOfNotNull(target?.logDate(), session.logDate()))
                    snackbarHostState.showSnackbar(if (target == null) "Fast logged" else "Fast updated")
                }
            },
            onDelete = target?.let { t ->
                {
                    editing = null
                    pendingDelete = t
                }
            }
        )
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this fast?") },
            text = { Text("It is removed from your log on this phone and from your relays.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        repository.deleteHistorySession(session.id)
                        publish(listOf(session.logDate()))
                        snackbarHostState.showSnackbar("Fast deleted")
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    if (showCalendar) {
        SectionLogCalendarSheet(
            filter = dateFilter,
            onDismiss = { showCalendar = false },
            datesWithActivity = datesWithActivity,
            onApplyFilter = {
                dateFilter = it
                showCalendar = false
            },
        )
    }
}

@Composable
private fun FastingHistoryRow(
    session: FastingSession,
    showDateHeader: Boolean = false,
    date: LocalDate = session.logDate(),
    onClick: (() -> Unit)? = null,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showDateHeader) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = session.titleLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = session.targetLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${formatFastingDuration(session.elapsedSeconds())} · ${formatFastingDateTime(session.startedAtEpochSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            session.mood?.let {
                Text(
                    text = "Mood: ${it.displayName()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (session.weight.isNotBlank()) {
                Text("Weight: ${session.weight}", style = MaterialTheme.typography.bodySmall)
            }
            if (session.notes.isNotBlank()) {
                Text(
                    text = session.notes,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun minuteOfDayToTime(minuteOfDay: Int): LocalTime =
    LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), minuteOfDay.floorMod(60))

private fun formatMinuteOfDay(minuteOfDay: Int): String =
    minuteOfDayToTime(minuteOfDay).format(DateTimeFormatter.ofPattern("h:mm a"))

private fun FastingSession.titleLabel(): String =
    when (kind) {
        FastingSessionKind.INTERMITTENT -> when (status) {
            FastingStatus.COMPLETED -> "Daily fast"
            FastingStatus.CANCELLED -> "Cancelled daily fast"
            FastingStatus.ACTIVE -> "Active daily fast"
        }
        FastingSessionKind.EXTENDED -> when (status) {
            FastingStatus.COMPLETED -> "Completed fast"
            FastingStatus.CANCELLED -> "Cancelled fast"
            FastingStatus.ACTIVE -> "Active fast"
        }
    }

private fun FastingSession.targetLabel(): String =
    if (kind == FastingSessionKind.INTERMITTENT) {
        protocolLabel.ifBlank { "${fastingHours}:${eatingHours}" }
    } else {
        "${formatTargetHours(targetHoursTotal())} target"
    }

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
