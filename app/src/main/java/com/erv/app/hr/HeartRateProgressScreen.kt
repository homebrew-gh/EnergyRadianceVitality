package com.erv.app.hr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioRepository
import com.erv.app.data.UserPreferences
import com.erv.app.unifiedroutines.UnifiedRoutineRepository
import com.erv.app.weighttraining.WeightRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateProgressScreen(
    userPreferences: UserPreferences,
    cardioRepository: CardioRepository,
    weightRepository: WeightRepository,
    unifiedRoutineRepository: UnifiedRoutineRepository,
    onBack: () -> Unit,
) {
    val zoneInputs by userPreferences.heartRateZoneInputs.collectAsState(
        initial = HeartRateZoneInputs(),
    )
    val cardioState by cardioRepository.state.collectAsState(initial = null)
    val weightState by weightRepository.state.collectAsState(initial = null)
    val unifiedState by unifiedRoutineRepository.state.collectAsState(initial = null)

    val stats = remember(cardioState, weightState, unifiedState, zoneInputs) {
        val workouts = collectHeartRateWorkouts(
            cardio = cardioState ?: return@remember null,
            weight = weightState ?: return@remember null,
            unified = unifiedState ?: return@remember null,
        )
        computeHeartRateProgressStats(workouts, zoneInputs)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Heart rate progress") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (stats == null) {
                Text("Loading…", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            if (stats.workoutsWithHr == 0) {
                Text(
                    "No workouts with heart rate data yet. Connect a BLE chest strap during cardio, " +
                        "strength, or unified workouts — zones and trends will appear here automatically.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "${stats.workoutsWithHr} workout(s) with heart rate across cardio, strength, and unified sessions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val totalZone = stats.totalZoneSeconds.sum().coerceAtLeast(1)
                Text("All-time time in zones", style = MaterialTheme.typography.titleSmall)
                for (z in 1..5) {
                    val sec = stats.totalZoneSeconds[z - 1]
                    if (sec <= 0) continue
                    val frac = sec.toFloat() / totalZone
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Z$z",
                            style = MaterialTheme.typography.bodySmall,
                            color = zoneColor(z),
                        )
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp),
                            color = zoneColor(z),
                        )
                        Text(
                            formatDurationSeconds(sec),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val zoneChartBuckets = stats.monthlyZoneSeconds.takeLast(12)
                if (zoneChartBuckets.isNotEmpty()) {
                    Text("Zone time by month", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Stacked bars show how long you spent in each zone (last ${zoneChartBuckets.size} months with HR data).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HeartRateMonthlyZoneStackedChart(
                        buckets = zoneChartBuckets,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val avgBuckets = stats.monthlyAvgBpm.takeLast(12)
                if (avgBuckets.isNotEmpty()) {
                    Text("Average heart rate by month", style = MaterialTheme.typography.titleSmall)
                    HeartRateMonthlyAvgLineChart(
                        buckets = avgBuckets,
                        lineColor = MaterialTheme.colorScheme.primary,
                        gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
