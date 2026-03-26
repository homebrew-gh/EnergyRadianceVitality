package com.erv.app.hr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioHrScaffolding

@Composable
fun HeartRateSessionAnalyticsSection(
    heartRate: CardioHrScaffolding?,
    userMaxHrBpm: Int?,
    useLightOnDarkBackground: Boolean,
    modifier: Modifier = Modifier,
    exerciseCorrelationLines: List<Pair<String, String>>? = null
) {
    val samples = heartRate?.samples.orEmpty()
    val hasScalars =
        heartRate?.avgBpm != null || heartRate?.maxBpm != null || heartRate?.minBpm != null
    val hasSeries = samples.size >= 2
    if (!hasScalars && !hasSeries) return

    val lineColor = if (useLightOnDarkBackground) Color.White.copy(alpha = 0.95f)
    else MaterialTheme.colorScheme.primary
    val gridColor = if (useLightOnDarkBackground) Color.White.copy(alpha = 0.22f)
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val textMain = if (useLightOnDarkBackground) Color.White.copy(alpha = 0.92f)
    else MaterialTheme.colorScheme.onSurface
    val textSub = if (useLightOnDarkBackground) Color.White.copy(alpha = 0.72f)
    else MaterialTheme.colorScheme.onSurfaceVariant

    val maxHr = remember(samples, userMaxHrBpm) { resolvedMaxHrForZones(userMaxHrBpm, samples) }
    val zoneSec = remember(samples, maxHr) { zoneDurationsSeconds(samples, maxHr) }
    val totalZone = zoneSec.sum().coerceAtLeast(1)
    val showZones = hasSeries && totalZone > 0

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Heart rate",
            style = MaterialTheme.typography.titleMedium,
            color = textMain
        )
        heartRate?.let { hr ->
            val parts = buildList {
                hr.avgBpm?.let { add("Avg $it") }
                hr.maxBpm?.let { add("Max $it") }
                hr.minBpm?.let { add("Min $it") }
            }
            if (parts.isNotEmpty()) {
                Text(
                    parts.joinToString(" · ") + " bpm",
                    style = MaterialTheme.typography.bodyLarge,
                    color = textSub
                )
            }
        }
        if (hasSeries) {
            Text(
                if (userMaxHrBpm != null) {
                    "Zones use your max HR setting ($maxHr bpm)."
                } else {
                    "Zones use this workout’s peak (~$maxHr bpm) as max — set max HR in Settings → Cardio for accuracy."
                },
                style = MaterialTheme.typography.bodySmall,
                color = textSub
            )
        }

        if (hasSeries) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (useLightOnDarkBackground) Color.White.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                HeartRateWorkoutChart(
                    samples = samples,
                    lineColor = lineColor,
                    gridColor = gridColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
        }

        if (showZones) {
            Text("Time in zones", style = MaterialTheme.typography.labelLarge, color = textMain)
            for (z in 1..5) {
                val sec = zoneSec[z - 1]
                if (sec <= 0) continue
                val frac = sec.toFloat() / totalZone
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Z$z",
                        style = MaterialTheme.typography.bodySmall,
                        color = zoneColor(z),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp),
                        color = zoneColor(z),
                        trackColor = if (useLightOnDarkBackground) Color.White.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        formatDurationSeconds(sec),
                        style = MaterialTheme.typography.bodySmall,
                        color = textSub
                    )
                }
            }
        }

        exerciseCorrelationLines?.takeIf { it.isNotEmpty() }?.let { lines ->
            Text(
                "By exercise (approx.)",
                style = MaterialTheme.typography.labelLarge,
                color = textMain,
                modifier = Modifier.padding(top = 4.dp)
            )
            lines.forEach { (title, detail) ->
                Text(
                    "• $title — $detail",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSub,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }
        }
    }
}
