package com.erv.app.ui.weighttraining

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.dashboard.CalendarPopup
import com.erv.app.ui.dashboard.DateNavigator
import com.erv.app.ui.dashboard.datesWithWeightActivity
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightSync
import com.erv.app.weighttraining.WeightWorkoutSession
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private sealed class WeightLogEditorState {
    data object Hidden : WeightLogEditorState()
    data object NewWorkout : WeightLogEditorState()
    data class Editing(val session: WeightWorkoutSession) : WeightLogEditorState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightTrainingLogScreen(
    repository: WeightRepository,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** When set (e.g. from dashboard), log screen starts on this day. */
    initialSelectedDate: LocalDate? = null,
    /** When true, calendar opens immediately (e.g. backfill flow from dashboard). */
    openCalendarInitially: Boolean = false
) {
    val loadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.LB)
    val state by repository.state.collectAsState(initial = WeightLibraryState())
    val datesWithActivity = remember(state) { datesWithWeightActivity(state) }
    var selectedDate by remember(initialSelectedDate) {
        mutableStateOf(initialSelectedDate ?: LocalDate.now())
    }
    var showCal by remember(openCalendarInitially) {
        mutableStateOf(openCalendarInitially)
    }
    var manualLogEditor by remember { mutableStateOf<WeightLogEditorState>(WeightLogEditorState.Hidden) }
    var workoutPendingDelete by remember { mutableStateOf<WeightWorkoutSession?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val darkTheme = isSystemInDarkTheme()
    val headerMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid

    suspend fun pushDayLog(date: LocalDate) {
        if (relayPool == null || signer == null) return
        val log = repository.currentState().logFor(date) ?: return
        WeightSync.publishDayLog(relayPool, signer, log)
    }

    LaunchedEffect(relayPool, signer?.publicKey) {
        if (relayPool != null && signer != null) {
            WeightSync.fetchFromNetwork(relayPool, signer, signer.publicKey)?.let { remote ->
                repository.replaceAll(remote)
            }
        }
    }

    when (val logEditor = manualLogEditor) {
        WeightLogEditorState.Hidden -> Unit
        WeightLogEditorState.NewWorkout -> {
            WeightManualWorkoutEditorScreen(
                existingSession = null,
                library = state,
                loadUnit = loadUnit,
                onLoadUnitChange = { u -> scope.launch { userPreferences.setWeightTrainingLoadUnit(u) } },
                onDismiss = { manualLogEditor = WeightLogEditorState.Hidden },
                onSave = { session ->
                    scope.launch {
                        repository.addWorkout(selectedDate, session)
                        pushDayLog(selectedDate)
                        manualLogEditor = WeightLogEditorState.Hidden
                        snackbarHostState.showSnackbar("Workout saved")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            return
        }
        is WeightLogEditorState.Editing -> {
            WeightManualWorkoutEditorScreen(
                existingSession = logEditor.session,
                library = state,
                loadUnit = loadUnit,
                onLoadUnitChange = { u -> scope.launch { userPreferences.setWeightTrainingLoadUnit(u) } },
                onDismiss = { manualLogEditor = WeightLogEditorState.Hidden },
                onSave = { session ->
                    scope.launch {
                        repository.updateWorkout(selectedDate, session)
                        pushDayLog(selectedDate)
                        manualLogEditor = WeightLogEditorState.Hidden
                        snackbarHostState.showSnackbar("Workout updated")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            return
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Weight Training Log") },
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { manualLogEditor = WeightLogEditorState.NewWorkout },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add workout") }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            DateNavigator(
                selectedDate = selectedDate,
                onPreviousDay = { selectedDate = selectedDate.minusDays(1) },
                onNextDay = { selectedDate = selectedDate.plusDays(1) },
                onPreviousWeek = { selectedDate = selectedDate.minusWeeks(1) },
                onNextWeek = { selectedDate = selectedDate.plusWeeks(1) },
                onTodayClick = { selectedDate = LocalDate.now() },
                onCalendarClick = { showCal = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            val dayWorkouts = state.logFor(selectedDate)?.workouts.orEmpty()
            WeightLogTabContent(
                selectedDate = selectedDate,
                workouts = dayWorkouts,
                library = state,
                loadUnit = loadUnit,
                onEdit = { manualLogEditor = WeightLogEditorState.Editing(it) },
                onDelete = { workoutPendingDelete = it },
                onShare = { session ->
                    scope.launch {
                        if (relayPool != null && signer != null) {
                            val ok = publishWeightWorkoutNote(
                                relayPool,
                                signer,
                                session,
                                state,
                                selectedDate,
                                loadUnit
                            )
                            snackbarHostState.showSnackbar(
                                if (ok) "Shared to your relays!" else "Failed to share — check relay connection"
                            )
                        } else {
                            snackbarHostState.showSnackbar("Connect relays and sign in to share.")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            )
        }
    }

    workoutPendingDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { workoutPendingDelete = null },
            title = { Text("Delete workout?") },
            text = {
                Text(
                    "Remove this session from ${selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteWorkout(selectedDate, w.id)
                            pushDayLog(selectedDate)
                            snackbarHostState.showSnackbar("Workout removed")
                        }
                        workoutPendingDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { workoutPendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showCal) {
        CalendarPopup(
            selectedDate = selectedDate,
            onDateSelected = { selectedDate = it; showCal = false },
            onDismiss = { showCal = false },
            datesWithActivity = datesWithActivity
        )
    }
}
