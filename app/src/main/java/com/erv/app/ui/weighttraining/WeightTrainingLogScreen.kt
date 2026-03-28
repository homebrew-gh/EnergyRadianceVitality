package com.erv.app.ui.weighttraining

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.LibraryStateMerge
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPayloadDigestStore
import com.erv.app.nostr.RelayPool
import com.erv.app.R
import com.erv.app.SectionLogDateFilter
import com.erv.app.ui.dashboard.SectionLogCalendarSheet
import com.erv.app.ui.dashboard.SectionLogFilterBar
import com.erv.app.ui.dashboard.datesWithWeightActivity
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedGlow
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedGlow
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightSync
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.datedWeightWorkoutsForSectionLog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private sealed class WeightLogEditorState {
    data object Hidden : WeightLogEditorState()
    data object NewWorkout : WeightLogEditorState()
    data class Editing(val logDate: LocalDate, val session: WeightWorkoutSession) : WeightLogEditorState()
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
    var dateFilter by remember(initialSelectedDate) {
        mutableStateOf<SectionLogDateFilter>(
            if (initialSelectedDate != null) SectionLogDateFilter.SingleDay(initialSelectedDate)
            else SectionLogDateFilter.AllHistory
        )
    }
    var showCal by remember(openCalendarInitially) {
        mutableStateOf(openCalendarInitially)
    }
    var manualLogEditor by remember { mutableStateOf<WeightLogEditorState>(WeightLogEditorState.Hidden) }
    var selectedWorkoutForSummary by remember { mutableStateOf<Pair<LocalDate, WeightWorkoutSession>?>(null) }
    var workoutPendingDelete by remember { mutableStateOf<Pair<LocalDate, WeightWorkoutSession>?>(null) }
    var nostrWeightShare by remember { mutableStateOf<Pair<LocalDate, WeightWorkoutSession>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val darkTheme = isSystemInDarkTheme()
    val headerDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark
    val headerGlow = if (darkTheme) ErvDarkTherapyRedGlow else ErvLightTherapyRedGlow
    val headerMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val keyManager = LocalKeyManager.current
    val appContext = LocalContext.current.applicationContext

    suspend fun pushDayLog(date: LocalDate) {
        if (relayPool == null || signer == null) return
        val log = repository.currentState().logFor(date) ?: return
        WeightSync.publishDayLog(appContext, relayPool, signer, log, keyManager.relayUrlsForKind30078Publish())
    }

    suspend fun pushMasters() {
        if (relayPool == null || signer == null) return
        val urls = keyManager.relayUrlsForKind30078Publish()
        val current = repository.currentState()
        WeightSync.publishExercises(appContext, relayPool, signer, current.exercises, urls)
        WeightSync.publishRoutines(appContext, relayPool, signer, current.routines, urls)
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
                        val target = LocalDate.now()
                        repository.addWorkout(target, session)
                        pushDayLog(target)
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
                        repository.updateWorkout(logEditor.logDate, session)
                        pushDayLog(logEditor.logDate)
                        manualLogEditor = WeightLogEditorState.Hidden
                        snackbarHostState.showSnackbar("Workout updated")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            return
        }
    }

    selectedWorkoutForSummary?.let { (summaryDate, summarySession) ->
        WeightWorkoutSummaryFullScreen(
            session = summarySession,
            logDate = summaryDate,
            library = state,
            loadUnit = loadUnit,
            userPreferences = userPreferences,
            dark = headerDark,
            mid = headerMid,
            glow = headerGlow,
            relayPool = relayPool,
            signer = signer,
            repository = repository,
            onAfterRoutineSync = { scope.launch { pushMasters() } },
            onRemoveFromLog = {
                scope.launch {
                    repository.deleteWorkout(summaryDate, summarySession.id)
                    pushDayLog(summaryDate)
                    selectedWorkoutForSummary = null
                    snackbarHostState.showSnackbar("Workout removed")
                }
            },
            onDone = { selectedWorkoutForSummary = null }
        )
        return
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
            SectionLogFilterBar(
                filter = dateFilter,
                onOpenCalendar = { showCal = true },
                onClearFilter = { dateFilter = SectionLogDateFilter.AllHistory }
            )
            val datedWorkouts = remember(state, dateFilter) {
                state.datedWeightWorkoutsForSectionLog(dateFilter)
            }
            val showLogDateOnCards = dateFilter !is SectionLogDateFilter.SingleDay
            WeightLogTabContent(
                datedWorkouts = datedWorkouts,
                showLogDateOnCards = showLogDateOnCards,
                emptyRangeLabel = when (val f = dateFilter) {
                    SectionLogDateFilter.AllHistory -> "No workouts logged yet."
                    is SectionLogDateFilter.SingleDay ->
                        "No workouts for ${f.day.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
                    is SectionLogDateFilter.DateRange -> "No workouts in this date range."
                },
                library = state,
                loadUnit = loadUnit,
                onOpen = { logDate, w -> selectedWorkoutForSummary = logDate to w },
                onEdit = { logDate, w -> manualLogEditor = WeightLogEditorState.Editing(logDate, w) },
                onDelete = { logDate, w -> workoutPendingDelete = logDate to w },
                onShare = { logDate, session ->
                    if (relayPool != null && signer != null) {
                        nostrWeightShare = logDate to session
                    } else {
                        scope.launch {
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

    nostrWeightShare?.let { (shareDate, shareSession) ->
        var extraHashtags by remember(shareDate, shareSession.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { nostrWeightShare = null },
            title = { Text(stringResource(R.string.workout_share_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = extraHashtags,
                    onValueChange = { extraHashtags = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.workout_share_extra_hashtags_label)) },
                    placeholder = { Text(stringResource(R.string.workout_share_extra_hashtags_placeholder)) },
                    supportingText = {
                        Text(
                            stringResource(R.string.workout_share_extra_hashtags_helper),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    minLines = 2,
                    maxLines = 4
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val d = shareDate
                        val s = shareSession
                        val input = extraHashtags
                        nostrWeightShare = null
                        scope.launch {
                            if (relayPool != null && signer != null) {
                                val ok = publishWeightWorkoutNote(
                                    relayPool,
                                    signer,
                                    s,
                                    state,
                                    d,
                                    loadUnit,
                                    input
                                )
                                snackbarHostState.showSnackbar(
                                    if (ok) "Shared to your relays!" else "Failed to share — check relay connection"
                                )
                            }
                        }
                    }
                ) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = { nostrWeightShare = null }) { Text("Cancel") }
            }
        )
    }

    workoutPendingDelete?.let { (deleteDate, w) ->
        AlertDialog(
            onDismissRequest = { workoutPendingDelete = null },
            title = { Text("Delete workout?") },
            text = {
                Text(
                    "Remove this session from ${deleteDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteWorkout(deleteDate, w.id)
                            pushDayLog(deleteDate)
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
        SectionLogCalendarSheet(
            filter = dateFilter,
            onDismiss = { showCal = false },
            datesWithActivity = datesWithActivity,
            onApplyFilter = { dateFilter = it }
        )
    }
}
