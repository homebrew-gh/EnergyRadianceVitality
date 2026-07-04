package com.erv.app.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.theme.ErvHeaderRed
import com.erv.app.workouts.Workout
import com.erv.app.workouts.WorkoutLibraryState
import com.erv.app.workouts.WorkoutRepository
import com.erv.app.workouts.WorkoutSegmentKind
import com.erv.app.workouts.WorkoutSync
import com.erv.app.workouts.duplicateCopy
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLibraryScreen(
    state: WorkoutLibraryState,
    repository: WorkoutRepository,
    keyManager: KeyManager,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onPullFromRelay: suspend () -> Unit = {},
    onBack: () -> Unit,
    onOpenComposer: (String?) -> Unit,
    onOpenRun: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<Workout?>(null) }
    var syncing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        syncing = true
        try {
            onPullFromRelay()
        } finally {
            syncing = false
        }
    }

    suspend fun publishLibrary() {
        WorkoutSync.publishLibraryIfSignedIn(
            appContext = context.applicationContext,
            relayPool = relayPool,
            signer = signer,
            state = repository.currentState(),
            dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
        )
    }

    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete workout?") },
            text = { Text("“${deleting!!.name}” will be removed from your library.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = deleting!!.id
                    deleting = null
                    scope.launch {
                        repository.deleteWorkout(id)
                        publishLibrary()
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenComposer(null) }) {
                Icon(Icons.Default.Add, contentDescription = "New workout")
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Workout Library") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                syncing = true
                                try {
                                    onPullFromRelay()
                                } finally {
                                    syncing = false
                                }
                            }
                        },
                        enabled = !syncing && signer != null && relayPool != null,
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync from relay")
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
        WorkoutLibraryListContent(
            state = state,
            onRun = onOpenRun,
            onEdit = onOpenComposer,
            onDuplicate = { workout ->
                scope.launch {
                    repository.upsertWorkout(workout.duplicateCopy())
                    publishLibrary()
                }
            },
            onDelete = { deleting = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
fun WorkoutLibraryListContent(
    state: WorkoutLibraryState,
    onRun: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (Workout) -> Unit,
    onDelete: (Workout) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "Storyboard workouts with segments (straight sets, circuits, and more).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.workouts.isEmpty()) {
            item {
                Text(
                    text = "No workouts synced yet. Workouts published from the Start9 builder " +
                        "appear here after a relay sync — tap Sync above or reopen the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(state.workouts, key = { it.id }) { workout ->
                WorkoutLibraryRow(
                    workout = workout,
                    onRun = { onRun(workout.id) },
                    onEdit = { onEdit(workout.id) },
                    onDuplicate = { onDuplicate(workout) },
                    onDelete = { onDelete(workout) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutLibraryRow(
    workout: Workout,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = workout.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${workout.segments.size} segment(s) · ${workout.segments.joinToString { it.kind.name.replace('_', ' ').lowercase() }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(onClick = onRun) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("Run", maxLines = 1, softWrap = false)
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text("Edit", maxLines = 1, softWrap = false)
                }
                TextButton(onClick = onDuplicate) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Text("Duplicate", maxLines = 1, softWrap = false)
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("Delete", maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

fun defaultWorkoutSegmentKindLabel(kind: WorkoutSegmentKind): String = when (kind) {
    WorkoutSegmentKind.STRAIGHT_SETS -> "Straight sets"
    WorkoutSegmentKind.CIRCUIT -> "Circuit"
    WorkoutSegmentKind.SUPERSET -> "Superset"
    WorkoutSegmentKind.COMPOSITE -> "Flow block"
    WorkoutSegmentKind.CARDIO -> "Cardio"
    WorkoutSegmentKind.INTERVAL -> "Intervals"
    else -> kind.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
}
