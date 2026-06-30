package com.erv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.data.TrainingProfileNostrPayload
import com.erv.app.data.avoidMovementPatternLabel
import com.erv.app.data.displayLabel
import com.erv.app.data.hasSummaryContent
import com.erv.app.data.trainingStylePresetLabel
import com.erv.app.ui.components.FormSectionLabelMedium

@Composable
fun TrainingProfileSettingsSection(profile: TrainingProfileNostrPayload) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Training profile is edited on the ERV web companion (StartOS). " +
                "Changes sync through your relay and appear here after sync.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!profile.hasSummaryContent()) {
            Text(
                text = "No training profile saved yet. Open the web companion → Profile tab to set goals, style, and limits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            return
        }
        profile.primaryGoal?.let { goal ->
            ProfileSummaryRow("Primary goal", goal.displayLabel())
        }
        profile.experienceLevel?.let { level ->
            ProfileSummaryRow("Experience", level.displayLabel())
        }
        profile.typicalSessionMinutes?.let { minutes ->
            ProfileSummaryRow("Typical session", "$minutes min")
        }
        profile.typicalTrainingDaysPerWeek?.let { days ->
            ProfileSummaryRow(
                "Training days per week",
                if (days == 1) "1 day" else "$days days",
            )
        }
        if (profile.stylePresetIds.isNotEmpty()) {
            FormSectionLabelMedium("Training style presets")
            profile.stylePresetIds.forEach { id ->
                Text(
                    text = "• ${trainingStylePresetLabel(id)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        profile.styleNotes?.takeIf { it.isNotBlank() }?.let { notes ->
            ProfileSummaryRow("Style notes", notes)
        }
        if (profile.avoidMovementPatterns.isNotEmpty()) {
            FormSectionLabelMedium("Movement limits")
            profile.avoidMovementPatterns.forEach { id ->
                Text(
                    text = "• ${avoidMovementPatternLabel(id)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        profile.customAvoidNotes?.takeIf { it.isNotBlank() }?.let { notes ->
            ProfileSummaryRow("Limitation notes", notes)
        }
        profile.progressionStyle?.let { style ->
            ProfileSummaryRow(
                "Progression",
                style.name.lowercase().replaceFirstChar { it.titlecase() },
            )
        }
        profile.cardioBias?.let { bias ->
            ProfileSummaryRow(
                "Cardio bias",
                when (bias.name) {
                    "ZONE2_BASE" -> "Zone 2 base"
                    else -> bias.name.lowercase().replaceFirstChar { it.titlecase() }
                },
            )
        }
        profile.ageYears?.let { age ->
            ProfileSummaryRow("Age", age.toString())
        }
        if (profile.heartRateMaxBpm != null || profile.heartRateRestingBpm != null) {
            val hrParts = buildList {
                profile.heartRateMaxBpm?.let { add("max $it bpm") }
                profile.heartRateRestingBpm?.let { add("rest $it bpm") }
            }
            ProfileSummaryRow("Heart rate zones", hrParts.joinToString(" · "))
        }
        if (profile.lastModifiedEpochSeconds > 0L) {
            Text(
                text = "Last updated (epoch): ${profile.lastModifiedEpochSeconds}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ProfileSummaryRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        FormSectionLabelMedium(label)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
