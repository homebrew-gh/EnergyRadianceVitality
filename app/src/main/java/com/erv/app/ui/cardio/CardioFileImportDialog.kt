package com.erv.app.ui.cardio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioFileImport
import com.erv.app.cardio.CardioFileParse
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioRepository
import com.erv.app.cardio.formatCardioDistanceFromMeters
import com.erv.app.cardio.formatCardioElapsedClock
import com.erv.app.cardio.resolveSnapshot
import com.erv.app.data.UserPreferences
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The activities offered for an imported file, most likely first. */
private val ImportActivities = listOf(
    CardioBuiltinActivity.BIKE,
    CardioBuiltinActivity.STATIONARY_BIKE,
    CardioBuiltinActivity.RUN,
    CardioBuiltinActivity.WALK,
    CardioBuiltinActivity.RUCK,
    CardioBuiltinActivity.HIKE,
    CardioBuiltinActivity.ROWING,
    CardioBuiltinActivity.SWIM,
    CardioBuiltinActivity.ELLIPTICAL,
)

/** Files bigger than this aren't a single workout. */
private const val MAX_IMPORT_BYTES = 40L * 1024 * 1024

private fun readImportFile(context: Context, uri: Uri): Pair<ByteArray, String?> {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            require(total <= MAX_IMPORT_BYTES) { "This file is too big to be one workout." }
            out.write(buffer, 0, n)
        }
        out.toByteArray()
    } ?: error("Couldn't open this file.")
    return bytes to name
}

/**
 * Import one GPX, TCX or FIT file: read it, show what's in it, pick the activity, and save it to
 * the cardio log. [onDone] gets a line for a snackbar, or null when cancelled.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardioFileImportDialog(
    uri: Uri,
    repository: CardioRepository,
    userPreferences: UserPreferences,
    onDone: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState(initial = CardioLibraryState())
    val distanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    var parse by remember(uri) { mutableStateOf<CardioFileParse?>(null) }
    var picked by remember(uri) { mutableStateOf<CardioBuiltinActivity?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        parse = withContext(Dispatchers.IO) {
            try {
                val (bytes, name) = readImportFile(context, uri)
                CardioFileImport.parse(bytes, name)
            } catch (e: Exception) {
                CardioFileParse.Failed(e.message ?: "Couldn't read this file.")
            }
        }
        (parse as? CardioFileParse.Parsed)?.let { picked = CardioFileImport.guessActivity(it.activity) }
    }

    when (val result = parse) {
        null -> AlertDialog(
            onDismissRequest = { onDone(null) },
            title = { Text("Import Workout") },
            text = { CircularProgressIndicator() },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
        )
        is CardioFileParse.Failed -> AlertDialog(
            onDismissRequest = { onDone(null) },
            title = { Text("Import Workout") },
            text = { Text(result.message) },
            confirmButton = { TextButton(onClick = { onDone(null) }) { Text("OK") } },
        )
        is CardioFileParse.Parsed -> {
            val activity = result.activity
            val duplicate = remember(activity, state) { CardioFileImport.findDuplicate(state, activity) }
            val started = Instant.ofEpochSecond(activity.startEpochSeconds).atZone(ZoneId.systemDefault())
            AlertDialog(
                onDismissRequest = { if (!saving) onDone(null) },
                title = { Text("Import Workout") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activity.name?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            DateTimeFormatter.ofPattern("EEE MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(started),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            listOfNotNull(
                                formatCardioElapsedClock(activity.movingSeconds?.takeIf { it > 0 } ?: activity.elapsedSeconds),
                                activity.resolvedDistanceMeters?.let { formatCardioDistanceFromMeters(it, distanceUnit) },
                                activity.heartRate.takeIf { it.isNotEmpty() }
                                    ?.let { hr -> "avg ${hr.map { it.bpm }.average().toInt()} bpm" },
                                if (activity.points.size >= 2) "with route" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("Activity", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ImportActivities.forEach { builtin ->
                                FilterChip(
                                    selected = picked == builtin,
                                    onClick = { picked = builtin },
                                    label = { Text(state.resolveSnapshot(builtin, null).displayLabel) },
                                )
                            }
                        }
                        if (duplicate != null) {
                            Text(
                                "Your log already has a ${duplicate.activity.displayLabel.lowercase(Locale.getDefault())} " +
                                    "that started at this time. Importing adds a second one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = picked != null && !saving,
                        onClick = {
                            val builtin = picked
                            if (builtin != null) saving = true
                            if (builtin != null) scope.launch {
                                val snapshot = state.resolveSnapshot(builtin, null)
                                val session = CardioFileImport.toSession(activity, snapshot)
                                repository.addSession(CardioFileImport.localDate(activity), session)
                                onDone("Imported ${snapshot.displayLabel} from ${activity.format.name}")
                            }
                        },
                    ) { Text(if (duplicate != null) "Import Anyway" else "Import") }
                },
                dismissButton = {
                    TextButton(enabled = !saving, onClick = { onDone(null) }) { Text("Cancel") }
                },
            )
        }
    }
}

/** Writes the session's route as a .gpx file and opens the share sheet for it. False when there's no route. */
fun shareSessionGpx(context: Context, session: com.erv.app.cardio.CardioSession): Boolean {
    val gpx = CardioFileImport.toGpx(session) ?: return false
    return try {
        val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
        val day = session.startEpochSeconds
            ?.let { Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
            ?: "route"
        val label = session.activity.displayLabel.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val file = java.io.File(dir, "erv-$label-$day.gpx")
        file.writeText(gpx, Charsets.UTF_8)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            android.content.Intent.createChooser(send, "Export GPX").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
        true
    } catch (_: Exception) {
        false
    }
}
