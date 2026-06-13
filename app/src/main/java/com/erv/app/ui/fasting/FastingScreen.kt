package com.erv.app.ui.fasting

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.erv.app.SectionLogDateFilter
import com.erv.app.fasting.FastingCompletionScheduler
import com.erv.app.fasting.FastingForegroundService
import com.erv.app.fasting.FastingLibraryState
import com.erv.app.fasting.FastingMood
import com.erv.app.fasting.FastingRepository
import com.erv.app.fasting.FastingSession
import com.erv.app.fasting.FastingSessionKind
import com.erv.app.fasting.FastingStatus
import com.erv.app.fasting.IntermittentFastingPhase
import com.erv.app.fasting.IntermittentFastingPlan
import com.erv.app.fasting.IntermittentFastingStatus
import com.erv.app.fasting.datedFastingSessionsForSectionLog
import com.erv.app.fasting.datesWithFastingActivity
import com.erv.app.fasting.displayName
import com.erv.app.fasting.elapsedSeconds
import com.erv.app.fasting.fastingNowEpochSeconds
import com.erv.app.fasting.formatFastingDateTime
import com.erv.app.fasting.formatFastingDuration
import com.erv.app.fasting.logDate
import com.erv.app.fasting.currentStatus
import com.erv.app.fasting.eatingEndMinutes
import com.erv.app.fasting.normalized
import com.erv.app.fasting.progress
import com.erv.app.fasting.remainingSeconds
import com.erv.app.ui.dashboard.SectionLogCalendarSheet
import com.erv.app.ui.dashboard.SectionLogFilterBar
import com.erv.app.ui.theme.ErvHeaderRed
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class FastingMode {
    INTERMITTENT,
    EXTENDED,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastingScreen(
    repository: FastingRepository,
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by repository.state.collectAsState(initial = FastingLibraryState())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var nowEpochSeconds by remember { mutableLongStateOf(fastingNowEpochSeconds()) }
    var completionSession by remember { mutableStateOf<FastingSession?>(null) }
    var cancelSession by remember { mutableStateOf<FastingSession?>(null) }
    var selectedMode by remember { mutableStateOf(FastingMode.INTERMITTENT) }

    LaunchedEffect(Unit) {
        while (true) {
            nowEpochSeconds = fastingNowEpochSeconds()
            delay(1_000L)
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ErvHeaderRed,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
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
                    selectedMode = selectedMode,
                    onSelected = { selectedMode = it },
                )
            }
            item {
                val active = state.activeSession
                when (selectedMode) {
                    FastingMode.INTERMITTENT -> {
                        IntermittentFastingCard(
                            plan = state.intermittentPlan,
                            nowEpochSeconds = nowEpochSeconds,
                            onPlanChanged = { plan ->
                                scope.launch {
                                    repository.setIntermittentPlan(plan)
                                    snackbarHostState.showSnackbar("${plan.protocolLabel} plan saved")
                                }
                            },
                            onLogCompletedWindow = { plan, status ->
                                scope.launch {
                                    val logged = repository.logCompletedIntermittentWindow(plan, status, nowEpochSeconds)
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
                                onStart = { days ->
                                    scope.launch {
                                        val session = repository.startFast(days)
                                        FastingForegroundService.start(context, session)
                                        val scheduled = FastingCompletionScheduler.schedule(context, session)
                                        snackbarHostState.showSnackbar(
                                            if (scheduled) {
                                                "${days}-day fast started"
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
                            )
                        }
                    }
                }
            }
            if (selectedMode == FastingMode.EXTENDED) {
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

    completionSession?.let { session ->
        FastingCompletionDialog(
            session = session,
            onDismiss = { completionSession = null },
            onSave = { mood, weight, notes ->
                scope.launch {
                    repository.completeActive(mood, weight, notes)
                    FastingCompletionScheduler.cancel(context, session.id)
                    FastingForegroundService.stop(context)
                    completionSession = null
                    snackbarHostState.showSnackbar("Fast saved")
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
                            repository.cancelActive()
                            FastingCompletionScheduler.cancel(context, session.id)
                            FastingForegroundService.stop(context)
                            cancelSession = null
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
            label = { Text("Intermittent") },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = selectedMode == FastingMode.EXTENDED,
            onClick = { onSelected(FastingMode.EXTENDED) },
            label = { Text("1-3 day fast") },
            modifier = Modifier.weight(1f),
        )
    }
}

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
    val protocols = listOf(
        IntermittentFastingPlan("14:10", fastingHours = 14, eatingHours = 10, eatingStartMinutes = normalized.eatingStartMinutes),
        IntermittentFastingPlan("16:8", fastingHours = 16, eatingHours = 8, eatingStartMinutes = normalized.eatingStartMinutes),
        IntermittentFastingPlan("18:6", fastingHours = 18, eatingHours = 6, eatingStartMinutes = normalized.eatingStartMinutes),
        IntermittentFastingPlan("20:4", fastingHours = 20, eatingHours = 4, eatingStartMinutes = normalized.eatingStartMinutes),
        IntermittentFastingPlan("OMAD", fastingHours = 23, eatingHours = 1, eatingStartMinutes = normalized.eatingStartMinutes),
    )
    val startOptions = listOf(8 * 60, 10 * 60, 12 * 60, 14 * 60, 16 * 60, 18 * 60)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    text = "Intermittent fasting",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Pick a daily fasting/eating rhythm. ERV derives the current phase from your schedule.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Protocol", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                protocols.take(3).forEach { option ->
                    FilterChip(
                        selected = normalized.protocolLabel == option.protocolLabel,
                        onClick = { onPlanChanged(option) },
                        label = { Text(option.protocolLabel) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                protocols.drop(3).forEach { option ->
                    FilterChip(
                        selected = normalized.protocolLabel == option.protocolLabel,
                        onClick = { onPlanChanged(option) },
                        label = { Text(option.protocolLabel) },
                    )
                }
            }
            Text("Eating window starts", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                startOptions.take(3).forEach { minute ->
                    FilterChip(
                        selected = normalized.eatingStartMinutes == minute,
                        onClick = { onPlanChanged(normalized.copy(eatingStartMinutes = minute)) },
                        label = { Text(formatMinuteOfDay(minute)) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                startOptions.drop(3).forEach { minute ->
                    FilterChip(
                        selected = normalized.eatingStartMinutes == minute,
                        onClick = { onPlanChanged(normalized.copy(eatingStartMinutes = minute)) },
                        label = { Text(formatMinuteOfDay(minute)) },
                    )
                }
            }
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
                    Text(
                        text = "Eating window: ${formatMinuteOfDay(normalized.eatingStartMinutes)} - ${formatMinuteOfDay(normalized.eatingEndMinutes())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (status.phase == IntermittentFastingPhase.EATING && status.completedFastStartEpochSeconds != null) {
                Button(
                    onClick = { onLogCompletedWindow(normalized, status) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Log completed ${normalized.protocolLabel} fast")
                }
            } else {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Completed windows can be logged during eating time")
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Fasting Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ErvHeaderRed,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
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
                            text = "Complete or cancel a fast to create a log entry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text = "Newest first. Entries are dated by completion or cancellation time.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(datedEntries, key = { it.session.id }) { entry ->
                        FastingHistoryRow(
                            session = entry.session,
                            showDateHeader = showLogDateOnCards,
                            date = entry.date,
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
            },
        )
    }
}

@Composable
private fun FastingStartCard(onStart: (Int) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                text = "Choose a simple target. ERV keeps the timer running in the background and reminds you when the fast ends.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(1, 2, 3).forEach { days ->
                    Button(
                        onClick = { onStart(days) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("${days}d")
                    }
                }
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
                )
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
                FastingStatChip("Target", "${session.targetDays} days")
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

@Composable
private fun FastingCompletionDialog(
    session: FastingSession,
    onDismiss: () -> Unit,
    onSave: (FastingMood?, String, String) -> Unit,
) {
    var mood by remember { mutableStateOf<FastingMood?>(null) }
    var weight by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete fast") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Duration: ${formatFastingDuration(session.elapsedSeconds())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("How did it feel?", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FastingMood.entries.forEach { option ->
                        FilterChip(
                            selected = mood == option,
                            onClick = { mood = if (mood == option) null else option },
                            label = { Text(option.displayName()) },
                        )
                    }
                }
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(mood, weight, notes) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not yet")
            }
        },
    )
}

@Composable
private fun FastingHistoryRow(
    session: FastingSession,
    showDateHeader: Boolean = false,
    date: LocalDate = session.logDate(),
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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

private fun formatMinuteOfDay(minuteOfDay: Int): String {
    val time = java.time.LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), minuteOfDay.floorMod(60))
    return time.format(DateTimeFormatter.ofPattern("h:mm a"))
}

private fun FastingSession.titleLabel(): String =
    when (kind) {
        FastingSessionKind.INTERMITTENT -> when (status) {
            FastingStatus.COMPLETED -> "Completed intermittent fast"
            FastingStatus.CANCELLED -> "Cancelled intermittent fast"
            FastingStatus.ACTIVE -> "Active intermittent fast"
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
        "${targetDays}d target"
    }

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
