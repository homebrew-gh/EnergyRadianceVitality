package com.erv.app.ui.workouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    onBack: () -> Unit,
    onOpenComposer: (String?) -> Unit,
    onOpenRun: (String) -> Unit,
    onOpenLegacyUnified: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<Workout?>(null) }

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
                title = { Text("Workout library") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ErvHeaderRed,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "Storyboard workouts with segments (straight sets, circuits, and more). " +
                        "Legacy mixed routines remain available below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.workouts.isEmpty()) {
                item {
                    Text(
                        text = "No workouts yet. Tap + to compose your first session.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(state.workouts, key = { it.id }) { workout ->
                    WorkoutLibraryRow(
                        workout = workout,
                        onRun = { onOpenRun(workout.id) },
                        onEdit = { onOpenComposer(workout.id) },
                        onDuplicate = {
                            scope.launch {
                                repository.upsertWorkout(workout.duplicateCopy())
                                publishLibrary()
                            }
                        },
                        onDelete = { deleting = workout },
                    )
                }
            }
            item {
                Text(
                    text = "Legacy",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            item {
                Text(
                    text = "Unified Workouts (pre-merge mixed routines)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenLegacyUnified)
                        .padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

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
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onRun) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("Run")
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text("Edit")
                }
                TextButton(onClick = onDuplicate) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Text("Duplicate")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("Delete")
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
