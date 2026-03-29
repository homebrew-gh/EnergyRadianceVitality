package com.erv.app.ui.unifiedroutines

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioSession
import com.erv.app.cardio.CardioTrackShareImage
import com.erv.app.cardio.formatCardioAveragePaceForSession
import com.erv.app.cardio.formatCardioDistanceFromMeters
import com.erv.app.cardio.formatCardioElevationGainLoss
import com.erv.app.cardio.resolvedElevationMeters
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.data.WorkoutMediaUploadBackend
import com.erv.app.hr.HeartRateSessionAnalyticsSection
import com.erv.app.nostr.BlossomUploader
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.Nip96Uploader
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.UnsignedEvent
import com.erv.app.nostr.buildWorkoutShareHashtagContentLineFromTopics
import com.erv.app.nostr.parseWorkoutShareTopics
import com.erv.app.nostr.workoutShareBaseTopicTags
import com.erv.app.nostr.workoutShareKind1TopicTagsFromTopics
import com.erv.app.ui.cardio.CardioGpsTrackSummaryPreview
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedGlow
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedGlow
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.unifiedroutines.UnifiedRoutineLibraryState
import com.erv.app.unifiedroutines.UnifiedRoutineRepository
import com.erv.app.unifiedroutines.nowUnifiedRoutineEpochSeconds
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.formatSetSummaryLine
import com.erv.app.weighttraining.totalSetCount
import com.erv.app.weighttraining.totalVolumeLoadTimesReps
import com.erv.app.weighttraining.weightLoadUnitSuffix
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun UnifiedWorkoutSummaryScreen(
    sessionId: String,
    repository: UnifiedRoutineRepository,
    unifiedState: UnifiedRoutineLibraryState,
    weightState: WeightLibraryState,
    cardioState: com.erv.app.cardio.CardioLibraryState,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val summary = unifiedState.sessionById(sessionId)
    val loadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.LB)
    val distanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    val maxHrPref by userPreferences.heartRateMaxBpm.collectAsState(initial = null)
    val nip96Origin by userPreferences.nip96MediaServerOrigin.collectAsState(initial = "")
    val blossomPublicOrigin by userPreferences.blossomPublicServerOrigin.collectAsState(initial = "")
    val workoutMediaBackend by userPreferences.workoutMediaUploadBackend.collectAsState(
        initial = WorkoutMediaUploadBackend.NIP96
    )
    val attachRouteImage by userPreferences.attachRouteImageToWorkoutNostrShare.collectAsState(initial = true)
    var sharing by remember { mutableStateOf(false) }
    var shared by remember { mutableStateOf(false) }
    var sharePersonalMessage by remember { mutableStateOf("") }
    var shareHashtags by remember {
        mutableStateOf(buildWorkoutShareHashtagContentLineFromTopics(workoutShareBaseTopicTags))
    }
    var showSaveRoutineDialog by remember { mutableStateOf(false) }

    val darkTheme = isSystemInDarkTheme()
    val dark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark
    val mid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val glow = if (darkTheme) ErvDarkTherapyRedGlow else ErvLightTherapyRedGlow

    if (summary == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Unified workout summary not found.")
        }
        return
    }

    var saveRoutineName by remember(summary.id) {
        mutableStateOf(
            when {
                summary.startedAsAdHoc -> summary.routineName
                else -> "${summary.routineName} Copy"
            }
        )
    }

    val weightSessions = remember(summary, weightState) {
        summary.blocks.mapNotNull { block ->
            if (block.type != com.erv.app.unifiedroutines.UnifiedRoutineBlockType.WEIGHT) return@mapNotNull null
            resolveWeightSession(weightState, block.linkedLogDate, block.linkedEntryId)
        }
    }
    val cardioSessions = remember(summary, cardioState) {
        summary.blocks.mapNotNull { block ->
            if (block.type != com.erv.app.unifiedroutines.UnifiedRoutineBlockType.CARDIO) return@mapNotNull null
            resolveCardioSession(cardioState, block.linkedLogDate, block.linkedEntryId)
        }
    }
    val totalCardioKcal = remember(cardioSessions) { cardioSessions.sumOf { it.estimatedKcal ?: 0.0 } }
    val totalWeightKcal = remember(weightSessions) { weightSessions.sumOf { it.estimatedKcal ?: 0.0 } }
    val totalEstimatedKcal = remember(totalCardioKcal, totalWeightKcal) {
        (totalCardioKcal + totalWeightKcal).takeIf { it > 0.5 }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(dark, mid, glow)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Workout logged", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(summary.routineName, style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.96f))
            Text(
                "${summary.displayRef} · ${formatUnifiedDate(summary.startedAtEpochSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f)
            )
            formatUnifiedElapsed(summary.startedAtEpochSeconds, summary.finishedAtEpochSeconds)?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f))
            }
            totalEstimatedKcal?.let { kcal ->
                Text(
                    "Est. calories: ~${kcal.toInt()} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            summary.heartRate?.let { hr ->
                HeartRateSessionAnalyticsSection(
                    heartRate = hr,
                    userMaxHrBpm = maxHrPref,
                    useLightOnDarkBackground = true
                )
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.35f))
            if (cardioSessions.isNotEmpty()) {
                Text("Cardio", style = MaterialTheme.typography.titleLarge, color = Color.White)
                val totalDistanceMeters = cardioSessions.sumOf { it.distanceMeters ?: 0.0 }
                val totalCardioMinutes = cardioSessions.sumOf { it.durationMinutes }
                Text(
                    buildString {
                        append("$totalCardioMinutes min")
                        if (totalDistanceMeters > 1.0) {
                            append(" • ${formatCardioDistanceFromMeters(totalDistanceMeters, distanceUnit)}")
                        }
                        if (totalCardioKcal > 0.5) {
                            append(" • ~${totalCardioKcal.toInt()} kcal")
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.86f)
                )
                cardioSessions.forEach { session ->
                    Text(session.activity.displayLabel, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.92f))
                    formatCardioAveragePaceForSession(session, distanceUnit, null)?.let { pace ->
                        Text("Avg pace: $pace", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f))
                    }
                    session.resolvedElevationMeters()?.let { (gain, loss) ->
                        Text(
                            formatCardioElevationGainLoss(gain, loss, distanceUnit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.82f)
                        )
                    }
                    session.gpsTrack?.points?.takeIf { it.isNotEmpty() }?.let { pts ->
                        CardioGpsTrackSummaryPreview(points = pts)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.28f))
            }
            if (weightSessions.isNotEmpty()) {
                val totalSets = weightSessions.sumOf { it.totalSetCount() }
                val totalVolume = weightSessions.sumOf { it.totalVolumeLoadTimesReps(loadUnit) }
                Text("Weight", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    buildString {
                        append("${weightSessions.sumOf { it.entries.size }} exercises • $totalSets sets")
                        if (totalVolume > 0.5) {
                            append(" • Volume ~${totalVolume.toInt()} ${weightLoadUnitSuffix(loadUnit)}×reps")
                        }
                        if (totalWeightKcal > 0.5) {
                            append(" • ~${totalWeightKcal.toInt()} kcal")
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.86f)
                )
                weightSessions.forEach { session ->
                    session.entries.forEach { entry ->
                        val exercise = weightState.exerciseById(entry.exerciseId)
                        Text(
                            exercise?.name ?: entry.exerciseId,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                        entry.sets.forEachIndexed { index, set ->
                            Text(
                                formatSetSummaryLine(
                                    set = set,
                                    setNumber = index + 1,
                                    loadUnit = loadUnit,
                                    loadSuffix = weightLoadUnitSuffix(loadUnit),
                                    weightIsAddedLoad = exercise?.equipment == com.erv.app.weighttraining.WeightEquipment.OTHER
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        }
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.28f))
            }
            if (relayPool != null && signer != null) {
                OutlinedTextField(
                    value = sharePersonalMessage,
                    onValueChange = { sharePersonalMessage = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sharing && !shared,
                    label = { Text("Personal message") },
                    minLines = 2,
                    maxLines = 5,
                    colors = summaryTextFieldColors()
                )
                OutlinedTextField(
                    value = shareHashtags,
                    onValueChange = { shareHashtags = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sharing && !shared,
                    label = { Text("Hashtags") },
                    minLines = 2,
                    maxLines = 4,
                    colors = summaryTextFieldColors()
                )
                OutlinedButton(
                    onClick = {
                        if (sharing || shared) return@OutlinedButton
                        sharing = true
                        scope.launch {
                            val routeSource = cardioSessions.firstOrNull { it.routeImageUrl != null || it.gpsTrack?.points?.isNotEmpty() == true }
                            val routeImageUrl = uploadUnifiedRouteImageIfNeeded(
                                context = context,
                                source = routeSource,
                                nip96OriginRaw = nip96Origin,
                                blossomPublicOriginRaw = blossomPublicOrigin,
                                mediaBackend = workoutMediaBackend,
                                attachRouteImage = attachRouteImage,
                                dark = dark,
                                mid = mid,
                                glow = glow,
                                signer = signer
                            )
                            val topics = parseWorkoutShareTopics(shareHashtags)
                            val tags = workoutShareKind1TopicTagsFromTopics(topics).toMutableList()
                            if (routeImageUrl != null) {
                                tags.add(listOf("imeta", "url $routeImageUrl", "m image/png", "dim 1080x1440"))
                            }
                            val unsigned = UnsignedEvent(
                                pubkey = signer.publicKey,
                                createdAt = System.currentTimeMillis() / 1000,
                                kind = 1,
                                tags = tags,
                                content = buildUnifiedWorkoutNoteContent(
                                    summary = summary,
                                    cardioSessions = cardioSessions,
                                    weightSessions = weightSessions,
                                    weightState = weightState,
                                    loadUnit = loadUnit,
                                    distanceUnit = distanceUnit,
                                    personalMessage = sharePersonalMessage,
                                    topics = topics,
                                    routeImageUrl = routeImageUrl
                                )
                            )
                            val ok = relayPool.publish(signer.sign(unsigned))
                            sharing = false
                            shared = ok
                            snackbarHostState.showSnackbar(
                                if (ok) "Shared to your relays!" else "Failed to share — check relay connection"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sharing && !shared,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (sharing) "Sharing…" else if (shared) "Shared" else "Share workout")
                }
            }
            if (summary.routineSnapshot != null) {
                OutlinedButton(
                    onClick = { showSaveRoutineDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                ) {
                    Text(if (summary.startedAsAdHoc) "Save as routine" else "Save copy as routine")
                }
            }
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = mid)
            ) {
                Text("Done")
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showSaveRoutineDialog && summary.routineSnapshot != null) {
        AlertDialog(
            onDismissRequest = { showSaveRoutineDialog = false },
            title = { Text("Save as routine") },
            text = {
                OutlinedTextField(
                    value = saveRoutineName,
                    onValueChange = { saveRoutineName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Routine name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val snapshot = summary.routineSnapshot ?: return@TextButton
                        val trimmedName = saveRoutineName.trim().ifBlank { snapshot.name }
                        scope.launch {
                            repository.upsertRoutine(
                                snapshot.copy(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = trimmedName,
                                    createdAtEpochSeconds = nowUnifiedRoutineEpochSeconds(),
                                    lastModifiedEpochSeconds = nowUnifiedRoutineEpochSeconds()
                                )
                            )
                            showSaveRoutineDialog = false
                            snackbarHostState.showSnackbar("Routine saved")
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveRoutineDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun resolveWeightSession(
    weightState: WeightLibraryState,
    logDate: String?,
    entryId: String?
): WeightWorkoutSession? {
    val date = runCatching { LocalDate.parse(logDate) }.getOrNull() ?: return null
    return weightState.logFor(date)?.workouts?.firstOrNull { it.id == entryId }
}

private fun resolveCardioSession(
    cardioState: com.erv.app.cardio.CardioLibraryState,
    logDate: String?,
    entryId: String?
): CardioSession? {
    val date = runCatching { LocalDate.parse(logDate) }.getOrNull() ?: return null
    return cardioState.logFor(date)?.sessions?.firstOrNull { it.id == entryId }
}

private fun formatUnifiedDate(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)

private fun formatUnifiedElapsed(startedAt: Long, finishedAt: Long?): String? {
    val end = finishedAt ?: return null
    val elapsed = (end - startedAt).coerceAtLeast(0L)
    val minutes = elapsed / 60
    val seconds = elapsed % 60
    return "Elapsed: %d:%02d".format(minutes, seconds)
}

@Composable
private fun summaryTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = Color.White.copy(alpha = 0.6f),
    focusedLabelColor = Color.White.copy(alpha = 0.85f),
    unfocusedLabelColor = Color.White.copy(alpha = 0.72f),
    disabledLabelColor = Color.White.copy(alpha = 0.5f),
    focusedBorderColor = Color.White.copy(alpha = 0.9f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
    disabledBorderColor = Color.White.copy(alpha = 0.35f),
    cursorColor = Color.White,
    focusedPlaceholderColor = Color.White.copy(alpha = 0.45f),
    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.45f),
)

private suspend fun uploadUnifiedRouteImageIfNeeded(
    context: android.content.Context,
    source: CardioSession?,
    nip96OriginRaw: String,
    blossomPublicOriginRaw: String,
    mediaBackend: WorkoutMediaUploadBackend,
    attachRouteImage: Boolean,
    dark: Color,
    mid: Color,
    glow: Color,
    signer: EventSigner,
): String? {
    if (!attachRouteImage || source == null) return null
    if (!source.routeImageUrl.isNullOrBlank()) return source.routeImageUrl
    val points = source.gpsTrack?.points?.takeIf { it.isNotEmpty() } ?: return null
    val normalizedOrigin = when (mediaBackend) {
        WorkoutMediaUploadBackend.NIP96 -> Nip96Uploader.normalizeMediaServerOrigin(nip96OriginRaw)
        WorkoutMediaUploadBackend.BLOSSOM -> Nip96Uploader.normalizeMediaServerOrigin(blossomPublicOriginRaw)
    }
    if (normalizedOrigin.isBlank()) return null
    val bytes = CardioTrackShareImage.renderRoutePngBytes(
        context.applicationContext,
        points,
        dark.toArgb(),
        mid.toArgb(),
        glow.toArgb()
    ) ?: return null
    return when (mediaBackend) {
        WorkoutMediaUploadBackend.NIP96 -> {
            Nip96Uploader.uploadRoutePngFromOrigin(
                normalizedOrigin,
                bytes,
                "erv_unified_route_${source.id.take(8)}.png",
                signer
            ).getOrNull()
        }
        WorkoutMediaUploadBackend.BLOSSOM ->
            BlossomUploader.uploadBlob(normalizedOrigin, bytes, "image/png", signer).getOrNull()
    }
}

private fun buildUnifiedWorkoutNoteContent(
    summary: com.erv.app.unifiedroutines.UnifiedWorkoutSession,
    cardioSessions: List<CardioSession>,
    weightSessions: List<WeightWorkoutSession>,
    weightState: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    distanceUnit: CardioDistanceUnit,
    personalMessage: String,
    topics: List<String>,
    routeImageUrl: String?,
): String = buildString {
    val totalEstimatedKcal =
        cardioSessions.sumOf { it.estimatedKcal ?: 0.0 } + weightSessions.sumOf { it.estimatedKcal ?: 0.0 }
    personalMessage.trim().takeIf { it.isNotBlank() }?.let {
        append(it)
        append("\n\n")
    }
    append("Completed: ${summary.routineName}\n")
    formatUnifiedElapsed(summary.startedAtEpochSeconds, summary.finishedAtEpochSeconds)?.let {
        append("$it\n")
    }
    if (totalEstimatedKcal > 0.5) {
        append("Est. calories: ~${totalEstimatedKcal.toInt()} kcal\n")
    }
    summary.heartRate?.avgBpm?.let { append("Heart rate (avg): $it bpm\n") }
    if (cardioSessions.isNotEmpty()) {
        append("\nCardio:\n")
        append("• ${cardioSessions.sumOf { it.durationMinutes }} min")
        if (cardioSessions.sumOf { it.estimatedKcal ?: 0.0 } > 0.5) {
            append(" • ~${cardioSessions.sumOf { it.estimatedKcal ?: 0.0 }.toInt()} kcal")
        }
        append("\n")
        cardioSessions.forEach { session ->
            append("• ${session.activity.displayLabel}")
            session.distanceMeters?.takeIf { it > 1.0 }?.let {
                append(" · ${formatCardioDistanceFromMeters(it, distanceUnit)}")
            }
            append("\n")
        }
    }
    if (weightSessions.isNotEmpty()) {
        append("\nWeight Training:\n")
        append(
            "• ${weightSessions.sumOf { it.entries.size }} exercises · ${weightSessions.sumOf { it.totalSetCount() }} sets"
        )
        val totalVolume = weightSessions.sumOf { it.totalVolumeLoadTimesReps(loadUnit) }
        if (totalVolume > 0.5) {
            append(" · volume ~${totalVolume.toInt()} ${weightLoadUnitSuffix(loadUnit)}×reps")
        }
        val totalWeightKcal = weightSessions.sumOf { it.estimatedKcal ?: 0.0 }
        if (totalWeightKcal > 0.5) {
            append(" · ~${totalWeightKcal.toInt()} kcal")
        }
        append("\n")
        weightSessions.flatMap { it.entries }.forEach { entry ->
            val exercise = weightState.exerciseById(entry.exerciseId)
            val exerciseName = exercise?.name ?: entry.exerciseId
            val addedLoad = exercise?.equipment == com.erv.app.weighttraining.WeightEquipment.OTHER
            append("• $exerciseName\n")
            entry.hiitBlock?.let { block ->
                append("  - ${formatUnifiedSharedHiitSummaryLine(block, loadUnit, addedLoad)}\n")
            } ?: entry.sets.forEachIndexed { index, set ->
                append(
                    "  - " + formatUnifiedSharedSetSummaryLine(
                        set = set,
                        setNumber = index + 1,
                        loadUnit = loadUnit,
                        weightIsAddedLoad = addedLoad
                    ) + "\n"
                )
            }
        }
    }
    routeImageUrl?.let {
        append("\n")
        append(it)
        append("\n")
    }
    if (topics.isNotEmpty()) {
        append("\n")
        append(buildWorkoutShareHashtagContentLineFromTopics(topics))
    }
}

private fun formatUnifiedSharedSetSummaryLine(
    set: com.erv.app.weighttraining.WeightSet,
    setNumber: Int,
    loadUnit: BodyWeightUnit,
    weightIsAddedLoad: Boolean
): String {
    val repsPart = if (set.reps > 0) "${set.reps} reps" else "— reps"
    val weightPart = set.weightKg?.let { weightKg ->
        val num = com.erv.app.weighttraining.formatWeightLoadNumber(weightKg, loadUnit)
        val suffix = weightLoadUnitSuffix(loadUnit)
        if (weightIsAddedLoad) " @ +$num $suffix" else " @ $num $suffix"
    }.orEmpty()
    return "Set $setNumber: $repsPart$weightPart"
}

private fun formatUnifiedSharedHiitSummaryLine(
    block: com.erv.app.weighttraining.WeightHiitBlockLog,
    loadUnit: BodyWeightUnit,
    weightIsAddedLoad: Boolean
): String {
    val weightPart = block.weightKg?.let { weightKg ->
        val num = com.erv.app.weighttraining.formatWeightLoadNumber(weightKg, loadUnit)
        val suffix = weightLoadUnitSuffix(loadUnit)
        if (weightIsAddedLoad) " @ +$num $suffix" else " @ $num $suffix"
    }.orEmpty()
    return "${block.intervals}× ${block.workSeconds}s work / ${block.restSeconds}s rest$weightPart"
}
