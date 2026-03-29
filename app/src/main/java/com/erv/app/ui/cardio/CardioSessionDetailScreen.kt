package com.erv.app.ui.cardio

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.erv.app.R
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioModality
import com.erv.app.cardio.CardioSession
import com.erv.app.cardio.CardioSessionSource
import com.erv.app.cardio.formatCardioAveragePaceForSession
import com.erv.app.cardio.formatCardioDistanceFromMeters
import com.erv.app.cardio.formatCardioElevationGainLoss
import com.erv.app.cardio.resolvedElevationMeters
import com.erv.app.cardio.formatCardioPackWeightFromKg
import com.erv.app.cardio.ruckLoadKgResolved
import com.erv.app.cardio.supportsPhoneGpsTracking
import com.erv.app.cardio.summaryLine
import com.erv.app.cardio.label
import com.erv.app.data.UserPreferences
import com.erv.app.hr.HeartRateSessionAnalyticsSection
import com.erv.app.ui.theme.ErvHeaderRed
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardioSessionDetailScreen(
    state: CardioLibraryState,
    logDate: LocalDate,
    sessionId: String,
    userPreferences: UserPreferences,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session = state.logFor(logDate)?.sessions?.firstOrNull { it.id == sessionId }
    val distanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    val heartRateMaxPref by userPreferences.heartRateMaxBpm.collectAsState(initial = null)
    val headerMid = ErvHeaderRed

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Workout details") },
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
        }
    ) { padding ->
        if (session == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "This workout is no longer in your log.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            CardioSessionDetailBody(
                session = session,
                distanceUnit = distanceUnit,
                userMaxHrBpm = heartRateMaxPref,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun CardioSessionDetailBody(
    session: CardioSession,
    distanceUnit: CardioDistanceUnit,
    userMaxHrBpm: Int?,
    modifier: Modifier = Modifier
) {
    val elapsedGuess = session.durationMinutes * 60
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(session.activity.displayLabel, style = MaterialTheme.typography.headlineSmall)
            Text(
                logDateLine(session),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Text(session.summaryLine(distanceUnit), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            DetailLine("Modality", session.modality.label())
        }
        if (session.modality == CardioModality.INDOOR_TREADMILL) {
            session.treadmill?.let { t ->
                item {
                    Column {
                        DetailLine("Indoor", "${t.speed} ${t.speedUnit.name}")
                        if (t.inclinePercent > 0.01) {
                            Text(
                                "${t.inclinePercent.toInt()}% incline",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        if (session.activity.builtin == CardioBuiltinActivity.RUCK) {
            session.ruckLoadKgResolved()?.let { kg ->
                item { DetailLine("Pack", formatCardioPackWeightFromKg(kg)) }
            }
        }
        item { DetailLine("Duration", "${session.durationMinutes} min") }
        session.distanceMeters?.takeIf { it > 1 }?.let { d ->
            item { DetailLine("Distance", formatCardioDistanceFromMeters(d, distanceUnit)) }
        }
        item {
            val pace = formatCardioAveragePaceForSession(session, distanceUnit, elapsedGuess)
            DetailLine("Avg pace", pace ?: "—")
        }
        session.estimatedKcal?.takeIf { it > 0.5 }?.let { k ->
            item { DetailLine("Est. calories", "~${k.toInt()} kcal") }
        }
        session.routineName?.takeIf { it.isNotBlank() }?.let { name ->
            item { DetailLine("Routine", name) }
        }
        item {
            DetailLine(
                "Source",
                when (session.source) {
                    CardioSessionSource.MANUAL -> "Manual"
                    CardioSessionSource.DURATION_TIMER -> "Timer"
                    CardioSessionSource.IMPORTED -> "Imported"
                }
            )
        }
        session.startEpochSeconds?.takeIf { it > 0 }?.let { start ->
            item { DetailLine("Started", formatDetailDateTime(start)) }
        }
        session.endEpochSeconds?.takeIf { it > 0 }?.let { end ->
            item { DetailLine("Ended", formatDetailDateTime(end)) }
        }
        item { DetailLine("Logged at", formatDetailTime(session.loggedAtEpochSeconds)) }

        session.heartRate?.let { hr ->
            if (hr.samples.size >= 2 || hr.avgBpm != null || hr.maxBpm != null || hr.minBpm != null) {
                item {
                    HeartRateSessionAnalyticsSection(
                        heartRate = hr,
                        userMaxHrBpm = userMaxHrBpm,
                        useLightOnDarkBackground = false,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        if (session.segments.isNotEmpty()) {
            item {
                Text("Segments", style = MaterialTheme.typography.titleMedium)
                session.segments.sortedBy { it.orderIndex }.forEach { seg ->
                    Text(
                        "${seg.activity.displayLabel} • ${seg.durationMinutes} min",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        session.gpsTrack?.points?.takeIf { it.isNotEmpty() }?.let { pts ->
            item {
                Text(stringResource(R.string.cardio_summary_gps_track_title), style = MaterialTheme.typography.titleMedium)
                CardioGpsTrackSummaryPreview(points = pts)
                if (session.activity.supportsPhoneGpsTracking()) {
                    val elev = remember(session, pts) { session.resolvedElevationMeters() }
                    val altSamples = remember(pts) { pts.count { it.altitudeMeters != null } }
                    when {
                        elev != null -> {
                            val (gain, loss) = elev
                            Text(
                                formatCardioElevationGainLoss(gain, loss, distanceUnit),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        altSamples < 2 -> {
                            Text(
                                stringResource(R.string.cardio_summary_elevation_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        session.routeImageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Route image (shared)", style = MaterialTheme.typography.titleMedium)
                RouteImageFromUrl(
                    url = url,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun logDateLine(session: CardioSession): String {
    val parts = mutableListOf<String>()
    session.startEpochSeconds?.takeIf { it > 0 }?.let {
        parts.add("Start ${formatDetailTime(it)}")
    }
    parts.add("Logged ${formatDetailTime(session.loggedAtEpochSeconds)}")
    return parts.joinToString(" · ")
}

private fun formatDetailTime(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    return Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(formatter)
}

private fun formatDetailDateTime(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
    return Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(formatter)
}

private val routeImageHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}

@Composable
private fun RouteImageFromUrl(url: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        bitmap = null
        failed = false
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(url).build()
                routeImageHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null) {
                            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        } else {
                            failed = true
                        }
                    } else {
                        failed = true
                    }
                }
            } catch (_: Exception) {
                failed = true
            }
        }
    }
    when {
        bitmap != null -> {
            Image(
                bitmap = bitmap!!,
                contentDescription = "Route map",
                modifier = modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        failed -> {
            Text(
                "Could not load image",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = modifier
            )
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(Modifier.size(40.dp))
            }
        }
    }
}
